package com.quran.alarm.alarm.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository

/**
 * Lightweight offline SharedPreferences implementation of [AlarmRepository].
 * Operates entirely in memory and on local sandboxed storage without any external dependencies.
 */
class AlarmRepositoryImpl(
    context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) : AlarmRepository {

    companion object {
        private const val PREFS_NAME = "quran_alarm_prefs"
        private const val KEY_ALARM_IDS = "key_alarm_ids"
        private const val KEY_PREFIX_TIME = "alarm_time_"
        private const val KEY_PREFIX_LABEL = "alarm_label_"
        private const val KEY_PREFIX_ENABLED = "alarm_enabled_"
        private const val KEY_PREFIX_PAGE = "alarm_page_"
    }

    override fun saveAlarm(alarm: AlarmItem) {
        val idStr = alarm.id.toString()
        val currentIds = getStoredIds().toMutableSet()
        currentIds.add(idStr)

        prefs.edit()
            .putStringSet(KEY_ALARM_IDS, currentIds)
            .putLong(KEY_PREFIX_TIME + idStr, alarm.timeInMillis)
            .putString(KEY_PREFIX_LABEL + idStr, alarm.label)
            .putBoolean(KEY_PREFIX_ENABLED + idStr, alarm.isEnabled)
            .putInt(KEY_PREFIX_PAGE + idStr, alarm.targetPage)
            .apply()
    }

    override fun getActiveAlarms(): List<AlarmItem> {
        val ids = getStoredIds()
        return ids.mapNotNull { idStr ->
            val id = idStr.toIntOrNull() ?: return@mapNotNull null
            val isEnabled = prefs.getBoolean(KEY_PREFIX_ENABLED + idStr, true)
            if (!isEnabled) return@mapNotNull null

            val time = prefs.getLong(KEY_PREFIX_TIME + idStr, 0L)
            val label = prefs.getString(KEY_PREFIX_LABEL + idStr, "ورد القرآن الكريم") ?: "ورد القرآن الكريم"
            val page = prefs.getInt(KEY_PREFIX_PAGE + idStr, 1)

            AlarmItem(
                id = id,
                timeInMillis = time,
                label = label,
                isEnabled = true,
                targetPage = page
            )
        }
    }

    override fun getAlarmById(id: Int): AlarmItem? {
        val idStr = id.toString()
        if (!getStoredIds().contains(idStr)) return null

        val time = prefs.getLong(KEY_PREFIX_TIME + idStr, 0L)
        val label = prefs.getString(KEY_PREFIX_LABEL + idStr, "ورد القرآن الكريم") ?: "ورد القرآن الكريم"
        val isEnabled = prefs.getBoolean(KEY_PREFIX_ENABLED + idStr, true)
        val page = prefs.getInt(KEY_PREFIX_PAGE + idStr, 1)

        return AlarmItem(
            id = id,
            timeInMillis = time,
            label = label,
            isEnabled = isEnabled,
            targetPage = page
        )
    }

    override fun deleteAlarm(id: Int) {
        val idStr = id.toString()
        val currentIds = getStoredIds().toMutableSet()
        currentIds.remove(idStr)

        prefs.edit()
            .putStringSet(KEY_ALARM_IDS, currentIds)
            .remove(KEY_PREFIX_TIME + idStr)
            .remove(KEY_PREFIX_LABEL + idStr)
            .remove(KEY_PREFIX_ENABLED + idStr)
            .remove(KEY_PREFIX_PAGE + idStr)
            .apply()
    }

    private fun getStoredIds(): Set<String> {
        return prefs.getStringSet(KEY_ALARM_IDS, emptySet()) ?: emptySet()
    }
}

