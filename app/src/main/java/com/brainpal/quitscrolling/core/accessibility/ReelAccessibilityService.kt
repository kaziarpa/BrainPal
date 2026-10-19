package com.brainpal.quitscrolling.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.brainpal.quitscrolling.BrainPalApplication
import com.brainpal.quitscrolling.core.haptics.LikhonHaptics
import com.brainpal.quitscrolling.core.overlay.FloatingPillOverlayManager
import com.brainpal.quitscrolling.core.overlay.ReelBlockerOverlayManager
import com.brainpal.quitscrolling.data.local.datastore.UserPreferencesState
import com.brainpal.quitscrolling.data.model.SocialPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ReelAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayManager: FloatingPillOverlayManager? = null
    private var blockerOverlayManager: ReelBlockerOverlayManager? = null
    private lateinit var scrollDetector: VideoScrollDetector

    private val latestPrefs = MutableStateFlow(UserPreferencesState())
    private val latestTodayCount = MutableStateFlow(0)

    private var currentForegroundPlatform: SocialPlatform? = null
    private var lastBlockTriggerElapsedRealtime: Long = 0L

    companion object {
        private val IGNORED_SYSTEM_PACKAGES = listOf(
            "com.android.systemui",
            "inputmethod",
            "keyboard",
            "gboard",
            "swiftkey",
            "permissioncontroller",
            "packageinstaller"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Dismiss any setup guide overlay now that the user successfully enabled the service!
        AccessibilityPermissionHelper.dismissFloatingSetupGuide(this)

        val app = applicationContext as? BrainPalApplication ?: return
        val scrollRepository = app.container.scrollRepository
        val prefsRepository = app.container.userPreferencesRepository

        scrollDetector = VideoScrollDetector(
            screenWidthProvider = { resources.displayMetrics.widthPixels },
            screenHeightProvider = { resources.displayMetrics.heightPixels }
        )

        overlayManager = FloatingPillOverlayManager(
            context = this,
            todayCountFlow = scrollRepository.todayCountFlow,
            previewOverrideFlow = scrollRepository.previewCountOverride,
            pillOffsetYDpFlow = prefsRepository.preferencesFlow.map { it.pillOffsetYDp }
        )

        blockerOverlayManager = ReelBlockerOverlayManager(
            context = this,
            onExitToHome = {
                overlayManager?.hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            },
            onSnoozeFiveMinutes = {
                serviceScope.launch(Dispatchers.IO) {
                    prefsRepository.snoozeBlockerForMinutes(5)
                }
            }
        )

        serviceScope.launch {
            prefsRepository.preferencesFlow.collect { prefs ->
                latestPrefs.value = prefs
            }
        }

        serviceScope.launch {
            scrollRepository.todayCountFlow.collect { count ->
                latestTodayCount.value = count
            }
        }
        // NOTE: Do NOT call overlayManager.showOverlay() here!
        // The floating pill turns on ONLY when opening monitored social apps / watching Reels.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore our own overlay / system UI transient events so they don't flicker the pill
        if (pkg == packageName) return
        val lowerPkg = pkg.lowercase()
        if (IGNORED_SYSTEM_PACKAGES.any { lowerPkg.contains(it) }) return

        val platform = SocialPlatform.fromPackageName(pkg)

        // 1. Handle App / Window Transitions: Hide Pill immediately when leaving monitored apps!
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentForegroundPlatform = platform
            if (platform == null) {
                overlayManager?.hideOverlay()
                blockerOverlayManager?.hideBlockerShield()
                return
            } else {
                val prefs = latestPrefs.value
                val root = rootInActiveWindow
                val inReels = scrollDetector.isReelOrVideoScreenActive(root, platform)

                if (!prefs.onlyShowPillInReels || inReels) {
                    overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)
                }

                // If Instant Block Mode is active and the user just opened a Reels/Video screen, block immediately!
                if (inReels && shouldBlockPlatformNow(platform, latestTodayCount.value, prefs)) {
                    triggerReelBlocker(platform, latestTodayCount.value, prefs)
                    return
                }
            }
        }

        if (platform == null) {
            if (currentForegroundPlatform != null) {
                currentForegroundPlatform = null
                overlayManager?.hideOverlay()
            }
            return
        }

        currentForegroundPlatform = platform
        val prefs = latestPrefs.value

        // 2. On content change inside a monitored social app, check if user entered or exited Reels view
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (!prefs.onlyShowPillInReels) {
                overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)
            }
        }

        // 3. Evaluate Scroll Events across Instagram, YouTube, Facebook, and X
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val rootNode = rootInActiveWindow
            val result = scrollDetector.evaluateScrollEvent(
                event = event,
                rootNode = rootNode
            )

            if (result != null) {
                // Ensure the Dynamic Island Floating Pill is visible now that the user is scrolling Reels/Videos
                overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)

                val app = applicationContext as? BrainPalApplication ?: return
                val updatedCount = latestTodayCount.value + 1

                // Record the scroll event
                serviceScope.launch(Dispatchers.IO) {
                    app.container.scrollRepository.recordScroll(
                        platform = result.platform,
                        contentType = result.contentType,
                        packageName = result.packageName
                    )
                }

                // Check if Reel Blocker should intercept and block this Reel/Video
                if (shouldBlockPlatformNow(result.platform, updatedCount, prefs)) {
                    triggerReelBlocker(result.platform, updatedCount, prefs)
                } else if (prefs.hapticFeedbackEnabled) {
                    LikhonHaptics.gentleScrollCountPulse(this)
                }
            }
        }
    }

    private fun shouldBlockPlatformNow(
        platform: SocialPlatform,
        currentOrUpdatedCount: Int,
        prefs: UserPreferencesState
    ): Boolean {
        if (!prefs.blockerEnabled) return false
        if (!prefs.isPlatformBlocked(platform)) return false
        if (prefs.isSnoozeActive()) return false

        return prefs.instantBlockMode || (currentOrUpdatedCount >= prefs.dailyGoalLimit)
    }

    private fun triggerReelBlocker(
        platform: SocialPlatform,
        todayCount: Int,
        prefs: UserPreferencesState
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockTriggerElapsedRealtime < 1200L) return
        lastBlockTriggerElapsedRealtime = now

        if (prefs.hapticFeedbackEnabled) {
            LikhonHaptics.blockerShieldPulse(this)
        }

        val app = applicationContext as? BrainPalApplication
        serviceScope.launch(Dispatchers.IO) {
            app?.container?.userPreferencesRepository?.incrementBlockedCount()
        }

        if (prefs.autoBackOnBlock) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        blockerOverlayManager?.showBlockerShield(
            platform = platform,
            todayCount = todayCount,
            dailyLimit = prefs.dailyGoalLimit,
            instantBlockMode = prefs.instantBlockMode
        )
    }

    override fun onInterrupt() {
        overlayManager?.hideOverlay()
        blockerOverlayManager?.hideBlockerShield()
    }

    override fun onDestroy() {
        overlayManager?.destroy()
        blockerOverlayManager?.hideBlockerShield()
        serviceScope.cancel()
        super.onDestroy()
    }
}
