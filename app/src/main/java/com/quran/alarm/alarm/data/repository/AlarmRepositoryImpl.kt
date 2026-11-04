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
        private const val KEY_PREFIX_HOUR = "alarm_hour_"
        private const val KEY_PREFIX_MINUTE = "alarm_minute_"
        private const val KEY_PREFIX_REPEAT = "alarm_repeat_"
        private const val KEY_PREFIX_LABEL = "alarm_label_"
        private const val KEY_PREFIX_ENABLED = "alarm_enabled_"
        private const val KEY_PREFIX_VIBRATE = "alarm_vibrate_"
        private const val KEY_PREFIX_PAGE = "alarm_page_"
        private const val KEY_PREFIX_START_PAGE = "alarm_start_page_"
        private const val KEY_PREFIX_PAGE_COUNT = "alarm_page_count_"
        private const val KEY_PREFIX_VOLUME = "alarm_volume_"
        private const val KEY_PREFIX_SOUND_URI = "alarm_sound_uri_"
        private const val KEY_PREFIX_SOUND_TITLE = "alarm_sound_title_"
        private const val KEY_PREFIX_EXPLICIT_TIME = "alarm_explicit_time_"
        private const val KEY_PREFIX_IS_SEQUENTIAL = "alarm_is_sequential_"
    }

    override fun saveAlarm(alarm: AlarmItem) {
        val idStr = alarm.id.toString()
        val currentIds = getStoredIds().toMutableSet()
        currentIds.add(idStr)

        val repeatDaysStr = alarm.repeatDays.joinToString(separator = ",")

        val editor = prefs.edit()
            .putStringSet(KEY_ALARM_IDS, currentIds)
            .putInt(KEY_PREFIX_HOUR + idStr, alarm.hour)
            .putInt(KEY_PREFIX_MINUTE + idStr, alarm.minute)
            .putString(KEY_PREFIX_REPEAT + idStr, repeatDaysStr)
            .putString(KEY_PREFIX_LABEL + idStr, alarm.label)
            .putBoolean(KEY_PREFIX_ENABLED + idStr, alarm.isEnabled)
            .putBoolean(KEY_PREFIX_VIBRATE + idStr, alarm.isVibrationEnabled)
            .putInt(KEY_PREFIX_PAGE + idStr, alarm.targetPage)
            .putInt(KEY_PREFIX_START_PAGE + idStr, alarm.startPage)
            .putInt(KEY_PREFIX_PAGE_COUNT + idStr, alarm.pageCount)
            .putInt(KEY_PREFIX_VOLUME + idStr, alarm.volumeLevel)
            .putBoolean(KEY_PREFIX_IS_SEQUENTIAL + idStr, alarm.isSequential)

        if (alarm.soundUri != null) {
            editor.putString(KEY_PREFIX_SOUND_URI + idStr, alarm.soundUri)
        } else {
            editor.remove(KEY_PREFIX_SOUND_URI + idStr)
        }

        if (alarm.soundTitle != null) {
            editor.putString(KEY_PREFIX_SOUND_TITLE + idStr, alarm.soundTitle)
        } else {
            editor.remove(KEY_PREFIX_SOUND_TITLE + idStr)
        }

        if (alarm.explicitTriggerMillis != null) {
            editor.putLong(KEY_PREFIX_EXPLICIT_TIME + idStr, alarm.explicitTriggerMillis)
        } else {
            editor.remove(KEY_PREFIX_EXPLICIT_TIME + idStr)
        }

        editor.apply()
    }

    override fun getAllAlarms(): List<AlarmItem> {
        val ids = getStoredIds()
        return ids.mapNotNull { idStr ->
            val id = idStr.toIntOrNull() ?: return@mapNotNull null
            readAlarm(id)
        }.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
    }

    override fun getActiveAlarms(): List<AlarmItem> {
        return getAllAlarms().filter { it.isEnabled }
    }

    override fun getAlarmById(id: Int): AlarmItem? {
        val idStr = id.toString()
        if (!getStoredIds().contains(idStr)) return null
        return readAlarm(id)
    }

    override fun deleteAlarm(id: Int) {
        val idStr = id.toString()
        val currentIds = getStoredIds().toMutableSet()
        currentIds.remove(idStr)

        prefs.edit()
            .putStringSet(KEY_ALARM_IDS, currentIds)
            .remove(KEY_PREFIX_HOUR + idStr)
            .remove(KEY_PREFIX_MINUTE + idStr)
            .remove(KEY_PREFIX_REPEAT + idStr)
            .remove(KEY_PREFIX_LABEL + idStr)
            .remove(KEY_PREFIX_ENABLED + idStr)
            .remove(KEY_PREFIX_VIBRATE + idStr)
            .remove(KEY_PREFIX_PAGE + idStr)
            .remove(KEY_PREFIX_START_PAGE + idStr)
            .remove(KEY_PREFIX_PAGE_COUNT + idStr)
            .remove(KEY_PREFIX_VOLUME + idStr)
            .remove(KEY_PREFIX_SOUND_URI + idStr)
            .remove(KEY_PREFIX_SOUND_TITLE + idStr)
            .remove(KEY_PREFIX_EXPLICIT_TIME + idStr)
            .remove(KEY_PREFIX_IS_SEQUENTIAL + idStr)
            .apply()
    }

    private fun readAlarm(id: Int): AlarmItem {
        val idStr = id.toString()
        val hour = prefs.getInt(KEY_PREFIX_HOUR + idStr, 5)
        val minute = prefs.getInt(KEY_PREFIX_MINUTE + idStr, 0)
        val repeatStr = prefs.getString(KEY_PREFIX_REPEAT + idStr, "") ?: ""
        val repeatDays = if (repeatStr.isNotBlank()) {
            repeatStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } else {
            emptySet()
        }
        val label = prefs.getString(KEY_PREFIX_LABEL + idStr, "ورد القرآن الكريم") ?: "ورد القرآن الكريم"
        val isEnabled = prefs.getBoolean(KEY_PREFIX_ENABLED + idStr, true)
        val isVibrate = prefs.getBoolean(KEY_PREFIX_VIBRATE + idStr, true)
        val page = prefs.getInt(KEY_PREFIX_PAGE + idStr, 1)
        val startPage = prefs.getInt(KEY_PREFIX_START_PAGE + idStr, page)
        val pageCount = prefs.getInt(KEY_PREFIX_PAGE_COUNT + idStr, 5)
        val volume = prefs.getInt(KEY_PREFIX_VOLUME + idStr, 100)
        val soundUri = prefs.getString(KEY_PREFIX_SOUND_URI + idStr, null)
        val soundTitle = prefs.getString(KEY_PREFIX_SOUND_TITLE + idStr, null)
        val isSequential = prefs.getBoolean(KEY_PREFIX_IS_SEQUENTIAL + idStr, true)

        val explicitTime = if (prefs.contains(KEY_PREFIX_EXPLICIT_TIME + idStr)) {
            prefs.getLong(KEY_PREFIX_EXPLICIT_TIME + idStr, 0L)
        } else {
            null
        }

        return AlarmItem(
            id = id,
            hour = hour,
            minute = minute,
            repeatDays = repeatDays,
            isEnabled = isEnabled,
            isVibrationEnabled = isVibrate,
            label = label,
            targetPage = startPage,
            startPage = startPage,
            pageCount = pageCount,
            volumeLevel = volume,
            soundUri = soundUri,
            explicitTriggerMillis = explicitTime,
            isSequential = isSequential,
            soundTitle = soundTitle
        )
    }

    private fun getStoredIds(): Set<String> {
        return prefs.getStringSet(KEY_ALARM_IDS, emptySet()) ?: emptySet()
    }
}
