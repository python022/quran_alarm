package com.quran.alarm.alarm.receiver

import android.content.Context
import android.content.Intent
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmReceiverTest {

    private lateinit var context: Context
    private lateinit var fakeRepo: FakeTestAlarmRepository
    private lateinit var fakeScheduler: FakeTestAlarmScheduler
    private lateinit var receiver: AlarmReceiver

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fakeRepo = FakeTestAlarmRepository()
        fakeScheduler = FakeTestAlarmScheduler()

        receiver = AlarmReceiver().apply {
            repositoryProvider = { fakeRepo }
            schedulerProvider = { fakeScheduler }
        }
    }

    @Test
    fun repeatingAlarm_onTrigger_immediatelyReschedulesNextOccurrence() {
        val repeatingAlarm = AlarmItem(
            id = 201,
            hour = 5,
            minute = 30,
            repeatDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY),
            isEnabled = true,
            label = "صلاة الفجر المتكررة"
        )
        fakeRepo.saveAlarm(repeatingAlarm)

        val triggerIntent = Intent(AlarmReceiver.ACTION_TRIGGER_ALARM).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, 201)
        }

        receiver.onReceive(context, triggerIntent)

        // The repeating alarm must be rescheduled immediately in AlarmScheduler
        assertTrue("Repeating alarm must be rescheduled in AlarmScheduler", fakeScheduler.scheduled.any { it.id == 201 })
        val refreshed = fakeRepo.getAlarmById(201)
        assertNotNull(refreshed)
        assertTrue("Alarm must remain enabled", refreshed!!.isEnabled)
        assertNotNull("Must have a future explicit trigger timestamp", refreshed.explicitTriggerMillis)
    }

    @Test
    fun oneShotAlarm_onTrigger_isMarkedDisabledInRepository() {
        val oneShotAlarm = AlarmItem(
            id = 301,
            hour = 6,
            minute = 0,
            repeatDays = emptySet(),
            isEnabled = true,
            label = "منبه فردي"
        )
        fakeRepo.saveAlarm(oneShotAlarm)

        val triggerIntent = Intent(AlarmReceiver.ACTION_TRIGGER_ALARM).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, 301)
        }

        receiver.onReceive(context, triggerIntent)

        val saved = fakeRepo.getAlarmById(301)
        assertNotNull(saved)
        assertFalse("One-shot alarm must be marked disabled after firing to prevent ghost alarms", saved!!.isEnabled)
    }

    @Test
    fun repeatingAlarm_withStaleFutureExplicitTrigger_recomputesNextScheduledOccurrence() {
        val staleSnoozeTimestamp = System.currentTimeMillis() + 600_000L
        val repeatingAlarm = AlarmItem(
            id = 202,
            hour = 5,
            minute = 30,
            repeatDays = setOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            ),
            isEnabled = true,
            explicitTriggerMillis = staleSnoozeTimestamp
        )
        fakeRepo.saveAlarm(repeatingAlarm)

        val triggerIntent = Intent(AlarmReceiver.ACTION_TRIGGER_ALARM).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, 202)
        }
        receiver.onReceive(context, triggerIntent)

        val refreshed = fakeRepo.getAlarmById(202)
        assertNotNull(refreshed)
        assertNotNull(refreshed!!.explicitTriggerMillis)
        assertTrue(
            "Stale explicitTriggerMillis must be replaced with true next repeating time",
            refreshed.explicitTriggerMillis != staleSnoozeTimestamp
        )
    }

    @Test
    fun deletedAlarm_onTrigger_doesNotStartServiceOrActivity_andCancelsSchedule() {
        val shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        val triggerIntent = Intent(AlarmReceiver.ACTION_TRIGGER_ALARM).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, 999)
        }

        receiver.onReceive(context, triggerIntent)

        assertTrue("Deleted alarm must cancel stale schedule", fakeScheduler.cancelled.contains(999))
        org.junit.Assert.assertNull("Deleted ghost alarm must not start foreground service", shadowApp.nextStartedService)
        org.junit.Assert.assertNull("Deleted ghost alarm must not start alert activity", shadowApp.nextStartedActivity)
    }

    @Test
    fun disabledAlarm_onTrigger_doesNotStartServiceOrActivity_andCancelsSchedule() {
        val disabledAlarm = AlarmItem(
            id = 404,
            hour = 6,
            minute = 0,
            isEnabled = false
        )
        fakeRepo.saveAlarm(disabledAlarm)
        val shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        val triggerIntent = Intent(AlarmReceiver.ACTION_TRIGGER_ALARM).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, 404)
        }

        receiver.onReceive(context, triggerIntent)

        assertTrue("Disabled alarm must cancel stale schedule", fakeScheduler.cancelled.contains(404))
        org.junit.Assert.assertNull("Disabled ghost alarm must not start foreground service", shadowApp.nextStartedService)
        org.junit.Assert.assertNull("Disabled ghost alarm must not start alert activity", shadowApp.nextStartedActivity)
    }

    private class FakeTestAlarmRepository : AlarmRepository {
        val alarms = mutableMapOf<Int, AlarmItem>()
        override fun getAllAlarms(): List<AlarmItem> = alarms.values.toList()
        override fun getActiveAlarms(): List<AlarmItem> = alarms.values.filter { it.isEnabled }
        override fun getAlarmById(id: Int): AlarmItem? = alarms[id]
        override fun saveAlarm(alarm: AlarmItem) { alarms[alarm.id] = alarm }
        override fun deleteAlarm(id: Int) { alarms.remove(id) }
    }

    private class FakeTestAlarmScheduler : AlarmScheduler {
        val scheduled = mutableListOf<AlarmItem>()
        val cancelled = mutableListOf<Int>()
        override fun schedule(item: AlarmItem) { scheduled.add(item) }
        override fun cancel(item: AlarmItem) { cancel(item.id) }
        override fun cancel(alarmId: Int) {
            cancelled.add(alarmId)
            scheduled.removeIf { it.id == alarmId }
        }
    }
}
