package com.quran.alarm.alarm

import com.quran.alarm.alarm.domain.model.AlarmItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmItemTest {

    @Test
    fun validAlarmItem_initializationSucceeds() {
        val now = System.currentTimeMillis() + 60_000
        val alarm = AlarmItem(
            id = 1,
            timeInMillis = now,
            label = "صلاة الفجر والورد القرآني",
            isEnabled = true,
            targetPage = 1
        )

        assertEquals(1, alarm.id)
        assertEquals(now, alarm.timeInMillis)
        assertEquals("صلاة الفجر والورد القرآني", alarm.label)
        assertTrue(alarm.isEnabled)
        assertEquals(1, alarm.targetPage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAlarmId_throwsException() {
        AlarmItem(
            id = -1,
            timeInMillis = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTargetPage_zero_throwsException() {
        AlarmItem(
            id = 1,
            timeInMillis = System.currentTimeMillis(),
            targetPage = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTargetPage_exceeds604_throwsException() {
        AlarmItem(
            id = 1,
            timeInMillis = System.currentTimeMillis(),
            targetPage = 605
        )
    }

    @Test
    fun targetPage_withinValidMushafRange() {
        val alarm1 = AlarmItem(id = 1, timeInMillis = 1000L, targetPage = 1)
        val alarm604 = AlarmItem(id = 2, timeInMillis = 2000L, targetPage = 604)

        assertEquals(1, alarm1.targetPage)
        assertEquals(604, alarm604.targetPage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRepeatDays_negative_throwsException() {
        AlarmItem(
            id = 1,
            hour = 5,
            minute = 30,
            repeatDays = setOf(-1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRepeatDays_exceeds7_throwsException() {
        AlarmItem(
            id = 1,
            hour = 5,
            minute = 30,
            repeatDays = setOf(8)
        )
    }
}
