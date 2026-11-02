package com.quran.alarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver responsible for capturing alarm triggers from [android.app.AlarmManager].
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ALARM_ID = "com.quran.alarm.EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "com.quran.alarm.EXTRA_ALARM_LABEL"
        const val EXTRA_TARGET_PAGE = "com.quran.alarm.EXTRA_TARGET_PAGE"
        const val ACTION_TRIGGER_ALARM = "com.quran.alarm.ACTION_TRIGGER_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Implementation for service dispatch will be completed in PR 3
    }
}

