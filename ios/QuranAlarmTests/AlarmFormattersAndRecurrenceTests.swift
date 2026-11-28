import XCTest
@testable import QuranAlarm

final class AlarmFormattersAndRecurrenceTests: XCTestCase {

    func testArabicPluralizationAndTimeFormatting() {
        XCTAssertEqual(AlarmUiFormatters.formatPagesCount(1), "صفحة واحدة")
        XCTAssertEqual(AlarmUiFormatters.formatPagesCount(2), "صفحتان")
        XCTAssertEqual(AlarmUiFormatters.formatPagesCount(5), "5 صفحات")
        XCTAssertEqual(AlarmUiFormatters.formatPagesCount(15), "15 صفحة")

        XCTAssertEqual(AlarmUiFormatters.formatAyahsCount(1), "آية واحدة")
        XCTAssertEqual(AlarmUiFormatters.formatAyahsCount(2), "آيتان")
        XCTAssertEqual(AlarmUiFormatters.formatAyahsCount(7), "7 آيات")

        XCTAssertEqual(AlarmUiFormatters.formatDaysCount(1), "يوم واحد")
        XCTAssertEqual(AlarmUiFormatters.formatDaysCount(2), "يومين")
        XCTAssertEqual(AlarmUiFormatters.formatDaysCount(3), "3 أيام")

        XCTAssertEqual(AlarmUiFormatters.formatDigits12HourPadded(hour24: 0, minute: 5), "12:05")
        XCTAssertEqual(AlarmUiFormatters.getPeriodLabel(0), "ص")
        XCTAssertEqual(AlarmUiFormatters.formatDigits12HourPadded(hour24: 17, minute: 30), "05:30")
        XCTAssertEqual(AlarmUiFormatters.getPeriodLabel(17), "م")
        XCTAssertEqual(AlarmUiFormatters.formatTime12Hour(hour: 5, minute: 0), "5:00 ص")
    }

    func testAlarmValidationRejectsInvalidBounds() throws {
        XCTAssertThrowsError(try AlarmItem.validated(id: -1, hour: 5, minute: 0))
        XCTAssertThrowsError(try AlarmItem.validated(id: 1, hour: 24, minute: 0))
        XCTAssertThrowsError(try AlarmItem.validated(id: 1, hour: 5, minute: 60))
        XCTAssertThrowsError(try AlarmItem.validated(id: 1, hour: 5, minute: 0, targetPage: 0))
        XCTAssertThrowsError(try AlarmItem.validated(id: 1, hour: 5, minute: 0, targetPage: 605))
        XCTAssertThrowsError(try AlarmItem.validated(id: 1, hour: 5, minute: 0, repeatDays: [-1]))
        XCTAssertThrowsError(try AlarmItem.validated(id: 1, hour: 5, minute: 0, repeatDays: [8]))

        let valid = try AlarmItem.validated(id: 1, hour: 5, minute: 15, targetPage: 604)
        XCTAssertEqual(valid.targetPage, 604)
    }

    func testSingleShotAndRepeatingGregorianRecurrence() {
        var cal = AlarmItem.gregorianCalendar()
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 11
        comps.day = 3 // Tuesday
        comps.hour = 10
        comps.minute = 0
        comps.second = 0
        let morningDate = cal.date(from: comps)!
        let morningMillis = Int64((morningDate.timeIntervalSince1970 * 1000.0).rounded())

        let sameDayAlarm = AlarmItem(id: 1, hour: 10, minute: 30, repeatDays: [])
        let nextToday = sameDayAlarm.getNextTriggerMillis(fromMillis: morningMillis)
        let expectedToday = cal.date(bySettingHour: 10, minute: 30, second: 0, of: morningDate)!
        XCTAssertEqual(nextToday, Int64((expectedToday.timeIntervalSince1970 * 1000.0).rounded()))

        // Repeating on Thursday (weekday = 5) at 06:00
        let thursdayAlarm = AlarmItem(id: 2, hour: 6, minute: 0, repeatDays: [AlarmItem.thursday])
        let thursdayTrigger = thursdayAlarm.getNextTriggerMillis(fromMillis: morningMillis)
        let triggerDate = Date(timeIntervalSince1970: Double(thursdayTrigger) / 1000.0)
        XCTAssertEqual(cal.component(.weekday, from: triggerDate), AlarmItem.thursday)
        XCTAssertEqual(cal.component(.hour, from: triggerDate), 6)
        XCTAssertGreaterThan(thursdayTrigger, morningMillis)
    }

    func testRepositorySaveLoadToggleAndDeletePersistence() {
        let suiteName = "test.alarm.repo.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let repo = AlarmRepositoryImpl(userDefaults: defaults)
        let alarm = AlarmItem(
            id: 42,
            hour: 5,
            minute: 15,
            repeatDays: [AlarmItem.friday],
            label: "سورة الكهف",
            targetPage: 293,
            startPage: 293,
            pageCount: 12,
            soundUri: AlarmSoundPresets.uriAdhanFajr,
            soundTitle: "أذان الفجر"
        )
        repo.saveAlarm(alarm)

        let loaded = repo.getAlarmById(42)
        XCTAssertEqual(loaded?.label, "سورة الكهف")
        XCTAssertEqual(loaded?.pageCount, 12)
        XCTAssertEqual(loaded?.soundUri, AlarmSoundPresets.uriAdhanFajr)
        XCTAssertEqual(repo.getAllAlarms().count, 1)

        repo.deleteAlarm(42)
        XCTAssertNil(repo.getAlarmById(42))
        XCTAssertTrue(repo.getAllAlarms().isEmpty)
    }
}
