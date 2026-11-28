import SwiftUI

public struct AlarmAlertView: View {
    @ObservedObject public var viewModel: AlarmAlertViewModel
    public let onDismissFinished: () -> Void

    @State private var pulseBell = false

    public init(viewModel: AlarmAlertViewModel, onDismissFinished: @escaping () -> Void) {
        self.viewModel = viewModel
        self.onDismissFinished = onDismissFinished
    }

    public var body: some View {
        let state = viewModel.uiState
        Group {
            if state.stage == .mushafReading {
                mushafReadingStage(state: state)
            } else {
                ringingStage(state: state)
            }
        }
        .background(
            (state.stage == .mushafReading ? QuranColors.mushafPaper : QuranColors.bgPrimary)
                .ignoresSafeArea()
        )
    }

    // MARK: - Stage 1: Ringing Screen

    private func ringingStage(state: MushafReaderUiState) -> some View {
        ScrollView {
            VStack(spacing: 24) {
                TimelineView(.periodic(from: .now, by: 1.0)) { context in
                    let nowMillis = Int64((context.date.timeIntervalSince1970 * 1000.0).rounded())
                    VStack(spacing: 8) {
                        if state.alarmId < 0 {
                            Text("معاينة تجريبية")
                                .font(QuranTypography.cairo(12, weight: .bold))
                                .foregroundColor(QuranColors.readerSky)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 4)
                                .background(QuranColors.readerSky.opacity(0.16))
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .padding(.bottom, 6)
                        }

                        Text(AlarmUiFormatters.formatCurrentDate(timeMillis: nowMillis))
                            .font(QuranTypography.cairo(18, weight: .medium))
                            .foregroundColor(QuranColors.textSecondary)

                        Text(AlarmUiFormatters.formatCurrentDigitalTime(timeMillis: nowMillis))
                            .font(QuranTypography.cairo(64, weight: .bold))
                            .foregroundColor(.white)
                            .environment(\.layoutDirection, .leftToRight)

                        ZStack {
                            Circle()
                                .fill(QuranColors.readerEmerald.opacity(0.2))
                                .frame(width: 72, height: 72)
                            Image(systemName: "bell.fill")
                                .font(.system(size: 32))
                                .foregroundColor(QuranColors.readerEmerald)
                        }
                        .scaleEffect(pulseBell ? 1.15 : 1.0)
                        .padding(.top, 8)
                        .onAppear {
                            withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                                pulseBell = true
                            }
                        }
                    }
                    .padding(.top, 20)
                }

                // Wird overview card
                VStack(spacing: 10) {
                    Text(state.alarmLabel)
                        .font(QuranTypography.cairo(20, weight: .bold))
                        .foregroundColor(QuranColors.textPrimary)
                        .multilineTextAlignment(.center)

                    if state.isAyahMode, let surahNum = state.wirdSurahNumber {
                        let surah = QuranStructure.getSurahByNumber(surahNum)
                        Text("ورد اليوم: سورة \(surah.nameArabic) (الآيات \(state.startAyah ?? 1) - \(state.endAyah ?? 1))")
                            .font(QuranTypography.cairo(17, weight: .bold))
                            .foregroundColor(QuranColors.goldBadgeText)
                            .multilineTextAlignment(.center)

                        let pagesLabel = state.startPage == state.endPage
                            ? "صفحة \(state.startPage) (صفحة واحدة)"
                            : "صفحة \(state.startPage) إلى \(state.endPage)"
                        Text("(\(AlarmUiFormatters.formatAyahsCount(state.dailyAyahCount ?? 1)) • \(pagesLabel))")
                            .font(QuranTypography.cairo(13, weight: .medium))
                            .foregroundColor(QuranColors.activePillText)
                    } else {
                        let titleText: String = {
                            if state.pageCount == 1 {
                                let surah = QuranStructure.getSurahAtPage(state.startPage)
                                return "ورد اليوم: سورة \(surah.nameArabic) • صفحة \(state.startPage)"
                            }
                            return "ورد اليوم: صفحة \(state.startPage) إلى \(state.endPage)"
                        }()
                        let countText = state.pageCount == 1
                            ? "(صفحة واحدة)"
                            : "(\(AlarmUiFormatters.formatPagesCount(state.pageCount)) متتالية)"

                        Text(titleText)
                            .font(QuranTypography.cairo(17, weight: .bold))
                            .foregroundColor(QuranColors.goldBadgeText)
                            .multilineTextAlignment(.center)

                        Text(countText)
                            .font(QuranTypography.cairo(13, weight: .medium))
                            .foregroundColor(QuranColors.activePillText)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(20)
                .background(QuranColors.bgCard)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(QuranColors.borderSubtle, lineWidth: 1)
                )

                VStack(spacing: 12) {
                    Button {
                        viewModel.startReadingWird()
                    } label: {
                        Text("📖 بدء قراءة الورد")
                            .font(QuranTypography.cairo(18, weight: .bold))
                            .foregroundColor(QuranColors.ctaText)
                            .frame(maxWidth: .infinity)
                            .frame(height: 60)
                            .background(QuranColors.ctaBg)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .accessibilityIdentifier("start_reading_wird_button")

                    if state.isSnoozeEnabled {
                        Button {
                            viewModel.snooze(minutes: 5, onSnooze: onDismissFinished)
                        } label: {
                            Text(state.isDismissedOrSnoozed ? "جاري التأجيل..." : "تأجيل (5 دقائق)")
                                .font(QuranTypography.cairo(14, weight: .medium))
                                .foregroundColor(state.isDismissedOrSnoozed ? QuranColors.textSecondary : Color(argb: 0xFFE2E8F0))
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(state.isDismissedOrSnoozed ? QuranColors.readerSlateCard : QuranColors.readerSlateBorder)
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .frame(maxWidth: 260)
                        .disabled(state.isDismissedOrSnoozed)
                        .accessibilityIdentifier("snooze_alarm_button")
                    }

                    if state.alarmId < 0 {
                        Button {
                            onDismissFinished()
                        } label: {
                            Text("إنهاء المعاينة")
                                .font(QuranTypography.cairo(14, weight: .bold))
                                .foregroundColor(QuranColors.readerSky)
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(QuranColors.readerSky, lineWidth: 1)
                                )
                        }
                        .frame(maxWidth: 260)
                        .accessibilityIdentifier("exit_preview_button")
                    }
                }
                .padding(.top, 8)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 24)
        }
        .background(QuranColors.bgPrimary.ignoresSafeArea())
    }

    // MARK: - Stage 2: Madinah Mushaf Reading Screen

    private func mushafReadingStage(state: MushafReaderUiState) -> some View {
        VStack(spacing: 0) {
            topReaderBar(state: state)

            ZStack {
                if let loadError = state.loadError {
                    errorOverlay(message: loadError)
                } else {
                    QuranPageView(
                        pageNumber: state.currentPage,
                        pageImage: state.currentImage,
                        lines: state.currentLines,
                        activeLine: state.activeLine,
                        onLineClicked: { line in
                            if !state.isLoading {
                                viewModel.onLineClicked(line)
                            }
                        }
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            bottomReaderBar(state: state)
        }
    }

    private func topReaderBar(state: MushafReaderUiState) -> some View {
        let canGoPrev = state.hasPrevPageInWird && !state.isLoading
        let canGoNext = state.canNavigateNextInAlert && !state.isLoading
        let totalWirdPages = max(state.wirdPages.count, 1)
        let sessionProgress = min(max(Double(state.completedPages.count) / Double(totalWirdPages), 0), 1)

        return VStack(spacing: 0) {
            HStack {
                Button {
                    viewModel.goToPreviousPage()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.right")
                            .font(.system(size: 13, weight: .semibold))
                        Text("السابقة")
                            .font(QuranTypography.cairo(12, weight: .medium))
                    }
                    .foregroundColor(canGoPrev ? .white : QuranColors.textMuted)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(canGoPrev ? Color.white.opacity(0.45) : QuranColors.borderSubtle, lineWidth: 1)
                    )
                }
                .disabled(!canGoPrev)
                .accessibilityIdentifier("prev_page_button")

                Spacer()

                VStack(spacing: 2) {
                    if state.isAyahMode, let surahNum = state.wirdSurahNumber {
                        let surah = QuranStructure.getSurahByNumber(surahNum)
                        Text("سورة \(surah.nameArabic) • آية \(state.startAyah ?? 1) - \(state.endAyah ?? 1)")
                            .font(QuranTypography.cairo(13, weight: .bold))
                            .foregroundColor(QuranColors.readerSky)
                    } else {
                        Text("صفحة \(state.currentPage) من 604")
                            .font(QuranTypography.cairo(15, weight: .bold))
                            .foregroundColor(QuranColors.readerSky)
                    }
                    Text("ص \(state.currentPage) • السطر \(state.activeLine) من \(state.lastReadableLineOnPage)")
                        .font(QuranTypography.cairo(12, weight: .regular))
                        .foregroundColor(QuranColors.readerEmerald)
                        .accessibilityIdentifier("mushaf_line_status_label")
                }

                Spacer()

                Button {
                    viewModel.goToNextPage()
                } label: {
                    HStack(spacing: 4) {
                        Text("التالية")
                            .font(QuranTypography.cairo(12, weight: .medium))
                        Image(systemName: "arrow.left")
                            .font(.system(size: 13, weight: .semibold))
                    }
                    .foregroundColor(canGoNext ? .white : QuranColors.textMuted)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(canGoNext ? Color.white.opacity(0.45) : QuranColors.borderSubtle, lineWidth: 1)
                    )
                }
                .disabled(!canGoNext)
                .accessibilityIdentifier("next_page_button")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle().fill(QuranColors.borderSubtle)
                    Rectangle()
                        .fill(QuranColors.ctaBg)
                        .frame(width: max(0, geo.size.width * CGFloat(sessionProgress)))
                }
            }
            .frame(height: 3)
        }
        .background(QuranColors.bgElevated)
    }

    private func errorOverlay(message: String) -> some View {
        ZStack {
            QuranColors.bgPrimary
            VStack(spacing: 12) {
                Text(message)
                    .font(QuranTypography.cairo(15, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                Button {
                    viewModel.retryLoadPage()
                } label: {
                    Text("إعادة المحاولة")
                        .font(QuranTypography.cairo(14, weight: .bold))
                        .foregroundColor(QuranColors.bgPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(QuranColors.readerSky)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                Button {
                    viewModel.emergencyExitOnError(onExit: onDismissFinished)
                } label: {
                    Text("تأجيل 5 دقائق والخروج الآمن")
                        .font(QuranTypography.cairo(14, weight: .medium))
                        .foregroundColor(QuranColors.readerAmber)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(QuranColors.warningOrange, lineWidth: 1)
                        )
                }
            }
            .padding(20)
            .background(QuranColors.readerSlateCard)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(QuranColors.dangerRed.opacity(0.5), lineWidth: 1)
            )
            .padding(24)
        }
    }

    private func bottomReaderBar(state: MushafReaderUiState) -> some View {
        let atEndWithoutFullCompletion = !state.isDismissEnabled
            && !state.hasNextPageInWird
            && state.activeLine >= state.lastReadableLineOnPage

        return VStack(spacing: 8) {
            if state.loadError != nil {
                Button {
                    viewModel.emergencyExitOnError(onExit: onDismissFinished)
                } label: {
                    Text("تأجيل 5 دقائق والخروج الآمن")
                        .font(QuranTypography.cairo(14, weight: .bold))
                        .foregroundColor(QuranColors.readerAmber)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(QuranColors.readerSlateCard)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(QuranColors.warningOrange, lineWidth: 1)
                        )
                }
            } else if state.isDismissEnabled {
                Button {
                    viewModel.completeWird(onComplete: onDismissFinished)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 16, weight: .bold))
                        Text("إتمام الورد المبارك والخروج")
                            .font(QuranTypography.cairo(15, weight: .bold))
                    }
                    .foregroundColor(QuranColors.ctaText)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(QuranColors.ctaBg)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .accessibilityIdentifier("complete_wird_dismiss_button")
            } else if atEndWithoutFullCompletion {
                Button {
                    viewModel.goToFirstIncompletePage()
                } label: {
                    Text("إكمال الصفحات المتبقية ←")
                        .font(QuranTypography.cairo(14, weight: .semibold))
                        .foregroundColor(QuranColors.readerAmber)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(QuranColors.readerSlateCard)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(QuranColors.warningOrange, lineWidth: 1)
                        )
                }
                .disabled(state.isLoading)
            } else {
                let btnText: String = {
                    if state.activeLine < state.lastReadableLineOnPage {
                        return "السطر التالي"
                    } else if state.hasNextPageInWird {
                        return "الصفحة التالية ←"
                    } else {
                        return "السطر التالي"
                    }
                }()
                Button {
                    viewModel.advanceToNextLineOrPage()
                } label: {
                    HStack(spacing: 6) {
                        Text(btnText)
                            .font(QuranTypography.cairo(14, weight: .semibold))
                        if !btnText.contains("←") {
                            Image(systemName: "arrow.left")
                                .font(.system(size: 14, weight: .semibold))
                        }
                    }
                    .foregroundColor(QuranColors.readerSky)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(QuranColors.readerSlateCard)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(QuranColors.readerSlateBorder, lineWidth: 1)
                    )
                }
                .disabled(state.isLoading)
                .accessibilityIdentifier("next_line_or_page_button")
            }

            if state.alarmId < 0 {
                Button {
                    onDismissFinished()
                } label: {
                    Text("إنهاء المعاينة")
                        .font(QuranTypography.cairo(14, weight: .bold))
                        .foregroundColor(QuranColors.readerSky)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(QuranColors.readerSky, lineWidth: 1)
                        )
                }
                .accessibilityIdentifier("exit_preview_button")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(QuranColors.bgPrimary)
    }
}
