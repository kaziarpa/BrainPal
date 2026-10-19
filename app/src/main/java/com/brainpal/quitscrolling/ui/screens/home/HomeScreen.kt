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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.brainpal.quitscrolling.core.haptics.LikhonHaptics
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
import com.brainpal.quitscrolling.ui.theme.StatusActiveGreen
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
    var lastSliderTickValue by remember { mutableIntStateOf(-1) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = AccessibilityPermissionHelper.isServiceEnabled(context)
                if (isAccessibilityEnabled) {
                    AccessibilityPermissionHelper.dismissFloatingSetupGuide(context)
                }
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
        // Live Dynamic Island Preview Pill at top of screen
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

        // Accessibility Service Onboarding / Status Banner (with Floating Guide Window)
        AccessibilityOnboardingBanner(
            isServiceEnabled = isAccessibilityEnabled,
            onOpenSettingsClick = {
                AccessibilityPermissionHelper.openAccessibilitySettings(context)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Hero Headline
        Text(
            text = "Every reel drains\nyour brain more.",
            color = TextPrimaryWhite,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("home_hero_title")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Rounded Pill Badge: "$displayCount reels" + Limit Indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BadgeMaroon)
                    .border(
                        width = 1.dp,
                        color = BadgeTextRed.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 18.dp, vertical = 7.dp)
                    .testTag("home_reel_count_badge"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${uiState.displayCount} / ${uiState.dailyGoalLimit} reels",
                    color = BadgeTextRed,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (uiState.blockerEnabled) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF13261E))
                        .border(1.dp, StatusActiveGreen.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (uiState.instantBlockMode) "🛡️ Instant Block" else "🛡️ Shield Armed",
                        color = StatusActiveGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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

        Spacer(modifier = Modifier.height(6.dp))

        // Large 225.dp Procedural 4-Stage Brain Mascot with idle bobbing
        Box(
            modifier = Modifier
                .size(225.dp)
                .offset { IntOffset(0, bobOffsetY.roundToInt()) }
                .clickable {
                    if (uiState.hapticFeedbackEnabled) {
                        LikhonHaptics.softTap(context)
                    }
                },
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
                            if (uiState.hapticFeedbackEnabled) {
                                LikhonHaptics.softTap(context)
                            }
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

        Spacer(modifier = Modifier.height(18.dp))

        // =========================================================================
        // NEW: REEL & SHORT BLOCKING SYSTEM CARD (Blocks what Likhon tracks!)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(
                    width = 1.2.dp,
                    color = if (uiState.blockerEnabled) BadgeTextRed.copy(alpha = 0.5f) else CardBorder,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(16.dp)
                .testTag("reel_blocker_system_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BadgeMaroon),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = BadgeTextRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Reel & Video Blocker System",
                            color = TextPrimaryWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (uiState.blockerEnabled) {
                                "${uiState.blockedCountToday} reels blocked today • Shield Active"
                            } else {
                                "Tracking only • Turn ON to block Reels"
                            },
                            color = if (uiState.blockerEnabled) StatusActiveGreen else TextSecondaryMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Switch(
                    checked = uiState.blockerEnabled,
                    onCheckedChange = { enabled ->
                        if (uiState.hapticFeedbackEnabled) {
                            LikhonHaptics.softTap(context)
                        }
                        viewModel.setBlockerEnabled(enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBackground,
                        checkedTrackColor = BadgeTextRed
                    ),
                    modifier = Modifier.testTag("master_reel_blocker_switch")
                )
            }

            AnimatedVisibility(visible = uiState.blockerEnabled) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // Mode Selection: Limit Mode vs Instant Block Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isLimitMode = !uiState.instantBlockMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isLimitMode) CardSurfaceElevated else ObsidianBackground)
                                .border(
                                    width = 1.dp,
                                    color = if (isLimitMode) AccentGold else CardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (uiState.hapticFeedbackEnabled) LikhonHaptics.softTap(context)
                                    viewModel.setInstantBlockMode(false)
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Block After Daily Limit",
                                color = if (isLimitMode) AccentGold else TextSecondaryMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (uiState.instantBlockMode) BadgeMaroon else ObsidianBackground)
                                .border(
                                    width = 1.dp,
                                    color = if (uiState.instantBlockMode) BadgeTextRed else CardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (uiState.hapticFeedbackEnabled) LikhonHaptics.blockerShieldPulse(context)
                                    viewModel.setInstantBlockMode(true)
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                                .testTag("instant_block_mode_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Instant Block (0 Reels)",
                                color = if (uiState.instantBlockMode) BadgeTextRed else TextSecondaryMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Daily Reel Limit Slider (when in Limit Mode)
                    if (!uiState.instantBlockMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Auto-Block After Daily Limit",
                                color = TextSecondaryMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${uiState.dailyGoalLimit} reels / day",
                                color = AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Slider(
                            value = uiState.dailyGoalLimit.toFloat(),
                            onValueChange = { raw ->
                                val stepped = ((raw / 5f).roundToInt() * 5).coerceIn(5, 250)
                                if (stepped != lastSliderTickValue) {
                                    lastSliderTickValue = stepped
                                    if (uiState.hapticFeedbackEnabled) {
                                        LikhonHaptics.microTick(context)
                                    }
                                }
                                viewModel.updateDailyGoalLimit(stepped)
                            },
                            valueRange = 5f..250f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentGold,
                                activeTrackColor = BadgeTextRed,
                                inactiveTrackColor = CardBorder
                            ),
                            modifier = Modifier.testTag("daily_limit_slider")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Per-App Blocking Toggles (IG, YT, FB, X)
                    Text(
                        text = "Apps Protected by Reel Blocker (Tap to toggle):",
                        color = TextSecondaryMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SocialPlatform.entries.forEach { platform ->
                            val isBlocked = platform.name in uiState.blockedPlatforms
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isBlocked) CardSurfaceElevated else ObsidianBackground)
                                    .border(
                                        width = 1.dp,
                                        color = if (isBlocked) BadgeTextRed.copy(alpha = 0.7f) else CardBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        if (uiState.hapticFeedbackEnabled) {
                                            LikhonHaptics.softTap(context)
                                        }
                                        viewModel.togglePlatformBlock(platform)
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PlatformBrandIcon(platform = platform, size = 22.dp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBlocked) "Blocked" else "Allowed",
                                    color = if (isBlocked) BadgeTextRed else TextSecondaryMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto-Back Enforcement Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-Back Out of Reels + Show Shield",
                                color = TextPrimaryWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = uiState.autoBackOnBlock,
                            onCheckedChange = {
                                if (uiState.hapticFeedbackEnabled) LikhonHaptics.softTap(context)
                                viewModel.setAutoBackOnBlock(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBackground,
                                checkedTrackColor = AccentGold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // FLOATING PILL VISIBILITY, HAPTICS & SIMULATOR CARD
        // =========================================================================
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
                        text = "Pill Behavior, Vibrations & Simulator",
                        color = TextPrimaryWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = {
                        if (uiState.hapticFeedbackEnabled) LikhonHaptics.softTap(context)
                        showLabControls = !showLabControls
                    },
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
                    // Smart Floating Pill Visibility Toggle (Reels-Only vs Social Apps, auto-off outside!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = StatusActiveGreen,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Show Pill ONLY While Watching Reels",
                                    color = TextPrimaryWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Automatically hides when you exit Reels or leave social apps",
                                    color = TextSecondaryMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = uiState.onlyShowPillInReels,
                            onCheckedChange = {
                                if (uiState.hapticFeedbackEnabled) LikhonHaptics.softTap(context)
                                viewModel.setOnlyShowPillInReels(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBackground,
                                checkedTrackColor = StatusActiveGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Soft Gentle Haptic Vibrations Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Gentle Haptic Vibrations",
                                    color = TextPrimaryWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Soft tactile feedback on scroll counts, buttons & sliders",
                                    color = TextSecondaryMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = uiState.hapticFeedbackEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) LikhonHaptics.gentleScrollCountPulse(context)
                                viewModel.setHapticFeedbackEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBackground,
                                checkedTrackColor = AccentGold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                                    .clickable {
                                        if (uiState.hapticFeedbackEnabled) LikhonHaptics.softTap(context)
                                        viewModel.selectSimPlatform(platform)
                                    }
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
                            onClick = {
                                if (uiState.hapticFeedbackEnabled) {
                                    LikhonHaptics.gentleScrollCountPulse(context)
                                }
                                viewModel.recordSimulatedReelScroll()
                            },
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
                            onClick = {
                                if (uiState.hapticFeedbackEnabled) LikhonHaptics.softTap(context)
                                viewModel.resetTodayScrolls()
                            },
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
                                onClick = {
                                    if (uiState.hapticFeedbackEnabled) LikhonHaptics.softTap(context)
                                    viewModel.setPreviewBrainRotCount(null)
                                }
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
                        onValueChange = {
                            val rounded = it.roundToInt()
                            if (rounded / 10 != lastSliderTickValue / 10) {
                                lastSliderTickValue = rounded
                                if (uiState.hapticFeedbackEnabled) LikhonHaptics.microTick(context)
                            }
                            viewModel.setPreviewBrainRotCount(rounded)
                        },
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
                        onValueChange = {
                            val rounded = it.roundToInt()
                            if (rounded != uiState.pillOffsetYDp && uiState.hapticFeedbackEnabled) {
                                LikhonHaptics.microTick(context)
                            }
                            viewModel.updatePillOffsetY(rounded)
                        },
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
