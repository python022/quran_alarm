import Foundation

public protocol LineBoundsRepository {
    func getLinesForPage(_ pageNumber: Int) async -> [LineRect]
    func getLinesForPageSync(_ pageNumber: Int) -> [LineRect]
    func getPageLineCount(_ pageNumber: Int) -> Int
    func getAyahRangeInfo(surahNumber: Int, startAyah: Int, endAyah: Int) async -> AyahPageRange?
    func getAyahLineRangeOnPage(
        surahNumber: Int,
        startAyah: Int,
        endAyah: Int,
        pageNumber: Int
    ) async -> ClosedRange<Int>?
}

public final class AyahInfoLineBoundsRepositoryImpl: LineBoundsRepository {
    private let database: AyahInfoDatabase

    public init(database: AyahInfoDatabase = .shared) {
        self.database = database
    }

    public func getLinesForPage(_ pageNumber: Int) async -> [LineRect] {
        await Task.detached(priority: .userInitiated) { [database] in
            database.getLinesForPage(pageNumber)
        }.value
    }

    public func getLinesForPageSync(_ pageNumber: Int) -> [LineRect] {
        database.getLinesForPage(pageNumber)
    }

    public func getPageLineCount(_ pageNumber: Int) -> Int {
        database.getPageLineCount(pageNumber: pageNumber)
    }

    public func getAyahRangeInfo(surahNumber: Int, startAyah: Int, endAyah: Int) async -> AyahPageRange? {
        await Task.detached(priority: .userInitiated) { [database] in
            database.getAyahRangeInfo(surahNumber: surahNumber, startAyah: startAyah, endAyah: endAyah)
        }.value
    }

    public func getAyahLineRangeOnPage(
        surahNumber: Int,
        startAyah: Int,
        endAyah: Int,
        pageNumber: Int
    ) async -> ClosedRange<Int>? {
        await Task.detached(priority: .userInitiated) { [database] in
            database.getAyahLineRangeOnPage(
                surahNumber: surahNumber,
                startAyah: startAyah,
                endAyah: endAyah,
                pageNumber: pageNumber
            )
        }.value
    }
}
