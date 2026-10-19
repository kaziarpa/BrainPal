package com.brainpal.quitscrolling.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
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
    private var cachedLauncherPackages: Set<String> = emptySet()

    companion object {
        // Ignore system overlays, 3-finger screenshot tools, floating assistants, keyboards, and volume bars
        // so taking a 3-finger screenshot NEVER hides the Blocker Shield or Floating Pill!
        private val IGNORED_OVERLAY_AND_SYSTEM_KEYWORDS = listOf(
            "com.android.systemui",
            "android",
            "screenshot",
            "smartcapture",
            "smartshot",
            "screenrecord",
            "capture",
            "oplus",
            "coloros",
            "heytap",
            "miui.screenshot",
            "smartsidebar",
            "floatassistant",
            "inputmethod",
            "keyboard",
            "gboard",
            "swiftkey",
            "honeyboard",
            "permissioncontroller",
            "packageinstaller",
            "volume",
            "clipboard"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityPermissionHelper.dismissFloatingSetupGuide(this)
        refreshLauncherPackages()

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
                reelSectionExitGraceUntilRealtime = SystemClock.elapsedRealtime() + 3500L
                scrollDetector.onAppExited(currentForegroundPackage)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        )

        serviceScope.launch {
            prefsRepository.preferencesFlow.collect { prefs ->
                latestPrefs.value = prefs
                if (!prefs.blockerEnabled) {
                    blockerOverlayManager?.hideBlockerShield()
                }
            }
        }

        serviceScope.launch {
            scrollRepository.todayCountFlow.collect { count ->
                latestTodayCount.value = count
            }
        }
    }

    private fun refreshLauncherPackages() {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolves = packageManager.queryIntentActivities(intent, 0)
            cachedLauncherPackages = resolves.mapNotNull { it.activityInfo?.packageName }.toSet()
        }
    }

    private fun isHomeLauncherPackage(pkg: String): Boolean {
        if (cachedLauncherPackages.isEmpty()) {
            refreshLauncherPackages()
        }
        val lower = pkg.lowercase()
        return pkg in cachedLauncherPackages ||
            lower.contains("launcher") ||
            lower.contains("trebuchet") ||
            lower.contains("nexuslauncher")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // If the user opened Likhon itself, hide the floating overlays so they can use the Likhon settings UI cleanly
        if (pkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val cls = event.className?.toString().orEmpty()
                if (cls.contains("MainActivity")) {
                    overlayManager?.hideOverlay()
                    blockerOverlayManager?.hideBlockerShield()
                }
            }
            return
        }

        val lowerPkg = pkg.lowercase()
        // Ignore 3-finger screenshot tools, system UI, volume bars, and floating tools!
        if (IGNORED_OVERLAY_AND_SYSTEM_KEYWORDS.any { lowerPkg == it || lowerPkg.contains(it) }) {
            return
        }

        val platform = SocialPlatform.fromPackageName(pkg)

        // 1. Handle App / Window Transitions
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (platform == null) {
                // CRITICAL FIX FOR 3-FINGER SCREENSHOT BUG:
                // If the Blocker Shield is currently active on screen, DO NOT allow any non-launcher window
                // (like a screenshot preview, floating transcription widget, or popup) to dismiss the Blocker Shield!
                // Only hide when the user is actually on the Home Screen Launcher.
                if (blockerOverlayManager?.isShowing == true) {
                    if (isHomeLauncherPackage(pkg)) {
                        currentForegroundPlatform = null
                        currentForegroundPackage = null
                        scrollDetector.onAppExited(null)
                        overlayManager?.hideOverlay()
                        blockerOverlayManager?.hideBlockerShield()
                    }
                    return
                }

                // When Blocker Shield is NOT showing, hide the floating pill when leaving to Launcher or another full app
                currentForegroundPlatform = null
                currentForegroundPackage = null
                scrollDetector.onAppExited(null)
                overlayManager?.resetManualDismissForNewAppSession()
                overlayManager?.hideOverlay()
                return
            }

            // Entering a monitored social app
            if (currentForegroundPackage != pkg) {
                overlayManager?.resetManualDismissForNewAppSession()
            }
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

        // Keep floating pill visible inside monitored social apps (unless user manually dragged it to the bottom X close target)
        overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)

        val now = SystemClock.elapsedRealtime()
        val inExitGracePeriod = now < reelSectionExitGraceUntilRealtime

        // 2. Detect when the user clicks on a Reel in the feed (like Screenshot 2) or taps the Reels/Shorts tab
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            scrollDetector.evaluateClickEvent(event, rootInActiveWindow)
            // Schedule a follow-up check 350ms after the click so once the Reel player animates open,
            // the initial Reel is immediately counted and checked against the Blocker limit!
            mainHandler.postDelayed({
                if (currentForegroundPlatform == platform && SystemClock.elapsedRealtime() >= reelSectionExitGraceUntilRealtime) {
                    checkWindowForInitialReel(pkg, platform, app)
                }
            }, 350L)
            return
        }

        // 3. Evaluate Window State / Content Change to count the VERY FIRST reel on entry
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            if (!inExitGracePeriod) {
                checkWindowForInitialReel(pkg, platform, app)
            }
        }

        // 4. Evaluate Scroll Events
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

    private fun checkWindowForInitialReel(
        pkg: String,
        platform: SocialPlatform,
        app: BrainPalApplication
    ) {
        val prefs = latestPrefs.value
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
            }
        } else if (isReelActive && shouldBlockPlatformNow(platform, latestTodayCount.value, prefs)) {
            triggerReelBlocker(platform, latestTodayCount.value)
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
        if (now - lastBlockTriggerElapsedRealtime < 800L && blockerOverlayManager?.isShowing == true) return
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
