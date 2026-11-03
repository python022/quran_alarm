package com.quran.alarm.alarm.service

import android.app.ActivityOptions
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
import com.quran.alarm.ui.AlarmAlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that plays the alarm sound, vibrates continuously, enforces max volume,
 * and maintains the ongoing notification and lockscreen launch window.
 *
 * Uses foregroundServiceType="mediaPlayback" to comply with Android 14+ background execution limits.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.quran.alarm.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.quran.alarm.ACTION_STOP_ALARM"

        const val EXTRA_ALARM_ID = "com.quran.alarm.EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "com.quran.alarm.EXTRA_ALARM_LABEL"
        const val EXTRA_TARGET_PAGE = "com.quran.alarm.EXTRA_TARGET_PAGE"
        const val EXTRA_START_PAGE = "com.quran.alarm.EXTRA_START_PAGE"
        const val EXTRA_PAGE_COUNT = "com.quran.alarm.EXTRA_PAGE_COUNT"
        const val EXTRA_VOLUME_LEVEL = "com.quran.alarm.EXTRA_VOLUME_LEVEL"
        const val EXTRA_IS_VIBRATE = "com.quran.alarm.EXTRA_IS_VIBRATE"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "quran_alarm_channel"
        const val CHANNEL_NAME = "منبه القرآن الكريم"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: SmartAlarmAudioManager
    private lateinit var vibratorManager: AlarmVibratorManager
    private lateinit var volumeEnforcer: VolumeEnforcer

    private var serviceJob = Job()
    private var serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var isAlarmRinging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = SmartAlarmAudioManager(this)
        vibratorManager = AlarmVibratorManager(this)
        volumeEnforcer = VolumeEnforcer(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_STOP_ALARM -> {
                isAlarmRinging = false
                serviceJob.cancel()
                serviceJob = Job()
                serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
                volumeEnforcer.stopEnforcing()
                audioManager.stopAlarm()
                vibratorManager.stopVibration()
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM, null -> {
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
                val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
                val targetPage = intent.getIntExtra(EXTRA_TARGET_PAGE, 1)
                val startPage = intent.getIntExtra(EXTRA_START_PAGE, targetPage)
                val pageCount = intent.getIntExtra(EXTRA_PAGE_COUNT, 5)
                val volumeLevel = intent.getIntExtra(EXTRA_VOLUME_LEVEL, 100).coerceIn(1, 100)
                val isVibrate = intent.getBooleanExtra(EXTRA_IS_VIBRATE, true)

                val notification = buildNotification(alarmId, label, startPage, pageCount)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                // Enforce user-configured volume level to prevent muting bypass
                volumeEnforcer.startEnforcing(volumeLevel)

                // Start alarm tone on STREAM_ALARM with configured volume
                audioManager.startAlarm(volumeLevel = volumeLevel)

                // Start high-intensity haptic vibration loop
                if (isVibrate) {
                    vibratorManager.startVibration()
                }

                // Start watchdog coroutine to guarantee AlarmAlertActivity stays front and center
                isAlarmRinging = true
                startWatchdog(alarmId, label, startPage, pageCount)
            }
        }

        return START_STICKY
    }

    private fun startWatchdog(alarmId: Int, label: String, startPage: Int, pageCount: Int) {
        serviceScope.launch {
            while (isAlarmRinging) {
                delay(1500)
                try {
                    if (isAlarmRinging && !AlarmAlertActivity.isActivityVisible) {
                        launchAlarmActivity(alarmId, label, startPage, pageCount)
                    }
                } catch (t: Throwable) {
                    Log.e("AlarmForegroundService", "Watchdog loop safe catch: ${t.message}")
                }
            }
        }
    }

    private fun launchAlarmActivity(alarmId: Int, label: String, startPage: Int, pageCount: Int) {
        try {
            val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, label)
                putExtra(EXTRA_TARGET_PAGE, startPage)
                putExtra(EXTRA_START_PAGE, startPage)
                putExtra(EXTRA_PAGE_COUNT, pageCount)
            }
            val pi = PendingIntent.getActivity(
                this,
                alarmId + 50000,
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val sendOptions = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                sendOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
            try {
                pi.send(this, 0, null, null, null, null, sendOptions.toBundle())
            } catch (e: Exception) {
                Log.w("AlarmForegroundService", "pi.send failed, falling back to startActivity: ${e.message}")
                try {
                    startActivity(alertIntent)
                } catch (_: Exception) {}
            }
        } catch (t: Throwable) {
            Log.e("AlarmForegroundService", "launchAlarmActivity error caught safely: ${t.message}")
        }
    }

    fun buildNotification(alarmId: Int, label: String, startPage: Int, pageCount: Int): Notification {
        val endPage = ((startPage + pageCount - 2) % 604) + 1
        val fullScreenIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_TARGET_PAGE, startPage)
            putExtra(EXTRA_START_PAGE, startPage)
            putExtra(EXTRA_PAGE_COUNT, pageCount)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            alarmId + 10000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("منبه القرآن الكريم")
            .setContentText("$label - الورد: صفحة $startPage إلى $endPage")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.mipmap.ic_launcher, "إيقاف", dismissPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
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
                setSound(null, null) // Explicitly silence notification channel to avoid system chime overlap
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "QuranAlarm:ForegroundServiceWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes maximum alarm duration
            }
        }
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
        isAlarmRinging = false
        serviceJob.cancel()
        volumeEnforcer.stopEnforcing()
        audioManager.stopAlarm()
        vibratorManager.stopVibration()
        releaseWakeLock()
        super.onDestroy()
    }
}
