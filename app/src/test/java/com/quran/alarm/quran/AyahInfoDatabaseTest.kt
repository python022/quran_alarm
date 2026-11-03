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

        // Line 6 and Line 7 must be vertically between line 5 and line 8 without any overlap
        assertTrue("Line 6 top must be strictly below line 5 bottom", line6.minY >= line5.maxY)
        assertTrue("Line 7 top must be strictly below line 6 bottom", line7.minY >= line6.maxY)
        assertTrue("Line 8 top must be strictly below line 7 bottom", line8.minY >= line7.maxY)
    }

    @Test
    fun testMultiLineMissingAtTopPage2() {
        // Page 2 has lines 1 and 2 missing (Surah Al-Baqarah header and Basmalah)
        val lines = database.getLinesForPage(2)
        assertEquals(15, lines.size)

        val line1 = lines[0]
        val line2 = lines[1]
        val line3 = lines[2]

        assertEquals(1, line1.lineNumber)
        assertEquals(2, line2.lineNumber)
        assertEquals(3, line3.lineNumber)

        assertTrue("Line 1 top must be positive", line1.minY >= 0)
        assertTrue("Line 2 top must be strictly below line 1 bottom", line2.minY >= line1.maxY)
        assertTrue("Line 3 top must be strictly below line 2 bottom", line3.minY >= line2.maxY)
    }

    @Test
    fun testGetPageLineCountReturnsCorrectReadableLines() {
        assertEquals(8, database.getPageLineCount(1))
        assertEquals(8, database.getPageLineCount(2))
        assertEquals(15, database.getPageLineCount(3))
        assertEquals(15, database.getPageLineCount(106))
        assertEquals(15, database.getPageLineCount(604))

        assertEquals(8, repository.getPageLineCount(1))
        assertEquals(8, repository.getPageLineCount(2))
        assertEquals(15, repository.getPageLineCount(7))
    }

    @Test
    fun testOutOfRangePagesCoercedSafely() {
        val pageZero = database.getLinesForPage(0)
        assertEquals("Page 0 coerces to page 1", 1, pageZero.first().pageNumber)

        val pageOver = database.getLinesForPage(999)
        assertEquals("Page 999 coerces to page 604", 604, pageOver.first().pageNumber)
    }
}

