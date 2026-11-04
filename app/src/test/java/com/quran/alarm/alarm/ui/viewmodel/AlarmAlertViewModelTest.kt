package com.quran.alarm.alarm.ui.viewmodel

import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.model.LineType
import com.quran.alarm.quran.domain.repository.LineBoundsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmAlertViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeLineRepo: FakeLineBoundsRepository
    private lateinit var fakeAlarmRepo: FakeAlarmRepository
    private lateinit var fakeAlarmScheduler: FakeAlarmScheduler
    private lateinit var viewModel: AlarmAlertViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLineRepo = FakeLineBoundsRepository()
        fakeAlarmRepo = FakeAlarmRepository()
        fakeAlarmScheduler = FakeAlarmScheduler()

        viewModel = AlarmAlertViewModel(
            lineBoundsRepository = fakeLineRepo,
            pageManager = null, // No bitmap decoding needed for logic tests
            alarmRepository = fakeAlarmRepo,
            alarmScheduler = fakeAlarmScheduler,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitializationCoercesNegativeAndInvalidInputsSafely() = runTest {
        // Negative / zero inputs should not crash or produce negative modulo
        viewModel.initialize(
            alarmId = 1,
            alarmLabel = "Test",
            rawStartPage = -5,
            rawPageCount = 0
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Start page should coerce to 1", 1, state.startPage)
        assertEquals("Page count should coerce to at least 1", 1, state.pageCount)
        assertEquals("End page should be 1", 1, state.endPage)
        assertFalse("Dismiss must be disabled on first line", state.isDismissEnabled)
    }

    @Test
    fun testDismissIsStrictlyForbiddenUntilAllWirdPagesAndLinesCompleted() = runTest {
        // 2 pages: startPage = 1 (8 lines), endPage = 2 (8 lines)
        viewModel.initialize(
            alarmId = 10,
            alarmLabel = "Fajr",
            rawStartPage = 1,
            rawPageCount = 2
        )
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.currentPage)
        assertEquals(2, state.endPage)
        assertEquals(2, state.activeLine) // Line 1 is Surah header, so starts at line 2
        assertFalse("Must not allow dismissal at start of page 1", state.isDismissEnabled)

        // Read through remaining readable lines of page 1 (lines 3..8)
        for (expectedLine in 3..8) {
            viewModel.advanceToNextLineOrPage()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.currentPage)
            assertEquals(expectedLine, viewModel.uiState.value.activeLine)
            assertFalse("Must not allow dismissal while still on page 1", viewModel.uiState.value.isDismissEnabled)
        }

        // Advance from line 8 of page 1 -> moves to page 2 (starts at line 2)
        viewModel.advanceToNextLineOrPage()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals("Should be on page 2", 2, state.currentPage)
        assertEquals("Page 2 starts at first readable line (line 2)", 2, state.activeLine)
        assertFalse("Must not allow dismissal at start of page 2", state.isDismissEnabled)

        // Read lines 3..7 of page 2
        for (expectedLine in 3..7) {
            viewModel.advanceToNextLineOrPage()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.currentPage)
            assertEquals(expectedLine, viewModel.uiState.value.activeLine)
            assertFalse("Must not allow dismissal before last line of endPage", viewModel.uiState.value.isDismissEnabled)
        }

        // Advance to last line (line 8) of page 2
        viewModel.advanceToNextLineOrPage()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(8, state.activeLine)
        assertTrue("Dismiss MUST be enabled after completing the entire wird portion", state.isDismissEnabled)

        // Complete wird
        var completed = false
        viewModel.completeWird { completed = true }
        advanceUntilIdle()
        assertTrue("onComplete callback invoked", completed)
        assertTrue(viewModel.uiState.value.isDismissedOrSnoozed)
    }

    @Test
    fun testAdvanceSkipsSurahHeaderAutomatically() = runTest {
        // On page 106: line 5 is AYAH_TEXT, line 6 is SURAH_HEADER, line 7 is BASMALAH
        viewModel.initialize(
            alarmId = 5,
            alarmLabel = "Wird",
            rawStartPage = 106,
            rawPageCount = 1
        )
        advanceUntilIdle()

        viewModel.onLineClicked(5)
        assertEquals(5, viewModel.uiState.value.activeLine)

        // Advancing from line 5 must skip line 6 (SURAH_HEADER) directly to line 7 (BASMALAH)
        viewModel.advanceToNextLineOrPage()
        advanceUntilIdle()
        assertEquals("Must skip decorative surah header to line 7", 7, viewModel.uiState.value.activeLine)
    }

    @Test
    fun testClickingSurahHeaderDoesNotActivateIt() = runTest {
        viewModel.initialize(
            alarmId = 5,
            alarmLabel = "Wird",
            rawStartPage = 106,
            rawPageCount = 1
        )
        advanceUntilIdle()

        val initialActive = viewModel.uiState.value.activeLine
        viewModel.onLineClicked(6) // Line 6 is SURAH_HEADER
        advanceUntilIdle()

        assertEquals("Clicking surah header must be ignored", initialActive, viewModel.uiState.value.activeLine)
    }

    // Fake implementations for testing
    private class FakeLineBoundsRepository : LineBoundsRepository {
        override suspend fun getLinesForPage(pageNumber: Int): List<LineRect> {
            val count = getPageLineCount(pageNumber)
            return (1..count).map { l ->
                val type = when {
                    pageNumber == 106 && l == 6 -> LineType.SURAH_HEADER
                    pageNumber == 106 && l == 7 -> LineType.BASMALAH
                    pageNumber == 1 && l == 1 -> LineType.SURAH_HEADER
                    pageNumber == 2 && l == 1 -> LineType.SURAH_HEADER
                    pageNumber == 2 && l == 2 -> LineType.BASMALAH
                    else -> LineType.AYAH_TEXT
                }
                LineRect(pageNumber, l, 100, 1100, (l - 1) * 100, l * 100 - 10, type)
            }
        }

        override fun getLinesForPageSync(pageNumber: Int): List<LineRect> = kotlinx.coroutines.runBlocking {
            getLinesForPage(pageNumber)
        }

        override fun getPageLineCount(pageNumber: Int): Int {
            return if (pageNumber in 1..2) 8 else 15
        }
    }

    private class FakeAlarmRepository : AlarmRepository {
        val alarms = mutableMapOf<Int, AlarmItem>()

        override fun getAllAlarms(): List<AlarmItem> = alarms.values.toList()
        override fun getActiveAlarms(): List<AlarmItem> = alarms.values.filter { it.isEnabled }
        override fun getAlarmById(id: Int): AlarmItem? = alarms[id]
        override fun saveAlarm(alarm: AlarmItem) { alarms[alarm.id] = alarm }
        override fun deleteAlarm(id: Int) { alarms.remove(id) }
    }

    private class FakeAlarmScheduler : AlarmScheduler {
        val scheduled = mutableListOf<AlarmItem>()
        val cancelled = mutableListOf<Int>()

        override fun schedule(item: AlarmItem) { scheduled.add(item) }
        override fun cancel(item: AlarmItem) { cancel(item.id) }
        override fun cancel(alarmId: Int) {
            cancelled.add(alarmId)
            scheduled.removeIf { it.id == alarmId }
        }
    }
}

