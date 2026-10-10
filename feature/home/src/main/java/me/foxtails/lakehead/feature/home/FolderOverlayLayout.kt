package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.FloatingOverlayFrame
import me.foxtails.lakehead.core.designsystem.FrameAnchor
import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.HorizontalFrameAnchor
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.VerticalFrameAnchor

/** Panel frame and cell metrics of an open folder; pure so the geometry is testable. */
internal data class FolderOverlayLayout(
    val panel: PixelRect,
    val metrics: HomeGridMetrics,
    /** Full height of the interior grid including padding; the panel scrolls when it is taller. */
    val contentHeightPx: Int,
)

/** Largest share of the viewport height a folder panel may take before it scrolls. */
internal const val FOLDER_PANEL_MAX_HEIGHT_FRACTION: Float = 0.7f

/**
 * Lays an open folder out as a centred floating panel.
 *
 * The interior is the same square-cell grid as Home with [columns] cells across the panel width
 * (five on a normal phone; the count is an input). Tiles sit at their persisted cells and spans;
 * the panel is as tall as the lowest tile plus padding, capped at
 * [FOLDER_PANEL_MAX_HEIGHT_FRACTION] of the viewport, and scrolls beyond that.
 */
internal fun folderOverlayLayout(
    tiles: List<HomeRenderTile>,
    columns: Int,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    marginPx: Int,
    paddingPx: Int,
    gapPx: Int,
    expandRows: Int = 0,
): FolderOverlayLayout {
    require(expandRows >= 0) { "expandRows must be non-negative" }
    require(columns > 0) { "columns must be positive" }
    require(viewportWidthPx > 2 * marginPx) { "viewport is narrower than the panel margins" }
    val panelWidthPx = viewportWidthPx - 2 * marginPx
    val geometry = SquareCellGeometry.fromViewport(
        availableWidthPx = panelWidthPx,
        horizontalPaddingPx = paddingPx,
        columnGapPx = gapPx,
        rowGapPx = gapPx,
        columnCount = columns,
    )
    val metrics = homeGridMetricsFor(
        geometry = geometry,
        columns = columns,
        originXPx = centeredOriginX(panelWidthPx, geometry),
        restOffsetYPx = paddingPx.toFloat(),
    )
    val lowestBottomPx = tiles.maxOfOrNull { metrics.pixelFrame(it.position, it.span).bottom } ?: paddingPx
    val minHeightPx = geometry.cellSizePx + 2 * paddingPx
    val baseContentHeightPx = maxOf(lowestBottomPx + paddingPx, minHeightPx)
    val contentHeightPx = baseContentHeightPx + expandRows * (geometry.cellSizePx + gapPx)
    val maxHeightPx = maxOf((viewportHeightPx * FOLDER_PANEL_MAX_HEIGHT_FRACTION).toInt(), minHeightPx)
    val panelHeightPx = minOf(contentHeightPx, maxHeightPx).coerceAtMost(viewportHeightPx)
    // The top edge stays where the unexpanded panel would sit, so growing never moves a held
    // tile's grid under the finger; extra rows extend downward and shift up only at the screen edge.
    val resting = FloatingOverlayFrame.measure(
        viewport = PixelRect(0, 0, viewportWidthPx, viewportHeightPx),
        size = FrameSize(panelWidthPx, minOf(baseContentHeightPx, maxHeightPx).coerceAtMost(viewportHeightPx)),
        anchor = FrameAnchor(HorizontalFrameAnchor.CENTER, VerticalFrameAnchor.CENTER),
    )
    val top = minOf(resting.top, viewportHeightPx - panelHeightPx)
    val panel = PixelRect(resting.left, top, resting.right, top + panelHeightPx)
    return FolderOverlayLayout(panel, metrics, contentHeightPx)
}
