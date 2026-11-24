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
        AlarmForegroundService.resetGlobalStateForTesting()
    }

    @After
    fun tearDown() {
        AlarmForegroundService.resetGlobalStateForTesting()
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

    @Test
    fun service_persistsAndRestoresAyahModeMetadataOnStickyRestart() {
        val sessionStore = com.quran.alarm.alarm.data.storage.AlarmSessionStore.fromContext(context)
        sessionStore.clearSession()

        val controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        val service = controller.get()

        val startIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START_ALARM
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, 77)
            putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, "البقرة")
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, 2)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 1)
            putExtra(AlarmForegroundService.EXTRA_VOLUME_LEVEL, 80)
            putExtra(AlarmForegroundService.EXTRA_IS_VIBRATE, false)
            putExtra(AlarmForegroundService.EXTRA_WIRD_SURAH_NUMBER, 2)
            putExtra(AlarmForegroundService.EXTRA_START_AYAH, 1)
            putExtra(AlarmForegroundService.EXTRA_DAILY_AYAH_COUNT, 5)
        }
        service.onStartCommand(startIntent, 0, 1)

        val silenceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_SILENCE_RINGING
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, 77)
            putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, "البقرة")
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, 2)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 1)
        }
        service.onStartCommand(silenceIntent, 0, 2)

        val restored = sessionStore.loadSession()
        org.junit.Assert.assertNotNull(restored)
        assertEquals(80, restored!!.volumeLevel)
        assertFalse(restored.isVibrate)
        assertEquals(2, restored.wirdSurahNumber)
        assertEquals(1, restored.startAyah)
        assertEquals(5, restored.dailyAyahCount)

        // Simulate START_STICKY restart (intent == null)
        service.onStartCommand(null, 0, 3)
        assertTrue(AlarmForegroundService.isReadingSessionActiveGlobally)
        assertEquals(77, AlarmForegroundService.activeSessionAlarmId)

        // Colliding second alarm during active reading session must not clobber alarm 77
        val collidingIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START_ALARM
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, 88)
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, 10)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 2)
        }
        service.onStartCommand(collidingIntent, 0, 4)

        assertEquals(77, AlarmForegroundService.activeSessionAlarmId)
        assertTrue(AlarmForegroundService.isReadingSessionActiveGlobally)
        assertFalse(AlarmForegroundService.isAlarmRingingGlobally)
        assertEquals(77, sessionStore.loadSession()?.alarmId)

        sessionStore.clearSession()
        controller.destroy()
    }

    @Test
    fun collidingSecondAlarm_duringRingingStage_doesNotClobberFirstAlarmSession() {
        val sessionStore = com.quran.alarm.alarm.data.storage.AlarmSessionStore.fromContext(context)
        sessionStore.clearSession()

        val controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        val service = controller.get()

        val firstStart = AlarmAlertIntentFactory.buildStartServiceIntent(
            context = context,
            alarmId = 101,
            label = "الأول",
            targetPage = 10,
            startPage = 10,
            pageCount = 2,
            volumeLevel = 80,
            isVibrate = true,
            soundUri = null
        )
        service.onStartCommand(firstStart, 0, 1)

        val secondStart = AlarmAlertIntentFactory.buildStartServiceIntent(
            context = context,
            alarmId = 202,
            label = "الثاني",
            targetPage = 50,
            startPage = 50,
            pageCount = 3,
            volumeLevel = 80,
            isVibrate = true,
            soundUri = null
        )
        service.onStartCommand(secondStart, 0, 2)

        assertEquals(101, AlarmForegroundService.activeSessionAlarmId)
        assertTrue(AlarmForegroundService.isAlarmRingingGlobally)
        assertEquals(101, sessionStore.loadSession()?.alarmId)

        sessionStore.clearSession()
        controller.destroy()
    }

    @Test
    fun previewTrigger_doesNotPersistSessionOrActivateGlobalLock() {
        val sessionStore = com.quran.alarm.alarm.data.storage.AlarmSessionStore.fromContext(context)
        sessionStore.clearSession()

        val controller = Robolectric.buildService(AlarmForegroundService::class.java).create()
        val service = controller.get()

        val previewStart = AlarmAlertIntentFactory.buildStartServiceIntent(
            context = context,
            alarmId = AlarmAlertIntentFactory.PREVIEW_ALARM_ID,
            label = "معاينة",
            targetPage = 1,
            startPage = 1,
            pageCount = 1,
            volumeLevel = 50,
            isVibrate = false,
            soundUri = null
        )
        service.onStartCommand(previewStart, 0, 1)

        assertFalse("Preview must not activate global lock", AlarmForegroundService.isSessionLockActive())
        assertEquals(null, sessionStore.loadSession())

        val previewSilence = AlarmAlertIntentFactory.buildSilenceServiceIntent(
            context = context,
            alarmId = AlarmAlertIntentFactory.PREVIEW_ALARM_ID,
            label = "معاينة",
            startPage = 1,
            pageCount = 1
        )
        service.onStartCommand(previewSilence, 0, 2)

        assertFalse("Preview silence must not activate reading session lock", AlarmForegroundService.isSessionLockActive())
        assertEquals(null, sessionStore.loadSession())

        controller.destroy()
    }
}

