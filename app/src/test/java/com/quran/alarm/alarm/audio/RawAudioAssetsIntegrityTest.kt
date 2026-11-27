package com.quran.alarm.alarm.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RawAudioAssetsIntegrityTest {

    private fun resolveRawDir(): File {
        return listOf(
            File("src/main/res/raw"),
            File("app/src/main/res/raw")
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: error("res/raw directory must exist")
    }

    @Test
    fun keepXml_protectsAllBuiltInAlarmRawSoundsFromResourceShrinking() {
        val rawDir = resolveRawDir()
        val keepXml = File(rawDir, "keep.xml")
        assertTrue("res/raw/keep.xml must exist to guard R.raw assets in release builds", keepXml.exists())

        val content = keepXml.readText()
        for (name in listOf("@raw/adhan_fajr", "@raw/calm_tone", "@raw/eid_takbeer", "@raw/quran_alert")) {
            assertTrue("keep.xml must retain $name", content.contains(name))
        }
    }

    @Test
    fun builtInM4aAssets_areNonFragmentedFaststartContainersWithMoovBeforeMdat() {
        val rawDir = resolveRawDir()
        for (fileName in listOf("adhan_fajr.m4a", "eid_takbeer.m4a", "quran_alert.m4a")) {
            val file = File(rawDir, fileName)
            assertTrue("$fileName must exist", file.exists())
            assertTrue("$fileName must be non-empty", file.length() > 100_000L)

            val bytes = file.readBytes()
            val topBoxes = mutableListOf<String>()
            var pos = 0
            while (pos + 8 <= bytes.size) {
                val sz = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val typ = String(bytes, pos + 4, 4, Charsets.US_ASCII)
                assertTrue("$fileName invalid box size $sz at $pos", sz >= 8 && pos + sz <= bytes.size)
                topBoxes.add(typ)
                if (typ == "moov") {
                    val moovSlice = bytes.copyOfRange(pos, (pos + sz).toInt())
                    assertFalse("$fileName must not contain fragmented mvex box", String(moovSlice, Charsets.ISO_8859_1).contains("mvex"))
                }
                pos += sz.toInt()
            }

            assertEquals("$fileName must have clean faststart box layout", listOf("ftyp", "moov", "mdat"), topBoxes)
        }
    }

    @Test
    fun calmToneWebmAsset_existsWithValidEbmlHeader() {
        val rawDir = resolveRawDir()
        val file = File(rawDir, "calm_tone.webm")
        assertTrue("calm_tone.webm must exist", file.exists())
        assertTrue("calm_tone.webm must be non-empty", file.length() > 100_000L)

        val header = file.inputStream().use { input ->
            ByteArray(4).also { input.read(it) }
        }
        assertEquals(0x1A.toByte(), header[0])
        assertEquals(0x45.toByte(), header[1])
        assertEquals(0xDF.toByte(), header[2])
        assertEquals(0xA3.toByte(), header[3])
    }
}
