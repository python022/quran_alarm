package com.quran.alarm.alarm.data.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Snapshot of an active alarm/reading session persisted in Device-Protected Storage.
 */
data class PersistedAlarmSession(
    val active: Boolean,
    val stage: String,
    val alarmId: Int,
    val label: String,
    val startPage: Int,
    val pageCount: Int,
    val volumeLevel: Int,
    val isVibrate: Boolean,
    val soundUri: String?,
    val wirdSurahNumber: Int?,
    val startAyah: Int?,
    val dailyAyahCount: Int?
)

/**
 * Snapshot of Mushaf reading progress within an active alarm session.
 */
data class SessionReadingProgress(
    val alarmId: Int,
    val stageName: String,
    val currentPage: Int,
    val completedPages: Set<Int>,
    val visitedLineByPage: Map<Int, Int>
)

interface ReadingProgressStore {
    fun load(alarmId: Int): SessionReadingProgress?
    fun save(progress: SessionReadingProgress)
    fun clear()
}

/**
 * Single unified SharedPreferences store (`quran_alarm_session_prefs`) shared between
 * [com.quran.alarm.alarm.service.AlarmForegroundService] and
 * [com.quran.alarm.alarm.ui.viewmodel.AlarmAlertViewModel], preventing conflicting keys
 * and inconsistent commit/apply behavior.
 */
class AlarmSessionStore(
    private val prefs: SharedPreferences
) : ReadingProgressStore {
    companion object {
        const val PREFS_NAME = "quran_alarm_session_prefs"
        const val STAGE_RINGING = "RINGING"
        const val STAGE_MUSHAF_READING = "MUSHAF_READING"

        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_SESSION_STAGE = "session_stage"
        private const val KEY_ALARM_ID = "session_alarm_id"
        private const val KEY_ALARM_LABEL = "session_alarm_label"
        private const val KEY_START_PAGE = "session_start_page"
        private const val KEY_PAGE_COUNT = "session_page_count"
        private const val KEY_VOLUME_LEVEL = "session_volume_level"
        private const val KEY_IS_VIBRATE = "session_is_vibrate"
        private const val KEY_SOUND_URI = "session_sound_uri"
        private const val KEY_WIRD_SURAH_NUMBER = "session_wird_surah_number"
        private const val KEY_START_AYAH = "session_start_ayah"
        private const val KEY_DAILY_AYAH_COUNT = "session_daily_ayah_count"

        private const val KEY_PROGRESS_ACTIVE = "progress_active"
        private const val KEY_PROGRESS_ALARM_ID = "progress_alarm_id"
        private const val KEY_PROGRESS_STAGE = "progress_stage"
        private const val KEY_PROGRESS_CURRENT_PAGE = "progress_current_page"
        private const val KEY_PROGRESS_COMPLETED_PAGES = "progress_completed_pages"
        private const val KEY_PROGRESS_VISITED_LINES = "progress_visited_lines"

        fun fromContext(context: Context): AlarmSessionStore {
            val prefs = DirectBootStorageHelper
                .resolveStorageContext(context, prefsNamesToMigrate = listOf(PREFS_NAME))
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AlarmSessionStore(prefs)
        }
    }

    fun loadSession(): PersistedAlarmSession? {
        if (!prefs.getBoolean(KEY_SESSION_ACTIVE, false)) return null
        val alarmId = prefs.getInt(KEY_ALARM_ID, -1)
        if (alarmId < 0) return null
        val wirdSurahNumber = prefs.getInt(KEY_WIRD_SURAH_NUMBER, -1).takeIf { it > 0 }
        val startAyah = prefs.getInt(KEY_START_AYAH, -1).takeIf { it > 0 }
        val dailyAyahCount = prefs.getInt(KEY_DAILY_AYAH_COUNT, -1).takeIf { it > 0 }
        return PersistedAlarmSession(
            active = true,
            stage = prefs.getString(KEY_SESSION_STAGE, STAGE_MUSHAF_READING) ?: STAGE_MUSHAF_READING,
            alarmId = alarmId,
            label = prefs.getString(KEY_ALARM_LABEL, "ورد القرآن الكريم") ?: "ورد القرآن الكريم",
            startPage = prefs.getInt(KEY_START_PAGE, 1).coerceIn(1, 604),
            pageCount = prefs.getInt(KEY_PAGE_COUNT, 5).coerceAtLeast(1),
            volumeLevel = prefs.getInt(KEY_VOLUME_LEVEL, 100).coerceIn(1, 100),
            isVibrate = prefs.getBoolean(KEY_IS_VIBRATE, true),
            soundUri = prefs.getString(KEY_SOUND_URI, null),
            wirdSurahNumber = wirdSurahNumber,
            startAyah = startAyah,
            dailyAyahCount = dailyAyahCount
        )
    }

    fun saveSession(session: PersistedAlarmSession) {
        if (session.alarmId < 0) return
        val editor = prefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, session.active)
            .putString(KEY_SESSION_STAGE, session.stage)
            .putInt(KEY_ALARM_ID, session.alarmId)
            .putString(KEY_ALARM_LABEL, session.label)
            .putInt(KEY_START_PAGE, session.startPage)
            .putInt(KEY_PAGE_COUNT, session.pageCount)
            .putInt(KEY_VOLUME_LEVEL, session.volumeLevel.coerceIn(1, 100))
            .putBoolean(KEY_IS_VIBRATE, session.isVibrate)

        if (session.soundUri != null) {
            editor.putString(KEY_SOUND_URI, session.soundUri)
        } else {
            editor.remove(KEY_SOUND_URI)
        }

        if (session.wirdSurahNumber != null && session.dailyAyahCount != null) {
            editor.putInt(KEY_WIRD_SURAH_NUMBER, session.wirdSurahNumber)
            editor.putInt(KEY_START_AYAH, session.startAyah ?: 1)
            editor.putInt(KEY_DAILY_AYAH_COUNT, session.dailyAyahCount)
        } else {
            editor.remove(KEY_WIRD_SURAH_NUMBER)
            editor.remove(KEY_START_AYAH)
            editor.remove(KEY_DAILY_AYAH_COUNT)
        }
        editor.commit()
    }

    /**
     * Transitions an existing session to [STAGE_MUSHAF_READING] while preserving audio
     * and Ayah-mode fields already stored for the active session.
     */
    fun markReadingStage(
        alarmId: Int,
        label: String,
        startPage: Int,
        pageCount: Int,
        wirdSurahNumber: Int? = null,
        startAyah: Int? = null,
        dailyAyahCount: Int? = null
    ) {
        if (alarmId < 0) return
        val existing = loadSession()?.takeIf { it.alarmId == alarmId }
        saveSession(
            PersistedAlarmSession(
                active = true,
                stage = STAGE_MUSHAF_READING,
                alarmId = alarmId,
                label = label,
                startPage = startPage,
                pageCount = pageCount,
                volumeLevel = existing?.volumeLevel ?: 100,
                isVibrate = existing?.isVibrate ?: true,
                soundUri = existing?.soundUri,
                wirdSurahNumber = wirdSurahNumber ?: existing?.wirdSurahNumber,
                startAyah = startAyah ?: existing?.startAyah,
                dailyAyahCount = dailyAyahCount ?: existing?.dailyAyahCount
            )
        )
    }

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .putString(KEY_SESSION_STAGE, STAGE_RINGING)
            .remove(KEY_ALARM_ID)
            .remove(KEY_ALARM_LABEL)
            .remove(KEY_START_PAGE)
            .remove(KEY_PAGE_COUNT)
            .remove(KEY_VOLUME_LEVEL)
            .remove(KEY_IS_VIBRATE)
            .remove(KEY_SOUND_URI)
            .remove(KEY_WIRD_SURAH_NUMBER)
            .remove(KEY_START_AYAH)
            .remove(KEY_DAILY_AYAH_COUNT)
            .putBoolean(KEY_PROGRESS_ACTIVE, false)
            .remove(KEY_PROGRESS_ALARM_ID)
            .remove(KEY_PROGRESS_STAGE)
            .remove(KEY_PROGRESS_CURRENT_PAGE)
            .remove(KEY_PROGRESS_COMPLETED_PAGES)
            .remove(KEY_PROGRESS_VISITED_LINES)
            .commit()
    }

    override fun load(alarmId: Int): SessionReadingProgress? = loadProgress(alarmId)

    override fun save(progress: SessionReadingProgress) = saveProgress(progress)

    override fun clear() = clearProgress()

    fun loadProgress(alarmId: Int): SessionReadingProgress? {
        if (alarmId < 0) return null
        if (!prefs.getBoolean(KEY_PROGRESS_ACTIVE, false)) return null
        if (prefs.getInt(KEY_PROGRESS_ALARM_ID, -1) != alarmId) return null
        val stageName = prefs.getString(KEY_PROGRESS_STAGE, STAGE_RINGING) ?: STAGE_RINGING
        val currentPage = prefs.getInt(KEY_PROGRESS_CURRENT_PAGE, 1)
        val completed = prefs.getString(KEY_PROGRESS_COMPLETED_PAGES, "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        val visited = prefs.getString(KEY_PROGRESS_VISITED_LINES, "")
            .orEmpty()
            .split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val page = parts[0].toIntOrNull()
                    val line = parts[1].toIntOrNull()
                    if (page != null && line != null) page to line else null
                } else {
                    null
                }
            }
            .toMap()
        return SessionReadingProgress(
            alarmId = alarmId,
            stageName = stageName,
            currentPage = currentPage,
            completedPages = completed,
            visitedLineByPage = visited
        )
    }

    fun saveProgress(progress: SessionReadingProgress) {
        if (progress.alarmId < 0) return
        val completedStr = progress.completedPages.joinToString(",")
        val visitedStr = progress.visitedLineByPage.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit()
            .putBoolean(KEY_PROGRESS_ACTIVE, true)
            .putInt(KEY_PROGRESS_ALARM_ID, progress.alarmId)
            .putString(KEY_PROGRESS_STAGE, progress.stageName)
            .putInt(KEY_PROGRESS_CURRENT_PAGE, progress.currentPage)
            .putString(KEY_PROGRESS_COMPLETED_PAGES, completedStr)
            .putString(KEY_PROGRESS_VISITED_LINES, visitedStr)
            .apply()
    }

    fun clearProgress() {
        prefs.edit()
            .putBoolean(KEY_PROGRESS_ACTIVE, false)
            .remove(KEY_PROGRESS_ALARM_ID)
            .remove(KEY_PROGRESS_STAGE)
            .remove(KEY_PROGRESS_CURRENT_PAGE)
            .remove(KEY_PROGRESS_COMPLETED_PAGES)
            .remove(KEY_PROGRESS_VISITED_LINES)
            .commit()
    }
}
