package com.quran.alarm.alarm

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import com.quran.alarm.alarm.audio.AlarmVibratorManager
import com.quran.alarm.alarm.audio.VolumeEnforcer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VibrationAndAudioEnforcerTest {

    @Test
    fun vibratorManager_initialStateIsNotVibrating() {
        val manager = AlarmVibratorManager(
            context = RuntimeEnvironment.getApplication(),
            vibratorProvider = { null }
        )
        assertFalse(manager.isVibrating)
    }

    @Test
    fun volumeEnforcer_initialStateIsNotEnforcing() {
        val enforcer = VolumeEnforcer(
            context = RuntimeEnvironment.getApplication(),
            audioManagerProvider = { null }
        )
        assertFalse(enforcer.isEnforcing)
    }

    @Test
    fun volumeEnforcer_startEnforcing_setsTargetVolumePercentage() {
        val enforcer = VolumeEnforcer(
            context = RuntimeEnvironment.getApplication(),
            audioManagerProvider = { null }
        )
        enforcer.startEnforcing(volumePercent = 45)
        assertTrue(enforcer.isEnforcing)
        assertEquals(45, enforcer.targetVolumePercent)

        enforcer.stopEnforcing()
        assertFalse(enforcer.isEnforcing)
    }

    @Test
    fun volumeEnforcer_clampsInvalidVolumePercentages() {
        val enforcer = VolumeEnforcer(
            context = RuntimeEnvironment.getApplication(),
            audioManagerProvider = { null }
        )
        enforcer.startEnforcing(volumePercent = -10)
        assertEquals(1, enforcer.targetVolumePercent)

        enforcer.startEnforcing(volumePercent = 250)
        assertEquals(100, enforcer.targetVolumePercent)
    }

    @Test
    fun volumeEnforcer_restoresOriginalAlarmStreamVolumeOnStop() {
        val context = RuntimeEnvironment.getApplication()
        val sysAudio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sysAudio.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0)

        val enforcer = VolumeEnforcer(context)
        enforcer.startEnforcing(volumePercent = 100)
        assertTrue(sysAudio.getStreamVolume(AudioManager.STREAM_ALARM) > 2)

        enforcer.stopEnforcing()
        assertEquals(2, sysAudio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test
    fun verifyVibratePermissionDeclaredInManifest() {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
            ?: error("AndroidManifest.xml must exist")
        val content = manifestFile.readText()
        assertTrue("android.permission.VIBRATE must be declared in AndroidManifest.xml", content.contains("android.permission.VIBRATE"))
    }
}

