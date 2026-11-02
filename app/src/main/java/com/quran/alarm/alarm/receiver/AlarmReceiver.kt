package com.quran.alarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.quran.alarm.alarm.service.AlarmForegroundService

/**
 * BroadcastReceiver responsible for capturing alarm triggers from [android.app.AlarmManager].
 * Holds a transient WakeLock to guarantee successful Foreground Service transition even under Doze mode.
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ALARM_ID = "com.quran.alarm.EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "com.quran.alarm.EXTRA_ALARM_LABEL"
        const val EXTRA_TARGET_PAGE = "com.quran.alarm.EXTRA_TARGET_PAGE"
        const val ACTION_TRIGGER_ALARM = "com.quran.alarm.ACTION_TRIGGER_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
        val targetPage = intent.getIntExtra(EXTRA_TARGET_PAGE, 1)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuranAlarm:AlarmReceiverWakeLock"
        )
        wakeLock?.acquire(15_000L) // 15 seconds transient window

        try {
            val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
                action = AlarmForegroundService.ACTION_START_ALARM
                putExtra(AlarmForegroundService.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, label)
                putExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, targetPage)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } finally {
            // Service handles its own persistent lifecycle WakeLock
        }
    }
}
