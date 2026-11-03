package com.quran.alarm.quran

import android.content.Context
import com.quran.alarm.quran.data.AyahInfoDatabase
import com.quran.alarm.quran.data.AyahInfoLineBoundsRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AyahInfoDatabaseTest {

    private lateinit var context: Context
    private lateinit var database: AyahInfoDatabase
    private lateinit var repository: AyahInfoLineBoundsRepositoryImpl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Open the database file directly from assets dir on host or via context
        val assetDb = File("src/main/assets/quran/ayahinfo.db")
        database = if (assetDb.exists()) {
            AyahInfoDatabase(assetDb)
        } else {
            AyahInfoDatabase.getInstance(context)
        }
        repository = AyahInfoLineBoundsRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAll604PagesReturnExactly15ValidLinesWithoutNulls() {
        for (page in 1..604) {
            val lines = database.getLinesForPage(page)
            assertEquals("Page $page must have exactly 15 lines", 15, lines.size)

            for (i in 0 until 15) {
                val line = lines[i]
                assertNotNull("Line must not be null", line)
                assertEquals("Line number must be consecutive 1..15", i + 1, line.lineNumber)
                assertEquals("Page number must match query", page, line.pageNumber)

                assertTrue("minX (${line.minX}) must be >= 0 on page $page line ${line.lineNumber}", line.minX >= 0)
                assertTrue("maxX (${line.maxX}) must be > minX (${line.minX}) on page $page", line.maxX > line.minX)
                assertTrue("minY (${line.minY}) must be >= 0 on page $page", line.minY >= 0)
                assertTrue("maxY (${line.maxY}) must be > minY (${line.minY}) on page $page", line.maxY > line.minY)

                assertTrue("LineRect must report valid()", line.isValid())
            }
        }
    }

    @Test
    fun testRepositoryReturns15LinesCoroutines() = runBlocking {
        val lines = repository.getLinesForPage(7)
        assertEquals(15, lines.size)
        assertEquals(1, lines.first().lineNumber)
        assertEquals(15, lines.last().lineNumber)
    }

    @Test
    fun testHeaderGapInterpolationOnPage106() {
        // Page 106 has lines 6 and 7 missing in raw glyphs due to Surah Al-An'am header and Basmalah
        val lines = database.getLinesForPage(106)
        assertEquals(15, lines.size)

        val line5 = lines[4]
        val line6 = lines[5]
        val line7 = lines[6]
        val line8 = lines[7]

        assertEquals(5, line5.lineNumber)
        assertEquals(6, line6.lineNumber)
        assertEquals(7, line7.lineNumber)
        assertEquals(8, line8.lineNumber)

        // Line 6 and Line 7 must be vertically between line 5 and line 8
        assertTrue("Line 6 top must be at or below line 5 top", line6.minY >= line5.minY)
        assertTrue("Line 7 top must be at or below line 6 top", line7.minY >= line6.minY)
        assertTrue("Line 8 bottom must be greater than line 7 bottom", line8.maxY > line7.maxY)
    }

    @Test
    fun testOutOfRangePagesCoercedSafely() {
        val pageZero = database.getLinesForPage(0)
        assertEquals("Page 0 coerces to page 1", 1, pageZero.first().pageNumber)

        val pageOver = database.getLinesForPage(999)
        assertEquals("Page 999 coerces to page 604", 604, pageOver.first().pageNumber)
    }
}

