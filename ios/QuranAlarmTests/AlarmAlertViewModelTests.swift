import XCTest
@testable import QuranAlarm

@MainActor
final class AlarmAlertViewModelTests: XCTestCase {

    func testStrictWirdGuardLocksDismissUntilAllPagesAndLinesRead() async {
        let suiteName = "test.alert.vm.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let repo = AlarmRepositoryImpl(userDefaults: defaults)
        let initialAlarm = AlarmItem(
            id: 7,
            hour: 5,
            minute: 0,
            repeatDays: [AlarmItem.sunday],
            isEnabled: true,
            startPage: 1,
            pageCount: 2,
            isSequential: true
        )
        repo.saveAlarm(initialAlarm)

        let db = AyahInfoDatabase(bundle: .main)
        defer { db.close() }
        let boundsRepo = AyahInfoLineBoundsRepositoryImpl(database: db)

        var silenced = false
        let vm = AlarmAlertViewModel(
            lineBoundsRepository: boundsRepo,
            pageManager: nil,
            alarmRepository: repo,
            alarmScheduler: NoOpAlarmScheduler(),
            progressStore: AlarmSessionStore(userDefaults: defaults),
            onSilenceAudio: { silenced = true }
        )

        await vm.initializeAndLoadSyncForTesting(
            alarmId: 7,
            alarmLabel: "الفجر",
            rawStartPage: 1,
            rawPageCount: 2
        )

        XCTAssertEqual(vm.uiState.stage, .ringing)
        XCTAssertFalse(vm.uiState.isDismissEnabled)
        XCTAssertEqual(vm.uiState.wirdPages, [1, 2])
        XCTAssertEqual(vm.uiState.activeLine, 2) // Page 1 line 1 is SURAH_HEADER

        vm.startReadingWird()
        XCTAssertTrue(silenced)
        XCTAssertEqual(vm.uiState.stage, .mushafReading)
        XCTAssertFalse(vm.uiState.isDismissEnabled)

        // Complete page 1 (lines 2..8)
        for line in 3...8 {
            vm.onLineClicked(line)
        }
        XCTAssertTrue(vm.uiState.completedPages.contains(1))
        XCTAssertFalse(vm.uiState.isDismissEnabled, "Page 2 still unread; dismiss must remain locked")

        // Advance to page 2 (line 1-2 are SURAH_HEADER & BASMALAH -> first readable is line 2)
        vm.goToNextPage()
        await vm.awaitPendingPageLoad()
        XCTAssertEqual(vm.uiState.currentPage, 2)
        XCTAssertFalse(vm.uiState.isDismissEnabled)

        vm.onLineClicked(vm.uiState.lastReadableLineOnPage)
        XCTAssertTrue(vm.uiState.isWirdCompleted)
        XCTAssertTrue(vm.uiState.isDismissEnabled)

        let completedExpectation = expectation(description: "completeWird finishes")
        vm.completeWird {
            completedExpectation.fulfill()
        }
        await fulfillment(of: [completedExpectation], timeout: 2.0)

        let updatedAlarm = repo.getAlarmById(7)
        XCTAssertEqual(updatedAlarm?.startPage, 3)
        XCTAssertTrue(updatedAlarm?.isEnabled ?? false)
    }
}
