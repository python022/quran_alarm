package com.quran.alarm.quran.domain.model

/**
 * Screen-projected bounding box for a line in pixels, including normalized [0f..1f] ratios.
 */
data class NormalizedLineRect(
    val lineNumber: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val leftRatio: Float,
    val topRatio: Float,
    val rightRatio: Float,
    val bottomRatio: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }

    fun verticalDistanceTo(y: Float): Float {
        return when {
            y < top -> top - y
            y > bottom -> y - bottom
            else -> 0f
        }
    }
}

