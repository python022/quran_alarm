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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

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

    @Test
    fun testAntiBypassPreventsPrematureDismissalWhenJumpingToEndPage() = runTest {
        // Wird of 3 pages: 10, 11, 12
        viewModel.initialize(
            alarmId = 42,
            alarmLabel = "Wird 3 pages",
            rawStartPage = 10,
            rawPageCount = 3
        )
        advanceUntilIdle()

        // User jumps to page 11 then 12 via next page button without reading page 10
        viewModel.goToNextPage()
        advanceUntilIdle()
        viewModel.goToNextPage()
        advanceUntilIdle()

        assertEquals(12, viewModel.uiState.value.currentPage)
        assertEquals(12, viewModel.uiState.value.endPage)

        // User clicks line 15 on page 12
        viewModel.onLineClicked(15)
        advanceUntilIdle()

        // Anti-bypass gate: must NOT allow dismissal because pages 10 and 11 were not completed!
        assertFalse("Must not allow dismissal when skipping earlier pages", viewModel.uiState.value.isDismissEnabled)
        assertFalse("Wird must not be marked completed when prior pages are unread", viewModel.uiState.value.isWirdCompleted)
    }

    @Test
    fun testKhatmaWrapAroundNavigationAndCompletion() = runTest {
        // Khatma boundary: page 604 and page 1
        viewModel.initialize(
            alarmId = 99,
            alarmLabel = "Khatma",
            rawStartPage = 604,
            rawPageCount = 2
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(604, 1), state.wirdPages)
        assertEquals(604, state.currentPage)
        assertEquals(1, state.endPage)
        assertFalse("Cannot go back before first wird page", state.hasPrevPageInWird)
        assertTrue("Can go forward to next wird page (page 1)", state.hasNextPageInWird)

        // Read page 604 lines 1..15
        for (l in 1..15) {
            viewModel.advanceToNextLineOrPage()
            advanceUntilIdle()
        }

        // Now on page 1 (starts at first readable line: line 2)
        assertEquals(1, viewModel.uiState.value.currentPage)
        assertEquals(2, viewModel.uiState.value.activeLine)
        assertTrue("Can navigate back to page 604 in wird", viewModel.uiState.value.hasPrevPageInWird)
        assertFalse("Cannot navigate past last page in wird", viewModel.uiState.value.hasNextPageInWird)
        assertFalse("Not completed yet", viewModel.uiState.value.isDismissEnabled)

        // Read through remaining readable lines of page 1 (lines 3..8)
        for (l in 3..8) {
            viewModel.advanceToNextLineOrPage()
            advanceUntilIdle()
        }

        assertEquals(8, viewModel.uiState.value.activeLine)
        assertTrue("Must be completed after finishing final page of Khatma wrap-around", viewModel.uiState.value.isWirdCompleted)
        assertTrue("Dismiss button must be enabled", viewModel.uiState.value.isDismissEnabled)
    }

    @Test
    fun testPageEndingInSurahHeaderCompletesOnLastReadableLine() = runTest {
        // Page 76 ends with SURAH_HEADER at line 15; line 14 is the last readable line
        viewModel.initialize(
            alarmId = 76,
            alarmLabel = "Page 76",
            rawStartPage = 76,
            rawPageCount = 1
        )
        advanceUntilIdle()

        assertEquals(14, viewModel.uiState.value.lastReadableLineOnPage)

        // Click line 14
        viewModel.onLineClicked(14)
        advanceUntilIdle()

        assertTrue("Must complete when reaching last readable line (line 14)", viewModel.uiState.value.isWirdCompleted)
        assertTrue("Dismiss must be enabled", viewModel.uiState.value.isDismissEnabled)
    }

    @Test
    fun testInitialStageIsRingingStage() = runTest {
        viewModel.initialize(
            alarmId = 1,
            alarmLabel = "صلاة الفجر والورد القرآني",
            rawStartPage = 10,
            rawPageCount = 3
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Initial stage must be RINGING before reading enters", AlarmAlertStage.RINGING, state.stage)
        assertFalse("Dismiss must never be enabled on ringing screen", state.isDismissEnabled)
        assertFalse("Wird is not completed on ringing screen", state.isWirdCompleted)
    }

    @Test
    fun testStartReadingWirdTransitionsStageFromRingingToMushafReading() = runTest {
        viewModel.initialize(
            alarmId = 2,
            alarmLabel = "صلاة الفجر والورد القرآني",
            rawStartPage = 10,
            rawPageCount = 3
        )
        advanceUntilIdle()

        assertEquals(AlarmAlertStage.RINGING, viewModel.uiState.value.stage)

        // User taps "بدء قراءة الورد"
        viewModel.startReadingWird()
        advanceUntilIdle()

        assertEquals("Must transition to MUSHAF_READING stage", AlarmAlertStage.MUSHAF_READING, viewModel.uiState.value.stage)
        assertFalse("Dismiss remains locked at start of recitation", viewModel.uiState.value.isDismissEnabled)
    }

    @Test
    fun testSnoozePreservesConfiguredSoundTitleVolumeAndVibrationFromRepository() = runTest {
        // Save alarm with customized sound and volume in repository
        val configuredAlarm = AlarmItem(
            id = 77,
            hour = 5,
            minute = 15,
            label = "صلاة الفجر المخصصة",
            startPage = 10,
            pageCount = 3,
            volumeLevel = 75,
            soundUri = "builtin://adhan_fajr",
            soundTitle = "أذان الفجر",
            isVibrationEnabled = true,
            isSequential = true
        )
        fakeAlarmRepo.saveAlarm(configuredAlarm)

        viewModel.initialize(
            alarmId = 77,
            alarmLabel = "صلاة الفجر المخصصة",
            rawStartPage = 10,
            rawPageCount = 3,
            isSnoozeEnabled = true
        )
        advanceUntilIdle()

        var snoozeInvoked = false
        viewModel.snooze(minutes = 5) {
            snoozeInvoked = true
        }
        advanceUntilIdle()

        assertTrue("Snooze callback must be invoked", snoozeInvoked)
        assertTrue("State must mark isDismissedOrSnoozed as true", viewModel.uiState.value.isDismissedOrSnoozed)

        val scheduledItem = fakeAlarmScheduler.scheduled.lastOrNull()
        assertNotNull("Snooze item must be scheduled in scheduler", scheduledItem)
        assertEquals("Snooze alarm must preserve custom soundUri", "builtin://adhan_fajr", scheduledItem!!.soundUri)
        assertEquals("Snooze alarm must preserve soundTitle", "أذان الفجر", scheduledItem.soundTitle)
        assertEquals("Snooze alarm must preserve volumeLevel", 75, scheduledItem.volumeLevel)
        assertTrue("Snooze alarm must preserve isVibrationEnabled", scheduledItem.isVibrationEnabled)
        assertTrue("Snooze alarm must be a one-shot trigger", scheduledItem.repeatDays.isEmpty())
        assertTrue("Snooze alarm must be enabled", scheduledItem.isEnabled)
    }

    @Test
    fun testInitializePropagatesIsSnoozeEnabled() = runTest {
        viewModel.initialize(
            alarmId = 88,
            alarmLabel = "Wird Without Snooze",
            rawStartPage = 20,
            rawPageCount = 2,
            isSnoozeEnabled = false
        )
        advanceUntilIdle()

        assertFalse("isSnoozeEnabled must be false when configured as false", viewModel.uiState.value.isSnoozeEnabled)
    }

    @Test
    fun testSoundPresetsAndCustomTitles() {
        val defaultTitle = com.quran.alarm.alarm.domain.model.AlarmSoundPresets.getTitleForUri(null)
        assertEquals("نغمة المنبه الافتراضية", defaultTitle)

        val adhanTitle = com.quran.alarm.alarm.domain.model.AlarmSoundPresets.getTitleForUri(
            com.quran.alarm.alarm.domain.model.AlarmSoundPresets.URI_ADHAN_FAJR
        )
        assertEquals("أذان الفجر", adhanTitle)

        val takbeerTitle = com.quran.alarm.alarm.domain.model.AlarmSoundPresets.getTitleForUri(
            com.quran.alarm.alarm.domain.model.AlarmSoundPresets.URI_EID_TAKBEER
        )
        assertEquals("تكبيرات العيد", takbeerTitle)

        val calmTitle = com.quran.alarm.alarm.domain.model.AlarmSoundPresets.getTitleForUri(
            com.quran.alarm.alarm.domain.model.AlarmSoundPresets.URI_CALM_TONE
        )
        assertEquals("نغمة هادئة", calmTitle)

        val alertTitle = com.quran.alarm.alarm.domain.model.AlarmSoundPresets.getTitleForUri(
            com.quran.alarm.alarm.domain.model.AlarmSoundPresets.URI_QURAN_ALERT
        )
        assertEquals("تنبيه قرآني", alertTitle)

        val customTitle = com.quran.alarm.alarm.domain.model.AlarmSoundPresets.getTitleForUri(
            "content://media/external/audio/media/123",
            defaultTitle = "ملف مخصص"
        )
        assertEquals("ملف مخصص", customTitle)
    }

    @Test
    fun testGoToNextPage_recordsCurrentPageIntoCompletedPages_andAllowsDismissAtEnd() = runTest {
        // 3 pages: 10, 11, 12
        viewModel.initialize(
            alarmId = 42,
            alarmLabel = "Multi-page wird",
            rawStartPage = 10,
            rawPageCount = 3
        )
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.currentPage)
        // Click line 15 (last line on page 10)
        viewModel.onLineClicked(15)
        advanceUntilIdle()
        assertTrue("Page 10 must be recorded in completedPages", 10 in viewModel.uiState.value.completedPages)
        assertFalse("Dismiss must not be enabled until endPage", viewModel.uiState.value.isDismissEnabled)

        // User navigates with goToNextPage()
        viewModel.goToNextPage()
        advanceUntilIdle()
        assertEquals(11, viewModel.uiState.value.currentPage)
        assertTrue(10 in viewModel.uiState.value.completedPages)

        // Read page 11 to last line and navigate
        viewModel.onLineClicked(15)
        advanceUntilIdle()
        assertTrue(11 in viewModel.uiState.value.completedPages)

        viewModel.goToNextPage()
        advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.currentPage)

        // Read to last line of final page (page 12)
        viewModel.onLineClicked(15)
        advanceUntilIdle()

        assertTrue("Wird must be marked complete", viewModel.uiState.value.isWirdCompleted)
        assertTrue("Dismiss must be enabled when reaching end of wird", viewModel.uiState.value.isDismissEnabled)
    }

    @Test
    fun testSnooze_persistsSnoozeAlarmIntoRepository() = runTest {
        val initialAlarm = AlarmItem(
            id = 55,
            hour = 5,
            minute = 0,
            repeatDays = setOf(Calendar.MONDAY, Calendar.FRIDAY),
            isEnabled = true,
            label = "صلاة الفجر"
        )
        fakeAlarmRepo.saveAlarm(initialAlarm)

        viewModel.initialize(
            alarmId = 55,
            alarmLabel = "صلاة الفجر",
            rawStartPage = 2,
            rawPageCount = 4
        )
        advanceUntilIdle()

        var snoozeInvoked = false
        viewModel.snooze(minutes = 5) {
            snoozeInvoked = true
        }
        advanceUntilIdle()

        assertTrue(snoozeInvoked)
        val savedInRepo = fakeAlarmRepo.getAlarmById(55)
        assertNotNull("Snooze alarm must be saved in repository to survive reboot", savedInRepo)
        assertTrue("Snooze alarm must be enabled", savedInRepo!!.isEnabled)
        assertNotNull("Snooze alarm must have explicitTriggerMillis", savedInRepo.explicitTriggerMillis)
        assertEquals("Snooze must preserve repeatDays so repeating alarm is not lost", setOf(Calendar.MONDAY, Calendar.FRIDAY), savedInRepo.repeatDays)
        assertEquals("Snooze must preserve base alarm hour", 5, savedInRepo.hour)
        assertEquals("Snooze must preserve base alarm minute", 0, savedInRepo.minute)
        assertTrue("Snooze alarm must be scheduled in scheduler", fakeAlarmScheduler.scheduled.any { it.id == 55 })
    }

    // Fake implementations for testing
    private class FakeLineBoundsRepository : LineBoundsRepository {
        override suspend fun getLinesForPage(pageNumber: Int): List<LineRect> {
            val count = getPageLineCount(pageNumber)
            return (1..count).map { l ->
                val type = when {
                    pageNumber == 106 && l == 6 -> LineType.SURAH_HEADER
                    pageNumber == 106 && l == 7 -> LineType.BASMALAH
                    pageNumber == 76 && l == 15 -> LineType.SURAH_HEADER
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

