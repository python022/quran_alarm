import Combine
import Foundation
import UIKit
import UserNotifications

/// Coordinates foreground/notification alarm delivery, active session restoration,
/// screen wake lock, and audio silencing across the app lifecycle.
@MainActor
public final class AlarmSessionCoordinator: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    public static let previewAlarmId = -999

    @Published public var activeAlertViewModel: AlarmAlertViewModel?
    @Published public var isPresentingAlert: Bool = false

    private let repository: AlarmRepository
    private let scheduler: AlarmScheduler
    private let sessionStore: AlarmSessionStore
    private let pageManager: QuranPageManager
    private let lineBoundsRepository: LineBoundsRepository
    private let audioManager: SmartAlarmAudioManager
    private let isUITestMode: Bool
    private weak var alarmViewModel: AlarmViewModel?
    private var foregroundCheckTimer: Timer?
    private var lastTriggeredAlarmKey: String?

    public init(
        repository: AlarmRepository,
        scheduler: AlarmScheduler,
        sessionStore: AlarmSessionStore,
        pageManager: QuranPageManager = QuranPageManager(),
        lineBoundsRepository: LineBoundsRepository = AyahInfoLineBoundsRepositoryImpl(),
        audioManager: SmartAlarmAudioManager = SmartAlarmAudioManager(),
        isUITestMode: Bool = ProcessInfo.processInfo.arguments.contains("-UITestMode")
    ) {
        self.repository = repository
        self.scheduler = scheduler
        self.sessionStore = sessionStore
        self.pageManager = pageManager
        self.lineBoundsRepository = lineBoundsRepository
        self.audioManager = audioManager
        self.isUITestMode = isUITestMode
        super.init()

        if !isUITestMode {
            UNUserNotificationCenter.current().delegate = self
            startForegroundTriggerMonitor()
        }
    }

    deinit {
        foregroundCheckTimer?.invalidate()
    }

    public func attachAlarmViewModel(_ viewModel: AlarmViewModel) {
        self.alarmViewModel = viewModel
    }

    public func restoreActiveSessionIfNeeded() {
        guard !isPresentingAlert, let session = sessionStore.loadSession(), session.active else {
            return
        }
        let vm = buildAlertViewModel()
        let stageOverride: AlarmAlertStage = session.stage == AlarmSessionStore.stageRinging ? .ringing : .mushafReading
        if stageOverride == .ringing && !isUITestMode {
            audioManager.startAlarm(
                soundUriString: session.soundUri,
                volumeLevel: session.volumeLevel,
                isVibrationEnabled: session.isVibrate
            )
        }
        vm.initialize(
            alarmId: session.alarmId,
            alarmLabel: session.label,
            rawStartPage: session.startPage,
            rawPageCount: session.pageCount,
            isSnoozeEnabled: true,
            isFreshTrigger: false,
            wirdSurahNumber: session.wirdSurahNumber,
            startAyah: session.startAyah,
            dailyAyahCount: session.dailyAyahCount,
            initialStageOverride: stageOverride
        )
        present(viewModel: vm)
    }

    public func presentPreview(
        volumeLevel: Int,
        isVibrate: Bool,
        soundUri: String?
    ) {
        let vm = buildAlertViewModel()
        if !isUITestMode {
            audioManager.startAlarm(
                soundUriString: soundUri,
                volumeLevel: volumeLevel,
                isVibrationEnabled: isVibrate
            )
        }
        vm.initialize(
            alarmId: Self.previewAlarmId,
            alarmLabel: "معاينة منبه الورد القرآني",
            rawStartPage: 1,
            rawPageCount: 1,
            isSnoozeEnabled: true,
            isFreshTrigger: true
        )
        present(viewModel: vm)
    }

    public func presentAlarmSession(
        alarm: AlarmItem,
        initialStage: AlarmAlertStage = .ringing,
        playAudio: Bool = true
    ) {
        sessionStore.saveSession(
            PersistedAlarmSession(
                active: true,
                stage: initialStage.rawValue,
                alarmId: alarm.id,
                label: alarm.label,
                startPage: alarm.startPage,
                pageCount: alarm.pageCount,
                volumeLevel: alarm.volumeLevel,
                isVibrate: alarm.isVibrationEnabled,
                soundUri: alarm.soundUri,
                wirdSurahNumber: alarm.wirdSurahNumber,
                startAyah: alarm.startAyah,
                dailyAyahCount: alarm.dailyAyahCount
            )
        )

        if playAudio && initialStage == .ringing && !isUITestMode {
            audioManager.startAlarm(
                soundUriString: alarm.soundUri,
                volumeLevel: alarm.volumeLevel,
                isVibrationEnabled: alarm.isVibrationEnabled
            )
        }

        let vm = buildAlertViewModel()
        vm.initialize(
            alarmId: alarm.id,
            alarmLabel: alarm.label,
            rawStartPage: alarm.startPage,
            rawPageCount: alarm.pageCount,
            isSnoozeEnabled: true,
            isFreshTrigger: true,
            wirdSurahNumber: alarm.wirdSurahNumber,
            startAyah: alarm.startAyah,
            dailyAyahCount: alarm.dailyAyahCount,
            initialStageOverride: initialStage
        )
        present(viewModel: vm)
    }

    public func finishSession() {
        audioManager.stopAlarm()
        sessionStore.clearSession()
        UIApplication.shared.isIdleTimerDisabled = false
        isPresentingAlert = false
        activeAlertViewModel = nil
        alarmViewModel?.loadAlarms()
    }

    private func present(viewModel: AlarmAlertViewModel) {
        UIApplication.shared.isIdleTimerDisabled = true
        activeAlertViewModel = viewModel
        isPresentingAlert = true
    }

    private func buildAlertViewModel() -> AlarmAlertViewModel {
        AlarmAlertViewModel(
            lineBoundsRepository: lineBoundsRepository,
            pageManager: pageManager,
            alarmRepository: repository,
            alarmScheduler: scheduler,
            progressStore: sessionStore,
            onSilenceAudio: { [weak self] in
                self?.audioManager.stopAlarm()
                if let active = self?.activeAlertViewModel?.uiState, active.alarmId >= 0 {
                    self?.sessionStore.markReadingStage(
                        alarmId: active.alarmId,
                        label: active.alarmLabel,
                        startPage: active.startPage,
                        pageCount: active.pageCount,
                        wirdSurahNumber: active.wirdSurahNumber,
                        startAyah: active.startAyah,
                        dailyAyahCount: active.dailyAyahCount
                    )
                }
            }
        )
    }

    private func startForegroundTriggerMonitor() {
        foregroundCheckTimer?.invalidate()
        let timer = Timer(timeInterval: 2.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.checkForegroundDueAlarms()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        foregroundCheckTimer = timer
    }

    private func checkForegroundDueAlarms() {
        guard !isPresentingAlert else { return }
        let now = AlarmItem.currentMillis()
        let cal = AlarmItem.gregorianCalendar()
        let nowComps = cal.dateComponents([.year, .month, .day, .hour, .minute, .weekday, .second], from: Date())
        guard let curHour = nowComps.hour,
              let curMinute = nowComps.minute,
              let curWeekday = nowComps.weekday,
              (nowComps.second ?? 0) <= 5 else {
            return
        }

        for alarm in repository.getActiveAlarms() {
            if let snooze = alarm.snoozeTriggerMillis, abs(now - snooze) <= 3_000 {
                let key = "snooze_\(alarm.id)_\(snooze / 60_000)"
                if lastTriggeredAlarmKey != key {
                    lastTriggeredAlarmKey = key
                    presentAlarmSession(alarm: alarm, initialStage: .ringing, playAudio: true)
                    return
                }
            }

            let matchesDay = alarm.repeatDays.isEmpty || alarm.repeatDays.contains(curWeekday)
            if matchesDay && alarm.hour == curHour && alarm.minute == curMinute {
                let minuteBucket = Int64(Date().timeIntervalSince1970) / 60
                let key = "alarm_\(alarm.id)_\(minuteBucket)"
                if lastTriggeredAlarmKey != key {
                    lastTriggeredAlarmKey = key
                    presentAlarmSession(alarm: alarm, initialStage: .ringing, playAudio: true)
                    return
                }
            }
        }
    }

    // MARK: - UNUserNotificationCenterDelegate

    public nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        Task { @MainActor [weak self] in
            if let self, !self.isPresentingAlert, let alarm = self.resolveAlarm(from: userInfo) {
                self.presentAlarmSession(alarm: alarm, initialStage: .ringing, playAudio: true)
                completionHandler([.banner])
            } else {
                completionHandler([.banner, .sound])
            }
        }
    }

    public nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let actionId = response.actionIdentifier
        let userInfo = response.notification.request.content.userInfo

        Task { @MainActor [weak self] in
            defer { completionHandler() }
            guard let self, let alarm = self.resolveAlarm(from: userInfo) else { return }

            if actionId == IOSAlarmSchedulerImpl.actionSnoozeIdentifier {
                let snoozeTime = AlarmItem.currentMillis() + 5 * 60_000
                let snoozeItem = alarm.copyWith(isEnabled: true, snoozeTriggerMillis: .some(snoozeTime))
                self.repository.saveAlarm(snoozeItem)
                self.scheduler.scheduleSnooze(snoozeItem, triggerMillis: snoozeTime)
                self.alarmViewModel?.loadAlarms()
            } else if actionId == IOSAlarmSchedulerImpl.actionStartWirdIdentifier {
                self.presentAlarmSession(alarm: alarm, initialStage: .mushafReading, playAudio: false)
            } else {
                self.presentAlarmSession(alarm: alarm, initialStage: .ringing, playAudio: true)
            }
        }
    }

    private func resolveAlarm(from userInfo: [AnyHashable: Any]) -> AlarmItem? {
        guard let alarmId = userInfo["alarmId"] as? Int else { return nil }
        if let stored = repository.getAlarmById(alarmId) {
            return stored
        }
        let label = userInfo["alarmLabel"] as? String ?? "ورد القرآن الكريم"
        let startPage = userInfo["startPage"] as? Int ?? 1
        let pageCount = userInfo["pageCount"] as? Int ?? 1
        let volume = userInfo["volumeLevel"] as? Int ?? 100
        let isVibrate = userInfo["isVibrate"] as? Bool ?? true
        let soundUri = userInfo["soundUri"] as? String
        let surahNum = userInfo["wirdSurahNumber"] as? Int
        let startAyah = userInfo["startAyah"] as? Int
        let dailyAyahs = userInfo["dailyAyahCount"] as? Int

        return AlarmItem(
            id: max(alarmId, 0),
            hour: 5,
            minute: 0,
            isVibrationEnabled: isVibrate,
            label: label,
            targetPage: startPage,
            startPage: startPage,
            pageCount: pageCount,
            volumeLevel: volume,
            soundUri: soundUri,
            wirdSurahNumber: surahNum,
            startAyah: startAyah,
            dailyAyahCount: dailyAyahs
        )
    }
}
