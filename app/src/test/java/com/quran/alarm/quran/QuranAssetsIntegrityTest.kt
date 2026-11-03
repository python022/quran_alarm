package com.quran.alarm.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class QuranAssetsIntegrityTest {

    private val assetsBaseDir = File("src/main/assets/quran")

    @Test
    fun testAll604PagesExistInAssetsAndAreNonEmpty() {
        val pagesDir = File(assetsBaseDir, "pages")
        assertTrue("Pages directory must exist at assets/quran/pages", pagesDir.exists() && pagesDir.isDirectory)

        val files = pagesDir.listFiles() ?: emptyArray()
        assertEquals("There must be exactly 604 WebP pages in assets", 604, files.size)

        for (page in 1..604) {
            val pageFileName = String.format("page_%03d.webp", page)
            val pageFile = File(pagesDir, pageFileName)
            assertTrue("Page $page file ($pageFileName) must exist", pageFile.exists())
            assertTrue("Page $page file must have positive byte size", pageFile.length() > 1024)

            // Verify RIFF and WEBP header magic bytes
            RandomAccessFile(pageFile, "r").use { raf ->
                val header = ByteArray(12)
                raf.readFully(header)
                val riff = String(header, 0, 4)
                val webp = String(header, 8, 4)
                assertEquals("Page $page must have RIFF header", "RIFF", riff)
                assertEquals("Page $page must have WEBP signature", "WEBP", webp)
            }
        }
    }

    @Test
    fun testAyahInfoDatabaseAssetExistsAndHasValidHeader() {
        val dbFile = File(assetsBaseDir, "ayahinfo.db")
        assertTrue("ayahinfo.db must exist at assets/quran/ayahinfo.db", dbFile.exists() && dbFile.isFile)
        assertTrue("ayahinfo.db must be non-empty (expected ~5.9 MB)", dbFile.length() > 1_000_000)

        // Verify SQLite 3 header: "SQLite format 3\0"
        RandomAccessFile(dbFile, "r").use { raf ->
            val header = ByteArray(16)
            raf.readFully(header)
            val magic = String(header, 0, 15)
            assertEquals("Database must start with SQLite format 3", "SQLite format 3", magic)
        }
    }
}

