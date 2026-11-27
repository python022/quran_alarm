package com.quran.alarm.alarm.domain.repository

import com.quran.alarm.alarm.domain.model.AlarmItem

/**
 * Repository interface for persisting and retrieving scheduled alarms across system reboots.
 */
interface AlarmRepository {
    fun saveAlarm(alarm: AlarmItem)
    fun saveAlarms(alarms: List<AlarmItem>) {
        alarms.forEach(::saveAlarm)
    }
    fun getAllAlarms(): List<AlarmItem>
    fun getActiveAlarms(): List<AlarmItem>
    fun getAlarmById(id: Int): AlarmItem?
    fun deleteAlarm(id: Int)
    fun cleanupOrphanedCustomSounds() {}
}
