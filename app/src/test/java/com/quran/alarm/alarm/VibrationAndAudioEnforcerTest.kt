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
import java.io.File

class VibrationAndAudioEnforcerTest {

    @Test
    fun vibratorManager_initialStateIsNotVibrating() {
        val manager = AlarmVibratorManager(
            context = android.app.Application(),
            vibratorProvider = { null }
        )
        assertFalse(manager.isVibrating)
    }

    @Test
    fun volumeEnforcer_initialStateIsNotEnforcing() {
        val enforcer = VolumeEnforcer(
            context = android.app.Application(),
            audioManagerProvider = { null }
        )
        assertFalse(enforcer.isEnforcing)
    }

    @Test
    fun volumeEnforcer_startEnforcing_setsTargetVolumePercentage() {
        val enforcer = VolumeEnforcer(
            context = android.app.Application(),
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
            context = android.app.Application(),
            audioManagerProvider = { null }
        )
        enforcer.startEnforcing(volumePercent = -10)
        assertEquals(1, enforcer.targetVolumePercent)

        enforcer.startEnforcing(volumePercent = 250)
        assertEquals(100, enforcer.targetVolumePercent)
    }

    @Test
    fun verifyVibratePermissionDeclaredInManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue("android.permission.VIBRATE must be declared in AndroidManifest.xml", content.contains("android.permission.VIBRATE"))
        }
    }
}

