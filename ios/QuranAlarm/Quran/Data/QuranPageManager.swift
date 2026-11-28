import Foundation
import ImageIO
import UIKit

/// High-performance page image manager for the 604 Madinah Mushaf WebP pages.
/// Enforces a strict memory budget via an LRU cache (default 32 MB).
public final class QuranPageManager {
    public static let pagesAssetSubdir = "quran/pages"
    public static let estimatedPageBytes = 1260 * 2038 * 2

    private struct CacheEntry {
        let image: UIImage
        let bytes: Int
    }

    private let maxCacheMemoryBytes: Int
    private let bundle: Bundle
    private let lock = NSLock()
    private var cache: [Int: CacheEntry] = [:]
    private var accessOrder: [Int] = []
    private var currentCacheBytes: Int = 0
    private var memoryWarningObserver: NSObjectProtocol?

    public init(maxCacheMemoryBytes: Int = 32 * 1024 * 1024, bundle: Bundle = .main) {
        self.maxCacheMemoryBytes = max(1, maxCacheMemoryBytes)
        self.bundle = bundle
        self.memoryWarningObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            guard let self else { return }
            self.trimToSize(maxBytes: self.maxCacheMemoryBytes / 2)
        }
    }

    deinit {
        if let observer = memoryWarningObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    public static func getPageFileName(_ pageNumber: Int) -> String {
        let safePage = min(max(pageNumber, 1), 604)
        return String(format: "page_%03d.webp", safePage)
    }

    public static func getPageResourceName(_ pageNumber: Int) -> String {
        let safePage = min(max(pageNumber, 1), 604)
        return String(format: "page_%03d", safePage)
    }

    public func pageURL(for pageNumber: Int) -> URL? {
        let resourceName = Self.getPageResourceName(pageNumber)
        return bundle.url(forResource: resourceName, withExtension: "webp", subdirectory: Self.pagesAssetSubdir)
            ?? bundle.url(forResource: resourceName, withExtension: "webp")
    }

    public func loadPageImage(pageNumber: Int) async -> UIImage? {
        await Task.detached(priority: .userInitiated) { [weak self] in
            self?.getPageImageSync(pageNumber)
        }.value
    }

    public func getPageImageSync(_ pageNumber: Int) -> UIImage? {
        let safePage = min(max(pageNumber, 1), 604)

        lock.lock()
        if let entry = cache[safePage] {
            touchKeyLocked(safePage)
            lock.unlock()
            return entry.image
        }
        lock.unlock()

        guard let url = pageURL(for: safePage),
              let image = decodeWebP(at: url) else {
            return nil
        }

        let entryBytes = Self.computeEntryBytes(for: image)
        lock.lock()
        insertEntryLocked(page: safePage, image: image, bytes: entryBytes)
        lock.unlock()

        return image
    }

    public func preloadPages(centerPage: Int, radius: Int = 1) async {
        await Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            for offset in (-radius)...radius where offset != 0 {
                let target = QuranStructure.positiveMod(centerPage + offset - 1, 604) + 1
                _ = self.getPageImageSync(target)
            }
        }.value
    }

    public func trimToSize(maxBytes: Int) {
        let targetMax = max(0, maxBytes)
        lock.lock()
        defer { lock.unlock() }
        while currentCacheBytes > targetMax, !accessOrder.isEmpty {
            let oldest = accessOrder.removeFirst()
            if let removed = cache.removeValue(forKey: oldest) {
                currentCacheBytes = max(0, currentCacheBytes - removed.bytes)
            }
        }
    }

    public func clearCache() {
        lock.lock()
        defer { lock.unlock() }
        cache.removeAll()
        accessOrder.removeAll()
        currentCacheBytes = 0
    }

    public func getCacheSizeBytes() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return currentCacheBytes
    }

    public func getCachedPagesCount() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return cache.count
    }

    private func decodeWebP(at url: URL) -> UIImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else {
            return nil
        }
        let options: [CFString: Any] = [
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let cgImage = CGImageSourceCreateImageAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }

    private static func computeEntryBytes(for image: UIImage) -> Int {
        if let cgImage = image.cgImage {
            return max(cgImage.bytesPerRow * cgImage.height, estimatedPageBytes)
        }
        return estimatedPageBytes
    }

    private func touchKeyLocked(_ key: Int) {
        if let idx = accessOrder.firstIndex(of: key) {
            accessOrder.remove(at: idx)
        }
        accessOrder.append(key)
    }

    private func insertEntryLocked(page: Int, image: UIImage, bytes: Int) {
        if let existing = cache.removeValue(forKey: page) {
            currentCacheBytes = max(0, currentCacheBytes - existing.bytes)
            if let idx = accessOrder.firstIndex(of: page) {
                accessOrder.remove(at: idx)
            }
        }
        guard bytes <= maxCacheMemoryBytes else {
            return
        }
        while currentCacheBytes + bytes > maxCacheMemoryBytes, !accessOrder.isEmpty {
            let oldest = accessOrder.removeFirst()
            if let removed = cache.removeValue(forKey: oldest) {
                currentCacheBytes = max(0, currentCacheBytes - removed.bytes)
            }
        }
        cache[page] = CacheEntry(image: image, bytes: bytes)
        accessOrder.append(page)
        currentCacheBytes += bytes
    }
}
