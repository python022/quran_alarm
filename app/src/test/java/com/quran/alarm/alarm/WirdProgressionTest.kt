package com.quran.alarm.alarm

import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import org.junit.Assert.assertEquals
import org.junit.Test

class WirdProgressionTest {

    @Test
    fun standardWird_calculatesEndPageCorrectly() {
        // Juz 3 starts at page 42, with 5 pages daily portion
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
    fun advanceToNextWird_advancesByPageCount() {
        val day1Alarm = AlarmItem(
            id = 1,
            hour = 5,
            minute = 0,
            startPage = 42,
            pageCount = 5
        )

        val day2Alarm = day1Alarm.advanceToNextWird()
        assertEquals(47, day2Alarm.startPage)
        assertEquals(51, day2Alarm.endPage) // 47, 48, 49, 50, 51

        val day3Alarm = day2Alarm.advanceToNextWird()
        assertEquals(52, day3Alarm.startPage)
        assertEquals(56, day3Alarm.endPage)
    }

    @Test
    fun advanceToNextWird_wrapsAroundAtPage604() {
        // Start near the end of the Mushaf
        val alarm = AlarmItem(
            id = 2,
            hour = 5,
            minute = 0,
            startPage = 601,
            pageCount = 5
        )

        val nextAlarm = alarm.advanceToNextWird()
        // 601, 602, 603, 604 -> next start is page 2
        assertEquals(2, nextAlarm.startPage)
    }

    @Test
    fun quranStructure_juzStartPagesMatchMadinahMushaf() {
        assertEquals(1, QuranStructure.getStartPageForJuz(1))
        assertEquals(22, QuranStructure.getStartPageForJuz(2))
        assertEquals(42, QuranStructure.getStartPageForJuz(3))
        assertEquals(582, QuranStructure.getStartPageForJuz(30))
    }
}

