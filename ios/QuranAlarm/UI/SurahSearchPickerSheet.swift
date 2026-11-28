import SwiftUI

public func normalizeArabicForSearch(_ input: String) -> String {
    var result = ""
    result.reserveCapacity(input.count)
    for scalar in input.unicodeScalars {
        let v = scalar.value
        if (0x064B...0x065F).contains(v) || v == 0x0670 || v == 0x0640 {
            continue
        }
        switch scalar {
        case "أ", "إ", "آ", "ٱ":
            result.append("ا")
        case "ة":
            result.append("ه")
        case "ى":
            result.append("ي")
        default:
            result.unicodeScalars.append(scalar)
        }
    }
    return result
}

public struct SurahSearchPickerSheet: View {
    public let title: String
    public let onSelectSurah: (SurahInfo) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var searchQuery = ""

    public init(title: String, onSelectSurah: @escaping (SurahInfo) -> Void) {
        self.title = title
        self.onSelectSurah = onSelectSurah
    }

    private var filteredSurahs: [SurahInfo] {
        let trimmed = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return QuranStructure.surahs }
        let q = normalizeArabicForSearch(trimmed)
        return QuranStructure.surahs.filter { surah in
            normalizeArabicForSearch(surah.nameArabic).contains(q)
                || String(surah.number).contains(q)
                || String(surah.startPage).contains(q)
        }
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(QuranColors.activePillText)

                    TextField("ابحث باسم السورة أو رقمها أو رقم الصفحة...", text: $searchQuery)
                        .font(QuranTypography.cairo(13, weight: .regular))
                        .foregroundColor(QuranColors.textPrimary)

                    if !searchQuery.isEmpty {
                        Button {
                            searchQuery = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(QuranColors.textMuted)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(QuranColors.bgElevated)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(QuranColors.borderSubtle, lineWidth: 1)
                )
                .padding(.horizontal, 16)
                .padding(.top, 8)

                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(filteredSurahs) { surah in
                            Button {
                                onSelectSurah(surah)
                                dismiss()
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(surah.number). سورة \(surah.nameArabic)")
                                            .font(QuranTypography.cairo(15, weight: .semibold))
                                            .foregroundColor(QuranColors.textPrimary)
                                        Text(AlarmUiFormatters.formatAyahsCount(surah.ayahCount))
                                            .font(QuranTypography.cairo(12, weight: .regular))
                                            .foregroundColor(QuranColors.textSecondary)
                                    }
                                    Spacer()
                                    GoldBadgeSurface(cornerRadius: 8) {
                                        Text("ص \(surah.startPage)")
                                            .font(QuranTypography.cairo(12, weight: .semibold))
                                            .foregroundColor(QuranColors.goldBadgeText)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 4)
                                    }
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(QuranColors.bgElevated)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(QuranColors.borderSubtle, lineWidth: 1)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
            .background(QuranColors.bgCard.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("إغلاق") { dismiss() }
                        .font(QuranTypography.cairo(14, weight: .semibold))
                        .foregroundColor(QuranColors.textSecondary)
                }
            }
        }
    }
}
