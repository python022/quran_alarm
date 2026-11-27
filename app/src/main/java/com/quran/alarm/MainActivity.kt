package com.quran.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.quran.alarm.alarm.data.device.OemDeviceHelper
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.ui.viewmodel.AlarmViewModel
import com.quran.alarm.alarm.ui.viewmodel.AlarmViewModelFactory
import com.quran.alarm.ui.components.AddEditAlarmScreen
import com.quran.alarm.ui.components.AlarmListScreen
import com.quran.alarm.ui.theme.QuranAlarmTheme
import com.quran.alarm.ui.theme.QuranColors
import kotlinx.coroutines.delay

/**
 * Main Activity hosting the Quran Alarm application.
 * Follows Android Clean Architecture and delegates all state to [AlarmViewModel].
 */
class MainActivity : ComponentActivity() {

    private enum class GuidedPermissionStep {
        FULL_SCREEN_INTENT,
        OVERLAY
    }

    private companion object {
        const val PREFS_PERMISSIONS = "quran_alarm_permission_prefs"
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications_once"
        const val KEY_PENDING_GUIDED_STEP = "pending_guided_step"
    }

    private var pendingGuidedStep: GuidedPermissionStep? = null
    private var hasResumedOnce: Boolean = false

    private val viewModel: AlarmViewModel by viewModels {
        AlarmViewModelFactory(
            repository = AlarmRepositoryImpl(applicationContext),
            scheduler = AlarmSchedulerImpl(applicationContext)
        )
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.checkOverlayPermission(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        pendingGuidedStep = savedInstanceState
            ?.getString(KEY_PENDING_GUIDED_STEP)
            ?.let { runCatching { GuidedPermissionStep.valueOf(it) }.getOrNull() }
        if (savedInstanceState == null) {
            requestNotificationsPermissionIfNeeded(fromWizard = false)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // Periodic live countdown ticker
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000)
                    viewModel.updateCountdown()
                }
            }

            // Show any user notifications / toasts
            LaunchedEffect(uiState.userMessage) {
                uiState.userMessage?.let { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearUserMessage()
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                QuranAlarmTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = QuranColors.BgPrimary
                    ) {
                        if (uiState.isAddEditDialogOpen) {
                            AddEditAlarmScreen(
                                alarmToEdit = uiState.editingAlarm,
                                existingAlarmIds = uiState.alarms.map { it.id }.toSet(),
                                onDismiss = { viewModel.dismissDialog() },
                                onSave = { alarm -> viewModel.saveAlarm(alarm) },
                                onDelete = { alarmId -> viewModel.deleteAlarm(alarmId) }
                            )
                        } else {
                            AlarmListScreen(
                                uiState = uiState,
                                onAddClick = { viewModel.openAddDialog() },
                                onAlarmClick = { alarm -> viewModel.openEditDialog(alarm) },
                                onToggleAlarm = { alarm, isEnabled ->
                                    viewModel.toggleAlarm(alarm, isEnabled)
                                },
                                onFixMissingCorePermission = { startGuidedCorePermissionFlow() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingGuidedStep?.let { outState.putString(KEY_PENDING_GUIDED_STEP, it.name) }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            viewModel.loadAlarms()
        } else {
            hasResumedOnce = true
        }
        viewModel.checkOverlayPermission(this)

        val step = pendingGuidedStep
        pendingGuidedStep = null
        if (step != null) {
            val status = OemDeviceHelper.getReadinessStatus(this)
            when (step) {
                GuidedPermissionStep.FULL_SCREEN_INTENT -> {
                    if (status.isFullScreenIntentGranted) {
                        if (!status.isOverlayGranted) {
                            pendingGuidedStep = GuidedPermissionStep.OVERLAY
                            OemDeviceHelper.openOverlaySettings(this)
                        } else {
                            Toast.makeText(this, "اكتمل إعداد أذونات المنبه الأساسية ✓", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                GuidedPermissionStep.OVERLAY -> {
                    if (status.isOverlayGranted) {
                        if (!status.isFullScreenIntentGranted) {
                            pendingGuidedStep = GuidedPermissionStep.FULL_SCREEN_INTENT
                            OemDeviceHelper.openFullScreenIntentSettings(this)
                        } else {
                            Toast.makeText(this, "اكتمل إعداد أذونات المنبه الأساسية ✓", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun startGuidedCorePermissionFlow() {
        val status = OemDeviceHelper.getReadinessStatus(this)
        when {
            !status.isFullScreenIntentGranted -> {
                pendingGuidedStep = GuidedPermissionStep.FULL_SCREEN_INTENT
                OemDeviceHelper.openFullScreenIntentSettings(this)
            }
            !status.isOverlayGranted -> {
                pendingGuidedStep = GuidedPermissionStep.OVERLAY
                OemDeviceHelper.openOverlaySettings(this)
            }
        }
    }

    fun requestNotificationsPermissionIfNeeded(fromWizard: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                val prefs = getSharedPreferences(PREFS_PERMISSIONS, Context.MODE_PRIVATE)
                val alreadyAsked = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
                val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                if (fromWizard && alreadyAsked && !showRationale) {
                    OemDeviceHelper.openNotificationSettings(this)
                } else {
                    prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
