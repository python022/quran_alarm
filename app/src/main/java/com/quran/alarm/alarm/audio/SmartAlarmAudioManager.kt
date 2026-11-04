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
import com.quran.alarm.R
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
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

    private var targetVolumePercent: Int = 100

    /**
     * Starts alarm playback given a tone URI string or preset URI identifier.
     */
    fun startAlarm(soundUriString: String?, volumeLevel: Int = 100) {
        val uri = resolveSoundUri(soundUriString)
        startAlarm(customToneUri = uri, volumeLevel = volumeLevel)
    }

    /**
     * Resolves sound URI string (built-in preset, local file content URI, or system ringtone).
     */
    fun resolveSoundUri(soundUriString: String?): Uri? {
        if (soundUriString.isNullOrBlank() || soundUriString == AlarmSoundPresets.URI_DEFAULT) {
            return null
        }
        return AlarmSoundPresets.resolveUri(context, soundUriString)
    }

    /**
     * Starts alarm playback at user-configured volume on the ALARM stream.
     */
    fun startAlarm(customToneUri: Uri? = null, volumeLevel: Int = 100) {
        stopAlarm()
        requestAudioFocus()
        targetVolumePercent = volumeLevel.coerceIn(1, 100)

        try {
            val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolumeIndex = ((maxStreamVolume * targetVolumePercent + 99) / 100).coerceIn(1, maxStreamVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolumeIndex, 0)

            val alarmUri = customToneUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (alarmUri == null) {
                fallbackToToneGenerator()
                return
            }

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
                setOnErrorListener { _, _, _ ->
                    // If custom tone playback encounters an error, fallback to default alarm first
                    if (customToneUri != null) {
                        try {
                            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                            if (defaultUri != null && defaultUri != customToneUri) {
                                startAlarm(customToneUri = null, volumeLevel = targetVolumePercent)
                                return@setOnErrorListener true
                            }
                        } catch (_: Exception) {}
                    }
                    fallbackToToneGenerator()
                    true
                }
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        if (customToneUri != null) {
                            try {
                                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                if (defaultUri != null && defaultUri != customToneUri) {
                                    startAlarm(customToneUri = null, volumeLevel = targetVolumePercent)
                                    return@setOnPreparedListener
                                }
                            } catch (_: Exception) {}
                        }
                        fallbackToToneGenerator()
                    }
                }
                prepareAsync()
            }
            currentVolume = 1.0f
            _playbackState.value = AudioPlaybackState.BLARING
        } catch (e: Exception) {
            // If custom tone failed to open, try falling back to system default alarm before ToneGenerator
            if (customToneUri != null) {
                try {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    if (defaultUri != null && defaultUri != customToneUri) {
                        startAlarm(customToneUri = null, volumeLevel = volumeLevel)
                        return
                    }
                } catch (_: Exception) {}
            }
            fallbackToToneGenerator()
        }
    }

    /**
     * Previews a selected sound without looping, on STREAM_MUSIC or STREAM_ALARM.
     */
    fun previewSound(soundUriString: String?, volumeLevel: Int = 80, onComplete: () -> Unit = {}) {
        stopAlarm()
        val uri = resolveSoundUri(soundUriString)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (uri == null) {
            onComplete()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = false
                val vol = (volumeLevel.coerceIn(1, 100) / 100f)
                setVolume(vol, vol)
                setOnCompletionListener {
                    stopAlarm()
                    onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    stopAlarm()
                    onComplete()
                    true
                }
                setOnPreparedListener { mp ->
                    mp.start()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            stopAlarm()
            onComplete()
        }
    }

    private fun fallbackToToneGenerator() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolumeIndex = ((maxStreamVolume * targetVolumePercent + 99) / 100).coerceIn(1, maxStreamVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolumeIndex, 0)
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, targetVolumePercent)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
        } catch (toneEx: Exception) {
            toneEx.printStackTrace()
        }
        _playbackState.value = AudioPlaybackState.BLARING
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
            val targetVolumeIndex = ((maxStreamVolume * targetVolumePercent + 99) / 100).coerceIn(1, maxStreamVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolumeIndex, 0)
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
