package com.quran.alarm.alarm

import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirdProgressionTest {

    @Test
    fun standardWird_calculatesEndPageCorrectly() {
        val alarm = AlarmItem(
            id = 1,
            hour = 5,
            minute = 0,
            startPage = 42,
            pageCount = 5
        )

        assertEquals(42, alarm.startPage)
        assertEquals(46, alarm.endPage) // 42, 43, 44, 45, 46
    }

    @Test
    fun advanceToNextWird_advancesByPageCount_whenSequential() {
        val day1Alarm = AlarmItem(
            id = 1,
            hour = 5,
            minute = 0,
            startPage = 42,
            pageCount = 5,
            isSequential = true
        )

        val day2Alarm = day1Alarm.advanceToNextWird()
        assertEquals(47, day2Alarm.startPage)
        assertEquals(51, day2Alarm.endPage)

        val day3Alarm = day2Alarm.advanceToNextWird()
        assertEquals(52, day3Alarm.startPage)
        assertEquals(56, day3Alarm.endPage)
    }

    @Test
    fun advanceToNextWird_doesNotAdvance_whenFixedWird() {
        // e.g. Surat Al-Mulk daily (page 562 to 564)
        val fixedAlarm = AlarmItem(
            id = 10,
            hour = 22,
            minute = 30,
            startPage = 562,
            pageCount = 3,
            isSequential = false
        )

        val nextAlarm = fixedAlarm.advanceToNextWird()
        assertEquals(562, nextAlarm.startPage)
        assertEquals(564, nextAlarm.endPage)
    }

    @Test
    fun advanceToNextWird_wrapsAroundAtPage604() {
        val alarm = AlarmItem(
            id = 2,
            hour = 5,
            minute = 0,
            startPage = 601,
            pageCount = 5,
            isSequential = true
        )

        val nextAlarm = alarm.advanceToNextWird()
        assertEquals(2, nextAlarm.startPage)
    }

    @Test
    fun quranStructure_surahsMetadata_isCompleteAndAccurate() {
        assertEquals(114, QuranStructure.SURAHS.size)
        
        // Surah 1: Al-Fatihah
        val fatihah = QuranStructure.getSurahByNumber(1)
        assertEquals("الفاتحة", fatihah.nameArabic)
        assertEquals(1, fatihah.startPage)
        assertEquals(1, fatihah.endPage)
        assertEquals(7, fatihah.ayahCount)

        // Surah 2: Al-Baqarah
        val baqarah = QuranStructure.getSurahByNumber(2)
        assertEquals("البقرة", baqarah.nameArabic)
        assertEquals(2, baqarah.startPage)
        assertEquals(49, baqarah.endPage)
        assertEquals(286, baqarah.ayahCount)

        // Surah 18: Al-Kahf
        val kahf = QuranStructure.getSurahByNumber(18)
        assertEquals("الكهف", kahf.nameArabic)
        assertEquals(293, kahf.startPage)
        assertEquals(304, kahf.endPage)

        // Surah 67: Al-Mulk
        val mulk = QuranStructure.getSurahByNumber(67)
        assertEquals("الملك", mulk.nameArabic)
        assertEquals(562, mulk.startPage)
        assertEquals(564, mulk.endPage)

        // Surah 114: An-Nas
        val nas = QuranStructure.getSurahByNumber(114)
        assertEquals("الناس", nas.nameArabic)
        assertEquals(604, nas.startPage)
        assertEquals(604, nas.endPage)
    }

    @Test
    fun quranStructure_ahzabMetadata_isCompleteAndAccurate() {
        assertEquals(60, QuranStructure.HIZB_START_PAGES.size)
        assertEquals(1, QuranStructure.getStartPageForHizb(1))
        assertEquals(10, QuranStructure.getEndPageForHizb(1))
        assertEquals(11, QuranStructure.getStartPageForHizb(2))
        assertEquals(591, QuranStructure.getStartPageForHizb(60))
        assertEquals(604, QuranStructure.getEndPageForHizb(60))
    }

    @Test
    fun quranStructure_pageMapping_resolvesAccurately() {
        assertEquals("البقرة", QuranStructure.getSurahAtPage(2).nameArabic)
        assertEquals("الكهف", QuranStructure.getSurahAtPage(295).nameArabic)
        assertEquals("الناس", QuranStructure.getSurahAtPage(604).nameArabic)

        assertEquals(1, QuranStructure.getJuzAtPage(1))
        assertEquals(3, QuranStructure.getJuzAtPage(42))
        assertEquals(30, QuranStructure.getJuzAtPage(604))

        assertEquals(1, QuranStructure.getHizbAtPage(1))
        assertEquals(5, QuranStructure.getHizbAtPage(42))
        assertEquals(60, QuranStructure.getHizbAtPage(604))
    }

    @Test
    fun khatmaForecast_calculatesAccurately() {
        // 5 pages daily starting at page 1
        val forecast1 = QuranStructure.calculateKhatmaForecast(startPage = 1, dailyPages = 5)
        assertEquals(604, forecast1.totalPagesRemaining)
        assertEquals(121, forecast1.daysToComplete) // ceil(604 / 5) = 121
        assertEquals(0f, forecast1.progressPercentage, 0.01f)

        // Halfway through: page 303 (remaining 302 pages) at 10 pages daily
        val forecast2 = QuranStructure.calculateKhatmaForecast(startPage = 303, dailyPages = 10)
        assertEquals(302, forecast2.totalPagesRemaining)
        assertEquals(31, forecast2.daysToComplete) // ceil(302 / 10) = 31
        assertEquals(50f, forecast2.progressPercentage, 0.1f)
    }
}
