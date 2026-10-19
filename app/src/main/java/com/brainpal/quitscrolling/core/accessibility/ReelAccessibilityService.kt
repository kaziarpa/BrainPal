package com.brainpal.quitscrolling.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.brainpal.quitscrolling.BrainPalApplication
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) return
        val lowerPkg = pkg.lowercase()
        if (IGNORED_SYSTEM_PACKAGES.any { lowerPkg.contains(it) }) return

        val platform = SocialPlatform.fromPackageName(pkg)

        // 1. Handle App / Window Transitions: Hide Pill & Reset Session when leaving monitored apps
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (platform == null) {
                currentForegroundPlatform = null
                scrollDetector.onAppExited(null)
                overlayManager?.hideOverlay()
                blockerOverlayManager?.hideBlockerShield()
                return
            }
            currentForegroundPlatform = platform
        }

        if (platform == null) {
            if (currentForegroundPlatform != null) {
                currentForegroundPlatform = null
                scrollDetector.onAppExited(null)
                overlayManager?.hideOverlay()
                blockerOverlayManager?.hideBlockerShield()
            }
            return
        }

        currentForegroundPlatform = platform
        val prefs = latestPrefs.value
        val app = applicationContext as? BrainPalApplication ?: return

        // 2. Evaluate Window State / Content Change to:
        //    - Count the VERY FIRST reel when opening Facebook Shorts / IG Reels / YT Shorts / X Videos
        //    - Control Floating Pill visibility
        //    - Trigger Reel Blocker if limit is already reached
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val (isReelActive, initialOrSettledResult) = scrollDetector.evaluateWindowForInitialReelOrVisibility(
                packageName = pkg,
                rootNode = rootInActiveWindow
            )

            if (!prefs.onlyShowPillInReels || isReelActive) {
                overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)
            } else {
                overlayManager?.hideOverlay()
            }

            if (initialOrSettledResult != null) {
                val updatedCount = latestTodayCount.value + 1
                serviceScope.launch(Dispatchers.IO) {
                    app.container.scrollRepository.recordScroll(
                        platform = initialOrSettledResult.platform,
                        contentType = initialOrSettledResult.contentType,
                        packageName = initialOrSettledResult.packageName
                    )
                }
                if (shouldBlockPlatformNow(platform, updatedCount, prefs)) {
                    triggerReelBlocker(platform, updatedCount, prefs)
                    return
                }
            } else if (isReelActive && shouldBlockPlatformNow(platform, latestTodayCount.value, prefs)) {
                triggerReelBlocker(platform, latestTodayCount.value, prefs)
                return
            }
        }

        // 3. Evaluate Scroll Events (with NO vibration when counting reels in social apps!)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val result = scrollDetector.evaluateScrollEvent(
                event = event,
                rootNode = rootInActiveWindow
            )

            if (result != null) {
                overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)
                val updatedCount = latestTodayCount.value + 1

                serviceScope.launch(Dispatchers.IO) {
                    app.container.scrollRepository.recordScroll(
                        platform = result.platform,
                        contentType = result.contentType,
                        packageName = result.packageName
                    )
                }

                // Check if Reel Blocker should block this Reel
                if (shouldBlockPlatformNow(result.platform, updatedCount, prefs)) {
                    triggerReelBlocker(result.platform, updatedCount, prefs)
                }
                // NOTE: No vibration on background reel counting per user request!
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

        return prefs.instantBlockMode || (currentOrUpdatedCount >= prefs.dailyGoalLimit)
    }

    private fun triggerReelBlocker(
        platform: SocialPlatform,
        todayCount: Int,
        prefs: UserPreferencesState
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockTriggerElapsedRealtime < 1000L) return
        lastBlockTriggerElapsedRealtime = now

        val app = applicationContext as? BrainPalApplication
        serviceScope.launch(Dispatchers.IO) {
            app?.container?.userPreferencesRepository?.incrementBlockedCount()
        }

        if (prefs.autoBackOnBlock) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        blockerOverlayManager?.showBlockerShield(
            platform = platform,
            todayCount = todayCount
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
