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
 * alarm stream to maximum volume if the user tries to lower it via volume keys or quick settings.
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

    val observer: ContentObserver by lazy {
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (isEnforcing) {
                    enforceMaxVolume()
                }
            }
        }
    }

    fun startEnforcing() {
        isEnforcing = true
        enforceMaxVolume()
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

    fun enforceMaxVolume() {
        val audioManager = audioManagerProvider() ?: return
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVolume < maxVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
