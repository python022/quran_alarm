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
 * Stages of the Alarm Alert lifecycle.
 *
 * 1. [RINGING]: Dedicated entry ringing screen. Plays ringtone, vibrates, shows time, label,
 *    and wird overview. User enters reading by pressing "بدء قراءة الورد" or snoozes.
 * 2. [MUSHAF_READING]: Recitation stage. Alarm sound and vibration are silenced so user
 *    can recite peacefully. Wake lock / fill light #FFFDF5 kept on. Dismiss button strictly locked
 *    until all wird pages are completed.
 */
enum class AlarmAlertStage {
    RINGING,
    MUSHAF_READING
}

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
    val wirdPages: List<Int> = emptyList(),
    val completedPages: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val isWirdCompleted: Boolean = false,
    val isDismissedOrSnoozed: Boolean = false,
    val isSnoozeEnabled: Boolean = true,
    val stage: AlarmAlertStage = AlarmAlertStage.RINGING
) {
    val pageIndexInWird: Int get() = wirdPages.indexOf(currentPage)
    val hasPrevPageInWird: Boolean get() = pageIndexInWird > 0
    val hasNextPageInWird: Boolean get() = pageIndexInWird >= 0 && pageIndexInWird < wirdPages.size - 1
    val lastReadableLineOnPage: Int get() = currentLines.filter { it.isReadable }.maxOfOrNull { it.lineNumber } ?: pageMaxLines

    /**
     * Law 6 in AGENTS.md (Single Sacred Mission Law):
     * Dismissing the alarm is strictly forbidden until entering reading and completing the entire wird.
     */
    val isDismissEnabled: Boolean
        get() = stage == AlarmAlertStage.MUSHAF_READING && isWirdCompleted
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
        rawPageCount: Int,
        isSnoozeEnabled: Boolean = true
    ) {
        val safeStartPage = rawStartPage.coerceIn(1, 604)
        val safePageCount = rawPageCount.coerceAtLeast(1)
        val computedWirdPages = (0 until safePageCount).map { Math.floorMod(safeStartPage - 1 + it, 604) + 1 }
        val safeEndPage = computedWirdPages.last()

        val initialMaxLines = lineBoundsRepository.getPageLineCount(safeStartPage)

        _uiState.update {
            it.copy(
                alarmId = alarmId,
                alarmLabel = alarmLabel,
                startPage = safeStartPage,
                endPage = safeEndPage,
                pageCount = safePageCount,
                currentPage = safeStartPage,
                pageMaxLines = initialMaxLines,
                wirdPages = computedWirdPages,
                completedPages = emptySet(),
                isWirdCompleted = false,
                isSnoozeEnabled = isSnoozeEnabled,
                stage = AlarmAlertStage.RINGING
            )
        }

        loadPage(safeStartPage)
    }

    /**
     * Transitions from the ringing alert screen to the sacred Mushaf reading session.
     */
    fun startReadingWird() {
        _uiState.update { it.copy(stage = AlarmAlertStage.MUSHAF_READING) }
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
            checkCompletion()
        }
    }

    fun onLineClicked(lineNumber: Int) {
        val state = _uiState.value
        val target = state.currentLines.firstOrNull { it.lineNumber == lineNumber }
        if (target != null && target.isReadable) {
            _uiState.update { it.copy(activeLine = lineNumber, stage = AlarmAlertStage.MUSHAF_READING) }
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
            _uiState.update { it.copy(activeLine = nextReadable.lineNumber, stage = AlarmAlertStage.MUSHAF_READING) }
            checkCompletion()
        } else if (state.hasNextPageInWird) {
            // Mark current page as completed and advance to next page in wird
            val nextPage = state.wirdPages[state.pageIndexInWird + 1]
            _uiState.update { it.copy(completedPages = it.completedPages + state.currentPage, stage = AlarmAlertStage.MUSHAF_READING) }
            loadPage(nextPage)
        } else {
            // Reached last readable line of final wird page
            val updatedCompleted = state.completedPages + state.currentPage
            val priorPages = state.wirdPages.takeWhile { it != state.endPage }
            val allPriorCompleted = priorPages.all { it in updatedCompleted }
            if (allPriorCompleted || state.wirdPages.size <= 1) {
                _uiState.update { it.copy(completedPages = updatedCompleted, isWirdCompleted = true, stage = AlarmAlertStage.MUSHAF_READING) }
            }
        }
    }

    fun goToPreviousPage() {
        val state = _uiState.value
        if (state.hasPrevPageInWird) {
            val prevPage = state.wirdPages[state.pageIndexInWird - 1]
            loadPage(prevPage)
        }
    }

    fun goToNextPage() {
        val state = _uiState.value
        if (state.hasNextPageInWird) {
            val lastReadable = state.currentLines.filter { it.isReadable }.maxOfOrNull { it.lineNumber } ?: state.pageMaxLines
            val updatedCompleted = if (state.activeLine >= lastReadable) {
                state.completedPages + state.currentPage
            } else {
                state.completedPages
            }
            val nextPage = state.wirdPages[state.pageIndexInWird + 1]
            _uiState.update { it.copy(completedPages = updatedCompleted) }
            loadPage(nextPage)
            checkCompletion()
        }
    }

    private fun checkCompletion() {
        val state = _uiState.value
        val lastReadable = state.currentLines.filter { it.isReadable }.maxOfOrNull { it.lineNumber } ?: state.pageMaxLines

        val isCurrentPageFinished = state.activeLine >= lastReadable
        val updatedCompleted = if (isCurrentPageFinished) {
            state.completedPages + state.currentPage
        } else {
            state.completedPages
        }

        val allWirdCompleted = state.wirdPages.all { it in updatedCompleted }
        val priorPages = state.wirdPages.takeWhile { it != state.endPage }
        val allPriorCompleted = priorPages.all { it in updatedCompleted }

        val isCompleted = allWirdCompleted || (state.currentPage == state.endPage && isCurrentPageFinished && (allPriorCompleted || state.wirdPages.size <= 1))

        if (updatedCompleted != state.completedPages || isCompleted != state.isWirdCompleted) {
            _uiState.update {
                it.copy(
                    completedPages = updatedCompleted,
                    isWirdCompleted = isCompleted,
                    stage = AlarmAlertStage.MUSHAF_READING
                )
            }
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

                val currentAlarm = alarmRepository.getAlarmById(state.alarmId)
                val baseAlarm = currentAlarm ?: AlarmItem(
                    id = state.alarmId,
                    hour = cal.get(Calendar.HOUR_OF_DAY),
                    minute = cal.get(Calendar.MINUTE),
                    repeatDays = emptySet(),
                    isEnabled = true,
                    isVibrationEnabled = true,
                    label = state.alarmLabel,
                    targetPage = state.startPage,
                    startPage = state.startPage,
                    pageCount = state.pageCount
                )

                val snoozeItem = baseAlarm.copy(
                    isEnabled = true,
                    explicitTriggerMillis = snoozeTime
                )
                alarmRepository.saveAlarm(snoozeItem)
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

