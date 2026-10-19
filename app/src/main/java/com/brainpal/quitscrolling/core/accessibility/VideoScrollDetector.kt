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
    var lastMainFeedScrollElapsedRealtime: Long = 0L,
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
        lastMainFeedScrollElapsedRealtime = 0L
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
        private const val MAX_TRAVERSAL_DEPTH = 12
        private const val MAX_NODES_VISITED = 140

        // Dedicated Full-Screen Reel / Shorts Viewer View IDs (never used by the regular news feed)
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
            // X / Twitter Immersive Video Player
            "immersive_video_player"
        )

        private val REEL_ENGAGEMENT_REGEX = Regex(
            "(?i)\\b(like|liked|react|reactions|comment|comments|reply|share|send|remix|original audio|audio|subscribe|follow|repost|bookmark|save|dislike)\\b"
        )

        // Horizontal feed post buttons (on Main Feed posts, videos, and ads, Like is on the left, Comment in center, Share on right)
        private val FEED_LIKE_REGEX = Regex(
            "(?i)^(like|liked|like button|react|reactions?)(\\b.*)?$"
        )
        private val FEED_COMMENT_REGEX = Regex(
            "(?i)^(comment|comments|comment button|reply)(\\b.*)?$"
        )
        private val FEED_SHARE_REGEX = Regex(
            "(?i)^(share|share button|send|repost)(\\b.*)?$"
        )

        // Chrome markers that ONLY appear inside a full-screen Reel player (never as a Main Feed tray title)
        private val REEL_SPECIFIC_CHROME_MARKERS = listOf(
            "original audio",
            "remix this reel",
            "use audio",
            "add a comment..."
        )

        private val VIDEO_SURFACE_KEYWORDS = listOf(
            "surfaceview",
            "textureview",
            "videoview",
            "exoplayer",
            "playerview"
        )

        // Markers that appear ONLY on the Main Feed / Home Tab / Navigation Bar / In-Feed Trays
        // and are NEVER present inside the Full-Screen Reels / Shorts player
        private val MAIN_FEED_ONLY_EXACT_OR_SUBSTRING_MARKERS = listOf(
            "add friend",
            "mutual friends",
            "people you may know",
            "what's on your mind",
            "create a post",
            "reels and short videos",
            "create a reel",
            "create reel",
            "see all",
            "marketplace",
            "news feed",
            "search facebook",
            "search youtube",
            "subscriptions",
            "your story",
            "create story",
            "add to story",
            "stories",
            "most relevant",
            "view more comments",
            "write a comment"
        )

        private val MAIN_FEED_SPONSORED_OR_SUGGESTED_MARKERS = listOf(
            "sponsored",
            "suggested for you"
        )

        // Matches Facebook's top/bottom main navigation tab accessibility labels like "Home, tab 1 of 6", "Video, tab 3 of 6"
        private val MAIN_FEED_TAB_OF_TOTAL_REGEX = Regex(
            "(?i).*\\btab \\d+ of \\d+\\b.*"
        )

        // Navigation tab labels that appear along the top/bottom bar of the Facebook/IG/YT main screen
        private val MAIN_FEED_NAV_TAB_REGEX = Regex(
            "(?i)^(home|feed|feeds|friends|video|watch|marketplace|notifications|menu|profile|groups|subscriptions|library|explore|messages|direct)(,.*|\\s+tab.*)?$"
        )

        private val REEL_CLICK_KEYWORDS = listOf(
            "reel",
            "reels",
            "short",
            "shorts"
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
     * Detects when the user taps a portrait Reel preview card in a Reels tray or taps the Reels/Shorts tab.
     */
    fun evaluateClickEvent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val pkg = event.packageName?.toString() ?: return
        SocialPlatform.fromPackageName(pkg) ?: return
        val state = sessionStates.getOrPut(pkg) { ScrollSessionState() }

        val screenWidth = screenWidthProvider().coerceAtLeast(1080)
        val screenHeight = screenHeightProvider().coerceAtLeast(1920)
        val now = SystemClock.elapsedRealtime()

        // If the user just scrolled the feed within the last 400ms, ignore accidental touch events
        if (now - state.lastScrollEventElapsedRealtime < 400L ||
            now - state.lastMainFeedScrollElapsedRealtime < 400L
        ) {
            return
        }

        val sourceNode = event.source
        val clickedBounds = Rect()
        sourceNode?.getBoundsInScreen(clickedBounds)

        // A Reel preview card in a horizontal "Reels" tray on the feed is a narrow portrait card
        // (width is 20%..65% of screen width, height is 20%..68% of screen height, height > width * 1.15)
        val isPortraitReelTrayCard = clickedBounds.height() in (screenHeight * 0.20f).toInt()..(screenHeight * 0.68f).toInt() &&
            clickedBounds.width() in (screenWidth * 0.20f).toInt()..(screenWidth * 0.65f).toInt() &&
            clickedBounds.height() > (clickedBounds.width() * 1.15f).toInt()

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
        val windowHasReelsTray = rootNode != null && hasReelsTrayInFeed(rootNode)

        if (hasReelKeyword || (isPortraitReelTrayCard && windowHasReelsTray)) {
            state.exitReelViewerToFeed()
            state.lastMainFeedScrollElapsedRealtime = 0L
            state.lastClickedReelElapsedRealtime = now
        }
    }

    private fun hasReelsTrayInFeed(root: AccessibilityNodeInfo): Boolean {
        var found = false
        var visited = 0
        fun check(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 8 || visited >= 80 || found) return
            visited++
            val t = node.text?.toString()?.lowercase().orEmpty()
            val d = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (t == "reels" || t == "shorts" ||
                t.contains("reels and short videos") ||
                d.contains("reels") || d.contains("shorts")
            ) {
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
     * Checks whether the current active window is inside the dedicated Full-Screen Reels / Shorts player,
     * AND counts the VERY FIRST reel when the user enters the Reels / Shorts section!
     * Strictly ignores the Main News Feed (including random feed videos, ads, and in-feed Reels trays).
     */
    fun evaluateWindowForInitialReelOrVisibility(
        packageName: String,
        rootNode: AccessibilityNodeInfo?
    ): Pair<Boolean, DetectedScrollResult?> {
        val platform = SocialPlatform.fromPackageName(packageName) ?: return false to null
        if (rootNode == null) return false to null

        val screenWidth = screenWidthProvider().coerceAtLeast(1080)
        val screenHeight = screenHeightProvider().coerceAtLeast(1920)

        if (hasVisibleCommentInput(rootNode, maxNodes = 40)) {
            return false to null
        }

        val scan = scanWindowForMediaAndFingerprint(rootNode, screenWidth, screenHeight)
        val state = sessionStates.getOrPut(packageName) { ScrollSessionState() }
        val now = SystemClock.elapsedRealtime()

        // CRITICAL: If the screen is on the Main Feed (e.g., horizontal Like/Comment/Share post bars,
        // side-by-side Reels tray cards, Facebook top/bottom tab bar, "Sponsored", "What's on your mind", etc.),
        // we are NOT inside the Reels section! Reset session and return immediately without counting!
        if (scan.isMainFeedScreen && !scan.hasKnownReelId) {
            state.exitReelViewerToFeed()
            return false to null
        }

        val recentlyClickedReel = (now - state.lastClickedReelElapsedRealtime) in 1..4000L

        // If the user is NOT in a Reel session, did NOT click a Reel, and was just scrolling the Main Feed,
        // do not let random feed content changes start a Reel session!
        if (!state.isInReelSession &&
            !recentlyClickedReel &&
            !scan.hasKnownReelId &&
            (now - state.lastMainFeedScrollElapsedRealtime) < 1500L
        ) {
            return false to null
        }

        val isInsideDedicatedReelSection = scan.hasKnownReelId ||
            (!scan.isMainFeedScreen && (
                scan.hasVerticalRightRail ||
                (scan.hasFullScreenMediaSurface && scan.rightRailEngagementCount >= 1) ||
                (platform == SocialPlatform.X_TWITTER && scan.hasFullScreenMediaSurface) ||
                (recentlyClickedReel && scan.hasFullScreenContainer && (
                    scan.rightRailEngagementCount >= 1 || scan.hasReelChromeMarker
                ))
            ))

        if (!isInsideDedicatedReelSection) {
            return false to null
        }

        state.lastReelScreenSeenElapsedRealtime = now

        // Count the INITIAL reel immediately when entering the dedicated Reels/Shorts section!
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

        // Also check if a forward scroll inside the Reels section was just completed and the new page content settled
        if (state.isInReelSession &&
            state.pendingForwardScrollConfirmed &&
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
     * Evaluates a TYPE_VIEW_SCROLLED event ONLY when the user is inside the dedicated Reels / Shorts section.
     * Scrolling the Main News Feed (even past random videos, ads, or Reels trays) is strictly ignored.
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

        // 1. Exclude horizontal carousels (such as swiping a horizontal Reels tray on the main feed)
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

        // CRITICAL: If the user is scrolling on the Main News Feed (where horizontal Like/Comment/Share bars,
        // side-by-side Reels tray cards, "Sponsored", "What's on your mind", or main feed navigation tabs are visible),
        // DO NOT count random feed videos, ads, or Reels trays!
        if (windowScan.isMainFeedScreen && matchedKnownId == null && !windowScan.hasKnownReelId) {
            state.lastMainFeedScrollElapsedRealtime = now
            state.exitReelViewerToFeed()
            return null
        }

        // Also check if the scrolled RecyclerView is a multi-item feed list (visible item span >= 2)
        // without a known Reel ID — a true full-screen Reel player only shows 1 full-screen Reel at a time!
        val visibleItemSpan = if (event.fromIndex >= 0 && event.toIndex >= event.fromIndex) {
            event.toIndex - event.fromIndex
        } else {
            0
        }
        if (visibleItemSpan >= 2 && matchedKnownId == null && !windowScan.hasKnownReelId) {
            state.lastMainFeedScrollElapsedRealtime = now
            state.exitReelViewerToFeed()
            return null
        }

        val isInsideDedicatedReelSection = matchedKnownId != null ||
            windowScan.hasKnownReelId ||
            (!windowScan.isMainFeedScreen && (
                windowScan.hasVerticalRightRail ||
                (windowScan.hasFullScreenMediaSurface && windowScan.rightRailEngagementCount >= 1) ||
                (platform == SocialPlatform.X_TWITTER && windowScan.hasFullScreenMediaSurface) ||
                (state.isInReelSession && (
                    windowScan.rightRailEngagementCount >= 1 ||
                    windowScan.hasFullScreenMediaSurface ||
                    windowScan.hasReelChromeMarker
                ))
            ))

        if (!isInsideDedicatedReelSection) {
            state.lastMainFeedScrollElapsedRealtime = now
            return null
        }

        state.lastReelScreenSeenElapsedRealtime = now

        // If this is the very first reel of the session and it wasn't counted yet on window open, count it now
        if (!state.hasCountedInitialReel) {
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
                contentType = ContentType.REEL_SHORT,
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
        val hasFullScreenContainer: Boolean,
        val hasReelChromeMarker: Boolean,
        val hasVerticalRightRail: Boolean,
        val rightRailEngagementCount: Int,
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
        var hasFullScreenContainer = false
        var hasReelChrome = false
        var hasMainFeedExactMarker = false
        var hasSponsoredOrSuggested = false
        var hasTabOfTotalMarker = false
        var topNavTabCount = 0
        var engagementCount = 0
        var visited = 0

        val leftLikeYs = mutableListOf<Int>()
        val midCommentYs = mutableListOf<Int>()
        val rightShareYs = mutableListOf<Int>()
        val portraitTrayCards = mutableListOf<Pair<Int, Int>>()
        val rightRailEngagementYs = mutableListOf<Int>()
        val rightRailControlYs = mutableListOf<Int>()

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

            node.getBoundsInScreen(rect)
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val width = rect.width()
            val height = rect.height()
            val isInPrimaryViewport = centerY in (screenHeight * 0.10f).toInt()..(screenHeight * 0.90f).toInt()

            // 1. Detect unambiguous Main Feed text/description markers
            if (MAIN_FEED_ONLY_EXACT_OR_SUBSTRING_MARKERS.any { lowerTxt.contains(it) || lowerDesc.contains(it) }) {
                hasMainFeedExactMarker = true
            }

            if (MAIN_FEED_SPONSORED_OR_SUGGESTED_MARKERS.any { lowerTxt == it || lowerDesc.contains(it) || lowerTxt.startsWith("$it ") }) {
                hasSponsoredOrSuggested = true
            }

            // 2. Detect Facebook's "tab X of Y" navigation bar tabs or top bar navigation tabs
            if (MAIN_FEED_TAB_OF_TOTAL_REGEX.matches(lowerDesc) || MAIN_FEED_TAB_OF_TOTAL_REGEX.matches(lowerTxt)) {
                hasTabOfTotalMarker = true
            } else if (centerY < (screenHeight * 0.22f).toInt() &&
                (MAIN_FEED_NAV_TAB_REGEX.matches(lowerTxt) || MAIN_FEED_NAV_TAB_REGEX.matches(lowerDesc))
            ) {
                topNavTabCount++
            }

            // 3. Detect Reel-specific player chrome ("Original audio", "Add a comment...", "Use audio")
            if (REEL_SPECIFIC_CHROME_MARKERS.any { lowerTxt.contains(it) || lowerDesc.contains(it) }) {
                hasReelChrome = true
            }

            // 4. Detect Horizontal Feed Post Action Bar (Like on left, Comment in middle, Share on right at same Y)
            if (isInPrimaryViewport && width in 10..(screenWidth * 0.48f).toInt() && height in 10..(screenHeight * 0.12f).toInt()) {
                val matchesLike = FEED_LIKE_REGEX.matches(lowerTxt) || FEED_LIKE_REGEX.matches(lowerDesc)
                val matchesComment = (FEED_COMMENT_REGEX.matches(lowerTxt) || FEED_COMMENT_REGEX.matches(lowerDesc)) &&
                    !lowerTxt.contains("add a comment") && !lowerDesc.contains("add a comment")
                val matchesShare = FEED_SHARE_REGEX.matches(lowerTxt) || FEED_SHARE_REGEX.matches(lowerDesc)

                if (matchesLike && centerX in (screenWidth * 0.02f).toInt()..(screenWidth * 0.46f).toInt()) {
                    leftLikeYs.add(centerY)
                }
                if (matchesComment && centerX in (screenWidth * 0.10f).toInt()..(screenWidth * 0.68f).toInt()) {
                    midCommentYs.add(centerY)
                }
                if (matchesShare && centerX in (screenWidth * 0.50f).toInt()..(screenWidth * 0.96f).toInt()) {
                    rightShareYs.add(centerY)
                }
            }

            // 5. Detect In-Feed Horizontal Reels / Shorts Tray (2+ narrow portrait cards side-by-side)
            if (width in (screenWidth * 0.20f).toInt()..(screenWidth * 0.64f).toInt() &&
                height in (screenHeight * 0.18f).toInt()..(screenHeight * 0.66f).toInt() &&
                height > (width * 1.12f).toInt()
            ) {
                portraitTrayCards.add(centerX to centerY)
            }

            // 6. Detect Full-Screen Container & Full-Screen Media Surface
            val isFullScreenBounds = height >= (screenHeight * 0.78f).toInt() &&
                width >= (screenWidth * 0.90f).toInt()

            if (isFullScreenBounds) {
                hasFullScreenContainer = true
            }

            if (VIDEO_SURFACE_KEYWORDS.any { cls.contains(it) }) {
                if (height >= (screenHeight * 0.78f).toInt() &&
                    width >= (screenWidth * 0.90f).toInt() &&
                    isInPrimaryViewport
                ) {
                    hasFullScreenSurface = true
                }
            }

            // 7. Detect Vertical Right-Edge Reel Engagement Rail (Like, Comment, Share stacked at centerX >= 70% width)
            if (isInPrimaryViewport) {
                val hasEngagementKeyword = REEL_ENGAGEMENT_REGEX.containsMatchIn(desc) || REEL_ENGAGEMENT_REGEX.containsMatchIn(txt)
                if (hasEngagementKeyword) {
                    engagementCount++
                    if (centerX >= (screenWidth * 0.68f).toInt() && width <= (screenWidth * 0.34f).toInt()) {
                        rightRailEngagementYs.add(centerY)
                    }
                }

                if (centerX >= (screenWidth * 0.72f).toInt() &&
                    width in 20..(screenWidth * 0.28f).toInt() &&
                    height in 20..(screenHeight * 0.18f).toInt() &&
                    centerY in (screenHeight * 0.25f).toInt()..(screenHeight * 0.90f).toInt() &&
                    (node.isClickable || txt.isNotEmpty() || desc.isNotEmpty())
                ) {
                    rightRailControlYs.add(centerY)
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

        val sameRowToleranceY = (screenHeight * 0.03f).toInt()
        val hasHorizontalFeedActionBar = leftLikeYs.size >= 2 ||
            leftLikeYs.any { likeY ->
                midCommentYs.any { commentY -> abs(likeY - commentY) <= sameRowToleranceY } ||
                    rightShareYs.any { shareY -> abs(likeY - shareY) <= sameRowToleranceY }
            }

        var hasInFeedReelsTray = false
        val trayRowToleranceY = (screenHeight * 0.08f).toInt()
        val trayMinSeparationX = (screenWidth * 0.22f).toInt()
        for (i in portraitTrayCards.indices) {
            for (j in i + 1 until portraitTrayCards.size) {
                val (x1, y1) = portraitTrayCards[i]
                val (x2, y2) = portraitTrayCards[j]
                if (abs(y1 - y2) <= trayRowToleranceY && abs(x1 - x2) >= trayMinSeparationX) {
                    hasInFeedReelsTray = true
                    break
                }
            }
            if (hasInFeedReelsTray) break
        }

        val distinctRightRailEngagement = countDistinctVerticalClusters(
            yPositions = rightRailEngagementYs,
            minGap = (screenHeight * 0.04f).toInt()
        )
        val distinctRightRailControls = countDistinctVerticalClusters(
            yPositions = rightRailControlYs,
            minGap = (screenHeight * 0.045f).toInt()
        )

        val hasVerticalRightRail = distinctRightRailEngagement >= 2 ||
            (distinctRightRailControls >= 3 && (distinctRightRailEngagement >= 1 || hasReelChrome))

        val isMainFeed = hasMainFeedExactMarker ||
            hasTabOfTotalMarker ||
            topNavTabCount >= 2 ||
            hasHorizontalFeedActionBar ||
            hasInFeedReelsTray ||
            (hasSponsoredOrSuggested && !hasVerticalRightRail)

        val stableFingerprint = if (textTokens.isEmpty()) {
            0
        } else {
            textTokens.sorted().joinToString("|").hashCode()
        }

        return WindowMediaScanResult(
            hasKnownReelId = hasKnownId,
            hasFullScreenMediaSurface = hasFullScreenSurface,
            hasFullScreenContainer = hasFullScreenContainer,
            hasReelChromeMarker = hasReelChrome,
            hasVerticalRightRail = hasVerticalRightRail,
            rightRailEngagementCount = distinctRightRailEngagement,
            isMainFeedScreen = isMainFeed,
            engagementCount = engagementCount,
            stableContentFingerprint = stableFingerprint
        )
    }

    private fun countDistinctVerticalClusters(yPositions: List<Int>, minGap: Int): Int {
        if (yPositions.isEmpty()) return 0
        val sorted = yPositions.sorted()
        var clusters = 1
        var lastY = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] - lastY >= minGap) {
                clusters++
                lastY = sorted[i]
            }
        }
        return clusters
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
}
