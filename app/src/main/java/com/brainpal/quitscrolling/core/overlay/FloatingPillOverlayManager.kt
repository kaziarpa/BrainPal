package com.brainpal.quitscrolling.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.brainpal.quitscrolling.core.haptics.LikhonHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FloatingPillOverlayManager(
    private val context: Context,
    private val todayCountFlow: Flow<Int>,
    private val previewOverrideFlow: Flow<Int?>,
    private val pillOffsetYDpFlow: Flow<Int>
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false

    // Bottom "X" dismiss target overlay while dragging
    private var dismissTargetView: ComposeView? = null
    private var dismissTargetLifecycleOwner: OverlayLifecycleOwner? = null
    private var isDismissTargetAttached = false

    private var latestOffsetYDp = 14
    private var hasCustomDraggedPosition = false
    private var savedDragX = 0
    private var savedDragY = 0

    // Tracks if user manually dragged the pill into the bottom "X" close target during the current app visit
    var isManuallyDismissedInSession: Boolean = false
        private set

    private val displayCountFlow = MutableStateFlow(0)
    private val isHoveringDismissZoneFlow = MutableStateFlow(false)

    init {
        scope.launch {
            combine(todayCountFlow, previewOverrideFlow) { todayCount, overrideCount ->
                overrideCount ?: todayCount
            }.collect { count ->
                displayCountFlow.value = count
            }
        }
        scope.launch {
            pillOffsetYDpFlow.collect { offsetDp ->
                latestOffsetYDp = offsetDp
                if (!hasCustomDraggedPosition) {
                    updateYOffset(offsetDp)
                }
            }
        }
    }

    fun resetManualDismissForNewAppSession() {
        isManuallyDismissedInSession = false
    }

    fun showOverlay(initialOffsetYDp: Int = latestOffsetYDp) {
        if (isManuallyDismissedInSession) return
        latestOffsetYDp = initialOffsetYDp

        if (isAttached && composeView != null) {
            return
        }

        if (composeView != null || lifecycleOwner != null) {
            cleanupInternal()
        }

        val density = context.resources.displayMetrics.density
        val defaultYOffsetPx = (initialOffsetYDp * density).toInt()

        // Using Gravity.TOP or Gravity.CENTER_HORIZONTAL so x=0 is centered horizontally at the top cutout,
        // and dragging adjusts x (signed horizontal offset from center) and y (vertical offset from top).
        val startX = if (hasCustomDraggedPosition) savedDragX else 0
        val startY = if (hasCustomDraggedPosition) savedDragY else defaultYOffsetPx

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = startX
            y = startY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val owner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
            onResume()
        }

        val view = ComposeView(context).apply {
            owner.attachToView(this)
            setContent {
                val count by displayCountFlow.collectAsState()
                val hoveringDismiss by isHoveringDismissZoneFlow.collectAsState()
                FloatingPillComposable(
                    reelCount = count,
                    isHoveringDismissZone = hoveringDismiss,
                    onDragStart = {
                        isHoveringDismissZoneFlow.value = false
                        showDismissTargetOverlay()
                    },
                    onDragBy = { dx, dy ->
                        handleDragDelta(dx, dy)
                    },
                    onDragEnd = {
                        handleDragRelease()
                    }
                )
            }
        }

        runCatching {
            windowManager.addView(view, params)
            composeView = view
            lifecycleOwner = owner
            layoutParams = params
            isAttached = true
        }.onFailure {
            owner.onDestroy()
            composeView = null
            lifecycleOwner = null
            layoutParams = null
            isAttached = false
        }
    }

    private fun handleDragDelta(dx: Float, dy: Float) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!isAttached) return

        val screenWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(1080)
        val screenHeight = context.resources.displayMetrics.heightPixels.coerceAtLeast(1920)
        val halfW = screenWidth / 2

        params.x = (params.x + dx.roundToInt()).coerceIn(-halfW + 60, halfW - 60)
        params.y = (params.y + dy.roundToInt()).coerceIn(0, screenHeight - 100)

        hasCustomDraggedPosition = true
        savedDragX = params.x
        savedDragY = params.y

        runCatching {
            windowManager.updateViewLayout(view, params)
        }

        // Check if pill is dragged into the bottom Messenger-style "X" dismiss zone
        val inBottomZone = params.y >= (screenHeight * 0.76f).toInt() &&
            abs(params.x) <= (screenWidth * 0.28f).toInt()

        if (inBottomZone != isHoveringDismissZoneFlow.value) {
            isHoveringDismissZoneFlow.value = inBottomZone
            if (inBottomZone) {
                LikhonHaptics.microTick(context)
            }
        }
    }

    private fun handleDragRelease() {
        val wasInDismissZone = isHoveringDismissZoneFlow.value
        isHoveringDismissZoneFlow.value = false
        hideDismissTargetOverlay()

        if (wasInDismissZone) {
            LikhonHaptics.softTap(context)
            isManuallyDismissedInSession = true
            // Reset saved position back to default top cutout for next session
            hasCustomDraggedPosition = false
            savedDragX = 0
            hideOverlay()
        }
    }

    private fun showDismissTargetOverlay() {
        if (isDismissTargetAttached) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        val owner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
            onResume()
        }

        val view = ComposeView(context).apply {
            owner.attachToView(this)
            setContent {
                val hovered by isHoveringDismissZoneFlow.collectAsState()
                FloatingPillDismissTargetComposable(isHovered = hovered)
            }
        }

        runCatching {
            windowManager.addView(view, params)
            dismissTargetView = view
            dismissTargetLifecycleOwner = owner
            isDismissTargetAttached = true
        }.onFailure {
            owner.onDestroy()
        }
    }

    private fun hideDismissTargetOverlay() {
        if (!isDismissTargetAttached && dismissTargetView == null) return
        dismissTargetView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        dismissTargetLifecycleOwner?.apply {
            runCatching {
                onPause()
                onStop()
                onDestroy()
            }
        }
        dismissTargetView = null
        dismissTargetLifecycleOwner = null
        isDismissTargetAttached = false
    }

    fun updateYOffset(offsetDp: Int) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!isAttached) return

        val density = context.resources.displayMetrics.density
        params.x = 0
        params.y = (offsetDp * density).toInt()
        hasCustomDraggedPosition = false
        runCatching {
            windowManager.updateViewLayout(view, params)
        }
    }

    fun hideOverlay() {
        hideDismissTargetOverlay()
        if (!isAttached && composeView == null) return
        cleanupInternal()
    }

    private fun cleanupInternal() {
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        lifecycleOwner?.apply {
            runCatching {
                onPause()
                onStop()
                onDestroy()
            }
        }
        composeView = null
        lifecycleOwner = null
        layoutParams = null
        isAttached = false
    }

    fun destroy() {
        hideOverlay()
        scope.cancel()
    }
}
