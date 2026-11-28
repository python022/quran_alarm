import Foundation
import UserNotifications

public struct AlarmSoundOption: Equatable, Identifiable {
    public var id: String { uriString ?? AlarmSoundPresets.uriDefault }
    public let title: String
    public let uriString: String?
    public let isCustomFile: Bool

    public init(title: String, uriString: String?, isCustomFile: Bool = false) {
        self.title = title
        self.uriString = uriString
        self.isCustomFile = isCustomFile
    }
}

/// Curated list of built-in alarm sounds and sound URI resolution for iOS.
/// Rejects any remote/network URI scheme to enforce 100% offline playback.
public enum AlarmSoundPresets {
    public static let uriDefault = "default_alarm"
    public static let uriAdhanFajr = "builtin://adhan_fajr"
    public static let uriEidTakbeer = "builtin://eid_takbeer"
    public static let uriCalmTone = "builtin://calm_tone"
    public static let uriQuranAlert = "builtin://quran_alert"

    public static let presetOptions: [AlarmSoundOption] = [
        AlarmSoundOption(title: "النغمة الافتراضية", uriString: nil),
        AlarmSoundOption(title: "أذان الفجر", uriString: uriAdhanFajr),
        AlarmSoundOption(title: "تكبيرات العيد", uriString: uriEidTakbeer),
        AlarmSoundOption(title: "نغمة هادئة", uriString: uriCalmTone),
        AlarmSoundOption(title: "تنبيه قرآني", uriString: uriQuranAlert)
    ]

    private static let blockedSchemes: Set<String> = [
        "http", "https", "ftp", "ftps", "rtsp", "ws", "wss", "smb"
    ]

    public static func getTitleForUri(_ uriString: String?, defaultTitle: String = "النغمة الافتراضية") -> String {
        guard let uri = uriString, !uri.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, uri != uriDefault else {
            return "النغمة الافتراضية"
        }
        return presetOptions.first(where: { $0.uriString == uri })?.title ?? defaultTitle
    }

    public static func bundledResourceBaseName(for uriString: String?) -> String? {
        switch uriString {
        case nil, "", uriDefault:
            return "quran_alert"
        case uriAdhanFajr:
            return "adhan_fajr"
        case uriEidTakbeer:
            return "eid_takbeer"
        case uriCalmTone:
            return "calm_tone"
        case uriQuranAlert:
            return "quran_alert"
        default:
            return nil
        }
    }

    public static func resolveAudioURL(for uriString: String?, bundle: Bundle = .main) -> URL? {
        if let baseName = bundledResourceBaseName(for: uriString) {
            return bundle.url(forResource: baseName, withExtension: "m4a")
                ?? bundle.url(forResource: baseName, withExtension: "m4a", subdirectory: "Sounds")
        }

        guard let uri = uriString, let parsed = URL(string: uri) else {
            return nil
        }
        if let scheme = parsed.scheme?.lowercased() {
            if blockedSchemes.contains(scheme) {
                return nil
            }
            if scheme == "file" {
                return CustomSoundStorage.resolveSafeCustomSoundURL(uriString: uri)
            }
            return nil
        }
        return nil
    }

    public static func notificationSoundFileName(for uriString: String?) -> String? {
        guard let baseName = bundledResourceBaseName(for: uriString) else {
            return nil
        }
        return "\(baseName)_30s.caf"
    }

    public static func notificationSound(for uriString: String?, bundle: Bundle = .main) -> UNNotificationSound {
        if let cafName = notificationSoundFileName(for: uriString) {
            let base = cafName.replacingOccurrences(of: ".caf", with: "")
            if bundle.url(forResource: base, withExtension: "caf") != nil {
                return UNNotificationSound(named: UNNotificationSoundName(rawValue: cafName))
            }
        }
        return .default
    }
}
