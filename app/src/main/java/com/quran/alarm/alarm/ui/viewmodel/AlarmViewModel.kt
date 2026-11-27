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
import kotlinx.coroutines.Job
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

    private var loadAlarmsJob: Job? = null

    init {
        loadAlarms()
        cleanupOrphanedSounds()
    }

    private fun cleanupOrphanedSounds() {
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                repository.cleanupOrphanedCustomSounds()
            }
        }
    }

    fun loadAlarms() {
        loadAlarmsJob?.cancel()
        loadAlarmsJob = viewModelScope.launch {
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
                val refreshed = withContext(ioDispatcher) {
                    repository.saveAlarm(finalAlarm)
                    if (finalAlarm.isEnabled) {
                        scheduler.schedule(finalAlarm)
                    } else {
                        scheduler.cancel(finalAlarm.id)
                    }
                    repository.getAllAlarms()
                }
                _uiState.update {
                    it.copy(
                        isAddEditDialogOpen = false,
                        editingAlarm = null,
                        userMessage = if (isEdit) "تم تحديث المنبه بنجاح!" else "تم حفظ المنبه بنجاح!"
                    )
                }
                updateAlarmsState(refreshed)
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
                val refreshed = withContext(ioDispatcher) {
                    repository.saveAlarm(updated)
                    if (isEnabled) {
                        scheduler.schedule(updated)
                    } else {
                        scheduler.cancel(alarm.id)
                    }
                    repository.getAllAlarms()
                }
                _uiState.update {
                    it.copy(userMessage = if (isEnabled) "تم تفعيل المنبه" else "تم تعطيل المنبه")
                }
                updateAlarmsState(refreshed)
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
                val refreshed = withContext(ioDispatcher) {
                    scheduler.cancel(alarmId)
                    repository.deleteAlarm(alarmId)
                    repository.getAllAlarms()
                }
                _uiState.update {
                    it.copy(
                        isAddEditDialogOpen = false,
                        editingAlarm = null,
                        userMessage = "تم حذف المنبه"
                    )
                }
                updateAlarmsState(refreshed)
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
        val uniqueAlarms = alarms.distinctBy { it.id }
        val countdown = computeNextAlarmCountdown(uniqueAlarms)

        // Sum daily pages across all enabled sequential alarms to compute accurate Khatma completion days
        val enabledSequentialPageAlarms = uniqueAlarms.filter { it.isEnabled && it.isSequential && !it.isAyahMode }
        val forecast = if (enabledSequentialPageAlarms.isNotEmpty()) {
            val totalDailyPages = enabledSequentialPageAlarms.sumOf { it.pageCount }.coerceAtLeast(1)
            QuranStructure.calculateKhatmaForecast(
                startPage = enabledSequentialPageAlarms.first().startPage,
                dailyPages = totalDailyPages
            )
        } else {
            null
        }

        val enabledSequentialAyahAlarms = uniqueAlarms.filter { it.isEnabled && it.isSequential && it.isAyahMode }
        val surahForecast = if (enabledSequentialAyahAlarms.isNotEmpty()) {
            val primary = enabledSequentialAyahAlarms.first()
            val surahNum = primary.wirdSurahNumber!!
            val totalDailyAyahs = enabledSequentialAyahAlarms
                .filter { it.wirdSurahNumber == surahNum }
                .sumOf { it.dailyAyahCount ?: 1 }
                .coerceAtLeast(1)
            QuranStructure.calculateSurahWirdForecast(
                surahNumber = surahNum,
                startAyah = primary.startAyah ?: 1,
                dailyAyahs = totalDailyAyahs
            )
        } else {
            null
        }

        _uiState.update {
            it.copy(
                alarms = uniqueAlarms,
                isLoading = false,
                khatmaProgress = forecast,
                surahWirdProgress = surahForecast,
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
