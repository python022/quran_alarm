package com.quran.alarm.alarm.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.quran.alarm.MainActivity
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.receiver.AlarmReceiver

/**
 * Android implementation of [AlarmScheduler] using [AlarmManager.setAlarmClock].
 *
 * `setAlarmClock` is the highest-priority alarm type in Android:
 * - Bypasses all Doze mode and App Standby restrictions without requiring battery optimization whitelisting.
 * - Shows an alarm icon on the user's status bar and lockscreen.
 * - Guarantees precision to the exact second.
 */
class AlarmSchedulerImpl(
    private val context: Context,
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
) : AlarmScheduler {

    override fun schedule(item: AlarmItem) {
        if (!item.isEnabled) {
            cancel(item)
            return
        }

        val triggerTime = item.timeInMillis
        val operationIntent = createOperationIntent(item)
        val showIntent = createShowIntent(item)

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, operationIntent)
    }

    override fun cancel(item: AlarmItem) {
        val operationIntent = createOperationIntent(item)
        alarmManager.cancel(operationIntent)
        operationIntent.cancel()
    }

    fun createOperationIntent(item: AlarmItem): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, item.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, item.label)
            putExtra(AlarmReceiver.EXTRA_TARGET_PAGE, item.targetPage)
        }
        return PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createShowIntent(item: AlarmItem): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, item.id)
        }
        return PendingIntent.getActivity(
            context,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

