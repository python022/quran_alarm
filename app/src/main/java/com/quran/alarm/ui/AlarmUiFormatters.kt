package com.quran.alarm.ui

import java.util.Calendar

/**
 * Pure formatters for Alarm UI presentation in Arabic.
 */
object AlarmUiFormatters {

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

    fun formatNextAlarmCountdown(triggerMillis: Long, fromMillis: Long = System.currentTimeMillis()): String {
        val diffMs = triggerMillis - fromMillis
        if (diffMs <= 0) return "المنبه الآن"

        val totalMinutes = (diffMs / 60_000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours == 0 && minutes <= 1 -> "المنبه خلال أقل من دقيقة"
            hours == 0 -> "المنبه خلال $minutes دقيقة"
            minutes == 0 -> "المنبه خلال $hours ساعة"
            else -> "المنبه خلال $hours ساعة و $minutes دقيقة"
        }
    }

    fun formatCurrentDate(timeMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val dayNames = mapOf(
            Calendar.SATURDAY to "السبت",
            Calendar.SUNDAY to "الأحد",
            Calendar.MONDAY to "الاثنين",
            Calendar.TUESDAY to "الثلاثاء",
            Calendar.WEDNESDAY to "الأربعاء",
            Calendar.THURSDAY to "الخميس",
            Calendar.FRIDAY to "الجمعة"
        )
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
