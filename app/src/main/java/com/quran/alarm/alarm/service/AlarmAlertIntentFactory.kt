package com.quran.alarm.alarm.service

import android.content.Context
import android.content.Intent
import com.quran.alarm.ui.AlarmAlertActivity

/**
 * Shared intent & page-range builder for alarm triggers, service foreground launches,
 * watchdog relaunches, and previews — eliminating duplicated Intent construction.
 */
object AlarmAlertIntentFactory {

    const val EXTRA_IS_FRESH_TRIGGER = "com.quran.alarm.EXTRA_IS_FRESH_TRIGGER"
    const val EXTRA_IS_SNOOZE_ENABLED = "com.quran.alarm.EXTRA_IS_SNOOZE_ENABLED"

    fun computeEndPage(startPage: Int, pageCount: Int): Int {
        val safeStart = startPage.coerceIn(1, 604)
        val safeCount = pageCount.coerceAtLeast(1)
        return Math.floorMod(safeStart + safeCount - 2, 604) + 1
    }

    fun buildAlertIntent(
        context: Context,
        alarmId: Int,
        label: String,
        targetPage: Int,
        startPage: Int,
        pageCount: Int,
        isSnoozeEnabled: Boolean = true,
        isFreshTrigger: Boolean = false,
        reorderToFront: Boolean = false
    ): Intent {
        return Intent(context, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                if (reorderToFront) Intent.FLAG_ACTIVITY_REORDER_TO_FRONT else 0
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, targetPage)
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, startPage)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, pageCount)
            putExtra(EXTRA_IS_SNOOZE_ENABLED, isSnoozeEnabled)
            putExtra(EXTRA_IS_FRESH_TRIGGER, isFreshTrigger)
        }
    }

    fun buildStartServiceIntent(
        context: Context,
        alarmId: Int,
        label: String,
        targetPage: Int,
        startPage: Int,
        pageCount: Int,
        volumeLevel: Int,
        isVibrate: Boolean,
        soundUri: String?
    ): Intent {
        return Intent(context, AlarmForegroundService::class.java).apply {
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
    }
}

