import Foundation

public protocol AlarmRepository {
    func saveAlarm(_ alarm: AlarmItem)
    func saveAlarms(_ alarms: [AlarmItem])
    func getAllAlarms() -> [AlarmItem]
    func getActiveAlarms() -> [AlarmItem]
    func getAlarmById(_ id: Int) -> AlarmItem?
    func deleteAlarm(_ id: Int)
    func cleanupOrphanedCustomSounds()
}

/// Offline UserDefaults JSON implementation of AlarmRepository.
public final class AlarmRepositoryImpl: AlarmRepository {
    private static let storageKey = "quran_alarms_v1"
    private let userDefaults: UserDefaults
    private let storageKey: String
    private let lock = NSRecursiveLock()

    public init(userDefaults: UserDefaults = .standard, storageKey: String = "quran_alarms_v1") {
        self.userDefaults = userDefaults
        self.storageKey = storageKey
    }

    public func saveAlarm(_ alarm: AlarmItem) {
        saveAlarms([alarm])
    }

    public func saveAlarms(_ alarms: [AlarmItem]) {
        guard !alarms.isEmpty else { return }

        var previousSoundURIs: [String] = []
        lock.lock()
        var byId: [Int: AlarmItem] = [:]
        for existing in readAllUnlocked() {
            byId[existing.id] = existing
        }

        for alarm in alarms {
            if let oldUri = byId[alarm.id]?.soundUri, oldUri != alarm.soundUri {
                previousSoundURIs.append(oldUri)
            }
            byId[alarm.id] = sanitize(alarm)
        }
        writeAllUnlocked(Array(byId.values))
        lock.unlock()

        for oldUri in previousSoundURIs {
            deleteUnusedCustomSound(oldUri)
        }
    }

    public func getAllAlarms() -> [AlarmItem] {
        lock.lock()
        defer { lock.unlock() }
        return readAllUnlocked()
            .sorted { lhs, rhs in
                if lhs.hour != rhs.hour { return lhs.hour < rhs.hour }
                if lhs.minute != rhs.minute { return lhs.minute < rhs.minute }
                return lhs.id < rhs.id
            }
    }

    public func getActiveAlarms() -> [AlarmItem] {
        getAllAlarms().filter(\.isEnabled)
    }

    public func getAlarmById(_ id: Int) -> AlarmItem? {
        lock.lock()
        defer { lock.unlock() }
        return readAllUnlocked().first(where: { $0.id == id })
    }

    public func deleteAlarm(_ id: Int) {
        lock.lock()
        let existing = readAllUnlocked()
        let removedSoundUri = existing.first(where: { $0.id == id })?.soundUri
        let filtered = existing.filter { $0.id != id }
        writeAllUnlocked(filtered)
        lock.unlock()

        if let removedSoundUri {
            deleteUnusedCustomSound(removedSoundUri)
        }
    }

    public func cleanupOrphanedCustomSounds() {
        let referenced = Set(getAllAlarms().compactMap(\.soundUri))
        CustomSoundStorage.cleanupOrphanedSounds(referencedUriStrings: referenced)
    }

    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }
        userDefaults.removeObject(forKey: storageKey)
    }

    private func readAllUnlocked() -> [AlarmItem] {
        guard let data = userDefaults.data(forKey: storageKey) else { return [] }
        guard let decoded = try? JSONDecoder().decode([AlarmItem].self, from: data) else {
            return []
        }
        var seen = Set<Int>()
        return decoded.compactMap { item in
            guard seen.insert(item.id).inserted else { return nil }
            return sanitize(item)
        }
    }

    private func writeAllUnlocked(_ alarms: [AlarmItem]) {
        if let data = try? JSONEncoder().encode(alarms) {
            userDefaults.set(data, forKey: storageKey)
        }
    }

    private func deleteUnusedCustomSound(_ uriString: String) {
        let stillReferenced = getAllAlarms().contains { $0.soundUri == uriString }
        CustomSoundStorage.deleteIfUnused(uriString: uriString, stillReferenced: stillReferenced)
    }

    private func sanitize(_ alarm: AlarmItem) -> AlarmItem {
        let safeSurah = alarm.wirdSurahNumber.map { min(max($0, 1), QuranStructure.totalSurahs) }
        let maxAyahs = safeSurah.map { QuranStructure.getSurahByNumber($0).ayahCount }
        let trimmedLabel = alarm.label.trimmingCharacters(in: .whitespacesAndNewlines)
        let safeLabel = String((trimmedLabel.isEmpty ? "ورد القرآن الكريم" : trimmedLabel).prefix(AlarmItem.maxLabelLength))
        let safeTargetPage = min(max(alarm.targetPage, 1), 604)
        let safeStartPage = min(max(alarm.startPage, 1), 604)
        return AlarmItem(
            id: max(alarm.id, 0),
            hour: min(max(alarm.hour, 0), 23),
            minute: min(max(alarm.minute, 0), 59),
            repeatDays: Set(alarm.repeatDays.filter { (1...7).contains($0) }),
            isEnabled: alarm.isEnabled,
            isVibrationEnabled: alarm.isVibrationEnabled,
            label: safeLabel,
            targetPage: safeTargetPage,
            startPage: safeStartPage,
            pageCount: min(max(alarm.pageCount, 1), 604),
            volumeLevel: min(max(alarm.volumeLevel, 1), 100),
            soundUri: alarm.soundUri,
            explicitTriggerMillis: alarm.explicitTriggerMillis,
            snoozeTriggerMillis: alarm.snoozeTriggerMillis,
            isSequential: alarm.isSequential,
            soundTitle: alarm.soundTitle,
            wirdSurahNumber: safeSurah,
            startAyah: maxAyahs.flatMap { maxA in alarm.startAyah.map { min(max($0, 1), maxA) } },
            dailyAyahCount: maxAyahs.flatMap { maxA in alarm.dailyAyahCount.map { min(max($0, 1), maxA) } }
        )
    }
}
