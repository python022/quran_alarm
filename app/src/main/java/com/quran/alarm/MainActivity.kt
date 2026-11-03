package com.quran.alarm

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.ui.viewmodel.AlarmViewModel
import com.quran.alarm.alarm.ui.viewmodel.AlarmViewModelFactory
import com.quran.alarm.ui.components.AlarmListScreen
import kotlinx.coroutines.delay

/**
 * Main Activity hosting the Quran Alarm application.
 * Follows Android Clean Architecture and delegates all state to [AlarmViewModel].
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels {
        AlarmViewModelFactory(
            repository = AlarmRepositoryImpl(applicationContext),
            scheduler = AlarmSchedulerImpl(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    AlarmListScreen(
                        uiState = uiState,
                        onAddClick = { viewModel.openAddDialog() },
                        onAlarmClick = { alarm -> viewModel.openEditDialog(alarm) },
                        onToggleAlarm = { alarm, isEnabled ->
                            viewModel.toggleAlarm(alarm, isEnabled)
                            Toast.makeText(
                                this@MainActivity,
                                if (isEnabled) "تم تفعيل المنبه" else "تم تعطيل المنبه",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDeleteConfirmed = { alarmId ->
                            viewModel.deleteAlarm(alarmId)
                            Toast.makeText(this@MainActivity, "تم حذف المنبه", Toast.LENGTH_SHORT).show()
                        },
                        onSaveAlarm = { alarm ->
                            val isEdit = uiState.editingAlarm != null
                            viewModel.saveAlarm(alarm)
                            Toast.makeText(
                                this@MainActivity,
                                if (isEdit) "تم تحديث المنبه بنجاح!" else "تم حفظ المنبه بنجاح!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDismissDialog = { viewModel.dismissDialog() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkOverlayPermission(this)
    }
}
