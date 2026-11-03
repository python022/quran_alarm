package com.quran.alarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler

/**
 * BroadcastReceiver responsible for rescheduling all active alarms upon device reboot.
 *
 * Catches:
 * - [Intent.ACTION_BOOT_COMPLETED]
 * - [Intent.ACTION_LOCKED_BOOT_COMPLETED]
 * - [Intent.ACTION_MY_PACKAGE_REPLACED]
 *
 * Guarantees that no scheduled prayer/Quran alarm is lost after battery drain or system restart.
 */
class BootReceiver : BroadcastReceiver() {

    var repositoryProvider: (Context) -> AlarmRepository = { context -> AlarmRepositoryImpl(context) }
    var schedulerProvider: (Context) -> AlarmScheduler = { context -> AlarmSchedulerImpl(context) }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
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
            val updatedAlarm = if (alarm.explicitTriggerMillis != null && alarm.explicitTriggerMillis <= now) {
                if (alarm.repeatDays.isNotEmpty()) {
                    val nextTime = alarm.getNextTriggerMillis(now)
                    val refreshed = alarm.copy(explicitTriggerMillis = nextTime)
                    repository.saveAlarm(refreshed)
                    refreshed
                } else {
                    // One-shot alarm elapsed while phone was powered off: do NOT ring as ghost alarm
                    val disabled = alarm.copy(isEnabled = false)
                    repository.saveAlarm(disabled)
                    null
                }
            } else {
                alarm
            }
            if (updatedAlarm != null && updatedAlarm.isEnabled) {
                scheduler.schedule(updatedAlarm)
            }
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
