import SwiftUI

public struct ModernIslamicTimePickerCard: View {
    @Binding public var hour12: Int
    @Binding public var minute: Int
    @Binding public var isAm: Bool

    private let posixLocale = Locale(identifier: "en_US_POSIX")

    public init(hour12: Binding<Int>, minute: Binding<Int>, isAm: Binding<Bool>) {
        self._hour12 = hour12
        self._minute = minute
        self._isAm = isAm
    }

    public var body: some View {
        let hour24 = AlarmUiFormatters.toHour24(hour12: hour12, isAm: isAm)
        let timeBadge: String = {
            if (0...2).contains(hour24) || hour24 >= 22 { return "🌙 ثلث الليل الآخر (قيام الليل)" }
            if (3...6).contains(hour24) { return "🌅 وقت السحر وصلاة الفجر" }
            if (7...11).contains(hour24) { return "☀️ صباحاً" }
            if (12...16).contains(hour24) { return "☀️ ظهراً ومساءً" }
            return "🌆 مساءً"
        }()

        VStack(spacing: 12) {
            GoldBadgeSurface(cornerRadius: 20) {
                Text(timeBadge)
                    .font(QuranTypography.cairo(12, weight: .semibold))
                    .foregroundColor(QuranColors.goldBadgeText)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
            }

            // Outer row stays RTL; only numeric digits container (hour : minute) is forced LTR
            HStack(spacing: 10) {
                HStack(spacing: 4) {
                    Picker("الساعة", selection: $hour12) {
                        ForEach(1...12, id: \.self) { h in
                            Text(String(format: "%02d", locale: posixLocale, h))
                                .font(QuranTypography.cairo(26, weight: .bold))
                                .foregroundColor(QuranColors.textPrimary)
                                .tag(h)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(height: 132)
                    .clipped()
                    .accessibilityIdentifier("hour_wheel_picker")

                    Text(":")
                        .font(QuranTypography.cairo(28, weight: .bold))
                        .foregroundColor(QuranColors.ctaBg)

                    Picker("الدقيقة", selection: $minute) {
                        ForEach(0...59, id: \.self) { m in
                            Text(String(format: "%02d", locale: posixLocale, m))
                                .font(QuranTypography.cairo(26, weight: .bold))
                                .foregroundColor(QuranColors.textPrimary)
                                .tag(m)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(height: 132)
                    .clipped()
                    .accessibilityIdentifier("minute_wheel_picker")
                }
                .environment(\.layoutDirection, .leftToRight)
                .frame(maxWidth: .infinity)

                Picker("الفترة", selection: $isAm) {
                    Text("ص")
                        .font(QuranTypography.cairo(20, weight: .bold))
                        .foregroundColor(QuranColors.textPrimary)
                        .tag(true)
                    Text("م")
                        .font(QuranTypography.cairo(20, weight: .bold))
                        .foregroundColor(QuranColors.textPrimary)
                        .tag(false)
                }
                .pickerStyle(.wheel)
                .frame(width: 72, height: 132)
                .clipped()
                .accessibilityIdentifier("ampm_wheel_picker")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(QuranColors.bgElevated)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(QuranColors.bgCard)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(QuranColors.borderSubtle, lineWidth: 1)
        )
    }
}

/// Reusable compact wheel picker matching Android VerticalWheelPicker.
public struct QuranWheelPicker<Item: Hashable>: View {
    public let items: [Item]
    @Binding public var selection: Item
    public let height: CGFloat
    public let fontSize: CGFloat
    public let itemLabel: (Item) -> String

    public init(
        items: [Item],
        selection: Binding<Item>,
        height: CGFloat = 128,
        fontSize: CGFloat = 13,
        itemLabel: @escaping (Item) -> String
    ) {
        self.items = items
        self._selection = selection
        self.height = height
        self.fontSize = fontSize
        self.itemLabel = itemLabel
    }

    public var body: some View {
        ZStack {
            Picker("", selection: $selection) {
                ForEach(items, id: \.self) { item in
                    Text(itemLabel(item))
                        .font(QuranTypography.cairo(fontSize, weight: .semibold))
                        .foregroundColor(QuranColors.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .tag(item)
                }
            }
            .pickerStyle(.wheel)
            .labelsHidden()
            .frame(height: height)
            .clipped()

            VStack(spacing: 40) {
                Rectangle()
                    .fill(QuranColors.activePillBorder.opacity(0.45))
                    .frame(height: 1)
                Rectangle()
                    .fill(QuranColors.activePillBorder.opacity(0.45))
                    .frame(height: 1)
            }
            .allowsHitTesting(false)
        }
    }
}
