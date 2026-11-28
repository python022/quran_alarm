import AVFoundation
import Foundation

public struct ImportedAudioResult {
    public let copiedURL: URL?
    public let uriString: String
    public let displayName: String
}

/// Centralized storage and security-validation helper for custom alarm sound files
/// stored in Application Support (`custom_sounds`), enforcing <= 15 MB and offline file-only access.
public enum CustomSoundStorage {
    public static let customSoundsDirName = "custom_sounds"
    public static let maxCustomAudioBytes: Int64 = 15 * 1024 * 1024
    public static let defaultOrphanGraceInterval: TimeInterval = 3600

    private static let allowedExtensions: Set<String> = [
        "m4a", "mp3", "wav", "caf", "aac", "aiff", "aif"
    ]

    public static func customSoundsDirectory(fileManager: FileManager = .default) -> URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return base.appendingPathComponent(customSoundsDirName, isDirectory: true)
    }

    public static func resolveSafeCustomSoundURL(
        uriString: String,
        fileManager: FileManager = .default
    ) -> URL? {
        guard let parsed = URL(string: uriString),
              parsed.scheme?.lowercased() == "file",
              parsed.host == nil || parsed.host?.isEmpty == true,
              parsed.query == nil,
              parsed.fragment == nil else {
            return nil
        }

        let dirPath = customSoundsDirectory(fileManager: fileManager).standardizedFileURL.resolvingSymlinksInPath().path
        let targetURL = parsed.standardizedFileURL.resolvingSymlinksInPath()
        let prefix = dirPath.hasSuffix("/") ? dirPath : dirPath + "/"
        guard targetURL.path.hasPrefix(prefix), fileManager.fileExists(atPath: targetURL.path) else {
            return nil
        }
        return targetURL
    }

    public static func importAudio(
        from sourceURL: URL,
        fallbackDisplayName: String = "ملف صوتي مخصص",
        fileManager: FileManager = .default
    ) -> ImportedAudioResult? {
        guard sourceURL.isFileURL else { return nil }

        let didAccess = sourceURL.startAccessingSecurityScopedResource()
        defer {
            if didAccess {
                sourceURL.stopAccessingSecurityScopedResource()
            }
        }

        let dir = customSoundsDirectory(fileManager: fileManager)
        do {
            try fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        } catch {
            return nil
        }

        let ext = sourceURL.pathExtension.lowercased()
        let safeExt = allowedExtensions.contains(ext) ? ext : "m4a"
        let targetURL = dir.appendingPathComponent("alarm_sound_\(UUID().uuidString).\(safeExt)")

        do {
            let values = try sourceURL.resourceValues(forKeys: [.fileSizeKey, .nameKey])
            if let fileSize = values.fileSize, Int64(fileSize) > maxCustomAudioBytes {
                return nil
            }
            try fileManager.copyItem(at: sourceURL, to: targetURL)

            let copiedAttrs = try fileManager.attributesOfItem(atPath: targetURL.path)
            let copiedSize = (copiedAttrs[.size] as? NSNumber)?.int64Value ?? 0
            guard copiedSize > 0, copiedSize <= maxCustomAudioBytes, isPlayableAudioFile(targetURL) else {
                try? fileManager.removeItem(at: targetURL)
                return nil
            }

            let rawName = sourceURL.deletingPathExtension().lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines)
            let displayName = rawName.isEmpty ? fallbackDisplayName : rawName
            return ImportedAudioResult(
                copiedURL: targetURL,
                uriString: targetURL.absoluteString,
                displayName: displayName
            )
        } catch {
            try? fileManager.removeItem(at: targetURL)
            return nil
        }
    }

    public static func isPlayableAudioFile(_ url: URL) -> Bool {
        if let player = try? AVAudioPlayer(contentsOf: url), player.duration > 0 {
            return true
        }
        return false
    }

    public static func deleteIfUnused(
        uriString: String,
        stillReferenced: Bool,
        fileManager: FileManager = .default
    ) {
        guard !stillReferenced,
              let targetURL = resolveSafeCustomSoundURL(uriString: uriString, fileManager: fileManager) else {
            return
        }
        try? fileManager.removeItem(at: targetURL)
    }

    public static func cleanupOrphanedSounds(
        referencedUriStrings: Set<String>,
        minAge: TimeInterval = defaultOrphanGraceInterval,
        now: Date = Date(),
        fileManager: FileManager = .default
    ) {
        let dir = customSoundsDirectory(fileManager: fileManager)
        guard fileManager.fileExists(atPath: dir.path) else { return }

        let referencedPaths = Set(
            referencedUriStrings.compactMap {
                resolveSafeCustomSoundURL(uriString: $0, fileManager: fileManager)?.path
            }
        )

        guard let contents = try? fileManager.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.isRegularFileKey, .contentModificationDateKey]
        ) else {
            return
        }

        for fileURL in contents {
            let resolvedPath = fileURL.standardizedFileURL.resolvingSymlinksInPath().path
            let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey, .contentModificationDateKey])
            guard values?.isRegularFile == true else { continue }
            let modDate = values?.contentModificationDate ?? now
            if !referencedPaths.contains(resolvedPath) && now.timeIntervalSince(modDate) >= minAge {
                try? fileManager.removeItem(at: fileURL)
            }
        }
    }
}
