package com.quran.alarm.alarm.domain.model

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranStructureAndPresetsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun getLastSurahAtPage_page604_returnsSurahAnNas() {
        val lastSurah = QuranStructure.getLastSurahAtPage(604)
        assertEquals("Surah 114 (An-Nas) must be the concluding surah on page 604", 114, lastSurah.number)
        assertEquals("الناس", lastSurah.nameArabic)

        // Contrast with getSurahAtPage which returns the first surah starting on page 604
        val firstSurah = QuranStructure.getSurahAtPage(604)
        assertEquals(112, firstSurah.number)
        assertEquals("الإخلاص", firstSurah.nameArabic)
    }

    @Test
    fun getLastSurahAtPage_page1_returnsAlFatihah() {
        val surah = QuranStructure.getLastSurahAtPage(1)
        assertEquals(1, surah.number)
        assertEquals("الفاتحة", surah.nameArabic)
    }

    @Test
    fun strictWhitelist_rejectsNetworkAndStreamingSchemes_compliesWithLaw2() {
        val prohibitedUris = listOf(
            "http://example.com/sound.mp3",
            "https://stream.quran.com/audio.wav",
            "ftp://files.org/sound.mp3",
            "rtsp://streaming.server:554/live.sdp",
            "ws://socket.host/stream",
            "wss://secure.socket/stream",
            "smb://network/share/sound.wav"
        )

        for (uri in prohibitedUris) {
            val resolved = AlarmSoundPresets.resolveUri(context, uri)
            assertNull("Network/streaming scheme '$uri' must be rejected", resolved)
        }

        val allowedContentUri = "content://media/external/audio/media/1234"
        val resolvedContent = AlarmSoundPresets.resolveUri(context, allowedContentUri)
        assertNotNull("content:// scheme must be allowed", resolvedContent)

        val disallowedExternalFileUri = "file:///storage/emulated/0/Alarms/adhan.wav"
        val resolvedExternalFile = AlarmSoundPresets.resolveUri(context, disallowedExternalFileUri)
        assertNull("Arbitrary external file:// path outside custom_sounds must be rejected", resolvedExternalFile)

        val storageContext = com.quran.alarm.alarm.data.storage.DirectBootStorageHelper.resolveStorageContext(context)
        val customSoundFile = java.io.File(java.io.File(storageContext.filesDir, "custom_sounds"), "alarm_test.audio")
        val allowedCustomFileUri = android.net.Uri.fromFile(customSoundFile).toString()
        val resolvedCustomFile = AlarmSoundPresets.resolveUri(context, allowedCustomFileUri)
        assertNotNull("file:// inside app custom_sounds must be allowed", resolvedCustomFile)
    }

    @Test
    fun normalizeArabicForSearch_matchesHamzaTaMarbutaAndDiacritics() {
        val surahs = QuranStructure.SURAHS
        fun search(query: String): List<String> {
            val q = com.quran.alarm.ui.components.normalizeArabicForSearch(query)
            return surahs.filter {
                com.quran.alarm.ui.components.normalizeArabicForSearch(it.nameArabic).contains(q)
            }.map { it.nameArabic }
        }

        assertTrue(search("ابراهيم").contains("إبراهيم"))
        assertTrue(search("الاخلاص").contains("الإخلاص"))
        assertTrue(search("ال عمران").contains("آل عمران"))
        assertTrue(search("التوبه").contains("التوبة"))
    }
}
