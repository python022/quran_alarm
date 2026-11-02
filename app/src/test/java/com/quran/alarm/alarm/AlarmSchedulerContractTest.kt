package com.quran.alarm.alarm

import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlarmSchedulerContractTest {

    private class FakeAlarmScheduler : AlarmScheduler {
        val scheduledAlarms = mutableListOf<AlarmItem>()
        val cancelledAlarms = mutableListOf<AlarmItem>()

        override fun schedule(item: AlarmItem) {
            if (item.isEnabled) {
                scheduledAlarms.add(item)
            } else {
                cancel(item)
            }
        }

        override fun cancel(item: AlarmItem) {
            cancelledAlarms.add(item)
            scheduledAlarms.removeAll { it.id == item.id }
        }
    }

    private lateinit var scheduler: FakeAlarmScheduler

    @Before
    fun setUp() {
        scheduler = FakeAlarmScheduler()
    }

    @Test
    fun scheduleActiveAlarm_isRecorded() {
        val alarm = AlarmItem(id = 10, timeInMillis = 50000L, isEnabled = true)
        scheduler.schedule(alarm)

        assertEquals(1, scheduler.scheduledAlarms.size)
        assertEquals(alarm, scheduler.scheduledAlarms.first())
    }

    @Test
    fun scheduleDisabledAlarm_routesToCancellation() {
        val alarm = AlarmItem(id = 11, timeInMillis = 50000L, isEnabled = false)
        scheduler.schedule(alarm)

        assertTrue(scheduler.scheduledAlarms.isEmpty())
        assertEquals(1, scheduler.cancelledAlarms.size)
        assertEquals(alarm, scheduler.cancelledAlarms.first())
    }

    @Test
    fun cancelExistingAlarm_removesFromScheduledList() {
        val alarm = AlarmItem(id = 12, timeInMillis = 50000L, isEnabled = true)
        scheduler.schedule(alarm)
        assertEquals(1, scheduler.scheduledAlarms.size)

        scheduler.cancel(alarm)
        assertTrue(scheduler.scheduledAlarms.isEmpty())
        assertEquals(1, scheduler.cancelledAlarms.size)
    }
}

