package com.quran.alarm.alarm.domain.scheduler

import com.quran.alarm.alarm.domain.model.AlarmItem

/**
 * Domain interface for scheduling and cancelling high-reliability Quran alarms.
 */
interface AlarmScheduler {
    /**
     * Schedules an alarm with exact timing using [android.app.AlarmManager.setAlarmClock].
     * Guarantees delivery even in deepest Doze mode.
     */
    fun schedule(item: AlarmItem)

    /**
     * Cancels an existing scheduled alarm.
     */
    fun cancel(item: AlarmItem)

    /**
     * Cancels an existing scheduled alarm by its unique ID.
     */
    fun cancel(alarmId: Int) {
        // Default implementation for backward compatibility with mock implementations
    }
}
