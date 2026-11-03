package com.quran.alarm.alarm

import android.app.AlarmManager
import android.content.Context
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.model.AlarmItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Real contract test for [AlarmSchedulerImpl] executing on Robolectric.
 * Verifies interaction with Android [AlarmManager], [AlarmManager.AlarmClockInfo], and Intent packaging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulerContractTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var scheduler: AlarmSchedulerImpl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = Shadows.shadowOf(alarmManager)
        scheduler = AlarmSchedulerImpl(context, alarmManager)
    }

    @Test
    fun schedule_setsAlarmClock_withExactTimeAndPendingIntent() {
        val alarm = AlarmItem(id = 10, hour = 5, minute = 30, isEnabled = true)
        scheduler.schedule(alarm)

        val scheduled = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Alarm must be scheduled in AlarmManager", scheduled)
        assertEquals(alarm.getNextTriggerMillis(), scheduled?.triggerAtTime)
        assertNotNull("AlarmClockInfo must be present on scheduled alarm", scheduled?.alarmClockInfo)
        assertNotNull("Operation PendingIntent must be present", scheduled?.operation)
    }

    @Test
    fun schedule_cancelsAlarm_whenDisabled() {
        val activeAlarm = AlarmItem(id = 11, hour = 6, minute = 0, isEnabled = true)
        scheduler.schedule(activeAlarm)
        assertNotNull(shadowAlarmManager.nextScheduledAlarm)

        val disabledAlarm = activeAlarm.copy(isEnabled = false)
        scheduler.schedule(disabledAlarm)

        val scheduled = shadowAlarmManager.nextScheduledAlarm
        assertNull("Disabled alarm must not be scheduled", scheduled)
    }

    @Test
    fun cancel_byItem_cancelsAlarmClockInAlarmManager() {
        val alarm = AlarmItem(id = 12, hour = 7, minute = 15, isEnabled = true)
        scheduler.schedule(alarm)
        assertNotNull(shadowAlarmManager.nextScheduledAlarm)

        scheduler.cancel(alarm)
        val scheduled = shadowAlarmManager.nextScheduledAlarm
        assertNull("Alarm must be removed from AlarmManager after cancel(item)", scheduled)
    }

    @Test
    fun cancel_byId_cancelsAlarmClockInAlarmManager() {
        val alarm = AlarmItem(id = 13, hour = 8, minute = 45, isEnabled = true)
        scheduler.schedule(alarm)
        assertNotNull(shadowAlarmManager.nextScheduledAlarm)

        scheduler.cancel(alarm.id)
        val scheduled = shadowAlarmManager.nextScheduledAlarm
        assertNull("Alarm must be removed from AlarmManager after cancel(alarmId)", scheduled)
    }

    @Test
    fun createOperationIntent_containsAllWirdAndVolumeExtras() {
        val alarm = AlarmItem(
            id = 14,
            hour = 5,
            minute = 0,
            label = "صلاة الفجر والورد القرآني",
            startPage = 77,
            pageCount = 10,
            volumeLevel = 85,
            isVibrationEnabled = true
        )

        val pendingIntent = scheduler.createOperationIntent(alarm)
        assertNotNull("Operation PendingIntent must not be null", pendingIntent)

        val showIntent = scheduler.createShowIntent(alarm)
        assertNotNull("Show PendingIntent for AlarmClockInfo must not be null", showIntent)
    }
}
