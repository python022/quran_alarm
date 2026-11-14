package com.quran.alarm.alarm

import android.content.Context
import android.content.Intent
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory
import com.quran.alarm.alarm.service.AlarmForegroundService
import com.quran.alarm.quran.data.AyahInfoDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSessionLockAndDirectBootTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AlarmForegroundService.isAlarmRingingGlobally = false
        AlarmForegroundService.isReadingSessionActiveGlobally = false
    }

    @After
    fun tearDown() {
        AlarmForegroundService.isAlarmRingingGlobally = false
        AlarmForegroundService.isReadingSessionActiveGlobally = false
    }

    @Test
    fun silenceRinging_preservesSessionLockWhileStoppingAlarmRinging() {
        val controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        val service = controller.get()

        val startIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START_ALARM
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, 42)
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, 40)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 6)
        }
        service.onStartCommand(startIntent, 0, 1)

        assertTrue(AlarmForegroundService.isAlarmRingingGlobally)
        assertTrue(AlarmForegroundService.isSessionLockActive())

        val silenceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_SILENCE_RINGING
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, 42)
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, 40)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 6)
        }
        service.onStartCommand(silenceIntent, 0, 2)

        assertFalse("Audio ringing flag must turn off when reading starts", AlarmForegroundService.isAlarmRingingGlobally)
        assertTrue("Reading session flag must stay ON", AlarmForegroundService.isReadingSessionActiveGlobally)
        assertTrue("Session lock must remain active during reading to block home-button bypass", AlarmForegroundService.isSessionLockActive())

        val stopIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        service.onStartCommand(stopIntent, 0, 3)

        assertFalse(AlarmForegroundService.isAlarmRingingGlobally)
        assertFalse(AlarmForegroundService.isReadingSessionActiveGlobally)
        assertFalse("Session lock must release only after completion or snooze", AlarmForegroundService.isSessionLockActive())

        controller.destroy()
    }

    @Test
    fun alertIntentFactory_buildsDistinguishableTriggerAndRelaunchIntents() {
        assertEquals(45, AlarmAlertIntentFactory.computeEndPage(startPage = 40, pageCount = 6))
        assertEquals(2, AlarmAlertIntentFactory.computeEndPage(startPage = 604, pageCount = 3))

        val freshTrigger = AlarmAlertIntentFactory.buildAlertIntent(
            context = context,
            alarmId = 10,
            label = "Fajr",
            targetPage = 40,
            startPage = 40,
            pageCount = 6,
            isFreshTrigger = true
        )
        assertTrue(freshTrigger.getBooleanExtra(AlarmAlertIntentFactory.EXTRA_IS_FRESH_TRIGGER, false))

        val relaunch = AlarmAlertIntentFactory.buildAlertIntent(
            context = context,
            alarmId = 10,
            label = "Fajr",
            targetPage = 40,
            startPage = 40,
            pageCount = 6,
            isFreshTrigger = false,
            reorderToFront = true
        )
        assertFalse(relaunch.getBooleanExtra(AlarmAlertIntentFactory.EXTRA_IS_FRESH_TRIGGER, true))
        assertTrue((relaunch.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0)
    }

    @Test
    fun ayahInfoDatabase_usesDeviceProtectedStorageForDirectBoot() {
        val storageContext = AyahInfoDatabase.resolveStorageContext(context)
        assertTrue(
            "Direct Boot awareness requires Device-Protected Storage context on API 24+",
            storageContext.isDeviceProtectedStorage
        )
    }
}

