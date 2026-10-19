package com.brainpal.quitscrolling.core.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainpal.quitscrolling.ui.components.BrainMascotCanvas
import com.brainpal.quitscrolling.ui.components.BrainRotStage

@Composable
fun FloatingPillComposable(
    reelCount: Int,
    modifier: Modifier = Modifier
) {
    val stage = BrainRotStage.fromCount(reelCount)

    val targetBgColor = when (stage) {
        BrainRotStage.FRESH -> Color(0xFF3D1E1E)
        BrainRotStage.DRAINING -> Color(0xFF4A2512)
        BrainRotStage.ROTTING -> Color(0xFF4F1416)
        BrainRotStage.DEAD_ROT -> Color(0xFF2B1B30)
    }

    val targetTextColor = when (stage) {
        BrainRotStage.FRESH -> Color(0xFFFF7B54)
        BrainRotStage.DRAINING -> Color(0xFFFF9248)
        BrainRotStage.ROTTING -> Color(0xFFFF5C5C)
        BrainRotStage.DEAD_ROT -> Color(0xFFE066FF)
    }

    val bgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(300),
        label = "pill_bg_color"
    )
    val textColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(300),
        label = "pill_text_color"
    )

    val scaleAnim = remember { Animatable(1f) }
    var previousCount by remember { mutableIntStateOf(reelCount) }

    LaunchedEffect(reelCount) {
        if (reelCount != previousCount) {
            previousCount = reelCount
            scaleAnim.animateTo(1.12f, animationSpec = tween(110))
            scaleAnim.animateTo(1.0f, animationSpec = tween(110))
        }
    }

    Row(
        modifier = modifier
            .scale(scaleAnim.value)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(50))
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = textColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 14.dp)
            .testTag("floating_dynamic_island_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BrainMascotCanvas(
            reelCount = reelCount,
            compactMode = true,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        AnimatedContent(
            targetState = reelCount,
            transitionSpec = {
                if (targetState >= initialState) {
                    (slideInVertically { height -> height } + fadeIn(tween(180)))
                        .togetherWith(slideOutVertically { height -> -height } + fadeOut(tween(180)))
                } else {
                    (slideInVertically { height -> -height } + fadeIn(tween(180)))
                        .togetherWith(slideOutVertically { height -> height } + fadeOut(tween(180)))
                }
            },
            label = "pill_counter_anim"
        ) { targetCount ->
            Text(
                text = targetCount.toString(),
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            )
        }
    }
}
