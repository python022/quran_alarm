package com.quran.alarm.alarm.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.data.storage.AlarmSessionStore
import com.quran.alarm.alarm.data.storage.ReadingProgressStore
import com.quran.alarm.alarm.data.storage.SessionReadingProgress
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.quran.data.AyahInfoDatabase
import com.quran.alarm.quran.data.AyahInfoLineBoundsRepositoryImpl
import com.quran.alarm.quran.data.QuranPageManager
import com.quran.alarm.quran.domain.model.LineRect
import com.quran.alarm.quran.domain.repository.LineBoundsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    MUSHAF_READING;

    companion object {
        val READING = MUSHAF_READING
    }
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
    val stage: AlarmAlertStage = AlarmAlertStage.RINGING,
    val wirdSurahNumber: Int? = null,
    val startAyah: Int? = null,
    val endAyah: Int? = null,
    val dailyAyahCount: Int? = null,
    val currentPageStartLine: Int? = null,
    val currentPageEndLine: Int? = null,
    val loadError: String? = null,
    val isInitialized: Boolean = false
) {
    val isAyahMode: Boolean
        get() = wirdSurahNumber != null && startAyah != null && endAyah != null && dailyAyahCount != null
    val pageIndexInWird: Int get() = wirdPages.indexOf(currentPage)
    val hasPrevPageInWird: Boolean get() = pageIndexInWird > 0
    val hasNextPageInWird: Boolean get() = pageIndexInWird >= 0 && pageIndexInWird < wirdPages.size - 1
    val firstReadableLineOnPage: Int
        get() = currentLines
            .filter { it.isReadable && (currentPageStartLine == null || it.lineNumber >= currentPageStartLine) }
            .minOfOrNull { it.lineNumber }
            ?: (currentPageStartLine ?: 1)
    val lastReadableLineOnPage: Int
        get() = currentLines
            .filter { it.isReadable && (currentPageEndLine == null || it.lineNumber <= currentPageEndLine) }
            .maxOfOrNull { it.lineNumber }
            ?: (currentPageEndLine ?: pageMaxLines)
    val canNavigateNextInAlert: Boolean get() = hasNextPageInWird && (currentPage in completedPages || activeLine >= lastReadableLineOnPage)

    /**
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val progressStore: ReadingProgressStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MushafReaderUiState())
    val uiState: StateFlow<MushafReaderUiState> = _uiState.asStateFlow()

    private var initializeJob: Job? = null
    private var loadPageJob: Job? = null
    private val visitedLineByPage = mutableMapOf<Int, Int>()

    val isInitialized: Boolean
        get() = _uiState.value.isInitialized

    fun initialize(
        alarmId: Int,
        alarmLabel: String,
        rawStartPage: Int,
        rawPageCount: Int,
        isSnoozeEnabled: Boolean = true,
        isFreshTrigger: Boolean = true,
        wirdSurahNumber: Int? = null,
        startAyah: Int? = null,
        dailyAyahCount: Int? = null
    ) {
        initializeJob?.cancel()
        loadPageJob?.cancel()
        visitedLineByPage.clear()
        _uiState.update { it.copy(isLoading = true) }

        initializeJob = viewModelScope.launch {
            val (storedAlarm, restored) = withContext(ioDispatcher) {
                val alarm = if (alarmId >= 0 && (wirdSurahNumber == null || dailyAyahCount == null)) {
                    runCatching { alarmRepository.getAlarmById(alarmId) }.getOrNull()
                } else {
                    null
                }
                val savedProgress = if (!isFreshTrigger && alarmId >= 0) {
                    progressStore?.load(alarmId)
                } else {
                    null
                }
                alarm to savedProgress
            }

            val resolvedSurah = (wirdSurahNumber ?: storedAlarm?.wirdSurahNumber)
                ?.takeIf { it in 1..QuranStructure.TOTAL_SURAHS }
            val resolvedDailyAyahs = (dailyAyahCount ?: storedAlarm?.dailyAyahCount)
                ?.takeIf { it >= 1 }
            val resolvedStartAyah = if (resolvedSurah != null && resolvedDailyAyahs != null) {
                val maxAyahs = QuranStructure.getSurahByNumber(resolvedSurah).ayahCount
                (startAyah ?: storedAlarm?.startAyah ?: 1).coerceIn(1, maxAyahs)
            } else {
                null
            }
            val resolvedEndAyah = QuranStructure.computeEndAyah(
                surahNumber = resolvedSurah,
                startAyah = resolvedStartAyah,
                dailyAyahCount = resolvedDailyAyahs
            )
            val ayahRange = if (resolvedSurah != null && resolvedStartAyah != null && resolvedEndAyah != null) {
                withContext(ioDispatcher) {
                    runCatching {
                        lineBoundsRepository.getAyahRangeInfo(resolvedSurah, resolvedStartAyah, resolvedEndAyah)
                    }.getOrNull()
                }
            } else {
                null
            }

            val effectiveStartPage = ayahRange?.startPage ?: rawStartPage
            val effectivePageCount = ayahRange?.let {
                QuranStructure.computePageCount(it.startPage, it.endPage)
            } ?: rawPageCount

            val safeStartPage = effectiveStartPage.coerceIn(1, 604)
            val safePageCount = effectivePageCount.coerceAtLeast(1)
            val computedWirdPages = (0 until safePageCount).map { Math.floorMod(safeStartPage - 1 + it, 604) + 1 }
            val safeEndPage = computedWirdPages.last()

            val initialStage = restored?.let {
                runCatching { AlarmAlertStage.valueOf(it.stageName) }.getOrNull()
            } ?: AlarmAlertStage.RINGING
            val initialPage = restored?.currentPage?.takeIf { it in computedWirdPages } ?: safeStartPage
            val initialCompleted = restored?.completedPages?.filter { it in computedWirdPages }?.toSet() ?: emptySet()
            if (restored != null) {
                visitedLineByPage.putAll(restored.visitedLineByPage)
            }

            _uiState.update {
                it.copy(
                    alarmId = alarmId,
                    alarmLabel = alarmLabel,
                    startPage = safeStartPage,
                    endPage = safeEndPage,
                    pageCount = safePageCount,
                    currentPage = initialPage,
                    wirdPages = computedWirdPages,
                    completedPages = initialCompleted,
                    isWirdCompleted = computedWirdPages.isNotEmpty() && computedWirdPages.all { p -> p in initialCompleted },
                    isSnoozeEnabled = isSnoozeEnabled,
                    stage = initialStage,
                    wirdSurahNumber = resolvedSurah,
                    startAyah = resolvedStartAyah,
                    endAyah = resolvedEndAyah,
                    dailyAyahCount = resolvedDailyAyahs,
                    currentPageStartLine = null,
                    currentPageEndLine = null,
                    isInitialized = true
                )
            }
            persistProgress()
            loadPage(initialPage, resetToFirstReadable = restored == null)
        }
    }

    /**
     * Transitions from the ringing alert screen to the sacred Mushaf reading session.
     */
    fun startReadingWird() {
        _uiState.update { it.copy(stage = AlarmAlertStage.MUSHAF_READING) }
        persistProgress()
    }

    fun loadPage(pageNumber: Int, resetToFirstReadable: Boolean = true) {
        val safePage = pageNumber.coerceIn(1, 604)
        val wasSamePage = _uiState.value.currentPage == safePage
        loadPageJob?.cancel()
        loadPageJob = viewModelScope.launch {
            try {
                // Keep old currentPage aligned with old currentLines/activeLine until IO finishes atomically.
                _uiState.update { it.copy(isLoading = true, loadError = null) }

                val (lines, maxLines) = withContext(ioDispatcher) {
                    lineBoundsRepository.getLinesForPage(safePage) to lineBoundsRepository.getPageLineCount(safePage)
                }
                if (lines.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "تعذر تحميل أسطر صفحة المصحف. يرجى المحاولة مرة أخرى."
                        )
                    }
                    return@launch
                }
                val stateSnapshot = _uiState.value
                val ayahLineRange = if (stateSnapshot.isAyahMode) {
                    withContext(ioDispatcher) {
                        lineBoundsRepository.getAyahLineRangeOnPage(
                            surahNumber = stateSnapshot.wirdSurahNumber!!,
                            startAyah = stateSnapshot.startAyah!!,
                            endAyah = stateSnapshot.endAyah!!,
                            pageNumber = safePage
                        )
                    }
                } else {
                    null
                }
                val bitmap = withContext(ioDispatcher) {
                    pageManager?.loadPageBitmap(safePage)
                }
                val allowedLines = if (ayahLineRange != null) {
                    lines.filter { it.isReadable && it.lineNumber in ayahLineRange }
                } else {
                    lines.filter { it.isReadable }
                }
                val firstReadable = allowedLines.minOfOrNull { it.lineNumber }
                    ?: (ayahLineRange?.first ?: lines.firstOrNull { it.isReadable }?.lineNumber ?: 1)

                _uiState.update { current ->
                    val savedLine = visitedLineByPage[safePage]
                    val lineToSet = when {
                        !resetToFirstReadable && savedLine != null && allowedLines.any { it.lineNumber == savedLine } -> savedLine
                        !resetToFirstReadable && wasSamePage && current.activeLine in 1..maxLines && allowedLines.any { it.lineNumber == current.activeLine } -> current.activeLine
                        else -> firstReadable
                    }
                    visitedLineByPage[safePage] = lineToSet
                    current.copy(
                        isLoading = false,
                        currentPage = safePage,
                        currentBitmap = bitmap,
                        currentLines = lines,
                        pageMaxLines = maxLines,
                        currentPageStartLine = ayahLineRange?.first,
                        currentPageEndLine = ayahLineRange?.last,
                        loadError = null,
                        activeLine = lineToSet
                    )
                }
                checkCompletion()

                // Asynchronously warm cache for adjacent pages without blocking UI
                launch(ioDispatcher) {
                    pageManager?.preloadPages(safePage, radius = 1)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                runCatching { Log.e("AlarmAlertViewModel", "Error loading page $safePage: ${e.message}", e) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = "تعذر تحميل صفحة المصحف الشريف. يرجى المحاولة مرة أخرى."
                    )
                }
            }
        }
    }

    fun retryLoadPage() {
        val state = _uiState.value
        loadPage(state.currentPage, resetToFirstReadable = false)
    }

    /**
     * Emergency exit gate allowed ONLY when page loading failed or in preview mode,
     * ensuring storage/DB failures can never trap the device in an inescapable lockscreen loop.
     */
    fun emergencyExitOnError(onExit: () -> Unit) {
        val state = _uiState.value
        if (state.loadError == null && state.alarmId >= 0) return
        snooze(minutes = 5, onSnooze = onExit)
    }

    fun onLineClicked(lineNumber: Int, allowDirectJump: Boolean = true) {
        val state = _uiState.value
        if (state.isLoading) return
        val target = state.currentLines.firstOrNull { it.lineNumber == lineNumber }
        val minAllowed = state.firstReadableLineOnPage
        val maxAllowed = state.lastReadableLineOnPage
        if (target != null && target.isReadable && lineNumber in minAllowed..maxAllowed) {
            val nextReadable = state.currentLines
                .filter { it.lineNumber > state.activeLine && it.lineNumber <= maxAllowed && it.isReadable }
                .minByOrNull { it.lineNumber }?.lineNumber

            if (allowDirectJump || lineNumber <= state.activeLine || lineNumber == nextReadable) {
                visitedLineByPage[state.currentPage] = lineNumber
                _uiState.update { it.copy(activeLine = lineNumber, stage = AlarmAlertStage.MUSHAF_READING) }
                checkCompletion()
            }
        }
    }

    fun advanceToNextLineOrPage() {
        val state = _uiState.value
        if (state.isLoading) return
        val lines = state.currentLines
        val maxAllowed = state.lastReadableLineOnPage

        // Find next readable line on current page within the wird bounds
        val nextReadable = lines
            .filter { it.lineNumber > state.activeLine && it.lineNumber <= maxAllowed && it.isReadable }
            .minByOrNull { it.lineNumber }

        if (nextReadable != null) {
            visitedLineByPage[state.currentPage] = nextReadable.lineNumber
            _uiState.update { it.copy(activeLine = nextReadable.lineNumber, stage = AlarmAlertStage.MUSHAF_READING) }
            checkCompletion()
        } else if (state.hasNextPageInWird) {
            // Mark current page as completed and advance to next page in wird
            val nextPage = state.wirdPages[state.pageIndexInWird + 1]
            _uiState.update { it.copy(completedPages = it.completedPages + state.currentPage, stage = AlarmAlertStage.MUSHAF_READING) }
            loadPage(nextPage, resetToFirstReadable = false)
        } else {
            // Reached last readable line of final wird page
            val (updatedCompleted, isCompleted) = evaluateWirdCompletion(
                state = state,
                isCurrentPageFinished = true
            )
            _uiState.update {
                it.copy(
                    completedPages = updatedCompleted,
                    isWirdCompleted = isCompleted || it.isWirdCompleted,
                    stage = AlarmAlertStage.MUSHAF_READING
                )
            }
            persistProgress()
        }
    }

    fun goToPreviousPage() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.hasPrevPageInWird) {
            val prevPage = state.wirdPages[state.pageIndexInWird - 1]
            loadPage(prevPage, resetToFirstReadable = false)
        }
    }

    fun goToNextPage() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.hasNextPageInWird) {
            val lastReadable = state.lastReadableLineOnPage
            val updatedCompleted = if (state.activeLine >= lastReadable || state.currentPage in state.completedPages) {
                state.completedPages + state.currentPage
            } else {
                state.completedPages
            }
            val nextPage = state.wirdPages[state.pageIndexInWird + 1]
            _uiState.update { it.copy(completedPages = updatedCompleted) }
            loadPage(nextPage, resetToFirstReadable = false)
        }
    }

    fun goToFirstIncompletePage() {
        val state = _uiState.value
        if (state.isLoading) return
        val incompletePage = state.wirdPages.firstOrNull { it !in state.completedPages } ?: return
        loadPage(incompletePage, resetToFirstReadable = false)
    }

    private fun evaluateWirdCompletion(
        state: MushafReaderUiState,
        isCurrentPageFinished: Boolean
    ): Pair<Set<Int>, Boolean> {
        val updatedCompleted = if (isCurrentPageFinished) {
            state.completedPages + state.currentPage
        } else {
            state.completedPages
        }
        val isCompleted = state.wirdPages.isNotEmpty() && state.wirdPages.all { it in updatedCompleted }
        return updatedCompleted to isCompleted
    }

    private fun checkCompletion() {
        val state = _uiState.value
        if (state.wirdPages.isEmpty()) return
        val lastReadable = state.lastReadableLineOnPage
        val isCurrentPageFinished = state.activeLine >= lastReadable
        val (updatedCompleted, isCompleted) = evaluateWirdCompletion(state, isCurrentPageFinished)

        if (updatedCompleted != state.completedPages || isCompleted != state.isWirdCompleted) {
            _uiState.update {
                it.copy(
                    completedPages = updatedCompleted,
                    isWirdCompleted = isCompleted
                )
            }
        }
        persistProgress()
    }

    private fun persistProgress() {
        val state = _uiState.value
        if (state.alarmId < 0) return
        progressStore?.save(
            SessionReadingProgress(
                alarmId = state.alarmId,
                stageName = state.stage.name,
                currentPage = state.currentPage,
                completedPages = state.completedPages,
                visitedLineByPage = visitedLineByPage.toMap()
            )
        )
    }

    fun completeWird(onComplete: () -> Unit) {
        val state = _uiState.value
        if (!state.isDismissEnabled || state.isDismissedOrSnoozed) return
        _uiState.update { it.copy(isDismissedOrSnoozed = true) }
        progressStore?.clear()

        viewModelScope.launch {
            try {
                withContext(ioDispatcher + NonCancellable) {
                    val currentAlarm = alarmRepository.getAlarmById(state.alarmId)
                    if (currentAlarm != null) {
                        val willRepeat = currentAlarm.repeatDays.isNotEmpty()
                        var advancedAlarm = currentAlarm.advanceToNextWird().copy(isEnabled = willRepeat)
                        if (advancedAlarm.isAyahMode) {
                            val nextStartAyah = advancedAlarm.startAyah ?: 1
                            val nextEndAyah = advancedAlarm.endAyah ?: nextStartAyah
                            val nextRange = lineBoundsRepository.getAyahRangeInfo(
                                surahNumber = advancedAlarm.wirdSurahNumber!!,
                                startAyah = nextStartAyah,
                                endAyah = nextEndAyah
                            )
                            if (nextRange != null) {
                                val nextPageCount = QuranStructure.computePageCount(nextRange.startPage, nextRange.endPage)
                                advancedAlarm = advancedAlarm.copy(
                                    startPage = nextRange.startPage,
                                    targetPage = nextRange.startPage,
                                    pageCount = nextPageCount
                                )
                            }
                        }
                        alarmRepository.saveAlarm(advancedAlarm)
                        if (willRepeat) {
                            alarmScheduler.schedule(advancedAlarm)
                        }
                    }
                }
            } finally {
                onComplete()
            }
        }
    }

    fun snooze(minutes: Int = 5, onSnooze: () -> Unit) {
        val state = _uiState.value
        if (state.isDismissedOrSnoozed) return
        _uiState.update { it.copy(isDismissedOrSnoozed = true) }
        progressStore?.clear()

        // Preview mode uses negative sentinel IDs (e.g. -999): exit safely without persisting or scheduling
        if (state.alarmId < 0) {
            onSnooze()
            return
        }

        viewModelScope.launch {
            try {
                withContext(ioDispatcher + NonCancellable) {
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
                        snoozeTriggerMillis = snoozeTime
                    )
                    alarmRepository.saveAlarm(snoozeItem)
                    alarmScheduler.scheduleSnooze(snoozeItem, snoozeTime)
                }
            } finally {
                onSnooze()
            }
        }
    }

    fun onTrimMemory(level: Int) {
        pageManager?.onTrimMemory(level)
    }

    override fun onCleared() {
        pageManager?.clearCache()
        super.onCleared()
    }
}

class AlarmAlertViewModelFactory(
    context: Context,
    private val pageManager: QuranPageManager? = null,
    private val lineBoundsRepository: LineBoundsRepository? = null,
    private val alarmRepository: AlarmRepository? = null,
    private val alarmScheduler: AlarmScheduler? = null
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext ?: context

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val pm = pageManager ?: QuranPageManager(appContext)
        val lb = lineBoundsRepository ?: AyahInfoLineBoundsRepositoryImpl(AyahInfoDatabase.getInstance(appContext))
        val ar = alarmRepository ?: AlarmRepositoryImpl(appContext)
        val asch = alarmScheduler ?: AlarmSchedulerImpl(appContext)
        return AlarmAlertViewModel(
            lb,
            pm,
            ar,
            asch,
            progressStore = AlarmSessionStore.fromContext(appContext)
        ) as T
    }
}

