package com.quran.alarm.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.service.AlarmForegroundService

/**
 * High-priority lock screen activity launched via FullScreenIntent when the alarm fires.
 *
 * Implements strict lockscreen display flags:
 * - Displays on top of keyguard / lockscreen.
 * - Turns screen on immediately.
 * - Keeps screen on during the reading session.
 * - Intercepts physical volume keys to prevent muting without recitation.
 */
class AlarmAlertActivity : ComponentActivity() {

    private var alarmId: Int = 0
    private var alarmLabel: String = "ورد القرآن الكريم"
    private var targetPage: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockscreenFlags()

        alarmId = intent.getIntExtra(AlarmForegroundService.EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(AlarmForegroundService.EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
        targetPage = intent.getIntExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, 1)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Deep Night Navy
                ) {
                    AlarmAlertScreen(
                        label = alarmLabel,
                        targetPage = targetPage,
                        onDismissAlarm = { stopAlarmAndDismiss() }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Prevent muting or disabling alarm via physical volume keys
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun stopAlarmAndDismiss() {
        val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }
}

@Composable
fun AlarmAlertScreen(
    label: String,
    targetPage: Int,
    onDismissAlarm: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = label,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "حان موعد الاستيقاظ وورد القرآن الكريم",
                fontSize = 15.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }

        // Middle Card: Page Details
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "صفحة المصحف الشريف",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "صفحة $targetPage",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF38BDF8)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "المنبه ينطلق بأقصى صوت. في المراحل القادمة سيتم الكتم التلقائي بمجرد تلاوة السطور بالعين.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        // Bottom Action Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onDismissAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text(
                    text = "إسكات المنبه (تجربة المرحلة 1)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

