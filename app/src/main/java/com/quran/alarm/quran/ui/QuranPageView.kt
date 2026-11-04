package com.quran.alarm.quran.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.service.CoordinateNormalizer

/**
 * Authentic Madinah Mushaf page view rendered via Canvas in Jetpack Compose.
 * Displays the page texture pixel-for-pixel and highlights the currently active line
 * with soft translucent green.
 */
@Composable
fun QuranPageView(
    pageNumber: Int,
    pageBitmap: Bitmap?,
    lines: List<LineRect>,
    activeLine: Int? = null,
    highlightColor: Color = Color(0x334CAF50),
    onLineClicked: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val normalizer = remember { CoordinateNormalizer() }
    val density = LocalDensity.current.density
    val pageColor = Color(0xFFFFFDF5) // Authentic Madinah mushaf paper tint

    // Pre-wrap ImageBitmap to eliminate frame-by-frame GC allocations during pulse animations
    val imageBitmap = remember(pageBitmap) {
        if (pageBitmap != null && !pageBitmap.isRecycled) pageBitmap.asImageBitmap() else null
    }

    // Pre-filter target line to ensure decorative surah banners are never highlighted
    val targetLine = remember(lines, activeLine) {
        lines.firstOrNull { it.lineNumber == activeLine && it.isReadable }
    }

    val cornerRadius = remember { CornerRadius(12f, 12f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(pageColor)
            .semantics {
                contentDescription = "صفحة المصحف الشريف رقم $pageNumber"
            }
    ) {
        val canvasW = constraints.maxWidth.toFloat()
        val canvasH = constraints.maxHeight.toFloat()

        val proj = remember(canvasW, canvasH) {
            normalizer.calculateProjection(canvasW, canvasH)
        }
        val dstOffset = remember(proj) {
            IntOffset(proj.offsetX.toInt(), proj.offsetY.toInt())
        }
        val dstSize = remember(proj) {
            IntSize(proj.renderedWidth.toInt(), proj.renderedHeight.toInt())
        }

        val normHighlight = remember(targetLine, canvasW, canvasH) {
            targetLine?.let { line ->
                normalizer.normalize(line, canvasW, canvasH, verticalAbsorptionPx = 4f)
            }
        }
        val highlightTopLeft = remember(normHighlight) {
            normHighlight?.let { Offset(it.left, it.top) }
        }
        val highlightSize = remember(normHighlight) {
            normHighlight?.let { Size(it.width, it.height) }
        }

        val normalizedLines = remember(lines, canvasW, canvasH) {
            normalizer.normalizeAll(lines, canvasW, canvasH)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(normalizedLines, onLineClicked, density) {
                    if (onLineClicked != null) {
                        detectTapGestures { tapOffset ->
                            val clickedLine = normalizer.findLineAt(
                                tapOffset.x,
                                tapOffset.y,
                                normalizedLines,
                                density = density
                            )
                            if (clickedLine != null) {
                                val selected = lines.firstOrNull { it.lineNumber == clickedLine }
                                if (selected?.isReadable == true) {
                                    onLineClicked(clickedLine)
                                }
                            }
                        }
                    }
                }
        ) {
            if (imageBitmap != null) {
                drawImage(
                    image = imageBitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )

                // Render active line highlight only if target is readable (zero allocations inside onDraw)
                if (highlightTopLeft != null && highlightSize != null) {
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = highlightTopLeft,
                        size = highlightSize,
                        cornerRadius = cornerRadius
                    )
                }
            }
        }
    }
}

