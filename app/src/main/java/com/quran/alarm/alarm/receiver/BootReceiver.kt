package com.quran.alarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.data.storage.AlarmSessionStore
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.service.AlarmForegroundService

/**
 * BroadcastReceiver responsible for rescheduling all active alarms upon device reboot
 * or time/package updates.
 *
 * Catches:
 * - [Intent.ACTION_BOOT_COMPLETED]
 * - [Intent.ACTION_LOCKED_BOOT_COMPLETED]
 * - `android.intent.action.QUICKBOOT_POWERON` / `com.htc.intent.action.QUICKBOOT_POWERON`
 *
 * Guarantees that no scheduled prayer/Quran alarm or active alert session is lost after
 * battery drain or system restart.
 */
open class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"

        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON
        )
    }

    var repositoryProvider: (Context) -> AlarmRepository = { context -> AlarmRepositoryImpl(context) }
    var schedulerProvider: (Context) -> AlarmScheduler = { context -> AlarmSchedulerImpl(context) }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val appContext = context.applicationContext ?: context
        val forceRecalculateRepeating =
            action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED
        val pendingResult = goAsync()
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuranAlarm:BootReceiverWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(30_000L)
        }

        val work = Runnable {
            try {
                rescheduleAllAlarms(
                    context = appContext,
                    forceRecalculateRepeating = forceRecalculateRepeating,
                    shouldRestoreSession = !forceRecalculateRepeating
                )
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
                pendingResult?.finish()
            }
        }

        if (pendingResult != null) {
            Thread(work, "QuranAlarm-BootReschedule").start()
        } else {
            work.run()
        }
    }

    fun rescheduleAllAlarms(
        context: Context,
        forceRecalculateRepeating: Boolean = false,
        shouldRestoreSession: Boolean = true
    ) {
        val repository = repositoryProvider(context)
        val scheduler = schedulerProvider(context)
        val activeAlarms = repository.getActiveAlarms()
        val now = System.currentTimeMillis()
        val alarmsToPersist = mutableListOf<AlarmItem>()

        for (alarm in activeAlarms) {
            val pendingSnooze = alarm.snoozeTriggerMillis?.takeIf { it > now }
            val normalizedAlarm = if (alarm.snoozeTriggerMillis != null && pendingSnooze == null) {
                alarm.copy(snoozeTriggerMillis = null)
            } else {
                alarm
            }

            val updatedAlarm = if (normalizedAlarm.repeatDays.isEmpty()) {
                val isExpired = normalizedAlarm.isOneShotExpired(now) && pendingSnooze == null
                if (isExpired) {
                    // One-shot alarm elapsed while phone was powered off: do NOT ring as ghost alarm
                    val disabled = normalizedAlarm.copy(isEnabled = false, snoozeTriggerMillis = null)
                    alarmsToPersist.add(disabled)
                    null
                } else {
                    if (normalizedAlarm.explicitTriggerMillis == null && pendingSnooze == null) {
                        val withExplicit = normalizedAlarm.copy(explicitTriggerMillis = normalizedAlarm.getNextTriggerMillis(now))
                        alarmsToPersist.add(withExplicit)
                        withExplicit
                    } else {
                        if (normalizedAlarm != alarm) alarmsToPersist.add(normalizedAlarm)
                        normalizedAlarm
                    }
                }
            } else {
                val needsRepeatingRecalc = forceRecalculateRepeating ||
                    normalizedAlarm.explicitTriggerMillis == null ||
                    normalizedAlarm.explicitTriggerMillis <= now
                if (needsRepeatingRecalc) {
                    val nextTime = normalizedAlarm.copy(explicitTriggerMillis = null).getNextTriggerMillis(now)
                    val refreshed = normalizedAlarm.copy(explicitTriggerMillis = nextTime)
                    alarmsToPersist.add(refreshed)
                    refreshed
                } else {
                    if (normalizedAlarm != alarm) alarmsToPersist.add(normalizedAlarm)
                    normalizedAlarm
                }
            }
            if (updatedAlarm != null && updatedAlarm.isEnabled) {
                if (updatedAlarm.repeatDays.isNotEmpty() || !updatedAlarm.isOneShotExpired(now)) {
                    scheduler.schedule(updatedAlarm)
                }
                if (pendingSnooze != null) {
                    scheduler.scheduleSnooze(updatedAlarm, pendingSnooze)
                }
            }
        }

        if (alarmsToPersist.isNotEmpty()) {
            repository.saveAlarms(alarmsToPersist)
        }

        if (shouldRestoreSession) {
            restoreInterruptedSession(context)
        }
    }

    private fun restoreInterruptedSession(context: Context) {
        runCatching {
            val session = AlarmSessionStore.fromContext(context).loadSession() ?: return
            if (!session.active || session.alarmId < 0) return
            val restoreIntent = Intent(context, AlarmForegroundService::class.java).apply {
                action = AlarmForegroundService.ACTION_RESTORE_SESSION
            }
            ContextCompat.startForegroundService(context, restoreIntent)
        }
    }
}
