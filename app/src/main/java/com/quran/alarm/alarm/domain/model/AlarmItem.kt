package com.quran.alarm.alarm.domain.model

import java.util.Calendar

/**
 * Immutable domain model representing a Quran Alarm with customizable Wird progression and audio settings.
 *
 * @property id Unique identifier for the alarm (non-negative).
 * @property hour Hour of day in 24-hour format (0..23).
 * @property minute Minute of hour (0..59).
 * @property repeatDays Set of Calendar day-of-week constants. If empty, the alarm triggers once.
 * @property isEnabled Whether the alarm is currently active and scheduled.
 * @property isVibrationEnabled Whether vibration is activated during alarm ringing.
 * @property label Human-readable description (e.g., "صلاة الفجر والورد القرآني").
 * @property targetPage Mushaf page number (1..604) for backward compatibility.
 * @property startPage Starting Mushaf page (1..604) for today's wird.
 * @property pageCount Number of consecutive pages assigned for the daily wird (1..50).
 * @property volumeLevel Volume level percentage (10..100%).
 * @property soundUri Optional custom tone URI string.
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
    val startPage: Int = targetPage,
    val pageCount: Int = 5,
    val volumeLevel: Int = 100,
    val soundUri: String? = null,
    val explicitTriggerMillis: Long? = null,
    val isSequential: Boolean = true
) {
    init {
        require(id >= 0) { "Alarm ID must be non-negative" }
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(targetPage in 1..604) { "Target page must be between 1 and 604" }
        require(startPage in 1..604) { "Start page must be between 1 and 604" }
        require(pageCount in 1..604) { "Page count must be between 1 and 604" }
        require(volumeLevel in 1..100) { "Volume level must be between 1 and 100" }
    }

    /**
     * Compatibility constructor for legacy timeInMillis tests and callers.
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
        startPage = targetPage,
        pageCount = 5,
        volumeLevel = 100,
        soundUri = null,
        explicitTriggerMillis = timeInMillis
    )

    /**
     * Calculates the concluding page of today's reading session.
     */
    val endPage: Int
        get() = ((startPage + pageCount - 2) % 604) + 1

    /**
     * Automatically advances the start page to the next daily portion upon session completion.
     * Wraps cleanly around page 604 (completing a Khatma back to Surah Al-Fatihah).
     */
    fun advanceToNextWird(): AlarmItem {
        if (!isSequential) {
            return this
        }
        val nextStart = ((startPage + pageCount - 1) % 604) + 1
        return copy(
            startPage = nextStart,
            targetPage = nextStart
        )
    }

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
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

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
