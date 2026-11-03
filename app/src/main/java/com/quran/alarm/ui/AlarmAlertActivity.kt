package com.quran.alarm.ui

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.service.AlarmForegroundService
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * High-priority lock screen activity launched via FullScreenIntent when the alarm fires.
 *
 * Implements strict anti-bypass lockscreen behavior:
 * - Hides navigation bars and status bar in sticky immersive mode.
 * - Blocks physical volume down / up / mute keys.
 * - Blocks back button via OnBackPressedCallback.
 * - Automatically brings activity to front if user attempts Home or Recents.
 * - Displays live device time and the user's sequential Quran Wird range.
 * - Automatically advances the Wird to the next portion upon session dismissal.
 */
class AlarmAlertActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isActivityVisible: Boolean = false
    }

    private var alarmId: Int = 0
    private var alarmLabel: String = "ورد القرآن الكريم"
    private var startPage: Int = 1
    private var pageCount: Int = 5
    private var isDismissedOrSnoozed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockscreenFlags()
        setupImmersiveStickyMode()
        setupAntiBypassBackBlocker()

        alarmId = intent.getIntExtra(AlarmForegroundService.EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(AlarmForegroundService.EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
        val targetPage = intent.getIntExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, 1)
        startPage = intent.getIntExtra(AlarmForegroundService.EXTRA_START_PAGE, targetPage)
        pageCount = intent.getIntExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 5)

        val endPage = ((startPage + pageCount - 2) % 604) + 1

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Deep Night Navy
                ) {
                    AlarmAlertScreen(
                        label = alarmLabel,
                        startPage = startPage,
                        endPage = endPage,
                        pageCount = pageCount,
                        onSnooze = { snoozeAlarm(minutes = 5) },
                        onDismissAlarm = { completeAndAdvanceWird() }
                    )
                }
            }
        }
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupImmersiveStickyMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupAntiBypassBackBlocker() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Strictly disallow back button exit during alarm ringing
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Prevent muting or disabling alarm via physical volume keys or navigation keys
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        setupImmersiveStickyMode()
        try {
            startLockTask()
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }

    override fun onDestroy() {
        isActivityVisible = false
        try {
            stopLockTask()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isDismissedOrSnoozed) {
            relaunchToFront()
        }
    }

    private fun relaunchToFront() {
        val intent = Intent(this, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmForegroundService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, startPage)
            putExtra(AlarmForegroundService.EXTRA_START_PAGE, startPage)
            putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, pageCount)
        }
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options.toBundle()
        )
        try {
            pendingIntent.send()
        } catch (_: Exception) {
            try {
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isDismissedOrSnoozed) {
            // Re-apply sticky immersive mode
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun snoozeAlarm(minutes: Int = 5) {
        isDismissedOrSnoozed = true
        val scheduler = AlarmSchedulerImpl(this)
        val snoozeTime = System.currentTimeMillis() + (minutes * 60_000L)
        val cal = Calendar.getInstance().apply { timeInMillis = snoozeTime }

        val snoozeItem = AlarmItem(
            id = alarmId,
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            repeatDays = emptySet(),
            isEnabled = true,
            isVibrationEnabled = true,
            label = alarmLabel,
            targetPage = startPage,
            startPage = startPage,
            pageCount = pageCount,
            explicitTriggerMillis = snoozeTime
        )
        scheduler.schedule(snoozeItem)

        // Stop current ringing
        val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        try {
            stopLockTask()
        } catch (_: Exception) {}
        finish()
    }

    private fun completeAndAdvanceWird() {
        isDismissedOrSnoozed = true

        // Automatically advance Wird to the next set of pages for the next session
        val repository = AlarmRepositoryImpl(this)
        val scheduler = AlarmSchedulerImpl(this)
        val currentAlarm = repository.getAlarmById(alarmId)
        if (currentAlarm != null) {
            val advancedAlarm = currentAlarm.advanceToNextWird()
            repository.saveAlarm(advancedAlarm)
            if (advancedAlarm.isEnabled && advancedAlarm.repeatDays.isNotEmpty()) {
                scheduler.schedule(advancedAlarm)
            }
        }

        val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        try {
            stopLockTask()
        } catch (_: Exception) {}
        finish()
    }
}

@Composable
fun AlarmAlertScreen(
    label: String,
    startPage: Int,
    endPage: Int,
    pageCount: Int,
    onSnooze: () -> Unit,
    onDismissAlarm: () -> Unit
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: Actual Live Date & Big Digital Clock
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(
                text = AlarmUiFormatters.formatCurrentDate(currentTimeMillis),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = AlarmUiFormatters.formatCurrentDigitalTime(currentTimeMillis),
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulseScale)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Center: Sacred Wird & Page Range Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "الورد اليومي: من صفحة $startPage إلى صفحة $endPage",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "($pageCount صفحات متتالية)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تم قفل أزرار التنقل والصوت. عند الانتهاء سينتقل المنبه تلقائياً إلى الورد التالي.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom Actions: Snooze ("غفوة") and Action Button ("إتمام الورد / إيقاف")
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Snooze Button
            Button(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
            ) {
                Text(
                    text = "غفوة (5 دقائق)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Completion Button
            Button(
                onClick = onDismissAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text(
                    text = "إتمام الورد وإيقاف المنبه",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
