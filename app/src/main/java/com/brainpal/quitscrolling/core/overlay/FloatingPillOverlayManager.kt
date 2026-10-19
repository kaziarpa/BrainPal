package com.brainpal.quitscrolling.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
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

    private val displayCountFlow = MutableStateFlow(0)

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
                updateYOffset(offsetDp)
            }
        }
    }

    fun showOverlay(initialOffsetYDp: Int = 14) {
        if (isAttached) return

        val density = context.resources.displayMetrics.density
        val yOffsetPx = (initialOffsetYDp * density).toInt()

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
            y = yOffsetPx
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
                FloatingPillComposable(reelCount = count)
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
        }
    }

    fun updateYOffset(offsetDp: Int) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!isAttached) return

        val density = context.resources.displayMetrics.density
        params.y = (offsetDp * density).toInt()
        runCatching {
            windowManager.updateViewLayout(view, params)
        }
    }

    fun hideOverlay() {
        if (!isAttached) return
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        lifecycleOwner?.apply {
            onPause()
            onStop()
            onDestroy()
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
