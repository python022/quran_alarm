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
 * @property soundTitle Optional human-readable title of the selected sound (preset name or filename).
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
    val snoozeTriggerMillis: Long? = null,
    val isSequential: Boolean = true,
    val soundTitle: String? = null,
    val wirdSurahNumber: Int? = null,
    val startAyah: Int? = null,
    val dailyAyahCount: Int? = null
) {
    companion object {
        const val MAX_LABEL_LENGTH = 100
    }
    init {
        require(id >= 0) { "Alarm ID must be non-negative" }
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(targetPage in 1..604) { "Target page must be between 1 and 604" }
        require(startPage in 1..604) { "Start page must be between 1 and 604" }
        require(pageCount in 1..604) { "Page count must be between 1 and 604" }
        require(volumeLevel in 1..100) { "Volume level must be between 1 and 100" }
        require(repeatDays.all { it in Calendar.SUNDAY..Calendar.SATURDAY }) {
            "Repeat days must contain valid Calendar day constants (SUNDAY..SATURDAY)"
        }
        if (wirdSurahNumber != null) {
            require(wirdSurahNumber in 1..QuranStructure.TOTAL_SURAHS) {
                "Wird Surah number must be between 1 and ${QuranStructure.TOTAL_SURAHS}"
            }
            val maxAyahs = QuranStructure.getSurahByNumber(wirdSurahNumber).ayahCount
            if (startAyah != null) {
                require(startAyah in 1..maxAyahs) {
                    "Start ayah must be between 1 and $maxAyahs for Surah $wirdSurahNumber"
                }
            }
            if (dailyAyahCount != null) {
                require(dailyAyahCount in 1..maxAyahs) {
                    "Daily ayah count must be between 1 and $maxAyahs for Surah $wirdSurahNumber"
                }
            }
        }
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
        cal = Calendar.getInstance().apply { this.timeInMillis = timeInMillis },
        label = label,
        isEnabled = isEnabled,
        targetPage = targetPage
    )

    private constructor(
        id: Int,
        cal: Calendar,
        label: String,
        isEnabled: Boolean,
        targetPage: Int
    ) : this(
        id = id,
        hour = cal.get(Calendar.HOUR_OF_DAY),
        minute = cal.get(Calendar.MINUTE),
        repeatDays = emptySet(),
        isEnabled = isEnabled,
        isVibrationEnabled = true,
        label = label,
        targetPage = targetPage,
        startPage = targetPage,
        pageCount = 5,
        volumeLevel = 100,
        soundUri = null,
        explicitTriggerMillis = cal.timeInMillis
    )

    /**
     * Whether this alarm uses single-Surah daily Ayah progression instead of whole-page progression.
     */
    val isAyahMode: Boolean
        get() = wirdSurahNumber != null && dailyAyahCount != null

    /**
     * Concluding Ayah number for today's reading session when [isAyahMode] is active.
     */
    val endAyah: Int?
        get() = QuranStructure.computeEndAyah(wirdSurahNumber, startAyah, dailyAyahCount)

    /**
     * Calculates the concluding page of today's reading session.
     */
    val endPage: Int
        get() = QuranStructure.computeEndPage(startPage, pageCount)

    /**
     * Automatically advances the start page (or start ayah in single-Surah Ayah mode) to the next daily portion
     * upon session completion.
     */
    fun advanceToNextWird(): AlarmItem {
        if (!isSequential) {
            return this
        }
        if (isAyahMode) {
            val surah = QuranStructure.getSurahByNumber(wirdSurahNumber!!)
            val currentStart = (startAyah ?: 1).coerceIn(1, surah.ayahCount)
            val currentEnd = endAyah ?: currentStart
            val nextStartAyah = if (currentEnd >= surah.ayahCount) 1 else currentEnd + 1
            return copy(
                startAyah = nextStartAyah,
                explicitTriggerMillis = null,
                snoozeTriggerMillis = null
            )
        }
        val safeStart = startPage.coerceIn(1, 604)
        val safeCount = pageCount.coerceAtLeast(1)
        val nextStart = Math.floorMod(safeStart + safeCount - 1, 604) + 1
        return copy(
            startPage = nextStart,
            targetPage = nextStart,
            explicitTriggerMillis = null,
            snoozeTriggerMillis = null
        )
    }

    /**
     * Checks whether a one-shot alarm's scheduled time has already elapsed as of [nowMillis].
     * Returns false when [explicitTriggerMillis] is null so overnight one-shot alarms are not
     * falsely treated as expired across midnight.
     */
    fun isOneShotExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (repeatDays.isNotEmpty()) return false
        return explicitTriggerMillis != null && explicitTriggerMillis <= nowMillis
    }

    /**
     * Returns the earliest upcoming trigger timestamp considering both the primary schedule
     * and any active snooze trigger.
     */
    fun getEffectiveNextTriggerMillis(fromMillis: Long = System.currentTimeMillis()): Long {
        val primary = getNextTriggerMillis(fromMillis)
        val snooze = snoozeTriggerMillis?.takeIf { it > fromMillis }
        return if (snooze != null) minOf(primary, snooze) else primary
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
                applyTimeOfDay(target)
            }
            return target.timeInMillis
        }

        for (dayOffset in 0..7) {
            val candidate = (target.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                applyTimeOfDay(this)
            }
            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            if (repeatDays.contains(dayOfWeek)) {
                if (candidate.after(now)) {
                    return candidate.timeInMillis
                }
            }
        }

        target.add(Calendar.DAY_OF_YEAR, 7)
        applyTimeOfDay(target)
        return target.timeInMillis
    }

    private fun applyTimeOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
}
