import Foundation

/// Pure Arabic UI formatters matching Android AlarmUiFormatters.kt 1-to-1.
public enum AlarmUiFormatters {
    private static let posixLocale = Locale(identifier: "en_US_POSIX")

    public static let dayNames: [Int: String] = [
        AlarmItem.saturday: "السبت",
        AlarmItem.sunday: "الأحد",
        AlarmItem.monday: "الاثنين",
        AlarmItem.tuesday: "الثلاثاء",
        AlarmItem.wednesday: "الأربعاء",
        AlarmItem.thursday: "الخميس",
        AlarmItem.friday: "الجمعة"
    ]

    public static func getHour12(_ hour24: Int) -> Int {
        let mod = QuranStructure.positiveMod(hour24, 24)
        switch mod {
        case 0: return 12
        case 1...12: return mod
        default: return mod - 12
        }
    }

    public static func isAm(_ hour24: Int) -> Bool {
        QuranStructure.positiveMod(hour24, 24) < 12
    }

    public static func toHour24(hour12: Int, isAm: Bool) -> Int {
        let h = min(max(hour12, 1), 12)
        if isAm && h == 12 { return 0 }
        if isAm { return h }
        if !isAm && h == 12 { return 12 }
        return h + 12
    }

    public static func formatTime12Hour(hour: Int, minute: Int) -> String {
        let h12 = getHour12(hour)
        let minFormatted = String(format: "%02d", locale: posixLocale, minute)
        let period = isAm(hour) ? "ص" : "م"
        return "\(h12):\(minFormatted) \(period)"
    }

    public static func formatDigits12HourPadded(hour24: Int, minute: Int) -> String {
        let h12 = getHour12(hour24)
        return String(format: "%02d:%02d", locale: posixLocale, h12, minute)
    }

    public static func getPeriodLabel(_ hour24: Int) -> String {
        isAm(hour24) ? "ص" : "م"
    }

    public static func formatCompactSoundTitle(_ rawTitle: String?) -> String {
        let trimmed = rawTitle?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if trimmed.isEmpty || trimmed == "نغمة المنبه الافتراضية" {
            return "النغمة الافتراضية"
        }
        return trimmed
    }

    public static func formatHoursCount(_ hours: Int, genitive: Bool = true) -> String {
        if hours == 1 { return "ساعة واحدة" }
        if hours == 2 { return genitive ? "ساعتين" : "ساعتان" }
        if (3...10).contains(hours) { return "\(hours) ساعات" }
        return "\(hours) ساعة"
    }

    public static func formatMinutesCount(_ minutes: Int, genitive: Bool = true) -> String {
        if minutes == 1 { return "دقيقة واحدة" }
        if minutes == 2 { return genitive ? "دقيقتين" : "دقيقتان" }
        if (3...10).contains(minutes) { return "\(minutes) دقائق" }
        return "\(minutes) دقيقة"
    }

    public static func formatDaysCount(_ days: Int, genitive: Bool = true) -> String {
        if days == 1 { return "يوم واحد" }
        if days == 2 { return genitive ? "يومين" : "يومان" }
        if (3...10).contains(days) { return "\(days) أيام" }
        if (11...99).contains(days % 100) { return "\(days) يوماً" }
        return "\(days) يوم"
    }

    public static func formatPagesCount(_ pages: Int) -> String {
        if pages == 1 { return "صفحة واحدة" }
        if pages == 2 { return "صفحتان" }
        if (3...10).contains(pages) { return "\(pages) صفحات" }
        return "\(pages) صفحة"
    }

    public static func formatAyahsCount(_ ayahs: Int) -> String {
        if ayahs == 1 { return "آية واحدة" }
        if ayahs == 2 { return "آيتان" }
        if (3...10).contains(ayahs) { return "\(ayahs) آيات" }
        return "\(ayahs) آية"
    }

    public static func formatEstimatedReadingTime(
        pageCount: Int,
        ayahCount: Int? = nil,
        surahTotalAyahs: Int? = nil
    ) -> String {
        let safePages = max(pageCount, 1)
        let totalMinutes: Int
        if let ayahs = ayahCount, let total = surahTotalAyahs, total > 0 {
            let ratio = min(max(Double(ayahs) / Double(total), 0.05), 1.0)
            totalMinutes = max(Int(ceil(Double(safePages) * 2.0 * ratio)), 1)
        } else {
            totalMinutes = max(safePages * 2, 1)
        }

        let hours = totalMinutes / 60
        let mins = totalMinutes % 60
        if hours == 0 {
            return "حوالي \(formatMinutesCount(mins, genitive: true))"
        }
        if mins == 0 {
            return "حوالي \(formatHoursCount(hours, genitive: true))"
        }
        return "حوالي \(formatHoursCount(hours, genitive: true)) و \(formatMinutesCount(mins, genitive: true))"
    }

    public static func formatPageRangeSummary(startPage: Int, endPage: Int) -> String {
        let safeStart = min(max(startPage, 1), QuranStructure.totalPages)
        let safeEnd = min(max(endPage, 1), QuranStructure.totalPages)
        if safeStart == safeEnd {
            let surah = QuranStructure.getSurahAtPage(safeStart)
            return "سورة \(surah.nameArabic) • صفحة \(safeStart) (صفحة واحدة)"
        }
        let count = safeEnd >= safeStart
            ? (safeEnd - safeStart + 1)
            : QuranStructure.computePageCount(startPage: safeStart, endPage: safeEnd)
        return "من ص \(safeStart) إلى ص \(safeEnd) (\(formatPagesCount(count)))"
    }

    public static func formatWirdCardSummary(_ alarm: AlarmItem) -> String {
        if alarm.isAyahMode, let surahNum = alarm.wirdSurahNumber {
            let surah = QuranStructure.getSurahByNumber(surahNum)
            let sAyah = alarm.startAyah ?? 1
            let eAyah = alarm.endAyah ?? sAyah
            if sAyah == eAyah {
                return "سورة \(surah.nameArabic) • آية \(sAyah) (آية واحدة)"
            }
            return "سورة \(surah.nameArabic) • آية \(sAyah)-\(eAyah) (\(formatAyahsCount(alarm.dailyAyahCount ?? 1)))"
        }

        let surahAtStart = QuranStructure.getSurahAtPage(alarm.startPage)
        if alarm.pageCount == 1 {
            return "سورة \(surahAtStart.nameArabic) • صفحة \(alarm.startPage)"
        }
        return "سورة \(surahAtStart.nameArabic) • ص \(alarm.startPage)-\(alarm.endPage) (\(formatPagesCount(alarm.pageCount)))"
    }

    public static func getDefaultWorkdays(locale: Locale = .current) -> Set<Int> {
        var cal = Calendar(identifier: .gregorian)
        cal.locale = locale
        if cal.firstWeekday == AlarmItem.monday {
            return [AlarmItem.monday, AlarmItem.tuesday, AlarmItem.wednesday, AlarmItem.thursday, AlarmItem.friday]
        }
        return [AlarmItem.sunday, AlarmItem.monday, AlarmItem.tuesday, AlarmItem.wednesday, AlarmItem.thursday]
    }

    public static func formatRepeatDays(_ repeatDays: Set<Int>) -> String {
        if repeatDays.isEmpty { return "مرة واحدة" }
        if repeatDays.count == 7 { return "كل يوم" }

        let sunThuWorkdays: Set<Int> = [AlarmItem.sunday, AlarmItem.monday, AlarmItem.tuesday, AlarmItem.wednesday, AlarmItem.thursday]
        let monFriWorkdays: Set<Int> = [AlarmItem.monday, AlarmItem.tuesday, AlarmItem.wednesday, AlarmItem.thursday, AlarmItem.friday]
        if repeatDays == sunThuWorkdays || repeatDays == monFriWorkdays { return "وسط أيام الأسبوع" }

        let friSatWeekend: Set<Int> = [AlarmItem.friday, AlarmItem.saturday]
        let satSunWeekend: Set<Int> = [AlarmItem.saturday, AlarmItem.sunday]
        if repeatDays == friSatWeekend || repeatDays == satSunWeekend { return "نهاية الأسبوع" }

        let orderedDays = [
            AlarmItem.saturday,
            AlarmItem.sunday,
            AlarmItem.monday,
            AlarmItem.tuesday,
            AlarmItem.wednesday,
            AlarmItem.thursday,
            AlarmItem.friday
        ]
        return orderedDays.filter { repeatDays.contains($0) }.compactMap { dayNames[$0] }.joined(separator: "، ")
    }

    public static func getDayName(_ dayOfWeek: Int) -> String? {
        dayNames[dayOfWeek]
    }

    public static func formatNextAlarmCountdown(
        triggerMillis: Int64,
        fromMillis: Int64 = AlarmItem.currentMillis(),
        targetDayName: String? = nil
    ) -> String {
        guard triggerMillis > fromMillis else { return "المنبه الآن" }

        let cal = AlarmItem.gregorianCalendar()
        let fromDate = Date(timeIntervalSince1970: Double(fromMillis) / 1000.0)
        let triggerDate = Date(timeIntervalSince1970: Double(triggerMillis) / 1000.0)

        var fromComps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fromDate)
        fromComps.second = 0
        fromComps.nanosecond = 0
        var triggerComps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: triggerDate)
        triggerComps.second = 0
        triggerComps.nanosecond = 0

        let cleanFrom = cal.date(from: fromComps) ?? fromDate
        let cleanTrigger = cal.date(from: triggerComps) ?? triggerDate
        let cleanDiffMs = Int64(((cleanTrigger.timeIntervalSince1970 - cleanFrom.timeIntervalSince1970) * 1000.0).rounded())
        let totalMinutes = cleanDiffMs > 0 ? Int(cleanDiffMs / 60_000) : 0

        let days = totalMinutes / (24 * 60)
        let remHours = (totalMinutes / 60) % 24
        let minutes = totalMinutes % 60

        let prefix: String
        if let day = targetDayName, !day.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            prefix = "المنبه يوم \(day) خلال"
        } else {
            prefix = "المنبه خلال"
        }

        if days > 0 {
            var parts = [formatDaysCount(days, genitive: true)]
            if remHours > 0 { parts.append(formatHoursCount(remHours, genitive: true)) }
            if minutes > 0 { parts.append(formatMinutesCount(minutes, genitive: true)) }
            return "\(prefix) \(parts.joined(separator: " و "))"
        }

        if remHours == 0 && totalMinutes == 0 {
            return "\(prefix) أقل من دقيقة"
        }
        if remHours == 0 {
            return "\(prefix) \(formatMinutesCount(totalMinutes, genitive: true))"
        }
        if minutes == 0 {
            return "\(prefix) \(formatHoursCount(remHours, genitive: true))"
        }
        return "\(prefix) \(formatHoursCount(remHours, genitive: true)) و \(formatMinutesCount(minutes, genitive: true))"
    }

    public static func formatCurrentDate(timeMillis: Int64 = AlarmItem.currentMillis()) -> String {
        let cal = AlarmItem.gregorianCalendar()
        let date = Date(timeIntervalSince1970: Double(timeMillis) / 1000.0)
        let comps = cal.dateComponents([.weekday, .day, .month], from: date)
        let monthNames = [
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        ]
        let dayOfWeek = comps.weekday.flatMap { dayNames[$0] } ?? ""
        let dayOfMonth = String(format: "%02d", locale: posixLocale, comps.day ?? 1)
        let monthIdx = min(max((comps.month ?? 1) - 1, 0), 11)
        return "\(dayOfWeek) \(dayOfMonth) \(monthNames[monthIdx])"
    }

    public static func formatCurrentDigitalTime(timeMillis: Int64 = AlarmItem.currentMillis()) -> String {
        let cal = AlarmItem.gregorianCalendar()
        let date = Date(timeIntervalSince1970: Double(timeMillis) / 1000.0)
        let comps = cal.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", locale: posixLocale, comps.hour ?? 0, comps.minute ?? 0)
    }
}
