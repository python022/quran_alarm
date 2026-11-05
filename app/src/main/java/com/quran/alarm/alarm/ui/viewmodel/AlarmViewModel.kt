package com.quran.alarm.alarm.ui.viewmodel

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.ui.AlarmUiFormatters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clean Architecture ViewModel for Quran Alarm.
 * Dispatches all repository and scheduling operations off the main thread.
 */
class AlarmViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    init {
        loadAlarms()
    }

    fun loadAlarms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val alarms = withContext(ioDispatcher) {
                repository.getAllAlarms().sortedBy { it.getNextTriggerMillis() }
            }
            updateAlarmsState(alarms)
        }
    }

    fun checkOverlayPermission(context: Context) {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        _uiState.update { it.copy(isOverlayPermissionGranted = isGranted) }
    }

    fun saveAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            val finalAlarm = if (alarm.repeatDays.isEmpty() && alarm.isEnabled && alarm.explicitTriggerMillis == null) {
                alarm.copy(explicitTriggerMillis = alarm.getNextTriggerMillis())
            } else {
                alarm
            }
            withContext(ioDispatcher) {
                repository.saveAlarm(finalAlarm)
                if (finalAlarm.isEnabled) {
                    scheduler.schedule(finalAlarm)
                } else {
                    scheduler.cancel(finalAlarm.id)
                }
            }
            _uiState.update { it.copy(isAddEditDialogOpen = false, editingAlarm = null) }
            loadAlarms()
        }
    }

    fun toggleAlarm(alarm: AlarmItem, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(
                isEnabled = isEnabled,
                explicitTriggerMillis = if (isEnabled && alarm.repeatDays.isEmpty()) {
                    alarm.getNextTriggerMillis()
                } else if (!isEnabled) {
                    null
                } else {
                    alarm.explicitTriggerMillis
                }
            )
            withContext(ioDispatcher) {
                repository.saveAlarm(updated)
                if (isEnabled) {
                    scheduler.schedule(updated)
                } else {
                    scheduler.cancel(alarm.id)
                }
            }
            loadAlarms()
        }
    }

    fun deleteAlarm(alarmId: Int) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                scheduler.cancel(alarmId)
                repository.deleteAlarm(alarmId)
            }
            loadAlarms()
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(editingAlarm = null, isAddEditDialogOpen = true) }
    }

    fun openEditDialog(alarm: AlarmItem) {
        _uiState.update { it.copy(editingAlarm = alarm, isAddEditDialogOpen = true) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(isAddEditDialogOpen = false, editingAlarm = null) }
    }

    fun updateCountdown() {
        val alarms = _uiState.value.alarms
        val activeAlarms = alarms.filter { it.isEnabled }
        val nextTrigger = activeAlarms.minOfOrNull { it.getNextTriggerMillis() }
        val countdown = if (nextTrigger != null) {
            AlarmUiFormatters.formatNextAlarmCountdown(nextTrigger)
        } else {
            "لا توجد منبهات نشطة"
        }
        _uiState.update { it.copy(nextAlarmCountdown = countdown) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun updateAlarmsState(alarms: List<AlarmItem>) {
        val activeAlarms = alarms.filter { it.isEnabled }
        val nextTrigger = activeAlarms.minOfOrNull { it.getNextTriggerMillis() }
        val countdown = if (nextTrigger != null) {
            AlarmUiFormatters.formatNextAlarmCountdown(nextTrigger)
        } else {
            "لا توجد منبهات نشطة"
        }

        val primaryAlarm = activeAlarms.firstOrNull() ?: alarms.firstOrNull()
        val forecast = primaryAlarm?.let {
            QuranStructure.calculateKhatmaForecast(it.startPage, it.pageCount)
        }

        _uiState.update {
            it.copy(
                alarms = alarms,
                isLoading = false,
                khatmaProgress = forecast,
                nextAlarmCountdown = countdown
            )
        }
    }
}
