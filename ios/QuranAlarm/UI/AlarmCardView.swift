import SwiftUI

/// Unified Dark Slate + Emerald + Gold alarm card matching Android AlarmCardItem.kt.
public struct AlarmCardView: View {
    public let alarm: AlarmItem
    public let onTap: () -> Void
    public let onToggle: (Bool) -> Void

    public init(alarm: AlarmItem, onTap: @escaping () -> Void, onToggle: @escaping (Bool) -> Void) {
        self.alarm = alarm
        self.onTap = onTap
        self.onToggle = onToggle
    }

    public var body: some View {
        let hasActiveSnooze = alarm.isEnabled
            && alarm.snoozeTriggerMillis != nil
            && (alarm.snoozeTriggerMillis ?? 0) > AlarmItem.currentMillis()
        let rawSoundTitle = alarm.soundTitle ?? AlarmSoundPresets.getTitleForUri(alarm.soundUri)
        let compactSoundTitle = AlarmUiFormatters.formatCompactSoundTitle(rawSoundTitle)

        VStack(alignment: .leading, spacing: 8) {
            // Top Row: LTR time + AM/PM + Wird type pill + Toggle
            HStack(alignment: .center, spacing: 8) {
                HStack(alignment: .center, spacing: 8) {
                    Text(AlarmUiFormatters.formatDigits12HourPadded(hour24: alarm.hour, minute: alarm.minute))
                        .font(QuranTypography.cairo(32, weight: .bold))
                        .foregroundColor(alarm.isEnabled ? QuranColors.textPrimary : QuranColors.textMuted)
                        .lineLimit(1)
                        .environment(\.layoutDirection, .leftToRight)

                    Text(AlarmUiFormatters.getPeriodLabel(alarm.hour))
                        .font(QuranTypography.cairo(15, weight: .semibold))
                        .foregroundColor(alarm.isEnabled ? QuranColors.textSecondary : QuranColors.textMuted)
                        .lineLimit(1)

                    Text(alarm.isSequential ? "ورد متسلسل" : "ورد ثابت")
                        .font(QuranTypography.cairo(11, weight: .semibold))
                        .foregroundColor(alarm.isSequential ? QuranColors.activePillText : QuranColors.textSecondary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(alarm.isSequential ? QuranColors.activePillBg : QuranColors.bgElevated)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(
                                alarm.isSequential ? QuranColors.activePillBorder : QuranColors.borderSubtle,
                                lineWidth: 1
                            )
                        )
                }

                Spacer(minLength: 4)

                Toggle(
                    "",
                    isOn: Binding(
                        get: { alarm.isEnabled },
                        set: { onToggle($0) }
                    )
                )
                .labelsHidden()
                .toggleStyle(QuranSwitchToggleStyle())
                .accessibilityIdentifier("alarm_toggle_\(alarm.id)")
            }

            // Repeat days & label (+ optional active snooze badge)
            HStack(spacing: 8) {
                Text("\(AlarmUiFormatters.formatRepeatDays(alarm.repeatDays)) • \(alarm.label)")
                    .font(QuranTypography.cairo(13, weight: .medium))
                    .foregroundColor(alarm.isEnabled ? QuranColors.textSecondary : QuranColors.textMuted)
                    .lineLimit(1)

                Spacer(minLength: 0)

                if hasActiveSnooze {
                    Text("غفوة نشطة")
                        .font(QuranTypography.cairo(11, weight: .semibold))
                        .foregroundColor(QuranColors.warningOrange)
                        .lineLimit(1)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(QuranColors.warningOrangeBg)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(QuranColors.warningOrange.opacity(0.6), lineWidth: 1)
                        )
                }
            }

            // Full-width Golden Surah/Wird Badge
            GoldBadgeSurface {
                Text("📖 \(AlarmUiFormatters.formatWirdCardSummary(alarm))")
                    .font(QuranTypography.cairo(12, weight: .medium))
                    .foregroundColor(alarm.isEnabled ? QuranColors.goldBadgeText : QuranColors.textMuted)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
            }

            // Full-width Sound Badge + Standalone Vibration Icon
            HStack(spacing: 8) {
                GoldBadgeSurface {
                    Text("🔔 \(compactSoundTitle) (\(alarm.volumeLevel)%)")
                        .font(QuranTypography.cairo(12, weight: .medium))
                        .foregroundColor(alarm.isEnabled ? QuranColors.goldBadgeText : QuranColors.textMuted)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                }

                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(alarm.isVibrationEnabled ? QuranColors.goldBadgeBg : QuranColors.bgElevated)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(
                                    alarm.isVibrationEnabled ? QuranColors.goldBadgeBorder : QuranColors.borderSubtle,
                                    lineWidth: 1
                                )
                        )
                    Image(systemName: alarm.isVibrationEnabled ? "iphone.radiowaves.left.and.right" : "iphone.slash")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(
                            (alarm.isVibrationEnabled && alarm.isEnabled)
                                ? QuranColors.goldBadgeText
                                : QuranColors.textMuted
                        )
                }
                .frame(width: 36, height: 30)
                .accessibilityLabel(alarm.isVibrationEnabled ? "الاهتزاز مفعل" : "الاهتزاز متوقف")
            }
        }
        .padding(16)
        .background(alarm.isEnabled ? QuranColors.bgCard : QuranColors.bgCard.opacity(0.65))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(
                    alarm.isEnabled ? QuranColors.activePillBorder.opacity(0.35) : QuranColors.borderSubtle,
                    lineWidth: 1
                )
        )
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .accessibilityIdentifier("alarm_card_\(alarm.id)")
    }
}
