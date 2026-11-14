package com.quran.alarm.alarm

import com.quran.alarm.ui.AlarmUiFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun hour12Conversion_handlesAllCasesAccurately() {
        // Midnight (12 AM) -> 0
        assertEquals(12, AlarmUiFormatters.getHour12(0))
        assertTrue(AlarmUiFormatters.isAm(0))
        assertEquals(0, AlarmUiFormatters.toHour24(12, true))

        // 5 AM -> 5
        assertEquals(5, AlarmUiFormatters.getHour12(5))
        assertTrue(AlarmUiFormatters.isAm(5))
        assertEquals(5, AlarmUiFormatters.toHour24(5, true))

        // Noon (12 PM) -> 12
        assertEquals(12, AlarmUiFormatters.getHour12(12))
        assertFalse(AlarmUiFormatters.isAm(12))
        assertEquals(12, AlarmUiFormatters.toHour24(12, false))

        // 5 PM -> 17
        assertEquals(5, AlarmUiFormatters.getHour12(17))
        assertFalse(AlarmUiFormatters.isAm(17))
        assertEquals(17, AlarmUiFormatters.toHour24(5, false))

        // 11 PM -> 23
        assertEquals(11, AlarmUiFormatters.getHour12(23))
        assertFalse(AlarmUiFormatters.isAm(23))
        assertEquals(23, AlarmUiFormatters.toHour24(11, false))
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
        val now = 1_200_000L // Clean 20-minute multiple from epoch (second = 0, ms = 0)
        assertEquals("المنبه الآن", AlarmUiFormatters.formatNextAlarmCountdown(now, now))
        assertEquals("المنبه خلال أقل من دقيقة", AlarmUiFormatters.formatNextAlarmCountdown(now + 30_000L, now))
        assertEquals("المنبه خلال دقيقة واحدة", AlarmUiFormatters.formatNextAlarmCountdown(now + 60_000L, now))
        assertEquals("المنبه خلال دقيقتين", AlarmUiFormatters.formatNextAlarmCountdown(now + 120_000L, now))
        assertEquals("المنبه خلال 45 دقيقة", AlarmUiFormatters.formatNextAlarmCountdown(now + 45 * 60_000L, now))
        assertEquals("المنبه خلال 2 ساعة", AlarmUiFormatters.formatNextAlarmCountdown(now + 120 * 60_000L, now))
        assertEquals("المنبه خلال 3 ساعة و 25 دقيقة", AlarmUiFormatters.formatNextAlarmCountdown(now + (3 * 60 + 25) * 60_000L, now))
    }

    @Test
    fun formatNextAlarmCountdown_cleanMinuteDifference_matchesUserExpectation() {
        // e.g. Phone clock is 02:33:45
        val phoneTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 33)
            set(Calendar.SECOND, 45)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Target set to 02:34:00 (1 clean minute ahead) -> "المنبه خلال دقيقة واحدة"
        val targetNextMinute = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 34)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("المنبه خلال دقيقة واحدة", AlarmUiFormatters.formatNextAlarmCountdown(targetNextMinute, phoneTime))

        // Target set to 02:35:00 (2 clean minutes ahead) -> "المنبه خلال دقيقتين"
        val targetTwoMinutes = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 35)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("المنبه خلال دقيقتين", AlarmUiFormatters.formatNextAlarmCountdown(targetTwoMinutes, phoneTime))

        // Within same minute (e.g. 02:33:10 to 02:33:50) -> "المنبه خلال أقل من دقيقة"
        val earlierInSameMinute = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 33)
            set(Calendar.SECOND, 10)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val laterInSameMinute = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 33)
            set(Calendar.SECOND, 50)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("المنبه خلال أقل من دقيقة", AlarmUiFormatters.formatNextAlarmCountdown(laterInSameMinute, earlierInSameMinute))
    }

    @Test
    fun formatNextAlarmCountdown_withTargetDayName_includesDayName() {
        val now = 1_200_000L
        val future = now + (57 * 60 + 19) * 60_000L
        val text = AlarmUiFormatters.formatNextAlarmCountdown(
            triggerMillis = future,
            fromMillis = now,
            targetDayName = "الأحد"
        )
        assertEquals("المنبه يوم الأحد خلال 57 ساعة و 19 دقيقة", text)

        val textShort = AlarmUiFormatters.formatNextAlarmCountdown(
            triggerMillis = now + 45 * 60_000L,
            fromMillis = now,
            targetDayName = "الجمعة"
        )
        assertEquals("المنبه يوم الجمعة خلال 45 دقيقة", textShort)
    }

    @Test
    fun getDayName_returnsArabicDayNames() {
        assertEquals("السبت", AlarmUiFormatters.getDayName(Calendar.SATURDAY))
        assertEquals("الأحد", AlarmUiFormatters.getDayName(Calendar.SUNDAY))
        assertEquals("الاثنين", AlarmUiFormatters.getDayName(Calendar.MONDAY))
        assertEquals("الثلاثاء", AlarmUiFormatters.getDayName(Calendar.TUESDAY))
        assertEquals("الأربعاء", AlarmUiFormatters.getDayName(Calendar.WEDNESDAY))
        assertEquals("الخميس", AlarmUiFormatters.getDayName(Calendar.THURSDAY))
        assertEquals("الجمعة", AlarmUiFormatters.getDayName(Calendar.FRIDAY))
    }
}
