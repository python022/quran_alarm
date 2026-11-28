import Foundation

public enum AlarmValidationError: Error, Equatable {
    case negativeId
    case invalidHour
    case invalidMinute
    case invalidTargetPage
    case invalidStartPage
    case invalidPageCount
    case invalidVolumeLevel
    case invalidRepeatDays
    case invalidSurahNumber
    case invalidStartAyah
    case invalidDailyAyahCount
}

/// Immutable domain model representing a Quran Alarm with customizable Wird progression and audio settings.
/// Weekday constants match Gregorian Calendar.weekday (1 = Sunday ... 7 = Saturday) and Android Calendar constants.
public struct AlarmItem: Codable, Identifiable, Equatable {
    public static let maxLabelLength = 100

    public static let sunday = 1
    public static let monday = 2
    public static let tuesday = 3
    public static let wednesday = 4
    public static let thursday = 5
    public static let friday = 6
    public static let saturday = 7

    public let id: Int
    public let hour: Int
    public let minute: Int
    public let repeatDays: Set<Int>
    public let isEnabled: Bool
    public let isVibrationEnabled: Bool
    public let label: String
    public let targetPage: Int
    public let startPage: Int
    public let pageCount: Int
    public let volumeLevel: Int
    public let soundUri: String?
    public let explicitTriggerMillis: Int64?
    public let snoozeTriggerMillis: Int64?
    public let isSequential: Bool
    public let soundTitle: String?
    public let wirdSurahNumber: Int?
    public let startAyah: Int?
    public let dailyAyahCount: Int?

    public init(
        id: Int,
        hour: Int,
        minute: Int,
        repeatDays: Set<Int> = [],
        isEnabled: Bool = true,
        isVibrationEnabled: Bool = true,
        label: String = "ورد القرآن الكريم",
        targetPage: Int = 1,
        startPage: Int? = nil,
        pageCount: Int = 5,
        volumeLevel: Int = 100,
        soundUri: String? = nil,
        explicitTriggerMillis: Int64? = nil,
        snoozeTriggerMillis: Int64? = nil,
        isSequential: Bool = true,
        soundTitle: String? = nil,
        wirdSurahNumber: Int? = nil,
        startAyah: Int? = nil,
        dailyAyahCount: Int? = nil
    ) {
        self.id = id
        self.hour = hour
        self.minute = minute
        self.repeatDays = repeatDays
        self.isEnabled = isEnabled
        self.isVibrationEnabled = isVibrationEnabled
        self.label = label
        self.targetPage = targetPage
        self.startPage = startPage ?? targetPage
        self.pageCount = pageCount
        self.volumeLevel = volumeLevel
        self.soundUri = soundUri
        self.explicitTriggerMillis = explicitTriggerMillis
        self.snoozeTriggerMillis = snoozeTriggerMillis
        self.isSequential = isSequential
        self.soundTitle = soundTitle
        self.wirdSurahNumber = wirdSurahNumber
        self.startAyah = startAyah
        self.dailyAyahCount = dailyAyahCount
    }

    /// Validating factory matching Android AlarmItem init preconditions.
    public static func validated(
        id: Int,
        hour: Int,
        minute: Int,
        repeatDays: Set<Int> = [],
        isEnabled: Bool = true,
        isVibrationEnabled: Bool = true,
        label: String = "ورد القرآن الكريم",
        targetPage: Int = 1,
        startPage: Int? = nil,
        pageCount: Int = 5,
        volumeLevel: Int = 100,
        soundUri: String? = nil,
        explicitTriggerMillis: Int64? = nil,
        snoozeTriggerMillis: Int64? = nil,
        isSequential: Bool = true,
        soundTitle: String? = nil,
        wirdSurahNumber: Int? = nil,
        startAyah: Int? = nil,
        dailyAyahCount: Int? = nil
    ) throws -> AlarmItem {
        let resolvedStartPage = startPage ?? targetPage
        guard id >= 0 else { throw AlarmValidationError.negativeId }
        guard (0...23).contains(hour) else { throw AlarmValidationError.invalidHour }
        guard (0...59).contains(minute) else { throw AlarmValidationError.invalidMinute }
        guard (1...604).contains(targetPage) else { throw AlarmValidationError.invalidTargetPage }
        guard (1...604).contains(resolvedStartPage) else { throw AlarmValidationError.invalidStartPage }
        guard (1...604).contains(pageCount) else { throw AlarmValidationError.invalidPageCount }
        guard (1...100).contains(volumeLevel) else { throw AlarmValidationError.invalidVolumeLevel }
        guard repeatDays.allSatisfy({ (1...7).contains($0) }) else { throw AlarmValidationError.invalidRepeatDays }

        if let surahNum = wirdSurahNumber {
            guard (1...QuranStructure.totalSurahs).contains(surahNum) else {
                throw AlarmValidationError.invalidSurahNumber
            }
            let maxAyahs = QuranStructure.getSurahByNumber(surahNum).ayahCount
            if let sAyah = startAyah, !(1...maxAyahs).contains(sAyah) {
                throw AlarmValidationError.invalidStartAyah
            }
            if let dAyah = dailyAyahCount, !(1...maxAyahs).contains(dAyah) {
                throw AlarmValidationError.invalidDailyAyahCount
            }
        }

        return AlarmItem(
            id: id,
            hour: hour,
            minute: minute,
            repeatDays: repeatDays,
            isEnabled: isEnabled,
            isVibrationEnabled: isVibrationEnabled,
            label: label,
            targetPage: targetPage,
            startPage: resolvedStartPage,
            pageCount: pageCount,
            volumeLevel: volumeLevel,
            soundUri: soundUri,
            explicitTriggerMillis: explicitTriggerMillis,
            snoozeTriggerMillis: snoozeTriggerMillis,
            isSequential: isSequential,
            soundTitle: soundTitle,
            wirdSurahNumber: wirdSurahNumber,
            startAyah: startAyah,
            dailyAyahCount: dailyAyahCount
        )
    }

    /// Convenience constructor from an epoch millisecond timestamp.
    public init(
        id: Int,
        timeInMillis: Int64,
        label: String = "ورد القرآن الكريم",
        isEnabled: Bool = true,
        targetPage: Int = 1
    ) {
        let cal = Self.gregorianCalendar()
        let date = Date(timeIntervalSince1970: Double(timeInMillis) / 1000.0)
        let comps = cal.dateComponents([.hour, .minute], from: date)
        self.init(
            id: id,
            hour: comps.hour ?? 5,
            minute: comps.minute ?? 0,
            repeatDays: [],
            isEnabled: isEnabled,
            isVibrationEnabled: true,
            label: label,
            targetPage: targetPage,
            startPage: targetPage,
            pageCount: 5,
            volumeLevel: 100,
            soundUri: nil,
            explicitTriggerMillis: timeInMillis
        )
    }

    public static func currentMillis() -> Int64 {
        Int64((Date().timeIntervalSince1970 * 1000.0).rounded())
    }

    public static func gregorianCalendar(timeZone: TimeZone = .current) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        return cal
    }

    public var isAyahMode: Bool {
        wirdSurahNumber != nil && dailyAyahCount != nil
    }

    public var endAyah: Int? {
        QuranStructure.computeEndAyah(
            surahNumber: wirdSurahNumber,
            startAyah: startAyah,
            dailyAyahCount: dailyAyahCount
        )
    }

    public var endPage: Int {
        QuranStructure.computeEndPage(startPage: startPage, pageCount: pageCount)
    }

    public func copyWith(
        id: Int? = nil,
        hour: Int? = nil,
        minute: Int? = nil,
        repeatDays: Set<Int>? = nil,
        isEnabled: Bool? = nil,
        isVibrationEnabled: Bool? = nil,
        label: String? = nil,
        targetPage: Int? = nil,
        startPage: Int? = nil,
        pageCount: Int? = nil,
        volumeLevel: Int? = nil,
        soundUri: String?? = nil,
        explicitTriggerMillis: Int64?? = nil,
        snoozeTriggerMillis: Int64?? = nil,
        isSequential: Bool? = nil,
        soundTitle: String?? = nil,
        wirdSurahNumber: Int?? = nil,
        startAyah: Int?? = nil,
        dailyAyahCount: Int?? = nil
    ) -> AlarmItem {
        AlarmItem(
            id: id ?? self.id,
            hour: hour ?? self.hour,
            minute: minute ?? self.minute,
            repeatDays: repeatDays ?? self.repeatDays,
            isEnabled: isEnabled ?? self.isEnabled,
            isVibrationEnabled: isVibrationEnabled ?? self.isVibrationEnabled,
            label: label ?? self.label,
            targetPage: targetPage ?? self.targetPage,
            startPage: startPage ?? self.startPage,
            pageCount: pageCount ?? self.pageCount,
            volumeLevel: volumeLevel ?? self.volumeLevel,
            soundUri: soundUri ?? self.soundUri,
            explicitTriggerMillis: explicitTriggerMillis ?? self.explicitTriggerMillis,
            snoozeTriggerMillis: snoozeTriggerMillis ?? self.snoozeTriggerMillis,
            isSequential: isSequential ?? self.isSequential,
            soundTitle: soundTitle ?? self.soundTitle,
            wirdSurahNumber: wirdSurahNumber ?? self.wirdSurahNumber,
            startAyah: startAyah ?? self.startAyah,
            dailyAyahCount: dailyAyahCount ?? self.dailyAyahCount
        )
    }

    public func advanceToNextWird() -> AlarmItem {
        guard isSequential else { return self }

        if isAyahMode, let surahNum = wirdSurahNumber {
            let surah = QuranStructure.getSurahByNumber(surahNum)
            let currentStart = min(max(startAyah ?? 1, 1), surah.ayahCount)
            let currentEnd = endAyah ?? currentStart
            let nextStartAyah = currentEnd >= surah.ayahCount ? 1 : (currentEnd + 1)
            return copyWith(
                explicitTriggerMillis: .some(nil),
                snoozeTriggerMillis: .some(nil),
                startAyah: .some(nextStartAyah)
            )
        }

        let safeStart = min(max(startPage, 1), 604)
        let safeCount = max(pageCount, 1)
        let nextStart = QuranStructure.positiveMod(safeStart + safeCount - 1, 604) + 1
        return copyWith(
            targetPage: nextStart,
            startPage: nextStart,
            explicitTriggerMillis: .some(nil),
            snoozeTriggerMillis: .some(nil)
        )
    }

    public func isOneShotExpired(nowMillis: Int64 = AlarmItem.currentMillis()) -> Bool {
        guard repeatDays.isEmpty else { return false }
        if let explicit = explicitTriggerMillis {
            return explicit <= nowMillis
        }
        return false
    }

    public func getEffectiveNextTriggerMillis(fromMillis: Int64 = AlarmItem.currentMillis()) -> Int64 {
        let primary = getNextTriggerMillis(fromMillis: fromMillis)
        if let snooze = snoozeTriggerMillis, snooze > fromMillis {
            return min(primary, snooze)
        }
        return primary
    }

    public var timeInMillis: Int64 {
        getNextTriggerMillis()
    }

    public func getNextTriggerMillis(fromMillis: Int64 = AlarmItem.currentMillis()) -> Int64 {
        if let explicit = explicitTriggerMillis, explicit > fromMillis {
            return explicit
        }

        let cal = Self.gregorianCalendar()
        let nowDate = Date(timeIntervalSince1970: Double(fromMillis) / 1000.0)
        var baseComps = cal.dateComponents([.year, .month, .day], from: nowDate)
        baseComps.hour = hour
        baseComps.minute = minute
        baseComps.second = 0
        baseComps.nanosecond = 0

        let todayTargetDate = cal.date(from: baseComps) ?? nowDate

        if repeatDays.isEmpty {
            var target = todayTargetDate
            if Int64((target.timeIntervalSince1970 * 1000.0).rounded()) <= fromMillis {
                let tomorrow = cal.date(byAdding: .day, value: 1, to: todayTargetDate) ?? todayTargetDate.addingTimeInterval(86_400)
                target = applyTimeOfDay(to: tomorrow, calendar: cal)
            }
            return Int64((target.timeIntervalSince1970 * 1000.0).rounded())
        }

        for dayOffset in 0...7 {
            if let shifted = cal.date(byAdding: .day, value: dayOffset, to: todayTargetDate) {
                let candidate = applyTimeOfDay(to: shifted, calendar: cal)
                let weekday = cal.component(.weekday, from: candidate)
                if repeatDays.contains(weekday) {
                    let candidateMillis = Int64((candidate.timeIntervalSince1970 * 1000.0).rounded())
                    if candidateMillis > fromMillis {
                        return candidateMillis
                    }
                }
            }
        }

        let fallback = cal.date(byAdding: .day, value: 7, to: todayTargetDate) ?? todayTargetDate.addingTimeInterval(7 * 86_400)
        let adjustedFallback = applyTimeOfDay(to: fallback, calendar: cal)
        return Int64((adjustedFallback.timeIntervalSince1970 * 1000.0).rounded())
    }

    private func applyTimeOfDay(to date: Date, calendar: Calendar) -> Date {
        var comps = calendar.dateComponents([.year, .month, .day], from: date)
        comps.hour = hour
        comps.minute = minute
        comps.second = 0
        comps.nanosecond = 0
        return calendar.date(from: comps) ?? date
    }
}
