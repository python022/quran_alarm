import Foundation

/// Screen-projected bounding box for a line, including normalized [0..1] ratios.
public struct NormalizedLineRect: Equatable {
    public let lineNumber: Int
    public let left: Float
    public let top: Float
    public let right: Float
    public let bottom: Float
    public let leftRatio: Float
    public let topRatio: Float
    public let rightRatio: Float
    public let bottomRatio: Float

    public var width: Float { right - left }
    public var height: Float { bottom - top }
    public var centerX: Float { (left + right) / 2.0 }
    public var centerY: Float { (top + bottom) / 2.0 }

    public func contains(x: Float, y: Float) -> Bool {
        return x >= left && x <= right && y >= top && y <= bottom
    }

    public func verticalDistanceTo(_ y: Float) -> Float {
        if y < top { return top - y }
        if y > bottom { return y - bottom }
        return 0
    }
}
