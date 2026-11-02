package com.quran.alarm.alarm.domain.model

/**
 * Immutable model representing a scheduled Quran Alarm.
 *
 * @property id Unique identifier for the alarm.
 * @property timeInMillis Scheduled epoch trigger time in milliseconds.
 * @property label Human-readable label (e.g., "صلاة الفجر والورد القرآني").
 * @property isEnabled Whether this alarm is active and scheduled.
 * @property targetPage The Mushaf page (1..604) assigned for this reading session.
 */
data class AlarmItem(
    val id: Int,
    val timeInMillis: Long,
    val label: String = "ورد القرآن الكريم",
    val isEnabled: Boolean = true,
    val targetPage: Int = 1
) {
    init {
        require(id >= 0) { "Alarm ID must be non-negative" }
        require(targetPage in 1..604) { "Target page must be between 1 and 604" }
    }
}

