package com.quran.alarm.quran.domain.model

/**
 * Represents the pixel bounding box of a single printed line in the Madinah Mushaf (1260x1920 reference).
 */
data class LineRect(
    val pageNumber: Int,
    val lineNumber: Int,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
) {
    val width: Int get() = maxX - minX
    val height: Int get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f

    fun isValid(): Boolean {
        return pageNumber in 1..604 &&
                lineNumber in 1..15 &&
                minX >= 0 && maxX > minX &&
                minY >= 0 && maxY > minY
    }
}

