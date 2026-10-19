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
    var lastClickedReelElapsedRealtime: Long = 0L,
    var maxReachedIndex: Int = -1,
    var currentIndex: Int = -1,
    var currentFingerprint: Int = 0,
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
        lastClickedReelElapsedRealtime = 0L
        maxReachedIndex = -1
        currentIndex = -1
        currentFingerprint = 0
        pendingForwardScrollConfirmed = false
        signedNetDeltaY = 0
        lastScrollY = -1
        seenFingerprints.clear()
    }

    fun exitReelViewerToFeed() {
        isInReelSession = false
        hasCountedInitialReel = false
        maxReachedIndex = -1
        currentIndex = -1
        pendingForwardScrollConfirmed = false
        signedNetDeltaY = 0
        lastScrollY = -1
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
        private const val MIN_REEL_COUNT_INTERVAL_MS = 900L
        private const val MAX_TRAVERSAL_DEPTH = 8
        private const val MAX_NODES_VISITED = 75

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
            "(?i)(like|comment|reply|share|send|remix|original audio|audio|subscribe|follow|repost|bookmark)"
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

        // Markers that only appear on the main social feed (like Screenshot 2: "Add friend", "See all", "What's on your mind")
        // and NEVER inside the full-screen Reel player!
        private val MAIN_FEED_ONLY_MARKERS = listOf(
            "add friend",
            "what's on your mind",
            "create a post",
            "stories",
            "people you may know",
            "see all"
        )

        private val REEL_CLICK_KEYWORDS = listOf(
            "reel",
            "reels",
            "short",
            "shorts",
            "video",
            "watch",
            "play"
        )

        private val COMMENT_INPUT_HINTS = listOf(
            "add a comment",
            "write a comment",
            "post your reply",
            "tweet your reply",
            "add a reply"
        )

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
     * Called when the user taps a view (TYPE_VIEW_CLICKED) inside a monitored social app.
     * Detects when the user taps a Reel card in the feed (like Screenshot 2) or taps the Reels/Shorts tab,
     * preparing or counting the initial Reel immediately!
     */
    fun evaluateClickEvent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val pkg = event.packageName?.toString() ?: return
        val platform = SocialPlatform.fromPackageName(pkg) ?: return
        val state = sessionStates.getOrPut(pkg) { ScrollSessionState() }

        val screenWidth = screenWidthProvider().coerceAtLeast(1080)
        val screenHeight = screenHeightProvider().coerceAtLeast(1920)
        val now = SystemClock.elapsedRealtime()

        val sourceNode = event.source
        val clickedBounds = Rect()
        sourceNode?.getBoundsInScreen(clickedBounds)

        // Check if the clicked item is a tall vertical Reel preview card (like "Papaya or Bulb" in Screenshot 2)
        // or has Reel/Short/Video text/description
        val isTallReelCard = clickedBounds.height() >= (screenHeight * 0.25f).toInt() &&
            clickedBounds.width() >= (screenWidth * 0.30f).toInt() &&
            clickedBounds.height() > clickedBounds.width()

        val combinedClickText = buildString {
            append(event.text.joinToString(" "))
            append(" ")
            append(event.contentDescription ?: "")
            append(" ")
            append(sourceNode?.text ?: "")
            append(" ")
            append(sourceNode?.contentDescription ?: "")
        }.lowercase()

        val hasReelKeyword = REEL_CLICK_KEYWORDS.any { combinedClickText.contains(it) }

        // Also check if the active window currently has a "Reels" tray header visible
        val windowHasReelsTray = rootNode != null && hasReelsTrayInFeed(rootNode)

        if (isTallReelCard || hasReelKeyword || (windowHasReelsTray && clickedBounds.height() >= (screenHeight * 0.18f).toInt())) {
            // User just tapped a Reel card or Reels tab!
            // Reset hasCountedInitialReel so as soon as the Reel player opens, the first Reel is counted!
            state.exitReelViewerToFeed()
            state.lastClickedReelElapsedRealtime = now
        }
    }

    private fun hasReelsTrayInFeed(root: AccessibilityNodeInfo): Boolean {
        var found = false
        var visited = 0
        fun check(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 5 || visited >= 45 || found) return
            visited++
            val t = node.text?.toString()?.lowercase().orEmpty()
            val d = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (t == "reels" || t == "shorts" || d.contains("reels") || d.contains("shorts")) {
                found = true
                return
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { check(it, depth + 1) }
            }
        }
        check(root, 0)
        return found
    }

    /**
     * Checks whether the current active window is displaying a full-screen Reel/Short player,
     * AND counts the VERY FIRST reel when the user opens a Reel/Short (either from a feed card tap or tab)!
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
        val state = sessionStates.getOrPut(packageName) { ScrollSessionState() }
        val now = SystemClock.elapsedRealtime()

        val recentlyClickedReel = (now - state.lastClickedReelElapsedRealtime) in 1..3800L

        // If the screen shows main feed markers ("Add friend", "See all", "What's on your mind"),
        // the user is on the regular News Feed (like Screenshot 2), NOT inside the full-screen Reel player!
        if (scan.isMainFeedScreen && !scan.hasKnownReelId) {
            state.exitReelViewerToFeed()
            return scan.hasAnyVideoSurface to null
        }

        val isFullScreenReelViewer = scan.hasKnownReelId ||
            (scan.hasFullScreenMediaSurface && scan.engagementCount >= 2) ||
            (scan.hasAnyVideoSurface && scan.engagementCount >= 2 && !scan.isMainFeedScreen) ||
            (scan.hasFullScreenPagerContainer && scan.engagementCount >= 2 && !scan.isMainFeedScreen) ||
            (recentlyClickedReel && scan.engagementCount >= 2 && !scan.isMainFeedScreen)

        if (!isFullScreenReelViewer) {
            return scan.hasAnyVideoSurface to null
        }

        state.lastReelScreenSeenElapsedRealtime = now

        // Count the INITIAL reel immediately when entering the full-screen Reel/Shorts player!
        if (!state.isInReelSession || !state.hasCountedInitialReel) {
            if (now - state.lastIncrementElapsedRealtime >= MIN_REEL_COUNT_INTERVAL_MS) {
                state.isInReelSession = true
                state.hasCountedInitialReel = true
                state.lastClickedReelElapsedRealtime = 0L
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
     * Evaluates a TYPE_VIEW_SCROLLED event with strict full-page completion and backward-scroll deduplication.
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

        // 1. Exclude horizontal carousels (like swiping a horizontal Reels tray on the main feed)
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

        // If user is scrolling on the main News Feed (where "Add friend" / "See all" are visible),
        // reset the Reel viewer session so clicking a Reel next will count the initial Reel!
        if (windowScan.isMainFeedScreen && matchedKnownId == null && !windowScan.hasKnownReelId) {
            state.exitReelViewerToFeed()
            return null
        }

        val isFullScreenReel = matchedKnownId != null ||
            windowScan.hasKnownReelId ||
            (windowScan.hasFullScreenMediaSurface && windowScan.engagementCount >= 2) ||
            (windowScan.hasFullScreenPagerContainer && windowScan.engagementCount >= 2 && !windowScan.isMainFeedScreen) ||
            matchesGeometricAndMediaHeuristics(sourceNode, screenWidth, screenHeight)

        val isFeedVideo = !isFullScreenReel &&
            !windowScan.isMainFeedScreen &&
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

        // CASE 1: Pager/RecyclerView provides a valid item index (fromIndex / toIndex)
        val settledIndex = when {
            event.fromIndex >= 0 && event.toIndex >= 0 && event.fromIndex == event.toIndex -> event.fromIndex
            event.fromIndex >= 0 && event.toIndex < 0 -> event.fromIndex
            else -> -1
        }

        if (settledIndex >= 0) {
            if (state.maxReachedIndex < 0) {
                state.currentIndex = settledIndex
                state.maxReachedIndex = settledIndex
                return false
            }

            if (settledIndex <= state.maxReachedIndex) {
                state.currentIndex = settledIndex
                state.signedNetDeltaY = 0
                return false
            }

            if (settledIndex > state.maxReachedIndex && cooldownReady) {
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

        // CASE 2: Obfuscated Pager / Litho / X where fromIndex == -1
        val fullPageSnapThreshold = (screenHeight * 0.65f).toInt()

        if (state.signedNetDeltaY >= fullPageSnapThreshold) {
            state.pendingForwardScrollConfirmed = true
        } else if (state.signedNetDeltaY < -fullPageSnapThreshold / 2) {
            state.pendingForwardScrollConfirmed = false
        }

        if (state.pendingForwardScrollConfirmed && cooldownReady) {
            if (currentFingerprint != 0 &&
                currentFingerprint != state.currentFingerprint &&
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

        return false
    }

    private data class WindowMediaScanResult(
        val hasKnownReelId: Boolean,
        val hasFullScreenMediaSurface: Boolean,
        val hasFullScreenPagerContainer: Boolean,
        val hasAnyVideoSurface: Boolean,
        val isMainFeedScreen: Boolean,
        val engagementCount: Int,
        val stableContentFingerprint: Int
    )

    private fun scanWindowForMediaAndFingerprint(
        root: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): WindowMediaScanResult {
        var hasKnownId = false
        var hasFullScreenSurface = false
        var hasFullScreenPager = false
        var hasAnySurface = false
        var isMainFeed = false
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
            val lowerTxt = txt.lowercase()
            val lowerDesc = desc.lowercase()

            // Detect if we are on the regular News Feed (e.g., "Add friend", "See all", "What's on your mind")
            if (MAIN_FEED_ONLY_MARKERS.any { lowerTxt == it || lowerDesc == it || lowerTxt.contains("mutual friends") }) {
                isMainFeed = true
            }

            node.getBoundsInScreen(rect)
            val centerY = rect.centerY()
            val isInPrimaryViewport = centerY in (screenHeight * 0.10f).toInt()..(screenHeight * 0.90f).toInt()

            if (PAGER_CANDIDATE_KEYWORDS.any { cls.contains(it) }) {
                if (rect.height() >= (screenHeight * 0.78f).toInt() &&
                    rect.width() >= (screenWidth * 0.85f).toInt()
                ) {
                    hasFullScreenPager = true
                }
            }

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
            hasFullScreenPagerContainer = hasFullScreenPager,
            hasAnyVideoSurface = hasAnySurface,
            isMainFeedScreen = isMainFeed,
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
        return !scan.isMainFeedScreen && (scan.hasAnyVideoSurface || scan.engagementCount >= 2)
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
