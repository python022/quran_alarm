package com.quran.alarm.alarm.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.quran.alarm.alarm.data.storage.CustomSoundStorage
import com.quran.alarm.alarm.data.storage.DirectBootStorageHelper
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Lightweight offline SharedPreferences implementation of [AlarmRepository].
 * Stores each alarm as a single compact JSON record and batches multi-alarm updates
 * into a single commit transaction.
 */
class AlarmRepositoryImpl(
    context: Context,
    private val prefs: SharedPreferences = DirectBootStorageHelper
        .resolveStorageContext(context, prefsNamesToMigrate = listOf(PREFS_NAME))
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) : AlarmRepository {
    private val storageContext: Context = DirectBootStorageHelper.resolveStorageContext(context)

    companion object {
        private val repoLock = Any()
        private const val PREFS_NAME = "quran_alarm_prefs"
        private const val KEY_ALARM_IDS = "key_alarm_ids"
        private const val KEY_PREFIX_JSON = "alarm_json_"
    }

    override fun saveAlarm(alarm: AlarmItem) {
        saveAlarms(listOf(alarm))
    }

    override fun saveAlarms(alarms: List<AlarmItem>) {
        if (alarms.isEmpty()) return

        val previousSoundUris: List<String>
        synchronized(repoLock) {
            previousSoundUris = alarms.mapNotNull { alarm ->
                val oldUri = readStoredSoundUri(alarm.id)
                if (oldUri != null && oldUri != alarm.soundUri) oldUri else null
            }

            val currentIds = getStoredIds().toMutableSet()
            val editor = prefs.edit()
            for (alarm in alarms) {
                val idStr = alarm.id.toString()
                currentIds.add(idStr)
                editor.putString(KEY_PREFIX_JSON + idStr, encodeAlarmJson(alarm))
            }
            editor.putStringSet(KEY_ALARM_IDS, currentIds)
            editor.commit()
        }

        for (oldUri in previousSoundUris) {
            deleteUnusedCustomSound(oldUri)
        }
    }

    override fun getAllAlarms(): List<AlarmItem> {
        synchronized(repoLock) {
            val normalizedIds = getStoredIds()
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
            return normalizedIds.mapNotNull { id ->
                readAlarm(id)
            }.distinctBy { it.id }
                .sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        }
    }

    override fun getActiveAlarms(): List<AlarmItem> {
        return getAllAlarms().filter { it.isEnabled }
    }

    override fun getAlarmById(id: Int): AlarmItem? {
        synchronized(repoLock) {
            val idStr = id.toString()
            if (!getStoredIds().contains(idStr)) return null
            return readAlarm(id)
        }
    }

    override fun deleteAlarm(id: Int) {
        val removedSoundUri: String?
        synchronized(repoLock) {
            val idStr = id.toString()
            removedSoundUri = readStoredSoundUri(id)
            val currentIds = getStoredIds().toMutableSet()
            currentIds.remove(idStr)

            prefs.edit()
                .putStringSet(KEY_ALARM_IDS, currentIds)
                .remove(KEY_PREFIX_JSON + idStr)
                .commit()
        }

        if (removedSoundUri != null) {
            deleteUnusedCustomSound(removedSoundUri)
        }
    }

    override fun cleanupOrphanedCustomSounds() {
        val referencedUris = getAllAlarms().mapNotNull { it.soundUri }.toSet()
        CustomSoundStorage.cleanupOrphanedSounds(storageContext, referencedUris)
    }

    private fun readAlarm(id: Int): AlarmItem? {
        val jsonRaw = prefs.getString(KEY_PREFIX_JSON + id.toString(), null)
        if (jsonRaw.isNullOrBlank()) return null
        return decodeAlarmJson(id, jsonRaw)
    }

    private fun readStoredSoundUri(id: Int): String? {
        val jsonRaw = prefs.getString(KEY_PREFIX_JSON + id.toString(), null)
        if (!jsonRaw.isNullOrBlank()) {
            runCatching {
                val obj = JSONObject(jsonRaw)
                if (obj.has("soundUri") && !obj.isNull("soundUri")) {
                    return obj.optString("soundUri").takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }

    private fun encodeAlarmJson(alarm: AlarmItem): String {
        return JSONObject().apply {
            put("id", alarm.id)
            put("hour", alarm.hour)
            put("minute", alarm.minute)
            put("repeatDays", JSONArray(alarm.repeatDays.sorted()))
            put("isEnabled", alarm.isEnabled)
            put("isVibrationEnabled", alarm.isVibrationEnabled)
            put("label", alarm.label.take(AlarmItem.MAX_LABEL_LENGTH))
            put("targetPage", alarm.targetPage)
            put("startPage", alarm.startPage)
            put("pageCount", alarm.pageCount)
            put("volumeLevel", alarm.volumeLevel)
            put("isSequential", alarm.isSequential)
            if (alarm.soundUri != null) put("soundUri", alarm.soundUri)
            if (alarm.soundTitle != null) put("soundTitle", alarm.soundTitle)
            if (alarm.explicitTriggerMillis != null) put("explicitTriggerMillis", alarm.explicitTriggerMillis)
            if (alarm.snoozeTriggerMillis != null) put("snoozeTriggerMillis", alarm.snoozeTriggerMillis)
            if (alarm.wirdSurahNumber != null) put("wirdSurahNumber", alarm.wirdSurahNumber)
            if (alarm.startAyah != null) put("startAyah", alarm.startAyah)
            if (alarm.dailyAyahCount != null) put("dailyAyahCount", alarm.dailyAyahCount)
        }.toString()
    }

    private fun decodeAlarmJson(id: Int, jsonRaw: String): AlarmItem? {
        return try {
            val obj = JSONObject(jsonRaw)
            val repeatArray = obj.optJSONArray("repeatDays")
            val repeatDays = buildSet {
                if (repeatArray != null) {
                    for (i in 0 until repeatArray.length()) {
                        add(repeatArray.optInt(i, -1))
                    }
                }
            }
            val page = obj.optInt("targetPage", 1)
            buildSanitizedAlarmItem(
                id = id,
                hour = obj.optInt("hour", 5),
                minute = obj.optInt("minute", 0),
                repeatDays = repeatDays,
                isEnabled = obj.optBoolean("isEnabled", true),
                isVibrate = obj.optBoolean("isVibrationEnabled", true),
                label = obj.optString("label", "ورد القرآن الكريم"),
                targetPage = page,
                startPage = obj.optInt("startPage", page),
                pageCount = obj.optInt("pageCount", 5),
                volume = obj.optInt("volumeLevel", 100),
                soundUri = if (obj.has("soundUri") && !obj.isNull("soundUri")) obj.optString("soundUri") else null,
                soundTitle = if (obj.has("soundTitle") && !obj.isNull("soundTitle")) obj.optString("soundTitle") else null,
                isSequential = obj.optBoolean("isSequential", true),
                explicitTime = if (obj.has("explicitTriggerMillis") && !obj.isNull("explicitTriggerMillis")) {
                    obj.optLong("explicitTriggerMillis")
                } else {
                    null
                },
                snoozeTime = if (obj.has("snoozeTriggerMillis") && !obj.isNull("snoozeTriggerMillis")) {
                    obj.optLong("snoozeTriggerMillis")
                } else {
                    null
                },
                wirdSurahNumber = if (obj.has("wirdSurahNumber") && !obj.isNull("wirdSurahNumber")) {
                    obj.optInt("wirdSurahNumber")
                } else {
                    null
                },
                startAyah = if (obj.has("startAyah") && !obj.isNull("startAyah")) obj.optInt("startAyah") else null,
                dailyAyahCount = if (obj.has("dailyAyahCount") && !obj.isNull("dailyAyahCount")) {
                    obj.optInt("dailyAyahCount")
                } else {
                    null
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSanitizedAlarmItem(
        id: Int,
        hour: Int,
        minute: Int,
        repeatDays: Set<Int>,
        isEnabled: Boolean,
        isVibrate: Boolean,
        label: String,
        targetPage: Int,
        startPage: Int,
        pageCount: Int,
        volume: Int,
        soundUri: String?,
        soundTitle: String?,
        isSequential: Boolean,
        explicitTime: Long?,
        snoozeTime: Long?,
        wirdSurahNumber: Int?,
        startAyah: Int?,
        dailyAyahCount: Int?
    ): AlarmItem {
        val safeSurah = wirdSurahNumber?.coerceIn(1, QuranStructure.TOTAL_SURAHS)
        val maxAyahs = safeSurah?.let { QuranStructure.getSurahByNumber(it).ayahCount }
        val safeTargetPage = targetPage.coerceIn(1, 604)
        val safeStartPage = startPage.coerceIn(1, 604)
        return AlarmItem(
            id = id.coerceAtLeast(0),
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            repeatDays = repeatDays.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }.toSet(),
            isEnabled = isEnabled,
            isVibrationEnabled = isVibrate,
            label = label.ifBlank { "ورد القرآن الكريم" }.take(AlarmItem.MAX_LABEL_LENGTH),
            targetPage = safeTargetPage,
            startPage = safeStartPage,
            pageCount = pageCount.coerceIn(1, 604),
            volumeLevel = volume.coerceIn(1, 100),
            soundUri = soundUri,
            explicitTriggerMillis = explicitTime,
            snoozeTriggerMillis = snoozeTime,
            isSequential = isSequential,
            soundTitle = soundTitle,
            wirdSurahNumber = safeSurah,
            startAyah = if (maxAyahs != null) startAyah?.coerceIn(1, maxAyahs) else null,
            dailyAyahCount = if (maxAyahs != null) dailyAyahCount?.coerceIn(1, maxAyahs) else null
        )
    }

    private fun deleteUnusedCustomSound(uriString: String) {
        val stillReferenced = getAllAlarms().any { it.soundUri == uriString }
        CustomSoundStorage.deleteIfUnused(storageContext, uriString, stillReferenced)
    }

    private fun getStoredIds(): Set<String> {
        val raw = prefs.getStringSet(KEY_ALARM_IDS, null) ?: return emptySet()
        return HashSet(raw)
    }
}
