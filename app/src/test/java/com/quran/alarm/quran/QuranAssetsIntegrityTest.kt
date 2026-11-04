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

            // Verify RIFF, WEBP, and exact 1260x2038 dimensions
            RandomAccessFile(pageFile, "r").use { raf ->
                val header = ByteArray(30)
                raf.readFully(header)
                val riff = String(header, 0, 4)
                val webp = String(header, 8, 4)
                assertEquals("Page $page must have RIFF header", "RIFF", riff)
                assertEquals("Page $page must have WEBP signature", "WEBP", webp)

                val format = String(header, 12, 4)
                val (width, height) = when (format) {
                    "VP8 " -> {
                        val w = ((header[27].toInt() and 0x3F) shl 8) or (header[26].toInt() and 0xFF)
                        val h = ((header[29].toInt() and 0x3F) shl 8) or (header[28].toInt() and 0xFF)
                        Pair(w, h)
                    }
                    "VP8X" -> {
                        val w = 1 + ((header[24].toInt() and 0xFF) or ((header[25].toInt() and 0xFF) shl 8) or ((header[26].toInt() and 0xFF) shl 16))
                        val h = 1 + ((header[27].toInt() and 0xFF) or ((header[28].toInt() and 0xFF) shl 8) or ((header[29].toInt() and 0xFF) shl 16))
                        Pair(w, h)
                    }
                    else -> Pair(0, 0)
                }
                assertEquals("Page $page width must be 1260", 1260, width)
                assertEquals("Page $page height must be 2038", 2038, height)
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

