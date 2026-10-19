package com.brainpal.quitscrolling.ui.screens.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brainpal.quitscrolling.core.haptics.LikhonHaptics
import com.brainpal.quitscrolling.ui.components.BrainMascotCanvas
import com.brainpal.quitscrolling.ui.components.PlatformBrandIcon
import com.brainpal.quitscrolling.ui.theme.AccentGold
import com.brainpal.quitscrolling.ui.theme.BadgeTextRed
import com.brainpal.quitscrolling.ui.theme.CardBorder
import com.brainpal.quitscrolling.ui.theme.CardSurface
import com.brainpal.quitscrolling.ui.theme.CardSurfaceElevated
import com.brainpal.quitscrolling.ui.theme.ObsidianBackground
import com.brainpal.quitscrolling.ui.theme.TextPrimaryWhite
import com.brainpal.quitscrolling.ui.theme.TextSecondaryMuted
import kotlin.math.max

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Top Header: "< STATS"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    LikhonHaptics.softTap(context)
                    onBackClick()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .border(1.dp, CardBorder, CircleShape)
                    .testTag("stats_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = TextPrimaryWhite
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "STATS",
                color = TextPrimaryWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Week Selector Bar: "<  May 18 - 24  >"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    LikhonHaptics.softTap(context)
                    viewModel.previousWeek()
                },
                modifier = Modifier.testTag("prev_week_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Week",
                    tint = TextPrimaryWhite
                )
            }

            Text(
                text = uiState.weekRangeLabel,
                color = TextPrimaryWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    LikhonHaptics.softTap(context)
                    viewModel.nextWeek()
                },
                enabled = uiState.canGoNextWeek,
                modifier = Modifier.testTag("next_week_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Week",
                    tint = if (uiState.canGoNextWeek) TextPrimaryWhite else TextSecondaryMuted.copy(alpha = 0.35f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly Bar Chart Card + Total Counter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val maxWeekCount = max(uiState.dayBars.maxOfOrNull { it.count } ?: 0, 50)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                uiState.dayBars.forEachIndexed { idx, dayBar ->
                    val fraction = (dayBar.count.toFloat() / maxWeekCount.toFloat()).coerceIn(0.06f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = if (dayBar.count == 0) 0.04f else fraction,
                        animationSpec = tween(420),
                        label = "bar_fraction_$idx"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                LikhonHaptics.microTick(context)
                                viewModel.selectDay(dayBar.date)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("day_bar_column_$idx")
                    ) {
                        // Count label above active/selected bar
                        Text(
                            text = if (dayBar.isSelected || dayBar.count > 0) "${dayBar.count}" else "",
                            color = if (dayBar.isSelected) AccentGold else TextSecondaryMuted,
                            fontSize = 11.sp,
                            fontWeight = if (dayBar.isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            modifier = Modifier.height(18.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Vertical Track (height = 150.dp, width = 10.dp, RoundedCornerShape(50))
                        Box(
                            modifier = Modifier
                                .height(150.dp)
                                .width(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF23232E)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedFraction)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        brush = if (dayBar.isSelected) {
                                            Brush.verticalGradient(
                                                colors = listOf(AccentGold, BadgeTextRed)
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFF8F6B5E),
                                                    Color(0xFF563A3B)
                                                )
                                            )
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Day of week letter (M, T, W, T, F, S, S)
                        Text(
                            text = dayBar.dayLetter,
                            color = if (dayBar.isSelected) TextPrimaryWhite else TextSecondaryMuted,
                            fontSize = 13.sp,
                            fontWeight = if (dayBar.isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Tiny 18.dp BrainMascotCanvas below the selected day letter
                        Box(
                            modifier = Modifier.size(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayBar.isSelected) {
                                BrainMascotCanvas(
                                    reelCount = dayBar.count,
                                    compactMode = true,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Large Centered Number (36.sp, bold white) + "Total Reels" subtitle
            Text(
                text = "${uiState.weekTotalCount}",
                color = TextPrimaryWhite,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("stats_total_reels_value")
            )
            Text(
                text = "Total Reels (${uiState.selectedDayCount} on selected day)",
                color = TextSecondaryMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Top Apps Breakdown List
        Text(
            text = "Top apps",
            color = TextPrimaryWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            uiState.platformStats.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                        .clickable { LikhonHaptics.microTick(context) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("platform_row_${item.platform.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        PlatformBrandIcon(
                            platform = item.platform,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.platform.displayName,
                                color = TextPrimaryWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(CardSurfaceElevated)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(item.shareFraction.coerceAtLeast(0.04f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(item.platform.brandColorHex))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${item.count}",
                            color = TextPrimaryWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "reels",
                            color = TextSecondaryMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
