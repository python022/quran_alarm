package com.quran.alarm.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertStage
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertViewModel
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertViewModelFactory
import com.quran.alarm.alarm.ui.viewmodel.MushafReaderUiState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.service.AlarmForegroundService
import com.quran.alarm.quran.data.AyahInfoDatabase
import com.quran.alarm.quran.data.AyahInfoLineBoundsRepositoryImpl
import com.quran.alarm.quran.data.QuranPageManager
import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.ui.QuranPageView
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * High-priority lock screen activity launched via FullScreenIntent and direct intent when the alarm fires.
 *
 * Implements strict anti-bypass lockscreen behavior and displays the authentic Madinah Mushaf page:
 * - Hides navigation bars and status bar in sticky immersive mode.
 * - Blocks physical volume down / up / mute keys.
 * - Disables back button exit via OnBackPressedCallback.
 * - Relaunches activity to front immediately on user leave hint, pause, or stop.
 * - Displays the authentic 15-line Madinah Mushaf page texture and active line highlighting.
 * - Automatically advances the Wird to the next portion upon session completion.
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

    private lateinit var pageManager: QuranPageManager
    private val viewModel: AlarmAlertViewModel by viewModels {
        AlarmAlertViewModelFactory(this, pageManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockscreenFlags()
        setupImmersiveStickyMode()
        setupAntiBypassBackBlocker()

        pageManager = QuranPageManager(this)

        alarmId = intent.getIntExtra(AlarmForegroundService.EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(AlarmForegroundService.EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
        val targetPage = intent.getIntExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, 1).coerceIn(1, 604)
        startPage = intent.getIntExtra(AlarmForegroundService.EXTRA_START_PAGE, targetPage).coerceIn(1, 604)
        pageCount = intent.getIntExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 5).coerceAtLeast(1)
        val isSnoozeEnabled = intent.getBooleanExtra("com.quran.alarm.EXTRA_IS_SNOOZE_ENABLED", true)

        if (savedInstanceState == null) {
            viewModel.initialize(alarmId, alarmLabel, startPage, pageCount, isSnoozeEnabled)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    AlarmAlertScreen(
                        uiState = uiState,
                        onPrevPage = { viewModel.goToPreviousPage() },
                        onNextPage = { viewModel.goToNextPage() },
                        onLineClicked = { viewModel.onLineClicked(it) },
                        onNextStep = { viewModel.advanceToNextLineOrPage() },
                        onStartReading = {
                            viewModel.startReadingWird()
                            val silenceIntent = Intent(this@AlarmAlertActivity, AlarmForegroundService::class.java).apply {
                                action = AlarmForegroundService.ACTION_SILENCE_RINGING
                                putExtra(AlarmForegroundService.EXTRA_ALARM_ID, alarmId)
                                putExtra(AlarmForegroundService.EXTRA_ALARM_LABEL, alarmLabel)
                                putExtra(AlarmForegroundService.EXTRA_START_PAGE, startPage)
                                putExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, pageCount)
                            }
                            startService(silenceIntent)
                        },
                        onSnooze = {
                            viewModel.snooze(minutes = 5) {
                                stopAlarmServiceAndFinish()
                            }
                        },
                        onDismissAlarm = {
                            viewModel.completeWird {
                                stopAlarmServiceAndFinish()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::pageManager.isInitialized) {
            pageManager.onTrimMemory(level)
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
        val params = window.attributes
        params.screenBrightness = 0.85f
        window.attributes = params
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
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        if (!isDismissedOrSnoozed) {
            relaunchToFront()
        }
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        if (!isDismissedOrSnoozed) {
            relaunchToFront()
        }
    }

    override fun onDestroy() {
        isActivityVisible = false
        if (::pageManager.isInitialized) {
            pageManager.clearCache()
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isDismissedOrSnoozed) {
            relaunchToFront()
        }
    }

    private fun relaunchToFront() {
        try {
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
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e("AlarmAlertActivity", "relaunchToFront error: ${t.message}")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isDismissedOrSnoozed) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private var isDismissedOrSnoozed = false

    private fun stopAlarmServiceAndFinish() {
        isDismissedOrSnoozed = true
        val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }
}

@Composable
fun AlarmAlertScreen(
    uiState: MushafReaderUiState,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onLineClicked: (Int) -> Unit,
    onNextStep: () -> Unit,
    onStartReading: () -> Unit,
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

    if (uiState.stage == AlarmAlertStage.MUSHAF_READING) {
        // Fullscreen Madinah Mushaf Reader with authentic paper illumination
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFFDF5))
        ) {
            // Top Reader Bar
            Surface(
                color = Color(0xFF1E293B),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPrevPage,
                        enabled = uiState.hasPrevPageInWird,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("السابقة", fontSize = 12.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "صفحة ${uiState.currentPage} من 604",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "السطر ${uiState.activeLine} من ${uiState.pageMaxLines}",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981)
                        )
                    }

                    OutlinedButton(
                        onClick = onNextPage,
                        enabled = uiState.hasNextPageInWird,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("التالية", fontSize = 12.sp)
                    }
                }
            }

            // Main Mushaf Page Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                QuranPageView(
                    pageNumber = uiState.currentPage,
                    pageBitmap = uiState.currentBitmap,
                    lines = uiState.currentLines,
                    activeLine = uiState.activeLine,
                    onLineClicked = onLineClicked,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Actions Control Bar
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onNextStep,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (uiState.activeLine < uiState.lastReadableLineOnPage) {
                                "السطر التالي"
                            } else if (uiState.hasNextPageInWird) {
                                "الصفحة التالية"
                            } else {
                                "إتمام الورد"
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    // Law 6 in AGENTS.md: Dismiss button strictly disabled until wird is completed
                    Button(
                        onClick = onDismissAlarm,
                        enabled = uiState.isDismissEnabled,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            disabledContainerColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (uiState.isDismissEnabled) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إتمام الورد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (uiState.isDismissEnabled) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    } else {
        // Dedicated Ringing Stage Screen before entering Quran recitation
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 20.dp)
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
                    fontSize = 68.sp,
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
                        text = uiState.alarmLabel,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ورد اليوم: صفحة ${uiState.startPage} إلى ${uiState.endPage}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "(${uiState.pageCount} صفحات متتالية)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Prominent Hero Action: Start Reading Wird (Silences sound & vibration, enters Mushaf)
                Button(
                    onClick = onStartReading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text(
                        text = "📖 بدء قراءة الورد",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Snooze option (if enabled)
                if (uiState.isSnoozeEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text(
                            text = "غفوة (5 دقائق)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}