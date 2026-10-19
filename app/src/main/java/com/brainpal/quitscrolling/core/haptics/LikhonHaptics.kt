package com.brainpal.quitscrolling.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object LikhonHaptics {

    private fun getVibrator(context: Context): Vibrator? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    /**
     * Ultra-soft micro-tick for slider adjustments and subtle UI interactions.
     */
    fun microTick(context: Context) {
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(10L, 35))
            }
        }
    }

    /**
     * Gentle tap feedback for navigation tabs, chips, and button presses.
     */
    fun softTap(context: Context) {
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(18L, 55))
            }
        }
    }

    /**
     * Soft organic pulse when a new Reel/Video scroll is counted in real time.
     */
    fun gentleScrollCountPulse(context: Context) {
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0L, 14L, 45L, 20L),
                intArrayOf(0, 45, 0, 65),
                -1
            )
            vibrator.vibrate(effect)
        }
    }

    /**
     * Distinct gentle double-pulse when the Reel Blocker Shield intercepts a Reel/Short.
     */
    fun blockerShieldPulse(context: Context) {
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0L, 35L, 60L, 45L, 60L, 30L),
                intArrayOf(0, 95, 0, 120, 0, 80),
                -1
            )
            vibrator.vibrate(effect)
        }
    }
}
