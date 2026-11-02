package com.quran.alarm.alarm

import com.quran.alarm.alarm.receiver.AlarmReceiver
import com.quran.alarm.alarm.service.AlarmForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlarmServiceConstantsTest {

    @Test
    fun verifyActionConstants() {
        assertEquals("com.quran.alarm.ACTION_START_ALARM", AlarmForegroundService.ACTION_START_ALARM)
        assertEquals("com.quran.alarm.ACTION_STOP_ALARM", AlarmForegroundService.ACTION_STOP_ALARM)
        assertEquals("com.quran.alarm.ACTION_TRIGGER_ALARM", AlarmReceiver.ACTION_TRIGGER_ALARM)
    }

    @Test
    fun verifyNotificationChannelMetadata() {
        assertEquals("quran_alarm_channel", AlarmForegroundService.CHANNEL_ID)
        assertEquals("منبه القرآن الكريم", AlarmForegroundService.CHANNEL_NAME)
        assertEquals(1001, AlarmForegroundService.NOTIFICATION_ID)
    }

    @Test
    fun verifyServiceDeclaredInManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue(
                "AlarmForegroundService must be declared in AndroidManifest",
                content.contains("AlarmForegroundService")
            )
            assertTrue(
                "foregroundServiceType must be mediaPlayback",
                content.contains("mediaPlayback")
            )
        }
    }
}

