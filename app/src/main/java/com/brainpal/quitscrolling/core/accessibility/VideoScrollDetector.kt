package com.brainpal.quitscrolling.core.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.brainpal.quitscrolling.data.model.ContentType
import com.brainpal.quitscrolling.data.model.SocialPlatform
import kotlin.math.abs

data class ScrollSessionState(
    var isInReelSession: Boolean = false,
    var hasCountedInitialReel: Boolean = false,
    var maxReachedIndex: Int = -1,
    var currentIndex: Int = -1,
    var currentFingerprint: Int = 0,
    var pendingCandidateFingerprint: Int = 0,
    var pendingForwardScrollConfirmed: Boolean = false,
    var signedNetDeltaY: Int = 0,
    var lastScrollY: Int = -1,
    var lastIncrementElapsedRealtime: Long = 0L,
    var lastScrollEventElapsedRealtime: Long = 0L,
    var lastReelScreenSeenElapsedRealtime: Long = 0L,
    val seenFingerprints: LinkedHashSet<Int> = linkedSetOf()
) {
    fun resetSession() {
        isInReelSession = false
        hasCountedInitialReel = false
        maxReachedIndex = -1
        currentIndex = -1
        currentFingerprint = 0
        pendingCandidateFingerprint = 0
        pendingForwardScrollConfirmed = false
        signedNetDeltaY = 0
        lastScrollY = -1
        seenFingerprints.clear()
    }

    fun rememberFingerprint(fp: Int) {
        if (fp == 0) return
        seenFingerprints.add(fp)
        if (seenFingerprints.size > 200) {
            val first = seenFingerprints.iterator().next()
            seenFingerprints.remove(first)
        }
    }
}

data class DetectedScrollResult(
    val platform: SocialPlatform,
    val contentType: ContentType,
    val packageName: String
)

class VideoScrollDetector(
    private val screenWidthProvider: () -> Int,
    private val screenHeightProvider: () -> Int
) {
    private val sessionStates = mutableMapOf<String, ScrollSessionState>()

    companion object {
        // Strict 950ms minimum interval between reel counts so a single swipe can NEVER count 2 or 3 times
        private const val MIN_REEL_COUNT_INTERVAL_MS = 950L
        // Session expires if user leaves the Reel player for more than 12 seconds
        private const val REEL_SESSION_IDLE_RESET_MS = 12_000L
        private const val MAX_TRAVERSAL_DEPTH = 7
        private const val MAX_NODES_VISITED = 65

        private val KNOWN_REEL_VIEW_IDS = setOf(
            // Instagram Reels
            "clips_viewer_view_pager",
            "reel_viewer_view_pager",
            "clips_video_container",
            "clips_item_overlay_component",
            // YouTube Shorts
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_fragment_root",
            "reel_multi_format_link_view",
            // Facebook Reels & Shorts
            "reels_viewer_recycler_view",
            "fb_shorts_viewer_pager",
            "video_home_root_recycler_view",
            // X / Twitter Immersive Video Player
            "immersive_video_player",
            "video_player_view",
            "av_player_view",
            "tweet_video_view"
        )

        private val PAGER_CANDIDATE_KEYWORDS = listOf(
            "viewpager",
            "recyclerview",
            "componenthost",
            "scrollview",
            "listview",
            "composeview"
        )

        private val REEL_ENGAGEMENT_REGEX = Regex(
            "(?i)(like|comment|reply|share|remix|original audio|subscribe|follow|repost|bookmark)"
        )

        private val VIDEO_SURFACE_KEYWORDS = listOf(
            "surfaceview",
            "textureview",
            "videoview",
            "exoplayer",
            "playerview",
            "aspectratioframelayout",
            "videoplayer",
            "avplayer"
        )

        private val FEED_VIDEO_PLAYER_REGEX = Regex(
            "(?i)(video player|unmute|mute|play video|pause video|watch reel|immersive)"
        )

        private val COMMENT_INPUT_HINTS = listOf(
            "add a comment",
            "write a comment",
            "post your reply",
            "tweet your reply",
            "add a reply"
        )

        // Dynamic timer/progress strings (e.g., "0:04 / 0:30") must be excluded from fingerprint
        // so a playing video doesn't change its own fingerprint every second!
        private val TIMESTAMP_NOISE_REGEX = Regex("^\\d{1,2}:\\d{2}.*")
    }

    fun onAppExited(packageName: String?) {
        if (packageName == null) {
            sessionStates.values.forEach { it.resetSession() }
        } else {
            sessionStates[packageName]?.resetSession()
        }
    }

    /**
     * Checks whether the current active window is displaying a Reel, Short, or Video player,
     * AND counts the VERY FIRST reel when the user opens a Reel/Short viewer!
     */
    fun evaluateWindowForInitialReelOrVisibility(
        packageName: String,
        rootNode: AccessibilityNodeInfo?
    ): Pair<Boolean, DetectedScrollResult?> {
        val platform = SocialPlatform.fromPackageName(packageName) ?: return false to null
        if (rootNode == null) return false to null

        val screenWidth = screenWidthProvider().coerceAtLeast(1080)
        val screenHeight = screenHeightProvider().coerceAtLeast(1920)

        if (hasVisibleCommentInput(rootNode, maxNodes = 35)) {
            return false to null
        }

        val scan = scanWindowForMediaAndFingerprint(rootNode, screenWidth, screenHeight)
        val isFullScreenReelViewer = scan.hasKnownReelId ||
            (scan.hasFullScreenMediaSurface && scan.engagementCount >= 2) ||
            (scan.hasAnyVideoSurface && scan.engagementCount >= 3)

        val state = sessionStates.getOrPut(packageName) { ScrollSessionState() }
        val now = SystemClock.elapsedRealtime()

        if (!isFullScreenReelViewer) {
            // If user navigated away from the Reels tab into DMs/Profile for > 12s, reset the reel session
            if (state.isInReelSession && (now - state.lastReelScreenSeenElapsedRealtime) > REEL_SESSION_IDLE_RESET_MS) {
                state.resetSession()
            }
            return scan.hasAnyVideoSurface to null
        }

        state.lastReelScreenSeenElapsedRealtime = now

        // Count the FIRST reel when opening Facebook Shorts / IG Reels / YT Shorts / X Videos!
        if (!state.isInReelSession || !state.hasCountedInitialReel) {
            if (now - state.lastIncrementElapsedRealtime >= MIN_REEL_COUNT_INTERVAL_MS) {
                state.isInReelSession = true
                state.hasCountedInitialReel = true
                state.signedNetDeltaY = 0
                if (scan.stableContentFingerprint != 0) {
                    state.currentFingerprint = scan.stableContentFingerprint
                    state.rememberFingerprint(scan.stableContentFingerprint)
                }
                state.lastIncrementElapsedRealtime = now
                return true to DetectedScrollResult(
                    platform = platform,
                    contentType = ContentType.REEL_SHORT,
                    packageName = packageName
                )
            }
        }

        // Also check if a forward scroll was just completed and the new page content has now settled
        if (state.pendingForwardScrollConfirmed &&
            scan.stableContentFingerprint != 0 &&
            scan.stableContentFingerprint != state.currentFingerprint &&
            scan.stableContentFingerprint !in state.seenFingerprints &&
            (now - state.lastIncrementElapsedRealtime) >= MIN_REEL_COUNT_INTERVAL_MS
        ) {
            state.pendingForwardScrollConfirmed = false
            state.signedNetDeltaY = 0
            state.currentFingerprint = scan.stableContentFingerprint
            state.rememberFingerprint(scan.stableContentFingerprint)
            state.lastIncrementElapsedRealtime = now
            return true to DetectedScrollResult(
                platform = platform,
                contentType = ContentType.REEL_SHORT,
                packageName = packageName
            )
        }

        return true to null
    }

    /**
     * Evaluates a TYPE_VIEW_SCROLLED event with strict full-page completion and backward-scroll deduplication:
     * - Partial up/down dragging on the same reel cancels out and NEVER counts.
     * - Scrolling back to a previous reel NEVER counts.
     * - Re-scrolling forward onto an already-counted reel NEVER counts.
     * - Only a completed forward scroll to a brand-new reel counts (+1).
     */
    fun evaluateScrollEvent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): DetectedScrollResult? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return null

        val pkg = event.packageName?.toString() ?: return null
        val platform = SocialPlatform.fromPackageName(pkg) ?: return null
        val sourceNode = event.source ?: rootNode ?: return null

        val screenWidth = screenWidthProvider().coerceAtLeast(1080)
        val screenHeight = screenHeightProvider().coerceAtLeast(1920)

        // 1. Exclude horizontal carousels
        val dx = event.scrollDeltaX
        val dy = event.scrollDeltaY
        if (abs(dx) > abs(dy) * 1.2f && abs(dx) > 35) {
            return null
        }

        // 2. Exclude comment bottom sheets
        if (isCommentOrBottomSheetActive(sourceNode, rootNode, screenHeight)) {
            return null
        }

        val state = sessionStates.getOrPut(pkg) { ScrollSessionState() }
        val now = SystemClock.elapsedRealtime()

        // If the user hasn't scrolled for > 3 seconds, reset any leftover partial drag delta
        // so small wiggles separated by several seconds never add up!
        if (now - state.lastScrollEventElapsedRealtime > 2500L) {
            state.signedNetDeltaY = 0
            state.pendingForwardScrollConfirmed = false
        }
        state.lastScrollEventElapsedRealtime = now

        val matchedKnownId = matchKnownViewId(sourceNode)
        val windowScan = scanWindowForMediaAndFingerprint(
            root = rootNode ?: sourceNode,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        val isFullScreenReel = matchedKnownId != null ||
            windowScan.hasKnownReelId ||
            (windowScan.hasFullScreenMediaSurface && windowScan.engagementCount >= 2) ||
            matchesGeometricAndMediaHeuristics(sourceNode, screenWidth, screenHeight)

        val isFeedVideo = !isFullScreenReel &&
            (windowScan.hasAnyVideoSurface || matchesInFeedVideoNode(sourceNode, screenHeight))

        if (!isFullScreenReel && !isFeedVideo) {
            return null
        }

        state.lastReelScreenSeenElapsedRealtime = now

        // If this is the very first reel of the session and it wasn't counted yet on window open, count it now
        if (isFullScreenReel && !state.hasCountedInitialReel) {
            state.isInReelSession = true
            state.hasCountedInitialReel = true
            if (event.fromIndex >= 0) {
                state.currentIndex = event.fromIndex
                state.maxReachedIndex = event.fromIndex
            }
            if (windowScan.stableContentFingerprint != 0) {
                state.currentFingerprint = windowScan.stableContentFingerprint
                state.rememberFingerprint(windowScan.stableContentFingerprint)
            }
            state.lastIncrementElapsedRealtime = now
            return DetectedScrollResult(
                platform = platform,
                contentType = ContentType.REEL_SHORT,
                packageName = pkg
            )
        }

        val contentType = if (isFullScreenReel) ContentType.REEL_SHORT else ContentType.FEED_VIDEO

        if (shouldConfirmCompletedNewReelScroll(
                event = event,
                state = state,
                currentFingerprint = windowScan.stableContentFingerprint,
                screenHeight = screenHeight,
                now = now
            )
        ) {
            return DetectedScrollResult(
                platform = platform,
                contentType = contentType,
                packageName = pkg
            )
        }

        return null
    }

    private fun shouldConfirmCompletedNewReelScroll(
        event: AccessibilityEvent,
        state: ScrollSessionState,
        currentFingerprint: Int,
        screenHeight: Int,
        now: Long
    ): Boolean {
        val cooldownReady = (now - state.lastIncrementElapsedRealtime) >= MIN_REEL_COUNT_INTERVAL_MS

        // Track signed vertical displacement:
        // Scrolling DOWN to the next reel is positive (+dy).
        // Dragging UP / returning to a previous reel is negative (-dy), which subtracts from signedNetDeltaY!
        val dy = event.scrollDeltaY
        if (dy != 0) {
            state.signedNetDeltaY = (state.signedNetDeltaY + dy).coerceAtLeast(-screenHeight)
        } else if (event.scrollY >= 0) {
            if (state.lastScrollY >= 0) {
                val diffY = event.scrollY - state.lastScrollY
                if (abs(diffY) < screenHeight * 2) {
                    state.signedNetDeltaY = (state.signedNetDeltaY + diffY).coerceAtLeast(-screenHeight)
                }
            }
            state.lastScrollY = event.scrollY
        }

        // =========================================================================
        // CASE 1: Pager/RecyclerView provides a valid item index (fromIndex / toIndex)
        // =========================================================================
        // In ViewPager2 / RecyclerView, when a single-item page snap completes, fromIndex == toIndex (or fromIndex advances).
        // If the user is only dragging halfway between page 0 and page 1, fromIndex is 0 and toIndex is 1,
        // and if they release without snapping to page 1, it settles back to fromIndex = 0, toIndex = 0!
        val settledIndex = when {
            event.fromIndex >= 0 && event.toIndex >= 0 && event.fromIndex == event.toIndex -> event.fromIndex
            event.fromIndex >= 0 && event.toIndex < 0 -> event.fromIndex
            else -> -1
        }

        if (settledIndex >= 0) {
            if (state.maxReachedIndex < 0) {
                // Initialize baseline index on the current reel without double-counting
                state.currentIndex = settledIndex
                state.maxReachedIndex = settledIndex
                return false
            }

            // If user scrolled BACKWARD to a previous reel (settledIndex < currentIndex)
            // or returned to an index <= maxReachedIndex, update currentIndex and DO NOT COUNT!
            if (settledIndex <= state.maxReachedIndex) {
                state.currentIndex = settledIndex
                state.signedNetDeltaY = 0
                return false
            }

            // User settled on a brand-new index strictly greater than maxReachedIndex!
            if (settledIndex > state.maxReachedIndex && cooldownReady) {
                // Also verify content fingerprint hasn't already been seen (if available)
                if (currentFingerprint != 0 && currentFingerprint in state.seenFingerprints) {
                    state.currentIndex = settledIndex
                    state.maxReachedIndex = settledIndex
                    return false
                }
                state.currentIndex = settledIndex
                state.maxReachedIndex = settledIndex
                state.signedNetDeltaY = 0
                state.pendingForwardScrollConfirmed = false
                if (currentFingerprint != 0) {
                    state.currentFingerprint = currentFingerprint
                    state.rememberFingerprint(currentFingerprint)
                }
                state.lastIncrementElapsedRealtime = now
                return true
            }
            return false
        }

        // What if the pager reports fromIndex != toIndex (e.g., RecyclerView reporting visible range [fromIndex, toIndex])?
        if (event.fromIndex >= 0 && event.toIndex > event.fromIndex) {
            val leadIndex = event.fromIndex
            if (state.maxReachedIndex < 0) {
                state.currentIndex = leadIndex
                state.maxReachedIndex = leadIndex
                return false
            }
            if (leadIndex <= state.maxReachedIndex) {
                state.currentIndex = leadIndex
                return false
            }
            if (leadIndex > state.maxReachedIndex && cooldownReady) {
                if (currentFingerprint != 0 && currentFingerprint in state.seenFingerprints) {
                    state.currentIndex = leadIndex
                    state.maxReachedIndex = leadIndex
                    return false
                }
                state.currentIndex = leadIndex
                state.maxReachedIndex = leadIndex
                state.signedNetDeltaY = 0
                state.pendingForwardScrollConfirmed = false
                if (currentFingerprint != 0) {
                    state.currentFingerprint = currentFingerprint
                    state.rememberFingerprint(currentFingerprint)
                }
                state.lastIncrementElapsedRealtime = now
                return true
            }
            return false
        }

        // =========================================================================
        // CASE 2: Obfuscated Pager / Litho / X where fromIndex == -1
        // =========================================================================
        // Require BOTH:
        // 1. A full-page forward net scroll displacement (>= 68% of screen height), so slight up/down drags never trigger.
        // 2. A brand-new position-independent contentFingerprint that is NOT in seenFingerprints,
        //    so wiggling the same reel or going back to a previous reel never counts!
        val fullPageSnapThreshold = (screenHeight * 0.68f).toInt()

        if (state.signedNetDeltaY >= fullPageSnapThreshold) {
            state.pendingForwardScrollConfirmed = true
        } else if (state.signedNetDeltaY < -fullPageSnapThreshold / 2) {
            // User scrolled backward (up) -> cancel pending forward scroll
            state.pendingForwardScrollConfirmed = false
        }

        if (state.pendingForwardScrollConfirmed && cooldownReady) {
            if (currentFingerprint != 0) {
                // Only count if the settled reel's content fingerprint is brand new!
                if (currentFingerprint != state.currentFingerprint &&
                    currentFingerprint !in state.seenFingerprints
                ) {
                    state.pendingForwardScrollConfirmed = false
                    state.signedNetDeltaY = 0
                    state.currentFingerprint = currentFingerprint
                    state.rememberFingerprint(currentFingerprint)
                    state.lastIncrementElapsedRealtime = now
                    return true
                }
            }
        }

        return false
    }

    private data class WindowMediaScanResult(
        val hasKnownReelId: Boolean,
        val hasFullScreenMediaSurface: Boolean,
        val hasAnyVideoSurface: Boolean,
        val engagementCount: Int,
        val stableContentFingerprint: Int
    )

    /**
     * Scans the active window for media surfaces and computes a POSITION-INDEPENDENT
     * content fingerprint from the author name, caption, and audio title of the currently visible reel.
     * Because pixel coordinates (rect.top / rect.centerY) are NOT included in the hash,
     * dragging the same reel slightly up and down NEVER changes stableContentFingerprint!
     */
    private fun scanWindowForMediaAndFingerprint(
        root: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): WindowMediaScanResult {
        var hasKnownId = false
        var hasFullScreenSurface = false
        var hasAnySurface = false
        var engagementCount = 0
        var visited = 0

        val textTokens = mutableListOf<String>()
        val rect = Rect()

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_TRAVERSAL_DEPTH || visited >= MAX_NODES_VISITED) return
            visited++

            val resName = node.viewIdResourceName
            if (!resName.isNullOrEmpty()) {
                val shortId = resName.substringAfterLast('/')
                if (shortId in KNOWN_REEL_VIEW_IDS) {
                    hasKnownId = true
                }
            }

            val cls = node.className?.toString().orEmpty().lowercase()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val txt = node.text?.toString()?.trim().orEmpty()

            node.getBoundsInScreen(rect)

            // Ignore nodes that are scrolled off-screen or barely peeking at the very top/bottom edge
            val centerY = rect.centerY()
            val isInPrimaryViewport = centerY in (screenHeight * 0.12f).toInt()..(screenHeight * 0.88f).toInt()

            val isVisibleMediaSize = rect.height() >= (screenHeight * 0.25f).toInt() &&
                rect.width() >= (screenWidth * 0.55f).toInt()

            if (VIDEO_SURFACE_KEYWORDS.any { cls.contains(it) } ||
                FEED_VIDEO_PLAYER_REGEX.containsMatchIn(desc)
            ) {
                if (isVisibleMediaSize && isInPrimaryViewport) {
                    hasAnySurface = true
                    if (rect.height() >= (screenHeight * 0.58f).toInt()) {
                        hasFullScreenSurface = true
                    }
                }
            }

            if (isInPrimaryViewport) {
                if (REEL_ENGAGEMENT_REGEX.containsMatchIn(desc) || REEL_ENGAGEMENT_REGEX.containsMatchIn(txt)) {
                    engagementCount++
                }

                // Collect stable text tokens (author handle, caption, audio title, engagement counts)
                // while excluding generic button labels and ticking playback timestamps
                if (txt.length in 3..100 &&
                    !TIMESTAMP_NOISE_REGEX.matches(txt) &&
                    !REEL_ENGAGEMENT_REGEX.matches(txt)
                ) {
                    if (textTokens.size < 6 && txt !in textTokens) {
                        textTokens.add(txt)
                    }
                } else if (desc.length in 5..120 &&
                    !TIMESTAMP_NOISE_REGEX.matches(desc) &&
                    !REEL_ENGAGEMENT_REGEX.matches(desc)
                ) {
                    if (textTokens.size < 6 && desc !in textTokens) {
                        textTokens.add(desc)
                    }
                }
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (visited >= MAX_NODES_VISITED) break
                node.getChild(i)?.let { traverse(it, depth + 1) }
            }
        }

        traverse(root, 0)

        val stableFingerprint = if (textTokens.isEmpty()) {
            0
        } else {
            textTokens.sorted().joinToString("|").hashCode()
        }

        return WindowMediaScanResult(
            hasKnownReelId = hasKnownId,
            hasFullScreenMediaSurface = hasFullScreenSurface,
            hasAnyVideoSurface = hasAnySurface,
            engagementCount = engagementCount,
            stableContentFingerprint = stableFingerprint
        )
    }

    private fun isCommentOrBottomSheetActive(
        scrolledNode: AccessibilityNodeInfo,
        rootNode: AccessibilityNodeInfo?,
        screenHeight: Int
    ): Boolean {
        val scrolledRect = Rect()
        scrolledNode.getBoundsInScreen(scrolledRect)

        val isBottomHalfSheet = scrolledRect.top > (screenHeight * 0.45f).toInt() &&
            scrolledRect.height() < (screenHeight * 0.55f).toInt()

        if (isBottomHalfSheet && rootNode != null && hasVisibleCommentInput(rootNode, maxNodes = 35)) {
            return true
        }

        return false
    }

    private fun hasVisibleCommentInput(root: AccessibilityNodeInfo, maxNodes: Int): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val current = queue.removeFirst()
            visited++

            val cls = current.className?.toString().orEmpty()
            if (cls.contains("EditText", ignoreCase = true) && current.isVisibleToUser && current.isFocused) {
                val hint = current.hintText?.toString().orEmpty().lowercase()
                val text = current.text?.toString().orEmpty().lowercase()
                val desc = current.contentDescription?.toString().orEmpty().lowercase()
                if (COMMENT_INPUT_HINTS.any { it in hint || it in text || it in desc }) {
                    return true
                }
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun matchKnownViewId(node: AccessibilityNodeInfo): String? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= 4) {
            val resName = current.viewIdResourceName
            if (!resName.isNullOrEmpty()) {
                val shortId = resName.substringAfterLast('/')
                if (shortId in KNOWN_REEL_VIEW_IDS) {
                    return shortId
                }
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun matchesGeometricAndMediaHeuristics(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val className = node.className?.toString().orEmpty().lowercase()
        val isCandidateClass = PAGER_CANDIDATE_KEYWORDS.any { className.contains(it) }
        if (!isCandidateClass) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val heightRatio = rect.height().toFloat() / screenHeight.toFloat()
        val widthRatio = rect.width().toFloat() / screenWidth.toFloat()

        if (heightRatio < 0.65f || widthRatio < 0.80f) return false

        val scan = scanWindowForMediaAndFingerprint(node, screenWidth, screenHeight)
        return scan.hasAnyVideoSurface && scan.engagementCount >= 2
    }

    private fun matchesInFeedVideoNode(
        sourceNode: AccessibilityNodeInfo,
        screenHeight: Int
    ): Boolean {
        var found = false
        var visited = 0
        val bounds = Rect()

        fun search(current: AccessibilityNodeInfo, depth: Int) {
            if (depth > 5 || visited >= 40 || found) return
            visited++

            val cls = current.className?.toString().orEmpty().lowercase()
            val desc = current.contentDescription?.toString().orEmpty()
            if (VIDEO_SURFACE_KEYWORDS.any { cls.contains(it) } ||
                FEED_VIDEO_PLAYER_REGEX.containsMatchIn(desc)
            ) {
                current.getBoundsInScreen(bounds)
                if (bounds.height() >= (screenHeight * 0.25f).toInt()) {
                    found = true
                    return
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { search(it, depth + 1) }
            }
        }

        search(sourceNode, 0)
        return found
    }
}
