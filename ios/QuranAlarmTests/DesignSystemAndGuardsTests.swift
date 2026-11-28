import UIKit
import XCTest
@testable import QuranAlarm

final class DesignSystemAndGuardsTests: XCTestCase {

    func testOfflineCairoFontsLoadWithExactPostScriptNames() {
        QuranTypography.registerFontsIfNeeded()

        let expectedNames = [
            QuranTypography.regularPostScriptName,
            QuranTypography.mediumPostScriptName,
            QuranTypography.semiboldPostScriptName,
            QuranTypography.boldPostScriptName
        ]
        for psName in expectedNames {
            let font = UIFont(name: psName, size: 16)
            XCTAssertNotNil(font, "Offline Cairo font \(psName) must load from Bundle")
            XCTAssertEqual(font?.fontName, psName)
        }
    }

    func testNonInvertedRangeGuardRejectsStartAfterEnd() {
        XCTAssertTrue(QuranStructure.isValidNonInvertedRange(startPage: 1, endPage: 1))
        XCTAssertTrue(QuranStructure.isValidNonInvertedRange(startPage: 293, endPage: 304))
        XCTAssertFalse(QuranStructure.isValidNonInvertedRange(startPage: 10, endPage: 9))
        XCTAssertFalse(QuranStructure.isValidNonInvertedRange(startPage: 604, endPage: 1))

        XCTAssertEqual(try QuranStructure.computeNonInvertedPageCount(startPage: 293, endPage: 304), 12)
        XCTAssertThrowsError(try QuranStructure.computeNonInvertedPageCount(startPage: 15, endPage: 10))
    }

    func testQuranColorsMatchDesignSystemSpecification() {
        XCTAssertEqual(QuranColors.bgPrimaryHex, 0xFF0B1117)
        XCTAssertEqual(QuranColors.bgCardHex, 0xFF121820)
        XCTAssertEqual(QuranColors.bgElevatedHex, 0xFF16202A)
        XCTAssertEqual(QuranColors.borderSubtleHex, 0xFF1F2D3D)
        XCTAssertEqual(QuranColors.ctaBgHex, 0xFF1AD699)
        XCTAssertEqual(QuranColors.ctaTextHex, 0xFF071A14)
        XCTAssertEqual(QuranColors.activePillBgHex, 0xFF142E28)
        XCTAssertEqual(QuranColors.goldBadgeBgHex, 0xFF1B2229)
        XCTAssertEqual(QuranColors.goldBadgeBorderHex, 0xFF2D3741)
        XCTAssertEqual(QuranColors.goldBadgeTextHex, 0xFFD4AF37)
        XCTAssertEqual(QuranColors.textPrimaryHex, 0xFFF8FAFC)
        XCTAssertEqual(QuranColors.textSecondaryHex, 0xFF94A3B8)
        XCTAssertEqual(QuranColors.textMutedHex, 0xFF64748B)
    }
}
