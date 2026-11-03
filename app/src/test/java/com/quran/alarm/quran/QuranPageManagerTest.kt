package com.quran.alarm.quran

import android.content.Context
import com.quran.alarm.quran.data.QuranPageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranPageManagerTest {

    private lateinit var context: Context
    private lateinit var pageManager: QuranPageManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        pageManager = QuranPageManager(
            context = context,
            maxCacheMemoryBytes = 32 * 1024 * 1024 // 32 MB cap
        )
    }

    @After
    fun tearDown() {
        pageManager.clearCache()
    }

    @Test
    fun testLoadPageReturnsValidBitmapDimensions() = runBlocking {
        val bitmap = pageManager.loadPageBitmap(3)
        assertNotNull("Page 3 bitmap must load from assets", bitmap)
        assertTrue("Page width must be positive", bitmap!!.width > 0)
        assertTrue("Page height must be positive", bitmap.height > 0)
    }

    @Test
    fun testConsecutive50PagesFlippingMemoryBoundedUnder120MB() = runBlocking {
        System.gc()
        val runtime = Runtime.getRuntime()

        // Flip through 50 consecutive pages
        for (page in 1..50) {
            val bitmap = pageManager.loadPageBitmap(page)
            assertNotNull("Page $page must load successfully", bitmap)
        }

        // Cache size must not exceed the configured 32 MB cap
        val cacheSizeBytes = pageManager.getCacheSizeBytes()
        assertTrue(
            "Cache memory (${cacheSizeBytes / (1024 * 1024)} MB) must be <= 32 MB",
            cacheSizeBytes <= 32 * 1024 * 1024
        )

        val cachedPages = pageManager.getCachedPagesCount()
        assertTrue("Cache must evict older pages and hold <= 8 pages (was $cachedPages)", cachedPages in 1..8)

        // Runtime memory check
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        assertTrue(
            "Total used memory ($usedMemoryMb MB) must remain well under 120 MB success gate",
            usedMemoryMb < 120
        )
    }

    @Test
    fun testPreloadPagesPopulatesCache() = runBlocking {
        pageManager.clearCache()
        assertEquals(0, pageManager.getCachedPagesCount())

        pageManager.preloadPages(centerPage = 10, radius = 2)
        // Should preload 8, 9, 11, 12
        assertTrue("Cache should contain preloaded pages", pageManager.getCachedPagesCount() >= 4)
    }

    @Test
    fun testClearCacheEvictsAllBitmaps() = runBlocking {
        pageManager.loadPageBitmap(1)
        assertTrue(pageManager.getCachedPagesCount() > 0)

        pageManager.clearCache()
        assertEquals(0, pageManager.getCachedPagesCount())
        assertEquals(0, pageManager.getCacheSizeBytes())
    }
}
