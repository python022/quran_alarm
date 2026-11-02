package com.quran.alarm.alarm

import com.quran.alarm.ui.AlarmUiFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmUiFormattersTest {

    @Test
    fun formatTime12Hour_formatsCorrectly() {
        assertEquals("12:00 ص", AlarmUiFormatters.formatTime12Hour(0, 0))
        assertEquals("4:30 ص", AlarmUiFormatters.formatTime12Hour(4, 30))
        assertEquals("12:00 م", AlarmUiFormatters.formatTime12Hour(12, 0))
        assertEquals("5:15 م", AlarmUiFormatters.formatTime12Hour(17, 15))
        assertEquals("11:59 م", AlarmUiFormatters.formatTime12Hour(23, 59))
    }

    @Test
    fun formatRepeatDays_singleShot_returnsOnce() {
        assertEquals("مرة واحدة", AlarmUiFormatters.formatRepeatDays(emptySet()))
    }

    @Test
    fun formatRepeatDays_allDays_returnsEveryDay() {
        val allDays = (Calendar.SUNDAY..Calendar.SATURDAY).toSet()
        assertEquals("كل يوم", AlarmUiFormatters.formatRepeatDays(allDays))
    }

    @Test
    fun formatRepeatDays_weekdays_returnsWorkDays() {
        val weekdays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)
        assertEquals("وسط أيام الأسبوع", AlarmUiFormatters.formatRepeatDays(weekdays))
    }

    @Test
    fun formatRepeatDays_weekend_returnsWeekend() {
        val weekend = setOf(Calendar.FRIDAY, Calendar.SATURDAY)
        assertEquals("نهاية الأسبوع", AlarmUiFormatters.formatRepeatDays(weekend))
    }

    @Test
    fun formatNextAlarmCountdown_calculatesCorrectText() {
        val now = 1_000_000L
        assertEquals("المنبه خلال أقل من دقيقة", AlarmUiFormatters.formatNextAlarmCountdown(now + 30_000L, now))
        assertEquals("المنبه خلال 45 دقيقة", AlarmUiFormatters.formatNextAlarmCountdown(now + 45 * 60_000L, now))
        assertEquals("المنبه خلال 2 ساعة", AlarmUiFormatters.formatNextAlarmCountdown(now + 120 * 60_000L, now))
        assertEquals("المنبه خلال 3 ساعة و 25 دقيقة", AlarmUiFormatters.formatNextAlarmCountdown(now + (3 * 60 + 25) * 60_000L, now))
    }
}

