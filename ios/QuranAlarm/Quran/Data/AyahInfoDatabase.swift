import Foundation
import SQLite3

/// Manages access to the bundled ayahinfo.db SQLite database.
/// Provides queries for glyph and line coordinates with strict monotonic separation.
public final class AyahInfoDatabase {
    public static let assetSubdirectory = "quran"
    public static let dbFileName = "ayahinfo"
    public static let dbExtension = "db"

    public static let shared = AyahInfoDatabase()

    private let lock = NSRecursiveLock()
    private let explicitDBURL: URL?
    private let bundle: Bundle
    private var dbHandle: OpaquePointer?

    public init(dbURL: URL? = nil, bundle: Bundle = .main) {
        self.explicitDBURL = dbURL
        self.bundle = bundle
    }

    deinit {
        close()
    }

    public func close() {
        lock.lock()
        defer { lock.unlock() }
        if let handle = dbHandle {
            sqlite3_close(handle)
            dbHandle = nil
        }
    }

    private func resolveDatabasePath() throws -> String {
        if let explicitDBURL {
            return explicitDBURL.path
        }

        if let bundledURL = bundle.url(
            forResource: Self.dbFileName,
            withExtension: Self.dbExtension,
            subdirectory: Self.assetSubdirectory
        ) ?? bundle.url(forResource: Self.dbFileName, withExtension: Self.dbExtension) {
            return bundledURL.path
        }

        throw NSError(
            domain: "com.quran.alarm.db",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "ayahinfo.db not found in bundle"]
        )
    }

    private func ensureCopiedToAppSupport(from sourcePath: String) throws -> String {
        let fm = FileManager.default
        let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fm.temporaryDirectory
        let targetDir = appSupport.appendingPathComponent("quran", isDirectory: true)
        try fm.createDirectory(at: targetDir, withIntermediateDirectories: true)
        let targetURL = targetDir.appendingPathComponent("ayahinfo.db")

        let needsCopy: Bool
        if fm.fileExists(atPath: targetURL.path) {
            let attrs = try? fm.attributesOfItem(atPath: targetURL.path)
            let size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0
            needsCopy = size < 100_000
        } else {
            needsCopy = true
        }

        if needsCopy {
            let tmpURL = targetDir.appendingPathComponent("ayahinfo.db.tmp")
            if fm.fileExists(atPath: tmpURL.path) {
                try? fm.removeItem(at: tmpURL)
            }
            try fm.copyItem(atPath: sourcePath, toPath: tmpURL.path)
            if fm.fileExists(atPath: targetURL.path) {
                try? fm.removeItem(at: targetURL)
            }
            try fm.moveItem(at: tmpURL, to: targetURL)
        }
        return targetURL.path
    }

    private func getDb() throws -> OpaquePointer {
        lock.lock()
        defer { lock.unlock() }

        if let existing = dbHandle {
            return existing
        }

        let path = try resolveDatabasePath()
        var handle: OpaquePointer?
        let flags = SQLITE_OPEN_READONLY | SQLITE_OPEN_FULLMUTEX
        if sqlite3_open_v2(path, &handle, flags, nil) == SQLITE_OK, let opened = handle {
            dbHandle = opened
            return opened
        }
        if let failedHandle = handle {
            sqlite3_close(failedHandle)
            handle = nil
        }

        let fallbackPath = try ensureCopiedToAppSupport(from: path)
        if sqlite3_open_v2(fallbackPath, &handle, flags, nil) == SQLITE_OK, let opened = handle {
            dbHandle = opened
            return opened
        }
        let message = handle.flatMap { String(cString: sqlite3_errmsg($0)) } ?? "Unable to open SQLite database"
        if let failedHandle = handle {
            sqlite3_close(failedHandle)
        }
        throw NSError(
            domain: "com.quran.alarm.db",
            code: 2,
            userInfo: [NSLocalizedDescriptionKey: message]
        )
    }

    public func getPageLineCount(pageNumber: Int) -> Int {
        let safePage = min(max(pageNumber, 1), 604)
        return (1...2).contains(safePage) ? 8 : 15
    }

    private func resolveMissingLineType(page: Int, lineNumber: Int, runSize: Int, indexInRun: Int) -> LineType {
        if runSize == 2 {
            return indexInRun == 0 ? .surahHeader : .basmalah
        }
        if lineNumber == 1 && (page == 1 || page == 187) {
            return .surahHeader
        }
        if lineNumber == 1 {
            return .basmalah
        }
        return .surahHeader
    }

    public func getLinesForPage(_ pageNumber: Int) -> [LineRect] {
        lock.lock()
        defer { lock.unlock() }

        let page = min(max(pageNumber, 1), 604)
        var rawLines: [Int: (minX: Int, maxX: Int, minY: Int, maxY: Int)] = [:]

        if let db = try? getDb() {
            let sql = """
            SELECT line_number, MIN(min_x), MAX(max_x), MIN(min_y), MAX(max_y)
            FROM glyphs WHERE page_number = ? GROUP BY line_number ORDER BY line_number ASC
            """
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let statement = stmt {
                defer { sqlite3_finalize(statement) }
                sqlite3_bind_int(statement, 1, Int32(page))
                while sqlite3_step(statement) == SQLITE_ROW {
                    let lineNum = Int(sqlite3_column_int(statement, 0))
                    let minX = Int(sqlite3_column_int(statement, 1))
                    let maxX = Int(sqlite3_column_int(statement, 2))
                    let minY = Int(sqlite3_column_int(statement, 3))
                    let maxY = Int(sqlite3_column_int(statement, 4))
                    rawLines[lineNum] = (minX, maxX, minY, maxY)
                }
            }
        }

        let avgMinX: Int
        let avgMaxX: Int
        let avgHeight: Int

        if !rawLines.isEmpty {
            let values = Array(rawLines.values)
            avgMinX = values.map(\.minX).reduce(0, +) / values.count
            avgMaxX = values.map(\.maxX).reduce(0, +) / values.count
            let heightsSum = values.map { $0.maxY - $0.minY }.reduce(0, +)
            avgHeight = max(20, heightsSum / values.count)
        } else {
            avgMinX = 90
            avgMaxX = 1170
            avgHeight = 80
        }

        var resolved: [Int: LineRect] = [:]
        let targetLines = getPageLineCount(pageNumber: page)

        for l in 1...targetLines {
            if let existing = rawLines[l] {
                let sameX = existing.minX == existing.maxX
                resolved[l] = LineRect(
                    pageNumber: page,
                    lineNumber: l,
                    minX: sameX ? avgMinX : existing.minX,
                    maxX: sameX ? avgMaxX : existing.maxX,
                    minY: existing.minY,
                    maxY: existing.maxY,
                    lineType: .ayahText
                )
            }
        }

        let missing = (1...targetLines).filter { resolved[$0] == nil }
        var runs: [[Int]] = []
        if !missing.isEmpty {
            var currentRun = [missing[0]]
            for idx in 1..<missing.count {
                let m = missing[idx]
                if m == (currentRun.last ?? 0) + 1 {
                    currentRun.append(m)
                } else {
                    runs.append(currentRun)
                    currentRun = [m]
                }
            }
            runs.append(currentRun)
        }

        for run in runs {
            let k = run.count
            let lStart = run[0]
            let lEnd = run[run.count - 1]
            let prevL = resolved[lStart - 1] != nil ? (lStart - 1) : nil
            let nextL = resolved[lEnd + 1] != nil ? (lEnd + 1) : nil

            if let prev = prevL, let next = nextL,
               let prevRect = resolved[prev], let nextRect = resolved[next] {
                let gapTop = prevRect.maxY
                let gapBot = nextRect.minY
                let gapH = max(k * 10, gapBot - gapTop)
                let slotH = gapH / k
                for (idx, l) in run.enumerated() {
                    let margin = min(6, max(1, slotH / 10))
                    let y1 = gapTop + idx * slotH + margin
                    var y2 = gapTop + (idx + 1) * slotH - margin
                    if y2 <= y1 { y2 = y1 + 1 }
                    let lineType = resolveMissingLineType(page: page, lineNumber: l, runSize: k, indexInRun: idx)
                    resolved[l] = LineRect(
                        pageNumber: page,
                        lineNumber: l,
                        minX: avgMinX,
                        maxX: avgMaxX,
                        minY: y1,
                        maxY: y2,
                        lineType: lineType
                    )
                }
            } else if let next = nextL, let nextRect = resolved[next] {
                let nextTop = nextRect.minY
                let availH = max(k * 10, nextTop - 25)
                let slotH = min(avgHeight, availH / k)
                for (idx, l) in run.enumerated() {
                    let stepsFromNext = k - idx
                    var y2 = nextTop - 10 - (stepsFromNext - 1) * (slotH + 10)
                    let y1 = max(15, y2 - slotH)
                    if y2 <= y1 { y2 = y1 + 1 }
                    let lineType = resolveMissingLineType(page: page, lineNumber: l, runSize: k, indexInRun: idx)
                    resolved[l] = LineRect(
                        pageNumber: page,
                        lineNumber: l,
                        minX: avgMinX,
                        maxX: avgMaxX,
                        minY: y1,
                        maxY: y2,
                        lineType: lineType
                    )
                }
            } else if let prev = prevL, let prevRect = resolved[prev] {
                let prevBot = prevRect.maxY
                let availH = max(k * 10, 2030 - prevBot)
                let slotH = min(avgHeight, availH / k)
                for (idx, l) in run.enumerated() {
                    let y1 = prevBot + 10 + idx * (slotH + 10)
                    var y2 = min(2035, y1 + slotH)
                    if y2 <= y1 { y2 = y1 + 1 }
                    let lineType = resolveMissingLineType(page: page, lineNumber: l, runSize: k, indexInRun: idx)
                    resolved[l] = LineRect(
                        pageNumber: page,
                        lineNumber: l,
                        minX: avgMinX,
                        maxX: avgMaxX,
                        minY: y1,
                        maxY: y2,
                        lineType: lineType
                    )
                }
            } else {
                let slotH = avgHeight
                for (idx, l) in run.enumerated() {
                    let y1 = 30 + idx * (slotH + 15)
                    let lineType = resolveMissingLineType(page: page, lineNumber: l, runSize: k, indexInRun: idx)
                    resolved[l] = LineRect(
                        pageNumber: page,
                        lineNumber: l,
                        minX: avgMinX,
                        maxX: avgMaxX,
                        minY: y1,
                        maxY: y1 + slotH,
                        lineType: lineType
                    )
                }
            }
        }

        // Strict Monotonic Separation Constraint
        if targetLines > 1 {
            for i in 1..<targetLines {
                guard let curr = resolved[i], let next = resolved[i + 1] else { continue }
                if curr.maxY >= next.minY {
                    let midY = (curr.maxY + next.minY) / 2
                    resolved[i] = LineRect(
                        pageNumber: curr.pageNumber,
                        lineNumber: curr.lineNumber,
                        minX: curr.minX,
                        maxX: curr.maxX,
                        minY: curr.minY,
                        maxY: midY,
                        lineType: curr.lineType
                    )
                    resolved[i + 1] = LineRect(
                        pageNumber: next.pageNumber,
                        lineNumber: next.lineNumber,
                        minX: next.minX,
                        maxX: next.maxX,
                        minY: midY + 1,
                        maxY: next.maxY,
                        lineType: next.lineType
                    )
                }
                if let fixedCurr = resolved[i], fixedCurr.minY >= fixedCurr.maxY {
                    resolved[i] = LineRect(
                        pageNumber: fixedCurr.pageNumber,
                        lineNumber: fixedCurr.lineNumber,
                        minX: fixedCurr.minX,
                        maxX: fixedCurr.maxX,
                        minY: fixedCurr.minY,
                        maxY: fixedCurr.minY + 1,
                        lineType: fixedCurr.lineType
                    )
                }
                if let fixedNext = resolved[i + 1], fixedNext.minY >= fixedNext.maxY {
                    resolved[i + 1] = LineRect(
                        pageNumber: fixedNext.pageNumber,
                        lineNumber: fixedNext.lineNumber,
                        minX: fixedNext.minX,
                        maxX: fixedNext.maxX,
                        minY: fixedNext.minY,
                        maxY: fixedNext.minY + 1,
                        lineType: fixedNext.lineType
                    )
                }
            }
        }

        return (1...targetLines).compactMap { resolved[$0] }
    }

    public func getAyahRangeInfo(surahNumber: Int, startAyah: Int, endAyah: Int) -> AyahPageRange? {
        lock.lock()
        defer { lock.unlock() }

        guard let db = try? getDb() else { return nil }
        let sql = """
        SELECT MIN(page_number), MAX(page_number) FROM glyphs
        WHERE sura_number = ? AND ayah_number BETWEEN ? AND ?
        """
        var stmt: OpaquePointer?
        var minPage: Int?
        var maxPage: Int?

        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let statement = stmt {
            defer { sqlite3_finalize(statement) }
            sqlite3_bind_int(statement, 1, Int32(surahNumber))
            sqlite3_bind_int(statement, 2, Int32(startAyah))
            sqlite3_bind_int(statement, 3, Int32(endAyah))
            if sqlite3_step(statement) == SQLITE_ROW,
               sqlite3_column_type(statement, 0) != SQLITE_NULL,
               sqlite3_column_type(statement, 1) != SQLITE_NULL {
                minPage = min(max(Int(sqlite3_column_int(statement, 0)), 1), 604)
                maxPage = min(max(Int(sqlite3_column_int(statement, 1)), 1), 604)
            }
        }

        guard let startP = minPage, let endP = maxPage else { return nil }
        let startLine = getAyahLineRangeOnPage(
            surahNumber: surahNumber,
            startAyah: startAyah,
            endAyah: endAyah,
            pageNumber: startP
        )?.lowerBound ?? 1
        let endLine = getAyahLineRangeOnPage(
            surahNumber: surahNumber,
            startAyah: startAyah,
            endAyah: endAyah,
            pageNumber: endP
        )?.upperBound ?? getPageLineCount(pageNumber: endP)

        return AyahPageRange(startPage: startP, endPage: endP, startLine: startLine, endLine: endLine)
    }

    public func getAyahLineRangeOnPage(
        surahNumber: Int,
        startAyah: Int,
        endAyah: Int,
        pageNumber: Int
    ) -> ClosedRange<Int>? {
        lock.lock()
        defer { lock.unlock() }

        let page = min(max(pageNumber, 1), 604)
        guard let db = try? getDb() else { return nil }

        let sql = """
        SELECT MIN(line_number), MAX(line_number) FROM glyphs
        WHERE sura_number = ? AND ayah_number BETWEEN ? AND ? AND page_number = ?
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let statement = stmt {
            defer { sqlite3_finalize(statement) }
            sqlite3_bind_int(statement, 1, Int32(surahNumber))
            sqlite3_bind_int(statement, 2, Int32(startAyah))
            sqlite3_bind_int(statement, 3, Int32(endAyah))
            sqlite3_bind_int(statement, 4, Int32(page))

            if sqlite3_step(statement) == SQLITE_ROW,
               sqlite3_column_type(statement, 0) != SQLITE_NULL,
               sqlite3_column_type(statement, 1) != SQLITE_NULL {
                let pageLines = getPageLineCount(pageNumber: page)
                let rawMinLine = min(max(Int(sqlite3_column_int(statement, 0)), 1), pageLines)
                let maxLine = min(max(Int(sqlite3_column_int(statement, 1)), rawMinLine), pageLines)

                let minLine: Int
                if startAyah == 1 && surahNumber != 1 && surahNumber != 9 && rawMinLine > 1 {
                    let lines = getLinesForPage(page)
                    let prevIdx = rawMinLine - 2
                    if lines.indices.contains(prevIdx), lines[prevIdx].lineType == .basmalah {
                        minLine = rawMinLine - 1
                    } else {
                        minLine = rawMinLine
                    }
                } else {
                    minLine = rawMinLine
                }

                return minLine...maxLine
            }
        }
        return nil
    }
}
