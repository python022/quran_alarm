package com.quran.alarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.service.AlarmForegroundService
import com.quran.alarm.ui.AlarmAlertActivity

/**
 * BroadcastReceiver responsible for capturing alarm triggers from [android.app.AlarmManager].
 * Holds a transient WakeLock and launches both the Foreground Service and AlarmAlertActivity.
 */
class AlarmReceiver : BroadcastReceiver() {
    var repositoryProvider: (Context) -> AlarmRepository = { context -> AlarmRepositoryImpl(context) }
    var schedulerProvider: (Context) -> AlarmScheduler = { context -> AlarmSchedulerImpl(context) }

    companion object {
        const val EXTRA_ALARM_ID = "com.quran.alarm.EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "com.quran.alarm.EXTRA_ALARM_LABEL"
        const val EXTRA_TARGET_PAGE = "com.quran.alarm.EXTRA_TARGET_PAGE"
        const val EXTRA_START_PAGE = "com.quran.alarm.EXTRA_START_PAGE"
        const val EXTRA_PAGE_COUNT = "com.quran.alarm.EXTRA_PAGE_COUNT"
        const val EXTRA_VOLUME_LEVEL = "com.quran.alarm.EXTRA_VOLUME_LEVEL"
        const val EXTRA_IS_VIBRATE = "com.quran.alarm.EXTRA_IS_VIBRATE"
        const val EXTRA_SOUND_URI = "com.quran.alarm.EXTRA_SOUND_URI"
        const val ACTION_TRIGGER_ALARM = "com.quran.alarm.ACTION_TRIGGER_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
        val targetPage = intent.getIntExtra(EXTRA_TARGET_PAGE, 1)
        val startPage = intent.getIntExtra(EXTRA_START_PAGE, targetPage)
        val pageCount = intent.getIntExtra(EXTRA_PAGE_COUNT, 5)
        val volumeLevel = intent.getIntExtra(EXTRA_VOLUME_LEVEL, 100)
        val isVibrate = intent.getBooleanExtra(EXTRA_IS_VIBRATE, true)
        val soundUri = intent.getStringExtra(EXTRA_SOUND_URI)

        // Immediately reschedule next occurrence for repeating alarms so Android Lifecycle or unexpected
        // shutdowns never cause missed alarms; disable one-shot alarms so they don't fire again
        val repository = repositoryProvider(context)
        val scheduler = schedulerProvider(context)
        val alarm = repository.getAlarmById(alarmId)
        if (alarm != null) {
            if (alarm.repeatDays.isNotEmpty()) {
                val nextTrigger = alarm.getNextTriggerMillis(System.currentTimeMillis() + 60_000L)
                val refreshed = alarm.copy(explicitTriggerMillis = nextTrigger)
                repository.saveAlarm(refreshed)
                scheduler.schedule(refreshed)
            } else {
                val disabled = alarm.copy(isEnabled = false)
                repository.saveAlarm(disabled)
            }
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuranAlarm:AlarmReceiverWakeLock"
        )
        wakeLock?.acquire(15_000L) // 15 seconds transient window

        // 1. Launch Foreground Audio and Vibration Service
        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START_ALARM
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, targetPage)
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, startPage)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, pageCount)
            putExtra(AlarmForegroundService.EXTRA_VOLUME_LEVEL, volumeLevel)
            putExtra(AlarmForegroundService.EXTRA_IS_VIBRATE, isVibrate)
            putExtra(AlarmForegroundService.EXTRA_SOUND_URI, soundUri)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // 2. Launch Full-Screen Lockscreen Alert Activity directly
        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, targetPage)
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, startPage)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, pageCount)
            putExtra(AlarmForegroundService.EXTRA_VOLUME_LEVEL, volumeLevel)
            putExtra(AlarmForegroundService.EXTRA_SOUND_URI, soundUri)
        }
        try {
            context.startActivity(alertIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
