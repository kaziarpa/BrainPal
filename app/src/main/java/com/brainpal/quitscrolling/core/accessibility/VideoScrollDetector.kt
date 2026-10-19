package com.brainpal.quitscrolling.core.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.brainpal.quitscrolling.data.model.ContentType
import com.brainpal.quitscrolling.data.model.SocialPlatform
import kotlin.math.abs

data class ScrollSessionState(
    var lastConfirmedFromIndex: Int = -1,
    var lastConfirmedToIndex: Int = -1,
    var lastConfirmedSignature: Int = 0,
    var lastScrollY: Int = -1,
    var lastIncrementElapsedRealtime: Long = 0L,
    var accumulatedDeltaY: Int = 0
)

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
        // 320ms merges single-swipe animation frames while never missing brisk consecutive reel swipes
        private const val COOLDOWN_MS = 320L
        private const val MAX_TRAVERSAL_DEPTH = 7
        private const val MAX_NODES_VISITED = 65

        private val KNOWN_REEL_VIEW_IDS = setOf(
            // Instagram Reels & Videos
            "clips_viewer_view_pager",
            "reel_viewer_view_pager",
            "clips_video_container",
            "clips_item_overlay_component",
            "feed_scrollable_container",
            // YouTube Shorts & Videos
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_fragment_root",
            "reel_multi_format_link_view",
            "results",
            "watch_list",
            // Facebook Reels & Watch
            "reels_viewer_recycler_view",
            "video_home_root_recycler_view",
            "fb_shorts_viewer_pager",
            // X / Twitter Immersive & Timeline Media
            "immersive_video_player",
            "video_player_view",
            "av_player_view",
            "tweet_video_view",
            "media_container",
            "view_pager",
            "pager",
            "timeline_recycler_view",
            "list"
        )

        private val PAGER_CANDIDATE_KEYWORDS = listOf(
            "viewpager",
            "recyclerview",
            "componenthost",
            "scrollview",
            "listview",
            "composeview",
            "framelayout",
            "touch"
        )

        private val ACTION_ENGAGEMENT_REGEX = Regex(
            "(?i)(like|comment|reply|share|remix|audio|original audio|subscribe|follow|repost|retweet|quote|bookmark|views|watch|unmute|mute|reel|short)"
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
            "(?i)(video player|unmute|mute|play video|pause video|watch reel|immersive|gif|0:|1:|2:|3:)"
        )

        private val COMMENT_INPUT_HINTS = listOf(
            "add a comment",
            "write a comment",
            "post your reply",
            "tweet your reply",
            "add a reply"
        )
    }

    /**
     * Checks whether the current active window is displaying a Reel, Short, or Video player.
     * Used to control smart Floating Pill visibility and instant Reel Blocking.
     */
    fun isReelOrVideoScreenActive(
        rootNode: AccessibilityNodeInfo?,
        platform: SocialPlatform?
    ): Boolean {
        if (rootNode == null || platform == null) return false
        val screenWidth = screenWidthProvider().coerceAtLeast(1080)
        val screenHeight = screenHeightProvider().coerceAtLeast(1920)

        if (hasVisibleCommentInput(rootNode, maxNodes = 35)) {
            return false
        }

        val scan = scanWindowForMediaAndEngagement(rootNode, screenWidth, screenHeight)
        return scan.hasKnownReelId ||
            scan.hasFullScreenMediaSurface ||
            (scan.hasAnyVideoSurface && scan.engagementCount >= 1) ||
            scan.engagementCount >= 3
    }

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

        // Pre-Filter: Exclude horizontal carousels (e.g., multi-photo swipe)
        val dx = event.scrollDeltaX
        val dy = event.scrollDeltaY
        if (abs(dx) > abs(dy) * 1.3f && abs(dx) > 40) {
            return null
        }

        // Pre-Filter: Comment & Bottom-Sheet Exclusion
        if (isCommentOrBottomSheetActive(sourceNode, rootNode, screenHeight)) {
            return null
        }

        val state = sessionStates.getOrPut(pkg) { ScrollSessionState() }
        val now = SystemClock.elapsedRealtime()

        // Tier 1: Known View ID Matching (source + up to 4 parent levels + active window)
        val matchedKnownId = matchKnownViewId(sourceNode)

        // Inspect both sourceNode and rootNode so X/Twitter & Facebook Litho nested containers are caught
        val windowScan = scanWindowForMediaAndEngagement(
            root = rootNode ?: sourceNode,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        // Tier 2: Full-Screen Reel / Short / X Immersive Video Pager
        val isFullScreenReelOrVideo = matchedKnownId != null ||
            windowScan.hasKnownReelId ||
            windowScan.hasFullScreenMediaSurface ||
            matchesGeometricAndMediaHeuristics(sourceNode, screenWidth, screenHeight) ||
            (platform == SocialPlatform.X_TWITTER && (windowScan.hasAnyVideoSurface || windowScan.engagementCount >= 2))

        if (isFullScreenReelOrVideo) {
            if (shouldConfirmScrollIncrement(event, state, windowScan.contentSignature, screenHeight, now)) {
                return DetectedScrollResult(
                    platform = platform,
                    contentType = ContentType.REEL_SHORT,
                    packageName = pkg
                )
            }
            return null
        }

        // Tier 3: Standard In-Feed Video Detection (X Timeline Videos, Facebook Watch, IG Feed, YT Feed)
        if (windowScan.hasAnyVideoSurface || matchesInFeedVideoNode(sourceNode, screenHeight)) {
            if (shouldConfirmScrollIncrement(event, state, windowScan.contentSignature, screenHeight, now)) {
                return DetectedScrollResult(
                    platform = platform,
                    contentType = ContentType.FEED_VIDEO,
                    packageName = pkg
                )
            }
        }

        return null
    }

    private data class WindowMediaScanResult(
        val hasKnownReelId: Boolean,
        val hasFullScreenMediaSurface: Boolean,
        val hasAnyVideoSurface: Boolean,
        val engagementCount: Int,
        val contentSignature: Int
    )

    private fun scanWindowForMediaAndEngagement(
        root: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): WindowMediaScanResult {
        var hasKnownId = false
        var hasFullScreenSurface = false
        var hasAnySurface = false
        var engagementCount = 0
        var signatureHash = 17
        var visited = 0

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
            val desc = node.contentDescription?.toString().orEmpty()
            val txt = node.text?.toString().orEmpty()

            node.getBoundsInScreen(rect)
            val isVisibleHeight = rect.height() >= (screenHeight * 0.24f).toInt() &&
                rect.width() >= (screenWidth * 0.55f).toInt()

            if (VIDEO_SURFACE_KEYWORDS.any { cls.contains(it) } ||
                FEED_VIDEO_PLAYER_REGEX.containsMatchIn(desc)
            ) {
                if (isVisibleHeight) {
                    hasAnySurface = true
                    signatureHash = signatureHash * 31 + rect.centerY() / 80
                    if (rect.height() >= (screenHeight * 0.58f).toInt()) {
                        hasFullScreenSurface = true
                    }
                }
            }

            if (ACTION_ENGAGEMENT_REGEX.containsMatchIn(desc) || ACTION_ENGAGEMENT_REGEX.containsMatchIn(txt)) {
                engagementCount++
            }

            // Include visible author/caption text in viewport signature so swiping to a new reel updates signatureHash
            if (txt.length in 2..90 && rect.top in 100..(screenHeight - 100)) {
                signatureHash = signatureHash * 31 + txt.hashCode()
            } else if (desc.length in 4..120 && !ACTION_ENGAGEMENT_REGEX.matches(desc)) {
                signatureHash = signatureHash * 31 + desc.hashCode()
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (visited >= MAX_NODES_VISITED) break
                node.getChild(i)?.let { traverse(it, depth + 1) }
            }
        }

        traverse(root, 0)
        return WindowMediaScanResult(
            hasKnownReelId = hasKnownId,
            hasFullScreenMediaSurface = hasFullScreenSurface,
            hasAnyVideoSurface = hasAnySurface,
            engagementCount = engagementCount,
            contentSignature = signatureHash
        )
    }

    private fun isCommentOrBottomSheetActive(
        scrolledNode: AccessibilityNodeInfo,
        rootNode: AccessibilityNodeInfo?,
        screenHeight: Int
    ): Boolean {
        val scrolledRect = Rect()
        scrolledNode.getBoundsInScreen(scrolledRect)

        // Only treat as a half-screen comment bottom sheet if the scrolled container starts in the lower half
        // AND is shorter than 55% of the screen AND a comment input is visible
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

        if (heightRatio < 0.55f || widthRatio < 0.75f) return false

        val scan = scanWindowForMediaAndEngagement(node, screenWidth, screenHeight)
        return scan.hasAnyVideoSurface || scan.engagementCount >= 2
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
                if (bounds.height() >= (screenHeight * 0.22f).toInt()) {
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

    /**
     * Tier 4: Multi-Signal Page-Snap, Delta-Y, ScrollY & Viewport Content Signature State Machine.
     * Prevents missed reels across Instagram, YouTube, Facebook, and X.
     */
    private fun shouldConfirmScrollIncrement(
        event: AccessibilityEvent,
        state: ScrollSessionState,
        currentSignature: Int,
        screenHeight: Int,
        now: Long
    ): Boolean {
        val cooldownReady = (now - state.lastIncrementElapsedRealtime) >= COOLDOWN_MS

        // Signal A: Index Transition (fromIndex or toIndex changed)
        val fromIdx = event.fromIndex
        val toIdx = event.toIndex
        if (fromIdx >= 0 || toIdx >= 0) {
            val indexChanged = (fromIdx >= 0 && fromIdx != state.lastConfirmedFromIndex) ||
                (toIdx >= 0 && toIdx != state.lastConfirmedToIndex)
            if (indexChanged && cooldownReady) {
                state.lastConfirmedFromIndex = fromIdx
                state.lastConfirmedToIndex = toIdx
                state.lastConfirmedSignature = currentSignature
                state.lastIncrementElapsedRealtime = now
                state.accumulatedDeltaY = 0
                return true
            }
        }

        // Signal B: ScrollDeltaY accumulation (lowered threshold to 18% of screen height for quick flick gestures)
        val dy = event.scrollDeltaY
        val dx = event.scrollDeltaX
        if (dy > 0 && abs(dy) >= abs(dx)) {
            state.accumulatedDeltaY += dy
            val flickThreshold = (screenHeight * 0.18f).toInt().coerceAtLeast(180)
            if (state.accumulatedDeltaY >= flickThreshold && cooldownReady) {
                state.accumulatedDeltaY = 0
                state.lastConfirmedSignature = currentSignature
                state.lastIncrementElapsedRealtime = now
                return true
            }
        }

        // Signal C: Legacy scrollY change (used by some RecyclerView / ViewPager / X containers when scrollDeltaY is unpopulated)
        val scrollY = event.scrollY
        if (scrollY > 0 && state.lastScrollY >= 0) {
            val diffY = scrollY - state.lastScrollY
            state.lastScrollY = scrollY
            if (diffY > 0) {
                state.accumulatedDeltaY += diffY
                val flickThreshold = (screenHeight * 0.18f).toInt().coerceAtLeast(180)
                if (state.accumulatedDeltaY >= flickThreshold && cooldownReady) {
                    state.accumulatedDeltaY = 0
                    state.lastConfirmedSignature = currentSignature
                    state.lastIncrementElapsedRealtime = now
                    return true
                }
            }
        } else if (scrollY >= 0) {
            state.lastScrollY = scrollY
        }

        // Signal D: Viewport Content Signature Shift (catches X/Twitter, Facebook Litho, and IG ViewPager snaps
        // where fromIndex == -1 and scrollDeltaY == 0)
        if (currentSignature != 17 &&
            currentSignature != state.lastConfirmedSignature &&
            (now - state.lastIncrementElapsedRealtime) >= 480L
        ) {
            val hadPrevious = state.lastConfirmedSignature != 0
            state.lastConfirmedSignature = currentSignature
            if (hadPrevious) {
                state.lastIncrementElapsedRealtime = now
                state.accumulatedDeltaY = 0
                return true
            }
        }

        return false
    }
}
