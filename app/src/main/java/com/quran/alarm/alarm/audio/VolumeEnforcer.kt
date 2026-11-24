package com.quran.alarm.alarm.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/**
 * Enforces configured volume on [AudioManager.STREAM_ALARM] during alarm firing.
 *
 * Listens to volume mutations via the public SDK [ContentObserver] on
 * [Settings.System.CONTENT_URI] and immediately resets the alarm stream to target
 * volume if the user tries to lower it via volume keys or quick settings.
 *
 * Employs a debounce window and hardware-cap detection to prevent infinite IPC
 * feedback loops with system AudioService or Bluetooth volume limits.
 */
class VolumeEnforcer(
    private val context: Context,
    private val handler: Handler? = runCatching { Handler(Looper.getMainLooper()) }.getOrNull(),
    private val audioManagerProvider: () -> AudioManager? = {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    },
    private val elapsedRealtimeProvider: () -> Long = {
        runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
    }
) {
    @Volatile
    var isEnforcing: Boolean = false
        private set

    @Volatile
    var targetVolumePercent: Int = 100
        private set

    @Volatile
    private var isSelfVolumeChange: Boolean = false

    @Volatile
    private var lastSelfSetElapsedMs: Long = 0L

    @Volatile
    private var originalAlarmVolume: Int? = null

    @Volatile
    private var cappedMaxVolume: Int? = null

    companion object {
        private const val SELF_CHANGE_DEBOUNCE_MS = 250L
    }

    val observer: ContentObserver by lazy {
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (!isEnforcing || isSelfVolumeChange) return
                val now = elapsedRealtimeProvider()
                if (now > 0L && now - lastSelfSetElapsedMs < SELF_CHANGE_DEBOUNCE_MS) {
                    return
                }
                // Filter out unrelated system settings changes (brightness, rotation, etc.)
                val segment = uri?.lastPathSegment
                if (segment != null && uri != Settings.System.CONTENT_URI &&
                    !segment.contains("volume", ignoreCase = true)
                ) {
                    return
                }
                enforceTargetVolume()
            }
        }
    }

    fun startEnforcing(volumePercent: Int = 100) {
        targetVolumePercent = volumePercent.coerceIn(1, 100)
        cappedMaxVolume = null
        val audioManager = audioManagerProvider()
        if (audioManager != null && originalAlarmVolume == null) {
            runCatching {
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }
        }
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
        cappedMaxVolume = null
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val savedOriginal = originalAlarmVolume
        originalAlarmVolume = null
        if (savedOriginal != null) {
            val audioManager = audioManagerProvider()
            if (audioManager != null) {
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedOriginal, 0)
                }
            }
        }
    }

    fun enforceTargetVolume() {
        if (!isEnforcing || isSelfVolumeChange) return
        val audioManager = audioManagerProvider() ?: return
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val desiredTarget = ((maxVolume * targetVolumePercent + 99) / 100).coerceIn(1, maxVolume)
            val effectiveTarget = cappedMaxVolume?.let { minOf(desiredTarget, it) } ?: desiredTarget
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVolume < effectiveTarget) {
                isSelfVolumeChange = true
                lastSelfSetElapsedMs = elapsedRealtimeProvider()
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, effectiveTarget, 0)
                    val actualAfter = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (actualAfter < effectiveTarget && actualAfter <= currentVolume) {
                        // System or Bluetooth headset capped volume below target: avoid infinite IPC loop
                        cappedMaxVolume = actualAfter
                    }
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
