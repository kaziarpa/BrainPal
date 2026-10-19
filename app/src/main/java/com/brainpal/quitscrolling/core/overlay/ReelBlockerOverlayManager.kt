package com.brainpal.quitscrolling.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainpal.quitscrolling.core.haptics.LikhonHaptics
import com.brainpal.quitscrolling.data.model.SocialPlatform
import com.brainpal.quitscrolling.ui.components.BrainMascotCanvas
import com.brainpal.quitscrolling.ui.theme.AccentGold
import com.brainpal.quitscrolling.ui.theme.BadgeMaroon
import com.brainpal.quitscrolling.ui.theme.BadgeTextRed
import com.brainpal.quitscrolling.ui.theme.ButtonCream
import com.brainpal.quitscrolling.ui.theme.ButtonDarkText
import com.brainpal.quitscrolling.ui.theme.CardBorder
import com.brainpal.quitscrolling.ui.theme.CardSurface
import com.brainpal.quitscrolling.ui.theme.ObsidianBackground
import com.brainpal.quitscrolling.ui.theme.TextPrimaryWhite
import com.brainpal.quitscrolling.ui.theme.TextSecondaryMuted

class ReelBlockerOverlayManager(
    private val context: Context,
    private val onExitToHome: () -> Unit,
    private val onSnoozeFiveMinutes: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var isShowing = false

    fun showBlockerShield(
        platform: SocialPlatform,
        todayCount: Int,
        dailyLimit: Int,
        instantBlockMode: Boolean
    ) {
        if (isShowing) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val owner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
            onResume()
        }

        val view = ComposeView(context).apply {
            owner.attachToView(this)
            setContent {
                ReelBlockerShieldComposable(
                    platform = platform,
                    todayCount = todayCount,
                    dailyLimit = dailyLimit,
                    instantBlockMode = instantBlockMode,
                    onGoHomeClick = {
                        LikhonHaptics.softTap(context)
                        hideBlockerShield()
                        onExitToHome()
                    },
                    onDismissClick = {
                        LikhonHaptics.softTap(context)
                        hideBlockerShield()
                    },
                    onSnoozeClick = {
                        LikhonHaptics.softTap(context)
                        hideBlockerShield()
                        onSnoozeFiveMinutes()
                    }
                )
            }
        }

        runCatching {
            windowManager.addView(view, params)
            composeView = view
            lifecycleOwner = owner
            isShowing = true
        }.onFailure {
            owner.onDestroy()
        }
    }

    fun hideBlockerShield() {
        if (!isShowing) return
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
        isShowing = false
    }
}

@Composable
private fun ReelBlockerShieldComposable(
    platform: SocialPlatform,
    todayCount: Int,
    dailyLimit: Int,
    instantBlockMode: Boolean,
    onGoHomeClick: () -> Unit,
    onDismissClick: () -> Unit,
    onSnoozeClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xEE0A0A0D),
                        Color(0xF51A0D10),
                        Color(0xFA0A0A0D)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CardSurface)
                .border(1.5.dp, BadgeTextRed.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Warning Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BadgeMaroon)
                    .border(1.dp, BadgeTextRed.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = BadgeTextRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIKHON REEL SHIELD ACTIVE",
                    color = BadgeTextRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mascot in Stage 4 Zombie Rot state to deter further scrolling
            Box(
                modifier = Modifier.size(145.dp),
                contentAlignment = Alignment.Center
            ) {
                BrainMascotCanvas(
                    reelCount = maxOf(todayCount, 210),
                    compactMode = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${platform.displayName} Reels Blocked!",
                color = TextPrimaryWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (instantBlockMode) {
                    "Zero-Tolerance Instant Reel Blocker is ON. Likhon automatically backed you out of the video feed to protect your focus."
                } else {
                    "You've reached $todayCount / $dailyLimit reels today. Likhon intercepted this scroll to stop brain rot."
                },
                color = TextSecondaryMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Primary Action: Exit to Home Screen
            Button(
                onClick = onGoHomeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonCream,
                    contentColor = ButtonDarkText
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quit Scrolling & Go Home",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Action: Stay in App (outside of Reels)
            OutlinedButton(
                onClick = onDismissClick,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Close Shield (Backed Out of Reels)",
                    color = TextPrimaryWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Optional 5-minute snooze
            TextButton(onClick = onSnoozeClick) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Snooze Blocker for 5 Minutes",
                    color = AccentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
