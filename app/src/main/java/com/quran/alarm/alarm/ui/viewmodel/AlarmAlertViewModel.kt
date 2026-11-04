package com.quran.alarm.alarm.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.quran.data.AyahInfoDatabase
import com.quran.alarm.quran.data.AyahInfoLineBoundsRepositoryImpl
import com.quran.alarm.quran.data.QuranPageManager
import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.repository.LineBoundsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * State representation for the sacred Quran Wird reading session during alarm alert.
 */
data class MushafReaderUiState(
    val alarmId: Int = 0,
    val alarmLabel: String = "ورد القرآن الكريم",
    val startPage: Int = 1,
    val endPage: Int = 1,
    val pageCount: Int = 1,
    val currentPage: Int = 1,
    val activeLine: Int = 1,
    val pageMaxLines: Int = 15,
    val currentBitmap: Bitmap? = null,
    val currentLines: List<LineRect> = emptyList(),
    val isLoading: Boolean = true,
    val isWirdCompleted: Boolean = false,
    val isDismissedOrSnoozed: Boolean = false
) {
    /**
     * Law 6 in AGENTS.md (Single Sacred Mission Law):
     * Dismissing the alarm is strictly forbidden until the entire wird is read.
     */
    val isDismissEnabled: Boolean
        get() = isWirdCompleted || (currentPage == endPage && activeLine >= pageMaxLines)
}

/**
 * Clean Architecture ViewModel for managing the Wird reading session and alarm lifecycle.
 * Preserves reading progress across configuration changes and enforces non-blocking IO.
 */
class AlarmAlertViewModel(
    private val lineBoundsRepository: LineBoundsRepository,
    private val pageManager: QuranPageManager?,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(MushafReaderUiState())
    val uiState: StateFlow<MushafReaderUiState> = _uiState.asStateFlow()

    fun initialize(
        alarmId: Int,
        alarmLabel: String,
        rawStartPage: Int,
        rawPageCount: Int
    ) {
        val safeStartPage = rawStartPage.coerceIn(1, 604)
        val safePageCount = rawPageCount.coerceAtLeast(1)
        val safeEndPage = Math.floorMod(safeStartPage + safePageCount - 2, 604) + 1

        val initialMaxLines = lineBoundsRepository.getPageLineCount(safeStartPage)

        _uiState.update {
            it.copy(
                alarmId = alarmId,
                alarmLabel = alarmLabel,
                startPage = safeStartPage,
                endPage = safeEndPage,
                pageCount = safePageCount,
                currentPage = safeStartPage,
                pageMaxLines = initialMaxLines
            )
        }

        loadPage(safeStartPage)
    }

    fun loadPage(pageNumber: Int, resetToFirstReadable: Boolean = true) {
        val safePage = pageNumber.coerceIn(1, 604)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentPage = safePage) }

            val lines = withContext(ioDispatcher) {
                lineBoundsRepository.getLinesForPage(safePage)
            }
            val bitmap = withContext(ioDispatcher) {
                pageManager?.loadPageBitmap(safePage)
            }
            withContext(ioDispatcher) {
                pageManager?.preloadPages(safePage, radius = 1)
            }

            val maxLines = lineBoundsRepository.getPageLineCount(safePage)
            val firstReadable = lines.firstOrNull { it.isReadable }?.lineNumber ?: 1

            _uiState.update { current ->
                val lineToSet = if (!resetToFirstReadable && current.activeLine in 1..maxLines && lines.any { it.lineNumber == current.activeLine && it.isReadable }) {
                    current.activeLine
                } else {
                    firstReadable
                }
                current.copy(
                    isLoading = false,
                    currentPage = safePage,
                    currentBitmap = bitmap,
                    currentLines = lines,
                    pageMaxLines = maxLines,
                    activeLine = lineToSet
                )
            }
        }
    }

    fun onLineClicked(lineNumber: Int) {
        val state = _uiState.value
        val target = state.currentLines.firstOrNull { it.lineNumber == lineNumber }
        if (target != null && target.isReadable) {
            _uiState.update { it.copy(activeLine = lineNumber) }
            checkCompletion()
        }
    }

    fun advanceToNextLineOrPage() {
        val state = _uiState.value
        val lines = state.currentLines

        // Find next readable line on current page
        val nextReadable = lines
            .filter { it.lineNumber > state.activeLine && it.isReadable }
            .minByOrNull { it.lineNumber }

        if (nextReadable != null) {
            _uiState.update { it.copy(activeLine = nextReadable.lineNumber) }
            checkCompletion()
        } else if (state.currentPage != state.endPage) {
            // Advance to next page in wird
            val nextPage = (state.currentPage % 604) + 1
            loadPage(nextPage)
        } else {
            // Reached last readable line of final wird page
            _uiState.update { it.copy(isWirdCompleted = true) }
        }
    }

    fun goToPreviousPage() {
        val state = _uiState.value
        if (state.currentPage > 1) {
            loadPage(state.currentPage - 1)
        }
    }

    fun goToNextPage() {
        val state = _uiState.value
        if (state.currentPage < 604) {
            loadPage(state.currentPage + 1)
        }
    }

    private fun checkCompletion() {
        val state = _uiState.value
        if (state.currentPage == state.endPage && state.activeLine >= state.pageMaxLines) {
            _uiState.update { it.copy(isWirdCompleted = true) }
        }
    }

    fun completeWird(onComplete: () -> Unit) {
        val state = _uiState.value
        if (!state.isDismissEnabled) return

        viewModelScope.launch {
            withContext(ioDispatcher) {
                val currentAlarm = alarmRepository.getAlarmById(state.alarmId)
                if (currentAlarm != null) {
                    val willRepeat = currentAlarm.repeatDays.isNotEmpty()
                    val advancedAlarm = currentAlarm.advanceToNextWird().copy(isEnabled = willRepeat)
                    alarmRepository.saveAlarm(advancedAlarm)
                    if (willRepeat) {
                        alarmScheduler.schedule(advancedAlarm)
                    }
                }
            }
            _uiState.update { it.copy(isDismissedOrSnoozed = true) }
            onComplete()
        }
    }

    fun snooze(minutes: Int = 5, onSnooze: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val snoozeTime = System.currentTimeMillis() + (minutes * 60_000L)
                val cal = Calendar.getInstance().apply { timeInMillis = snoozeTime }

                val snoozeItem = AlarmItem(
                    id = state.alarmId,
                    hour = cal.get(Calendar.HOUR_OF_DAY),
                    minute = cal.get(Calendar.MINUTE),
                    repeatDays = emptySet(),
                    isEnabled = true,
                    isVibrationEnabled = true,
                    label = state.alarmLabel,
                    targetPage = state.startPage,
                    startPage = state.startPage,
                    pageCount = state.pageCount,
                    explicitTriggerMillis = snoozeTime
                )
                alarmScheduler.schedule(snoozeItem)
            }
            _uiState.update { it.copy(isDismissedOrSnoozed = true) }
            onSnooze()
        }
    }
}

class AlarmAlertViewModelFactory(
    private val context: Context,
    private val pageManager: QuranPageManager? = null,
    private val lineBoundsRepository: LineBoundsRepository? = null,
    private val alarmRepository: AlarmRepository? = null,
    private val alarmScheduler: AlarmScheduler? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val pm = pageManager ?: QuranPageManager(appContext)
        val lb = lineBoundsRepository ?: AyahInfoLineBoundsRepositoryImpl(AyahInfoDatabase.getInstance(appContext))
        val ar = alarmRepository ?: AlarmRepositoryImpl(appContext)
        val asch = alarmScheduler ?: AlarmSchedulerImpl(appContext)
        return AlarmAlertViewModel(lb, pm, ar, asch) as T
    }
}

