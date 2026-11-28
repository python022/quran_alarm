import SwiftUI
import UIKit

/// Authentic Madinah Mushaf page view displaying the WebP texture and highlighting the active line.
public struct QuranPageView: View {
    public let pageNumber: Int
    public let pageImage: UIImage?
    public let lines: [LineRect]
    public let activeLine: Int?
    public let highlightColor: Color
    public let onLineClicked: ((Int) -> Void)?

    private let normalizer = CoordinateNormalizer()

    public init(
        pageNumber: Int,
        pageImage: UIImage?,
        lines: [LineRect],
        activeLine: Int? = nil,
        highlightColor: Color = QuranColors.mushafHighlight,
        onLineClicked: ((Int) -> Void)? = nil
    ) {
        self.pageNumber = pageNumber
        self.pageImage = pageImage
        self.lines = lines
        self.activeLine = activeLine
        self.highlightColor = highlightColor
        self.onLineClicked = onLineClicked
    }

    public var body: some View {
        GeometryReader { geo in
            let canvasW = Float(geo.size.width)
            let canvasH = Float(geo.size.height)
            let proj = normalizer.calculateProjection(viewportWidth: canvasW, viewportHeight: canvasH)
            let targetLine = lines.first(where: { $0.lineNumber == activeLine && $0.isReadable })
            let normHighlight = targetLine.map {
                normalizer.normalize(
                    line: $0,
                    viewportWidth: canvasW,
                    viewportHeight: canvasH,
                    verticalAbsorptionPx: 4.0,
                    density: 1.0
                )
            }
            let normalizedLines = normalizer.normalizeAll(
                lines: lines,
                viewportWidth: canvasW,
                viewportHeight: canvasH,
                verticalAbsorptionPx: 8.0,
                density: 1.0
            )

            ZStack(alignment: .topLeading) {
                QuranColors.mushafPaper

                if let image = pageImage {
                    Image(uiImage: image)
                        .resizable()
                        .interpolation(.high)
                        .frame(
                            width: CGFloat(proj.renderedWidth),
                            height: CGFloat(proj.renderedHeight)
                        )
                        .offset(
                            x: CGFloat(proj.offsetX),
                            y: CGFloat(proj.offsetY)
                        )

                    if let highlight = normHighlight {
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(highlightColor)
                            .frame(
                                width: max(0, CGFloat(highlight.width)),
                                height: max(0, CGFloat(highlight.height))
                            )
                            .offset(
                                x: CGFloat(highlight.left),
                                y: CGFloat(highlight.top)
                            )
                    }
                }
            }
            .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onEnded { value in
                        guard let onLineClicked else { return }
                        let tapX = Float(value.location.x)
                        let tapY = Float(value.location.y)
                        guard let clickedLine = normalizer.findLineAt(
                            x: tapX,
                            y: tapY,
                            lines: normalizedLines,
                            density: 1.0
                        ) else {
                            return
                        }
                        if let selected = lines.first(where: { $0.lineNumber == clickedLine }), selected.isReadable {
                            onLineClicked(clickedLine)
                        } else if let nearestReadable = lines
                            .filter(\.isReadable)
                            .min(by: { abs($0.lineNumber - clickedLine) < abs($1.lineNumber - clickedLine) }) {
                            onLineClicked(nearestReadable.lineNumber)
                        }
                    }
            )
            // Lock coordinate space to LTR so top-left origin matches 1260x2038 Mushaf coordinates
            .environment(\.layoutDirection, .leftToRight)
        }
        .background(QuranColors.mushafPaper)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("صفحة المصحف الشريف رقم \(pageNumber)")
        .accessibilityIdentifier("mushaf_page_canvas")
    }
}
