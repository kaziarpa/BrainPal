package com.brainpal.quitscrolling.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brainpal.quitscrolling.data.model.SocialPlatform

@Composable
fun PlatformBrandIcon(
    platform: SocialPlatform,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        when (platform) {
            SocialPlatform.INSTAGRAM -> {
                // Gradient squircle background
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF833AB4),
                            Color(0xFFE1306C),
                            Color(0xFFF77737),
                            Color(0xFFFCAF45)
                        ),
                        start = Offset(w * 0.85f, h * 0.1f),
                        end = Offset(w * 0.15f, h * 0.9f)
                    ),
                    cornerRadius = CornerRadius(w * 0.28f, h * 0.28f)
                )
                // Inner camera frame
                val strokeW = w * 0.075f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.22f, h * 0.22f),
                    size = Size(w * 0.56f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.16f, h * 0.16f),
                    style = Stroke(width = strokeW)
                )
                // Camera lens circle
                drawCircle(
                    color = Color.White,
                    radius = w * 0.135f,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeW)
                )
                // Flash dot
                drawCircle(
                    color = Color.White,
                    radius = w * 0.04f,
                    center = Offset(w * 0.65f, h * 0.35f)
                )
            }

            SocialPlatform.YOUTUBE -> {
                // Red rounded pill rectangle
                drawRoundRect(
                    color = Color(0xFFFF0000),
                    topLeft = Offset(w * 0.06f, h * 0.16f),
                    size = Size(w * 0.88f, h * 0.68f),
                    cornerRadius = CornerRadius(w * 0.22f, h * 0.22f)
                )
                // White play triangle
                val triangle = Path().apply {
                    moveTo(w * 0.41f, h * 0.35f)
                    lineTo(w * 0.65f, cy)
                    lineTo(w * 0.41f, h * 0.65f)
                    close()
                }
                drawPath(path = triangle, color = Color.White, style = Fill)
            }

            SocialPlatform.FACEBOOK -> {
                // Facebook Blue circle
                drawCircle(
                    color = Color(0xFF1877F2),
                    radius = w * 0.46f,
                    center = Offset(cx, cy)
                )
                // Crisp 'f' stem & crossbar
                val fStroke = w * 0.115f
                val fPath = Path().apply {
                    moveTo(w * 0.62f, h * 0.26f)
                    lineTo(w * 0.55f, h * 0.26f)
                    cubicTo(
                        w * 0.48f, h * 0.26f,
                        w * 0.47f, h * 0.32f,
                        w * 0.47f, h * 0.40f
                    )
                    lineTo(w * 0.47f, h * 0.86f)
                }
                drawPath(
                    path = fPath,
                    color = Color.White,
                    style = Stroke(width = fStroke, cap = StrokeCap.Round)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.36f, h * 0.52f),
                    end = Offset(w * 0.61f, h * 0.52f),
                    strokeWidth = fStroke * 0.9f,
                    cap = StrokeCap.Round
                )
            }

            SocialPlatform.X_TWITTER -> {
                // Charcoal-black circle with subtle border
                drawCircle(
                    color = Color(0xFF16161C),
                    radius = w * 0.46f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFF343442),
                    radius = w * 0.46f,
                    center = Offset(cx, cy),
                    style = Stroke(width = w * 0.04f)
                )
                // Crisp X strokes
                val xStroke = w * 0.085f
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.30f, h * 0.28f),
                    end = Offset(w * 0.70f, h * 0.72f),
                    strokeWidth = xStroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.70f, h * 0.28f),
                    end = Offset(w * 0.30f, h * 0.72f),
                    strokeWidth = xStroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
