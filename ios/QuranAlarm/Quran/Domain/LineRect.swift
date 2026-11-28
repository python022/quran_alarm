import Foundation

public enum LineType: Equatable, Hashable {
    case ayahText
    case surahHeader
    case basmalah

    public var isReadable: Bool {
        self != .surahHeader
    }
}

/// Pixel bounding box of a single printed line in the Madinah Mushaf (1260x2038 reference).
public struct LineRect: Equatable, Hashable {
    public let pageNumber: Int
    public let lineNumber: Int
    public let minX: Int
    public let maxX: Int
    public let minY: Int
    public let maxY: Int
    public let lineType: LineType

    public init(
        pageNumber: Int,
        lineNumber: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        lineType: LineType = .ayahText
    ) {
        self.pageNumber = pageNumber
        self.lineNumber = lineNumber
        self.minX = minX
        self.maxX = maxX
        self.minY = minY
        self.maxY = maxY
        self.lineType = lineType
    }

    public var width: Int { maxX - minX }
    public var height: Int { maxY - minY }
    public var centerX: Float { Float(minX + maxX) / 2.0 }
    public var centerY: Float { Float(minY + maxY) / 2.0 }
    public var isReadable: Bool { lineType.isReadable }

    public func isValid() -> Bool {
        return (1...604).contains(pageNumber)
            && (1...15).contains(lineNumber)
            && minX >= 0 && maxX > minX && maxX <= 1260
            && minY >= 0 && maxY > minY && maxY <= 2038
    }
}

public struct AyahPageRange: Equatable {
    public let startPage: Int
    public let endPage: Int
    public let startLine: Int
    public let endLine: Int

    public init(startPage: Int, endPage: Int, startLine: Int, endLine: Int) {
        self.startPage = startPage
        self.endPage = endPage
        self.startLine = startLine
        self.endLine = endLine
    }
}
