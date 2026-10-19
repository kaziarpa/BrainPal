package com.brainpal.quitscrolling.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

enum class BrainRotStage(
    val stageNumber: Int,
    val title: String,
    val subtitle: String,
    val rangeLabel: String,
    val baseColor: Color,
    val lobeColor: Color,
    val outlineColor: Color,
    val accentColor: Color
) {
    FRESH(
        stageNumber = 1,
        title = "Stage 1: Fresh Brain",
        subtitle = "Sharp, hydrated & focused",
        rangeLabel = "0 – 49 reels",
        baseColor = Color(0xFFFF9EBB),
        lobeColor = Color(0xFFFF7AA2),
        outlineColor = Color(0xFF4A1525),
        accentColor = Color(0xFFFF7B54)
    ),
    DRAINING(
        stageNumber = 2,
        title = "Stage 2: Draining",
        subtitle = "Dopamine dipping, eyelids heavy",
        rangeLabel = "50 – 99 reels",
        baseColor = Color(0xFFE58B9E),
        lobeColor = Color(0xFFC96D82),
        outlineColor = Color(0xFF3D1822),
        accentColor = Color(0xFFFF9248)
    ),
    ROTTING(
        stageNumber = 3,
        title = "Stage 3: Brain Rot",
        subtitle = "Deep fissures, dark eye bags & fog",
        rangeLabel = "100 – 199 reels",
        baseColor = Color(0xFFB8A3AA),
        lobeColor = Color(0xFF968188),
        outlineColor = Color(0xFF2E2226),
        accentColor = Color(0xFFFF5C5C)
    ),
    DEAD_ROT(
        stageNumber = 4,
        title = "Stage 4: Zombie Rot",
        subtitle = "Dehydrated ash-grey doomscroll trance",
        rangeLabel = "200+ reels",
        baseColor = Color(0xFF7D777B),
        lobeColor = Color(0xFF5E585C),
        outlineColor = Color(0xFF1C191B),
        accentColor = Color(0xFFE066FF)
    );

    companion object {
        fun fromCount(reelCount: Int): BrainRotStage = when {
            reelCount < 50 -> FRESH
            reelCount < 100 -> DRAINING
            reelCount < 200 -> ROTTING
            else -> DEAD_ROT
        }
    }
}

@Composable
fun BrainMascotCanvas(
    reelCount: Int,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false
) {
    val stage = BrainRotStage.fromCount(reelCount)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = if (compactMode) h * 0.52f else h * 0.44f
        val brainRadiusX = if (compactMode) w * 0.43f else w * 0.35f
        val brainRadiusY = if (compactMode) h * 0.37f else h * 0.27f
        val strokeW = (w * if (compactMode) 0.065f else 0.026f).coerceAtLeast(2f)

        // 1. Ambient Stage Aura (Non-compact mode)
        if (!compactMode) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stage.accentColor.copy(alpha = 0.22f),
                        stage.baseColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.48f
                ),
                radius = w * 0.48f,
                center = Offset(cx, cy)
            )

            // Ground shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(cx - brainRadiusX * 0.72f, h * 0.84f),
                size = Size(brainRadiusX * 1.44f, h * 0.07f)
            )
        }

        // 2. Legs & Arms (Non-compact mode)
        if (!compactMode) {
            val limbColor = stage.outlineColor
            val legSlouch = when (stage) {
                BrainRotStage.FRESH -> 0f
                BrainRotStage.DRAINING -> h * 0.01f
                BrainRotStage.ROTTING -> h * 0.025f
                BrainRotStage.DEAD_ROT -> h * 0.04f
            }
            val bodyBottomY = cy + brainRadiusY * 0.85f + legSlouch
            val footY = h * 0.85f

            // Left Leg
            drawLine(
                color = limbColor,
                start = Offset(cx - brainRadiusX * 0.32f, bodyBottomY),
                end = Offset(cx - brainRadiusX * 0.36f, footY),
                strokeWidth = strokeW * 1.35f,
                cap = StrokeCap.Round
            )
            // Left Shoe
            drawRoundRect(
                color = stage.lobeColor,
                topLeft = Offset(cx - brainRadiusX * 0.52f, footY - strokeW),
                size = Size(brainRadiusX * 0.28f, strokeW * 2.1f),
                cornerRadius = CornerRadius(strokeW, strokeW)
            )
            drawRoundRect(
                color = limbColor,
                topLeft = Offset(cx - brainRadiusX * 0.52f, footY - strokeW),
                size = Size(brainRadiusX * 0.28f, strokeW * 2.1f),
                cornerRadius = CornerRadius(strokeW, strokeW),
                style = Stroke(width = strokeW * 0.7f)
            )

            // Right Leg
            drawLine(
                color = limbColor,
                start = Offset(cx + brainRadiusX * 0.30f, bodyBottomY),
                end = Offset(cx + brainRadiusX * 0.34f, footY),
                strokeWidth = strokeW * 1.35f,
                cap = StrokeCap.Round
            )
            // Right Shoe
            drawRoundRect(
                color = stage.lobeColor,
                topLeft = Offset(cx + brainRadiusX * 0.24f, footY - strokeW),
                size = Size(brainRadiusX * 0.28f, strokeW * 2.1f),
                cornerRadius = CornerRadius(strokeW, strokeW)
            )
            drawRoundRect(
                color = limbColor,
                topLeft = Offset(cx + brainRadiusX * 0.24f, footY - strokeW),
                size = Size(brainRadiusX * 0.28f, strokeW * 2.1f),
                cornerRadius = CornerRadius(strokeW, strokeW),
                style = Stroke(width = strokeW * 0.7f)
            )

            // Left Arm (hanging or resting on knee)
            val leftArmPath = Path().apply {
                moveTo(cx - brainRadiusX * 0.86f, cy + brainRadiusY * 0.15f)
                quadraticTo(
                    cx - brainRadiusX * 1.15f,
                    cy + brainRadiusY * 0.65f,
                    cx - brainRadiusX * 0.75f,
                    cy + brainRadiusY * 0.95f
                )
            }
            drawPath(
                path = leftArmPath,
                color = limbColor,
                style = Stroke(width = strokeW * 1.25f, cap = StrokeCap.Round)
            )
        }

        // 3. Brain Silhouette & Lobes
        drawBrainSilhouette(
            cx = cx,
            cy = cy,
            rx = brainRadiusX,
            ry = brainRadiusY,
            stage = stage,
            strokeW = strokeW,
            compactMode = compactMode
        )

        // 4. Eyes, Eyelids, Eye Bags & Mouth
        drawFacialExpression(
            cx = cx,
            cy = cy,
            rx = brainRadiusX,
            ry = brainRadiusY,
            stage = stage,
            strokeW = strokeW,
            compactMode = compactMode
        )

        // 5. Right Arm holding glowing Smartphone (Non-compact mode)
        if (!compactMode) {
            val phoneCenterX = cx + brainRadiusX * 0.78f
            val phoneCenterY = cy + brainRadiusY * 0.58f
            val phoneW = brainRadiusX * 0.42f
            val phoneH = brainRadiusY * 0.78f

            // Right Arm curving to hold phone
            val rightArmPath = Path().apply {
                moveTo(cx + brainRadiusX * 0.78f, cy + brainRadiusY * 0.20f)
                quadraticTo(
                    cx + brainRadiusX * 1.08f,
                    cy + brainRadiusY * 0.48f,
                    phoneCenterX,
                    phoneCenterY + phoneH * 0.12f
                )
            }
            drawPath(
                path = rightArmPath,
                color = stage.outlineColor,
                style = Stroke(width = strokeW * 1.25f, cap = StrokeCap.Round)
            )

            // Screen glow cast toward face in Stage 3 & Stage 4
            if (stage == BrainRotStage.ROTTING || stage == BrainRotStage.DEAD_ROT) {
                val glowPath = Path().apply {
                    moveTo(phoneCenterX - phoneW * 0.3f, phoneCenterY - phoneH * 0.3f)
                    lineTo(cx - brainRadiusX * 0.25f, cy - brainRadiusY * 0.1f)
                    lineTo(cx + brainRadiusX * 0.25f, cy + brainRadiusY * 0.55f)
                    close()
                }
                drawPath(
                    path = glowPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF7DE2FF).copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        start = Offset(phoneCenterX, phoneCenterY),
                        end = Offset(cx, cy)
                    )
                )
            }

            // Smartphone body tilted slightly toward mascot
            rotate(degrees = -14f, pivot = Offset(phoneCenterX, phoneCenterY)) {
                drawRoundRect(
                    color = Color(0xFF1A1B24),
                    topLeft = Offset(phoneCenterX - phoneW / 2f, phoneCenterY - phoneH / 2f),
                    size = Size(phoneW, phoneH),
                    cornerRadius = CornerRadius(phoneW * 0.24f, phoneW * 0.24f)
                )
                // Glowing phone screen
                val screenGlowColor = when (stage) {
                    BrainRotStage.FRESH -> Color(0xFFFF7AA2)
                    BrainRotStage.DRAINING -> Color(0xFFFFB86C)
                    BrainRotStage.ROTTING -> Color(0xFF6BE5FF)
                    BrainRotStage.DEAD_ROT -> Color(0xFFB57CFF)
                }
                drawRoundRect(
                    color = screenGlowColor,
                    topLeft = Offset(phoneCenterX - phoneW * 0.38f, phoneCenterY - phoneH * 0.38f),
                    size = Size(phoneW * 0.76f, phoneH * 0.76f),
                    cornerRadius = CornerRadius(phoneW * 0.16f, phoneW * 0.16f)
                )
                // Phone outline
                drawRoundRect(
                    color = stage.outlineColor,
                    topLeft = Offset(phoneCenterX - phoneW / 2f, phoneCenterY - phoneH / 2f),
                    size = Size(phoneW, phoneH),
                    cornerRadius = CornerRadius(phoneW * 0.24f, phoneW * 0.24f),
                    style = Stroke(width = strokeW * 0.75f)
                )
            }
        }
    }
}

private fun DrawScope.drawBrainSilhouette(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    stage: BrainRotStage,
    strokeW: Float,
    compactMode: Boolean
) {
    // Stage 4 shrinks / shrivels slightly
    val shrinkFactor = when (stage) {
        BrainRotStage.FRESH -> 1.0f
        BrainRotStage.DRAINING -> 0.97f
        BrainRotStage.ROTTING -> 0.93f
        BrainRotStage.DEAD_ROT -> 0.88f
    }
    val wRx = rx * shrinkFactor
    val hRy = ry * shrinkFactor

    // Scalloped multi-lobe brain path
    val brainPath = Path().apply {
        // Bottom center indentation
        moveTo(cx, cy + hRy * 0.86f)
        // Bottom-left lobe
        cubicTo(
            cx - wRx * 0.55f, cy + hRy * 0.96f,
            cx - wRx * 1.05f, cy + hRy * 0.65f,
            cx - wRx * 0.94f, cy + hRy * 0.16f
        )
        // Mid-left temple lobe
        cubicTo(
            cx - wRx * 1.12f, cy - hRy * 0.25f,
            cx - wRx * 0.92f, cy - hRy * 0.75f,
            cx - wRx * 0.54f, cy - hRy * 0.82f
        )
        // Top-left crown lobe
        cubicTo(
            cx - wRx * 0.38f, cy - hRy * 1.08f,
            cx - wRx * 0.08f, cy - hRy * 1.04f,
            cx, cy - hRy * 0.84f
        )
        // Top-right crown lobe
        cubicTo(
            cx + wRx * 0.08f, cy - hRy * 1.04f,
            cx + wRx * 0.38f, cy - hRy * 1.08f,
            cx + wRx * 0.54f, cy - hRy * 0.82f
        )
        // Mid-right temple lobe
        cubicTo(
            cx + wRx * 0.92f, cy - hRy * 0.75f,
            cx + wRx * 1.12f, cy - hRy * 0.25f,
            cx + wRx * 0.94f, cy + hRy * 0.16f
        )
        // Bottom-right lobe
        cubicTo(
            cx + wRx * 1.05f, cy + hRy * 0.65f,
            cx + wRx * 0.55f, cy + hRy * 0.96f,
            cx, cy + hRy * 0.86f
        )
        close()
    }

    // Base gradient fill
    drawPath(
        path = brainPath,
        brush = Brush.verticalGradient(
            colors = listOf(stage.baseColor, stage.lobeColor),
            startY = cy - hRy,
            endY = cy + hRy
        ),
        style = Fill
    )

    // Top specular highlight for Stage 1 & 2
    if (!compactMode && (stage == BrainRotStage.FRESH || stage == BrainRotStage.DRAINING)) {
        drawOval(
            color = Color.White.copy(alpha = if (stage == BrainRotStage.FRESH) 0.32f else 0.16f),
            topLeft = Offset(cx - wRx * 0.52f, cy - hRy * 0.78f),
            size = Size(wRx * 0.38f, hRy * 0.22f)
        )
    }

    // Outer thick cartoon outline
    drawPath(
        path = brainPath,
        color = stage.outlineColor,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Brain Folds / Gyri & Fissures
    val foldStroke = (strokeW * 0.72f).coerceAtLeast(1.5f)
    val foldsPath = Path().apply {
        // Central longitudinal fissure at top
        moveTo(cx, cy - hRy * 0.84f)
        lineTo(cx, cy - hRy * 0.38f)

        // Left upper gyrus curl
        moveTo(cx - wRx * 0.65f, cy - hRy * 0.35f)
        quadraticTo(cx - wRx * 0.35f, cy - hRy * 0.58f, cx - wRx * 0.22f, cy - hRy * 0.28f)

        // Right upper gyrus curl
        moveTo(cx + wRx * 0.65f, cy - hRy * 0.35f)
        quadraticTo(cx + wRx * 0.35f, cy - hRy * 0.58f, cx + wRx * 0.22f, cy - hRy * 0.28f)

        if (!compactMode) {
            // Side temple folds
            moveTo(cx - wRx * 0.88f, cy + hRy * 0.08f)
            quadraticTo(cx - wRx * 0.64f, cy - hRy * 0.05f, cx - wRx * 0.58f, cy + hRy * 0.18f)

            moveTo(cx + wRx * 0.88f, cy + hRy * 0.08f)
            quadraticTo(cx + wRx * 0.64f, cy - hRy * 0.05f, cx + wRx * 0.58f, cy + hRy * 0.18f)
        }

        // Extra jagged fissures for Stage 3 (Rotting) & Stage 4 (Dead Rot)
        if (stage == BrainRotStage.ROTTING || stage == BrainRotStage.DEAD_ROT) {
            moveTo(cx - wRx * 0.42f, cy - hRy * 0.72f)
            lineTo(cx - wRx * 0.28f, cy - hRy * 0.52f)
            lineTo(cx - wRx * 0.44f, cy - hRy * 0.38f)

            moveTo(cx + wRx * 0.45f, cy - hRy * 0.68f)
            lineTo(cx + wRx * 0.30f, cy - hRy * 0.48f)
            lineTo(cx + wRx * 0.48f, cy - hRy * 0.32f)
        }
    }

    drawPath(
        path = foldsPath,
        color = stage.outlineColor.copy(alpha = 0.85f),
        style = Stroke(width = foldStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Stage 2 Sweat Drop on temple
    if (!compactMode && stage == BrainRotStage.DRAINING) {
        val dropX = cx + wRx * 0.74f
        val dropY = cy - hRy * 0.36f
        val dropPath = Path().apply {
            moveTo(dropX, dropY - hRy * 0.14f)
            cubicTo(
                dropX + wRx * 0.12f, dropY + hRy * 0.05f,
                dropX + wRx * 0.06f, dropY + hRy * 0.18f,
                dropX, dropY + hRy * 0.18f
            )
            cubicTo(
                dropX - wRx * 0.06f, dropY + hRy * 0.18f,
                dropX - wRx * 0.12f, dropY + hRy * 0.05f,
                dropX, dropY - hRy * 0.14f
            )
            close()
        }
        drawPath(path = dropPath, color = Color(0xFF5CE1E6))
        drawPath(
            path = dropPath,
            color = stage.outlineColor,
            style = Stroke(width = strokeW * 0.6f)
        )
    }
}

private fun DrawScope.drawFacialExpression(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    stage: BrainRotStage,
    strokeW: Float,
    compactMode: Boolean
) {
    val eyeOffsetX = rx * 0.34f
    val eyeY = cy + ry * 0.08f
    val eyeRadius = rx * (if (compactMode) 0.17f else 0.145f)

    listOf(cx - eyeOffsetX, cx + eyeOffsetX).forEach { ex ->
        // Dark eye bags for Stage 3 & Stage 4
        if (stage == BrainRotStage.ROTTING || stage == BrainRotStage.DEAD_ROT) {
            drawArc(
                color = Color(0xFF43324D).copy(alpha = 0.8f),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(ex - eyeRadius * 1.15f, eyeY - eyeRadius * 0.65f),
                size = Size(eyeRadius * 2.3f, eyeRadius * 2.15f),
                style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round)
            )
        }

        if (stage == BrainRotStage.DEAD_ROT) {
            // Stage 4: Deeply sunken hollow zombie sockets with dim pinprick pupils
            drawCircle(
                color = Color(0xFF1F1921),
                radius = eyeRadius * 1.05f,
                center = Offset(ex, eyeY)
            )
            drawCircle(
                color = Color(0xFFE066FF).copy(alpha = 0.75f),
                radius = eyeRadius * 0.28f,
                center = Offset(ex, eyeY + eyeRadius * 0.1f)
            )
        } else {
            // Stage 1-3: Expressive dark eyes
            drawCircle(
                color = Color(0xFF230C14),
                radius = eyeRadius,
                center = Offset(ex, eyeY)
            )

            // Glossy anime specular highlights
            val primarySpecRatio = if (stage == BrainRotStage.FRESH) 0.38f else 0.26f
            drawCircle(
                color = Color.White,
                radius = eyeRadius * primarySpecRatio,
                center = Offset(ex - eyeRadius * 0.28f, eyeY - eyeRadius * 0.28f)
            )
            if (stage == BrainRotStage.FRESH && !compactMode) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = eyeRadius * 0.18f,
                    center = Offset(ex + eyeRadius * 0.32f, eyeY + eyeRadius * 0.28f)
                )
            }

            // Heavy lowered upper eyelids for Stage 2 & Stage 3
            if (stage == BrainRotStage.DRAINING || stage == BrainRotStage.ROTTING) {
                val lidSweep = if (stage == BrainRotStage.DRAINING) 180f else 200f
                val lidStart = if (stage == BrainRotStage.DRAINING) 180f else 170f
                drawArc(
                    color = stage.lobeColor,
                    startAngle = lidStart,
                    sweepAngle = lidSweep,
                    useCenter = false,
                    topLeft = Offset(ex - eyeRadius * 1.05f, eyeY - eyeRadius * 1.05f),
                    size = Size(eyeRadius * 2.1f, eyeRadius * 2.1f),
                    style = Fill
                )
                drawLine(
                    color = stage.outlineColor,
                    start = Offset(ex - eyeRadius * 1.08f, eyeY - eyeRadius * 0.08f),
                    end = Offset(ex + eyeRadius * 1.08f, eyeY + if (stage == BrainRotStage.ROTTING) eyeRadius * 0.15f else -eyeRadius * 0.08f),
                    strokeWidth = strokeW * 0.85f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    // Rosy cheeks in Stage 1
    if (stage == BrainRotStage.FRESH && !compactMode) {
        drawOval(
            color = Color(0xFFFF5A8A).copy(alpha = 0.45f),
            topLeft = Offset(cx - eyeOffsetX - eyeRadius * 1.4f, eyeY + eyeRadius * 0.65f),
            size = Size(eyeRadius * 1.3f, eyeRadius * 0.65f)
        )
        drawOval(
            color = Color(0xFFFF5A8A).copy(alpha = 0.45f),
            topLeft = Offset(cx + eyeOffsetX + eyeRadius * 0.1f, eyeY + eyeRadius * 0.65f),
            size = Size(eyeRadius * 1.3f, eyeRadius * 0.65f)
        )
    }

    // Mouth expression
    if (!compactMode) {
        val mouthY = cy + ry * 0.44f
        val mouthW = rx * 0.22f
        val mouthPath = Path()
        when (stage) {
            BrainRotStage.FRESH -> {
                // Cheerful little curve smile
                mouthPath.moveTo(cx - mouthW, mouthY - ry * 0.04f)
                mouthPath.quadraticTo(cx, mouthY + ry * 0.14f, cx + mouthW, mouthY - ry * 0.04f)
            }
            BrainRotStage.DRAINING -> {
                // Flat worried line
                mouthPath.moveTo(cx - mouthW * 0.9f, mouthY + ry * 0.02f)
                mouthPath.lineTo(cx + mouthW * 0.9f, mouthY - ry * 0.02f)
            }
            BrainRotStage.ROTTING -> {
                // Downturned exhausted frown
                mouthPath.moveTo(cx - mouthW, mouthY + ry * 0.09f)
                mouthPath.quadraticTo(cx, mouthY - ry * 0.08f, cx + mouthW, mouthY + ry * 0.09f)
            }
            BrainRotStage.DEAD_ROT -> {
                // Wavy zombie grimace
                mouthPath.moveTo(cx - mouthW * 1.1f, mouthY + ry * 0.06f)
                mouthPath.cubicTo(
                    cx - mouthW * 0.35f, mouthY - ry * 0.10f,
                    cx + mouthW * 0.35f, mouthY + ry * 0.16f,
                    cx + mouthW * 1.1f, mouthY - ry * 0.02f
                )
            }
        }
        drawPath(
            path = mouthPath,
            color = stage.outlineColor,
            style = Stroke(width = strokeW * 0.9f, cap = StrokeCap.Round)
        )
    }
}
