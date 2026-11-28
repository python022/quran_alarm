import XCTest
@testable import QuranAlarm

final class QuranPageManagerTests: XCTestCase {

    func testAll604WebPPagesExistInBundle() {
        let manager = QuranPageManager(bundle: .main)
        for page in 1...604 {
            XCTAssertNotNil(manager.pageURL(for: page), "Missing bundled WebP for page \(page)")
        }
    }

    func testDecodesPagesAndEnforcesLRUMemoryBudget() {
        let budget = 32 * 1024 * 1024
        let manager = QuranPageManager(maxCacheMemoryBytes: budget, bundle: .main)

        let firstImage = manager.getPageImageSync(1)
        XCTAssertNotNil(firstImage)
        XCTAssertEqual(firstImage?.cgImage?.width, 1260)
        XCTAssertEqual(firstImage?.cgImage?.height, 2038)

        for page in 1...20 {
            let img = manager.getPageImageSync(page)
            XCTAssertNotNil(img, "Failed to decode WebP page \(page)")
            XCTAssertLessThanOrEqual(manager.getCacheSizeBytes(), budget)
        }

        XCTAssertGreaterThan(manager.getCachedPagesCount(), 0)
        manager.trimToSize(maxBytes: 1)
        XCTAssertEqual(manager.getCachedPagesCount(), 0)
        XCTAssertEqual(manager.getCacheSizeBytes(), 0)
    }
}
