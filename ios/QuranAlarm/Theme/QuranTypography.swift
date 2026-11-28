import CoreText
import SwiftUI
import UIKit

/// Cairo Typography system matching Android's CairoFontFamily and Type.kt scale.
public enum QuranTypography {
    public static let regularPostScriptName = "Cairo-Regular"
    public static let mediumPostScriptName = "Cairo-Medium"
    public static let semiboldPostScriptName = "Cairo-SemiBold"
    public static let boldPostScriptName = "Cairo-Bold"

    private static let fontFileNames = [
        "cairo_regular",
        "cairo_medium",
        "cairo_semibold",
        "cairo_bold"
    ]

    private static var didRegisterFonts = false
    private static let registerLock = NSLock()

    /// Ensures offline Cairo fonts are registered with CoreText even in unit test hosts.
    public static func registerFontsIfNeeded(bundle: Bundle = .main) {
        registerLock.lock()
        defer { registerLock.unlock() }
        guard !didRegisterFonts else { return }

        for name in fontFileNames {
            let url = bundle.url(forResource: name, withExtension: "ttf")
                ?? bundle.url(forResource: name, withExtension: "ttf", subdirectory: "Fonts")
            if let url {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        didRegisterFonts = true
    }

    public static func postScriptName(for weight: Font.Weight) -> String {
        switch weight {
        case .bold, .heavy, .black:
            return boldPostScriptName
        case .semibold:
            return semiboldPostScriptName
        case .medium:
            return mediumPostScriptName
        default:
            return regularPostScriptName
        }
    }

    public static func cairo(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        registerFontsIfNeeded()
        return .custom(postScriptName(for: weight), size: size)
    }

    public static func uiFont(size: CGFloat, weight: UIFont.Weight = .regular) -> UIFont {
        registerFontsIfNeeded()
        let psName: String
        switch weight {
        case .bold, .heavy, .black:
            psName = boldPostScriptName
        case .semibold:
            psName = semiboldPostScriptName
        case .medium:
            psName = mediumPostScriptName
        default:
            psName = regularPostScriptName
        }
        return UIFont(name: psName, size: size) ?? .systemFont(ofSize: size, weight: weight)
    }

    // Semantic scale matching Android Type.kt
    public static let displaySmall = cairo(32, weight: .bold)
    public static let headlineSmall = cairo(20, weight: .bold)
    public static let titleLarge = cairo(18, weight: .bold)
    public static let titleMedium = cairo(16, weight: .semibold)
    public static let titleSmall = cairo(15, weight: .semibold)
    public static let bodyLarge = cairo(14, weight: .medium)
    public static let bodyMedium = cairo(13, weight: .regular)
    public static let bodySmall = cairo(12, weight: .regular)
    public static let labelLarge = cairo(15, weight: .semibold)
    public static let labelMedium = cairo(12, weight: .medium)
    public static let labelSmall = cairo(11, weight: .medium)
}
