package com.quran.alarm.quran.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.model.LineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Manages access to the bundled ayahinfo.db SQLite database.
 * Extracts the database from assets using atomic file operations,
 * and provides queries for glyph and line coordinates with strict monotonic separation.
 */
class AyahInfoDatabase(
    private val dbFile: File,
    private val context: Context? = null
) {
    companion object {
        const val ASSET_PATH = "quran/ayahinfo.db"
        const val DB_NAME = "ayahinfo.db"
        const val EXPECTED_DB_SIZE = 5_922_816L
        const val DB_VERSION = 1
        private const val PREFS_NAME = "ayahinfo_db_prefs"
        private const val KEY_DB_VERSION = "db_version"

        @Volatile
        private var instance: AyahInfoDatabase? = null

        suspend fun getInstanceAsync(context: Context): AyahInfoDatabase = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val targetFile = appContext.getDatabasePath(DB_NAME)
            ensureExtracted(appContext, targetFile)
            getInstance(appContext)
        }

        fun getInstance(context: Context): AyahInfoDatabase {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val targetFile = appContext.getDatabasePath(DB_NAME)
                    AyahInfoDatabase(targetFile, appContext).also { instance = it }
                }
            }
        }

        @Synchronized
        fun ensureExtracted(appContext: Context, targetFile: File) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val installedVersion = prefs.getInt(KEY_DB_VERSION, -1)

            val needsExtraction = !targetFile.exists() ||
                    targetFile.length() != EXPECTED_DB_SIZE ||
                    installedVersion != DB_VERSION

            if (needsExtraction) {
                targetFile.parentFile?.mkdirs()
                val tmpFile = File(targetFile.parentFile, "$DB_NAME.tmp")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                appContext.assets.open(ASSET_PATH).use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tmpFile.length() == EXPECTED_DB_SIZE) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!tmpFile.renameTo(targetFile)) {
                        tmpFile.copyTo(targetFile, overwrite = true)
                        tmpFile.delete()
                    }
                    prefs.edit().putInt(KEY_DB_VERSION, DB_VERSION).apply()
                } else {
                    val actualLength = tmpFile.length()
                    tmpFile.delete()
                    throw IllegalStateException("Failed to extract complete ayahinfo.db asset: got $actualLength bytes, expected $EXPECTED_DB_SIZE")
                }
            }
        }
    }


    private var sqliteDb: SQLiteDatabase? = null

    @Synchronized
    private fun getDb(): SQLiteDatabase {
        val current = sqliteDb
        if (current != null && current.isOpen) {
            return current
        }
        if (context != null) {
            ensureExtracted(context, dbFile)
        }
        val opened = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        sqliteDb = opened
        return opened
    }

    private fun resolveMissingLineType(page: Int, lineNumber: Int, runSize: Int, indexInRun: Int): LineType {
        return when {
            runSize == 2 -> if (indexInRun == 0) LineType.SURAH_HEADER else LineType.BASMALAH
            lineNumber == 1 && (page == 1 || page == 187) -> LineType.SURAH_HEADER
            lineNumber == 1 -> LineType.BASMALAH
            lineNumber == 15 -> LineType.SURAH_HEADER
            else -> LineType.SURAH_HEADER
        }
    }

    /**
     * Queries the glyphs table and resolves the 15 line bounding boxes for the specified [pageNumber].
     * Never returns null bounds and guarantees 15 ordered [LineRect] items.
     */
    fun getLinesForPage(pageNumber: Int): List<LineRect> {
        val page = pageNumber.coerceIn(1, 604)
        val db = getDb()

        val rawLines = mutableMapOf<Int, IntArray>()
        val sql = "SELECT line_number, MIN(min_x), MAX(max_x), MIN(min_y), MAX(max_y) " +
                "FROM glyphs WHERE page_number = ? GROUP BY line_number ORDER BY line_number ASC"

        db.rawQuery(sql, arrayOf(page.toString())).use { cursor ->
            val lineIdx = cursor.getColumnIndexOrThrow("line_number")
            val minXIdx = cursor.getColumnIndexOrThrow("MIN(min_x)")
            val maxXIdx = cursor.getColumnIndexOrThrow("MAX(max_x)")
            val minYIdx = cursor.getColumnIndexOrThrow("MIN(min_y)")
            val maxYIdx = cursor.getColumnIndexOrThrow("MAX(max_y)")

            while (cursor.moveToNext()) {
                val lineNum = cursor.getInt(lineIdx)
                val minX = cursor.getInt(minXIdx)
                val maxX = cursor.getInt(maxXIdx)
                val minY = cursor.getInt(minYIdx)
                val maxY = cursor.getInt(maxYIdx)
                rawLines[lineNum] = intArrayOf(minX, maxX, minY, maxY)
            }
        }

        val avgMinX: Int
        val avgMaxX: Int
        val avgHeight: Int

        if (rawLines.isNotEmpty()) {
            avgMinX = (rawLines.values.map { it[0] }.average()).toInt()
            avgMaxX = (rawLines.values.map { it[1] }.average()).toInt()
            val heights = rawLines.values.map { it[3] - it[2] }
            avgHeight = max(20, heights.average().toInt())
        } else {
            avgMinX = 90
            avgMaxX = 1170
            avgHeight = 80
        }

        val resolved = mutableMapOf<Int, LineRect>()
        val targetLines = getPageLineCount(page)

        for (l in 1..targetLines) {
            val existing = rawLines[l]
            if (existing != null) {
                resolved[l] = LineRect(
                    pageNumber = page,
                    lineNumber = l,
                    minX = if (existing[0] == existing[1]) avgMinX else existing[0],
                    maxX = if (existing[0] == existing[1]) avgMaxX else existing[1],
                    minY = existing[2],
                    maxY = existing[3],
                    lineType = LineType.AYAH_TEXT
                )
            }
        }

        val missing = (1..targetLines).filter { it !in resolved }
        val runs = mutableListOf<MutableList<Int>>()
        if (missing.isNotEmpty()) {
            var currRun = mutableListOf(missing[0])
            for (idx in 1 until missing.size) {
                val m = missing[idx]
                if (m == currRun.last() + 1) {
                    currRun.add(m)
                } else {
                    runs.add(currRun)
                    currRun = mutableListOf(m)
                }
            }
            runs.add(currRun)
        }

        for (run in runs) {
            val k = run.size
            val lStart = run.first()
            val lEnd = run.last()
            val prevL = if (lStart - 1 in resolved) lStart - 1 else null
            val nextL = if (lEnd + 1 in resolved) lEnd + 1 else null

            if (prevL != null && nextL != null) {
                val gapTop = resolved[prevL]!!.maxY
                val gapBot = resolved[nextL]!!.minY
                val gapH = max(k * 10, gapBot - gapTop)
                val slotH = gapH / k
                for ((idx, l) in run.withIndex()) {
                    val margin = min(6, max(1, slotH / 10))
                    val y1 = gapTop + idx * slotH + margin
                    var y2 = gapTop + (idx + 1) * slotH - margin
                    if (y2 <= y1) y2 = y1 + 1
                    val lineType = resolveMissingLineType(page, l, run.size, idx)
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y2, lineType = lineType)
                }
            } else if (nextL != null) {
                val nextTop = resolved[nextL]!!.minY
                val availH = max(k * 10, nextTop - 25)
                val slotH = min(avgHeight, availH / k)
                for ((idx, l) in run.withIndex()) {
                    val stepsFromNext = k - idx
                    var y2 = nextTop - 10 - (stepsFromNext - 1) * (slotH + 10)
                    var y1 = max(15, y2 - slotH)
                    if (y2 <= y1) y2 = y1 + 1
                    val lineType = resolveMissingLineType(page, l, run.size, idx)
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y2, lineType = lineType)
                }
            } else if (prevL != null) {
                val prevBot = resolved[prevL]!!.maxY
                val availH = max(k * 10, 2030 - prevBot)
                val slotH = min(avgHeight, availH / k)
                for ((idx, l) in run.withIndex()) {
                    val y1 = prevBot + 10 + idx * (slotH + 10)
                    var y2 = min(2035, y1 + slotH)
                    if (y2 <= y1) y2 = y1 + 1
                    val lineType = resolveMissingLineType(page, l, run.size, idx)
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y2, lineType = lineType)
                }
            } else {
                val slotH = avgHeight
                for ((idx, l) in run.withIndex()) {
                    val y1 = 30 + idx * (slotH + 15)
                    val lineType = resolveMissingLineType(page, l, run.size, idx)
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y1 + slotH, lineType = lineType)
                }
            }
        }

        // Strict Monotonic Constraint: eliminate any vertical overlaps between consecutive lines
        for (i in 1 until targetLines) {
            val curr = resolved[i]!!
            val next = resolved[i + 1]!!
            if (curr.maxY >= next.minY) {
                val midY = (curr.maxY + next.minY) / 2
                resolved[i] = curr.copy(maxY = midY)
                resolved[i + 1] = next.copy(minY = midY + 1)
            }
        }

        return (1..targetLines).map { resolved[it]!! }
    }

    /**
     * Returns the number of readable lines on [pageNumber] (8 for pages 1 and 2, 15 for all others).
     */
    fun getPageLineCount(pageNumber: Int): Int {
        val safePage = pageNumber.coerceIn(1, 604)
        return if (safePage in 1..2) 8 else 15
    }

    @Synchronized
    fun close() {
        sqliteDb?.close()
        sqliteDb = null
    }
}

