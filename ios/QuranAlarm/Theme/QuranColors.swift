import SwiftUI
import UIKit

/// Unified Dark Slate + Emerald + Gold color palette for Quran Alarm.
public enum QuranColors {
    // Raw ARGB constants (1-to-1 parity with Android Color.kt)
    public static let bgPrimaryHex: UInt32 = 0xFF0B1117
    public static let bgCardHex: UInt32 = 0xFF121820
    public static let bgElevatedHex: UInt32 = 0xFF16202A
    public static let borderSubtleHex: UInt32 = 0xFF1F2D3D

    public static let ctaBgHex: UInt32 = 0xFF1AD699
    public static let ctaTextHex: UInt32 = 0xFF071A14

    public static let activePillBgHex: UInt32 = 0xFF142E28
    public static let activePillBorderHex: UInt32 = 0xFF1AD699
    public static let activePillTextHex: UInt32 = 0xFF1AD699

    public static let goldBadgeBgHex: UInt32 = 0xFF1B2229
    public static let goldBadgeBorderHex: UInt32 = 0xFF2D3741
    public static let goldBadgeTextHex: UInt32 = 0xFFD4AF37

    public static let textPrimaryHex: UInt32 = 0xFFF8FAFC
    public static let textSecondaryHex: UInt32 = 0xFF94A3B8
    public static let textMutedHex: UInt32 = 0xFF64748B

    public static let warningOrangeHex: UInt32 = 0xFFF59E0B
    public static let warningOrangeBgHex: UInt32 = 0xFF2B1D0E
    public static let dangerRedHex: UInt32 = 0xFFEF4444
    public static let dangerRedBgHex: UInt32 = 0xFF2A1215

    public static let mushafPaperHex: UInt32 = 0xFFFFFDF5
    public static let mushafHighlightHex: UInt32 = 0x334CAF50

    public static let readerSkyHex: UInt32 = 0xFF38BDF8
    public static let readerEmeraldHex: UInt32 = 0xFF10B981
    public static let readerAmberHex: UInt32 = 0xFFFBBF24
    public static let readerSlateCardHex: UInt32 = 0xFF1E293B
    public static let readerSlateBorderHex: UInt32 = 0xFF334155

    public static let bgPrimary = Color(argb: bgPrimaryHex)
    public static let bgCard = Color(argb: bgCardHex)
    public static let bgElevated = Color(argb: bgElevatedHex)
    public static let borderSubtle = Color(argb: borderSubtleHex)

    public static let ctaBg = Color(argb: ctaBgHex)
    public static let ctaText = Color(argb: ctaTextHex)

    public static let activePillBg = Color(argb: activePillBgHex)
    public static let activePillBorder = Color(argb: activePillBorderHex)
    public static let activePillText = Color(argb: activePillTextHex)

    public static let goldBadgeBg = Color(argb: goldBadgeBgHex)
    public static let goldBadgeBorder = Color(argb: goldBadgeBorderHex)
    public static let goldBadgeText = Color(argb: goldBadgeTextHex)

    public static let textPrimary = Color(argb: textPrimaryHex)
    public static let textSecondary = Color(argb: textSecondaryHex)
    public static let textMuted = Color(argb: textMutedHex)

    public static let warningOrange = Color(argb: warningOrangeHex)
    public static let warningOrangeBg = Color(argb: warningOrangeBgHex)
    public static let dangerRed = Color(argb: dangerRedHex)
    public static let dangerRedBg = Color(argb: dangerRedBgHex)

    public static let mushafPaper = Color(argb: mushafPaperHex)
    public static let mushafHighlight = Color(argb: mushafHighlightHex)

    public static let readerSky = Color(argb: readerSkyHex)
    public static let readerEmerald = Color(argb: readerEmeraldHex)
    public static let readerAmber = Color(argb: readerAmberHex)
    public static let readerSlateCard = Color(argb: readerSlateCardHex)
    public static let readerSlateBorder = Color(argb: readerSlateBorderHex)
}

public extension Color {
    init(argb: UInt32) {
        let a = Double((argb >> 24) & 0xFF) / 255.0
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

public extension UIColor {
    convenience init(argb: UInt32) {
        let a = CGFloat((argb >> 24) & 0xFF) / 255.0
        let r = CGFloat((argb >> 16) & 0xFF) / 255.0
        let g = CGFloat((argb >> 8) & 0xFF) / 255.0
        let b = CGFloat(argb & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, alpha: a)
    }
}
