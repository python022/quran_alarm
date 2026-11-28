import XCTest
@testable import QuranAlarm

final class QuranStructureAndWirdTests: XCTestCase {

    func testStandardWirdCalculatesEndPageCorrectly() {
        let alarm = AlarmItem(id: 1, hour: 5, minute: 0, startPage: 42, pageCount: 5)
        XCTAssertEqual(alarm.startPage, 42)
        XCTAssertEqual(alarm.endPage, 46)
    }

    func testAdvanceToNextWirdAdvancesWhenSequential() {
        let day1 = AlarmItem(id: 1, hour: 5, minute: 0, startPage: 42, pageCount: 5, isSequential: true)
        let day2 = day1.advanceToNextWird()
        XCTAssertEqual(day2.startPage, 47)
        XCTAssertEqual(day2.endPage, 51)

        let day3 = day2.advanceToNextWird()
        XCTAssertEqual(day3.startPage, 52)
        XCTAssertEqual(day3.endPage, 56)
    }

    func testAdvanceToNextWirdDoesNotAdvanceWhenFixed() {
        let fixed = AlarmItem(id: 10, hour: 22, minute: 30, startPage: 562, pageCount: 3, isSequential: false)
        let next = fixed.advanceToNextWird()
        XCTAssertEqual(next.startPage, 562)
        XCTAssertEqual(next.endPage, 564)
    }

    func testAdvanceToNextWirdWrapsAroundAtPage604() {
        let alarm = AlarmItem(id: 2, hour: 5, minute: 0, startPage: 601, pageCount: 5, isSequential: true)
        XCTAssertEqual(alarm.advanceToNextWird().startPage, 2)
    }

    func testSurahsJuzAndAhzabMetadataCompleteAndAccurate() {
        XCTAssertEqual(QuranStructure.surahs.count, 114)
        XCTAssertEqual(QuranStructure.juzStartPages.count, 30)
        XCTAssertEqual(QuranStructure.hizbStartPages.count, 60)

        let fatihah = QuranStructure.getSurahByNumber(1)
        XCTAssertEqual(fatihah.nameArabic, "الفاتحة")
        XCTAssertEqual(fatihah.startPage, 1)
        XCTAssertEqual(fatihah.endPage, 1)
        XCTAssertEqual(fatihah.ayahCount, 7)

        let baqarah = QuranStructure.getSurahByNumber(2)
        XCTAssertEqual(baqarah.nameArabic, "البقرة")
        XCTAssertEqual(baqarah.startPage, 2)
        XCTAssertEqual(baqarah.endPage, 49)
        XCTAssertEqual(baqarah.ayahCount, 286)

        let kahf = QuranStructure.getSurahByNumber(18)
        XCTAssertEqual(kahf.nameArabic, "الكهف")
        XCTAssertEqual(kahf.startPage, 293)
        XCTAssertEqual(kahf.endPage, 304)

        let nas = QuranStructure.getSurahByNumber(114)
        XCTAssertEqual(nas.nameArabic, "الناس")
        XCTAssertEqual(nas.startPage, 604)
        XCTAssertEqual(nas.endPage, 604)

        XCTAssertEqual(QuranStructure.getStartPageForHizb(1), 1)
        XCTAssertEqual(QuranStructure.getEndPageForHizb(1), 10)
        XCTAssertEqual(QuranStructure.getStartPageForHizb(60), 591)
        XCTAssertEqual(QuranStructure.getEndPageForHizb(60), 604)
    }

    func testPageMappingAndForecastCalculations() {
        XCTAssertEqual(QuranStructure.getSurahAtPage(2).nameArabic, "البقرة")
        XCTAssertEqual(QuranStructure.getSurahAtPage(295).nameArabic, "الكهف")
        XCTAssertEqual(QuranStructure.getSurahAtPage(604).nameArabic, "الإخلاص")
        XCTAssertEqual(QuranStructure.getSurahsAtPage(604).map(\.nameArabic), ["الإخلاص", "الفلق", "الناس"])
        XCTAssertEqual(QuranStructure.getSurahsAtPage(601).map(\.nameArabic), ["العصر", "الهمزة", "الفيل"])

        XCTAssertEqual(QuranStructure.getJuzAtPage(42), 3)
        XCTAssertEqual(QuranStructure.getHizbAtPage(42), 5)

        let forecast1 = QuranStructure.calculateKhatmaForecast(startPage: 1, dailyPages: 5)
        XCTAssertEqual(forecast1.totalPagesRemaining, 604)
        XCTAssertEqual(forecast1.daysToComplete, 121)
        XCTAssertEqual(forecast1.progressPercentage, 0, accuracy: 0.01)

        let forecast2 = QuranStructure.calculateKhatmaForecast(startPage: 303, dailyPages: 10)
        XCTAssertEqual(forecast2.totalPagesRemaining, 302)
        XCTAssertEqual(forecast2.daysToComplete, 31)
        XCTAssertEqual(forecast2.progressPercentage, 50.0, accuracy: 0.1)
    }

    func testSingleSurahAyahModeAdvancesClampsAndWraps() {
        let day1 = AlarmItem(
            id: 50,
            hour: 22,
            minute: 0,
            startPage: 562,
            pageCount: 1,
            isSequential: true,
            wirdSurahNumber: 67,
            startAyah: 1,
            dailyAyahCount: 10
        )
        XCTAssertTrue(day1.isAyahMode)
        XCTAssertEqual(day1.endAyah, 10)

        let day2 = day1.advanceToNextWird()
        XCTAssertEqual(day2.startAyah, 11)
        XCTAssertEqual(day2.endAyah, 20)

        let day3 = day2.advanceToNextWird()
        XCTAssertEqual(day3.startAyah, 21)
        XCTAssertEqual(day3.endAyah, 30)

        let day4 = day3.advanceToNextWird()
        XCTAssertEqual(day4.startAyah, 1)
        XCTAssertEqual(day4.endAyah, 10)

        let nearEnd = day1.copyWith(startAyah: .some(25))
        XCTAssertEqual(nearEnd.endAyah, 30)
        XCTAssertEqual(nearEnd.advanceToNextWird().startAyah, 1)

        let fixed = nearEnd.copyWith(isSequential: false)
        XCTAssertEqual(fixed.advanceToNextWird().startAyah, 25)
    }
}
