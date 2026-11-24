package com.quran.alarm.alarm

import com.quran.alarm.alarm.domain.model.AlarmItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmItemRecurrenceTest {

    @Test
    fun singleShot_futureTimeToday_triggersToday() {
        // Base time: 2026-11-03 10:00:00
        val baseCalendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fromMillis = baseCalendar.timeInMillis

        // Alarm at 10:30 today
        val alarm = AlarmItem(
            id = 1,
            hour = 10,
            minute = 30,
            repeatDays = emptySet()
        )

        val nextTrigger = alarm.getNextTriggerMillis(fromMillis)
        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, nextTrigger)
    }

    @Test
    fun singleShot_pastTimeToday_rollsOverToTomorrow() {
        // Base time: 2026-11-03 14:00:00
        val baseCalendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fromMillis = baseCalendar.timeInMillis

        // Alarm at 05:00 (which already passed today)
        val alarm = AlarmItem(
            id = 2,
            hour = 5,
            minute = 0,
            repeatDays = emptySet()
        )

        val nextTrigger = alarm.getNextTriggerMillis(fromMillis)
        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 4, 5, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, nextTrigger)
    }

    @Test
    fun repeatingAlarm_findsNearestConfiguredDay() {
        // 2026-11-03 is Tuesday (Calendar.TUESDAY = 3)
        val baseCalendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 3, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fromMillis = baseCalendar.timeInMillis

        // Repeat only on Thursday (Calendar.THURSDAY = 5) at 06:00
        val alarm = AlarmItem(
            id = 3,
            hour = 6,
            minute = 0,
            repeatDays = setOf(Calendar.THURSDAY)
        )

        val nextTrigger = alarm.getNextTriggerMillis(fromMillis)
        val resultCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }

        assertEquals(Calendar.THURSDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(6, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertTrue(nextTrigger > fromMillis)
    }

    @Test
    fun explicitTriggerMillis_takesPrecedenceWhenInFuture() {
        val now = 100_000L
        val explicitFuture = 150_000L

        val alarm = AlarmItem(
            id = 4,
            hour = 4,
            minute = 0,
            explicitTriggerMillis = explicitFuture
        )

        assertEquals(explicitFuture, alarm.getNextTriggerMillis(now))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHour_throwsException() {
        AlarmItem(id = 5, hour = 24, minute = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMinute_throwsException() {
        AlarmItem(id = 6, hour = 5, minute = 60)
    }
}

