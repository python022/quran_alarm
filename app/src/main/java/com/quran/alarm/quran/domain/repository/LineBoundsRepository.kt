package com.quran.alarm.quran.domain.repository

import com.quran.alarm.quran.domain.model.LineRect

/**
 * Repository interface for retrieving line bounding boxes from the Madinah Mushaf.
 */
interface LineBoundsRepository {
    /**
     * Retrieves the 15 line bounding boxes for the given page (1..604).
     * Guaranteed to return a non-null, ordered list of 15 valid [LineRect] bounds.
     */
    suspend fun getLinesForPage(pageNumber: Int): List<LineRect>

    /**
     * Synchronous variant for high-throughput rendering and background calculations.
     */
    fun getLinesForPageSync(pageNumber: Int): List<LineRect>
}

