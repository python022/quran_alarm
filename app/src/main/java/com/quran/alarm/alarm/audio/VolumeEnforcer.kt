package com.quran.alarm.alarm.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.provider.Settings

/**
 * Enforces maximum volume on [AudioManager.STREAM_ALARM] during alarm firing.
 *
 * Listens to volume mutations via [ContentObserver] and immediately resets the
 * alarm stream to target volume if the user tries to lower it via volume keys or quick settings.
 *
 * Employs a strict re-entrancy flag ([isSelfVolumeChange]) to prevent infinite IPC feedback loops
 * with system AudioService.
 */
class VolumeEnforcer(
    private val context: Context,
    private val handler: Handler? = null,
    private val audioManagerProvider: () -> AudioManager? = {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
) {
    var isEnforcing: Boolean = false
        private set

    var targetVolumePercent: Int = 100
        private set

    @Volatile
    private var isSelfVolumeChange: Boolean = false

    val observer: ContentObserver by lazy {
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (isEnforcing && !isSelfVolumeChange) {
                    enforceTargetVolume()
                }
            }
        }
    }

    fun startEnforcing(volumePercent: Int = 100) {
        targetVolumePercent = volumePercent.coerceIn(1, 100)
        isEnforcing = true
        enforceTargetVolume()
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopEnforcing() {
        isEnforcing = false
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun enforceTargetVolume() {
        if (isSelfVolumeChange) return
        val audioManager = audioManagerProvider() ?: return
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolume = ((maxVolume * targetVolumePercent + 99) / 100).coerceIn(1, maxVolume)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVolume < targetVolume) {
                isSelfVolumeChange = true
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
                } finally {
                    isSelfVolumeChange = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun enforceMaxVolume() {
        enforceTargetVolume()
    }
}
