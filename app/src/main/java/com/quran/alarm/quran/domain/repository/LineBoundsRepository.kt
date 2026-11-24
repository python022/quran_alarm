package com.quran.alarm.quran.domain.repository

import com.quran.alarm.quran.domain.model.LineRect

/**
 * Page and line bounds for a contiguous range of ayahs within a Surah.
 */
data class AyahPageRange(
    val startPage: Int,
    val endPage: Int,
    val startLine: Int,
    val endLine: Int
)

/**
 * Repository interface for retrieving line bounding boxes from the Madinah Mushaf.
 */
interface LineBoundsRepository {
    /**
     * Retrieves the line bounding boxes for the given page (1..604).
     * Guaranteed to return a non-null, ordered list of valid [LineRect] bounds
     * (8 lines for pages 1 and 2, 15 lines for pages 3..604).
     */
    suspend fun getLinesForPage(pageNumber: Int): List<LineRect>

    /**
     * Synchronous variant for high-throughput rendering and background calculations.
     * Guaranteed to return a non-null, ordered list of valid [LineRect] bounds
     * (8 lines for pages 1 and 2, 15 lines for pages 3..604).
     */
    fun getLinesForPageSync(pageNumber: Int): List<LineRect>

    /**
     * Returns the number of readable lines on [pageNumber] (8 for pages 1 and 2, 15 for pages 3..604).
     */
    fun getPageLineCount(pageNumber: Int): Int

    /**
     * Resolves the Mushaf page and line span for [startAyah]..[endAyah] in [surahNumber].
     */
    suspend fun getAyahRangeInfo(surahNumber: Int, startAyah: Int, endAyah: Int): AyahPageRange? = null

    /**
     * Resolves the inclusive line range on [pageNumber] covered by [startAyah]..[endAyah] in [surahNumber].
     */
    suspend fun getAyahLineRangeOnPage(
        surahNumber: Int,
        startAyah: Int,
        endAyah: Int,
        pageNumber: Int
    ): IntRange? = null
}

