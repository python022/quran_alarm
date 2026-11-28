import SwiftUI
import UniformTypeIdentifiers

public struct AddEditAlarmView: View {
    public let alarmToEdit: AlarmItem?
    public let existingAlarmIds: Set<Int>
    public let onDismiss: () -> Void
    public let onSave: (AlarmItem) -> Void
    public let onDelete: ((Int) -> Void)?
    public let onPreviewAlarm: (Int, Bool, String?) -> Void

    private var isEditMode: Bool { alarmToEdit != nil }

    @State private var hour12: Int
    @State private var minute: Int
    @State private var isAm: Bool
    @State private var repeatDays: Set<Int>
    @State private var isVibrate: Bool
    @State private var label: String

    @State private var selectionMode: WirdSelectionMode
    @State private var selectedStartSurah: Int
    @State private var selectedEndSurah: Int
    @State private var useAyahCount: Bool
    @State private var startAyahInput: Int
    @State private var dailyAyahCountInput: Int
    @State private var selectedStartHizb: Int
    @State private var selectedEndHizb: Int
    @State private var selectedStartJuz: Int
    @State private var selectedEndJuz: Int
    @State private var startPage: Int
    @State private var endPage: Int

    @State private var isSequential: Bool
    @State private var volumeLevel: Double
    @State private var selectedSoundUri: String?
    @State private var selectedSoundTitle: String

    @State private var isSoundSectionExpanded = false
    @State private var isRepeatSectionExpanded = false
    @State private var isLabelSectionExpanded = false

    @State private var showStartSurahPicker = false
    @State private var showEndSurahPicker = false
    @State private var showStartPageInputDialog = false
    @State private var showEndPageInputDialog = false
    @State private var showFileImporter = false

    @State private var showSaveConfirmDialog = false
    @State private var showDeleteConfirmDialog = false
    @State private var isSaving = false
    @State private var isAlarmSaved = false
    @State private var pendingCopiedURLs: [URL] = []
    @State private var validationBannerMessage: String?

    @StateObject private var audioManager = SmartAlarmAudioManager()
    @State private var isPlayingPreview = false

    private let daysList: [(Int, String)] = [
        (AlarmItem.saturday, "السبت"),
        (AlarmItem.sunday, "الأحد"),
        (AlarmItem.monday, "الاثنين"),
        (AlarmItem.tuesday, "الثلاثاء"),
        (AlarmItem.wednesday, "الأربعاء"),
        (AlarmItem.thursday, "الخميس"),
        (AlarmItem.friday, "الجمعة")
    ]

    public init(
        alarmToEdit: AlarmItem? = nil,
        existingAlarmIds: Set<Int> = [],
        onDismiss: @escaping () -> Void,
        onSave: @escaping (AlarmItem) -> Void,
        onDelete: ((Int) -> Void)? = nil,
        onPreviewAlarm: @escaping (Int, Bool, String?) -> Void
    ) {
        self.alarmToEdit = alarmToEdit
        self.existingAlarmIds = existingAlarmIds
        self.onDismiss = onDismiss
        self.onSave = onSave
        self.onDelete = onDelete
        self.onPreviewAlarm = onPreviewAlarm

        let initialHour24 = alarmToEdit?.hour ?? 5
        let initialStartPage = min(max(alarmToEdit?.startPage ?? 1, 1), QuranStructure.totalPages)
        let initialEndPage: Int = {
            if let edit = alarmToEdit {
                return min(max(edit.startPage + edit.pageCount - 1, initialStartPage), QuranStructure.totalPages)
            }
            return 1
        }()

        let initialMode: WirdSelectionMode = {
            guard let edit = alarmToEdit else { return .pages }
            if edit.isAyahMode { return .surahs }
            let sSurah = QuranStructure.getSurahAtPage(initialStartPage)
            let eSurah = QuranStructure.getLastSurahAtPage(initialEndPage)
            if sSurah.startPage == initialStartPage && eSurah.endPage == initialEndPage {
                return .surahs
            }
            return .pages
        }()

        _hour12 = State(initialValue: AlarmUiFormatters.getHour12(initialHour24))
        _minute = State(initialValue: alarmToEdit?.minute ?? 0)
        _isAm = State(initialValue: AlarmUiFormatters.isAm(initialHour24))
        _repeatDays = State(initialValue: alarmToEdit?.repeatDays ?? [
            AlarmItem.sunday, AlarmItem.monday, AlarmItem.tuesday, AlarmItem.wednesday,
            AlarmItem.thursday, AlarmItem.friday, AlarmItem.saturday
        ])
        _isVibrate = State(initialValue: alarmToEdit?.isVibrationEnabled ?? true)
        _label = State(initialValue: alarmToEdit?.label ?? "صلاة الفجر والورد القرآني")
        _selectionMode = State(initialValue: initialMode)
        _selectedStartSurah = State(initialValue: alarmToEdit?.wirdSurahNumber ?? QuranStructure.getSurahAtPage(initialStartPage).number)
        _selectedEndSurah = State(initialValue: {
            if let surah = alarmToEdit?.wirdSurahNumber { return surah }
            if alarmToEdit != nil { return QuranStructure.getLastSurahAtPage(initialEndPage).number }
            return QuranStructure.getSurahAtPage(1).number
        }())
        _useAyahCount = State(initialValue: alarmToEdit?.isAyahMode ?? false)
        _startAyahInput = State(initialValue: alarmToEdit?.startAyah ?? 1)
        _dailyAyahCountInput = State(initialValue: alarmToEdit?.dailyAyahCount ?? 10)
        _selectedStartHizb = State(initialValue: QuranStructure.getHizbAtPage(initialStartPage))
        _selectedEndHizb = State(initialValue: QuranStructure.getHizbAtPage(initialEndPage))
        _selectedStartJuz = State(initialValue: QuranStructure.getJuzAtPage(initialStartPage))
        _selectedEndJuz = State(initialValue: QuranStructure.getJuzAtPage(initialEndPage))
        _startPage = State(initialValue: initialStartPage)
        _endPage = State(initialValue: initialEndPage)
        _isSequential = State(initialValue: alarmToEdit?.isSequential ?? true)
        _volumeLevel = State(initialValue: Double(alarmToEdit?.volumeLevel ?? 100))
        _selectedSoundUri = State(initialValue: alarmToEdit?.soundUri)
        _selectedSoundTitle = State(initialValue: AlarmUiFormatters.formatCompactSoundTitle(
            alarmToEdit?.soundTitle ?? AlarmSoundPresets.getTitleForUri(alarmToEdit?.soundUri)
        ))
    }

    private var isAyahModeActive: Bool {
        selectionMode == .surahs && selectedStartSurah == selectedEndSurah && useAyahCount
    }

    private var isCurrentRangeInverted: Bool {
        !QuranStructure.isValidNonInvertedRange(startPage: startPage, endPage: endPage)
            || (selectionMode == .surahs && selectedStartSurah > selectedEndSurah)
            || (selectionMode == .ahzab && selectedStartHizb > selectedEndHizb)
            || (selectionMode == .ajzaa && selectedStartJuz > selectedEndJuz)
    }

    private var calculatedPageCount: Int {
        if QuranStructure.isValidNonInvertedRange(startPage: startPage, endPage: endPage) {
            return (try? QuranStructure.computeNonInvertedPageCount(startPage: startPage, endPage: endPage)) ?? 1
        }
        return 1
    }

    public var body: some View {
        let forecast = QuranStructure.calculateKhatmaForecast(startPage: startPage, dailyPages: calculatedPageCount)

        ZStack {
            QuranColors.bgPrimary.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar

                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("وقت المنبه")
                            .font(QuranTypography.cairo(15, weight: .semibold))
                            .foregroundColor(QuranColors.textPrimary)

                        ModernIslamicTimePickerCard(hour12: $hour12, minute: $minute, isAm: $isAm)

                        countdownText

                        WirdConfigurationSection(
                            selectionMode: selectionMode,
                            selectedStartSurah: selectedStartSurah,
                            selectedEndSurah: selectedEndSurah,
                            selectedStartHizb: selectedStartHizb,
                            selectedEndHizb: selectedEndHizb,
                            selectedStartJuz: selectedStartJuz,
                            selectedEndJuz: selectedEndJuz,
                            startPage: startPage,
                            endPage: endPage,
                            useAyahCount: useAyahCount,
                            startAyahInput: startAyahInput,
                            dailyAyahCountInput: dailyAyahCountInput,
                            isAyahModeActive: isAyahModeActive,
                            isSequential: isSequential,
                            calculatedPageCount: calculatedPageCount,
                            forecast: forecast,
                            onSelectMode: syncPagesToMode,
                            onStartSurahSelected: selectStartSurah,
                            onEndSurahSelected: selectEndSurah,
                            onOpenStartSurahPicker: { showStartSurahPicker = true },
                            onOpenEndSurahPicker: { showEndSurahPicker = true },
                            onOpenStartPageInput: { showStartPageInputDialog = true },
                            onOpenEndPageInput: { showEndPageInputDialog = true },
                            onStartHizbSelected: { hizbNum in
                                selectedStartHizb = hizbNum
                                if selectedEndHizb < selectedStartHizb {
                                    selectedEndHizb = selectedStartHizb
                                    endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                                }
                                startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                                if endPage < startPage { endPage = startPage }
                            },
                            onEndHizbSelected: { hizbNum in
                                selectedEndHizb = hizbNum < selectedStartHizb ? selectedStartHizb : hizbNum
                                endPage = max(startPage, QuranStructure.getEndPageForHizb(selectedEndHizb))
                            },
                            onStartJuzSelected: { juzNum in
                                selectedStartJuz = juzNum
                                if selectedEndJuz < selectedStartJuz {
                                    selectedEndJuz = selectedStartJuz
                                    endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                                }
                                startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                                if endPage < startPage { endPage = startPage }
                            },
                            onEndJuzSelected: { juzNum in
                                selectedEndJuz = juzNum < selectedStartJuz ? selectedStartJuz : juzNum
                                endPage = max(startPage, QuranStructure.getEndPageForJuz(selectedEndJuz))
                            },
                            onStartPageSelected: { p in
                                startPage = p
                                if endPage < startPage { endPage = startPage }
                            },
                            onEndPageSelected: { p in
                                endPage = p < startPage ? startPage : p
                            },
                            onQuickPortionSelected: { targetEndPage in
                                selectionMode = .pages
                                useAyahCount = false
                                endPage = min(max(targetEndPage, startPage), QuranStructure.totalPages)
                            },
                            onToggleAyahCount: { enabled in
                                let sameSurah = QuranStructure.getSurahByNumber(selectedStartSurah)
                                useAyahCount = enabled
                                if enabled {
                                    startAyahInput = min(max(startAyahInput, 1), sameSurah.ayahCount)
                                    dailyAyahCountInput = min(max(dailyAyahCountInput, 1), sameSurah.ayahCount)
                                } else {
                                    startPage = sameSurah.startPage
                                    endPage = sameSurah.endPage
                                }
                            },
                            onStartAyahChanged: { startAyahInput = $0 },
                            onDailyAyahCountChanged: { dailyAyahCountInput = $0 },
                            onSequentialChanged: { isSequential = $0 }
                        )

                        let soundSummary = "\(AlarmUiFormatters.formatCompactSoundTitle(selectedSoundTitle)) • \(Int(volumeLevel))%\(isVibrate ? " • اهتزاز" : "")"
                        CollapsibleSettingCard(
                            title: "🔔 النغمة والصوت",
                            summary: soundSummary,
                            isExpanded: isSoundSectionExpanded,
                            accessibilityId: "section_sound",
                            onToggle: { withAnimation { isSoundSectionExpanded.toggle() } }
                        ) {
                            AlarmAudioAndSoundSection(
                                volumeLevel: $volumeLevel,
                                isVibrate: $isVibrate,
                                selectedSoundUri: selectedSoundUri,
                                selectedSoundTitle: selectedSoundTitle,
                                isPlayingPreview: isPlayingPreview,
                                onVolumeChanged: { newVol in
                                    if isPlayingPreview {
                                        audioManager.updateVolume(Int(newVol))
                                    }
                                },
                                onVolumeEditingChanged: { editing in
                                    if !editing && !isPlayingPreview {
                                        playPreview(uriStr: selectedSoundUri, isShort: true)
                                    }
                                },
                                onTogglePreview: {
                                    if isPlayingPreview { stopPreview() } else { playPreview(uriStr: selectedSoundUri) }
                                },
                                onSelectPreset: { uriStr, title in
                                    selectedSoundUri = uriStr
                                    selectedSoundTitle = AlarmUiFormatters.formatCompactSoundTitle(title)
                                    playPreview(uriStr: uriStr)
                                },
                                onOpenFileImporter: { showFileImporter = true }
                            )
                        }

                        CollapsibleSettingCard(
                            title: "🔁 التكرار",
                            summary: AlarmUiFormatters.formatRepeatDays(repeatDays),
                            isExpanded: isRepeatSectionExpanded,
                            accessibilityId: "section_repeat",
                            onToggle: { withAnimation { isRepeatSectionExpanded.toggle() } }
                        ) {
                            RepeatSelectionContent(daysList: daysList, repeatDays: $repeatDays)
                        }

                        CollapsibleSettingCard(
                            title: "🏷️ تسمية المنبه",
                            summary: label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "ورد القرآن الكريم" : label,
                            isExpanded: isLabelSectionExpanded,
                            accessibilityId: "section_label",
                            onToggle: { withAnimation { isLabelSectionExpanded.toggle() } }
                        ) {
                            LabelSelectionContent(label: $label) { preset in
                                label = preset
                                if preset.contains("سورة الملك") {
                                    selectionMode = .surahs
                                    selectedStartSurah = 67
                                    selectedEndSurah = 67
                                    useAyahCount = false
                                    startPage = 562
                                    endPage = 564
                                    isSequential = false
                                } else if preset.contains("سورة الكهف") {
                                    selectionMode = .surahs
                                    selectedStartSurah = 18
                                    selectedEndSurah = 18
                                    useAyahCount = false
                                    startPage = 293
                                    endPage = 304
                                    isSequential = false
                                }
                            }
                        }

                        if isEditMode, let onDelete, let editAlarm = alarmToEdit {
                            Button {
                                showDeleteConfirmDialog = true
                            } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: "trash.fill")
                                        .font(.system(size: 16))
                                    Text("حذف المنبه")
                                        .font(QuranTypography.cairo(15, weight: .semibold))
                                }
                                .foregroundColor(QuranColors.dangerRed)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(QuranColors.dangerRed.opacity(0.65), lineWidth: 1)
                                )
                            }
                            .accessibilityIdentifier("delete_alarm_button")
                            .padding(.top, 4)
                        }

                        if let err = validationBannerMessage {
                            Text(err)
                                .font(QuranTypography.cairo(12, weight: .semibold))
                                .foregroundColor(QuranColors.dangerRed)
                                .frame(maxWidth: .infinity, alignment: .center)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                }

                bottomBar
            }

            if showStartPageInputDialog {
                PageDirectInputModal(
                    title: "إدخال صفحة البداية",
                    initialPage: startPage,
                    minAllowedPage: 1,
                    onDismiss: { showStartPageInputDialog = false },
                    onConfirmPage: { entered in
                        startPage = entered
                        if endPage < entered { endPage = entered }
                    }
                )
            }

            if showEndPageInputDialog {
                PageDirectInputModal(
                    title: "إدخال صفحة الانتهاء",
                    initialPage: endPage,
                    minAllowedPage: startPage,
                    onDismiss: { showEndPageInputDialog = false },
                    onConfirmPage: { entered in
                        endPage = max(entered, startPage)
                    }
                )
            }

            if showSaveConfirmDialog {
                saveConfirmationModal
            }

            if showDeleteConfirmDialog, let editAlarm = alarmToEdit {
                deleteConfirmationModal(for: editAlarm)
            }
        }
        .task(id: "\(selectionMode.rawValue)_\(selectedStartSurah)_\(selectedEndSurah)_\(useAyahCount)_\(startAyahInput)_\(dailyAyahCountInput)") {
            await syncAyahPagesIfActive()
        }
        .sheet(isPresented: $showStartSurahPicker) {
            SurahSearchPickerSheet(title: "اختر سورة البداية") { surah in
                selectStartSurah(surah.number)
            }
            .environment(\.layoutDirection, .rightToLeft)
            .preferredColorScheme(.dark)
        }
        .sheet(isPresented: $showEndSurahPicker) {
            SurahSearchPickerSheet(title: "اختر سورة الانتهاء") { surah in
                selectEndSurah(surah.number)
            }
            .environment(\.layoutDirection, .rightToLeft)
            .preferredColorScheme(.dark)
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.audio],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let pickedURL = urls.first {
                if let imported = CustomSoundStorage.importAudio(from: pickedURL) {
                    if let copied = imported.copiedURL {
                        pendingCopiedURLs.append(copied)
                    }
                    selectedSoundUri = imported.uriString
                    selectedSoundTitle = AlarmUiFormatters.formatCompactSoundTitle(imported.displayName)
                    playPreview(uriStr: imported.uriString)
                } else {
                    validationBannerMessage = "تعذر اختيار الملف: تأكد أنه ملف صوتي صالح بحجم أقل من 15 ميجابايت"
                }
            }
        }
        .onDisappear {
            stopPreview()
            let keptPath = isAlarmSaved ? selectedSoundUri.flatMap { URL(string: $0)?.path } : nil
            for url in pendingCopiedURLs where url.path != keptPath {
                try? FileManager.default.removeItem(at: url)
            }
        }
    }

    private var topBar: some View {
        HStack(spacing: 8) {
            Button {
                stopPreview()
                onDismiss()
            } label: {
                Image(systemName: "arrow.forward")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(QuranColors.textPrimary)
                    .padding(6)
            }
            .accessibilityLabel("رجوع")

            Text(isEditMode ? "تعديل منبه الورد القرآني" : "ضبط منبه الورد القرآني")
                .font(QuranTypography.cairo(18, weight: .bold))
                .foregroundColor(QuranColors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)

            Spacer()

            Button {
                stopPreview()
                onPreviewAlarm(Int(volumeLevel), isVibrate, selectedSoundUri)
            } label: {
                Text("معاينة")
                    .font(QuranTypography.cairo(12, weight: .semibold))
                    .foregroundColor(QuranColors.activePillText)
                    .padding(.horizontal, 12)
                    .frame(height: 34)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(QuranColors.activePillBorder, lineWidth: 1)
                    )
            }
            .accessibilityIdentifier("preview_alarm_button")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(QuranColors.bgElevated)
    }

    private var countdownText: some View {
        let current24 = AlarmUiFormatters.toHour24(hour12: hour12, isAm: isAm)
        let previewAlarm = AlarmItem(id: 0, hour: current24, minute: minute, repeatDays: repeatDays)
        let trigger = previewAlarm.getNextTriggerMillis()
        let dayName: String? = (1...6).contains(repeatDays.count)
            ? AlarmUiFormatters.getDayName(
                AlarmItem.gregorianCalendar().component(.weekday, from: Date(timeIntervalSince1970: Double(trigger) / 1000.0))
            )
            : nil
        let text = "⏰ " + AlarmUiFormatters.formatNextAlarmCountdown(
            triggerMillis: trigger,
            targetDayName: dayName
        )

        return Text(text)
            .font(QuranTypography.cairo(13, weight: .medium))
            .foregroundColor(QuranColors.activePillText)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    private var bottomBar: some View {
        HStack(spacing: 10) {
            Button {
                if isCurrentRangeInverted {
                    validationBannerMessage = "لا يمكن حفظ نطاق معكوس: يجب أن تكون البداية قبل أو مساوية للنهاية"
                } else {
                    validationBannerMessage = nil
                    showSaveConfirmDialog = true
                }
            } label: {
                Text(isEditMode ? "حفظ التعديلات" : "حفظ المنبه")
                    .font(QuranTypography.cairo(16, weight: .semibold))
                    .foregroundColor(QuranColors.ctaText)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(QuranColors.ctaBg)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .disabled(isSaving)
            .accessibilityIdentifier("save_alarm_button")

            Button {
                stopPreview()
                onDismiss()
            } label: {
                Text("إلغاء")
                    .font(QuranTypography.cairo(15, weight: .semibold))
                    .foregroundColor(QuranColors.textSecondary)
                    .frame(width: 106, height: 52)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(QuranColors.borderSubtle, lineWidth: 1)
                    )
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(QuranColors.bgCard)
        .overlay(
            Rectangle()
                .frame(height: 1)
                .foregroundColor(QuranColors.borderSubtle),
            alignment: .top
        )
    }

    private var saveConfirmationModal: some View {
        let isVeryLarge = calculatedPageCount > 20
        let isModeratelyLarge = (11...20).contains(calculatedPageCount)
        let rangeText: String = {
            if isAyahModeActive {
                let surah = QuranStructure.getSurahByNumber(selectedStartSurah)
                let safeStart = min(max(startAyahInput, 1), surah.ayahCount)
                let safeDaily = min(max(dailyAyahCountInput, 1), surah.ayahCount)
                let safeEnd = QuranStructure.computeEndAyah(surahNumber: selectedStartSurah, startAyah: safeStart, dailyAyahCount: safeDaily) ?? safeStart
                return "سورة \(surah.nameArabic) • من آية \(safeStart) إلى آية \(safeEnd) (\(AlarmUiFormatters.formatAyahsCount(safeDaily)))"
            }
            return AlarmUiFormatters.formatPageRangeSummary(startPage: startPage, endPage: endPage)
        }()
        let estimatedTime: String = {
            if isAyahModeActive {
                let surah = QuranStructure.getSurahByNumber(selectedStartSurah)
                return AlarmUiFormatters.formatEstimatedReadingTime(
                    pageCount: calculatedPageCount,
                    ayahCount: min(max(dailyAyahCountInput, 1), surah.ayahCount),
                    surahTotalAyahs: surah.ayahCount
                )
            }
            return AlarmUiFormatters.formatEstimatedReadingTime(pageCount: calculatedPageCount)
        }()

        return ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 12) {
                Text("تأكيد حجم الورد قبل الحفظ")
                    .font(QuranTypography.cairo(18, weight: .bold))
                    .foregroundColor(QuranColors.textPrimary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("النطاق المختار: \(rangeText)")
                        .font(QuranTypography.cairo(14, weight: .semibold))
                        .foregroundColor(QuranColors.textPrimary)
                    Text("عدد الصفحات الإجمالي: \(AlarmUiFormatters.formatPagesCount(calculatedPageCount))")
                        .font(QuranTypography.cairo(13, weight: .medium))
                        .foregroundColor(QuranColors.activePillText)
                    Text("الوقت التقريبي للقراءة: \(estimatedTime)")
                        .font(QuranTypography.cairo(13, weight: .medium))
                        .foregroundColor(QuranColors.goldBadgeText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(QuranColors.bgElevated)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(QuranColors.borderSubtle, lineWidth: 1)
                )

                if isVeryLarge || isModeratelyLarge {
                    let warnColor = isVeryLarge ? QuranColors.dangerRed : QuranColors.warningOrange
                    let warnBg = isVeryLarge ? QuranColors.dangerRedBg : QuranColors.warningOrangeBg
                    let warnMsg = isVeryLarge
                        ? "تحذير: حجم الورد كبير جداً (\(AlarmUiFormatters.formatPagesCount(calculatedPageCount)))! لن يتم إغلاق شاشة المنبه إلا بعد إتمام قراءة هذا الورد كاملاً."
                        : "تنبيه: الورد المختار كبير نسبياً (\(AlarmUiFormatters.formatPagesCount(calculatedPageCount))). يرجى التأكد قبل الحفظ."
                    Text(warnMsg)
                        .font(QuranTypography.cairo(13, weight: .semibold))
                        .foregroundColor(warnColor)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(warnBg)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(warnColor, lineWidth: 1)
                        )
                }

                HStack(spacing: 10) {
                    Button {
                        showSaveConfirmDialog = false
                        persistAndSave()
                    } label: {
                        Text("موافق وحفظ")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(isVeryLarge ? QuranColors.textPrimary : QuranColors.ctaText)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(isVeryLarge ? QuranColors.dangerRed : QuranColors.ctaBg)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("confirm_save_wird_button")

                    Button {
                        showSaveConfirmDialog = false
                    } label: {
                        Text("تعديل")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.textSecondary)
                            .padding(.horizontal, 18)
                            .frame(height: 44)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .stroke(QuranColors.borderSubtle, lineWidth: 1)
                            )
                    }
                }
            }
            .padding(20)
            .background(QuranColors.bgCard)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(QuranColors.borderSubtle, lineWidth: 1)
            )
            .padding(.horizontal, 24)
        }
    }

    private func deleteConfirmationModal(for editAlarm: AlarmItem) -> some View {
        ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 14) {
                Text("تأكيد حذف المنبه")
                    .font(QuranTypography.cairo(18, weight: .bold))
                    .foregroundColor(QuranColors.textPrimary)

                Text("هل أنت متأكد من حذف منبه (\(editAlarm.label)) المقرر في \(AlarmUiFormatters.formatTime12Hour(hour: editAlarm.hour, minute: editAlarm.minute))؟")
                    .font(QuranTypography.cairo(14, weight: .regular))
                    .foregroundColor(QuranColors.textSecondary)

                HStack(spacing: 10) {
                    Button {
                        showDeleteConfirmDialog = false
                        stopPreview()
                        onDelete?(editAlarm.id)
                    } label: {
                        Text("حذف")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.textPrimary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(QuranColors.dangerRed)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .accessibilityIdentifier("confirm_delete_alarm_button")

                    Button {
                        showDeleteConfirmDialog = false
                    } label: {
                        Text("إلغاء")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.textSecondary)
                            .padding(.horizontal, 18)
                            .frame(height: 44)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .stroke(QuranColors.borderSubtle, lineWidth: 1)
                            )
                    }
                }
            }
            .padding(20)
            .background(QuranColors.bgCard)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(QuranColors.borderSubtle, lineWidth: 1)
            )
            .padding(.horizontal, 24)
        }
    }

    // MARK: - Helpers

    private func syncPagesToMode(_ mode: WirdSelectionMode) {
        selectionMode = mode
        if mode != .surahs {
            useAyahCount = false
        }
        switch mode {
        case .surahs:
            if !(selectedStartSurah == selectedEndSurah && useAyahCount) {
                let sPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
                let ePage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
                startPage = sPage
                endPage = max(sPage, ePage)
            }
        case .ahzab:
            let sPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
            let ePage = QuranStructure.getEndPageForHizb(selectedEndHizb)
            startPage = sPage
            endPage = max(sPage, ePage)
        case .ajzaa:
            let sPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
            let ePage = QuranStructure.getEndPageForJuz(selectedEndJuz)
            startPage = sPage
            endPage = max(sPage, ePage)
        case .pages:
            break
        }
    }

    private func selectStartSurah(_ surahNum: Int) {
        selectedStartSurah = surahNum
        if selectedEndSurah < selectedStartSurah {
            selectedEndSurah = selectedStartSurah
            endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
        }
        if selectedStartSurah != selectedEndSurah {
            useAyahCount = false
        } else {
            let maxAyahs = QuranStructure.getSurahByNumber(surahNum).ayahCount
            startAyahInput = min(max(startAyahInput, 1), maxAyahs)
            dailyAyahCountInput = min(max(dailyAyahCountInput, 1), maxAyahs)
        }
        if !useAyahCount {
            let sPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
            startPage = sPage
            if endPage < sPage {
                endPage = max(QuranStructure.getSurahByNumber(selectedEndSurah).endPage, sPage)
            }
        }
    }

    private func selectEndSurah(_ surahNum: Int) {
        let valid = surahNum < selectedStartSurah ? selectedStartSurah : surahNum
        selectedEndSurah = valid
        if selectedStartSurah != selectedEndSurah {
            useAyahCount = false
        } else {
            let maxAyahs = QuranStructure.getSurahByNumber(valid).ayahCount
            startAyahInput = min(max(startAyahInput, 1), maxAyahs)
            dailyAyahCountInput = min(max(dailyAyahCountInput, 1), maxAyahs)
        }
        if !useAyahCount {
            let ePage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
            endPage = max(startPage, ePage)
        }
    }

    private func syncAyahPagesIfActive() async {
        guard isAyahModeActive else { return }
        let surah = QuranStructure.getSurahByNumber(selectedStartSurah)
        let safeStart = min(max(startAyahInput, 1), surah.ayahCount)
        let safeEnd = QuranStructure.computeEndAyah(
            surahNumber: selectedStartSurah,
            startAyah: safeStart,
            dailyAyahCount: dailyAyahCountInput
        ) ?? safeStart
        if let range = await Task.detached(priority: .userInitiated, operation: {
            AyahInfoDatabase.shared.getAyahRangeInfo(
                surahNumber: selectedStartSurah,
                startAyah: safeStart,
                endAyah: safeEnd
            )
        }).value {
            startPage = range.startPage
            endPage = max(range.startPage, range.endPage)
        }
    }

    private func stopPreview() {
        audioManager.stopAlarm()
        isPlayingPreview = false
    }

    private func playPreview(uriStr: String?, isShort: Bool = false) {
        stopPreview()
        isPlayingPreview = true
        audioManager.previewSound(
            soundUriString: uriStr,
            volumeLevel: Int(volumeLevel),
            durationMs: isShort ? 2000 : nil,
            onError: {
                validationBannerMessage = "تعذر تشغيل النغمة المختارة"
            },
            onComplete: {
                isPlayingPreview = false
            }
        )
    }

    private func persistAndSave() {
        guard !isSaving else { return }
        if isCurrentRangeInverted {
            validationBannerMessage = "لا يمكن حفظ نطاق معكوس: يجب أن تكون البداية قبل أو مساوية للنهاية"
            return
        }
        isSaving = true
        stopPreview()

        Task {
            defer { isSaving = false }
            var resolvedStartPage = startPage
            var resolvedEndPage = endPage

            let savedSurahNumber: Int? = isAyahModeActive ? selectedStartSurah : nil
            let savedMaxAyahs = savedSurahNumber.map { QuranStructure.getSurahByNumber($0).ayahCount }
            let savedStartAyah = savedMaxAyahs.map { min(max(startAyahInput, 1), $0) }
            let savedDailyAyahCount = savedMaxAyahs.map { min(max(dailyAyahCountInput, 1), $0) }

            if let surahNum = savedSurahNumber, let sAyah = savedStartAyah, let daily = savedDailyAyahCount {
                let endAyah = QuranStructure.computeEndAyah(surahNumber: surahNum, startAyah: sAyah, dailyAyahCount: daily) ?? sAyah
                if let range = await Task.detached(priority: .userInitiated, operation: {
                    AyahInfoDatabase.shared.getAyahRangeInfo(surahNumber: surahNum, startAyah: sAyah, endAyah: endAyah)
                }).value {
                    resolvedStartPage = range.startPage
                    resolvedEndPage = range.endPage
                }
            }

            guard QuranStructure.isValidNonInvertedRange(startPage: resolvedStartPage, endPage: resolvedEndPage) else {
                validationBannerMessage = "لا يمكن حفظ نطاق معكوس: يجب أن تكون البداية قبل أو مساوية للنهاية"
                return
            }

            let final24Hour = AlarmUiFormatters.toHour24(hour12: hour12, isAm: isAm)
            let idToSave = alarmToEdit?.id ?? generateSafeAlarmId(existingAlarmIds: existingAlarmIds)
            let finalPageCount = (try? QuranStructure.computeNonInvertedPageCount(
                startPage: resolvedStartPage,
                endPage: resolvedEndPage
            )) ?? 1
            let trimmedLabel = label.trimmingCharacters(in: .whitespacesAndNewlines)

            let candidate = AlarmItem(
                id: idToSave,
                hour: final24Hour,
                minute: minute,
                repeatDays: repeatDays,
                isEnabled: true,
                isVibrationEnabled: isVibrate,
                label: trimmedLabel.isEmpty ? "ورد القرآن الكريم" : trimmedLabel,
                targetPage: resolvedStartPage,
                startPage: resolvedStartPage,
                pageCount: finalPageCount,
                volumeLevel: Int(volumeLevel),
                soundUri: selectedSoundUri,
                isSequential: isSequential,
                soundTitle: AlarmUiFormatters.formatCompactSoundTitle(selectedSoundTitle),
                wirdSurahNumber: savedSurahNumber,
                startAyah: savedStartAyah,
                dailyAyahCount: savedDailyAyahCount
            )
            let explicitTrigger: Int64? = repeatDays.isEmpty ? candidate.getNextTriggerMillis() : nil
            let savedAlarm = candidate.copyWith(explicitTriggerMillis: .some(explicitTrigger))
            isAlarmSaved = true
            onSave(savedAlarm)
        }
    }

    private func generateSafeAlarmId(existingAlarmIds: Set<Int>) -> Int {
        let startCandidate = max(Int(AlarmItem.currentMillis() % 99_999), 1)
        var candidate = startCandidate
        for _ in 0..<99_999 {
            if !existingAlarmIds.contains(candidate) { return candidate }
            candidate = (candidate % 99_999) + 1
        }
        return startCandidate
    }
}

private struct PageDirectInputModal: View {
    let title: String
    let initialPage: Int
    let minAllowedPage: Int
    let onDismiss: () -> Void
    let onConfirmPage: (Int) -> Void

    @State private var inputText: String
    @State private var errorMessage: String?

    init(
        title: String,
        initialPage: Int,
        minAllowedPage: Int,
        onDismiss: @escaping () -> Void,
        onConfirmPage: @escaping (Int) -> Void
    ) {
        self.title = title
        self.initialPage = initialPage
        self.minAllowedPage = minAllowedPage
        self.onDismiss = onDismiss
        self.onConfirmPage = onConfirmPage
        _inputText = State(initialValue: String(initialPage))
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 10) {
                Text(title)
                    .font(QuranTypography.cairo(18, weight: .bold))
                    .foregroundColor(QuranColors.textPrimary)

                Text("أدخل رقم الصفحة المطلوبة (من \(minAllowedPage) إلى \(QuranStructure.totalPages)):")
                    .font(QuranTypography.cairo(13, weight: .regular))
                    .foregroundColor(QuranColors.textSecondary)

                TextField("مثال: 293", text: Binding(
                    get: { inputText },
                    set: { raw in
                        inputText = String(raw.filter(\.isNumber).prefix(3))
                        errorMessage = nil
                    }
                ))
                .keyboardType(.numberPad)
                .font(QuranTypography.cairo(15, weight: .semibold))
                .foregroundColor(QuranColors.textPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(QuranColors.bgElevated)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(QuranColors.activePillBorder, lineWidth: 1)
                )

                if let errorMessage {
                    Text(errorMessage)
                        .font(QuranTypography.cairo(12, weight: .regular))
                        .foregroundColor(QuranColors.dangerRed)
                }

                HStack(spacing: 10) {
                    Button {
                        guard let parsed = Int(inputText), (1...QuranStructure.totalPages).contains(parsed) else {
                            errorMessage = "يرجى إدخال رقم صفحة صحيح بين 1 و \(QuranStructure.totalPages)"
                            return
                        }
                        guard parsed >= minAllowedPage else {
                            errorMessage = "يجب أن تكون صفحة النهاية (\(parsed)) مساوية أو بعد صفحة البداية (\(minAllowedPage))"
                            return
                        }
                        onConfirmPage(parsed)
                        onDismiss()
                    } label: {
                        Text("تطبيق")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.ctaText)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(QuranColors.ctaBg)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }

                    Button {
                        onDismiss()
                    } label: {
                        Text("إلغاء")
                            .font(QuranTypography.cairo(14, weight: .semibold))
                            .foregroundColor(QuranColors.textSecondary)
                            .padding(.horizontal, 18)
                            .frame(height: 44)
                    }
                }
            }
            .padding(20)
            .background(QuranColors.bgCard)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(QuranColors.borderSubtle, lineWidth: 1)
            )
            .padding(.horizontal, 24)
        }
    }
}
