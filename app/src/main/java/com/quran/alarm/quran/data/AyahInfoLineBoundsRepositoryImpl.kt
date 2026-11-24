package com.quran.alarm.quran.data

import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.repository.AyahPageRange
import com.quran.alarm.quran.domain.repository.LineBoundsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [LineBoundsRepository] backed by [AyahInfoDatabase].
 */
class AyahInfoLineBoundsRepositoryImpl(
    private val database: AyahInfoDatabase
) : LineBoundsRepository {

    override suspend fun getLinesForPage(pageNumber: Int): List<LineRect> = withContext(Dispatchers.IO) {
        database.getLinesForPage(pageNumber)
    }

    override fun getLinesForPageSync(pageNumber: Int): List<LineRect> {
        return database.getLinesForPage(pageNumber)
    }

    override fun getPageLineCount(pageNumber: Int): Int {
        return database.getPageLineCount(pageNumber)
    }

    override suspend fun getAyahRangeInfo(
        surahNumber: Int,
        startAyah: Int,
        endAyah: Int
    ): AyahPageRange? = withContext(Dispatchers.IO) {
        database.getAyahRangeInfo(surahNumber, startAyah, endAyah)
    }

    override suspend fun getAyahLineRangeOnPage(
        surahNumber: Int,
        startAyah: Int,
        endAyah: Int,
        pageNumber: Int
    ): IntRange? = withContext(Dispatchers.IO) {
        database.getAyahLineRangeOnPage(surahNumber, startAyah, endAyah, pageNumber)
    }
}

