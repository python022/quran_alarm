package com.quran.alarm.quran.domain.repository

import com.quran.alarm.quran.domain.model.LineRect

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
}

