package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pixel rectangle in grid content coordinates, edges exclusive on the right and bottom. */
data class GridPixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Unvalidated top-left cell requested by a pointer. Deliberately unconstrained:
 * a request above row 0 or past the right edge is representable, so it can be
 * judged invalid instead of being clamped into a different cell.
 */
data class RequestedCell(val column: Long, val row: Long)

/**
 * The single owner of every logical↔pixel transform on the Home grid.
 *
 * All coordinates are grid content coordinates (scroll already applied once by
 * the caller via [contentY]). Logical row 0 / column 0 is permanent; [restOffsetYPx]
 * is a presentation-only shift (e.g. centring short content) that callers must
 * hold constant for an edit session. It is never a second logical row origin.
 */
data class HomeGridMetrics(
    val columns: Int,
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val columnGapPx: Float,
    val rowGapPx: Float,
    val originXPx: Float,
    val restOffsetYPx: Float,
) {
    init {
        require(columns > 0) { "columns must be positive, was $columns" }
        require(cellWidthPx > 0f && cellHeightPx > 0f) { "cell size must be positive" }
        require(columnGapPx >= 0f && rowGapPx >= 0f) { "gaps must be non-negative" }
    }

    val columnStridePx: Float get() = cellWidthPx + columnGapPx
    val rowStridePx: Float get() = cellHeightPx + rowGapPx

    fun left(column: Int): Float = originXPx + column * columnStridePx

    fun top(row: Int): Float = restOffsetYPx + row * rowStridePx

    /** Extent of [columnSpan] cells: gaps inside the span, none after the last cell. */
    fun width(columnSpan: Int): Float = columnSpan * cellWidthPx + (columnSpan - 1) * columnGapPx

    fun height(rowSpan: Int): Float = rowSpan * cellHeightPx + (rowSpan - 1) * rowGapPx

    fun rect(position: LogicalPosition, span: LogicalSpan): GridPixelRect {
        val left = left(position.column)
        val top = top(position.row)
        return GridPixelRect(left, top, left + width(span.columns), top + height(span.rows))
    }

    /**
     * [rect] converted to the Int frame the designsystem placement helpers consume.
     * The metrics' float origins carry whole pixels, so rounding is exact.
     */
    fun signedFrame(position: LogicalPosition, span: LogicalSpan): SignedPixelFrame {
        val rect = rect(position, span)
        return SignedPixelFrame(
            left = rect.left.roundToInt(),
            top = rect.top.roundToInt(),
            widthPx = rect.width.roundToInt(),
            heightPx = rect.height.roundToInt(),
        )
    }

    /** Viewport Y → content Y: the one place scroll is applied. */
    fun contentY(viewportYPx: Float, scrollYPx: Float): Float = viewportYPx + scrollYPx

    /**
     * Inverse of [left]/[top] for a tile's top-left corner: the cell whose
     * stride band contains the point (floor). A corner within [SNAP_TOLERANCE_CELLS]
     * below a cell boundary snaps up to it, so `left(c)` always maps back to `c`
     * despite float error. The result is never clamped.
     */
    fun requestedTopLeftCell(contentX: Float, contentY: Float): RequestedCell =
        RequestedCell(
            column = snapCell((contentX - originXPx) / columnStridePx),
            row = snapCell((contentY - restOffsetYPx) / rowStridePx),
        )

    /**
     * Raw span for a resize: the bottom-right corner follows the content point
     * while the tile's top-left [origin] stays fixed. Minimum 1×1.
     */
    fun requestedSpan(origin: LogicalPosition, contentX: Float, contentY: Float): LogicalSpan {
        val columns = ((contentX - left(origin.column)) / columnStridePx + 1f).toInt()
        val rows = ((contentY - top(origin.row)) / rowStridePx + 1f).toInt()
        return LogicalSpan(columns.coerceAtLeast(1), rows.coerceAtLeast(1))
    }

    private fun snapCell(strides: Float): Long = floor(strides + SNAP_TOLERANCE_CELLS).toLong()

    companion object {
        /** Float-error allowance, in cells, applied by the single snap rule. */
        const val SNAP_TOLERANCE_CELLS: Float = 1e-3f
    }
}

/** Metrics for [geometry] at a given [restOffsetYPx]; the one place pixel sizes become Float. */
fun homeGridMetricsFor(
    geometry: SquareCellGeometry,
    columns: Int,
    originXPx: Int,
    restOffsetYPx: Float,
): HomeGridMetrics = HomeGridMetrics(
    columns = columns,
    cellWidthPx = geometry.cellSizePx.toFloat(),
    cellHeightPx = geometry.cellSizePx.toFloat(),
    columnGapPx = geometry.columnGapPx.toFloat(),
    rowGapPx = geometry.rowGapPx.toFloat(),
    originXPx = originXPx.toFloat(),
    restOffsetYPx = restOffsetYPx,
)

/** Whole-pixel row stride used by the integer row-window policies below. */
val HomeGridMetrics.rowStrideCells: Long get() = rowStridePx.toLong()

/**
 * Visible logical row window for a scroll offset: `firstRow = scroll / stride`
 * (floor) from the permanent row-0 origin, covering the viewport with ceiling
 * division plus one partially visible boundary row. The upper bound saturates.
 * Overscan is applied by the caller.
 */
internal fun visibleWindowFor(
    scrollPx: Int,
    viewportHeightPx: Int,
    rowStridePx: Long,
    overscanRows: Long,
): GridRowWindow {
    require(scrollPx >= 0) { "scrollPx must be non-negative" }
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
    val firstRow: Long = scrollPx.toLong() / rowStridePx
    val visibleCount: Long = if (viewportHeightPx == 0) {
        1L
    } else {
        val heightLong = viewportHeightPx.toLong()
        val ceil = Math.addExact(heightLong, Math.subtractExact(rowStridePx, 1L)) / rowStridePx
        Math.addExact(ceil, 1L)
    }
    return GridRowWindow(firstRow = firstRow, lastRowExclusive = firstRow + visibleCount)
}

/**
 * Viewport rows for the edit-boundary policy: usable height over row stride,
 * floored, with a minimum of one so [GridVerticalLayout.createEditBoundary]'s
 * positive-viewport requirement never fails (including zero-height viewports).
 */
fun viewportRowsFor(usableHeightPx: Int, rowStridePx: Long): Long {
    require(usableHeightPx >= 0) { "usableHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    if (usableHeightPx == 0) return 1L
    return maxOf(1L, usableHeightPx.toLong() / rowStridePx)
}

/** Positive validation extent: occupied rows or whole usable rows, never below one. */
internal fun normalLegalityRows(
    extent: GridVerticalExtent,
    usableHeightPx: Int,
    rowStridePx: Long,
): Int {
    require(usableHeightPx >= 0) { "usableHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    val usableRows = if (usableHeightPx == 0) 0L else usableHeightPx.toLong() / rowStridePx
    return maxOf(extent.occupiedMaxRowExclusive, usableRows, 1L)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** The tile whose cell range contains the content point, or null (including above/left of the grid). */
fun HomeGridMetrics.tileAt(tiles: List<HomeRenderTile>, contentX: Float, contentY: Float): HomeRenderTile? {
    val cell = requestedTopLeftCell(contentX, contentY)
    return tiles.firstOrNull { tile ->
        cell.column in tile.position.column.toLong() until
            (tile.position.column + tile.span.columns).toLong() &&
            cell.row in tile.position.row.toLong() until
            (tile.position.row + tile.span.rows).toLong()
    }
}
