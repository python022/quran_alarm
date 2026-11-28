import SwiftUI

public struct AlarmListView: View {
    @ObservedObject public var viewModel: AlarmViewModel
    @ObservedObject public var sessionCoordinator: AlarmSessionCoordinator

    @State private var showReliabilityWizard = false

    public init(viewModel: AlarmViewModel, sessionCoordinator: AlarmSessionCoordinator) {
        self.viewModel = viewModel
        self.sessionCoordinator = sessionCoordinator
    }

    public var body: some View {
        let state = viewModel.uiState
        let uniqueAlarms = state.alarms
        let hasEnabledAlarm = uniqueAlarms.contains(where: \.isEnabled)
        let readiness = state.deviceReadinessStatus
        let needsAttention = readiness?.needsAttention == true

        ZStack(alignment: .bottomLeading) {
            QuranColors.bgPrimary.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar(countdown: state.nextAlarmCountdown, needsAttention: needsAttention)

                ScrollView {
                    VStack(spacing: 12) {
                        if hasEnabledAlarm && needsAttention {
                            permissionsGuidanceBanner
                        }

                        if let khatma = state.khatmaProgress {
                            WirdProgressCardView(
                                title: "📖 مسيرة ختمة القرآن الكريم",
                                badgeText: "\(Int(khatma.progressPercentage))% (صفحة \(khatma.currentStartPage) من 604)",
                                progress: min(max(Double(khatma.progressPercentage) / 100.0, 0), 1),
                                summary: khatma.forecastSummary
                            )
                        } else if let surahWird = state.surahWirdProgress {
                            WirdProgressCardView(
                                title: "📖 مسيرة الورد في سورة \(surahWird.surahName)",
                                badgeText: "\(Int(surahWird.progressPercentage))% (آية \(surahWird.currentStartAyah) من \(surahWird.totalAyahs))",
                                progress: min(max(Double(surahWird.progressPercentage) / 100.0, 0), 1),
                                summary: surahWird.forecastSummary
                            )
                        }

                        HStack {
                            Text("قائمة المنبهات (\(uniqueAlarms.count))")
                                .font(QuranTypography.cairo(16, weight: .semibold))
                                .foregroundColor(QuranColors.textPrimary)
                                .accessibilityIdentifier("alarms_count_label")
                            Spacer()
                            Text("انقر على أي منبه لتعديله")
                                .font(QuranTypography.cairo(12, weight: .medium))
                                .foregroundColor(QuranColors.textSecondary)
                        }
                        .padding(.top, 2)

                        if state.isLoading {
                            ProgressView()
                                .tint(QuranColors.ctaBg)
                                .padding(.top, 40)
                        } else if uniqueAlarms.isEmpty {
                            emptyStateCard
                        } else {
                            LazyVStack(spacing: 10) {
                                ForEach(uniqueAlarms) { alarm in
                                    AlarmCardView(
                                        alarm: alarm,
                                        onTap: { viewModel.openEditDialog(alarm) },
                                        onToggle: { isEnabled in viewModel.toggleAlarm(alarm, isEnabled: isEnabled) }
                                    )
                                }
                            }
                            .padding(.bottom, 88)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
            }

            // Floating Action Button (+)
            Button {
                viewModel.openAddDialog()
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(QuranColors.ctaText)
                    .frame(width: 56, height: 56)
                    .background(QuranColors.ctaBg)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(color: Color.black.opacity(0.35), radius: 8, x: 0, y: 4)
            }
            .accessibilityLabel("إضافة منبه")
            .accessibilityIdentifier("add_alarm_fab")
            .padding(.horizontal, 20)
            .padding(.bottom, 20)

            // Toast banner
            if let message = state.userMessage {
                VStack {
                    Spacer()
                    Text(message)
                        .font(QuranTypography.cairo(13, weight: .semibold))
                        .foregroundColor(QuranColors.textPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(QuranColors.bgElevated)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(QuranColors.activePillBorder.opacity(0.6), lineWidth: 1))
                        .shadow(radius: 6)
                        .padding(.bottom, 88)
                }
                .frame(maxWidth: .infinity)
                .transition(.opacity)
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                        viewModel.clearUserMessage()
                    }
                }
            }
        }
        .sheet(isPresented: $showReliabilityWizard, onDismiss: {
            viewModel.refreshDeviceReadiness()
        }) {
            IOSReliabilityWizardSheet(
                status: readiness ?? IOSDeviceReadinessStatus(),
                onRefresh: { viewModel.refreshDeviceReadiness() }
            )
            .environment(\.layoutDirection, .rightToLeft)
            .preferredColorScheme(.dark)
        }
    }

    private func topBar(countdown: String, needsAttention: Bool) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("منبه القرآن الكريم")
                    .font(QuranTypography.cairo(20, weight: .bold))
                    .foregroundColor(QuranColors.textPrimary)

                let hasActive = !countdown.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    && !countdown.contains("لا توجد")
                if hasActive {
                    Text("⏰ \(countdown)")
                        .font(QuranTypography.cairo(12, weight: .medium))
                        .foregroundColor(QuranColors.activePillText)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 2)
                        .background(QuranColors.activePillBg)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(QuranColors.activePillBorder, lineWidth: 1))
                } else {
                    Text("لا توجد منبهات نشطة")
                        .font(QuranTypography.cairo(12, weight: .regular))
                        .foregroundColor(QuranColors.textSecondary)
                }
            }

            Spacer()

            Button {
                viewModel.refreshDeviceReadiness()
                showReliabilityWizard = true
            } label: {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 19))
                    .foregroundColor(needsAttention ? QuranColors.warningOrange : QuranColors.textSecondary)
                    .padding(8)
            }
            .accessibilityLabel("معالج موثوقية الجهاز")
            .accessibilityIdentifier("reliability_wizard_button")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(QuranColors.bgElevated)
    }

    private var permissionsGuidanceBanner: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("💡")
                    .font(.system(size: 16))
                Text("مطلوب مراجعة إعدادات تنبيهات الآيفون")
                    .font(QuranTypography.cairo(15, weight: .semibold))
                    .foregroundColor(QuranColors.textPrimary)
            }
            Text("لضمان وصول إشعار المنبه بالصوت في موعده حتى مع تفعيل نمط التركيز، يرجى تفعيل الإشعارات والأصوات والإشعارات الحساسة للوقت.")
                .font(QuranTypography.cairo(12, weight: .regular))
                .foregroundColor(QuranColors.textSecondary)
                .lineSpacing(3)

            HStack(spacing: 8) {
                Button {
                    IOSDeviceReadinessHelper.openAppNotificationSettings()
                } label: {
                    Text("فتح إعدادات الإشعارات")
                        .font(QuranTypography.cairo(12, weight: .semibold))
                        .foregroundColor(QuranColors.ctaText)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(QuranColors.warningOrange)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                Button {
                    showReliabilityWizard = true
                } label: {
                    Text("معالج الجهاز")
                        .font(QuranTypography.cairo(12, weight: .semibold))
                        .foregroundColor(QuranColors.warningOrange)
                        .padding(.horizontal, 12)
                        .frame(height: 40)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(QuranColors.warningOrange.opacity(0.6), lineWidth: 1)
                        )
                }
            }
        }
        .padding(14)
        .background(QuranColors.warningOrangeBg)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(QuranColors.warningOrange.opacity(0.55), lineWidth: 1)
        )
    }

    private var emptyStateCard: some View {
        VStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(QuranColors.activePillBg)
                    .frame(width: 68, height: 68)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(QuranColors.activePillBorder.opacity(0.5), lineWidth: 1)
                    )
                Image(systemName: "book.closed.fill")
                    .font(.system(size: 30))
                    .foregroundColor(QuranColors.ctaBg)
            }
            .padding(.bottom, 4)

            Text("ابدأ يومك بنور القرآن والبركة")
                .font(QuranTypography.cairo(16, weight: .semibold))
                .foregroundColor(QuranColors.textPrimary)
                .multilineTextAlignment(.center)

            Text("لم تقم بضبط أي منبه بعد. اضغط على زر (+) في الأسفل لضبط منبه الفجر ووردك القرآني اليومي مع ميزة تتبع التلاوة.")
                .font(QuranTypography.cairo(13, weight: .regular))
                .foregroundColor(QuranColors.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(3)

            Button {
                viewModel.openAddDialog()
            } label: {
                Text("إضافة منبه جديد")
                    .font(QuranTypography.cairo(15, weight: .semibold))
                    .foregroundColor(QuranColors.ctaText)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(QuranColors.ctaBg)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(QuranColors.bgCard)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(QuranColors.borderSubtle, lineWidth: 1)
        )
        .padding(.top, 16)
    }
}

private struct WirdProgressCardView: View {
    let title: String
    let badgeText: String
    let progress: Double
    let summary: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title)
                    .font(QuranTypography.cairo(15, weight: .semibold))
                    .foregroundColor(QuranColors.textPrimary)
                Spacer()
                Text(badgeText)
                    .font(QuranTypography.cairo(12, weight: .semibold))
                    .foregroundColor(QuranColors.activePillText)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(QuranColors.bgPrimary)
                        .frame(height: 8)
                    Capsule()
                        .fill(QuranColors.ctaBg)
                        .frame(width: max(0, geo.size.width * CGFloat(progress)), height: 8)
                }
            }
            .frame(height: 8)

            GoldBadgeSurface {
                Text(summary)
                    .font(QuranTypography.cairo(12, weight: .medium))
                    .foregroundColor(QuranColors.goldBadgeText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
            }
        }
        .padding(14)
        .background(QuranColors.bgElevated)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(QuranColors.borderSubtle, lineWidth: 1)
        )
    }
}
