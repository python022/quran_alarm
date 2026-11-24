package com.quran.alarm.alarm.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.quran.alarm.MainActivity
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.receiver.AlarmReceiver
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory

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

        val triggerTime = item.getNextTriggerMillis()
        val operationIntent = createOperationIntent(item)
        val showIntent = createShowIntent(item)
        scheduleExactOrFallback(triggerTime, showIntent, operationIntent)
    }

    override fun scheduleSnooze(item: AlarmItem, triggerAtMillis: Long) {
        val snoozeItem = item.copy(isEnabled = true, explicitTriggerMillis = triggerAtMillis)
        val operationIntent = createOperationIntent(
            item = snoozeItem,
            requestCodeOffset = SNOOZE_REQUEST_CODE_OFFSET,
            isSnoozeTrigger = true
        )
        val showIntent = createShowIntent(snoozeItem)
        scheduleExactOrFallback(triggerAtMillis, showIntent, operationIntent)
    }

    private fun scheduleExactOrFallback(
        triggerTime: Long,
        showIntent: PendingIntent,
        operationIntent: PendingIntent
    ) {
        val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)

        if (canUseExactAlarm) {
            try {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, operationIntent)
                return
            } catch (se: SecurityException) {
                Log.w("AlarmSchedulerImpl", "Exact alarm permission revoked; falling back to inexact alarm", se)
            }
        }

        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, operationIntent)
        }.onFailure { error ->
            Log.e("AlarmSchedulerImpl", "Fallback alarm scheduling failed", error)
        }
    }

    override fun cancel(item: AlarmItem) {
        cancel(item.id)
    }

    override fun cancel(alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        for (offset in listOf(PRIMARY_REQUEST_CODE_OFFSET, SNOOZE_REQUEST_CODE_OFFSET)) {
            val operationIntent = PendingIntent.getBroadcast(
                context,
                alarmId + offset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(operationIntent)
            operationIntent.cancel()
        }
    }

    fun createOperationIntent(
        item: AlarmItem,
        requestCodeOffset: Int = PRIMARY_REQUEST_CODE_OFFSET,
        isSnoozeTrigger: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, item.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, item.label)
            putExtra(AlarmReceiver.EXTRA_TARGET_PAGE, item.targetPage)
            putExtra(AlarmReceiver.EXTRA_START_PAGE, item.startPage)
            putExtra(AlarmReceiver.EXTRA_PAGE_COUNT, item.pageCount)
            putExtra(AlarmReceiver.EXTRA_VOLUME_LEVEL, item.volumeLevel)
            putExtra(AlarmReceiver.EXTRA_IS_VIBRATE, item.isVibrationEnabled)
            putExtra(AlarmReceiver.EXTRA_SOUND_URI, item.soundUri)
            putExtra(AlarmReceiver.EXTRA_IS_SNOOZE_TRIGGER, isSnoozeTrigger)
            if (item.wirdSurahNumber != null && item.dailyAyahCount != null) {
                putExtra(AlarmAlertIntentFactory.EXTRA_WIRD_SURAH_NUMBER, item.wirdSurahNumber)
                putExtra(AlarmAlertIntentFactory.EXTRA_START_AYAH, item.startAyah ?: 1)
                putExtra(AlarmAlertIntentFactory.EXTRA_DAILY_AYAH_COUNT, item.dailyAyahCount)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            item.id + requestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val PRIMARY_REQUEST_CODE_OFFSET = 100_000
        private const val SNOOZE_REQUEST_CODE_OFFSET = 500_000
    }

    fun createShowIntent(item: AlarmItem): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, item.id)
        }
        return PendingIntent.getActivity(
            context,
            item.id + 200_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
