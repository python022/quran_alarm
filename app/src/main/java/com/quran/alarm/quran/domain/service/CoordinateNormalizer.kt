package com.quran.alarm.quran.domain.service

import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.model.NormalizedLineRect
import kotlin.math.min

/**
 * Transforms reference Madinah Mushaf coordinates (1260x1920) into actual screen viewport pixels
 * while maintaining aspect ratio (FitCenter) and adding vertical absorption margins.
 */
class CoordinateNormalizer(
    val referenceWidth: Float = 1260f,
    val referenceHeight: Float = 1920f
) {
    data class ViewportProjection(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val renderedWidth: Float,
        val renderedHeight: Float
    )

    /**
     * Calculates the FitCenter projection parameters for the given viewport.
     */
    fun calculateProjection(viewportWidth: Float, viewportHeight: Float): ViewportProjection {
        val safeWidth = if (viewportWidth > 0f) viewportWidth else referenceWidth
        val safeHeight = if (viewportHeight > 0f) viewportHeight else referenceHeight

        val scale = min(safeWidth / referenceWidth, safeHeight / referenceHeight)
        val renderedWidth = referenceWidth * scale
        val renderedHeight = referenceHeight * scale
        val offsetX = (safeWidth - renderedWidth) / 2f
        val offsetY = (safeHeight - renderedHeight) / 2f

        return ViewportProjection(
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            renderedWidth = renderedWidth,
            renderedHeight = renderedHeight
        )
    }

    /**
     * Normalizes a single [LineRect] into screen coordinates with absorption padding.
     */
    fun normalize(
        line: LineRect,
        viewportWidth: Float,
        viewportHeight: Float,
        verticalAbsorptionPx: Float = 8f
    ): NormalizedLineRect {
        val proj = calculateProjection(viewportWidth, viewportHeight)

        val screenLeft = proj.offsetX + line.minX * proj.scale
        val screenRight = proj.offsetX + line.maxX * proj.scale
        val screenTop = (proj.offsetY + line.minY * proj.scale - verticalAbsorptionPx).coerceAtLeast(0f)
        val screenBottom = (proj.offsetY + line.maxY * proj.scale + verticalAbsorptionPx).coerceAtMost(viewportHeight)

        // Ratios relative to the rendered page (independent of letterbox offsets)
        val leftRatio = (line.minX / referenceWidth).coerceIn(0f, 1f)
        val rightRatio = (line.maxX / referenceWidth).coerceIn(0f, 1f)
        val topRatio = (line.minY / referenceHeight).coerceIn(0f, 1f)
        val bottomRatio = (line.maxY / referenceHeight).coerceIn(0f, 1f)

        return NormalizedLineRect(
            lineNumber = line.lineNumber,
            left = screenLeft,
            top = screenTop,
            right = screenRight,
            bottom = screenBottom,
            leftRatio = leftRatio,
            topRatio = topRatio,
            rightRatio = rightRatio,
            bottomRatio = bottomRatio
        )
    }

    /**
     * Normalizes a full list of lines for a page.
     */
    fun normalizeAll(
        lines: List<LineRect>,
        viewportWidth: Float,
        viewportHeight: Float,
        verticalAbsorptionPx: Float = 8f
    ): List<NormalizedLineRect> {
        return lines.map { normalize(it, viewportWidth, viewportHeight, verticalAbsorptionPx) }
    }

    /**
     * Finds the matching line for screen point (x, y).
     * If strictly within a line, returns that line number.
     * If between lines, snaps to the closest line within [maxSnapDistancePx].
     */
    fun findLineAt(
        x: Float,
        y: Float,
        lines: List<NormalizedLineRect>,
        maxSnapDistancePx: Float = 32f
    ): Int? {
        if (lines.isEmpty()) return null

        // 1. Direct hit
        val direct = lines.firstOrNull { it.contains(x, y) }
        if (direct != null) return direct.lineNumber

        // 2. Proximity snap within vertical threshold
        val horizontalMatch = lines.filter { x >= it.left - 20f && x <= it.right + 20f }
        val candidatePool = if (horizontalMatch.isNotEmpty()) horizontalMatch else lines

        val closest = candidatePool.minByOrNull { it.verticalDistanceTo(y) }
        return if (closest != null && closest.verticalDistanceTo(y) <= maxSnapDistancePx) {
            closest.lineNumber
        } else {
            null
        }
    }
}

