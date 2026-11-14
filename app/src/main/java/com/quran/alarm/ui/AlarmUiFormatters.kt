package com.quran.alarm.ui

import java.util.Calendar

/**
 * Pure formatters for Alarm UI presentation in Arabic.
 */
object AlarmUiFormatters {

    fun getHour12(hour24: Int): Int {
        return when (hour24 % 24) {
            0 -> 12
            in 1..12 -> hour24
            else -> hour24 - 12
        }
    }

    fun isAm(hour24: Int): Boolean {
        return (hour24 % 24) < 12
    }

    fun toHour24(hour12: Int, isAm: Boolean): Int {
        val h = hour12.coerceIn(1, 12)
        return when {
            isAm && h == 12 -> 0
            isAm -> h
            !isAm && h == 12 -> 12
            else -> h + 12
        }
    }

    fun formatTime12Hour(hour: Int, minute: Int): String {
        val isAm = hour < 12
        val hour12 = when (hour) {
            0 -> 12
            in 1..12 -> hour
            else -> hour - 12
        }
        val minuteFormatted = String.format("%02d", minute)
        val period = if (isAm) "ص" else "م"
        return "$hour12:$minuteFormatted $period"
    }

    fun formatRepeatDays(repeatDays: Set<Int>): String {
        if (repeatDays.isEmpty()) return "مرة واحدة"
        if (repeatDays.size == 7) return "كل يوم"

        val weekdays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)
        if (repeatDays == weekdays) return "وسط أيام الأسبوع"

        val weekend = setOf(Calendar.FRIDAY, Calendar.SATURDAY)
        if (repeatDays == weekend) return "نهاية الأسبوع"

        val dayNames = mapOf(
            Calendar.SATURDAY to "السبت",
            Calendar.SUNDAY to "الأحد",
            Calendar.MONDAY to "الاثنين",
            Calendar.TUESDAY to "الثلاثاء",
            Calendar.WEDNESDAY to "الأربعاء",
            Calendar.THURSDAY to "الخميس",
            Calendar.FRIDAY to "الجمعة"
        )

        val orderedDays = listOf(
            Calendar.SATURDAY,
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )

        return orderedDays.filter { repeatDays.contains(it) }.mapNotNull { dayNames[it] }.joinToString("، ")
    }

    val DAY_NAMES = mapOf(
        Calendar.SATURDAY to "السبت",
        Calendar.SUNDAY to "الأحد",
        Calendar.MONDAY to "الاثنين",
        Calendar.TUESDAY to "الثلاثاء",
        Calendar.WEDNESDAY to "الأربعاء",
        Calendar.THURSDAY to "الخميس",
        Calendar.FRIDAY to "الجمعة"
    )

    fun getDayName(dayOfWeek: Int): String? = DAY_NAMES[dayOfWeek]

    fun formatNextAlarmCountdown(
        triggerMillis: Long,
        fromMillis: Long = System.currentTimeMillis(),
        targetDayName: String? = null
    ): String {
        if (triggerMillis <= fromMillis) return "المنبه الآن"

        val fromCal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerCal = Calendar.getInstance().apply {
            timeInMillis = triggerMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val cleanDiffMs = triggerCal.timeInMillis - fromCal.timeInMillis
        val totalMinutes = if (cleanDiffMs > 0) {
            (cleanDiffMs / 60_000L).toInt()
        } else {
            0
        }

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        val prefix = if (!targetDayName.isNullOrBlank()) "المنبه يوم $targetDayName خلال" else "المنبه خلال"

        return when {
            hours == 0 && totalMinutes == 0 -> "$prefix أقل من دقيقة"
            hours == 0 && totalMinutes == 1 -> "$prefix دقيقة واحدة"
            hours == 0 && totalMinutes == 2 -> "$prefix دقيقتين"
            hours == 0 -> "$prefix $minutes دقيقة"
            minutes == 0 -> "$prefix $hours ساعة"
            else -> "$prefix $hours ساعة و $minutes دقيقة"
        }
    }

    fun formatCurrentDate(timeMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val dayNames = DAY_NAMES
        val monthNames = arrayOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
        val dayOfWeek = dayNames[cal.get(Calendar.DAY_OF_WEEK)] ?: ""
        val dayOfMonth = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        val month = monthNames[cal.get(Calendar.MONTH)]
        return "$dayOfWeek $dayOfMonth $month"
    }

    fun formatCurrentDigitalTime(timeMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
}
