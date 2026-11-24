package com.quran.alarm.alarm.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quran.alarm.alarm.data.device.OemDeviceHelper
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
import java.util.Calendar

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
        cleanupOrphanedSounds()
    }

    private fun cleanupOrphanedSounds() {
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                (repository as? com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl)
                    ?.cleanupOrphanedCustomSounds()
            }
        }
    }

    fun loadAlarms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val alarms = withContext(ioDispatcher) {
                    repository.getAllAlarms()
                }
                updateAlarmsState(alarms)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "تعذر تحميل المنبهات"
                    )
                }
            }
        }
    }

    fun checkOverlayPermission(context: Context) {
        viewModelScope.launch {
            val status = withContext(ioDispatcher) {
                OemDeviceHelper.getReadinessStatus(context)
            }
            _uiState.update {
                it.copy(
                    isOverlayPermissionGranted = status.isOverlayGranted,
                    deviceReadinessStatus = status
                )
            }
        }
    }

    fun saveAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            val isEdit = _uiState.value.editingAlarm != null
            val finalAlarm = if (alarm.repeatDays.isEmpty() && alarm.isEnabled && alarm.explicitTriggerMillis == null) {
                alarm.copy(explicitTriggerMillis = alarm.getNextTriggerMillis())
            } else {
                alarm
            }
            try {
                withContext(ioDispatcher) {
                    repository.saveAlarm(finalAlarm)
                    if (finalAlarm.isEnabled) {
                        scheduler.schedule(finalAlarm)
                    } else {
                        scheduler.cancel(finalAlarm.id)
                    }
                }
                _uiState.update {
                    it.copy(
                        isAddEditDialogOpen = false,
                        editingAlarm = null,
                        userMessage = if (isEdit) "تم تحديث المنبه بنجاح!" else "تم حفظ المنبه بنجاح!"
                    )
                }
                loadAlarms()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(userMessage = "تعذر حفظ المنبه")
                }
            }
        }
    }

    fun toggleAlarm(alarm: AlarmItem, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(
                isEnabled = isEnabled,
                snoozeTriggerMillis = null,
                explicitTriggerMillis = if (isEnabled && alarm.repeatDays.isEmpty()) {
                    alarm.copy(explicitTriggerMillis = null, snoozeTriggerMillis = null).getNextTriggerMillis()
                } else {
                    null
                }
            )
            try {
                withContext(ioDispatcher) {
                    repository.saveAlarm(updated)
                    if (isEnabled) {
                        scheduler.schedule(updated)
                    } else {
                        scheduler.cancel(alarm.id)
                    }
                }
                _uiState.update {
                    it.copy(userMessage = if (isEnabled) "تم تفعيل المنبه" else "تم تعطيل المنبه")
                }
                loadAlarms()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(userMessage = "تعذر تحديث حالة المنبه")
                }
            }
        }
    }

    fun deleteAlarm(alarmId: Int) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    scheduler.cancel(alarmId)
                    repository.deleteAlarm(alarmId)
                }
                _uiState.update {
                    it.copy(userMessage = "تم حذف المنبه")
                }
                loadAlarms()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(userMessage = "تعذر حذف المنبه")
                }
            }
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
        val countdown = computeNextAlarmCountdown(_uiState.value.alarms)
        _uiState.update { it.copy(nextAlarmCountdown = countdown) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun updateAlarmsState(alarms: List<AlarmItem>) {
        val countdown = computeNextAlarmCountdown(alarms)

        val activeAlarms = alarms.filter { it.isEnabled }
        val primarySequentialAlarm = activeAlarms.firstOrNull { it.isSequential && !it.isAyahMode }
            ?: alarms.firstOrNull { it.isSequential && !it.isAyahMode }
        val forecast = primarySequentialAlarm?.let {
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

    private fun computeNextAlarmCountdown(alarms: List<AlarmItem>): String {
        val now = System.currentTimeMillis()
        val nextEntry = alarms
            .filter { it.isEnabled }
            .map { it to it.getEffectiveNextTriggerMillis(now) }
            .minByOrNull { it.second }
            ?: return "لا توجد منبهات نشطة"

        val (alarm, nextTrigger) = nextEntry
        val targetDayName = if (alarm.repeatDays.size in 1..6) {
            val targetCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
            AlarmUiFormatters.getDayName(targetCal.get(Calendar.DAY_OF_WEEK))
        } else {
            null
        }
        return AlarmUiFormatters.formatNextAlarmCountdown(
            triggerMillis = nextTrigger,
            fromMillis = now,
            targetDayName = targetDayName
        )
    }
}
