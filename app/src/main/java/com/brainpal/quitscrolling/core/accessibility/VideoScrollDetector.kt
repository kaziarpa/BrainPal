package com.brainpal.quitscrolling.core.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.brainpal.quitscrolling.data.model.ContentType
import com.brainpal.quitscrolling.data.model.SocialPlatform
import kotlin.math.abs

data class ScrollSessionState(
    var lastConfirmedIndex: Int = -1,
    var lastIncrementElapsedRealtime: Long = 0L,
    var accumulatedDeltaY: Int = 0,
    var lastSeenVideoNodeHash: Int = 0
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
        private const val COOLDOWN_MS = 850L
        private const val MAX_TRAVERSAL_DEPTH = 5
        private const val MAX_NODES_VISITED = 40

        private val KNOWN_REEL_VIEW_IDS = setOf(
            // Instagram Reels & Videos
            "clips_viewer_view_pager",
            "reel_viewer_view_pager",
            "clips_video_container",
            "feed_scrollable_container",
            // YouTube Shorts & Videos
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_fragment_root",
            "results",
            "watch_list",
            // Facebook Reels & Watch
            "reels_viewer_recycler_view",
            "video_home_root_recycler_view",
            // X / Twitter Immersive & Media
            "immersive_video_player",
            "view_pager",
            "video_player_view",
            "timeline_recycler_view"
        )

        private val PAGER_CANDIDATE_CLASSES = setOf(
            "androidx.viewpager2.widget.ViewPager2",
            "androidx.viewpager.widget.ViewPager",
            "androidx.recyclerview.widget.RecyclerView",
            "com.facebook.litho.ComponentHost"
        )

        private val ACTION_ENGAGEMENT_REGEX = Regex(
            "(?i)(like|comment|share|remix|audio|original audio|subscribe|follow|repost|bookmark)"
        )

        private val FEED_VIDEO_PLAYER_REGEX = Regex(
            "(?i)(video player|unmute|mute|play video|watch reel)"
        )

        private val COMMENT_INPUT_HINTS = listOf(
            "comment",
            "reply",
            "add a comment"
        )
    }

    fun evaluateScrollEvent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): DetectedScrollResult? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return null

        val pkg = event.packageName?.toString() ?: return null
        val platform = SocialPlatform.fromPackageName(pkg) ?: return null
        val sourceNode = event.source ?: return null

        val screenWidth = screenWidthProvider().coerceAtLeast(1080)
        val screenHeight = screenHeightProvider().coerceAtLeast(1920)

        // Pre-Filter: Comment & Bottom-Sheet Exclusion (CRITICAL)
        if (isCommentOrBottomSheetActive(sourceNode, rootNode, screenHeight)) {
            return null
        }

        // Tier 1: Known View ID Matching (source + up to 3 parent levels)
        val matchedKnownId = matchKnownViewId(sourceNode)

        // Tier 2: Obfuscation-Resilient Geometric & Media Heuristics
        val isFullScreenReelPager = matchedKnownId != null ||
            matchesGeometricAndMediaHeuristics(sourceNode, screenWidth, screenHeight)

        val state = sessionStates.getOrPut(pkg) { ScrollSessionState() }
        val now = SystemClock.elapsedRealtime()

        if (isFullScreenReelPager) {
            if (shouldConfirmScrollIncrement(event, state, screenHeight, now)) {
                return DetectedScrollResult(
                    platform = platform,
                    contentType = ContentType.REEL_SHORT,
                    packageName = pkg
                )
            }
            return null
        }

        // Tier 3: Standard In-Feed Video Detection
        if (matchesInFeedVideoScroll(event, sourceNode, state, screenHeight, now)) {
            return DetectedScrollResult(
                platform = platform,
                contentType = ContentType.FEED_VIDEO,
                packageName = pkg
            )
        }

        return null
    }

    private fun isCommentOrBottomSheetActive(
        scrolledNode: AccessibilityNodeInfo,
        rootNode: AccessibilityNodeInfo?,
        screenHeight: Int
    ): Boolean {
        val scrolledRect = Rect()
        scrolledNode.getBoundsInScreen(scrolledRect)

        // If the scrolled container's top bound is in the lower 70% of the screen (rect.top > 0.30 * screenHeight)
        if (scrolledRect.top > (screenHeight * 0.30f).toInt()) {
            return true
        }

        // Check for visible comment EditText in active window root
        if (rootNode != null && hasVisibleCommentInput(rootNode, maxNodes = 35)) {
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
            if (cls.contains("EditText", ignoreCase = true) && current.isVisibleToUser) {
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
        while (current != null && depth <= 3) {
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
        val className = node.className?.toString().orEmpty()
        val isCandidateClass = className in PAGER_CANDIDATE_CLASSES ||
            className.contains("ViewPager", ignoreCase = true) ||
            className.contains("RecyclerView", ignoreCase = true) ||
            className.contains("ComponentHost", ignoreCase = true)

        if (!isCandidateClass) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val heightRatio = rect.height().toFloat() / screenHeight.toFloat()
        val widthRatio = rect.width().toFloat() / screenWidth.toFloat()

        if (heightRatio < 0.75f || widthRatio < 0.90f) return false

        // Traverse visible children (max depth = 5, max nodes visited = 40)
        var hasVideoSurface = false
        var engagementNodeCount = 0
        var visited = 0

        fun traverse(current: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_TRAVERSAL_DEPTH || visited >= MAX_NODES_VISITED) return
            visited++

            val cls = current.className?.toString().orEmpty()
            if (cls.contains("SurfaceView") || cls.contains("TextureView") || cls.contains("VideoView")) {
                hasVideoSurface = true
                return
            }

            val desc = current.contentDescription?.toString().orEmpty()
            val txt = current.text?.toString().orEmpty()
            if (ACTION_ENGAGEMENT_REGEX.containsMatchIn(desc) || ACTION_ENGAGEMENT_REGEX.containsMatchIn(txt)) {
                engagementNodeCount++
            }

            val count = current.childCount
            for (i in 0 until count) {
                if (hasVideoSurface || engagementNodeCount >= 2 || visited >= MAX_NODES_VISITED) break
                current.getChild(i)?.let { child ->
                    traverse(child, depth + 1)
                }
            }
        }

        traverse(node, 0)
        return hasVideoSurface || engagementNodeCount >= 2
    }

    private fun matchesInFeedVideoScroll(
        event: AccessibilityEvent,
        sourceNode: AccessibilityNodeInfo,
        state: ScrollSessionState,
        screenHeight: Int,
        now: Long
    ): Boolean {
        if (now - state.lastIncrementElapsedRealtime < COOLDOWN_MS) return false

        var activeVideoHash = 0
        var visited = 0

        fun findFeedVideo(current: AccessibilityNodeInfo, depth: Int) {
            if (depth > 4 || visited >= 30 || activeVideoHash != 0) return
            visited++

            val cls = current.className?.toString().orEmpty()
            val desc = current.contentDescription?.toString().orEmpty()
            val isMediaView = cls.contains("SurfaceView") ||
                cls.contains("TextureView") ||
                FEED_VIDEO_PLAYER_REGEX.containsMatchIn(desc)

            if (isMediaView) {
                val bounds = Rect()
                current.getBoundsInScreen(bounds)
                if (bounds.height() >= (screenHeight * 0.30f).toInt()) {
                    activeVideoHash = (cls.hashCode() * 31) + bounds.height()
                    return
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { findFeedVideo(it, depth + 1) }
            }
        }

        findFeedVideo(sourceNode, 0)
        if (activeVideoHash == 0) return false

        val dy = event.scrollDeltaY
        if (dy > 0) {
            state.accumulatedDeltaY += dy
        }

        val threshold = (screenHeight * 0.55f).toInt()
        if (state.accumulatedDeltaY >= threshold && activeVideoHash != state.lastSeenVideoNodeHash) {
            state.accumulatedDeltaY = 0
            state.lastSeenVideoNodeHash = activeVideoHash
            state.lastIncrementElapsedRealtime = now
            return true
        }

        return false
    }

    // Tier 4: Page-Snap Index & Cooldown State Machine
    private fun shouldConfirmScrollIncrement(
        event: AccessibilityEvent,
        state: ScrollSessionState,
        screenHeight: Int,
        now: Long
    ): Boolean {
        // Rule A: Index Transition
        if (event.fromIndex >= 0 && event.fromIndex != state.lastConfirmedIndex) {
            if (now - state.lastIncrementElapsedRealtime >= COOLDOWN_MS) {
                state.lastConfirmedIndex = event.fromIndex
                state.lastIncrementElapsedRealtime = now
                state.accumulatedDeltaY = 0
                return true
            }
            return false
        }

        // Rule B: Delta-Y Fallback when fromIndex == -1
        val dx = event.scrollDeltaX
        val dy = event.scrollDeltaY
        if (dy > 0 && abs(dy) > (1.5f * abs(dx))) {
            state.accumulatedDeltaY += dy
            val snapThreshold = (screenHeight * 0.48f).toInt()
            if (state.accumulatedDeltaY >= snapThreshold &&
                now - state.lastIncrementElapsedRealtime >= COOLDOWN_MS
            ) {
                state.accumulatedDeltaY = 0
                state.lastIncrementElapsedRealtime = now
                return true
            }
        }

        return false
    }
}
