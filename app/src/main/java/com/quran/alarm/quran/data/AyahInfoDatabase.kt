package com.quran.alarm.quran.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.alarm.quran.domain.model.LineRect
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Manages access to the bundled ayahinfo.db SQLite database.
 * Extracts the database from assets if not present in the app's database storage,
 * and provides queries for glyph and line coordinates.
 */
class AyahInfoDatabase(
    private val dbFile: File
) {
    companion object {
        const val ASSET_PATH = "quran/ayahinfo.db"
        const val DB_NAME = "ayahinfo.db"

        @Volatile
        private var instance: AyahInfoDatabase? = null

        fun getInstance(context: Context): AyahInfoDatabase {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val targetFile = context.getDatabasePath(DB_NAME)
                    if (!targetFile.exists() || targetFile.length() < 1_000_000) {
                        targetFile.parentFile?.mkdirs()
                        context.assets.open(ASSET_PATH).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    AyahInfoDatabase(targetFile).also { instance = it }
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
        val opened = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        sqliteDb = opened
        return opened
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
        val targetLines = if (page in 1..2) 8 else 15

        for (l in 1..targetLines) {
            val existing = rawLines[l]
            if (existing != null) {
                resolved[l] = LineRect(
                    pageNumber = page,
                    lineNumber = l,
                    minX = if (existing[0] == existing[1]) avgMinX else existing[0],
                    maxX = if (existing[0] == existing[1]) avgMaxX else existing[1],
                    minY = existing[2],
                    maxY = existing[3]
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
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y2)
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
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y2)
                }
            } else if (prevL != null) {
                val prevBot = resolved[prevL]!!.maxY
                val availH = max(k * 10, 2030 - prevBot)
                val slotH = min(avgHeight, availH / k)
                for ((idx, l) in run.withIndex()) {
                    val y1 = prevBot + 10 + idx * (slotH + 10)
                    var y2 = min(2035, y1 + slotH)
                    if (y2 <= y1) y2 = y1 + 1
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y2)
                }
            } else {
                val slotH = avgHeight
                for ((idx, l) in run.withIndex()) {
                    val y1 = 30 + idx * (slotH + 15)
                    resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, y1 + slotH)
                }
            }
        }

        // Expand pages 1 and 2 from 8 to 15 slots so all 15-line queries are satisfied
        if (page in 1..2) {
            for (l in 9..15) {
                val prev = resolved[l - 1]!!
                val y1 = prev.maxY + 15
                val y2 = min(2035, y1 + avgHeight)
                resolved[l] = LineRect(page, l, avgMinX, avgMaxX, y1, max(y1 + 1, y2))
            }
        }

        return (1..15).map { resolved[it]!! }
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

