package com.brainpal.quitscrolling.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.brainpal.quitscrolling.ui.components.BrainRotStage

object MascotBitmapRenderer {

    fun renderMascotBitmap(reelCount: Int, sizePx: Int = 220): Bitmap {
        val stage = BrainRotStage.fromCount(reelCount)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val w = sizePx.toFloat()
        val h = sizePx.toFloat()
        val cx = w / 2f
        val cy = h * 0.50f
        val rx = w * 0.39f
        val ry = h * 0.32f
        val strokeW = (w * 0.045f).coerceAtLeast(3f)

        val baseColorInt = android.graphics.Color.argb(
            255,
            (stage.baseColor.red * 255).toInt(),
            (stage.baseColor.green * 255).toInt(),
            (stage.baseColor.blue * 255).toInt()
        )
        val lobeColorInt = android.graphics.Color.argb(
            255,
            (stage.lobeColor.red * 255).toInt(),
            (stage.lobeColor.green * 255).toInt(),
            (stage.lobeColor.blue * 255).toInt()
        )
        val outlineColorInt = android.graphics.Color.argb(
            255,
            (stage.outlineColor.red * 255).toInt(),
            (stage.outlineColor.green * 255).toInt(),
            (stage.outlineColor.blue * 255).toInt()
        )

        val brainPath = Path().apply {
            moveTo(cx, cy + ry * 0.85f)
            cubicTo(
                cx - rx * 0.55f, cy + ry * 0.95f,
                cx - rx * 1.05f, cy + ry * 0.65f,
                cx - rx * 0.94f, cy + ry * 0.16f
            )
            cubicTo(
                cx - rx * 1.12f, cy - ry * 0.25f,
                cx - rx * 0.92f, cy - ry * 0.75f,
                cx - rx * 0.54f, cy - ry * 0.82f
            )
            cubicTo(
                cx - rx * 0.38f, cy - ry * 1.08f,
                cx - rx * 0.08f, cy - ry * 1.04f,
                cx, cy - ry * 0.84f
            )
            cubicTo(
                cx + rx * 0.08f, cy - ry * 1.04f,
                cx + rx * 0.38f, cy - ry * 1.08f,
                cx + rx * 0.54f, cy - ry * 0.82f
            )
            cubicTo(
                cx + rx * 0.92f, cy - ry * 0.75f,
                cx + rx * 1.12f, cy - ry * 0.25f,
                cx + rx * 0.94f, cy + ry * 0.16f
            )
            cubicTo(
                cx + rx * 1.05f, cy + ry * 0.65f,
                cx + rx * 0.55f, cy + ry * 0.95f,
                cx, cy + ry * 0.85f
            )
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                cx, cy - ry, cx, cy + ry,
                baseColorInt, lobeColorInt,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(brainPath, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = outlineColorInt
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(brainPath, strokePaint)

        // Central fissure & side gyri
        val foldPaint = Paint(strokePaint).apply {
            strokeWidth = strokeW * 0.7f
        }
        canvas.drawLine(cx, cy - ry * 0.82f, cx, cy - ry * 0.36f, foldPaint)

        // Eyes
        val eyeOffsetX = rx * 0.34f
        val eyeY = cy + ry * 0.08f
        val eyeR = rx * 0.155f
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.parseColor("#230C14")
        }
        val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }

        listOf(cx - eyeOffsetX, cx + eyeOffsetX).forEach { ex ->
            canvas.drawCircle(ex, eyeY, eyeR, eyePaint)
            if (stage != BrainRotStage.DEAD_ROT) {
                canvas.drawCircle(ex - eyeR * 0.28f, eyeY - eyeR * 0.28f, eyeR * 0.34f, specPaint)
            } else {
                val rotPupil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = android.graphics.Color.parseColor("#E066FF")
                }
                canvas.drawCircle(ex, eyeY, eyeR * 0.28f, rotPupil)
            }
        }

        // Mouth
        val mouthY = cy + ry * 0.44f
        val mouthW = rx * 0.22f
        val mouthRect = RectF(cx - mouthW, mouthY - ry * 0.1f, cx + mouthW, mouthY + ry * 0.16f)
        when (stage) {
            BrainRotStage.FRESH -> canvas.drawArc(mouthRect, 15f, 150f, false, foldPaint)
            BrainRotStage.DRAINING -> canvas.drawLine(cx - mouthW, mouthY, cx + mouthW, mouthY, foldPaint)
            BrainRotStage.ROTTING, BrainRotStage.DEAD_ROT -> canvas.drawArc(mouthRect, 195f, 150f, false, foldPaint)
        }

        return bitmap
    }
}
