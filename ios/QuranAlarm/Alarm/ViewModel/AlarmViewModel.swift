import Combine
import Foundation

public struct AlarmUiState: Equatable {
    public var alarms: [AlarmItem] = []
    public var isLoading: Bool = false
    public var khatmaProgress: KhatmaForecast? = nil
    public var surahWirdProgress: SurahWirdForecast? = nil
    public var nextAlarmCountdown: String = ""
    public var editingAlarm: AlarmItem? = nil
    public var isAddEditDialogOpen: Bool = false
    public var deviceReadinessStatus: IOSDeviceReadinessStatus? = nil
    public var userMessage: String? = nil
}

@MainActor
public final class AlarmViewModel: ObservableObject {
    @Published public private(set) var uiState = AlarmUiState()

    private let repository: AlarmRepository
    private let scheduler: AlarmScheduler

    public init(repository: AlarmRepository, scheduler: AlarmScheduler) {
        self.repository = repository
        self.scheduler = scheduler
        loadAlarms()
        cleanupOrphanedSounds()
    }

    private func cleanupOrphanedSounds() {
        Task.detached(priority: .utility) { [repository] in
            repository.cleanupOrphanedCustomSounds()
        }
    }

    public func loadAlarms() {
        uiState.isLoading = true
        let alarms = repository.getAllAlarms()
        updateAlarmsState(alarms)
    }

    public func refreshDeviceReadiness() {
        Task {
            let status = await IOSDeviceReadinessHelper.fetchReadinessStatus()
            uiState.deviceReadinessStatus = status
        }
    }

    public func saveAlarm(_ alarm: AlarmItem) {
        let isEdit = uiState.editingAlarm != nil
        let finalAlarm: AlarmItem
        if alarm.repeatDays.isEmpty && alarm.isEnabled && alarm.explicitTriggerMillis == nil {
            finalAlarm = alarm.copyWith(explicitTriggerMillis: .some(alarm.getNextTriggerMillis()))
        } else {
            finalAlarm = alarm
        }

        repository.saveAlarm(finalAlarm)
        if finalAlarm.isEnabled {
            scheduler.schedule(finalAlarm)
        } else {
            scheduler.cancel(alarmId: finalAlarm.id)
        }

        let refreshed = repository.getAllAlarms()
        uiState.isAddEditDialogOpen = false
        uiState.editingAlarm = nil
        uiState.userMessage = isEdit ? "تم تحديث المنبه بنجاح!" : "تم حفظ المنبه بنجاح!"
        updateAlarmsState(refreshed)
    }

    public func toggleAlarm(_ alarm: AlarmItem, isEnabled: Bool) {
        let explicitMillis: Int64? = (isEnabled && alarm.repeatDays.isEmpty)
            ? alarm.copyWith(explicitTriggerMillis: .some(nil), snoozeTriggerMillis: .some(nil)).getNextTriggerMillis()
            : nil

        let updated = alarm.copyWith(
            isEnabled: isEnabled,
            explicitTriggerMillis: .some(explicitMillis),
            snoozeTriggerMillis: .some(nil)
        )

        repository.saveAlarm(updated)
        if isEnabled {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(alarmId: alarm.id)
        }

        let refreshed = repository.getAllAlarms()
        uiState.userMessage = isEnabled ? "تم تفعيل المنبه" : "تم تعطيل المنبه"
        updateAlarmsState(refreshed)
    }

    public func deleteAlarm(_ alarmId: Int) {
        scheduler.cancel(alarmId: alarmId)
        repository.deleteAlarm(alarmId)
        let refreshed = repository.getAllAlarms()
        uiState.isAddEditDialogOpen = false
        uiState.editingAlarm = nil
        uiState.userMessage = "تم حذف المنبه"
        updateAlarmsState(refreshed)
    }

    public func openAddDialog() {
        uiState.editingAlarm = nil
        uiState.isAddEditDialogOpen = true
    }

    public func openEditDialog(_ alarm: AlarmItem) {
        uiState.editingAlarm = alarm
        uiState.isAddEditDialogOpen = true
    }

    public func dismissDialog() {
        uiState.isAddEditDialogOpen = false
        uiState.editingAlarm = nil
    }

    public func updateCountdown() {
        uiState.nextAlarmCountdown = computeNextAlarmCountdown(uiState.alarms)
    }

    public func clearUserMessage() {
        uiState.userMessage = nil
    }

    private func updateAlarmsState(_ alarms: [AlarmItem]) {
        var seen = Set<Int>()
        let uniqueAlarms = alarms.filter { seen.insert($0.id).inserted }
        let countdown = computeNextAlarmCountdown(uniqueAlarms)

        let enabledSequentialPageAlarms = uniqueAlarms.filter { $0.isEnabled && $0.isSequential && !$0.isAyahMode }
        let forecast: KhatmaForecast?
        if let first = enabledSequentialPageAlarms.first {
            let totalDailyPages = max(enabledSequentialPageAlarms.map(\.pageCount).reduce(0, +), 1)
            forecast = QuranStructure.calculateKhatmaForecast(
                startPage: first.startPage,
                dailyPages: totalDailyPages
            )
        } else {
            forecast = nil
        }

        let enabledSequentialAyahAlarms = uniqueAlarms.filter { $0.isEnabled && $0.isSequential && $0.isAyahMode }
        let surahForecast: SurahWirdForecast?
        if let primary = enabledSequentialAyahAlarms.first, let surahNum = primary.wirdSurahNumber {
            let totalDailyAyahs = max(
                enabledSequentialAyahAlarms
                    .filter { $0.wirdSurahNumber == surahNum }
                    .map { $0.dailyAyahCount ?? 1 }
                    .reduce(0, +),
                1
            )
            surahForecast = QuranStructure.calculateSurahWirdForecast(
                surahNumber: surahNum,
                startAyah: primary.startAyah ?? 1,
                dailyAyahs: totalDailyAyahs
            )
        } else {
            surahForecast = nil
        }

        uiState.alarms = uniqueAlarms
        uiState.isLoading = false
        uiState.khatmaProgress = forecast
        uiState.surahWirdProgress = surahForecast
        uiState.nextAlarmCountdown = countdown
    }

    private func computeNextAlarmCountdown(_ alarms: [AlarmItem]) -> String {
        let now = AlarmItem.currentMillis()
        let nextEntry = alarms
            .filter(\.isEnabled)
            .map { ($0, $0.getEffectiveNextTriggerMillis(fromMillis: now)) }
            .min { $0.1 < $1.1 }

        guard let (alarm, nextTrigger) = nextEntry else {
            return "لا توجد منبهات نشطة"
        }

        let targetDayName: String?
        if (1...6).contains(alarm.repeatDays.count) {
            let cal = AlarmItem.gregorianCalendar()
            let date = Date(timeIntervalSince1970: Double(nextTrigger) / 1000.0)
            targetDayName = AlarmUiFormatters.getDayName(cal.component(.weekday, from: date))
        } else {
            targetDayName = nil
        }

        return AlarmUiFormatters.formatNextAlarmCountdown(
            triggerMillis: nextTrigger,
            fromMillis: now,
            targetDayName: targetDayName
        )
    }
}
