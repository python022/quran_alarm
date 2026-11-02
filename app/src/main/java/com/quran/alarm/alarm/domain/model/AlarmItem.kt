package com.quran.alarm.alarm.domain.model

import java.util.Calendar

/**
 * Immutable domain model representing a Quran Alarm.
 *
 * Supports single-shot alarms, recurring day-of-week alarms, custom labels,
 * vibration settings, and target Mushaf page.
 *
 * @property id Unique identifier for the alarm (non-negative).
 * @property hour Hour of day in 24-hour format (0..23).
 * @property minute Minute of hour (0..59).
 * @property repeatDays Set of Calendar day-of-week constants (e.g., [Calendar.SUNDAY]..[Calendar.SATURDAY]).
 *                      If empty, the alarm triggers once on the nearest occurrence.
 * @property isEnabled Whether the alarm is currently active and scheduled.
 * @property isVibrationEnabled Whether vibration is activated during alarm ringing.
 * @property label Human-readable description (e.g., "صلاة الفجر والورد القرآني").
 * @property targetPage Mushaf page number (1..604).
 * @property explicitTriggerMillis Optional explicit epoch timestamp (used for quick tests, snooze, or exact triggers).
 */
data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int> = emptySet(),
    val isEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val label: String = "ورد القرآن الكريم",
    val targetPage: Int = 1,
    val explicitTriggerMillis: Long? = null
) {
    init {
        require(id >= 0) { "Alarm ID must be non-negative" }
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(targetPage in 1..604) { "Target page must be between 1 and 604" }
    }

    /**
     * Compatibility constructor for explicit epoch timestamp alarms.
     */
    constructor(
        id: Int,
        timeInMillis: Long,
        label: String = "ورد القرآن الكريم",
        isEnabled: Boolean = true,
        targetPage: Int = 1
    ) : this(
        id = id,
        hour = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }.get(Calendar.HOUR_OF_DAY),
        minute = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }.get(Calendar.MINUTE),
        repeatDays = emptySet(),
        isEnabled = isEnabled,
        isVibrationEnabled = true,
        label = label,
        targetPage = targetPage,
        explicitTriggerMillis = timeInMillis
    )

    /**
     * Epoch timestamp in milliseconds for backward compatibility.
     */
    val timeInMillis: Long
        get() = getNextTriggerMillis()

    /**
     * Calculates the next epoch timestamp (in milliseconds) when this alarm should fire,
     * calculated strictly relative to [fromMillis].
     */
    fun getNextTriggerMillis(fromMillis: Long = System.currentTimeMillis()): Long {
        if (explicitTriggerMillis != null && explicitTriggerMillis > fromMillis) {
            return explicitTriggerMillis
        }

        val now = Calendar.getInstance().apply { this.timeInMillis = fromMillis }
        val target = Calendar.getInstance().apply {
            this.timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays.isEmpty()) {
            // Once-only alarm: if target time is in the past, roll over to tomorrow
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        // Repeating alarm: find earliest matching day of week that is strictly in the future
        for (dayOffset in 0..7) {
            val candidate = (target.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            if (repeatDays.contains(dayOfWeek)) {
                if (candidate.after(now)) {
                    return candidate.timeInMillis
                }
            }
        }

        target.add(Calendar.DAY_OF_YEAR, 7)
        return target.timeInMillis
    }
}
