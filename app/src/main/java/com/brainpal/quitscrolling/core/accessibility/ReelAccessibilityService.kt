package com.brainpal.quitscrolling.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.brainpal.quitscrolling.BrainPalApplication
import com.brainpal.quitscrolling.core.overlay.FloatingPillOverlayManager
import com.brainpal.quitscrolling.data.model.SocialPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ReelAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayManager: FloatingPillOverlayManager? = null
    private lateinit var scrollDetector: VideoScrollDetector

    override fun onServiceConnected() {
        super.onServiceConnected()
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

        serviceScope.launch {
            val prefs = prefsRepository.preferencesFlow.first()
            overlayManager?.showOverlay(initialOffsetYDp = prefs.pillOffsetYDp)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Show overlay when entering supported social platforms or BrainPal itself
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isMonitoredApp = SocialPlatform.fromPackageName(pkg) != null ||
                pkg == packageName
            if (isMonitoredApp) {
                overlayManager?.showOverlay()
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val result = scrollDetector.evaluateScrollEvent(
                event = event,
                rootNode = rootInActiveWindow
            )
            if (result != null) {
                val app = applicationContext as? BrainPalApplication ?: return
                serviceScope.launch(Dispatchers.IO) {
                    app.container.scrollRepository.recordScroll(
                        platform = result.platform,
                        contentType = result.contentType,
                        packageName = result.packageName
                    )
                }
            }
        }
    }

    override fun onInterrupt() {
        overlayManager?.hideOverlay()
    }

    override fun onDestroy() {
        overlayManager?.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
