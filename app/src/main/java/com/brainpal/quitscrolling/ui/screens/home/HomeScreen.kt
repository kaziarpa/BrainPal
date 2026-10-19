package com.brainpal.quitscrolling.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brainpal.quitscrolling.core.accessibility.AccessibilityPermissionHelper
import com.brainpal.quitscrolling.core.overlay.FloatingPillComposable
import com.brainpal.quitscrolling.data.model.SocialPlatform
import com.brainpal.quitscrolling.ui.components.AccessibilityOnboardingBanner
import com.brainpal.quitscrolling.ui.components.BrainMascotCanvas
import com.brainpal.quitscrolling.ui.components.BrainRotStage
import com.brainpal.quitscrolling.ui.components.PlatformBrandIcon
import com.brainpal.quitscrolling.ui.theme.AccentGold
import com.brainpal.quitscrolling.ui.theme.BadgeMaroon
import com.brainpal.quitscrolling.ui.theme.BadgeTextRed
import com.brainpal.quitscrolling.ui.theme.ButtonCream
import com.brainpal.quitscrolling.ui.theme.ButtonDarkText
import com.brainpal.quitscrolling.ui.theme.CardBorder
import com.brainpal.quitscrolling.ui.theme.CardSurface
import com.brainpal.quitscrolling.ui.theme.CardSurfaceElevated
import com.brainpal.quitscrolling.ui.theme.ObsidianBackground
import com.brainpal.quitscrolling.ui.theme.TextPrimaryWhite
import com.brainpal.quitscrolling.ui.theme.TextSecondaryMuted
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityEnabled by remember {
        mutableStateOf(AccessibilityPermissionHelper.isServiceEnabled(context))
    }
    var showLabControls by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = AccessibilityPermissionHelper.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentStage = BrainRotStage.fromCount(uiState.displayCount)

    // Floating idle vertical bobbing animation for the hero Brain Mascot
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_idle")
    val bobOffsetY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_offset_y"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live Dynamic Island Preview Pill at top of screen (mirrors the system overlay at 0:01 & 0:06)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingPillComposable(
                reelCount = uiState.displayCount
            )
        }

        // Accessibility Service Onboarding / Status Banner
        AccessibilityOnboardingBanner(
            isServiceEnabled = isAccessibilityEnabled,
            onOpenSettingsClick = {
                AccessibilityPermissionHelper.openAccessibilitySettings(context)
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Hero Headline (matches 0:19-0:20 in video)
        Text(
            text = "Every reel drains\nyour brain more.",
            color = TextPrimaryWhite,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("home_hero_title")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Rounded Pill Badge: "$displayCount reels"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(BadgeMaroon)
                .border(
                    width = 1.dp,
                    color = BadgeTextRed.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 20.dp, vertical = 7.dp)
                .testTag("home_reel_count_badge"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${uiState.displayCount} reels",
                color = BadgeTextRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Stage Indicator Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(currentStage.accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${currentStage.title} • ${currentStage.subtitle}",
                color = TextSecondaryMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large 240.dp Procedural 4-Stage Brain Mascot with idle bobbing
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset { IntOffset(0, bobOffsetY.roundToInt()) },
            contentAlignment = Alignment.Center
        ) {
            BrainMascotCanvas(
                reelCount = uiState.displayCount,
                compactMode = false,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_brain_mascot_canvas")
            )
        }

        // 4 Stage Quick Selector Chips (0-49, 50-99, 100-199, 200+)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presetCounts = listOf(
                BrainRotStage.FRESH to 24,
                BrainRotStage.DRAINING to 72,
                BrainRotStage.ROTTING to 145,
                BrainRotStage.DEAD_ROT to 230
            )
            presetCounts.forEach { (stage, sampleCount) ->
                val isSelected = currentStage == stage
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) CardSurfaceElevated else CardSurface)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) stage.accentColor else CardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            viewModel.setPreviewBrainRotCount(sampleCount)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("stage_chip_${stage.stageNumber}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Stage ${stage.stageNumber}",
                        color = if (isSelected) stage.accentColor else TextPrimaryWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stage.rangeLabel,
                        color = TextSecondaryMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Simulation & Overlay Calibration Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Controls",
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Scroll Simulator & Pill Calibration",
                        color = TextPrimaryWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { showLabControls = !showLabControls },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = if (showLabControls) "Hide" else "Show",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = showLabControls) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Platform Selector + "+1 Test Reel" Action
                    Text(
                        text = "Simulate a reel scroll on:",
                        color = TextSecondaryMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialPlatform.entries.forEach { platform ->
                            val selected = uiState.selectedSimPlatform == platform
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) CardSurfaceElevated else ObsidianBackground)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) AccentGold else CardBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectSimPlatform(platform) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlatformBrandIcon(platform = platform, size = 18.dp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.recordSimulatedReelScroll() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ButtonCream,
                                contentColor = ButtonDarkText
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("increment_test_reel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+1 ${uiState.selectedSimPlatform.displayName} Reel",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = { viewModel.resetTodayScrolls() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CardSurfaceElevated)
                                .border(1.dp, CardBorder, CircleShape)
                                .testTag("reset_today_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Today's Count",
                                tint = TextSecondaryMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preview Brain Rot Level Slider (0 - 300)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preview Brain Rot Level (0 – 300)",
                            color = TextSecondaryMuted,
                            fontSize = 12.sp
                        )
                        if (uiState.previewCountOverride != null) {
                            TextButton(
                                onClick = { viewModel.setPreviewBrainRotCount(null) }
                            ) {
                                Text(
                                    text = "Return to Live (${uiState.todayActualCount})",
                                    color = AccentGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "${uiState.displayCount} reels",
                                color = AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Slider(
                        value = uiState.displayCount.toFloat().coerceIn(0f, 300f),
                        onValueChange = { viewModel.setPreviewBrainRotCount(it.roundToInt()) },
                        valueRange = 0f..300f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGold,
                            activeTrackColor = BadgeTextRed,
                            inactiveTrackColor = CardBorder
                        ),
                        modifier = Modifier.testTag("brain_rot_preview_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Dynamic Island Cutout Y-Offset Slider (0dp..60dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Camera Cutout Pill Y-Offset",
                            color = TextSecondaryMuted,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${uiState.pillOffsetYDp} dp",
                            color = TextPrimaryWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = uiState.pillOffsetYDp.toFloat(),
                        onValueChange = { viewModel.updatePillOffsetY(it.roundToInt()) },
                        valueRange = 0f..60f,
                        colors = SliderDefaults.colors(
                            thumbColor = ButtonCream,
                            activeTrackColor = AccentGold,
                            inactiveTrackColor = CardBorder
                        ),
                        modifier = Modifier.testTag("pill_offset_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
