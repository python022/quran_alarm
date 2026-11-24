package com.quran.alarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.data.storage.AlarmSessionStore
import com.quran.alarm.alarm.data.storage.PersistedAlarmSession
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory
import com.quran.alarm.alarm.service.AlarmForegroundService
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * BroadcastReceiver responsible for capturing alarm triggers from [android.app.AlarmManager].
 * Offloads repository/scheduler I/O onto a background executor via [goAsync], rejects deleted/disabled
 * ghost alarms, holds a transient WakeLock around service/activity launch, and releases it cleanly.
 */
class AlarmReceiver : BroadcastReceiver() {
    var repositoryProvider: (Context) -> AlarmRepository = { context -> AlarmRepositoryImpl(context) }
    var schedulerProvider: (Context) -> AlarmScheduler = { context -> AlarmSchedulerImpl(context) }
    var backgroundExecutor: Executor = DEFAULT_EXECUTOR

    companion object {
        const val EXTRA_ALARM_ID = AlarmAlertIntentFactory.EXTRA_ALARM_ID
        const val EXTRA_ALARM_LABEL = AlarmAlertIntentFactory.EXTRA_ALARM_LABEL
        const val EXTRA_TARGET_PAGE = AlarmAlertIntentFactory.EXTRA_TARGET_PAGE
        const val EXTRA_START_PAGE = AlarmAlertIntentFactory.EXTRA_START_PAGE
        const val EXTRA_PAGE_COUNT = AlarmAlertIntentFactory.EXTRA_PAGE_COUNT
        const val EXTRA_VOLUME_LEVEL = AlarmAlertIntentFactory.EXTRA_VOLUME_LEVEL
        const val EXTRA_IS_VIBRATE = AlarmAlertIntentFactory.EXTRA_IS_VIBRATE
        const val EXTRA_SOUND_URI = AlarmAlertIntentFactory.EXTRA_SOUND_URI
        const val EXTRA_IS_SNOOZE_TRIGGER = AlarmAlertIntentFactory.EXTRA_IS_SNOOZE_TRIGGER
        const val ACTION_TRIGGER_ALARM = "com.quran.alarm.ACTION_TRIGGER_ALARM"

        private val DEFAULT_EXECUTOR: Executor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext ?: context
        val pendingResult = goAsync()
        if (pendingResult != null) {
            backgroundExecutor.execute {
                try {
                    handleAlarmTrigger(appContext, intent)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            handleAlarmTrigger(appContext, intent)
        }
    }

    internal fun handleAlarmTrigger(context: Context, intent: Intent) {
        val payload = AlarmAlertIntentFactory.parsePayload(intent)
        val alarmId = payload.alarmId
        if (alarmId == AlarmAlertIntentFactory.INVALID_ALARM_ID) return

        val repository = repositoryProvider(context)
        val scheduler = schedulerProvider(context)
        val alarm = if (alarmId >= 0) repository.getAlarmById(alarmId) else null

        // Prevent deleted or disabled alarms ("ghost alarms") from firing if a stale PendingIntent triggers
        if (alarmId >= 0 && (alarm == null || !alarm.isEnabled)) {
            scheduler.cancel(alarmId)
            return
        }

        val label = alarm?.label ?: payload.label
        val targetPage = alarm?.targetPage ?: payload.targetPage
        val startPage = alarm?.startPage ?: payload.startPage
        val pageCount = alarm?.pageCount ?: payload.pageCount
        val volumeLevel = alarm?.volumeLevel ?: payload.volumeLevel
        val isVibrate = alarm?.isVibrationEnabled ?: payload.isVibrate
        val soundUri = alarm?.soundUri ?: payload.soundUri
        val isSnoozeTrigger = intent.getBooleanExtra(EXTRA_IS_SNOOZE_TRIGGER, false)
        val wirdSurahNumber = alarm?.wirdSurahNumber ?: payload.wirdSurahNumber
        val startAyah = alarm?.startAyah ?: payload.startAyah
        val dailyAyahCount = alarm?.dailyAyahCount ?: payload.dailyAyahCount

        // Immediately persist active session before disabling one-shot alarm so a crash/reboot mid-ring recovers
        if (alarmId >= 0) {
            runCatching {
                AlarmSessionStore.fromContext(context).saveSession(
                    PersistedAlarmSession(
                        active = true,
                        stage = AlarmSessionStore.STAGE_RINGING,
                        alarmId = alarmId,
                        label = label,
                        startPage = startPage,
                        pageCount = pageCount,
                        volumeLevel = volumeLevel,
                        isVibrate = isVibrate,
                        soundUri = soundUri,
                        wirdSurahNumber = wirdSurahNumber,
                        startAyah = startAyah,
                        dailyAyahCount = dailyAyahCount
                    )
                )
            }
        }

        // Immediately reschedule next occurrence for repeating alarms so unexpected shutdowns
        // never cause missed alarms; disable one-shot alarms so they don't fire again.
        if (alarm != null) {
            if (alarm.repeatDays.isNotEmpty()) {
                val now = System.currentTimeMillis()
                if (!isSnoozeTrigger) {
                    val nextTrigger = alarm.copy(explicitTriggerMillis = null, snoozeTriggerMillis = null)
                        .getNextTriggerMillis(now + 60_000L)
                    val refreshed = alarm.copy(explicitTriggerMillis = nextTrigger, snoozeTriggerMillis = null)
                    repository.saveAlarm(refreshed)
                    scheduler.schedule(refreshed)
                } else {
                    val clearedSnooze = alarm.copy(snoozeTriggerMillis = null)
                    if (clearedSnooze.explicitTriggerMillis == null || clearedSnooze.explicitTriggerMillis <= now) {
                        val nextTrigger = clearedSnooze.copy(explicitTriggerMillis = null)
                            .getNextTriggerMillis(now + 60_000L)
                        val refreshed = clearedSnooze.copy(explicitTriggerMillis = nextTrigger)
                        repository.saveAlarm(refreshed)
                        scheduler.schedule(refreshed)
                    } else {
                        repository.saveAlarm(clearedSnooze)
                    }
                }
            } else {
                val disabled = alarm.copy(isEnabled = false, explicitTriggerMillis = null, snoozeTriggerMillis = null)
                repository.saveAlarm(disabled)
            }
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuranAlarm:AlarmReceiverWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        try {
            // 1. Launch Foreground Audio and Vibration Service
            val serviceIntent = AlarmAlertIntentFactory.buildStartServiceIntent(
                context = context,
                alarmId = alarmId,
                label = label,
                targetPage = targetPage,
                startPage = startPage,
                pageCount = pageCount,
                volumeLevel = volumeLevel,
                isVibrate = isVibrate,
                soundUri = soundUri,
                wirdSurahNumber = wirdSurahNumber,
                startAyah = startAyah,
                dailyAyahCount = dailyAyahCount
            )
            ContextCompat.startForegroundService(context, serviceIntent)

            // 2. Launch Full-Screen Lockscreen Alert Activity directly
            val alertIntent = AlarmAlertIntentFactory.buildAlertIntent(
                context = context,
                alarmId = alarmId,
                label = label,
                targetPage = targetPage,
                startPage = startPage,
                pageCount = pageCount,
                isSnoozeEnabled = true,
                isFreshTrigger = true,
                wirdSurahNumber = wirdSurahNumber,
                startAyah = startAyah,
                dailyAyahCount = dailyAyahCount
            ).apply {
                putExtra(AlarmForegroundService.EXTRA_VOLUME_LEVEL, volumeLevel)
                putExtra(AlarmForegroundService.EXTRA_SOUND_URI, soundUri)
            }
            AlarmAlertIntentFactory.launchActivityWithBalFallback(
                context = context,
                alertIntent = alertIntent,
                pendingIntentRequestCode = alarmId + 500_000
            )
        } finally {
            runCatching {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }
        }
    }
}
