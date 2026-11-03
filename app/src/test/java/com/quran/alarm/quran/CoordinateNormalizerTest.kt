package com.quran.alarm.quran

import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.service.CoordinateNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoordinateNormalizerTest {

    private lateinit var normalizer: CoordinateNormalizer
    private lateinit var sampleLine: LineRect

    @Before
    fun setUp() {
        normalizer = CoordinateNormalizer(1260f, 1920f)
        sampleLine = LineRect(
            pageNumber = 3,
            lineNumber = 1,
            minX = 91,
            maxX = 1176,
            minY = 30,
            maxY = 145
        )
    }

    @Test
    fun testExactScalingIdentity() {
        val proj = normalizer.calculateProjection(1260f, 1920f)
        assertEquals(1.0f, proj.scale, 0.001f)
        assertEquals(0.0f, proj.offsetX, 0.001f)
        assertEquals(0.0f, proj.offsetY, 0.001f)

        val norm = normalizer.normalize(sampleLine, 1260f, 1920f, verticalAbsorptionPx = 0f)
        assertEquals(91f, norm.left, 0.01f)
        assertEquals(1176f, norm.right, 0.01f)
        assertEquals(30f, norm.top, 0.01f)
        assertEquals(145f, norm.bottom, 0.01f)
    }

    @Test
    fun testTallPhoneAspectRatioScaling() {
        // Modern 20:9 phone, e.g. 1080 x 2400
        val screenW = 1080f
        val screenH = 2400f
        val proj = normalizer.calculateProjection(screenW, screenH)

        // Width is constraining factor: 1080 / 1260 = ~0.8571
        val expectedScale = 1080f / 1260f
        assertEquals(expectedScale, proj.scale, 0.001f)
        assertEquals(0.0f, proj.offsetX, 0.001f)
        assertTrue("Vertical letterboxing offset must be positive", proj.offsetY > 0f)

        val norm = normalizer.normalize(sampleLine, screenW, screenH, verticalAbsorptionPx = 8f)
        assertTrue(norm.left >= 0f)
        assertTrue(norm.right <= screenW)
        assertTrue(norm.top >= proj.offsetY)
    }

    @Test
    fun testWideTabletAspectRatioScaling() {
        // Tablet 4:3, e.g. 1536 x 2048
        val screenW = 1536f
        val screenH = 2048f
        val proj = normalizer.calculateProjection(screenW, screenH)

        // Height is constraining: 2048 / 1920 = 1.0667, Width scale: 1536 / 1260 = 1.219
        val expectedScale = 2048f / 1920f
        assertEquals(expectedScale, proj.scale, 0.001f)
        assertTrue("Horizontal letterboxing offset must be positive on wide tablet", proj.offsetX > 0f)
    }

    @Test
    fun testAbsorptionMarginExpandsLineHitArea() {
        val withoutAbsorption = normalizer.normalize(sampleLine, 1260f, 1920f, verticalAbsorptionPx = 0f)
        val withAbsorption = normalizer.normalize(sampleLine, 1260f, 1920f, verticalAbsorptionPx = 10f)

        assertEquals(withoutAbsorption.top - 10f, withAbsorption.top, 0.001f)
        assertEquals(withoutAbsorption.bottom + 10f, withAbsorption.bottom, 0.001f)
        assertTrue(withAbsorption.height > withoutAbsorption.height)
    }

    @Test
    fun testFindLineAtDirectHitAndProximitySnap() {
        val line1 = sampleLine
        val line2 = LineRect(pageNumber = 3, lineNumber = 2, minX = 91, maxX = 1182, minY = 162, maxY = 272)
        val normalizedList = normalizer.normalizeAll(listOf(line1, line2), 1260f, 1920f, verticalAbsorptionPx = 4f)

        // 1. Direct hit on Line 1 center
        val hit1 = normalizer.findLineAt(line1.centerX, line1.centerY, normalizedList)
        assertEquals(1, hit1)

        // 2. Direct hit on Line 2 center
        val hit2 = normalizer.findLineAt(line2.centerX, line2.centerY, normalizedList)
        assertEquals(2, hit2)

        // 3. Point in small gap between Line 1 (maxY ~145) and Line 2 (minY ~162), e.g. y = 150
        val snap = normalizer.findLineAt(500f, 150f, normalizedList)
        assertNotNull("Should snap to closest adjacent line in gap", snap)

        // 4. Point far below bottom (e.g. y = 1500)
        val farMiss = normalizer.findLineAt(500f, 1500f, normalizedList)
        assertNull("Far point should not snap", farMiss)
    }

    @Test
    fun testZeroAndNegativeDimensionsSafety() {
        val projZero = normalizer.calculateProjection(0f, 0f)
        assertTrue(projZero.scale > 0f)

        val projNeg = normalizer.calculateProjection(-100f, -200f)
        assertTrue(projNeg.scale > 0f)

        val norm = normalizer.normalize(sampleLine, 0f, 0f)
        assertNotNull(norm)
    }
}

