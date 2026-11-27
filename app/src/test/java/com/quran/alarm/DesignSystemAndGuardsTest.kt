package com.quran.alarm

import androidx.compose.ui.graphics.toArgb
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.ui.theme.QuranColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesignSystemAndGuardsTest {

    private fun resolveResDir(): File {
        return listOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: error("src/main/res directory must exist")
    }

    @Test
    fun offlineCairoFontsAndThemeResourcesExistWithValidHeaders() {
        val resDir = resolveResDir()
        val fontDir = File(resDir, "font")
        assertTrue("res/font directory must exist", fontDir.exists())

        val fontFiles = listOf(
            "cairo_regular.ttf",
            "cairo_medium.ttf",
            "cairo_semibold.ttf",
            "cairo_bold.ttf"
        )
        for (fileName in fontFiles) {
            val file = File(fontDir, fileName)
            assertTrue("$fileName must exist offline", file.exists())
            assertTrue("$fileName must be larger than 50KB", file.length() > 50_000L)
            val bytes = file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
            assertEquals(0x00.toByte(), bytes[0])
            assertEquals(0x01.toByte(), bytes[1])
            assertEquals(0x00.toByte(), bytes[2])
            assertEquals(0x00.toByte(), bytes[3])
        }

        val cairoXml = File(fontDir, "cairo.xml")
        assertTrue("res/font/cairo.xml must exist", cairoXml.exists())
        val cairoXmlText = cairoXml.readText()
        assertTrue(cairoXmlText.contains("@font/cairo_regular"))
        assertTrue(cairoXmlText.contains("@font/cairo_bold"))

        val themesXml = File(resDir, "values/themes.xml")
        assertTrue("res/values/themes.xml must exist", themesXml.exists())
        val themesText = themesXml.readText()
        assertTrue("themes.xml must apply @font/cairo globally", themesText.contains("@font/cairo"))

        val keepXml = File(resDir, "raw/keep.xml")
        assertTrue(keepXml.readText().contains("@font/cairo_regular"))

        val vibrationIcon = File(resDir, "drawable/ic_vibration.xml")
        assertTrue("res/drawable/ic_vibration.xml must exist", vibrationIcon.exists())
    }

    @Test
    fun nonInvertedRangeGuard_rejectsStartAfterEnd() {
        assertTrue(QuranStructure.isValidNonInvertedRange(1, 1))
        assertTrue(QuranStructure.isValidNonInvertedRange(293, 304))
        assertFalse(QuranStructure.isValidNonInvertedRange(10, 9))
        assertFalse(QuranStructure.isValidNonInvertedRange(604, 1))
        assertEquals(12, QuranStructure.computeNonInvertedPageCount(293, 304))

        val failed = runCatching { QuranStructure.computeNonInvertedPageCount(15, 10) }.isFailure
        assertTrue("computeNonInvertedPageCount must throw for inverted range", failed)
    }

    @Test
    fun quranColors_matchDesignSystemSpecification() {
        assertEquals(0xFF0B1117.toInt(), QuranColors.BgPrimary.toArgb())
        assertEquals(0xFF121820.toInt(), QuranColors.BgCard.toArgb())
        assertEquals(0xFF16202A.toInt(), QuranColors.BgElevated.toArgb())
        assertEquals(0xFF1F2D3D.toInt(), QuranColors.BorderSubtle.toArgb())
        assertEquals(0xFF1AD699.toInt(), QuranColors.CtaBg.toArgb())
        assertEquals(0xFF071A14.toInt(), QuranColors.CtaText.toArgb())
        assertEquals(0xFF142E28.toInt(), QuranColors.ActivePillBg.toArgb())
        assertEquals(0xFF1B2229.toInt(), QuranColors.GoldBadgeBg.toArgb())
        assertEquals(0xFF2D3741.toInt(), QuranColors.GoldBadgeBorder.toArgb())
        assertEquals(0xFFD4AF37.toInt(), QuranColors.GoldBadgeText.toArgb())
        assertEquals(0xFFF8FAFC.toInt(), QuranColors.TextPrimary.toArgb())
        assertEquals(0xFF94A3B8.toInt(), QuranColors.TextSecondary.toArgb())
        assertEquals(0xFF64748B.toInt(), QuranColors.TextMuted.toArgb())
    }
}
