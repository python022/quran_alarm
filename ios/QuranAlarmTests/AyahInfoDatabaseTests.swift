import XCTest
@testable import QuranAlarm

final class AyahInfoDatabaseTests: XCTestCase {
    private var database: AyahInfoDatabase!
    private var repository: AyahInfoLineBoundsRepositoryImpl!

    override func setUp() {
        super.setUp()
        database = AyahInfoDatabase(bundle: .main)
        repository = AyahInfoLineBoundsRepositoryImpl(database: database)
    }

    override func tearDown() {
        database.close()
        database = nil
        repository = nil
        super.tearDown()
    }

    func testAll604PagesReturnValidLinesWithoutOverlap() {
        for page in 1...604 {
            let lines = database.getLinesForPage(page)
            let expectedCount = database.getPageLineCount(pageNumber: page)
            XCTAssertEqual(lines.count, expectedCount, "Page \(page) must have \(expectedCount) lines")

            for (idx, line) in lines.enumerated() {
                XCTAssertEqual(line.lineNumber, idx + 1)
                XCTAssertEqual(line.pageNumber, page)
                XCTAssertTrue(line.isValid(), "LineRect invalid on page \(page) line \(line.lineNumber)")
                if idx < lines.count - 1 {
                    let nextLine = lines[idx + 1]
                    XCTAssertLessThan(
                        line.maxY,
                        nextLine.minY,
                        "Overlap on page \(page) between lines \(line.lineNumber) and \(nextLine.lineNumber)"
                    )
                }
            }
        }
    }

    func testAsyncRepositoryReturns15Lines() async {
        let lines = await repository.getLinesForPage(7)
        XCTAssertEqual(lines.count, 15)
        XCTAssertEqual(lines.first?.lineNumber, 1)
        XCTAssertEqual(lines.last?.lineNumber, 15)
    }

    func testHeaderGapInterpolationAndClassificationOnPage106() {
        let lines = database.getLinesForPage(106)
        XCTAssertEqual(lines.count, 15)
        XCTAssertEqual(lines[4].lineType, .ayahText)
        XCTAssertEqual(lines[5].lineType, .surahHeader)
        XCTAssertEqual(lines[6].lineType, .basmalah)
        XCTAssertEqual(lines[7].lineType, .ayahText)
        XCTAssertTrue(lines[4].isReadable)
        XCTAssertFalse(lines[5].isReadable)
        XCTAssertTrue(lines[6].isReadable)
        XCTAssertTrue(lines[7].isReadable)
    }

    func testMultiLineMissingAtTopPage2AndPage1() {
        let page2 = database.getLinesForPage(2)
        XCTAssertEqual(page2.count, 8)
        XCTAssertEqual(page2[0].lineType, .surahHeader)
        XCTAssertEqual(page2[1].lineType, .basmalah)
        XCTAssertEqual(page2[2].lineType, .ayahText)

        let page1 = database.getLinesForPage(1)
        XCTAssertEqual(page1.count, 8)
        XCTAssertEqual(page1[0].lineType, .surahHeader)
        XCTAssertFalse(page1[0].isReadable)
        XCTAssertTrue(page1[1].isReadable)
    }

    func testSurahAtTawbahPage187HasHeaderWithoutBasmalah() {
        let lines = database.getLinesForPage(187)
        XCTAssertEqual(lines.count, 15)
        XCTAssertEqual(lines[0].lineType, .surahHeader)
        XCTAssertFalse(lines[0].isReadable)
        XCTAssertEqual(lines[1].lineType, .ayahText)
        XCTAssertTrue(lines[1].isReadable)
    }

    func testPageBoundarySurahHeaderAndBasmalah() {
        let lines76 = database.getLinesForPage(76)
        XCTAssertEqual(lines76.last?.lineType, .surahHeader)
        XCTAssertFalse(lines76.last?.isReadable ?? true)

        let lines77 = database.getLinesForPage(77)
        XCTAssertEqual(lines77.first?.lineType, .basmalah)
        XCTAssertTrue(lines77.first?.isReadable ?? false)

        let lines331 = database.getLinesForPage(331)
        XCTAssertEqual(lines331.last?.lineType, .surahHeader)

        let lines332 = database.getLinesForPage(332)
        XCTAssertEqual(lines332.first?.lineType, .basmalah)
    }

    func testAyahLineRangeIncludesBasmalahAndSkipsTawbahHeader() {
        let baqarahRange = database.getAyahLineRangeOnPage(surahNumber: 2, startAyah: 1, endAyah: 5, pageNumber: 2)
        XCTAssertEqual(baqarahRange?.lowerBound, 2)

        let kawtharRange = database.getAyahLineRangeOnPage(surahNumber: 108, startAyah: 1, endAyah: 3, pageNumber: 602)
        XCTAssertNotNil(kawtharRange)
        let lines602 = database.getLinesForPage(602)
        if let firstLine = kawtharRange?.lowerBound {
            XCTAssertEqual(lines602[firstLine - 1].lineType, .basmalah)
        }

        let tawbahRange = database.getAyahLineRangeOnPage(surahNumber: 9, startAyah: 1, endAyah: 3, pageNumber: 187)
        XCTAssertEqual(tawbahRange?.lowerBound, 2)
    }
}
