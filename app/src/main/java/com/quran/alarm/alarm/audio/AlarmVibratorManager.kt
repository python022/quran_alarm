package com.quran.alarm.alarm.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Manages high-intensity haptic vibration loops for alarm wake-up.
 *
 * Uses [VibratorManager] on Android 12+ (API 31+) and legacy [Vibrator] on older APIs.
 * Executes a high-energy alert waveform repeating infinitely until cancelled.
 */
class AlarmVibratorManager(
    private val context: Context,
    private val vibratorProvider: () -> Vibrator? = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
) {
    private val pattern = longArrayOf(0, 600, 250, 600, 250, 1000, 400)
    private val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0)

    var isVibrating: Boolean = false
        private set

    fun startVibration() {
        val vibrator = vibratorProvider() ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(pattern, amplitudes, 0)
                } else {
                    VibrationEffect.createWaveform(pattern, 0)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
            isVibrating = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopVibration() {
        try {
            vibratorProvider()?.cancel()
            isVibrating = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

