package com.quran.alarm.quran.domain.model

/**
 * Semantic classification of a line on the Madinah Mushaf page.
 */
enum class LineType {
    AYAH_TEXT,
    SURAH_HEADER,
    BASMALAH;

    val isReadable: Boolean get() = this != SURAH_HEADER
}

/**
 * Represents the pixel bounding box of a single printed line in the Madinah Mushaf (1260x2038 reference).
 */
data class LineRect(
    val pageNumber: Int,
    val lineNumber: Int,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
    val lineType: LineType = LineType.AYAH_TEXT
) {
    val width: Int get() = maxX - minX
    val height: Int get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val isReadable: Boolean get() = lineType.isReadable

    fun isValid(): Boolean {
        return pageNumber in 1..604 &&
                lineNumber in 1..15 &&
                minX >= 0 && maxX > minX && maxX <= 1260 &&
                minY >= 0 && maxY > minY && maxY <= 2038
    }
}


