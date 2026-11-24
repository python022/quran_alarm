package com.quran.alarm.alarm.ui.viewmodel

import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAlarmRepository
    private lateinit var fakeScheduler: FakeAlarmScheduler
    private lateinit var viewModel: AlarmViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAlarmRepository()
        fakeScheduler = FakeAlarmScheduler()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadAlarms_updatesUiState_withAlarmsAndForecast() = runTest(testDispatcher) {
        val alarm1 = AlarmItem(id = 1, hour = 5, minute = 0, startPage = 2, pageCount = 5)
        val alarm2 = AlarmItem(id = 2, hour = 6, minute = 30, startPage = 50, pageCount = 10)
        fakeRepository.saveAlarm(alarm1)
        fakeRepository.saveAlarm(alarm2)

        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.alarms.size)
        assertFalse(state.isLoading)
        assertNotNull(state.khatmaProgress)
        assertTrue(state.nextAlarmCountdown.isNotEmpty())
    }

    @Test
    fun saveAlarm_persistsAndSchedules_whenEnabled() = runTest(testDispatcher) {
        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        val newAlarm = AlarmItem(id = 42, hour = 5, minute = 15, isEnabled = true, startPage = 77, pageCount = 4)
        viewModel.saveAlarm(newAlarm)
        advanceUntilIdle()

        assertEquals(1, fakeRepository.getAllAlarms().size)
        assertEquals(42, fakeRepository.getAllAlarms().first().id)
        assertTrue(fakeScheduler.scheduledAlarms.contains(42))
        assertFalse(viewModel.uiState.value.isAddEditDialogOpen)
    }

    @Test
    fun saveAlarm_cancels_whenDisabled() = runTest(testDispatcher) {
        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        val disabledAlarm = AlarmItem(id = 99, hour = 7, minute = 0, isEnabled = false)
        viewModel.saveAlarm(disabledAlarm)
        advanceUntilIdle()

        assertTrue(fakeScheduler.cancelledAlarms.contains(99))
    }

    @Test
    fun toggleAlarm_updatesEnabledStateAndScheduler() = runTest(testDispatcher) {
        val alarm = AlarmItem(id = 10, hour = 5, minute = 0, isEnabled = true)
        fakeRepository.saveAlarm(alarm)

        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        // Toggle OFF
        viewModel.toggleAlarm(alarm, isEnabled = false)
        advanceUntilIdle()

        assertFalse(fakeRepository.getAlarmById(10)!!.isEnabled)
        assertTrue(fakeScheduler.cancelledAlarms.contains(10))

        // Toggle ON
        viewModel.toggleAlarm(alarm, isEnabled = true)
        advanceUntilIdle()

        assertTrue(fakeRepository.getAlarmById(10)!!.isEnabled)
        assertTrue(fakeScheduler.scheduledAlarms.contains(10))
    }

    @Test
    fun toggleAlarm_clearsStaleSnoozeTrigger_andEmitsUserMessage() = runTest(testDispatcher) {
        val snoozed = AlarmItem(
            id = 25,
            hour = 6,
            minute = 0,
            isEnabled = true,
            snoozeTriggerMillis = System.currentTimeMillis() + 300_000L
        )
        fakeRepository.saveAlarm(snoozed)

        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        viewModel.toggleAlarm(snoozed, isEnabled = false)
        advanceUntilIdle()

        val afterDisable = fakeRepository.getAlarmById(25)!!
        assertNull("Toggling OFF must clear snoozeTriggerMillis", afterDisable.snoozeTriggerMillis)
        assertEquals("تم تعطيل المنبه", viewModel.uiState.value.userMessage)

        viewModel.toggleAlarm(afterDisable, isEnabled = true)
        advanceUntilIdle()

        val afterEnable = fakeRepository.getAlarmById(25)!!
        assertNull("Toggling ON must clear stale snoozeTriggerMillis", afterEnable.snoozeTriggerMillis)
        assertNotNull("One-shot enable must compute explicitTriggerMillis", afterEnable.explicitTriggerMillis)
        assertEquals("تم تفعيل المنبه", viewModel.uiState.value.userMessage)
    }

    @Test
    fun saveAlarm_whenRepositoryFails_setsErrorUserMessage() = runTest(testDispatcher) {
        val failingRepo = object : AlarmRepository {
            override fun getAllAlarms(): List<AlarmItem> = emptyList()
            override fun getActiveAlarms(): List<AlarmItem> = emptyList()
            override fun getAlarmById(id: Int): AlarmItem? = null
            override fun saveAlarm(alarm: AlarmItem) { error("Disk write failed") }
            override fun deleteAlarm(id: Int) {}
        }

        viewModel = AlarmViewModel(failingRepo, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        viewModel.saveAlarm(AlarmItem(id = 99, hour = 5, minute = 0))
        advanceUntilIdle()

        assertEquals("تعذر حفظ المنبه", viewModel.uiState.value.userMessage)
    }

    @Test
    fun deleteAlarm_removesFromRepositoryAndCancels() = runTest(testDispatcher) {
        val alarm = AlarmItem(id = 15, hour = 5, minute = 0)
        fakeRepository.saveAlarm(alarm)

        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        viewModel.deleteAlarm(15)
        advanceUntilIdle()

        assertNull(fakeRepository.getAlarmById(15))
        assertTrue(fakeScheduler.cancelledAlarms.contains(15))
        assertEquals(0, viewModel.uiState.value.alarms.size)
    }

    @Test
    fun dialogState_transitionsCorrectly() = runTest(testDispatcher) {
        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAddEditDialogOpen)
        assertNull(viewModel.uiState.value.editingAlarm)

        val alarm = AlarmItem(id = 7, hour = 5, minute = 0)
        viewModel.openEditDialog(alarm)
        assertTrue(viewModel.uiState.value.isAddEditDialogOpen)
        assertEquals(alarm, viewModel.uiState.value.editingAlarm)

        viewModel.dismissDialog()
        assertFalse(viewModel.uiState.value.isAddEditDialogOpen)
        assertNull(viewModel.uiState.value.editingAlarm)

        viewModel.openAddDialog()
        assertTrue(viewModel.uiState.value.isAddEditDialogOpen)
        assertNull(viewModel.uiState.value.editingAlarm)
    }

    @Test
    fun khatmaProgress_excludesFixedAndAyahModeAlarms_andUsesSequentialFullMushafAlarm() = runTest(testDispatcher) {
        val fixedMulk = AlarmItem(
            id = 1,
            hour = 22,
            minute = 0,
            startPage = 562,
            pageCount = 3,
            isSequential = false
        )
        val ayahBaqarah = AlarmItem(
            id = 2,
            hour = 6,
            minute = 0,
            startPage = 2,
            pageCount = 1,
            isSequential = true,
            wirdSurahNumber = 2,
            startAyah = 1,
            dailyAyahCount = 5
        )
        fakeRepository.saveAlarm(fixedMulk)
        fakeRepository.saveAlarm(ayahBaqarah)

        viewModel = AlarmViewModel(fakeRepository, fakeScheduler, testDispatcher)
        advanceUntilIdle()
        assertNull(
            "Fixed and Ayah-mode alarms must not produce misleading full-Mushaf Khatma progress",
            viewModel.uiState.value.khatmaProgress
        )

        val sequentialWird = AlarmItem(
            id = 3,
            hour = 5,
            minute = 0,
            startPage = 100,
            pageCount = 5,
            isSequential = true
        )
        viewModel.saveAlarm(sequentialWird)
        advanceUntilIdle()

        val progress = viewModel.uiState.value.khatmaProgress
        assertNotNull("Sequential full-Mushaf alarm must produce Khatma progress", progress)
        assertEquals(100, progress!!.currentStartPage)
    }

    // Test Fakes
    private class FakeAlarmRepository : AlarmRepository {
        private val storage = mutableMapOf<Int, AlarmItem>()

        override fun getAllAlarms(): List<AlarmItem> = storage.values.toList()
        override fun getActiveAlarms(): List<AlarmItem> = storage.values.filter { it.isEnabled }
        override fun getAlarmById(id: Int): AlarmItem? = storage[id]
        override fun saveAlarm(alarm: AlarmItem) { storage[alarm.id] = alarm }
        override fun deleteAlarm(id: Int) { storage.remove(id) }
    }

    private class FakeAlarmScheduler : AlarmScheduler {
        val scheduledAlarms = mutableSetOf<Int>()
        val cancelledAlarms = mutableSetOf<Int>()

        override fun schedule(item: AlarmItem) {
            scheduledAlarms.add(item.id)
            cancelledAlarms.remove(item.id)
        }

        override fun cancel(item: AlarmItem) {
            cancel(item.id)
        }

        override fun cancel(alarmId: Int) {
            cancelledAlarms.add(alarmId)
            scheduledAlarms.remove(alarmId)
        }
    }
}

