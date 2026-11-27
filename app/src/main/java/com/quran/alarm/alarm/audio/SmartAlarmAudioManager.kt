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
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    STOPPED
}

/**
 * Smart audio manager that guarantees alarm sound playback on [AudioManager.STREAM_ALARM].
 *
 * - Plays alarm sound at configured volume on [AudioManager.STREAM_ALARM].
 * - Supports optional external stream-volume ownership (e.g. [VolumeEnforcer]) to avoid competing mutations.
 * - Hardware fallback via [ToneGenerator] if audio files/URIs fail.
 */
class SmartAlarmAudioManager(
    private val context: Context,
    externalScope: CoroutineScope? = null
) {
    private val ownedJob: Job? = if (externalScope == null) SupervisorJob() else null
    private val scope: CoroutineScope = externalScope ?: CoroutineScope(Dispatchers.Main + ownedJob!!)
    private var fallbackAttempted = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var fadeJob: Job? = null
    private var previewTimerJob: Job? = null

    private val _playbackState = MutableStateFlow(AudioPlaybackState.IDLE)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    @Volatile
    private var playbackGeneration: Long = 0L
    private var currentVolume = 1.0f

    private var targetVolumePercent: Int = 100
    private var originalAlarmStreamVolume: Int? = null
    private var managingStreamVolume: Boolean = false

    /**
     * Starts alarm playback given a tone URI string or preset URI identifier.
     */
    fun startAlarm(
        soundUriString: String?,
        volumeLevel: Int = 100,
        manageSystemStreamVolume: Boolean = true
    ) {
        val uri = resolveSoundUri(soundUriString)
        val rawResId = AlarmSoundPresets.getRawResId(soundUriString)
        startAlarm(
            customToneUri = uri,
            volumeLevel = volumeLevel,
            manageSystemStreamVolume = manageSystemStreamVolume,
            rawResId = rawResId
        )
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
    fun startAlarm(
        customToneUri: Uri? = null,
        volumeLevel: Int = 100,
        manageSystemStreamVolume: Boolean = true,
        rawResId: Int? = null
    ) {
        if (customToneUri != null || rawResId != null) {
            fallbackAttempted = false
        }
        stopAlarm()
        val generation = ++playbackGeneration
        requestAudioFocus()
        targetVolumePercent = volumeLevel.coerceIn(1, 100)
        managingStreamVolume = manageSystemStreamVolume

        try {
            if (manageSystemStreamVolume) {
                applyAlarmStreamVolumeIfNeeded()
            }

            val alarmUri = customToneUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (alarmUri == null && rawResId == null) {
                fallbackToToneGenerator(manageSystemStreamVolume)
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(createAlarmAudioAttributes())
                configureDataSource(this, alarmUri, rawResId)
                isLooping = true
                setVolume(1.0f, 1.0f)
                setOnErrorListener { _, _, _ ->
                    scope.launch {
                        if (generation != playbackGeneration || _playbackState.value == AudioPlaybackState.STOPPED) {
                            return@launch
                        }
                        if (!fallbackFromCustomTone(alarmUri, targetVolumePercent, manageSystemStreamVolume)) {
                            if (generation == playbackGeneration && _playbackState.value != AudioPlaybackState.STOPPED) {
                                fallbackToToneGenerator(manageSystemStreamVolume)
                            }
                        }
                    }
                    true
                }
                setOnPreparedListener { mp ->
                    if (generation != playbackGeneration || _playbackState.value == AudioPlaybackState.STOPPED) {
                        runCatching {
                            mp.reset()
                            mp.release()
                        }
                        return@setOnPreparedListener
                    }
                    try {
                        mp.start()
                    } catch (_: Exception) {
                        if (generation == playbackGeneration && _playbackState.value != AudioPlaybackState.STOPPED) {
                            if (!fallbackFromCustomTone(alarmUri, targetVolumePercent, manageSystemStreamVolume)) {
                                fallbackToToneGenerator(manageSystemStreamVolume)
                            }
                        }
                    }
                }
                prepareAsync()
            }
            currentVolume = 1.0f
            _playbackState.value = AudioPlaybackState.BLARING
        } catch (_: Exception) {
            if (!fallbackFromCustomTone(customToneUri, volumeLevel, manageSystemStreamVolume)) {
                fallbackToToneGenerator(manageSystemStreamVolume)
            }
        }
    }

    private fun fallbackFromCustomTone(
        customToneUri: Uri?,
        volumeLevel: Int,
        manageSystemStreamVolume: Boolean
    ): Boolean {
        if (customToneUri == null || fallbackAttempted) return false
        fallbackAttempted = true
        return try {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (defaultUri != null && defaultUri != customToneUri) {
                startAlarm(
                    customToneUri = null,
                    volumeLevel = volumeLevel,
                    manageSystemStreamVolume = manageSystemStreamVolume,
                    rawResId = null
                )
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Dynamically updates the volume on the active MediaPlayer without interrupting playback.
     */
    fun updateVolume(volumeLevel: Int) {
        targetVolumePercent = volumeLevel.coerceIn(1, 100)
        if (managingStreamVolume) {
            applyAlarmStreamVolumeIfNeeded()
        } else {
            val vol = (targetVolumePercent / 100f)
            currentVolume = vol
            try {
                mediaPlayer?.setVolume(vol, vol)
            } catch (_: Exception) {}
        }
    }

    /**
     * Previews a selected sound without looping, on STREAM_ALARM.
     */
    fun previewSound(
        soundUriString: String?,
        volumeLevel: Int = 80,
        durationMs: Long? = null,
        onError: () -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        stopAlarm()
        val generation = ++playbackGeneration
        val rawResId = AlarmSoundPresets.getRawResId(soundUriString)
        val uri = resolveSoundUri(soundUriString)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (uri == null && rawResId == null) {
            onError()
            onComplete()
            return
        }

        targetVolumePercent = volumeLevel.coerceIn(1, 100)
        managingStreamVolume = true

        try {
            applyAlarmStreamVolumeIfNeeded()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(createAlarmAudioAttributes())
                configureDataSource(this, uri, rawResId)
                isLooping = false
                setVolume(1.0f, 1.0f)
                currentVolume = 1.0f
                setOnCompletionListener {
                    if (generation == playbackGeneration) {
                        stopAlarm()
                        onComplete()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (generation == playbackGeneration) {
                        stopAlarm()
                        onError()
                        onComplete()
                    }
                    true
                }
                setOnPreparedListener { mp ->
                    if (generation != playbackGeneration || _playbackState.value == AudioPlaybackState.STOPPED) {
                        runCatching {
                            mp.reset()
                            mp.release()
                        }
                        return@setOnPreparedListener
                    }
                    try {
                        mp.start()
                        _playbackState.value = AudioPlaybackState.BLARING
                        if (durationMs != null && durationMs > 0) {
                            previewTimerJob?.cancel()
                            previewTimerJob = scope.launch {
                                delay(durationMs)
                                if (generation == playbackGeneration && mediaPlayer == mp) {
                                    stopAlarm()
                                    onComplete()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        if (generation == playbackGeneration) {
                            stopAlarm()
                            onError()
                            onComplete()
                        }
                    }
                }
                _playbackState.value = AudioPlaybackState.BLARING
                prepareAsync()
            }
        } catch (_: Exception) {
            stopAlarm()
            onError()
            onComplete()
        }
    }

    private fun configureDataSource(player: MediaPlayer, uri: Uri?, rawResId: Int?) {
        if (uri != null) {
            val uriSucceeded = runCatching {
                player.setDataSource(context, uri)
            }.isSuccess
            if (uriSucceeded) return
        }
        if (rawResId != null) {
            context.resources.openRawResourceFd(rawResId)?.use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                return
            }
        }
        throw IllegalStateException("Unable to configure audio data source for uri=$uri rawResId=$rawResId")
    }

    private fun createAlarmAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun applyAlarmStreamVolumeIfNeeded() {
        runCatching {
            val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolumeIndex = ((maxStreamVolume * targetVolumePercent + 99) / 100).coerceIn(1, maxStreamVolume)
            val currentStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentStreamVolume != targetVolumeIndex) {
                if (originalAlarmStreamVolume == null) {
                    originalAlarmStreamVolume = currentStreamVolume
                }
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolumeIndex, 0)
            }
        }
    }

    private fun fallbackToToneGenerator(manageSystemStreamVolume: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            if (manageSystemStreamVolume) {
                applyAlarmStreamVolumeIfNeeded()
            }
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
     * Completely silences and releases media player, tone generator, audio focus,
     * and restores the user's original alarm stream volume if this manager modified it.
     */
    fun stopAlarm() {
        playbackGeneration++
        fadeJob?.cancel()
        previewTimerJob?.cancel()
        previewTimerJob = null
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null

        val savedVolume = originalAlarmStreamVolume
        originalAlarmStreamVolume = null
        val shouldRestore = managingStreamVolume
        managingStreamVolume = false
        if (shouldRestore && savedVolume != null) {
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            }
        }

        currentVolume = 0.0f
        abandonAudioFocus()
        _playbackState.value = AudioPlaybackState.STOPPED
    }

    /**
     * Releases all audio resources and cancels any internally created coroutine scope.
     */
    fun destroy() {
        stopAlarm()
        ownedJob?.cancel()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = createAlarmAudioAttributes()
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
