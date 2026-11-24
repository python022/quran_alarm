package com.quran.alarm.alarm.audio

import android.content.Context
import android.net.Uri
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartAlarmAudioManagerTest {

    private lateinit var context: Context
    private lateinit var audioManager: SmartAlarmAudioManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        audioManager = SmartAlarmAudioManager(context)
    }

    @Test
    fun resolveSoundUri_nullOrDefault_returnsNull() {
        assertNull(audioManager.resolveSoundUri(null))
        assertNull(audioManager.resolveSoundUri(""))
        assertNull(audioManager.resolveSoundUri("   "))
        assertNull(audioManager.resolveSoundUri(AlarmSoundPresets.URI_DEFAULT))
    }

    @Test
    fun resolveSoundUri_builtinPresets_resolvesToAndroidResource() {
        val adhanUri = audioManager.resolveSoundUri(AlarmSoundPresets.URI_ADHAN_FAJR)
        assertNotNull(adhanUri)
        assertTrue(adhanUri.toString().startsWith("android.resource://${context.packageName}/"))

        val eidUri = audioManager.resolveSoundUri(AlarmSoundPresets.URI_EID_TAKBEER)
        assertNotNull(eidUri)
        assertTrue(eidUri.toString().startsWith("android.resource://${context.packageName}/"))

        val calmUri = audioManager.resolveSoundUri(AlarmSoundPresets.URI_CALM_TONE)
        assertNotNull(calmUri)
        assertTrue(calmUri.toString().startsWith("android.resource://${context.packageName}/"))

        val quranUri = audioManager.resolveSoundUri(AlarmSoundPresets.URI_QURAN_ALERT)
        assertNotNull(quranUri)
        assertTrue(quranUri.toString().startsWith("android.resource://${context.packageName}/"))
    }

    @Test
    fun resolveSoundUri_customContentUri_parsesDirectly() {
        val contentUriStr = "content://media/external/audio/media/456"
        val resolved = audioManager.resolveSoundUri(contentUriStr)
        assertNotNull(resolved)
        assertEquals(Uri.parse(contentUriStr), resolved)
    }

    @Test
    fun resolveSoundUri_networkUri_isRejectedByZeroNetworkLaw() {
        // Network URIs must be strictly rejected to preserve offline-only operation
        assertNull(audioManager.resolveSoundUri("http://example.com/audio.mp3"))
        assertNull(audioManager.resolveSoundUri("https://example.com/audio.mp3"))
        assertNull(audioManager.resolveSoundUri("ftp://example.com/audio.mp3"))
        assertNull(AlarmSoundPresets.resolveUri(context, "http://example.com/audio.mp3"))
        assertNull(AlarmSoundPresets.resolveUri(context, "https://example.com/audio.mp3"))
    }

    @Test
    fun alarmSoundPresets_resolveUri_defaultResolvesToSystemAlarmOrRingtone() {
        val defaultResolved = AlarmSoundPresets.resolveUri(context, null)
        assertNotNull("Default URI must resolve to system alarm or ringtone", defaultResolved)

        val defaultTagResolved = AlarmSoundPresets.resolveUri(context, AlarmSoundPresets.URI_DEFAULT)
        assertNotNull("URI_DEFAULT must resolve to system alarm or ringtone", defaultTagResolved)
    }

    @Test
    fun initialPlaybackState_isIdle() {
        assertEquals(AudioPlaybackState.IDLE, audioManager.playbackState.value)
    }

    @Test
    fun updateVolume_clampsAndExecutesSafely() {
        audioManager.updateVolume(50)
        audioManager.updateVolume(0)
        audioManager.updateVolume(150)
    }

    @Test
    fun previewSound_preparesAndStartsPlayback_withoutBeingDiscardedByStoppedState() {
        val uri = audioManager.resolveSoundUri(AlarmSoundPresets.URI_ADHAN_FAJR)!!
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, uri),
            ShadowMediaPlayer.MediaInfo(3000, 10)
        )

        var completedEarly = false
        audioManager.previewSound(
            soundUriString = AlarmSoundPresets.URI_ADHAN_FAJR,
            volumeLevel = 80
        ) {
            completedEarly = true
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(
            "previewSound must stay BLARING once prepared",
            AudioPlaybackState.BLARING,
            audioManager.playbackState.value
        )
        assertEquals(false, completedEarly)

        audioManager.stopAlarm()
        assertEquals(AudioPlaybackState.STOPPED, audioManager.playbackState.value)
    }

    @Test
    fun previewSound_withDuration_stopsAfterDuration() {
        val sysAudio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        sysAudio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 2, 0)

        audioManager.previewSound(
            soundUriString = AlarmSoundPresets.URI_CALM_TONE,
            volumeLevel = 50,
            durationMs = 500L
        )
        audioManager.stopAlarm()
        assertEquals(AudioPlaybackState.STOPPED, audioManager.playbackState.value)
        assertEquals(
            "stopAlarm must restore original STREAM_ALARM volume",
            2,
            sysAudio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
        )
    }
}

