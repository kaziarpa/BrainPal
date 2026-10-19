package com.brainpal.quitscrolling.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayManager: FloatingPillOverlayManager? = null
    private var blockerOverlayManager: ReelBlockerOverlayManager? = null
    private lateinit var scrollDetector: VideoScrollDetector

    private val latestPrefs = MutableStateFlow(UserPreferencesState())
    private val latestTodayCount = MutableStateFlow(0)

    private var currentForegroundPlatform: SocialPlatform? = null
    private var currentForegroundPackage: String? = null
    private var lastBlockTriggerElapsedRealtime: Long = 0L
    private var reelSectionExitGraceUntilRealtime: Long = 0L

    companion object {
        private val IGNORED_SYSTEM_PACKAGES = listOf(
            "com.android.systemui",
            "android",
            "inputmethod",
            "keyboard",
            "gboard",
            "swiftkey",
            "permissioncontroller",
            "packageinstaller",
            "samsung.android.honeyboard"
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
            onCloseApp = { platform ->
                // Option 1: Close Facebook / App (exits to Home screen and terminates background process)
                val targetPkg = currentForegroundPackage
                overlayManager?.hideOverlay()
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                mainHandler.postDelayed({
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    platform.packageNames.forEach { pkg ->
                        runCatching { am?.killBackgroundProcesses(pkg) }
                    }
                    if (targetPkg != null) {
                        runCatching { am?.killBackgroundProcesses(targetPkg) }
                    }
                }, 300L)
            },
            onCloseReelSection = { _ ->
                // Option 2: Close Reel Section / Close Shorts Section
                // Backs out of the Reels/Shorts player so the user can still use the rest of the app
                reelSectionExitGraceUntilRealtime = SystemClock.elapsedRealtime() + 3500L
                scrollDetector.onAppExited(currentForegroundPackage)
                performGlobalAction(GLOBAL_ACTION_BACK)
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

        // Ignore events from our own package or transient system UI overlays (volume, status bar, keyboard)
        if (pkg == packageName) return
        val lowerPkg = pkg.lowercase()
        if (IGNORED_SYSTEM_PACKAGES.any { lowerPkg == it || lowerPkg.contains(it) }) return

        val platform = SocialPlatform.fromPackageName(pkg)

        // 1. Handle App / Window Transitions
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (platform == null) {
                // User switched to Launcher or a non-social app -> hide floating pill & blocker shield
                currentForegroundPlatform = null
                currentForegroundPackage = null
                scrollDetector.onAppExited(null)
                overlayManager?.hideOverlay()
                blockerOverlayManager?.hideBlockerShield()
                return
            }

            // User is inside a monitored social app (Facebook, Instagram, YouTube, or X)!
            // Always show the floating counter pill so it never disappears while inside the app.
            currentForegroundPlatform = platform
            currentForegroundPackage = pkg
            val prefs = latestPrefs.value
            overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)
        }

        if (platform == null) {
            return
        }

        currentForegroundPlatform = platform
        currentForegroundPackage = pkg
        val prefs = latestPrefs.value
        val app = applicationContext as? BrainPalApplication ?: return

        // Ensure the floating pill stays visible whenever active inside Facebook, Instagram, YouTube, or X
        overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)

        val now = SystemClock.elapsedRealtime()
        val inExitGracePeriod = now < reelSectionExitGraceUntilRealtime

        // 2. Evaluate Window State / Content Change to count the VERY FIRST reel on entry
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            if (!inExitGracePeriod) {
                val (isReelActive, initialOrSettledResult) = scrollDetector.evaluateWindowForInitialReelOrVisibility(
                    packageName = pkg,
                    rootNode = rootInActiveWindow
                )

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
                        triggerReelBlocker(platform, updatedCount)
                        return
                    }
                } else if (isReelActive && shouldBlockPlatformNow(platform, latestTodayCount.value, prefs)) {
                    triggerReelBlocker(platform, latestTodayCount.value)
                    return
                }
            }
        }

        // 3. Evaluate Scroll Events (100% silent — no vibration when counting reels in social apps)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val result = scrollDetector.evaluateScrollEvent(
                event = event,
                rootNode = rootInActiveWindow
            )

            if (result != null) {
                val updatedCount = latestTodayCount.value + 1

                serviceScope.launch(Dispatchers.IO) {
                    app.container.scrollRepository.recordScroll(
                        platform = result.platform,
                        contentType = result.contentType,
                        packageName = result.packageName
                    )
                }

                if (shouldBlockPlatformNow(result.platform, updatedCount, prefs)) {
                    triggerReelBlocker(result.platform, updatedCount)
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

        return prefs.instantBlockMode || (currentOrUpdatedCount >= prefs.dailyGoalLimit)
    }

    private fun triggerReelBlocker(
        platform: SocialPlatform,
        todayCount: Int
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockTriggerElapsedRealtime < 1000L) return
        lastBlockTriggerElapsedRealtime = now

        val app = applicationContext as? BrainPalApplication
        serviceScope.launch(Dispatchers.IO) {
            app?.container?.userPreferencesRepository?.incrementBlockedCount()
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
