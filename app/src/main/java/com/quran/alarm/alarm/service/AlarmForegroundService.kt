package com.quran.alarm.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quran.alarm.R
import com.quran.alarm.alarm.audio.AlarmVibratorManager
import com.quran.alarm.alarm.audio.SmartAlarmAudioManager
import com.quran.alarm.alarm.audio.VolumeEnforcer
import com.quran.alarm.alarm.data.device.OemDeviceHelper
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.data.storage.AlarmSessionStore
import com.quran.alarm.alarm.data.storage.PersistedAlarmSession
import com.quran.alarm.ui.AlarmAlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that plays the alarm sound, vibrates continuously, enforces target volume,
 * and maintains the ongoing notification and lockscreen launch window.
 *
 * Uses foregroundServiceType="mediaPlayback" to comply with Android 14+ background execution limits.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.quran.alarm.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.quran.alarm.ACTION_STOP_ALARM"
        const val ACTION_SILENCE_RINGING = "com.quran.alarm.ACTION_SILENCE_RINGING"
        const val ACTION_RESTORE_SESSION = "com.quran.alarm.ACTION_RESTORE_SESSION"
        const val ACTION_ALERT_VISIBLE = "com.quran.alarm.ACTION_ALERT_VISIBLE"

        const val EXTRA_ALARM_ID = AlarmAlertIntentFactory.EXTRA_ALARM_ID
        const val EXTRA_ALARM_LABEL = AlarmAlertIntentFactory.EXTRA_ALARM_LABEL
        const val EXTRA_TARGET_PAGE = AlarmAlertIntentFactory.EXTRA_TARGET_PAGE
        const val EXTRA_START_PAGE = AlarmAlertIntentFactory.EXTRA_START_PAGE
        const val EXTRA_PAGE_COUNT = AlarmAlertIntentFactory.EXTRA_PAGE_COUNT
        const val EXTRA_VOLUME_LEVEL = AlarmAlertIntentFactory.EXTRA_VOLUME_LEVEL
        const val EXTRA_IS_VIBRATE = AlarmAlertIntentFactory.EXTRA_IS_VIBRATE
        const val EXTRA_SOUND_URI = AlarmAlertIntentFactory.EXTRA_SOUND_URI
        const val EXTRA_WIRD_SURAH_NUMBER = AlarmAlertIntentFactory.EXTRA_WIRD_SURAH_NUMBER
        const val EXTRA_START_AYAH = AlarmAlertIntentFactory.EXTRA_START_AYAH
        const val EXTRA_DAILY_AYAH_COUNT = AlarmAlertIntentFactory.EXTRA_DAILY_AYAH_COUNT

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "quran_alarm_channel"
        const val CHANNEL_NAME = "منبه القرآن الكريم"
        const val CHANNEL_ONGOING_ID = "quran_alarm_ongoing_channel"
        const val CHANNEL_ONGOING_NAME = "حالة المنبه النشط"
        const val SESSION_PREFS = AlarmSessionStore.PREFS_NAME
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_INTERVAL_MS = 5 * 60 * 1000L
        private const val DEFERRED_SNOOZE_MS = 5 * 60_000L

        @Volatile
        var isAlarmRingingGlobally: Boolean = false

        @Volatile
        var isReadingSessionActiveGlobally: Boolean = false

        @Volatile
        var activeSessionAlarmId: Int? = null

        @JvmStatic
        fun isSessionLockActive(): Boolean =
            isAlarmRingingGlobally || isReadingSessionActiveGlobally

        fun resetGlobalStateForTesting() {
            isAlarmRingingGlobally = false
            isReadingSessionActiveGlobally = false
            activeSessionAlarmId = null
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "قناة إشعارات رنين منبه القرآن الكريم فائقة الأولوية"
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    setSound(null, null)
                }
                val ongoingChannel = NotificationChannel(
                    CHANNEL_ONGOING_ID,
                    CHANNEL_ONGOING_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "إشعار صامت عند فتح شاشة المنبه أو أثناء قراءة الورد"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    enableVibration(false)
                    setSound(null, null)
                    setShowBadge(false)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
                manager?.createNotificationChannel(ongoingChannel)
            }
        }

        fun buildServiceNotification(
            context: Context,
            payload: AlarmAlertPayload,
            isReading: Boolean,
            silentOngoing: Boolean
        ): Notification {
            val endPage = AlarmAlertIntentFactory.computeEndPage(payload.startPage, payload.pageCount)
            val alertIntent = AlarmAlertIntentFactory.buildAlertIntent(
                context = context,
                alarmId = payload.alarmId,
                label = payload.label,
                targetPage = payload.startPage,
                startPage = payload.startPage,
                pageCount = payload.pageCount,
                isSnoozeEnabled = true,
                isFreshTrigger = false,
                wirdSurahNumber = payload.wirdSurahNumber,
                startAyah = payload.startAyah,
                dailyAyahCount = payload.dailyAyahCount
            )
            val pendingIntent = PendingIntent.getActivity(
                context,
                payload.alarmId + 300_000,
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val title = if (isReading) "قراءة الورد القرآني جارية" else "منبه القرآن الكريم"
            val text = if (isReading) {
                "${payload.label} - صفحة ${payload.startPage} إلى $endPage"
            } else {
                "${payload.label} - الورد: صفحة ${payload.startPage} إلى $endPage"
            }
            val channelId = if (silentOngoing) CHANNEL_ONGOING_ID else CHANNEL_ID

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_quran_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)

            if (silentOngoing) {
                builder
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(if (isReading) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
            } else {
                builder
                    .setPriority(if (isReading) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_MAX)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                if (isReading) {
                    builder.setOnlyAlertOnce(true)
                } else {
                    builder.setFullScreenIntent(pendingIntent, true)
                }
            }
            return builder.build()
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: SmartAlarmAudioManager
    private lateinit var vibratorManager: AlarmVibratorManager
    private lateinit var volumeEnforcer: VolumeEnforcer
    private lateinit var sessionStore: AlarmSessionStore

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var isAlarmRinging = false
    private var isSessionActive = false
    private var isAudioDeferredByCall = false
    private var activeSoundUri: String? = null
    private var activeVolumeLevel: Int = 100
    private var activeIsVibrate: Boolean = true
    private var activePayload: AlarmAlertPayload? = null
    private var watchdogJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = SmartAlarmAudioManager(this, externalScope = serviceScope)
        vibratorManager = AlarmVibratorManager(this)
        volumeEnforcer = VolumeEnforcer(this)
        sessionStore = AlarmSessionStore.fromContext(this)
        ensureNotificationChannels(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_RESTORE_SESSION) {
            return handleRestoreSession()
        }

        when (intent.action) {
            ACTION_ALERT_VISIBLE -> {
                val payload = activePayload
                if (payload != null && (isSessionActive || payload.alarmId < 0)) {
                    val silentNotification = buildServiceNotification(
                        context = this,
                        payload = payload,
                        isReading = !isAlarmRinging && payload.alarmId >= 0,
                        silentOngoing = true
                    )
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, silentNotification)
                    return if (isSessionActive) START_STICKY else START_NOT_STICKY
                }
                if (!isSessionActive) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                return START_STICKY
            }
            ACTION_SILENCE_RINGING -> {
                isAlarmRingingGlobally = false
                isAlarmRinging = false
                isAudioDeferredByCall = false
                pauseRingingOutputs()
                releaseWakeLock()

                val parsedPayload = AlarmAlertIntentFactory.parsePayload(
                    intent = intent,
                    fallbackAlarmId = activeSessionAlarmId ?: AlarmAlertIntentFactory.INVALID_ALARM_ID
                )
                if (parsedPayload.alarmId < 0) {
                    if (!isSessionLockActive()) {
                        isSessionActive = false
                        stopAlarmAndSelf()
                    }
                    return if (isSessionLockActive()) START_STICKY else START_NOT_STICKY
                }

                isReadingSessionActiveGlobally = true
                isSessionActive = true
                activeSessionAlarmId = parsedPayload.alarmId
                sessionStore.markReadingStage(
                    alarmId = parsedPayload.alarmId,
                    label = parsedPayload.label,
                    startPage = parsedPayload.startPage,
                    pageCount = parsedPayload.pageCount,
                    wirdSurahNumber = parsedPayload.wirdSurahNumber,
                    startAyah = parsedPayload.startAyah,
                    dailyAyahCount = parsedPayload.dailyAyahCount
                )
                val resolvedPayload = sessionStore.loadSession()?.toPayload() ?: parsedPayload
                activePayload = resolvedPayload

                val readingNotification = buildServiceNotification(
                    context = this,
                    payload = resolvedPayload,
                    isReading = true,
                    silentOngoing = true
                )
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, readingNotification)

                ensureWatchdogRunning(resolvedPayload)
                return START_STICKY
            }
            ACTION_STOP_ALARM -> {
                activePayload = null
                isAlarmRingingGlobally = false
                isReadingSessionActiveGlobally = false
                activeSessionAlarmId = null
                isAlarmRinging = false
                isSessionActive = false
                isAudioDeferredByCall = false
                sessionStore.clearSession()
                watchdogJob?.cancel()
                watchdogJob = null
                pauseRingingOutputs()
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM, null -> {
                val payload = AlarmAlertIntentFactory.parsePayload(intent)
                val alarmId = payload.alarmId
                if (alarmId == AlarmAlertIntentFactory.INVALID_ALARM_ID) {
                    if (!isSessionActive) {
                        stopAlarmAndSelf()
                        return START_NOT_STICKY
                    }
                    return START_STICKY
                }

                val isPreview = alarmId < 0
                val currentSessionId = activeSessionAlarmId ?: sessionStore.loadSession()?.alarmId
                if (!isPreview &&
                    isSessionLockActive() &&
                    currentSessionId != null &&
                    currentSessionId >= 0 &&
                    currentSessionId != alarmId
                ) {
                    // Preserve active alarm/reading session and defer the colliding alarm
                    deferCollidingAlarm(alarmId)
                    return START_STICKY
                }
                if (isPreview && isSessionLockActive() && currentSessionId != null && currentSessionId >= 0) {
                    return START_STICKY
                }

                activeSoundUri = payload.soundUri
                activeVolumeLevel = payload.volumeLevel
                activeIsVibrate = payload.isVibrate
                activePayload = payload

                if (isPreview) {
                    val notification = buildServiceNotification(
                        context = this,
                        payload = payload,
                        isReading = false,
                        silentOngoing = true
                    )
                    startForegroundCompat(notification)
                    if (!OemDeviceHelper.isPhoneCallActive(this)) {
                        startRingingOutputs(payload.volumeLevel, payload.soundUri, payload.isVibrate)
                    }
                    if (!AlarmAlertActivity.isActivityVisible) {
                        launchAlarmActivity(payload)
                    }
                    return START_NOT_STICKY
                }

                isAlarmRingingGlobally = true
                isReadingSessionActiveGlobally = false
                activeSessionAlarmId = alarmId
                isAlarmRinging = true
                isSessionActive = true
                AlarmAlertActivity.isDismissedOrSnoozedGlobally = false

                sessionStore.saveSession(
                    PersistedAlarmSession(
                        active = true,
                        stage = AlarmSessionStore.STAGE_RINGING,
                        alarmId = alarmId,
                        label = payload.label,
                        startPage = payload.startPage,
                        pageCount = payload.pageCount,
                        volumeLevel = payload.volumeLevel,
                        isVibrate = payload.isVibrate,
                        soundUri = payload.soundUri,
                        wirdSurahNumber = payload.wirdSurahNumber,
                        startAyah = payload.startAyah,
                        dailyAyahCount = payload.dailyAyahCount
                    )
                )

                startForegroundForRinging(payload)
                acquireWakeLock()

                if (OemDeviceHelper.isPhoneCallActive(this)) {
                    isAudioDeferredByCall = true
                } else {
                    isAudioDeferredByCall = false
                    startRingingOutputs(payload.volumeLevel, payload.soundUri, payload.isVibrate)
                }

                if (!AlarmAlertActivity.isActivityVisible) {
                    launchAlarmActivity(payload)
                }

                watchdogJob?.cancel()
                watchdogJob = startWatchdog(payload)
            }
        }

        return START_STICKY
    }

    private fun handleRestoreSession(): Int {
        val restored = sessionStore.loadSession()
        if (restored != null && restored.alarmId >= 0) {
            val payload = restored.toPayload()
            activeSessionAlarmId = restored.alarmId
            activeSoundUri = restored.soundUri
            activeVolumeLevel = restored.volumeLevel
            activeIsVibrate = restored.isVibrate
            activePayload = payload

            if (restored.stage == AlarmSessionStore.STAGE_RINGING) {
                isAlarmRingingGlobally = true
                isReadingSessionActiveGlobally = false
                isAlarmRinging = true
                isSessionActive = true
                AlarmAlertActivity.isDismissedOrSnoozedGlobally = false

                startForegroundForRinging(payload)
                acquireWakeLock()
                if (OemDeviceHelper.isPhoneCallActive(this)) {
                    isAudioDeferredByCall = true
                } else {
                    isAudioDeferredByCall = false
                    startRingingOutputs(restored.volumeLevel, restored.soundUri, restored.isVibrate)
                }
            } else {
                isAlarmRingingGlobally = false
                isReadingSessionActiveGlobally = true
                isAlarmRinging = false
                isSessionActive = true
                isAudioDeferredByCall = false
                AlarmAlertActivity.isDismissedOrSnoozedGlobally = false
                val readingNotification = buildServiceNotification(
                    context = this,
                    payload = payload,
                    isReading = true,
                    silentOngoing = true
                )
                startForegroundCompat(readingNotification)
            }

            if (!AlarmAlertActivity.isActivityVisible) {
                launchAlarmActivity(payload)
            }
            ensureWatchdogRunning(payload)
            return START_STICKY
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun ensureWatchdogRunning(payload: AlarmAlertPayload) {
        if (watchdogJob?.isActive != true) {
            watchdogJob = startWatchdog(payload)
        }
    }

    private fun startRingingOutputs(volumeLevel: Int, soundUri: String?, isVibrate: Boolean) {
        volumeEnforcer.startEnforcing(volumeLevel)
        audioManager.startAlarm(
            soundUriString = soundUri,
            volumeLevel = volumeLevel,
            manageSystemStreamVolume = false
        )
        if (isVibrate) {
            vibratorManager.startVibration()
        }
    }

    private fun pauseRingingOutputs() {
        volumeEnforcer.stopEnforcing()
        audioManager.stopAlarm()
        vibratorManager.stopVibration()
    }

    private fun deferCollidingAlarm(alarmId: Int) {
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val repo = AlarmRepositoryImpl(this@AlarmForegroundService)
                val scheduler = AlarmSchedulerImpl(this@AlarmForegroundService)
                val alarm = repo.getAlarmById(alarmId) ?: return@runCatching
                val snoozeTime = System.currentTimeMillis() + DEFERRED_SNOOZE_MS
                val deferred = alarm.copy(isEnabled = true, snoozeTriggerMillis = snoozeTime)
                repo.saveAlarm(deferred)
                scheduler.scheduleSnooze(deferred, snoozeTime)
            }
        }
    }

    private fun startWatchdog(payload: AlarmAlertPayload): Job {
        return serviceScope.launch {
            var relaunchBackoffMs = 1000L
            var consecutiveInvisibleAttempts = 0
            var lastWakeLockRenewMs = System.currentTimeMillis()
            while (isSessionActive && isSessionLockActive()) {
                val loopDelay = if (AlarmAlertActivity.isActivityVisible) 1000L else relaunchBackoffMs
                delay(loopDelay)

                try {
                    val callActive = OemDeviceHelper.isPhoneCallActive(this@AlarmForegroundService)
                    if (isAlarmRinging) {
                        if (callActive && !isAudioDeferredByCall) {
                            isAudioDeferredByCall = true
                            pauseRingingOutputs()
                        } else if (!callActive && isAudioDeferredByCall) {
                            isAudioDeferredByCall = false
                            startRingingOutputs(activeVolumeLevel, activeSoundUri, activeIsVibrate)
                        } else if (!callActive) {
                            volumeEnforcer.enforceTargetVolume()
                        }
                        if (System.currentTimeMillis() - lastWakeLockRenewMs >= WAKE_LOCK_RENEW_INTERVAL_MS) {
                            acquireWakeLock()
                            lastWakeLockRenewMs = System.currentTimeMillis()
                        }
                    }

                    if (isSessionActive &&
                        isSessionLockActive() &&
                        !AlarmAlertActivity.isActivityVisible &&
                        !AlarmAlertActivity.isDismissedOrSnoozedGlobally &&
                        !callActive
                    ) {
                        if (consecutiveInvisibleAttempts < 20) {
                            launchAlarmActivity(payload)
                            consecutiveInvisibleAttempts++
                            relaunchBackoffMs = (relaunchBackoffMs * 2).coerceAtMost(15_000L)
                        } else if (isAlarmRinging) {
                            relaunchBackoffMs = 30_000L
                        } else {
                            break
                        }
                    } else if (!callActive) {
                        consecutiveInvisibleAttempts = 0
                        relaunchBackoffMs = 1000L
                    } else {
                        relaunchBackoffMs = 2000L
                    }
                } catch (t: Throwable) {
                    Log.e("AlarmForegroundService", "Watchdog loop safe catch: ${t.message}")
                }
            }
        }
    }

    private fun launchAlarmActivity(payload: AlarmAlertPayload) {
        val alertIntent = AlarmAlertIntentFactory.buildAlertIntent(
            context = this,
            alarmId = payload.alarmId,
            label = payload.label,
            targetPage = payload.startPage,
            startPage = payload.startPage,
            pageCount = payload.pageCount,
            isSnoozeEnabled = true,
            isFreshTrigger = false,
            reorderToFront = true,
            wirdSurahNumber = payload.wirdSurahNumber,
            startAyah = payload.startAyah,
            dailyAyahCount = payload.dailyAyahCount
        )
        AlarmAlertIntentFactory.launchActivityWithBalFallback(
            context = this,
            alertIntent = alertIntent,
            pendingIntentRequestCode = payload.alarmId + 400_000
        )
    }

    private fun startForegroundForRinging(payload: AlarmAlertPayload) {
        // Only start with a silent notification if the Alert Activity is already visible;
        // otherwise post FullScreenIntent immediately so Xiaomi/Vivo/Samsung lockscreens wake up reliably.
        val notification = buildServiceNotification(
            context = this,
            payload = payload,
            isReading = false,
            silentOngoing = AlarmAlertActivity.isActivityVisible
        )
        startForegroundCompat(notification)
    }

    fun buildNotification(
        alarmId: Int,
        label: String,
        startPage: Int,
        pageCount: Int,
        wirdSurahNumber: Int? = null,
        startAyah: Int? = null,
        dailyAyahCount: Int? = null
    ): Notification =
        buildServiceNotification(
            context = this,
            payload = AlarmAlertPayload(
                alarmId = alarmId,
                label = label,
                targetPage = startPage,
                startPage = startPage,
                pageCount = pageCount,
                volumeLevel = activeVolumeLevel,
                isVibrate = activeIsVibrate,
                soundUri = activeSoundUri,
                isSnoozeTrigger = false,
                isSnoozeEnabled = true,
                isFreshTrigger = false,
                wirdSurahNumber = wirdSurahNumber,
                startAyah = startAyah,
                dailyAyahCount = dailyAyahCount
            ),
            isReading = false,
            silentOngoing = false
        )

    fun buildReadingNotification(
        alarmId: Int,
        label: String,
        startPage: Int,
        pageCount: Int,
        wirdSurahNumber: Int? = null,
        startAyah: Int? = null,
        dailyAyahCount: Int? = null
    ): Notification =
        buildServiceNotification(
            context = this,
            payload = AlarmAlertPayload(
                alarmId = alarmId,
                label = label,
                targetPage = startPage,
                startPage = startPage,
                pageCount = pageCount,
                volumeLevel = activeVolumeLevel,
                isVibrate = activeIsVibrate,
                soundUri = activeSoundUri,
                isSnoozeTrigger = false,
                isSnoozeEnabled = true,
                isFreshTrigger = false,
                wirdSurahNumber = wirdSurahNumber,
                startAyah = startAyah,
                dailyAyahCount = dailyAyahCount
            ),
            isReading = true,
            silentOngoing = true
        )

    private fun PersistedAlarmSession.toPayload(): AlarmAlertPayload = AlarmAlertPayload(
        alarmId = alarmId,
        label = label,
        targetPage = startPage,
        startPage = startPage,
        pageCount = pageCount,
        volumeLevel = volumeLevel,
        isVibrate = isVibrate,
        soundUri = soundUri,
        isSnoozeTrigger = false,
        isSnoozeEnabled = true,
        isFreshTrigger = false,
        wirdSurahNumber = wirdSurahNumber,
        startAyah = startAyah,
        dailyAyahCount = dailyAyahCount
    )

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "QuranAlarm:ForegroundServiceWakeLock"
            )
        }
        try {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null
    }

    private fun stopAlarmAndSelf() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isAlarmRingingGlobally = false
        isReadingSessionActiveGlobally = false
        activeSessionAlarmId = null
        isAlarmRinging = false
        isSessionActive = false
        activePayload = null
        watchdogJob?.cancel()
        watchdogJob = null
        serviceJob.cancel()
        volumeEnforcer.stopEnforcing()
        audioManager.destroy()
        vibratorManager.stopVibration()
        releaseWakeLock()
        super.onDestroy()
    }
}
