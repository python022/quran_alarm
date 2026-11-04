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
import org.robolectric.annotation.Config

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
    fun initialPlaybackState_isIdle() {
        assertEquals(AudioPlaybackState.IDLE, audioManager.playbackState.value)
    }
}
