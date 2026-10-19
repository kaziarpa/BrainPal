package com.brainpal.quitscrolling.core.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainpal.quitscrolling.core.haptics.LikhonHaptics
import com.brainpal.quitscrolling.core.overlay.OverlayLifecycleOwner
import com.brainpal.quitscrolling.ui.theme.AccentGold
import com.brainpal.quitscrolling.ui.theme.CardSurface
import com.brainpal.quitscrolling.ui.theme.StatusActiveGreen
import com.brainpal.quitscrolling.ui.theme.TextPrimaryWhite
import com.brainpal.quitscrolling.ui.theme.TextSecondaryMuted

object AccessibilityPermissionHelper {

    private var guideView: ComposeView? = null
    private var guideLifecycleOwner: OverlayLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, ReelAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null &&
                (enabledComponent == expectedComponentName ||
                    enabledComponent.className == ReelAccessibilityService::class.java.name)
            ) {
                return true
            }
        }
        return false
    }

    fun canDrawFloatingGuideWindow(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openDrawOverlayPermissionSettings(context: Context) {
        LikhonHaptics.softTap(context)
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }
    }

    /**
     * Opens Android Accessibility Settings AND displays a floating guide window / multi-step prompt
     * telling the user exactly which service to enable ("Likhon Scroll Tracker & Blocker").
     */
    fun openAccessibilitySettings(context: Context) {
        LikhonHaptics.softTap(context)
        val appContext = context.applicationContext

        // 1. If SYSTEM_ALERT_WINDOW is available, attach a real floating Compose card over Settings
        if (Settings.canDrawOverlays(appContext)) {
            showFloatingSetupGuideOverlay(appContext)
        }

        // 2. Always schedule sequential floating Toast prompts so the user sees which service to tap
        // even before granting extra overlay permissions
        val stepMessages = listOf(
            "👉 Step 1: Tap 'Installed apps' (or 'Downloaded apps')",
            "👉 Step 2: Tap 'Likhon Scroll Tracker & Blocker' and switch it ON!"
        )
        mainHandler.postDelayed({
            if (!isServiceEnabled(appContext)) {
                Toast.makeText(appContext, stepMessages[0], Toast.LENGTH_LONG).show()
            }
        }, 450L)

        mainHandler.postDelayed({
            if (!isServiceEnabled(appContext)) {
                Toast.makeText(appContext, stepMessages[1], Toast.LENGTH_LONG).show()
            }
        }, 3800L)

        // 3. Launch Android Accessibility Settings
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }
    }

    private fun showFloatingSetupGuideOverlay(context: Context) {
        dismissFloatingSetupGuide(context)

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (context.resources.displayMetrics.density * 48).toInt()
        }

        val owner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
            onResume()
        }

        val view = ComposeView(context).apply {
            owner.attachToView(this)
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardSurface)
                        .border(1.5.dp, AccentGold, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Likhon Accessibility Guide",
                                color = AccentGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        IconButton(
                            onClick = { dismissFloatingSetupGuide(context) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Guide",
                                tint = TextSecondaryMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "1. Tap \"Installed apps\" (or \"Downloaded services\") below",
                        color = TextPrimaryWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "2. Select ",
                            color = TextPrimaryWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "\"Likhon Scroll Tracker & Blocker\"",
                            color = StatusActiveGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "3. Turn the toggle ON and tap Allow!",
                        color = TextSecondaryMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        runCatching {
            windowManager.addView(view, params)
            guideView = view
            guideLifecycleOwner = owner

            // Poll every 1s to auto-dismiss once enabled, or after 25s
            scheduleGuideAutoDismissCheck(context, attemptsRemaining = 25)
        }.onFailure {
            owner.onDestroy()
        }
    }

    private fun scheduleGuideAutoDismissCheck(context: Context, attemptsRemaining: Int) {
        if (attemptsRemaining <= 0) {
            dismissFloatingSetupGuide(context)
            return
        }
        mainHandler.postDelayed({
            if (isServiceEnabled(context)) {
                LikhonHaptics.gentleScrollCountPulse(context)
                dismissFloatingSetupGuide(context)
            } else if (guideView != null) {
                scheduleGuideAutoDismissCheck(context, attemptsRemaining - 1)
            }
        }, 1000L)
    }

    fun dismissFloatingSetupGuide(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        guideView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        guideLifecycleOwner?.apply {
            onPause()
            onStop()
            onDestroy()
        }
        guideView = null
        guideLifecycleOwner = null
    }
}
