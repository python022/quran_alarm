import Foundation

public struct PersistedAlarmSession: Codable, Equatable {
    public let active: Bool
    public let stage: String
    public let alarmId: Int
    public let label: String
    public let startPage: Int
    public let pageCount: Int
    public let volumeLevel: Int
    public let isVibrate: Bool
    public let soundUri: String?
    public let wirdSurahNumber: Int?
    public let startAyah: Int?
    public let dailyAyahCount: Int?
}

public struct SessionReadingProgress: Codable, Equatable {
    public let alarmId: Int
    public let stageName: String
    public let currentPage: Int
    public let completedPages: Set<Int>
    public let visitedLineByPage: [Int: Int]
}

public protocol ReadingProgressStore {
    func load(alarmId: Int) -> SessionReadingProgress?
    func save(_ progress: SessionReadingProgress)
    func clear()
}

/// Unified UserDefaults store for persisting active alarm sessions and Mushaf reading progress.
public final class AlarmSessionStore: ReadingProgressStore {
    public static let stageRinging = "RINGING"
    public static let stageMushafReading = "MUSHAF_READING"

    private let userDefaults: UserDefaults
    private let sessionKey = "quran_alarm_session_v1"
    private let progressKey = "quran_alarm_progress_v1"
    private let lock = NSLock()

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    public func loadSession() -> PersistedAlarmSession? {
        lock.lock()
        defer { lock.unlock() }
        guard let data = userDefaults.data(forKey: sessionKey),
              let session = try? JSONDecoder().decode(PersistedAlarmSession.self, from: data),
              session.active,
              session.alarmId >= 0 else {
            return nil
        }
        return session
    }

    public func saveSession(_ session: PersistedAlarmSession) {
        guard session.alarmId >= 0 else { return }
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(session) {
            userDefaults.set(data, forKey: sessionKey)
        }
    }

    public func markReadingStage(
        alarmId: Int,
        label: String,
        startPage: Int,
        pageCount: Int,
        wirdSurahNumber: Int? = nil,
        startAyah: Int? = nil,
        dailyAyahCount: Int? = nil
    ) {
        guard alarmId >= 0 else { return }
        let existing = loadSession().flatMap { $0.alarmId == alarmId ? $0 : nil }
        saveSession(
            PersistedAlarmSession(
                active: true,
                stage: Self.stageMushafReading,
                alarmId: alarmId,
                label: label,
                startPage: startPage,
                pageCount: pageCount,
                volumeLevel: existing?.volumeLevel ?? 100,
                isVibrate: existing?.isVibrate ?? true,
                soundUri: existing?.soundUri,
                wirdSurahNumber: wirdSurahNumber ?? existing?.wirdSurahNumber,
                startAyah: startAyah ?? existing?.startAyah,
                dailyAyahCount: dailyAyahCount ?? existing?.dailyAyahCount
            )
        )
    }

    public func clearSession() {
        lock.lock()
        defer { lock.unlock() }
        userDefaults.removeObject(forKey: sessionKey)
        userDefaults.removeObject(forKey: progressKey)
    }

    public func load(alarmId: Int) -> SessionReadingProgress? {
        guard alarmId >= 0 else { return nil }
        lock.lock()
        defer { lock.unlock() }
        guard let data = userDefaults.data(forKey: progressKey),
              let progress = try? JSONDecoder().decode(SessionReadingProgress.self, from: data),
              progress.alarmId == alarmId else {
            return nil
        }
        return progress
    }

    public func save(_ progress: SessionReadingProgress) {
        guard progress.alarmId >= 0 else { return }
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(progress) {
            userDefaults.set(data, forKey: progressKey)
        }
    }

    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        userDefaults.removeObject(forKey: progressKey)
    }
}
