package com.quran.alarm.alarm.service

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.ui.AlarmAlertActivity

/**
 * Parsed, sanitized payload extracted from an alarm trigger, service, or alert activity Intent.
 */
data class AlarmAlertPayload(
    val alarmId: Int,
    val label: String,
    val targetPage: Int,
    val startPage: Int,
    val pageCount: Int,
    val volumeLevel: Int,
    val isVibrate: Boolean,
    val soundUri: String?,
    val isSnoozeTrigger: Boolean,
    val isSnoozeEnabled: Boolean,
    val isFreshTrigger: Boolean,
    val wirdSurahNumber: Int?,
    val startAyah: Int?,
    val dailyAyahCount: Int?
)

/**
 * Shared intent, payload parser, and page-range builder for alarm triggers, service foreground
 * launches, watchdog relaunches, and previews — eliminating duplicated Intent construction/parsing.
 */
object AlarmAlertIntentFactory {

    const val INVALID_ALARM_ID = -1
    const val PREVIEW_ALARM_ID = -999
    const val DEFAULT_ALARM_LABEL = "ورد القرآن الكريم"

    const val EXTRA_ALARM_ID = "com.quran.alarm.EXTRA_ALARM_ID"
    const val EXTRA_ALARM_LABEL = "com.quran.alarm.EXTRA_ALARM_LABEL"
    const val EXTRA_TARGET_PAGE = "com.quran.alarm.EXTRA_TARGET_PAGE"
    const val EXTRA_START_PAGE = "com.quran.alarm.EXTRA_START_PAGE"
    const val EXTRA_PAGE_COUNT = "com.quran.alarm.EXTRA_PAGE_COUNT"
    const val EXTRA_VOLUME_LEVEL = "com.quran.alarm.EXTRA_VOLUME_LEVEL"
    const val EXTRA_IS_VIBRATE = "com.quran.alarm.EXTRA_IS_VIBRATE"
    const val EXTRA_SOUND_URI = "com.quran.alarm.EXTRA_SOUND_URI"
    const val EXTRA_IS_SNOOZE_TRIGGER = "com.quran.alarm.EXTRA_IS_SNOOZE_TRIGGER"
    const val EXTRA_IS_FRESH_TRIGGER = "com.quran.alarm.EXTRA_IS_FRESH_TRIGGER"
    const val EXTRA_IS_SNOOZE_ENABLED = "com.quran.alarm.EXTRA_IS_SNOOZE_ENABLED"
    const val EXTRA_WIRD_SURAH_NUMBER = "com.quran.alarm.EXTRA_WIRD_SURAH_NUMBER"
    const val EXTRA_START_AYAH = "com.quran.alarm.EXTRA_START_AYAH"
    const val EXTRA_DAILY_AYAH_COUNT = "com.quran.alarm.EXTRA_DAILY_AYAH_COUNT"

    fun computeEndPage(startPage: Int, pageCount: Int): Int =
        QuranStructure.computeEndPage(startPage, pageCount)

    fun sanitizeLabel(rawLabel: String?): String =
        rawLabel?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(AlarmItem.MAX_LABEL_LENGTH)
            ?: DEFAULT_ALARM_LABEL

    fun parsePayload(
        intent: Intent?,
        fallbackAlarmId: Int = INVALID_ALARM_ID,
        defaultFreshTrigger: Boolean = false
    ): AlarmAlertPayload {
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, fallbackAlarmId) ?: fallbackAlarmId
        val label = sanitizeLabel(intent?.getStringExtra(EXTRA_ALARM_LABEL))
        val targetPage = (intent?.getIntExtra(EXTRA_TARGET_PAGE, 1) ?: 1).coerceIn(1, 604)
        val startPage = (intent?.getIntExtra(EXTRA_START_PAGE, targetPage) ?: targetPage).coerceIn(1, 604)
        val pageCount = (intent?.getIntExtra(EXTRA_PAGE_COUNT, 5) ?: 5).coerceIn(1, 604)
        val volumeLevel = (intent?.getIntExtra(EXTRA_VOLUME_LEVEL, 100) ?: 100).coerceIn(1, 100)
        val isVibrate = intent?.getBooleanExtra(EXTRA_IS_VIBRATE, true) ?: true
        val soundUri = intent?.getStringExtra(EXTRA_SOUND_URI)
        val isSnoozeTrigger = intent?.getBooleanExtra(EXTRA_IS_SNOOZE_TRIGGER, false) ?: false
        val isSnoozeEnabled = intent?.getBooleanExtra(EXTRA_IS_SNOOZE_ENABLED, true) ?: true
        val isFreshTrigger = intent?.getBooleanExtra(EXTRA_IS_FRESH_TRIGGER, defaultFreshTrigger) ?: defaultFreshTrigger

        val wirdSurahNumber = (intent?.getIntExtra(EXTRA_WIRD_SURAH_NUMBER, -1) ?: -1)
            .takeIf { it in 1..QuranStructure.TOTAL_SURAHS }
        val maxAyahs = wirdSurahNumber?.let { QuranStructure.getSurahByNumber(it).ayahCount }
        val startAyah = if (maxAyahs != null) {
            (intent?.getIntExtra(EXTRA_START_AYAH, -1) ?: -1)
                .takeIf { it > 0 }
                ?.coerceIn(1, maxAyahs)
        } else {
            null
        }
        val dailyAyahCount = if (maxAyahs != null) {
            (intent?.getIntExtra(EXTRA_DAILY_AYAH_COUNT, -1) ?: -1)
                .takeIf { it > 0 }
                ?.coerceIn(1, maxAyahs)
        } else {
            null
        }

        return AlarmAlertPayload(
            alarmId = alarmId,
            label = label,
            targetPage = targetPage,
            startPage = startPage,
            pageCount = pageCount,
            volumeLevel = volumeLevel,
            isVibrate = isVibrate,
            soundUri = soundUri,
            isSnoozeTrigger = isSnoozeTrigger,
            isSnoozeEnabled = isSnoozeEnabled,
            isFreshTrigger = isFreshTrigger,
            wirdSurahNumber = wirdSurahNumber,
            startAyah = startAyah,
            dailyAyahCount = dailyAyahCount
        )
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
        reorderToFront: Boolean = false,
        wirdSurahNumber: Int? = null,
        startAyah: Int? = null,
        dailyAyahCount: Int? = null
    ): Intent {
        return Intent(context, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                if (reorderToFront) Intent.FLAG_ACTIVITY_REORDER_TO_FRONT else 0
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, sanitizeLabel(label))
            putExtra(EXTRA_TARGET_PAGE, targetPage.coerceIn(1, 604))
            putExtra(EXTRA_START_PAGE, startPage.coerceIn(1, 604))
            putExtra(EXTRA_PAGE_COUNT, pageCount.coerceIn(1, 604))
            putExtra(EXTRA_IS_SNOOZE_ENABLED, isSnoozeEnabled)
            putExtra(EXTRA_IS_FRESH_TRIGGER, isFreshTrigger)
            if (wirdSurahNumber != null && dailyAyahCount != null) {
                putExtra(EXTRA_WIRD_SURAH_NUMBER, wirdSurahNumber)
                putExtra(EXTRA_START_AYAH, startAyah ?: 1)
                putExtra(EXTRA_DAILY_AYAH_COUNT, dailyAyahCount)
            }
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
        soundUri: String?,
        wirdSurahNumber: Int? = null,
        startAyah: Int? = null,
        dailyAyahCount: Int? = null
    ): Intent {
        return Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, sanitizeLabel(label))
            putExtra(EXTRA_TARGET_PAGE, targetPage.coerceIn(1, 604))
            putExtra(EXTRA_START_PAGE, startPage.coerceIn(1, 604))
            putExtra(EXTRA_PAGE_COUNT, pageCount.coerceIn(1, 604))
            putExtra(EXTRA_VOLUME_LEVEL, volumeLevel.coerceIn(1, 100))
            putExtra(EXTRA_IS_VIBRATE, isVibrate)
            putExtra(EXTRA_SOUND_URI, soundUri)
            if (wirdSurahNumber != null && dailyAyahCount != null) {
                putExtra(EXTRA_WIRD_SURAH_NUMBER, wirdSurahNumber)
                putExtra(EXTRA_START_AYAH, startAyah ?: 1)
                putExtra(EXTRA_DAILY_AYAH_COUNT, dailyAyahCount)
            }
        }
    }

    fun buildSilenceServiceIntent(
        context: Context,
        alarmId: Int,
        label: String,
        startPage: Int,
        pageCount: Int,
        wirdSurahNumber: Int? = null,
        startAyah: Int? = null,
        dailyAyahCount: Int? = null
    ): Intent {
        return Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_SILENCE_RINGING
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, sanitizeLabel(label))
            putExtra(EXTRA_START_PAGE, startPage.coerceIn(1, 604))
            putExtra(EXTRA_PAGE_COUNT, pageCount.coerceIn(1, 604))
            if (wirdSurahNumber != null && dailyAyahCount != null) {
                putExtra(EXTRA_WIRD_SURAH_NUMBER, wirdSurahNumber)
                putExtra(EXTRA_START_AYAH, startAyah ?: 1)
                putExtra(EXTRA_DAILY_AYAH_COUNT, dailyAyahCount)
            }
        }
    }

    fun launchActivityWithBalFallback(
        context: Context,
        alertIntent: Intent,
        pendingIntentRequestCode: Int
    ) {
        try {
            context.startActivity(alertIntent)
        } catch (t: Throwable) {
            Log.w("AlarmAlertIntentFactory", "Direct activity launch failed, trying PendingIntent BAL: ${t.message}")
            runCatching {
                val pi = PendingIntent.getActivity(
                    context,
                    pendingIntentRequestCode,
                    alertIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                        .toBundle()
                    pi.send(context, 0, null, null, null, null, options)
                } else {
                    pi.send()
                }
            }
        }
    }
}

