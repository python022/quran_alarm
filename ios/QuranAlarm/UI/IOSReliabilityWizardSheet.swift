import SwiftUI

public struct IOSReliabilityWizardSheet: View {
    public let status: IOSDeviceReadinessStatus
    public let onRefresh: () -> Void

    @Environment(\.dismiss) private var dismiss

    public init(status: IOSDeviceReadinessStatus, onRefresh: @escaping () -> Void) {
        self.status = status
        self.onRefresh = onRefresh
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("جهاز iPhone (نظام iOS)")
                            .font(QuranTypography.cairo(13, weight: .semibold))
                            .foregroundColor(QuranColors.goldBadgeText)
                        Text("لضمان رنين منبه القرآن الكريم على شاشة القفل، يعتمد نظام iOS على الإشعارات الحساسة للوقت (Time-Sensitive Notifications) ومستوى صوت الوسائط داخل التطبيق عند القراءة.")
                            .font(QuranTypography.cairo(12, weight: .regular))
                            .foregroundColor(QuranColors.textSecondary)
                            .lineSpacing(3)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(QuranColors.bgElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(QuranColors.borderSubtle, lineWidth: 1)
                    )

                    Text("متطلبات التنبيه الموثوق على iOS")
                        .font(QuranTypography.cairo(13, weight: .bold))
                        .foregroundColor(QuranColors.activePillText)

                    stepRow(
                        title: "إذن الإشعارات",
                        isGranted: status.isNotificationsAuthorized,
                        actionLabel: "فتح الإعدادات"
                    ) {
                        IOSDeviceReadinessHelper.openAppNotificationSettings()
                    }

                    stepRow(
                        title: "تفعيل صوت الإشعارات",
                        isGranted: status.isSoundEnabled,
                        actionLabel: "فتح الإعدادات"
                    ) {
                        IOSDeviceReadinessHelper.openAppNotificationSettings()
                    }

                    stepRow(
                        title: "الإشعارات الحساسة للوقت (Time-Sensitive)",
                        isGranted: status.isTimeSensitiveEnabled,
                        actionLabel: "فتح الإعدادات"
                    ) {
                        IOSDeviceReadinessHelper.openAppNotificationSettings()
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("ملاحظات مهمة لنظام iOS:")
                            .font(QuranTypography.cairo(13, weight: .semibold))
                            .foregroundColor(QuranColors.goldBadgeText)
                        Text("• تأكد من إلغاء زر الصامت الجانبي ليصل صوت إشعار القفل بوضوح.\n• عند فتح شاشة المنبه داخل التطبيق، يعمل الصوت مباشرة عبر مسار الوسائط ويتوقف تلقائياً بمجرد ضغط «بدء قراءة الورد».")
                            .font(QuranTypography.cairo(12, weight: .regular))
                            .foregroundColor(QuranColors.textSecondary)
                            .lineSpacing(4)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(QuranColors.goldBadgeBg)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(QuranColors.goldBadgeBorder, lineWidth: 1)
                    )
                }
                .padding(16)
            }
            .background(QuranColors.bgPrimary.ignoresSafeArea())
            .navigationTitle("معالج موثوقية المنبه")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("تم") {
                        onRefresh()
                        dismiss()
                    }
                    .font(QuranTypography.cairo(14, weight: .semibold))
                    .foregroundColor(QuranColors.ctaBg)
                }
            }
        }
        .onAppear(perform: onRefresh)
    }

    private func stepRow(
        title: String,
        isGranted: Bool,
        actionLabel: String,
        onAction: @escaping () -> Void
    ) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(QuranTypography.cairo(13, weight: .medium))
                    .foregroundColor(QuranColors.textPrimary)
                Text(isGranted ? "مفعّل ✓" : "يتطلب تفعيل")
                    .font(QuranTypography.cairo(11, weight: .regular))
                    .foregroundColor(isGranted ? QuranColors.activePillText : QuranColors.warningOrange)
            }
            Spacer()
            if !isGranted {
                Button(actionLabel, action: onAction)
                    .font(QuranTypography.cairo(12, weight: .semibold))
                    .foregroundColor(QuranColors.activePillText)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(QuranColors.bgElevated)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(QuranColors.borderSubtle, lineWidth: 1)
        )
    }
}
