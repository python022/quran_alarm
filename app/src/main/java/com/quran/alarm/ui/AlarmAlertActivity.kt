package com.quran.alarm.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory
import com.quran.alarm.alarm.service.AlarmForegroundService
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertStage
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertViewModel
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertViewModelFactory
import com.quran.alarm.quran.data.QuranPageManager

/**
 * High-priority lock screen activity launched via FullScreenIntent and direct intent when the alarm fires.
 *
 * Implements strict anti-bypass lockscreen behavior and displays the authentic Madinah Mushaf page:
 * - Hides navigation bars and status bar in sticky immersive mode while respecting safe insets.
 * - Blocks physical volume down / up / mute keys.
 * - Disables back button exit via OnBackPressedCallback.
 * - Relaunches activity to front immediately on user leave hint, pause, or stop throughout the entire
 *   active session (both RINGING and MUSHAF_READING until Wird completion or Snooze).
 * - Automatically advances the Wird to the next portion upon session completion.
 */
class AlarmAlertActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isActivityVisible: Boolean = false

        @Volatile
        var isDismissedOrSnoozedGlobally: Boolean = false
    }

    private var alarmId: Int = 0
    private var alarmLabel: String = "ورد القرآن الكريم"
    private var startPage: Int = 1
    private var pageCount: Int = 5
    private var isDismissedOrSnoozed = false

    private val pageManager by lazy { QuranPageManager(applicationContext) }
    private val viewModel: AlarmAlertViewModel by viewModels {
        AlarmAlertViewModelFactory(applicationContext, pageManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDismissedOrSnoozed = false
        isDismissedOrSnoozedGlobally = false
        setupLockscreenFlags()
        setupImmersiveStickyMode()
        setupAntiBypassBackBlocker()

        alarmId = intent.getIntExtra(AlarmForegroundService.EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(AlarmForegroundService.EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
        val targetPage = intent.getIntExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, 1).coerceIn(1, 604)
        startPage = intent.getIntExtra(AlarmForegroundService.EXTRA_START_PAGE, targetPage).coerceIn(1, 604)
        pageCount = intent.getIntExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 5).coerceAtLeast(1)
        val isSnoozeEnabled = intent.getBooleanExtra(AlarmAlertIntentFactory.EXTRA_IS_SNOOZE_ENABLED, true)

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
                        onLineClicked = { viewModel.onLineClicked(it, allowDirectJump = false) },
                        onNextStep = { viewModel.advanceToNextLineOrPage() },
                        onJumpToIncompletePage = { viewModel.goToFirstIncompletePage() },
                        onStartReading = {
                            AlarmForegroundService.isReadingSessionActiveGlobally = true
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
                            if (!isDismissedOrSnoozed && !isDismissedOrSnoozedGlobally) {
                                isDismissedOrSnoozed = true
                                isDismissedOrSnoozedGlobally = true
                                AlarmForegroundService.isAlarmRingingGlobally = false
                                AlarmForegroundService.isReadingSessionActiveGlobally = false

                                val stopIntent = Intent(this@AlarmAlertActivity, AlarmForegroundService::class.java).apply {
                                    action = AlarmForegroundService.ACTION_STOP_ALARM
                                }
                                startService(stopIntent)

                                viewModel.snooze(minutes = 5) {
                                    finish()
                                }
                            }
                        },
                        onDismissAlarm = {
                            if (!isDismissedOrSnoozed && !isDismissedOrSnoozedGlobally) {
                                isDismissedOrSnoozed = true
                                isDismissedOrSnoozedGlobally = true
                                AlarmForegroundService.isAlarmRingingGlobally = false
                                AlarmForegroundService.isReadingSessionActiveGlobally = false

                                val stopIntent = Intent(this@AlarmAlertActivity, AlarmForegroundService::class.java).apply {
                                    action = AlarmForegroundService.ACTION_STOP_ALARM
                                }
                                startService(stopIntent)

                                viewModel.completeWird {
                                    finish()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isDismissedOrSnoozed || isDismissedOrSnoozedGlobally) {
            finish()
            return
        }
        val newAlarmId = intent.getIntExtra(AlarmForegroundService.EXTRA_ALARM_ID, 0)
        val newAlarmLabel = intent.getStringExtra(AlarmForegroundService.EXTRA_ALARM_LABEL) ?: "ورد القرآن الكريم"
        val targetPage = intent.getIntExtra(AlarmForegroundService.EXTRA_TARGET_PAGE, 1).coerceIn(1, 604)
        val newStartPage = intent.getIntExtra(AlarmForegroundService.EXTRA_START_PAGE, targetPage).coerceIn(1, 604)
        val newPageCount = intent.getIntExtra(AlarmForegroundService.EXTRA_PAGE_COUNT, 5).coerceAtLeast(1)
        val isSnoozeEnabled = intent.getBooleanExtra(AlarmAlertIntentFactory.EXTRA_IS_SNOOZE_ENABLED, true)
        val isFreshTrigger = intent.getBooleanExtra(AlarmAlertIntentFactory.EXTRA_IS_FRESH_TRIGGER, false)

        if (newAlarmId != alarmId || isFreshTrigger || viewModel.uiState.value.stage == AlarmAlertStage.RINGING) {
            isDismissedOrSnoozed = false
            alarmId = newAlarmId
            alarmLabel = newAlarmLabel
            startPage = newStartPage
            pageCount = newPageCount
            viewModel.initialize(alarmId, alarmLabel, startPage, pageCount, isSnoozeEnabled)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        pageManager.onTrimMemory(level)
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
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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
                // Strictly disallow back button exit during active alarm/reading session
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_BACK -> true
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
        if (shouldRelaunchToFront()) {
            relaunchToFront()
        }
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        if (shouldRelaunchToFront()) {
            relaunchToFront()
        }
    }

    override fun onDestroy() {
        isActivityVisible = false
        if (!isChangingConfigurations) {
            pageManager.clearCache()
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldRelaunchToFront()) {
            relaunchToFront()
        }
    }

    private fun shouldRelaunchToFront(): Boolean =
        !isDismissedOrSnoozed &&
            !isDismissedOrSnoozedGlobally &&
            AlarmForegroundService.isSessionLockActive()

    private fun relaunchToFront() {
        if (!shouldRelaunchToFront()) return
        try {
            val intent = AlarmAlertIntentFactory.buildAlertIntent(
                context = this,
                alarmId = alarmId,
                label = alarmLabel,
                targetPage = startPage,
                startPage = startPage,
                pageCount = pageCount,
                isSnoozeEnabled = viewModel.uiState.value.isSnoozeEnabled,
                isFreshTrigger = false,
                reorderToFront = true
            )
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e("AlarmAlertActivity", "relaunchToFront error: ${t.message}")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isDismissedOrSnoozed && !isDismissedOrSnoozedGlobally) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
