package com.quran.alarm.alarm.ui.viewmodel

import com.quran.alarm.alarm.data.device.DeviceReadinessStatus
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.KhatmaForecast

/**
 * Immutable UI State representing the state of the Alarm screen.
 */
data class AlarmUiState(
    val alarms: List<AlarmItem> = emptyList(),
    val isLoading: Boolean = false,
    val khatmaProgress: KhatmaForecast? = null,
    val nextAlarmCountdown: String = "",
    val editingAlarm: AlarmItem? = null,
    val isAddEditDialogOpen: Boolean = false,
    val isOverlayPermissionGranted: Boolean = true,
    val deviceReadinessStatus: DeviceReadinessStatus? = null,
    val userMessage: String? = null
)
