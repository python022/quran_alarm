import Foundation
import UIKit
import UserNotifications

public struct IOSDeviceReadinessStatus: Equatable {
    public let isNotificationsAuthorized: Bool
    public let isSoundEnabled: Bool
    public let isTimeSensitiveEnabled: Bool
    public let authorizationDetermined: Bool

    public var needsAttention: Bool {
        !isNotificationsAuthorized || !isSoundEnabled || !isTimeSensitiveEnabled
    }

    public init(
        isNotificationsAuthorized: Bool = true,
        isSoundEnabled: Bool = true,
        isTimeSensitiveEnabled: Bool = true,
        authorizationDetermined: Bool = false
    ) {
        self.isNotificationsAuthorized = isNotificationsAuthorized
        self.isSoundEnabled = isSoundEnabled
        self.isTimeSensitiveEnabled = isTimeSensitiveEnabled
        self.authorizationDetermined = authorizationDetermined
    }
}

public enum IOSDeviceReadinessHelper {
    public static func fetchReadinessStatus() async -> IOSDeviceReadinessStatus {
        if ProcessInfo.processInfo.arguments.contains("-UITestMode") {
            return IOSDeviceReadinessStatus(
                isNotificationsAuthorized: true,
                isSoundEnabled: true,
                isTimeSensitiveEnabled: true,
                authorizationDetermined: true
            )
        }

        return await withCheckedContinuation { continuation in
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                let authorized = settings.authorizationStatus == .authorized
                    || settings.authorizationStatus == .provisional
                let soundOk = settings.soundSetting == .enabled
                let timeSensitiveOk: Bool
                if #available(iOS 15.0, *) {
                    timeSensitiveOk = settings.timeSensitiveSetting == .enabled
                } else {
                    timeSensitiveOk = true
                }
                let determined = settings.authorizationStatus != .notDetermined
                continuation.resume(
                    returning: IOSDeviceReadinessStatus(
                        isNotificationsAuthorized: authorized,
                        isSoundEnabled: soundOk,
                        isTimeSensitiveEnabled: timeSensitiveOk,
                        authorizationDetermined: determined
                    )
                )
            }
        }
    }

    @MainActor
    public static func openAppNotificationSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
