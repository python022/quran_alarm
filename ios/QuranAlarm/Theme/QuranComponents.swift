import SwiftUI

/// Unified capsule pill used for tabs, days, presets, and sounds.
public struct QuranSelectablePill: View {
    public let text: String
    public let selected: Bool
    public let fontSize: CGFloat
    public let horizontalPadding: CGFloat
    public let verticalPadding: CGFloat
    public let fullWidth: Bool
    public let accessibilityId: String?
    public let onClick: () -> Void

    public init(
        text: String,
        selected: Bool,
        fontSize: CGFloat = 12,
        horizontalPadding: CGFloat = 12,
        verticalPadding: CGFloat = 8,
        fullWidth: Bool = false,
        accessibilityId: String? = nil,
        onClick: @escaping () -> Void
    ) {
        self.text = text
        self.selected = selected
        self.fontSize = fontSize
        self.horizontalPadding = horizontalPadding
        self.verticalPadding = verticalPadding
        self.fullWidth = fullWidth
        self.accessibilityId = accessibilityId
        self.onClick = onClick
    }

    public var body: some View {
        Button(action: onClick) {
            Text(text)
                .font(QuranTypography.cairo(fontSize, weight: selected ? .semibold : .medium))
                .foregroundColor(selected ? QuranColors.activePillText : QuranColors.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .multilineTextAlignment(.center)
                .padding(.horizontal, horizontalPadding)
                .padding(.vertical, verticalPadding)
                .frame(maxWidth: fullWidth ? .infinity : nil)
                .background(selected ? QuranColors.activePillBg : QuranColors.bgElevated)
                .clipShape(Capsule())
                .overlay(
                    Capsule().stroke(
                        selected ? QuranColors.activePillBorder : QuranColors.borderSubtle,
                        lineWidth: 1
                    )
                )
        }
        .buttonStyle(.plain)
        .modifier(OptionalAccessibilityIdentifier(id: accessibilityId))
    }
}

/// Unified Golden informational badge surface (#1B2229 bg + #2D3741 stroke).
public struct GoldBadgeSurface<Content: View>: View {
    public let cornerRadius: CGFloat
    public let content: () -> Content

    public init(cornerRadius: CGFloat = 10, @ViewBuilder content: @escaping () -> Content) {
        self.cornerRadius = cornerRadius
        self.content = content
    }

    public var body: some View {
        content()
            .background(QuranColors.goldBadgeBg)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(QuranColors.goldBadgeBorder, lineWidth: 1)
            )
    }
}

/// Progressive disclosure expandable card matching Android CollapsibleSettingCard.
public struct CollapsibleSettingCard<Content: View>: View {
    public let title: String
    public let summary: String
    public let isExpanded: Bool
    public let accessibilityId: String?
    public let onToggle: () -> Void
    public let content: () -> Content

    public init(
        title: String,
        summary: String,
        isExpanded: Bool,
        accessibilityId: String? = nil,
        onToggle: @escaping () -> Void,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.summary = summary
        self.isExpanded = isExpanded
        self.accessibilityId = accessibilityId
        self.onToggle = onToggle
        self.content = content
    }

    public var body: some View {
        VStack(spacing: 0) {
            Button(action: onToggle) {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(QuranTypography.cairo(15, weight: .semibold))
                            .foregroundColor(QuranColors.textPrimary)
                        Text(summary)
                            .font(QuranTypography.cairo(12, weight: .medium))
                            .foregroundColor(QuranColors.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(QuranColors.textSecondary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .modifier(OptionalAccessibilityIdentifier(id: accessibilityId))

            if isExpanded {
                VStack(alignment: .leading, spacing: 0) {
                    content()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
        }
        .background(QuranColors.bgCard)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(
                    isExpanded ? QuranColors.activePillBorder.opacity(0.45) : QuranColors.borderSubtle,
                    lineWidth: 1
                )
        )
    }
}

/// Custom ToggleStyle matching Android quranSwitchColors().
public struct QuranSwitchToggleStyle: ToggleStyle {
    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        HStack {
            configuration.label
            Button {
                withAnimation(.easeInOut(duration: 0.18)) {
                    configuration.isOn.toggle()
                }
            } label: {
                ZStack(alignment: configuration.isOn ? .trailing : .leading) {
                    Capsule()
                        .fill(configuration.isOn ? QuranColors.ctaBg : QuranColors.bgElevated)
                        .frame(width: 50, height: 30)
                        .overlay(
                            Capsule()
                                .stroke(configuration.isOn ? QuranColors.ctaBg : QuranColors.borderSubtle, lineWidth: 1.5)
                        )

                    Circle()
                        .fill(configuration.isOn ? QuranColors.ctaText : QuranColors.textMuted)
                        .frame(width: 22, height: 22)
                        .padding(.horizontal, 4)
                }
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isButton)
            .accessibilityValue(configuration.isOn ? "مفعل" : "متوقف")
        }
    }
}

/// Wrapping horizontal layout (equivalent to Jetpack Compose FlowRow) for iOS 16+.
public struct FlowLayout: Layout {
    public var horizontalSpacing: CGFloat
    public var verticalSpacing: CGFloat

    public init(horizontalSpacing: CGFloat = 8, verticalSpacing: CGFloat = 8) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
    }

    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let rows = computeRows(maxWidth: maxWidth, subviews: subviews)
        let totalHeight = rows.enumerated().reduce(CGFloat(0)) { partial, item in
            let rowHeight = item.element.map(\.size.height).max() ?? 0
            let spacing = item.offset > 0 ? verticalSpacing : 0
            return partial + spacing + rowHeight
        }
        let maxRowWidth = rows.map { row in
            row.enumerated().reduce(CGFloat(0)) { w, elem in
                w + elem.element.size.width + (elem.offset > 0 ? horizontalSpacing : 0)
            }
        }.max() ?? 0
        return CGSize(width: maxWidth.isFinite ? maxWidth : maxRowWidth, height: totalHeight)
    }

    public func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = computeRows(maxWidth: bounds.width, subviews: subviews)
        var y = bounds.minY
        for row in rows {
            let rowHeight = row.map(\.size.height).max() ?? 0
            var x = bounds.minX
            for item in row {
                let itemY = y + (rowHeight - item.size.height) / 2
                item.subview.place(
                    at: CGPoint(x: x, y: itemY),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(item.size)
                )
                x += item.size.width + horizontalSpacing
            }
            y += rowHeight + verticalSpacing
        }
    }

    private func computeRows(maxWidth: CGFloat, subviews: Subviews) -> [[(subview: LayoutSubview, size: CGSize)]] {
        var rows: [[(subview: LayoutSubview, size: CGSize)]] = []
        var currentRow: [(subview: LayoutSubview, size: CGSize)] = []
        var currentRowWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(ProposedViewSize(width: maxWidth.isFinite ? maxWidth : nil, height: nil))
            let addedWidth = currentRow.isEmpty ? size.width : horizontalSpacing + size.width
            if !currentRow.isEmpty && currentRowWidth + addedWidth > maxWidth {
                rows.append(currentRow)
                currentRow = [(subview, size)]
                currentRowWidth = size.width
            } else {
                currentRow.append((subview, size))
                currentRowWidth += addedWidth
            }
        }
        if !currentRow.isEmpty {
            rows.append(currentRow)
        }
        return rows
    }
}

public struct OptionalAccessibilityIdentifier: ViewModifier {
    public let id: String?

    public func body(content: Content) -> some View {
        if let id {
            content.accessibilityIdentifier(id)
        } else {
            content
        }
    }
}
