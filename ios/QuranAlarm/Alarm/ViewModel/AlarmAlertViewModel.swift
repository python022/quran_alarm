import Combine
import Foundation
import UIKit

public enum AlarmAlertStage: String, Codable, Equatable {
    case ringing = "RINGING"
    case mushafReading = "MUSHAF_READING"
}

public struct MushafReaderUiState: Equatable {
    public var alarmId: Int = 0
    public var alarmLabel: String = "ورد القرآن الكريم"
    public var startPage: Int = 1
    public var endPage: Int = 1
    public var pageCount: Int = 1
    public var currentPage: Int = 1
    public var activeLine: Int = 1
    public var pageMaxLines: Int = 15
    public var currentImage: UIImage? = nil
    public var currentLines: [LineRect] = []
    public var wirdPages: [Int] = []
    public var completedPages: Set<Int> = []
    public var isLoading: Bool = true
    public var isWirdCompleted: Bool = false
    public var isDismissedOrSnoozed: Bool = false
    public var isSnoozeEnabled: Bool = true
    public var stage: AlarmAlertStage = .ringing
    public var wirdSurahNumber: Int? = nil
    public var startAyah: Int? = nil
    public var endAyah: Int? = nil
    public var dailyAyahCount: Int? = nil
    public var currentPageStartLine: Int? = nil
    public var currentPageEndLine: Int? = nil
    public var loadError: String? = nil
    public var isInitialized: Bool = false

    public var isAyahMode: Bool {
        wirdSurahNumber != nil && startAyah != nil && endAyah != nil && dailyAyahCount != nil
    }

    public var pageIndexInWird: Int {
        wirdPages.firstIndex(of: currentPage) ?? -1
    }

    public var hasPrevPageInWird: Bool {
        pageIndexInWird > 0
    }

    public var hasNextPageInWird: Bool {
        pageIndexInWird >= 0 && pageIndexInWird < wirdPages.count - 1
    }

    public var firstReadableLineOnPage: Int {
        currentLines
            .filter { $0.isReadable && (currentPageStartLine == nil || $0.lineNumber >= currentPageStartLine!) }
            .map(\.lineNumber)
            .min() ?? (currentPageStartLine ?? 1)
    }

    public var lastReadableLineOnPage: Int {
        currentLines
            .filter { $0.isReadable && (currentPageEndLine == nil || $0.lineNumber <= currentPageEndLine!) }
            .map(\.lineNumber)
            .max() ?? (currentPageEndLine ?? pageMaxLines)
    }

    public var canNavigateNextInAlert: Bool {
        hasNextPageInWird && (completedPages.contains(currentPage) || activeLine >= lastReadableLineOnPage)
    }

    /// Dismissing the alarm is strictly forbidden until entering reading and completing the entire wird.
    public var isDismissEnabled: Bool {
        stage == .mushafReading && isWirdCompleted
    }
}

@MainActor
public final class AlarmAlertViewModel: ObservableObject {
    @Published public private(set) var uiState = MushafReaderUiState()

    private let lineBoundsRepository: LineBoundsRepository
    private let pageManager: QuranPageManager?
    private let alarmRepository: AlarmRepository
    private let alarmScheduler: AlarmScheduler
    private let progressStore: ReadingProgressStore?
    private let onSilenceAudio: () -> Void

    private var initializeTask: Task<Void, Never>?
    private var loadPageTask: Task<Void, Never>?
    private var visitedLineByPage: [Int: Int] = [:]

    public var isInitialized: Bool {
        uiState.isInitialized
    }

    public init(
        lineBoundsRepository: LineBoundsRepository,
        pageManager: QuranPageManager?,
        alarmRepository: AlarmRepository,
        alarmScheduler: AlarmScheduler,
        progressStore: ReadingProgressStore? = nil,
        onSilenceAudio: @escaping () -> Void = {}
    ) {
        self.lineBoundsRepository = lineBoundsRepository
        self.pageManager = pageManager
        self.alarmRepository = alarmRepository
        self.alarmScheduler = alarmScheduler
        self.progressStore = progressStore
        self.onSilenceAudio = onSilenceAudio
    }

    deinit {
        initializeTask?.cancel()
        loadPageTask?.cancel()
    }

    public func initialize(
        alarmId: Int,
        alarmLabel: String,
        rawStartPage: Int,
        rawPageCount: Int,
        isSnoozeEnabled: Bool = true,
        isFreshTrigger: Bool = true,
        wirdSurahNumber: Int? = nil,
        startAyah: Int? = nil,
        dailyAyahCount: Int? = nil,
        initialStageOverride: AlarmAlertStage? = nil
    ) {
        initializeTask?.cancel()
        loadPageTask?.cancel()
        visitedLineByPage.removeAll()
        uiState.isLoading = true

        initializeTask = Task { [weak self] in
            guard let self else { return }
            let storedAlarm: AlarmItem? = (alarmId >= 0 && (wirdSurahNumber == nil || dailyAyahCount == nil))
                ? self.alarmRepository.getAlarmById(alarmId)
                : nil
            let restored: SessionReadingProgress? = (!isFreshTrigger && alarmId >= 0)
                ? self.progressStore?.load(alarmId: alarmId)
                : nil

            guard !Task.isCancelled else { return }

            let resolvedSurah = (wirdSurahNumber ?? storedAlarm?.wirdSurahNumber)
                .flatMap { (1...QuranStructure.totalSurahs).contains($0) ? $0 : nil }
            let resolvedDailyAyahs = (dailyAyahCount ?? storedAlarm?.dailyAyahCount)
                .flatMap { $0 >= 1 ? $0 : nil }
            let resolvedStartAyah: Int?
            if let surahNum = resolvedSurah, resolvedDailyAyahs != nil {
                let maxAyahs = QuranStructure.getSurahByNumber(surahNum).ayahCount
                resolvedStartAyah = min(max(startAyah ?? storedAlarm?.startAyah ?? 1, 1), maxAyahs)
            } else {
                resolvedStartAyah = nil
            }

            let resolvedEndAyah = QuranStructure.computeEndAyah(
                surahNumber: resolvedSurah,
                startAyah: resolvedStartAyah,
                dailyAyahCount: resolvedDailyAyahs
            )

            let ayahRange: AyahPageRange?
            if let sNum = resolvedSurah, let sAyah = resolvedStartAyah, let eAyah = resolvedEndAyah {
                ayahRange = await self.lineBoundsRepository.getAyahRangeInfo(
                    surahNumber: sNum,
                    startAyah: sAyah,
                    endAyah: eAyah
                )
            } else {
                ayahRange = nil
            }

            guard !Task.isCancelled else { return }

            let effectiveStartPage = ayahRange?.startPage ?? rawStartPage
            let effectivePageCount = ayahRange.map {
                QuranStructure.computePageCount(startPage: $0.startPage, endPage: $0.endPage)
            } ?? rawPageCount

            let safeStartPage = min(max(effectiveStartPage, 1), 604)
            let safePageCount = max(effectivePageCount, 1)
            let computedWirdPages = (0..<safePageCount).map {
                QuranStructure.positiveMod(safeStartPage - 1 + $0, 604) + 1
            }
            let safeEndPage = computedWirdPages.last ?? safeStartPage

            let restoredStage = restored.flatMap { AlarmAlertStage(rawValue: $0.stageName) }
            let initialStage = initialStageOverride ?? restoredStage ?? .ringing
            let initialPage = restored?.currentPage.flatMap { computedWirdPages.contains($0) ? $0 : nil } ?? safeStartPage
            let initialCompleted = Set((restored?.completedPages ?? []).filter { computedWirdPages.contains($0) })
            if let restored {
                for (k, v) in restored.visitedLineByPage {
                    self.visitedLineByPage[k] = v
                }
            }

            if initialStage == .mushafReading {
                self.onSilenceAudio()
            }

            self.uiState.alarmId = alarmId
            self.uiState.alarmLabel = alarmLabel
            self.uiState.startPage = safeStartPage
            self.uiState.endPage = safeEndPage
            self.uiState.pageCount = safePageCount
            self.uiState.currentPage = initialPage
            self.uiState.wirdPages = computedWirdPages
            self.uiState.completedPages = initialCompleted
            self.uiState.isWirdCompleted = !computedWirdPages.isEmpty && computedWirdPages.allSatisfy { initialCompleted.contains($0) }
            self.uiState.isSnoozeEnabled = isSnoozeEnabled
            self.uiState.stage = initialStage
            self.uiState.wirdSurahNumber = resolvedSurah
            self.uiState.startAyah = resolvedStartAyah
            self.uiState.endAyah = resolvedEndAyah
            self.uiState.dailyAyahCount = resolvedDailyAyahs
            self.uiState.currentPageStartLine = nil
            self.uiState.currentPageEndLine = nil
            self.uiState.isInitialized = true

            self.persistProgress()
            self.loadPage(initialPage, resetToFirstReadable: restored == nil)
        }
    }

    public func startReadingWird() {
        onSilenceAudio()
        uiState.stage = .mushafReading
        persistProgress()
    }

    public func loadPage(_ pageNumber: Int, resetToFirstReadable: Bool = true) {
        let safePage = min(max(pageNumber, 1), 604)
        let wasSamePage = uiState.currentPage == safePage
        loadPageTask?.cancel()
        uiState.isLoading = true
        uiState.loadError = nil

        loadPageTask = Task { [weak self] in
            guard let self else { return }
            let lines = await self.lineBoundsRepository.getLinesForPage(safePage)
            let maxLines = self.lineBoundsRepository.getPageLineCount(safePage)

            guard !Task.isCancelled else { return }

            if lines.isEmpty {
                self.uiState.isLoading = false
                self.uiState.loadError = "تعذر تحميل أسطر صفحة المصحف. يرجى المحاولة مرة أخرى."
                return
            }

            let snapshot = self.uiState
            let ayahLineRange: ClosedRange<Int>?
            if snapshot.isAyahMode,
               let sNum = snapshot.wirdSurahNumber,
               let sAyah = snapshot.startAyah,
               let eAyah = snapshot.endAyah {
                ayahLineRange = await self.lineBoundsRepository.getAyahLineRangeOnPage(
                    surahNumber: sNum,
                    startAyah: sAyah,
                    endAyah: eAyah,
                    pageNumber: safePage
                )
            } else {
                ayahLineRange = nil
            }

            guard !Task.isCancelled else { return }

            let image = await self.pageManager?.loadPageImage(pageNumber: safePage)
            guard !Task.isCancelled else { return }

            let allowedLines: [LineRect]
            if let range = ayahLineRange {
                allowedLines = lines.filter { $0.isReadable && range.contains($0.lineNumber) }
            } else {
                allowedLines = lines.filter(\.isReadable)
            }

            let firstReadable = allowedLines.map(\.lineNumber).min()
                ?? ayahLineRange?.lowerBound
                ?? lines.first(where: \.isReadable)?.lineNumber
                ?? 1

            let savedLine = self.visitedLineByPage[safePage]
            let lineToSet: Int
            if !resetToFirstReadable, let saved = savedLine, allowedLines.contains(where: { $0.lineNumber == saved }) {
                lineToSet = saved
            } else if !resetToFirstReadable,
                      wasSamePage,
                      (1...maxLines).contains(self.uiState.activeLine),
                      allowedLines.contains(where: { $0.lineNumber == self.uiState.activeLine }) {
                lineToSet = self.uiState.activeLine
            } else {
                lineToSet = firstReadable
            }

            self.visitedLineByPage[safePage] = lineToSet
            self.uiState.isLoading = false
            self.uiState.currentPage = safePage
            self.uiState.currentImage = image
            self.uiState.currentLines = lines
            self.uiState.pageMaxLines = maxLines
            self.uiState.currentPageStartLine = ayahLineRange?.lowerBound
            self.uiState.currentPageEndLine = ayahLineRange?.upperBound
            self.uiState.loadError = nil
            self.uiState.activeLine = lineToSet

            self.checkCompletion()

            if let pm = self.pageManager {
                Task.detached(priority: .utility) {
                    await pm.preloadPages(centerPage: safePage, radius: 1)
                }
            }
        }
    }

    /// Synchronous helper for deterministic unit testing without async sleep races.
    public func initializeAndLoadSyncForTesting(
        alarmId: Int,
        alarmLabel: String,
        rawStartPage: Int,
        rawPageCount: Int,
        isSnoozeEnabled: Bool = true,
        wirdSurahNumber: Int? = nil,
        startAyah: Int? = nil,
        dailyAyahCount: Int? = nil
    ) async {
        initialize(
            alarmId: alarmId,
            alarmLabel: alarmLabel,
            rawStartPage: rawStartPage,
            rawPageCount: rawPageCount,
            isSnoozeEnabled: isSnoozeEnabled,
            isFreshTrigger: true,
            wirdSurahNumber: wirdSurahNumber,
            startAyah: startAyah,
            dailyAyahCount: dailyAyahCount
        )
        await initializeTask?.value
        await loadPageTask?.value
    }

    public func awaitPendingPageLoad() async {
        await loadPageTask?.value
    }

    public func retryLoadPage() {
        loadPage(uiState.currentPage, resetToFirstReadable: false)
    }

    public func emergencyExitOnError(onExit: @escaping () -> Void) {
        if uiState.loadError == nil && uiState.alarmId >= 0 { return }
        snooze(minutes: 5, onSnooze: onExit)
    }

    public func onLineClicked(_ lineNumber: Int, allowDirectJump: Bool = true) {
        guard !uiState.isLoading else { return }
        let target = uiState.currentLines.first(where: { $0.lineNumber == lineNumber })
        let minAllowed = uiState.firstReadableLineOnPage
        let maxAllowed = uiState.lastReadableLineOnPage
        guard let target, target.isReadable, (minAllowed...maxAllowed).contains(lineNumber) else {
            return
        }

        let nextReadable = uiState.currentLines
            .filter { $0.lineNumber > uiState.activeLine && $0.lineNumber <= maxAllowed && $0.isReadable }
            .map(\.lineNumber)
            .min()

        if allowDirectJump || lineNumber <= uiState.activeLine || lineNumber == nextReadable {
            onSilenceAudio()
            visitedLineByPage[uiState.currentPage] = lineNumber
            uiState.activeLine = lineNumber
            uiState.stage = .mushafReading
            checkCompletion()
        }
    }

    public func advanceToNextLineOrPage() {
        guard !uiState.isLoading else { return }
        let maxAllowed = uiState.lastReadableLineOnPage
        let nextReadable = uiState.currentLines
            .filter { $0.lineNumber > uiState.activeLine && $0.lineNumber <= maxAllowed && $0.isReadable }
            .min(by: { $0.lineNumber < $1.lineNumber })

        onSilenceAudio()
        if let nextReadable {
            visitedLineByPage[uiState.currentPage] = nextReadable.lineNumber
            uiState.activeLine = nextReadable.lineNumber
            uiState.stage = .mushafReading
            checkCompletion()
        } else if uiState.hasNextPageInWird {
            let nextPage = uiState.wirdPages[uiState.pageIndexInWird + 1]
            uiState.completedPages.insert(uiState.currentPage)
            uiState.stage = .mushafReading
            loadPage(nextPage, resetToFirstReadable: false)
        } else {
            let (updatedCompleted, isCompleted) = evaluateWirdCompletion(
                state: uiState,
                isCurrentPageFinished: true
            )
            uiState.completedPages = updatedCompleted
            uiState.isWirdCompleted = isCompleted || uiState.isWirdCompleted
            uiState.stage = .mushafReading
            persistProgress()
        }
    }

    public func goToPreviousPage() {
        guard !uiState.isLoading, uiState.hasPrevPageInWird else { return }
        let prevPage = uiState.wirdPages[uiState.pageIndexInWird - 1]
        loadPage(prevPage, resetToFirstReadable: false)
    }

    public func goToNextPage() {
        guard !uiState.isLoading, uiState.hasNextPageInWird else { return }
        let lastReadable = uiState.lastReadableLineOnPage
        if uiState.activeLine >= lastReadable || uiState.completedPages.contains(uiState.currentPage) {
            uiState.completedPages.insert(uiState.currentPage)
        }
        let nextPage = uiState.wirdPages[uiState.pageIndexInWird + 1]
        loadPage(nextPage, resetToFirstReadable: false)
    }

    public func goToFirstIncompletePage() {
        guard !uiState.isLoading else { return }
        guard let incomplete = uiState.wirdPages.first(where: { !uiState.completedPages.contains($0) }) else {
            return
        }
        loadPage(incomplete, resetToFirstReadable: false)
    }

    private func evaluateWirdCompletion(
        state: MushafReaderUiState,
        isCurrentPageFinished: Bool
    ) -> (Set<Int>, Bool) {
        var updated = state.completedPages
        if isCurrentPageFinished {
            updated.insert(state.currentPage)
        }
        let isCompleted = !state.wirdPages.isEmpty && state.wirdPages.allSatisfy { updated.contains($0) }
        return (updated, isCompleted)
    }

    private func checkCompletion() {
        guard !uiState.wirdPages.isEmpty else { return }
        let lastReadable = uiState.lastReadableLineOnPage
        let isCurrentPageFinished = uiState.activeLine >= lastReadable
        let (updatedCompleted, isCompleted) = evaluateWirdCompletion(
            state: uiState,
            isCurrentPageFinished: isCurrentPageFinished
        )

        if updatedCompleted != uiState.completedPages || isCompleted != uiState.isWirdCompleted {
            uiState.completedPages = updatedCompleted
            uiState.isWirdCompleted = isCompleted
        }
        persistProgress()
    }

    private func persistProgress() {
        guard uiState.alarmId >= 0 else { return }
        progressStore?.save(
            SessionReadingProgress(
                alarmId: uiState.alarmId,
                stageName: uiState.stage.rawValue,
                currentPage: uiState.currentPage,
                completedPages: uiState.completedPages,
                visitedLineByPage: visitedLineByPage
            )
        )
    }

    public func completeWird(onComplete: @escaping () -> Void) {
        guard uiState.isDismissEnabled, !uiState.isDismissedOrSnoozed else { return }
        uiState.isDismissedOrSnoozed = true
        onSilenceAudio()
        progressStore?.clear()

        let alarmId = uiState.alarmId
        Task { [weak self] in
            guard let self else {
                onComplete()
                return
            }
            if alarmId >= 0, let currentAlarm = self.alarmRepository.getAlarmById(alarmId) {
                let willRepeat = !currentAlarm.repeatDays.isEmpty
                var advancedAlarm = currentAlarm.advanceToNextWird().copyWith(isEnabled: willRepeat)

                if advancedAlarm.isAyahMode, let surahNum = advancedAlarm.wirdSurahNumber {
                    let nextStartAyah = advancedAlarm.startAyah ?? 1
                    let nextEndAyah = advancedAlarm.endAyah ?? nextStartAyah
                    if let nextRange = await self.lineBoundsRepository.getAyahRangeInfo(
                        surahNumber: surahNum,
                        startAyah: nextStartAyah,
                        endAyah: nextEndAyah
                    ) {
                        let nextPageCount = QuranStructure.computePageCount(
                            startPage: nextRange.startPage,
                            endPage: nextRange.endPage
                        )
                        advancedAlarm = advancedAlarm.copyWith(
                            targetPage: nextRange.startPage,
                            startPage: nextRange.startPage,
                            pageCount: nextPageCount
                        )
                    }
                }

                self.alarmRepository.saveAlarm(advancedAlarm)
                if willRepeat {
                    self.alarmScheduler.schedule(advancedAlarm)
                }
            }
            onComplete()
        }
    }

    public func snooze(minutes: Int = 5, onSnooze: @escaping () -> Void) {
        guard !uiState.isDismissedOrSnoozed else { return }
        uiState.isDismissedOrSnoozed = true
        onSilenceAudio()
        progressStore?.clear()

        if uiState.alarmId < 0 {
            onSnooze()
            return
        }

        let alarmId = uiState.alarmId
        let snoozeTime = AlarmItem.currentMillis() + Int64(minutes) * 60_000
        let cal = AlarmItem.gregorianCalendar()
        let date = Date(timeIntervalSince1970: Double(snoozeTime) / 1000.0)
        let comps = cal.dateComponents([.hour, .minute], from: date)

        let currentAlarm = alarmRepository.getAlarmById(alarmId)
        let baseAlarm = currentAlarm ?? AlarmItem(
            id: alarmId,
            hour: comps.hour ?? 5,
            minute: comps.minute ?? 0,
            repeatDays: [],
            isEnabled: true,
            isVibrationEnabled: true,
            label: uiState.alarmLabel,
            targetPage: uiState.startPage,
            startPage: uiState.startPage,
            pageCount: uiState.pageCount
        )

        let snoozeItem = baseAlarm.copyWith(
            isEnabled: true,
            snoozeTriggerMillis: .some(snoozeTime)
        )
        alarmRepository.saveAlarm(snoozeItem)
        alarmScheduler.scheduleSnooze(snoozeItem, triggerMillis: snoozeTime)
        onSnooze()
    }
}
