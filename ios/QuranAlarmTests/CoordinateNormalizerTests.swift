import XCTest
@testable import QuranAlarm

final class CoordinateNormalizerTests: XCTestCase {

    func testFitCenterProjectionMaintainsAspectRatio() {
        let normalizer = CoordinateNormalizer()
        let proj = normalizer.calculateProjection(viewportWidth: 390, viewportHeight: 700)

        let expectedScale = min(Float(390) / 1260.0, Float(700) / 2038.0)
        XCTAssertEqual(proj.scale, expectedScale, accuracy: 0.0001)
        XCTAssertEqual(proj.renderedWidth, 1260.0 * expectedScale, accuracy: 0.01)
        XCTAssertEqual(proj.renderedHeight, 2038.0 * expectedScale, accuracy: 0.01)
        XCTAssertGreaterThanOrEqual(proj.offsetX, 0)
        XCTAssertGreaterThanOrEqual(proj.offsetY, 0)
    }

    func testNormalizeAndHitTestSnapToNearestLine() {
        let normalizer = CoordinateNormalizer()
        let sampleLines = [
            LineRect(pageNumber: 1, lineNumber: 2, minX: 250, maxX: 1000, minY: 250, maxY: 330),
            LineRect(pageNumber: 1, lineNumber: 3, minX: 250, maxX: 1000, minY: 365, maxY: 445)
        ]

        let normalized = normalizer.normalizeAll(
            lines: sampleLines,
            viewportWidth: 390,
            viewportHeight: 700,
            verticalAbsorptionPx: 8,
            density: 1
        )
        XCTAssertEqual(normalized.count, 2)

        let line2 = normalized[0]
        XCTAssertEqual(normalizer.findLineAt(x: line2.centerX, y: line2.centerY, lines: normalized), 2)

        let line3 = normalized[1]
        XCTAssertEqual(normalizer.findLineAt(x: line3.centerX, y: line3.centerY, lines: normalized), 3)

        // Far outside snap distance returns nil
        XCTAssertNil(normalizer.findLineAt(x: line2.centerX, y: 690, lines: normalized))
    }
}
