import SwiftUI

struct WirdConfigurationSection: View {
    let selectionMode: WirdSelectionMode
    let selectedStartSurah: Int
    let selectedEndSurah: Int
    let selectedStartHizb: Int
    let selectedEndHizb: Int
    let selectedStartJuz: Int
    let selectedEndJuz: Int
    let startPage: Int
    let endPage: Int
    let useAyahCount: Bool
    let startAyahInput: Int
    let dailyAyahCountInput: Int
    let isAyahModeActive: Bool
    let isSequential: Bool
    let calculatedPageCount: Int
    let forecast: KhatmaForecast

    let onSelectMode: (WirdSelectionMode) -> Void
    let onStartSurahSelected: (Int) -> Void
    let onEndSurahSelected: (Int) -> Void
    let onOpenStartSurahPicker: () -> Void
    let onOpenEndSurahPicker: () -> Void
    let onOpenStartPageInput: () -> Void
    let onOpenEndPageInput: () -> Void
    let onStartHizbSelected: (Int) -> Void
    let onEndHizbSelected: (Int) -> Void
    let onStartJuzSelected: (Int) -> Void
    let onEndJuzSelected: (Int) -> Void
    let onStartPageSelected: (Int) -> Void
    let onEndPageSelected: (Int) -> Void
    let onQuickPortionSelected: (Int) -> Void
    let onToggleAyahCount: (Bool) -> Void
    let onStartAyahChanged: (Int) -> Void
    let onDailyAyahCountChanged: (Int) -> Void
    let onSequentialChanged: (Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("📖 تحديد الورد القرآني")
                .font(QuranTypography.cairo(15, weight: .semibold))
                .foregroundColor(QuranColors.textPrimary)

            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    modePill(.surahs)
                    modePill(.ahzab)
                }
                HStack(spacing: 8) {
                    modePill(.ajzaa)
                    modePill(.pages)
                }
            }

            // Elevated inner wheel container
            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    VStack(spacing: 6) {
                        Text("البدء")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.textPrimary)
                        if selectionMode == .surahs {
                            actionChip(label: "بحث بالاسم", showSearchIcon: true, onClick: onOpenStartSurahPicker)
                        } else if selectionMode == .pages {
                            actionChip(label: "إدخال رقم", showSearchIcon: false, onClick: onOpenStartPageInput)
                        }
                    }
                    .frame(maxWidth: .infinity)

                    VStack(spacing: 6) {
                        Text("الانتهاء")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.activePillText)
                        if selectionMode == .surahs {
                            actionChip(label: "بحث بالاسم", showSearchIcon: true, onClick: onOpenEndSurahPicker)
                        } else if selectionMode == .pages {
                            actionChip(label: "إدخال رقم", showSearchIcon: false, onClick: onOpenEndPageInput)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }

                rangeWheels

                if selectionMode == .surahs && selectedStartSurah == selectedEndSurah {
                    singleSurahAyahSection
                }

                QuickWirdPortionRow(
                    startPage: startPage,
                    endPage: endPage,
                    onSelectTargetEndPage: onQuickPortionSelected
                )
            }
            .padding(12)
            .background(QuranColors.bgElevated)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(QuranColors.borderSubtle, lineWidth: 1)
            )

            WirdLivePreviewBox(
                isAyahModeActive: isAyahModeActive,
                selectedStartSurah: selectedStartSurah,
                startAyahInput: startAyahInput,
                dailyAyahCountInput: dailyAyahCountInput,
                startPage: startPage,
                endPage: endPage,
                calculatedPageCount: calculatedPageCount,
                forecast: forecast
            )

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(isSequential ? "الورد المتسلسل التلقائي" : "ورد ثابت محدد")
                        .font(QuranTypography.cairo(14, weight: .semibold))
                        .foregroundColor(QuranColors.textPrimary)
                    Text(
                        isSequential
                            ? "يتقدم تلقائياً كل يوم للصفحات التالية لختم المصحف"
                            : "قراءة نفس السور أو الصفحات يومياً دون تقدم"
                    )
                    .font(QuranTypography.cairo(12, weight: .regular))
                    .foregroundColor(QuranColors.textSecondary)
                }
                Spacer(minLength: 8)
                Toggle(
                    "",
                    isOn: Binding(get: { isSequential }, set: { onSequentialChanged($0) })
                )
                .labelsHidden()
                .toggleStyle(QuranSwitchToggleStyle())
            }
        }
        .padding(14)
        .background(QuranColors.bgCard)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(QuranColors.borderSubtle, lineWidth: 1)
        )
    }

    private func modePill(_ mode: WirdSelectionMode) -> some View {
        QuranSelectablePill(
            text: mode.titleArabic,
            selected: selectionMode == mode,
            fontSize: 13,
            fullWidth: true,
            onClick: { onSelectMode(mode) }
        )
    }

    private func actionChip(label: String, showSearchIcon: Bool, onClick: @escaping () -> Void) -> some View {
        Button(action: onClick) {
            HStack(spacing: 4) {
                if showSearchIcon {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 11, weight: .semibold))
                }
                Text(label)
                    .font(QuranTypography.cairo(12, weight: .semibold))
            }
            .foregroundColor(QuranColors.activePillText)
            .padding(.horizontal, 12)
            .padding(.vertical, 5)
            .background(QuranColors.activePillBg)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(QuranColors.activePillBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var rangeWheels: some View {
        switch selectionMode {
        case .surahs:
            let surahIds = Array(1...114)
            HStack(spacing: 10) {
                QuranWheelPicker(
                    items: surahIds,
                    selection: Binding(get: { selectedStartSurah }, set: { onStartSurahSelected($0) }),
                    fontSize: 13
                ) { num in
                    let s = QuranStructure.getSurahByNumber(num)
                    return "\(s.nameArabic) (ص \(s.startPage))"
                }
                QuranWheelPicker(
                    items: surahIds,
                    selection: Binding(get: { selectedEndSurah }, set: { onEndSurahSelected($0) }),
                    fontSize: 13
                ) { num in
                    let s = QuranStructure.getSurahByNumber(num)
                    return "\(s.nameArabic) (ص \(s.endPage))"
                }
            }
        case .ahzab:
            let ahzab = Array(1...60)
            HStack(spacing: 10) {
                QuranWheelPicker(
                    items: ahzab,
                    selection: Binding(get: { selectedStartHizb }, set: { onStartHizbSelected($0) }),
                    fontSize: 13
                ) { "حزب \($0) (ص \(QuranStructure.getStartPageForHizb($0)))" }
                QuranWheelPicker(
                    items: ahzab,
                    selection: Binding(get: { selectedEndHizb }, set: { onEndHizbSelected($0) }),
                    fontSize: 13
                ) { "حزب \($0) (ص \(QuranStructure.getEndPageForHizb($0)))" }
            }
        case .ajzaa:
            let ajzaa = Array(1...30)
            HStack(spacing: 10) {
                QuranWheelPicker(
                    items: ajzaa,
                    selection: Binding(get: { selectedStartJuz }, set: { onStartJuzSelected($0) }),
                    fontSize: 13
                ) { "جزء \($0) (ص \(QuranStructure.getStartPageForJuz($0)))" }
                QuranWheelPicker(
                    items: ajzaa,
                    selection: Binding(get: { selectedEndJuz }, set: { onEndJuzSelected($0) }),
                    fontSize: 13
                ) { "جزء \($0) (ص \(QuranStructure.getEndPageForJuz($0)))" }
            }
        case .pages:
            let pages = Array(1...604)
            HStack(spacing: 10) {
                QuranWheelPicker(
                    items: pages,
                    selection: Binding(get: { startPage }, set: { onStartPageSelected($0) }),
                    fontSize: 18
                ) { "ص \($0)" }
                QuranWheelPicker(
                    items: pages,
                    selection: Binding(get: { endPage }, set: { onEndPageSelected($0) }),
                    fontSize: 18
                ) { "ص \($0)" }
            }
        }
    }

    private var singleSurahAyahSection: some View {
        let sameSurah = QuranStructure.getSurahByNumber(selectedStartSurah)
        let safeStartAyah = min(max(startAyahInput, 1), sameSurah.ayahCount)
        let safeDailyAyah = min(max(dailyAyahCountInput, 1), sameSurah.ayahCount)
        let allAyahs = Array(1...sameSurah.ayahCount)

        return VStack(spacing: 8) {
            HStack(spacing: 8) {
                QuranSelectablePill(
                    text: "السورة كاملة (\(AlarmUiFormatters.formatAyahsCount(sameSurah.ayahCount)))",
                    selected: !useAyahCount,
                    fontSize: 12,
                    fullWidth: true,
                    onClick: { onToggleAyahCount(false) }
                )
                QuranSelectablePill(
                    text: "تحديد آيات يومياً",
                    selected: useAyahCount,
                    fontSize: 12,
                    fullWidth: true,
                    onClick: { onToggleAyahCount(true) }
                )
            }

            if useAyahCount {
                HStack(spacing: 10) {
                    Text("آية البدء")
                        .font(QuranTypography.cairo(12, weight: .semibold))
                        .foregroundColor(QuranColors.textPrimary)
                        .frame(maxWidth: .infinity)
                    Text("عدد الآيات يومياً")
                        .font(QuranTypography.cairo(12, weight: .semibold))
                        .foregroundColor(QuranColors.activePillText)
                        .frame(maxWidth: .infinity)
                }

                HStack(spacing: 10) {
                    QuranWheelPicker(
                        items: allAyahs,
                        selection: Binding(get: { safeStartAyah }, set: { onStartAyahChanged($0) }),
                        height: 116,
                        fontSize: 13
                    ) { "آية \($0)" }

                    QuranWheelPicker(
                        items: allAyahs,
                        selection: Binding(get: { safeDailyAyah }, set: { onDailyAyahCountChanged($0) }),
                        height: 116,
                        fontSize: 13
                    ) { AlarmUiFormatters.formatAyahsCount($0) }
                }
            }
        }
    }
}

private struct QuickWirdPortionRow: View {
    let startPage: Int
    let endPage: Int
    let onSelectTargetEndPage: (Int) -> Void

    var body: some View {
        let safeStart = min(max(startPage, 1), QuranStructure.totalPages)
        let safeEnd = min(max(endPage, safeStart), QuranStructure.totalPages)
        let currentLen = safeEnd - safeStart + 1
        let hizbAtStart = QuranStructure.getHizbAtPage(safeStart)
        let hizbEndTarget = safeStart == QuranStructure.getStartPageForHizb(hizbAtStart)
            ? QuranStructure.getEndPageForHizb(hizbAtStart)
            : min(safeStart + 9, QuranStructure.totalPages)

        FlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
            QuranSelectablePill(
                text: "+1 صفحة",
                selected: false,
                fontSize: 12,
                horizontalPadding: 10,
                verticalPadding: 6
            ) {
                onSelectTargetEndPage(min(safeEnd + 1, QuranStructure.totalPages))
            }
            QuranSelectablePill(
                text: "+2",
                selected: false,
                fontSize: 12,
                horizontalPadding: 10,
                verticalPadding: 6
            ) {
                onSelectTargetEndPage(min(safeEnd + 2, QuranStructure.totalPages))
            }
            QuranSelectablePill(
                text: "+5",
                selected: false,
                fontSize: 12,
                horizontalPadding: 10,
                verticalPadding: 6
            ) {
                onSelectTargetEndPage(min(safeEnd + 5, QuranStructure.totalPages))
            }
            QuranSelectablePill(
                text: "ربع",
                selected: currentLen == 3,
                fontSize: 12,
                horizontalPadding: 10,
                verticalPadding: 6
            ) {
                onSelectTargetEndPage(min(safeStart + 2, QuranStructure.totalPages))
            }
            QuranSelectablePill(
                text: "حزب",
                selected: safeEnd == hizbEndTarget && currentLen >= 8,
                fontSize: 12,
                horizontalPadding: 10,
                verticalPadding: 6
            ) {
                onSelectTargetEndPage(hizbEndTarget)
            }
            if safeEnd > safeStart {
                QuranSelectablePill(
                    text: "صفحة واحدة",
                    selected: false,
                    fontSize: 12,
                    horizontalPadding: 10,
                    verticalPadding: 6
                ) {
                    onSelectTargetEndPage(safeStart)
                }
            }
        }
    }
}

struct WirdLivePreviewBox: View {
    let isAyahModeActive: Bool
    let selectedStartSurah: Int
    let startAyahInput: Int
    let dailyAyahCountInput: Int
    let startPage: Int
    let endPage: Int
    let calculatedPageCount: Int
    let forecast: KhatmaForecast

    var body: some View {
        let isVeryLarge = calculatedPageCount > 20
        let isModeratelyLarge = (11...20).contains(calculatedPageCount)

        let borderColor: Color = isVeryLarge
            ? QuranColors.dangerRed.opacity(0.7)
            : (isModeratelyLarge ? QuranColors.warningOrange.opacity(0.7) : QuranColors.goldBadgeBorder)
        let boxColor: Color = isVeryLarge
            ? QuranColors.dangerRedBg
            : (isModeratelyLarge ? QuranColors.warningOrangeBg : QuranColors.goldBadgeBg)

        VStack(alignment: .leading, spacing: 4) {
            if isAyahModeActive {
                let sameSurah = QuranStructure.getSurahByNumber(selectedStartSurah)
                let safeStart = min(max(startAyahInput, 1), sameSurah.ayahCount)
                let safeDaily = min(max(dailyAyahCountInput, 1), sameSurah.ayahCount)
                let safeEnd = QuranStructure.computeEndAyah(
                    surahNumber: selectedStartSurah,
                    startAyah: safeStart,
                    dailyAyahCount: safeDaily
                ) ?? safeStart
                let remaining = sameSurah.ayahCount - safeStart + 1
                let daysToFinish = Int(ceil(Double(remaining) / Double(safeDaily)))
                let ayahForecastText = remaining <= safeDaily
                    ? "اليوم إتمام سورة \(sameSurah.nameArabic) بإذن الله!"
                    : "إتمام سورة \(sameSurah.nameArabic) خلال \(AlarmUiFormatters.formatDaysCount(daysToFinish)) (\(AlarmUiFormatters.formatAyahsCount(remaining)) متبقية)"
                let estimatedTime = AlarmUiFormatters.formatEstimatedReadingTime(
                    pageCount: calculatedPageCount,
                    ayahCount: safeDaily,
                    surahTotalAyahs: sameSurah.ayahCount
                )
                let pagesLabel = startPage == endPage
                    ? "صفحة \(startPage) (صفحة واحدة)"
                    : "ص \(startPage)-\(endPage) (\(AlarmUiFormatters.formatPagesCount(calculatedPageCount)))"

                Text("معاينة الورد: سورة \(sameSurah.nameArabic) • من آية \(safeStart) إلى آية \(safeEnd) (\(AlarmUiFormatters.formatAyahsCount(safeDaily)) • \(pagesLabel))")
                    .font(QuranTypography.cairo(13, weight: .semibold))
                    .foregroundColor(QuranColors.textPrimary)
                Text("⏱ الوقت التقريبي للقراءة: \(estimatedTime)")
                    .font(QuranTypography.cairo(12, weight: .medium))
                    .foregroundColor(QuranColors.activePillText)
                Text(ayahForecastText)
                    .font(QuranTypography.cairo(12, weight: .regular))
                    .foregroundColor(QuranColors.goldBadgeText)
            } else {
                let rangeSummary = AlarmUiFormatters.formatPageRangeSummary(startPage: startPage, endPage: endPage)
                let estimatedTime = AlarmUiFormatters.formatEstimatedReadingTime(pageCount: calculatedPageCount)

                Text("معاينة الورد: \(rangeSummary)")
                    .font(QuranTypography.cairo(13, weight: .semibold))
                    .foregroundColor(QuranColors.textPrimary)
                Text("📊 إجمالي الورد: \(AlarmUiFormatters.formatPagesCount(calculatedPageCount)) • ⏱ الوقت التقريبي: \(estimatedTime)")
                    .font(QuranTypography.cairo(12, weight: .medium))
                    .foregroundColor(QuranColors.activePillText)
                Text(forecast.forecastSummary)
                    .font(QuranTypography.cairo(12, weight: .regular))
                    .foregroundColor(QuranColors.goldBadgeText)
            }

            if isVeryLarge {
                Text("⚠ تنبيه: الورد كبير جداً (\(AlarmUiFormatters.formatPagesCount(calculatedPageCount)))، وقد يتطلب وقتاً طويلاً لإتمامه قبل إغلاق المنبه.")
                    .font(QuranTypography.cairo(12, weight: .semibold))
                    .foregroundColor(QuranColors.dangerRed)
            } else if isModeratelyLarge {
                Text("⚠ ملاحظة: حجم الورد كبير نسبياً (\(AlarmUiFormatters.formatPagesCount(calculatedPageCount))).")
                    .font(QuranTypography.cairo(12, weight: .medium))
                    .foregroundColor(QuranColors.warningOrange)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(boxColor)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(borderColor, lineWidth: 1)
        )
    }
}

struct AlarmAudioAndSoundSection: View {
    @Binding var volumeLevel: Double
    @Binding var isVibrate: Bool
    let selectedSoundUri: String?
    let selectedSoundTitle: String
    let isPlayingPreview: Bool
    let onVolumeChanged: (Double) -> Void
    let onVolumeEditingChanged: (Bool) -> Void
    let onTogglePreview: () -> Void
    let onSelectPreset: (String?, String) -> Void
    let onOpenFileImporter: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("درجة الصوت: \(Int(volumeLevel))%")
                .font(QuranTypography.cairo(13, weight: .medium))
                .foregroundColor(QuranColors.textSecondary)

            Slider(
                value: Binding(
                    get: { volumeLevel },
                    set: {
                        volumeLevel = $0
                        onVolumeChanged($0)
                    }
                ),
                in: 10...100,
                step: 1,
                onEditingChanged: onVolumeEditingChanged
            )
            .tint(QuranColors.ctaBg)

            Toggle(isOn: $isVibrate) {
                Text("الاهتزاز المتواصل")
                    .font(QuranTypography.cairo(14, weight: .medium))
                    .foregroundColor(QuranColors.textPrimary)
                Spacer()
            }
            .toggleStyle(QuranSwitchToggleStyle())

            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("النغمة الحالية")
                            .font(QuranTypography.cairo(11, weight: .regular))
                            .foregroundColor(QuranColors.textSecondary)
                        Text(AlarmUiFormatters.formatCompactSoundTitle(selectedSoundTitle))
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.textPrimary)
                            .lineLimit(1)
                    }
                    Spacer()
                    Button(action: onTogglePreview) {
                        Text(isPlayingPreview ? "إيقاف ⏹" : "استماع ▶")
                            .font(QuranTypography.cairo(12, weight: .semibold))
                            .foregroundColor(isPlayingPreview ? QuranColors.textPrimary : QuranColors.ctaText)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(isPlayingPreview ? QuranColors.dangerRed : QuranColors.ctaBg)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }

                Text("النغمات المتاحة:")
                    .font(QuranTypography.cairo(12, weight: .regular))
                    .foregroundColor(QuranColors.textSecondary)

                FlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    ForEach(AlarmSoundPresets.presetOptions) { preset in
                        let isSelected = selectedSoundUri == preset.uriString
                            || (selectedSoundUri == nil && preset.uriString == nil)
                            || (selectedSoundUri == AlarmSoundPresets.uriDefault && preset.uriString == nil)
                        QuranSelectablePill(
                            text: preset.title,
                            selected: isSelected,
                            fontSize: 12,
                            onClick: { onSelectPreset(preset.uriString, preset.title) }
                        )
                    }
                }

                Button(action: onOpenFileImporter) {
                    HStack {
                        Image(systemName: "folder.badge.plus")
                        Text("اختيار ملف صوتي من الجهاز")
                            .font(QuranTypography.cairo(12, weight: .semibold))
                    }
                    .foregroundColor(QuranColors.textPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 42)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(QuranColors.borderSubtle, lineWidth: 1)
                    )
                }
            }
            .padding(12)
            .background(QuranColors.bgElevated)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(QuranColors.borderSubtle, lineWidth: 1)
            )
        }
    }
}

struct RepeatSelectionContent: View {
    let daysList: [(Int, String)]
    @Binding var repeatDays: Set<Int>

    private let allDaysSet: Set<Int> = [
        AlarmItem.sunday, AlarmItem.monday, AlarmItem.tuesday,
        AlarmItem.wednesday, AlarmItem.thursday, AlarmItem.friday, AlarmItem.saturday
    ]

    var body: some View {
        let defaultWorkdays = AlarmUiFormatters.getDefaultWorkdays()
        VStack(alignment: .leading, spacing: 10) {
            FlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                QuranSelectablePill(
                    text: "كل يوم",
                    selected: repeatDays.count == 7,
                    fontSize: 12,
                    onClick: { repeatDays = allDaysSet }
                )
                QuranSelectablePill(
                    text: "وسط أيام الأسبوع",
                    selected: repeatDays == defaultWorkdays,
                    fontSize: 12,
                    onClick: { repeatDays = defaultWorkdays }
                )
                QuranSelectablePill(
                    text: "مرة واحدة",
                    selected: repeatDays.isEmpty,
                    fontSize: 12,
                    onClick: { repeatDays = [] }
                )
            }

            let row1 = Array(daysList.prefix(4))
            let row2 = Array(daysList.dropFirst(4))

            VStack(spacing: 6) {
                dayRow(row1)
                dayRow(row2)
            }
        }
    }

    private func dayRow(_ days: [(Int, String)]) -> some View {
        HStack(spacing: 6) {
            ForEach(days, id: \.0) { dayConst, dayName in
                let isSelected = repeatDays.contains(dayConst)
                QuranSelectablePill(
                    text: dayName,
                    selected: isSelected,
                    fontSize: 12,
                    horizontalPadding: 6,
                    verticalPadding: 8,
                    fullWidth: true
                ) {
                    if isSelected {
                        repeatDays.remove(dayConst)
                    } else {
                        repeatDays.insert(dayConst)
                    }
                }
            }
        }
    }
}

struct LabelSelectionContent: View {
    @Binding var label: String
    let onPresetSelected: (String) -> Void

    private let presets = [
        "صلاة الفجر والورد القرآني",
        "قيام الليل والورد القرآني",
        "سورة الملك قبل النوم",
        "سورة الكهف الجمعة"
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            FlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                ForEach(presets, id: \.self) { preset in
                    QuranSelectablePill(
                        text: preset,
                        selected: label == preset,
                        fontSize: 12,
                        onClick: { onPresetSelected(preset) }
                    )
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("الوصف المخصص")
                    .font(QuranTypography.cairo(12, weight: .regular))
                    .foregroundColor(QuranColors.textSecondary)

                TextField("أدخل تسمية المنبه...", text: Binding(
                    get: { label },
                    set: { label = String($0.prefix(AlarmItem.maxLabelLength)) }
                ))
                .font(QuranTypography.cairo(14, weight: .medium))
                .foregroundColor(QuranColors.textPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(QuranColors.bgElevated)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(QuranColors.borderSubtle, lineWidth: 1)
                )
            }
        }
    }
}
