import Foundation
import UserNotifications

public protocol AlarmScheduler {
    func schedule(_ alarm: AlarmItem)
    func scheduleSnooze(_ alarm: AlarmItem, triggerMillis: Int64)
    func cancel(alarmId: Int)
}

/// iOS UNUserNotificationCenter alarm scheduler using Time-Sensitive notifications and custom <=28s .caf tones.
public final class IOSAlarmSchedulerImpl: AlarmScheduler {
    public static let categoryIdentifier = "QURAN_ALARM_CATEGORY"
    public static let actionStartWirdIdentifier = "START_WIRD_ACTION"
    public static let actionSnoozeIdentifier = "SNOOZE_ACTION"

    private let center: UNUserNotificationCenter
    private let isUITestMode: Bool

    public init(
        center: UNUserNotificationCenter = .current(),
        isUITestMode: Bool = ProcessInfo.processInfo.arguments.contains("-UITestMode")
    ) {
        self.center = center
        self.isUITestMode = isUITestMode
    }

    public func registerCategories() {
        guard !isUITestMode else { return }
        let startAction = UNNotificationAction(
            identifier: Self.actionStartWirdIdentifier,
            title: "بدء قراءة الورد",
            options: [.foreground]
        )
        let snoozeAction = UNNotificationAction(
            identifier: Self.actionSnoozeIdentifier,
            title: "تأجيل 5 دقائق",
            options: []
        )
        let category = UNNotificationCategory(
            identifier: Self.categoryIdentifier,
            actions: [startAction, snoozeAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        center.setNotificationCategories([category])
    }

    public func requestAuthorizationIfNeeded(completion: ((Bool) -> Void)? = nil) {
        guard !isUITestMode else {
            completion?(true)
            return
        }
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            DispatchQueue.main.async {
                completion?(granted)
            }
        }
    }

    public func schedule(_ alarm: AlarmItem) {
        cancel(alarmId: alarm.id)
        guard alarm.isEnabled, !isUITestMode else { return }

        let cal = AlarmItem.gregorianCalendar()
        if alarm.repeatDays.isEmpty {
            let triggerMillis = alarm.getNextTriggerMillis()
            scheduleCascade(alarm: alarm, baseTriggerMillis: triggerMillis, prefix: "oneshot")
        } else {
            for weekday in alarm.repeatDays.sorted() {
                var comps = DateComponents()
                comps.calendar = cal
                comps.weekday = weekday
                comps.hour = alarm.hour
                comps.minute = alarm.minute
                comps.second = 0

                let content = buildContent(for: alarm, followUpIndex: 0)
                let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
                let id = notificationIdentifier(alarmId: alarm.id, tag: "repeat_d\(weekday)")
                center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            }

            // Also schedule 3 follow-up cascade reminders (+30s, +60s, +90s) for the next upcoming instance
            let nextTriggerMillis = alarm.getNextTriggerMillis()
            scheduleFollowUpOnly(alarm: alarm, baseTriggerMillis: nextTriggerMillis, prefix: "repeat_next")
        }
    }

    public func scheduleSnooze(_ alarm: AlarmItem, triggerMillis: Int64) {
        cancel(alarmId: alarm.id)
        guard !isUITestMode else { return }
        scheduleCascade(alarm: alarm, baseTriggerMillis: triggerMillis, prefix: "snooze")
    }

    public func cancel(alarmId: Int) {
        guard !isUITestMode else { return }
        let prefix = "quran_alarm_\(alarmId)_"
        center.getPendingNotificationRequests { [weak self] requests in
            let matchingIds = requests.map(\.identifier).filter { $0.hasPrefix(prefix) }
            if !matchingIds.isEmpty {
                self?.center.removePendingNotificationRequests(withIdentifiers: matchingIds)
            }
        }
    }

    private func scheduleCascade(alarm: AlarmItem, baseTriggerMillis: Int64, prefix: String) {
        let offsetsSeconds = [0, 30, 60, 90]
        let cal = AlarmItem.gregorianCalendar()

        for (index, offset) in offsetsSeconds.enumerated() {
            let fireDate = Date(timeIntervalSince1970: Double(baseTriggerMillis) / 1000.0 + Double(offset))
            let comps = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            let content = buildContent(for: alarm, followUpIndex: index)
            let id = notificationIdentifier(alarmId: alarm.id, tag: "\(prefix)_\(index)")
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        }
    }

    private func scheduleFollowUpOnly(alarm: AlarmItem, baseTriggerMillis: Int64, prefix: String) {
        let offsetsSeconds = [30, 60, 90]
        let cal = AlarmItem.gregorianCalendar()

        for (idx, offset) in offsetsSeconds.enumerated() {
            let fireDate = Date(timeIntervalSince1970: Double(baseTriggerMillis) / 1000.0 + Double(offset))
            let comps = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            let content = buildContent(for: alarm, followUpIndex: idx + 1)
            let id = notificationIdentifier(alarmId: alarm.id, tag: "\(prefix)_\(idx + 1)")
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        }
    }

    private func buildContent(for alarm: AlarmItem, followUpIndex: Int) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = alarm.label.isEmpty ? "منبه القرآن الكريم" : alarm.label
        content.body = followUpIndex == 0
            ? "حان موعد الورد القرآني: \(AlarmUiFormatters.formatWirdCardSummary(alarm))"
            : "تذكير بإكمال الورد القرآني: \(AlarmUiFormatters.formatWirdCardSummary(alarm))"
        content.sound = AlarmSoundPresets.notificationSound(for: alarm.soundUri)
        content.categoryIdentifier = Self.categoryIdentifier
        content.interruptionLevel = .timeSensitive

        var info: [String: Any] = [
            "alarmId": alarm.id,
            "alarmLabel": alarm.label,
            "startPage": alarm.startPage,
            "pageCount": alarm.pageCount,
            "volumeLevel": alarm.volumeLevel,
            "isVibrate": alarm.isVibrationEnabled
        ]
        if let soundUri = alarm.soundUri {
            info["soundUri"] = soundUri
        }
        if let surah = alarm.wirdSurahNumber {
            info["wirdSurahNumber"] = surah
        }
        if let startAyah = alarm.startAyah {
            info["startAyah"] = startAyah
        }
        if let dailyAyahs = alarm.dailyAyahCount {
            info["dailyAyahCount"] = dailyAyahs
        }
        content.userInfo = info
        return content
    }

    private func notificationIdentifier(alarmId: Int, tag: String) -> String {
        "quran_alarm_\(alarmId)_\(tag)"
    }
}

public final class NoOpAlarmScheduler: AlarmScheduler {
    public init() {}
    public func schedule(_ alarm: AlarmItem) {}
    public func scheduleSnooze(_ alarm: AlarmItem, triggerMillis: Int64) {}
    public func cancel(alarmId: Int) {}
}
