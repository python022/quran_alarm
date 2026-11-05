package com.quran.alarm.alarm.domain.model

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            assertNull("Network/streaming scheme '$uri' must be rejected under AGENTS.md Law 2", resolved)
        }

        val allowedContentUri = "content://media/external/audio/media/1234"
        val resolvedContent = AlarmSoundPresets.resolveUri(context, allowedContentUri)
        assertNotNull("content:// scheme must be allowed", resolvedContent)

        val allowedFileUri = "file:///storage/emulated/0/Alarms/adhan.wav"
        val resolvedFile = AlarmSoundPresets.resolveUri(context, allowedFileUri)
        assertNotNull("file:// scheme must be allowed", resolvedFile)
    }
}
