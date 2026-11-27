package com.quran.alarm.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quran.alarm.alarm.data.device.OemDeviceHelper
import com.quran.alarm.alarm.data.storage.AlarmSessionStore
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory
import com.quran.alarm.alarm.service.AlarmAlertPayload
import com.quran.alarm.alarm.service.AlarmForegroundService
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertViewModel
import com.quran.alarm.alarm.ui.viewmodel.AlarmAlertViewModelFactory
import com.quran.alarm.ui.theme.QuranAlarmTheme
import com.quran.alarm.ui.theme.QuranColors

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

    private var alarmId: Int = AlarmAlertIntentFactory.INVALID_ALARM_ID
    private var alarmLabel: String = AlarmAlertIntentFactory.DEFAULT_ALARM_LABEL
    private var startPage: Int = 1
    private var pageCount: Int = 5
    private var isDismissedOrSnoozed = false
    private var lastRelaunchTimestampMs: Long = 0L

    private val viewModel: AlarmAlertViewModel by viewModels {
        AlarmAlertViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDismissedOrSnoozed = false
        isDismissedOrSnoozedGlobally = false
        setupLockscreenFlags()
        setupImmersiveStickyMode()
        setupAntiBypassBackBlocker()

        val payload = resolveLaunchPayload(intent)
        if (payload == null) {
            finish()
            return
        }
        applyPayloadFields(payload)

        if (savedInstanceState == null || !viewModel.isInitialized) {
            val effectiveFreshTrigger = if (savedInstanceState != null) false else payload.isFreshTrigger
            viewModel.initialize(
                alarmId = payload.alarmId,
                alarmLabel = payload.label,
                rawStartPage = payload.startPage,
                rawPageCount = payload.pageCount,
                isSnoozeEnabled = payload.isSnoozeEnabled,
                isFreshTrigger = effectiveFreshTrigger,
                wirdSurahNumber = payload.wirdSurahNumber,
                startAyah = payload.startAyah,
                dailyAyahCount = payload.dailyAyahCount
            )
        }

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                QuranAlarmTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = QuranColors.BgPrimary
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
                                if (alarmId >= 0) {
                                    AlarmForegroundService.isReadingSessionActiveGlobally = true
                                }
                                viewModel.startReadingWird()
                                val stateSnapshot = viewModel.uiState.value
                                runCatching {
                                    val silenceIntent = AlarmAlertIntentFactory.buildSilenceServiceIntent(
                                        context = this@AlarmAlertActivity,
                                        alarmId = alarmId,
                                        label = alarmLabel,
                                        startPage = startPage,
                                        pageCount = pageCount,
                                        wirdSurahNumber = stateSnapshot.wirdSurahNumber,
                                        startAyah = stateSnapshot.startAyah,
                                        dailyAyahCount = stateSnapshot.dailyAyahCount
                                    )
                                    startService(silenceIntent)
                                }
                            },
                            onSnooze = {
                                if (beginHandlingAction()) {
                                    viewModel.snooze(minutes = 5) {
                                        stopAlarmService()
                                        finish()
                                    }
                                }
                            },
                            onDismissAlarm = {
                                if (viewModel.uiState.value.isDismissEnabled && beginHandlingAction()) {
                                    viewModel.completeWird {
                                        stopAlarmService()
                                        finish()
                                    }
                                }
                            },
                            onRetryLoad = { viewModel.retryLoadPage() },
                            onEmergencyExit = {
                                if ((viewModel.uiState.value.loadError != null || alarmId < 0) && beginHandlingAction()) {
                                    viewModel.emergencyExitOnError {
                                        stopAlarmService()
                                        finish()
                                    }
                                }
                            },
                            onExitPreview = {
                                if (alarmId < 0 && beginHandlingAction()) {
                                    stopAlarmService()
                                    finish()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val payload = resolveLaunchPayload(intent)
        if (payload == null) {
            if (isDismissedOrSnoozed || isDismissedOrSnoozedGlobally) {
                finish()
            }
            return
        }
        val isNewOrFreshTrigger = payload.isFreshTrigger || payload.alarmId != alarmId
        if (!isNewOrFreshTrigger && (isDismissedOrSnoozed || isDismissedOrSnoozedGlobally)) {
            finish()
            return
        }
        if (isNewOrFreshTrigger || !viewModel.isInitialized) {
            isDismissedOrSnoozed = false
            isDismissedOrSnoozedGlobally = false
            applyPayloadFields(payload)
            viewModel.initialize(
                alarmId = payload.alarmId,
                alarmLabel = payload.label,
                rawStartPage = payload.startPage,
                rawPageCount = payload.pageCount,
                isSnoozeEnabled = payload.isSnoozeEnabled,
                isFreshTrigger = payload.isFreshTrigger,
                wirdSurahNumber = payload.wirdSurahNumber,
                startAyah = payload.startAyah,
                dailyAyahCount = payload.dailyAyahCount
            )
        }
    }

    private fun resolveLaunchPayload(launchIntent: Intent): AlarmAlertPayload? {
        val parsed = AlarmAlertIntentFactory.parsePayload(launchIntent)
        if (parsed.alarmId != AlarmAlertIntentFactory.INVALID_ALARM_ID) {
            return parsed
        }
        val activeSession = AlarmSessionStore.fromContext(applicationContext).loadSession() ?: return null
        if (activeSession.alarmId < 0) return null
        return AlarmAlertPayload(
            alarmId = activeSession.alarmId,
            label = activeSession.label,
            targetPage = activeSession.startPage,
            startPage = activeSession.startPage,
            pageCount = activeSession.pageCount,
            volumeLevel = activeSession.volumeLevel,
            isVibrate = activeSession.isVibrate,
            soundUri = activeSession.soundUri,
            isSnoozeTrigger = false,
            isSnoozeEnabled = true,
            isFreshTrigger = false,
            wirdSurahNumber = activeSession.wirdSurahNumber,
            startAyah = activeSession.startAyah,
            dailyAyahCount = activeSession.dailyAyahCount
        )
    }

    private fun applyPayloadFields(payload: AlarmAlertPayload) {
        alarmId = payload.alarmId
        alarmLabel = payload.label
        startPage = payload.startPage
        pageCount = payload.pageCount
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModel.onTrimMemory(level)
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
                if (alarmId < 0 && beginHandlingAction()) {
                    stopAlarmService()
                    finish()
                }
                // Strictly disallow back button exit during active real alarm/reading session
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> true
            KeyEvent.KEYCODE_BACK -> {
                if (alarmId < 0 && beginHandlingAction()) {
                    stopAlarmService()
                    finish()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        setupImmersiveStickyMode()
        if (AlarmForegroundService.isSessionLockActive() || alarmId < 0) {
            runCatching {
                startService(
                    Intent(this, AlarmForegroundService::class.java).apply {
                        action = AlarmForegroundService.ACTION_ALERT_VISIBLE
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        if (!isChangingConfigurations && shouldRelaunchToFront()) {
            relaunchToFront()
        }
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        if (!isChangingConfigurations) {
            if (alarmId < 0 && !isDismissedOrSnoozed) {
                isDismissedOrSnoozed = true
                stopAlarmService()
                finish()
            } else if (shouldRelaunchToFront()) {
                relaunchToFront()
            }
        }
    }

    override fun onDestroy() {
        isActivityVisible = false
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldRelaunchToFront()) {
            relaunchToFront()
        }
    }

    private fun beginHandlingAction(): Boolean {
        if (isDismissedOrSnoozed || isDismissedOrSnoozedGlobally) return false
        isDismissedOrSnoozed = true
        isDismissedOrSnoozedGlobally = true
        return true
    }

    private fun stopAlarmService() {
        AlarmForegroundService.isAlarmRingingGlobally = false
        AlarmForegroundService.isReadingSessionActiveGlobally = false
        runCatching {
            val stopIntent = Intent(this, AlarmForegroundService::class.java).apply {
                action = AlarmForegroundService.ACTION_STOP_ALARM
            }
            startService(stopIntent)
        }
    }

    private fun shouldRelaunchToFront(): Boolean =
        alarmId >= 0 &&
            !isDismissedOrSnoozed &&
            !isDismissedOrSnoozedGlobally &&
            AlarmForegroundService.isSessionLockActive() &&
            !OemDeviceHelper.isPhoneCallActive(this)

    private fun relaunchToFront() {
        if (!shouldRelaunchToFront()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRelaunchTimestampMs < 600L) return
        lastRelaunchTimestampMs = now
        val stateSnapshot = viewModel.uiState.value
        try {
            val intent = AlarmAlertIntentFactory.buildAlertIntent(
                context = this,
                alarmId = alarmId,
                label = alarmLabel,
                targetPage = startPage,
                startPage = startPage,
                pageCount = pageCount,
                isSnoozeEnabled = stateSnapshot.isSnoozeEnabled,
                isFreshTrigger = false,
                reorderToFront = true,
                wirdSurahNumber = stateSnapshot.wirdSurahNumber,
                startAyah = stateSnapshot.startAyah,
                dailyAyahCount = stateSnapshot.dailyAyahCount
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
