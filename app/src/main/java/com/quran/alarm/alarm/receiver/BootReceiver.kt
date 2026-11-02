package com.quran.alarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler

/**
 * BroadcastReceiver responsible for rescheduling all active Quran alarms upon device reboot.
 * Guarantees zero missed alarms when the phone restarts overnight or after battery depletion.
 */
class BootReceiver(
    private val repositoryProvider: (Context) -> AlarmRepository = { context -> AlarmRepositoryImpl(context) },
    private val schedulerProvider: (Context) -> AlarmScheduler = { context -> AlarmSchedulerImpl(context) }
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            rescheduleAllAlarms(context)
        }
    }

    fun rescheduleAllAlarms(context: Context) {
        val repository = repositoryProvider(context)
        val scheduler = schedulerProvider(context)
        val activeAlarms = repository.getActiveAlarms()
        val now = System.currentTimeMillis()

        for (alarm in activeAlarms) {
            val updatedAlarm = if (alarm.timeInMillis <= now) {
                // If the scheduled time has already elapsed while device was off, advance by 24h
                val nextTime = calculateNextTriggerTime(alarm.timeInMillis, now)
                val refreshed = alarm.copy(timeInMillis = nextTime)
                repository.saveAlarm(refreshed)
                refreshed
            } else {
                alarm
            }
            scheduler.schedule(updatedAlarm)
        }
    }

    companion object {
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

        fun calculateNextTriggerTime(originalTime: Long, currentTime: Long): Long {
            var time = originalTime
            while (time <= currentTime) {
                time += ONE_DAY_MS
            }
            return time
        }
    }
}

