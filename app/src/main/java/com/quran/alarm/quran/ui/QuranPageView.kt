package com.quran.alarm.quran.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.service.CoordinateNormalizer

/**
 * Authentic Madinah Mushaf page view rendered via Canvas in Jetpack Compose.
 * Displays the 15-line page texture pixel-for-pixel and highlights the currently active line
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
    val pageColor = Color(0xFFFFFDF5) // Authentic Madinah mushaf paper tint

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageColor)
            .semantics {
                contentDescription = "صفحة المصحف الشريف رقم $pageNumber"
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(lines, onLineClicked) {
                    if (onLineClicked != null) {
                        detectTapGestures { tapOffset ->
                            val normalized = normalizer.normalizeAll(lines, size.width.toFloat(), size.height.toFloat())
                            val clickedLine = normalizer.findLineAt(tapOffset.x, tapOffset.y, normalized)
                            if (clickedLine != null) {
                                onLineClicked(clickedLine)
                            }
                        }
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            if (pageBitmap != null && !pageBitmap.isRecycled) {
                val proj = normalizer.calculateProjection(canvasW, canvasH)
                val imageBitmap = pageBitmap.asImageBitmap()

                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(proj.offsetX.toInt(), proj.offsetY.toInt()),
                    dstSize = IntSize(proj.renderedWidth.toInt(), proj.renderedHeight.toInt())
                )

                // Render active line highlight if specified
                if (activeLine != null && activeLine in 1..15) {
                    val targetLine = lines.firstOrNull { it.lineNumber == activeLine }
                    if (targetLine != null) {
                        val norm = normalizer.normalize(targetLine, canvasW, canvasH, verticalAbsorptionPx = 4f)
                        drawRoundRect(
                            color = highlightColor,
                            topLeft = Offset(norm.left, norm.top),
                            size = Size(norm.width, norm.height),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }
                }
            }
        }
    }
}
