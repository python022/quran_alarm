import Foundation

public struct SurahInfo: Identifiable, Hashable, Codable {
    public var id: Int { number }
    public let number: Int
    public let nameArabic: String
    public let startPage: Int
    public let endPage: Int
    public let ayahCount: Int

    public init(number: Int, nameArabic: String, startPage: Int, endPage: Int, ayahCount: Int) {
        self.number = number
        self.nameArabic = nameArabic
        self.startPage = startPage
        self.endPage = endPage
        self.ayahCount = ayahCount
    }
}

public struct HizbInfo: Identifiable, Hashable, Codable {
    public var id: Int { number }
    public let number: Int
    public let startPage: Int
    public let endPage: Int
}

public struct JuzInfo: Identifiable, Hashable, Codable {
    public var id: Int { number }
    public let number: Int
    public let startPage: Int
    public let endPage: Int
}

public enum WirdSelectionMode: String, CaseIterable, Codable, Identifiable {
    case surahs = "SURAHS"
    case ahzab = "AHZAB"
    case ajzaa = "AJZAA"
    case pages = "PAGES"

    public var id: String { rawValue }

    public var titleArabic: String {
        switch self {
        case .surahs: return "بالسور"
        case .ahzab: return "بالأحزاب"
        case .ajzaa: return "بالأجزاء"
        case .pages: return "بالصفحات"
        }
    }
}

public struct KhatmaForecast: Equatable {
    public let currentStartPage: Int
    public let dailyPages: Int
    public let totalPagesRemaining: Int
    public let daysToComplete: Int
    public let progressPercentage: Float
    public let forecastSummary: String
}

public struct SurahWirdForecast: Equatable {
    public let surahName: String
    public let currentStartAyah: Int
    public let dailyAyahs: Int
    public let totalAyahs: Int
    public let daysToComplete: Int
    public let progressPercentage: Float
    public let forecastSummary: String
}

public enum QuranRangeError: Error, Equatable {
    case invertedOrOutOfBounds(startPage: Int, endPage: Int)
}

/// Comprehensive structural reference for the King Fahd Complex Madinah Mushaf (604 pages, 15 lines).
public enum QuranStructure {
    public static let totalPages = 604
    public static let totalSurahs = 114
    public static let totalJuz = 30
    public static let totalAhzab = 60

    public static func positiveMod(_ a: Int, _ m: Int) -> Int {
        let r = a % m
        return r >= 0 ? r : r + m
    }

    public static let surahs: [SurahInfo] = [
        SurahInfo(number: 1, nameArabic: "الفاتحة", startPage: 1, endPage: 1, ayahCount: 7),
        SurahInfo(number: 2, nameArabic: "البقرة", startPage: 2, endPage: 49, ayahCount: 286),
        SurahInfo(number: 3, nameArabic: "آل عمران", startPage: 50, endPage: 76, ayahCount: 200),
        SurahInfo(number: 4, nameArabic: "النساء", startPage: 77, endPage: 106, ayahCount: 176),
        SurahInfo(number: 5, nameArabic: "المائدة", startPage: 106, endPage: 127, ayahCount: 120),
        SurahInfo(number: 6, nameArabic: "الأنعام", startPage: 128, endPage: 150, ayahCount: 165),
        SurahInfo(number: 7, nameArabic: "الأعراف", startPage: 151, endPage: 176, ayahCount: 206),
        SurahInfo(number: 8, nameArabic: "الأنفال", startPage: 177, endPage: 186, ayahCount: 75),
        SurahInfo(number: 9, nameArabic: "التوبة", startPage: 187, endPage: 207, ayahCount: 129),
        SurahInfo(number: 10, nameArabic: "يونس", startPage: 208, endPage: 221, ayahCount: 109),
        SurahInfo(number: 11, nameArabic: "هود", startPage: 221, endPage: 235, ayahCount: 123),
        SurahInfo(number: 12, nameArabic: "يوسف", startPage: 235, endPage: 248, ayahCount: 111),
        SurahInfo(number: 13, nameArabic: "الرعد", startPage: 249, endPage: 255, ayahCount: 43),
        SurahInfo(number: 14, nameArabic: "إبراهيم", startPage: 255, endPage: 261, ayahCount: 52),
        SurahInfo(number: 15, nameArabic: "الحجر", startPage: 262, endPage: 267, ayahCount: 99),
        SurahInfo(number: 16, nameArabic: "النحل", startPage: 267, endPage: 281, ayahCount: 128),
        SurahInfo(number: 17, nameArabic: "الإسراء", startPage: 282, endPage: 293, ayahCount: 111),
        SurahInfo(number: 18, nameArabic: "الكهف", startPage: 293, endPage: 304, ayahCount: 110),
        SurahInfo(number: 19, nameArabic: "مريم", startPage: 305, endPage: 312, ayahCount: 98),
        SurahInfo(number: 20, nameArabic: "طه", startPage: 312, endPage: 321, ayahCount: 135),
        SurahInfo(number: 21, nameArabic: "الأنبياء", startPage: 322, endPage: 331, ayahCount: 112),
        SurahInfo(number: 22, nameArabic: "الحج", startPage: 332, endPage: 341, ayahCount: 78),
        SurahInfo(number: 23, nameArabic: "المؤمنون", startPage: 342, endPage: 349, ayahCount: 118),
        SurahInfo(number: 24, nameArabic: "النور", startPage: 350, endPage: 359, ayahCount: 64),
        SurahInfo(number: 25, nameArabic: "الفرقان", startPage: 359, endPage: 366, ayahCount: 77),
        SurahInfo(number: 26, nameArabic: "الشعراء", startPage: 367, endPage: 376, ayahCount: 227),
        SurahInfo(number: 27, nameArabic: "النمل", startPage: 377, endPage: 385, ayahCount: 93),
        SurahInfo(number: 28, nameArabic: "القصص", startPage: 385, endPage: 396, ayahCount: 88),
        SurahInfo(number: 29, nameArabic: "العنكبوت", startPage: 396, endPage: 404, ayahCount: 69),
        SurahInfo(number: 30, nameArabic: "الروم", startPage: 404, endPage: 410, ayahCount: 60),
        SurahInfo(number: 31, nameArabic: "لقمان", startPage: 411, endPage: 414, ayahCount: 34),
        SurahInfo(number: 32, nameArabic: "السجدة", startPage: 415, endPage: 417, ayahCount: 30),
        SurahInfo(number: 33, nameArabic: "الأحزاب", startPage: 418, endPage: 427, ayahCount: 73),
        SurahInfo(number: 34, nameArabic: "سبأ", startPage: 428, endPage: 434, ayahCount: 54),
        SurahInfo(number: 35, nameArabic: "فاطر", startPage: 434, endPage: 440, ayahCount: 45),
        SurahInfo(number: 36, nameArabic: "يس", startPage: 440, endPage: 445, ayahCount: 83),
        SurahInfo(number: 37, nameArabic: "الصافات", startPage: 446, endPage: 452, ayahCount: 182),
        SurahInfo(number: 38, nameArabic: "ص", startPage: 453, endPage: 458, ayahCount: 88),
        SurahInfo(number: 39, nameArabic: "الزمر", startPage: 458, endPage: 467, ayahCount: 75),
        SurahInfo(number: 40, nameArabic: "غافر", startPage: 467, endPage: 476, ayahCount: 85),
        SurahInfo(number: 41, nameArabic: "فصلت", startPage: 477, endPage: 482, ayahCount: 54),
        SurahInfo(number: 42, nameArabic: "الشورى", startPage: 483, endPage: 489, ayahCount: 53),
        SurahInfo(number: 43, nameArabic: "الزخرف", startPage: 489, endPage: 495, ayahCount: 89),
        SurahInfo(number: 44, nameArabic: "الدخان", startPage: 496, endPage: 498, ayahCount: 59),
        SurahInfo(number: 45, nameArabic: "الجاثية", startPage: 499, endPage: 502, ayahCount: 37),
        SurahInfo(number: 46, nameArabic: "الأحقاف", startPage: 502, endPage: 506, ayahCount: 35),
        SurahInfo(number: 47, nameArabic: "محمد", startPage: 507, endPage: 510, ayahCount: 38),
        SurahInfo(number: 48, nameArabic: "الفتح", startPage: 511, endPage: 515, ayahCount: 29),
        SurahInfo(number: 49, nameArabic: "الحجرات", startPage: 515, endPage: 517, ayahCount: 18),
        SurahInfo(number: 50, nameArabic: "ق", startPage: 518, endPage: 520, ayahCount: 45),
        SurahInfo(number: 51, nameArabic: "الذاريات", startPage: 520, endPage: 523, ayahCount: 60),
        SurahInfo(number: 52, nameArabic: "الطور", startPage: 523, endPage: 525, ayahCount: 49),
        SurahInfo(number: 53, nameArabic: "النجم", startPage: 526, endPage: 528, ayahCount: 62),
        SurahInfo(number: 54, nameArabic: "القمر", startPage: 528, endPage: 531, ayahCount: 55),
        SurahInfo(number: 55, nameArabic: "الرحمن", startPage: 531, endPage: 534, ayahCount: 78),
        SurahInfo(number: 56, nameArabic: "الواقعة", startPage: 534, endPage: 537, ayahCount: 96),
        SurahInfo(number: 57, nameArabic: "الحديد", startPage: 537, endPage: 541, ayahCount: 29),
        SurahInfo(number: 58, nameArabic: "المجادلة", startPage: 542, endPage: 545, ayahCount: 22),
        SurahInfo(number: 59, nameArabic: "الحشر", startPage: 545, endPage: 548, ayahCount: 24),
        SurahInfo(number: 60, nameArabic: "الممتحنة", startPage: 549, endPage: 551, ayahCount: 13),
        SurahInfo(number: 61, nameArabic: "الصف", startPage: 551, endPage: 552, ayahCount: 14),
        SurahInfo(number: 62, nameArabic: "الجمعة", startPage: 553, endPage: 554, ayahCount: 11),
        SurahInfo(number: 63, nameArabic: "المنافقون", startPage: 554, endPage: 555, ayahCount: 11),
        SurahInfo(number: 64, nameArabic: "التغابن", startPage: 556, endPage: 557, ayahCount: 18),
        SurahInfo(number: 65, nameArabic: "الطلاق", startPage: 558, endPage: 559, ayahCount: 12),
        SurahInfo(number: 66, nameArabic: "التحريم", startPage: 560, endPage: 561, ayahCount: 12),
        SurahInfo(number: 67, nameArabic: "الملك", startPage: 562, endPage: 564, ayahCount: 30),
        SurahInfo(number: 68, nameArabic: "القلم", startPage: 564, endPage: 566, ayahCount: 52),
        SurahInfo(number: 69, nameArabic: "الحاقة", startPage: 566, endPage: 568, ayahCount: 52),
        SurahInfo(number: 70, nameArabic: "المعارج", startPage: 568, endPage: 570, ayahCount: 44),
        SurahInfo(number: 71, nameArabic: "نوح", startPage: 570, endPage: 571, ayahCount: 28),
        SurahInfo(number: 72, nameArabic: "الجن", startPage: 572, endPage: 573, ayahCount: 28),
        SurahInfo(number: 73, nameArabic: "المزمل", startPage: 574, endPage: 575, ayahCount: 20),
        SurahInfo(number: 74, nameArabic: "المدثر", startPage: 575, endPage: 577, ayahCount: 56),
        SurahInfo(number: 75, nameArabic: "القيامة", startPage: 577, endPage: 578, ayahCount: 40),
        SurahInfo(number: 76, nameArabic: "الإنسان", startPage: 578, endPage: 580, ayahCount: 31),
        SurahInfo(number: 77, nameArabic: "المرسلات", startPage: 580, endPage: 581, ayahCount: 50),
        SurahInfo(number: 78, nameArabic: "النبأ", startPage: 582, endPage: 583, ayahCount: 40),
        SurahInfo(number: 79, nameArabic: "النازعات", startPage: 583, endPage: 584, ayahCount: 46),
        SurahInfo(number: 80, nameArabic: "عبس", startPage: 585, endPage: 586, ayahCount: 42),
        SurahInfo(number: 81, nameArabic: "التكوير", startPage: 586, endPage: 586, ayahCount: 29),
        SurahInfo(number: 82, nameArabic: "الانفطار", startPage: 587, endPage: 587, ayahCount: 19),
        SurahInfo(number: 83, nameArabic: "المطففين", startPage: 587, endPage: 589, ayahCount: 36),
        SurahInfo(number: 84, nameArabic: "الانشقاق", startPage: 589, endPage: 590, ayahCount: 25),
        SurahInfo(number: 85, nameArabic: "البروج", startPage: 590, endPage: 590, ayahCount: 22),
        SurahInfo(number: 86, nameArabic: "الطارق", startPage: 591, endPage: 591, ayahCount: 17),
        SurahInfo(number: 87, nameArabic: "الأعلى", startPage: 591, endPage: 592, ayahCount: 19),
        SurahInfo(number: 88, nameArabic: "الغاشية", startPage: 592, endPage: 593, ayahCount: 26),
        SurahInfo(number: 89, nameArabic: "الفجر", startPage: 593, endPage: 594, ayahCount: 30),
        SurahInfo(number: 90, nameArabic: "البلد", startPage: 594, endPage: 595, ayahCount: 20),
        SurahInfo(number: 91, nameArabic: "الشمس", startPage: 595, endPage: 595, ayahCount: 15),
        SurahInfo(number: 92, nameArabic: "الليل", startPage: 595, endPage: 596, ayahCount: 21),
        SurahInfo(number: 93, nameArabic: "الضحى", startPage: 596, endPage: 596, ayahCount: 11),
        SurahInfo(number: 94, nameArabic: "الشرح", startPage: 596, endPage: 597, ayahCount: 8),
        SurahInfo(number: 95, nameArabic: "التين", startPage: 597, endPage: 597, ayahCount: 8),
        SurahInfo(number: 96, nameArabic: "العلق", startPage: 597, endPage: 598, ayahCount: 19),
        SurahInfo(number: 97, nameArabic: "القدر", startPage: 598, endPage: 598, ayahCount: 5),
        SurahInfo(number: 98, nameArabic: "البينة", startPage: 598, endPage: 599, ayahCount: 8),
        SurahInfo(number: 99, nameArabic: "الزلزلة", startPage: 599, endPage: 599, ayahCount: 8),
        SurahInfo(number: 100, nameArabic: "العاديات", startPage: 599, endPage: 600, ayahCount: 11),
        SurahInfo(number: 101, nameArabic: "القارعة", startPage: 600, endPage: 600, ayahCount: 11),
        SurahInfo(number: 102, nameArabic: "التكاثر", startPage: 600, endPage: 600, ayahCount: 8),
        SurahInfo(number: 103, nameArabic: "العصر", startPage: 601, endPage: 601, ayahCount: 3),
        SurahInfo(number: 104, nameArabic: "الهمزة", startPage: 601, endPage: 601, ayahCount: 9),
        SurahInfo(number: 105, nameArabic: "الفيل", startPage: 601, endPage: 601, ayahCount: 5),
        SurahInfo(number: 106, nameArabic: "قريش", startPage: 602, endPage: 602, ayahCount: 4),
        SurahInfo(number: 107, nameArabic: "الماعون", startPage: 602, endPage: 602, ayahCount: 7),
        SurahInfo(number: 108, nameArabic: "الكوثر", startPage: 602, endPage: 602, ayahCount: 3),
        SurahInfo(number: 109, nameArabic: "الكافرون", startPage: 603, endPage: 603, ayahCount: 6),
        SurahInfo(number: 110, nameArabic: "النصر", startPage: 603, endPage: 603, ayahCount: 3),
        SurahInfo(number: 111, nameArabic: "المسد", startPage: 603, endPage: 603, ayahCount: 5),
        SurahInfo(number: 112, nameArabic: "الإخلاص", startPage: 604, endPage: 604, ayahCount: 4),
        SurahInfo(number: 113, nameArabic: "الفلق", startPage: 604, endPage: 604, ayahCount: 5),
        SurahInfo(number: 114, nameArabic: "الناس", startPage: 604, endPage: 604, ayahCount: 6)
    ]

    public static let juzStartPages: [Int] = [
        1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
        201, 222, 242, 262, 282, 302, 322, 342, 362, 382,
        402, 422, 442, 462, 482, 502, 522, 542, 562, 582
    ]

    public static let hizbStartPages: [Int] = [
        1, 11, 22, 32, 42, 51, 62, 72, 82, 92,
        102, 111, 121, 132, 142, 151, 162, 173, 182, 192,
        201, 211, 222, 231, 242, 252, 262, 272, 282, 292,
        302, 312, 322, 332, 342, 351, 362, 371, 382, 392,
        402, 413, 422, 431, 442, 451, 462, 472, 482, 491,
        502, 513, 522, 531, 542, 553, 562, 572, 582, 591
    ]

    public static func getSurahByNumber(_ number: Int) -> SurahInfo {
        let clamped = min(max(number, 1), totalSurahs)
        return surahs[clamped - 1]
    }

    public static func getStartPageForJuz(_ juzNumber: Int) -> Int {
        let clamped = min(max(juzNumber, 1), totalJuz)
        return juzStartPages[clamped - 1]
    }

    public static func getEndPageForJuz(_ juzNumber: Int) -> Int {
        let clamped = min(max(juzNumber, 1), totalJuz)
        return clamped == totalJuz ? totalPages : juzStartPages[clamped] - 1
    }

    public static func getStartPageForHizb(_ hizbNumber: Int) -> Int {
        let clamped = min(max(hizbNumber, 1), totalAhzab)
        return hizbStartPages[clamped - 1]
    }

    public static func getEndPageForHizb(_ hizbNumber: Int) -> Int {
        let clamped = min(max(hizbNumber, 1), totalAhzab)
        return clamped == totalAhzab ? totalPages : hizbStartPages[clamped] - 1
    }

    public static func getSurahAtPage(_ page: Int) -> SurahInfo {
        let safePage = min(max(page, 1), totalPages)
        return surahs.first(where: { $0.startPage == safePage })
            ?? surahs.last(where: { $0.startPage < safePage })
            ?? surahs[0]
    }

    public static func getLastSurahAtPage(_ page: Int) -> SurahInfo {
        let safePage = min(max(page, 1), totalPages)
        return surahs.last(where: { $0.startPage <= safePage }) ?? surahs[surahs.count - 1]
    }

    public static func getSurahsAtPage(_ page: Int) -> [SurahInfo] {
        let safePage = min(max(page, 1), totalPages)
        let startingHere = surahs.filter { $0.startPage == safePage }
        if !startingHere.isEmpty {
            return startingHere
        }
        if let spanning = surahs.last(where: { $0.startPage < safePage }) {
            return [spanning]
        }
        return [surahs[0]]
    }

    public static func getJuzAtPage(_ page: Int) -> Int {
        let safePage = min(max(page, 1), totalPages)
        for idx in stride(from: juzStartPages.count - 1, through: 0, by: -1) {
            if safePage >= juzStartPages[idx] {
                return idx + 1
            }
        }
        return 1
    }

    public static func getHizbAtPage(_ page: Int) -> Int {
        let safePage = min(max(page, 1), totalPages)
        for idx in stride(from: hizbStartPages.count - 1, through: 0, by: -1) {
            if safePage >= hizbStartPages[idx] {
                return idx + 1
            }
        }
        return 1
    }

    public static func computeEndPage(startPage: Int, pageCount: Int) -> Int {
        let safeStart = min(max(startPage, 1), totalPages)
        let safeCount = max(pageCount, 1)
        return positiveMod(safeStart + safeCount - 2, totalPages) + 1
    }

    public static func computePageCount(startPage: Int, endPage: Int) -> Int {
        let safeStart = min(max(startPage, 1), totalPages)
        let safeEnd = min(max(endPage, 1), totalPages)
        if safeEnd >= safeStart {
            return safeEnd - safeStart + 1
        }
        return (totalPages - safeStart + 1) + safeEnd
    }

    public static func isValidNonInvertedRange(startPage: Int, endPage: Int) -> Bool {
        return (1...totalPages).contains(startPage)
            && (1...totalPages).contains(endPage)
            && startPage <= endPage
    }

    public static func computeNonInvertedPageCount(startPage: Int, endPage: Int) throws -> Int {
        guard isValidNonInvertedRange(startPage: startPage, endPage: endPage) else {
            throw QuranRangeError.invertedOrOutOfBounds(startPage: startPage, endPage: endPage)
        }
        return endPage - startPage + 1
    }

    public static func computeEndAyah(surahNumber: Int?, startAyah: Int?, dailyAyahCount: Int?) -> Int? {
        guard let surahNum = surahNumber, (1...totalSurahs).contains(surahNum),
              let daily = dailyAyahCount, daily >= 1 else {
            return nil
        }
        let maxAyahs = getSurahByNumber(surahNum).ayahCount
        let safeStart = min(max(startAyah ?? 1, 1), maxAyahs)
        let safeDaily = min(max(daily, 1), maxAyahs)
        return min(safeStart + safeDaily - 1, maxAyahs)
    }

    public static func calculateKhatmaForecast(startPage: Int, dailyPages: Int) -> KhatmaForecast {
        let safeStart = min(max(startPage, 1), totalPages)
        let safeDaily = max(dailyPages, 1)
        let remaining = totalPages - safeStart + 1
        let days = Int(ceil(Double(remaining) / Double(safeDaily)))
        let percentage = (Float(safeStart - 1) / Float(totalPages - 1)) * 100.0

        let summary: String
        if remaining <= safeDaily {
            summary = "اليوم موعد الختمة المباركة بإذن الله!"
        } else {
            let daysLabel = AlarmUiFormatters.formatDaysCount(days, genitive: true)
            let remainingLabel = AlarmUiFormatters.formatPagesCount(remaining)
            summary = "الختمة المتوقعة خلال \(daysLabel) (\(remainingLabel) متبقية)"
        }

        return KhatmaForecast(
            currentStartPage: safeStart,
            dailyPages: safeDaily,
            totalPagesRemaining: remaining,
            daysToComplete: days,
            progressPercentage: percentage,
            forecastSummary: summary
        )
    }

    public static func calculateSurahWirdForecast(
        surahNumber: Int,
        startAyah: Int,
        dailyAyahs: Int
    ) -> SurahWirdForecast {
        let safeSurahNum = min(max(surahNumber, 1), totalSurahs)
        let surah = getSurahByNumber(safeSurahNum)
        let safeStart = min(max(startAyah, 1), surah.ayahCount)
        let safeDaily = min(max(dailyAyahs, 1), surah.ayahCount)
        let remaining = surah.ayahCount - safeStart + 1
        let days = Int(ceil(Double(remaining) / Double(safeDaily)))
        let percentage: Float = surah.ayahCount <= 1
            ? 0
            : (Float(safeStart - 1) / Float(surah.ayahCount)) * 100.0

        let summary: String
        if remaining <= safeDaily {
            summary = "اليوم إتمام سورة \(surah.nameArabic) بإذن الله!"
        } else {
            let daysLabel = AlarmUiFormatters.formatDaysCount(days, genitive: true)
            let remainingLabel = AlarmUiFormatters.formatAyahsCount(remaining)
            summary = "إتمام سورة \(surah.nameArabic) خلال \(daysLabel) (\(remainingLabel) متبقية)"
        }

        return SurahWirdForecast(
            surahName: surah.nameArabic,
            currentStartAyah: safeStart,
            dailyAyahs: safeDaily,
            totalAyahs: surah.ayahCount,
            daysToComplete: days,
            progressPercentage: percentage,
            forecastSummary: summary
        )
    }
}
