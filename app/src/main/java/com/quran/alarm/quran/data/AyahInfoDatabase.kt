package com.quran.alarm.quran.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.alarm.quran.domain.model.LineRect
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

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

        val resolved = mutableListOf<LineRect>()
        val maxLines = if (page in 1..2) 8 else 15

        for (l in 1..maxLines) {
            val existing = rawLines[l]
            if (existing != null) {
                resolved.add(
                    LineRect(
                        pageNumber = page,
                        lineNumber = l,
                        minX = existing[0],
                        maxX = existing[1],
                        minY = existing[2],
                        maxY = existing[3]
                    )
                )
            } else {
                // Interpolate gap
                val prev = rawLines.keys.filter { it < l }.maxOrNull()
                val next = rawLines.keys.filter { it > l }.minOrNull()

                var topY: Int
                var botY: Int

                if (prev != null && next != null) {
                    val prevBot = rawLines[prev]!![3]
                    val nextTop = rawLines[next]!![2]
                    val gap = nextTop - prevBot
                    val steps = next - prev
                    val stepY = gap.toFloat() / steps
                    val offset = l - prev
                    topY = (prevBot + (offset - 1) * stepY + (stepY - avgHeight) / 2f).toInt()
                    botY = topY + avgHeight
                } else if (prev != null) {
                    topY = rawLines[prev]!![3] + 15
                    botY = topY + avgHeight
                } else if (next != null) {
                    botY = rawLines[next]!![2] - 15
                    topY = max(10, botY - avgHeight)
                    if (botY <= topY) botY = topY + avgHeight
                } else {
                    topY = 30 + (l - 1) * (avgHeight + 40)
                    botY = topY + avgHeight
                }

                topY = max(0, topY)
                botY = max(topY + 1, botY)

                resolved.add(
                    LineRect(
                        pageNumber = page,
                        lineNumber = l,
                        minX = avgMinX,
                        maxX = avgMaxX,
                        minY = topY,
                        maxY = botY
                    )
                )
            }
        }

        // Expand pages 1 and 2 to 15 slots so any 15-line query is satisfied
        if (page in 1..2 && resolved.size < 15) {
            while (resolved.size < 15) {
                val last = resolved.last()
                val nextL = resolved.size + 1
                val topY = last.maxY + 15
                val botY = topY + avgHeight
                resolved.add(
                    LineRect(
                        pageNumber = page,
                        lineNumber = nextL,
                        minX = avgMinX,
                        maxX = avgMaxX,
                        minY = topY,
                        maxY = botY
                    )
                )
            }
        }

        return resolved
    }

    @Synchronized
    fun close() {
        sqliteDb?.close()
        sqliteDb = null
    }
}

