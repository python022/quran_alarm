package com.quran.alarm.alarm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the alarm audio playback lifecycle.
 */
enum class AudioPlaybackState {
    IDLE,
    BLARING,
    MUTED,
    STOPPED
}

/**
 * Smart audio manager that guarantees alarm sound playback on [AudioManager.STREAM_ALARM].
 *
 * Implements the sacred reading lifecycle:
 * - Blaring at maximum volume to awaken the sleeper.
 * - Hardware fallback via [ToneGenerator] if audio files/URIs fail.
 * - Smooth auto-muting when the user attentively reads the Quran lines.
 * - Instant penalty screaming at maximum volume if user looks away.
 */
class SmartAlarmAudioManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var fadeJob: Job? = null

    private val _playbackState = MutableStateFlow(AudioPlaybackState.IDLE)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private var currentVolume = 1.0f

    /**
     * Starts alarm playback at maximum volume on the ALARM stream.
     */
    fun startAlarm(customToneUri: Uri? = null) {
        stopAlarm()
        requestAudioFocus()

        try {
            // Force stream volume to max
            val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxStreamVolume, 0)

            val alarmUri = customToneUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            currentVolume = 1.0f
            _playbackState.value = AudioPlaybackState.BLARING
        } catch (e: Exception) {
            e.printStackTrace()
            // High-reliability Fallback: continuous alert tone via ToneGenerator
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
            } catch (toneEx: Exception) {
                toneEx.printStackTrace()
            }
            _playbackState.value = AudioPlaybackState.BLARING
        }
    }

    /**
     * Smoothly fades out the volume to 0 over [durationMs] so the reader can recite in peace.
     */
    fun smoothMute(durationMs: Long = 400L) {
        fadeJob?.cancel()
        if (_playbackState.value != AudioPlaybackState.BLARING) return

        fadeJob = scope.launch {
            val steps = 10
            val interval = durationMs / steps
            for (i in (steps - 1) downTo 0) {
                val vol = i.toFloat() / steps.toFloat()
                try {
                    mediaPlayer?.setVolume(vol, vol)
                } catch (ignored: Exception) {}
                currentVolume = vol
                delay(interval)
            }
            try {
                mediaPlayer?.setVolume(0.0f, 0.0f)
                toneGenerator?.stopTone()
            } catch (ignored: Exception) {}
            currentVolume = 0.0f
            _playbackState.value = AudioPlaybackState.MUTED
        }
    }

    /**
     * Instantly restores maximum volume when user distraction or looking away is detected.
     */
    fun instantScream() {
        fadeJob?.cancel()
        currentVolume = 1.0f
        try {
            val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxStreamVolume, 0)
            mediaPlayer?.setVolume(1.0f, 1.0f)
            if (mediaPlayer == null && toneGenerator != null) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
            }
        } catch (ignored: Exception) {}
        _playbackState.value = AudioPlaybackState.BLARING
    }

    /**
     * Completely silences and releases media player, tone generator, and audio focus.
     */
    fun stopAlarm() {
        fadeJob?.cancel()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (ignored: Exception) {}
        mediaPlayer = null

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (ignored: Exception) {}
        toneGenerator = null

        currentVolume = 0.0f
        abandonAudioFocus()
        _playbackState.value = AudioPlaybackState.STOPPED
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
