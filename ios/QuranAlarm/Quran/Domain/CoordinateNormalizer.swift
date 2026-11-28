import Foundation

/// Transforms reference Madinah Mushaf coordinates (1260x2038) into screen viewport coordinates
/// while maintaining aspect ratio (FitCenter) and adding vertical absorption margins.
public struct CoordinateNormalizer {
    public struct ViewportProjection: Equatable {
        public let scale: Float
        public let offsetX: Float
        public let offsetY: Float
        public let renderedWidth: Float
        public let renderedHeight: Float
    }

    public let referenceWidth: Float
    public let referenceHeight: Float

    public init(referenceWidth: Float = 1260.0, referenceHeight: Float = 2038.0) {
        self.referenceWidth = referenceWidth
        self.referenceHeight = referenceHeight
    }

    public func calculateProjection(viewportWidth: Float, viewportHeight: Float) -> ViewportProjection {
        let safeWidth = viewportWidth > 0 ? viewportWidth : referenceWidth
        let safeHeight = viewportHeight > 0 ? viewportHeight : referenceHeight

        let scale = min(safeWidth / referenceWidth, safeHeight / referenceHeight)
        let renderedWidth = referenceWidth * scale
        let renderedHeight = referenceHeight * scale
        let offsetX = (safeWidth - renderedWidth) / 2.0
        let offsetY = (safeHeight - renderedHeight) / 2.0

        return ViewportProjection(
            scale: scale,
            offsetX: offsetX,
            offsetY: offsetY,
            renderedWidth: renderedWidth,
            renderedHeight: renderedHeight
        )
    }

    public func normalize(
        line: LineRect,
        viewportWidth: Float,
        viewportHeight: Float,
        verticalAbsorptionPx: Float = 8.0,
        density: Float = 1.0
    ) -> NormalizedLineRect {
        let proj = calculateProjection(viewportWidth: viewportWidth, viewportHeight: viewportHeight)
        let effectiveAbsorptionPx = verticalAbsorptionPx * density

        let screenLeft = proj.offsetX + Float(line.minX) * proj.scale
        let screenRight = proj.offsetX + Float(line.maxX) * proj.scale
        let screenTop = max(0, proj.offsetY + Float(line.minY) * proj.scale - effectiveAbsorptionPx)
        let screenBottom = min(viewportHeight, proj.offsetY + Float(line.maxY) * proj.scale + effectiveAbsorptionPx)

        let leftRatio = min(max(Float(line.minX) / referenceWidth, 0), 1)
        let rightRatio = min(max(Float(line.maxX) / referenceWidth, 0), 1)
        let topRatio = min(max(Float(line.minY) / referenceHeight, 0), 1)
        let bottomRatio = min(max(Float(line.maxY) / referenceHeight, 0), 1)

        return NormalizedLineRect(
            lineNumber: line.lineNumber,
            left: screenLeft,
            top: screenTop,
            right: screenRight,
            bottom: screenBottom,
            leftRatio: leftRatio,
            topRatio: topRatio,
            rightRatio: rightRatio,
            bottomRatio: bottomRatio
        )
    }

    public func normalizeAll(
        lines: [LineRect],
        viewportWidth: Float,
        viewportHeight: Float,
        verticalAbsorptionPx: Float = 8.0,
        density: Float = 1.0
    ) -> [NormalizedLineRect] {
        lines.map {
            normalize(
                line: $0,
                viewportWidth: viewportWidth,
                viewportHeight: viewportHeight,
                verticalAbsorptionPx: verticalAbsorptionPx,
                density: density
            )
        }
    }

    public func findLineAt(
        x: Float,
        y: Float,
        lines: [NormalizedLineRect],
        maxSnapDistancePx: Float = 32.0,
        density: Float = 1.0
    ) -> Int? {
        guard !lines.isEmpty else { return nil }

        let directHits = lines.filter { $0.contains(x: x, y: y) }
        if let bestDirect = directHits.min(by: { abs($0.centerY - y) < abs($1.centerY - y) }) {
            return bestDirect.lineNumber
        }

        let horizontalMargin = 20.0 * density
        let effectiveSnapDistance = maxSnapDistancePx * density
        let horizontalMatch = lines.filter { x >= ($0.left - horizontalMargin) && x <= ($0.right + horizontalMargin) }
        let candidatePool = horizontalMatch.isEmpty ? lines : horizontalMatch

        if let closest = candidatePool.min(by: { $0.verticalDistanceTo(y) < $1.verticalDistanceTo(y) }),
           closest.verticalDistanceTo(y) <= effectiveSnapDistance {
            return closest.lineNumber
        }
        return nil
    }
}
