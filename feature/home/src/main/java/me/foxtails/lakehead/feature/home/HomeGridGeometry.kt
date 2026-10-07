package me.foxtails.lakehead.feature.home

import androidx.compose.ui.unit.IntOffset
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRest
import me.foxtails.lakehead.core.grid.GridRestOrigin
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.GridVerticalLayout
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
    fun pixelFrame(position: LogicalPosition, span: LogicalSpan): SignedPixelFrame {
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
     * Cell whose top-left corner is nearest a dragged tile's top-left corner
     * (round, half up). Symmetric: half a stride past a cell boundary in either
     * direction switches cells, which equals snapping the tile by its centre.
     * Used for moves only; hit-testing a point stays the floor of
     * [requestedTopLeftCell]. The result is never clamped.
     */
    fun nearestTopLeftCell(contentX: Float, contentY: Float): RequestedCell =
        RequestedCell(
            column = floor((contentX - originXPx) / columnStridePx + 0.5f).toLong(),
            row = floor((contentY - restOffsetYPx) / rowStridePx + 0.5f).toLong(),
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

/** Whole-pixel row stride used by the integer row arithmetic below. */
val HomeGridMetrics.rowStrideCells: Long get() = rowStridePx.toLong()

/**
 * Viewport rows used to size the transient edit workspace: usable height over
 * row stride, floored, with a minimum of one so zero-height viewports stay positive.
 */
fun viewportRowsFor(usableHeightPx: Int, rowStridePx: Long): Long {
    require(usableHeightPx >= 0) { "usableHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    if (usableHeightPx == 0) return 1L
    return maxOf(1L, usableHeightPx.toLong() / rowStridePx)
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

/**
 * Centered horizontal origin (column 0 left edge) for the home canvas grid.
 *
 * Rule: `originX = (availableWidthPx - (count * cell + (count - 1) * gap)) / 2`,
 * computed with [Long] intermediates and flooring division, so the leftover slack
 * is split evenly and left/right slack stays equal within 1px. The geometry formula
 * itself is unchanged; this only centers the already-derived grid.
 */
internal fun centeredOriginX(
    availableWidthPx: Int,
    geometry: SquareCellGeometry,
): Int {
    require(availableWidthPx > 0) { "availableWidthPx must be positive" }
    val count = geometry.columnCount.toLong()
    val gridWidthPx = count * geometry.cellSizePx.toLong() +
        (count - 1L) * geometry.columnGapPx.toLong()
    val originLong = Math.floorDiv(availableWidthPx.toLong() - gridWidthPx, 2L)
    return originLong.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * G5 animated-origin target: the signed rest origin as a Float for the single
 * origin [Animatable] in [HomeCanvasGrid].
 *
 * The rest is the [restFor] result, so this equals the signed
 * `gridOriginY` target (bias centering for short content, zero anchor for tall
 * content); a `null` rest yields `0`. Placements are untouched — only the
 * shared origin moves.
 */
internal fun originTargetFor(rest: GridRest?): Float = viewportFor(rest).gridOriginY.toFloat()

/**
 * G5 snap predicate: snap (no animation) on the first composition, when the
 * rest is `null` (invalid viewport height or rest arithmetic overflow), or when
 * the viewport height is zero (nothing to animate against). Otherwise the
 * shared origin animates toward the new target.
 */
internal fun shouldSnapOrigin(
    rest: GridRest?,
    viewportHeightPx: Int,
    isFirstFrame: Boolean,
): Boolean = isFirstFrame || rest == null || viewportHeightPx <= 0

/**
 * Computes the rest offset for the content.
 *
 * The rest is computed with `minRow = 0`: row zero is the permanent origin and
 * `gridOriginY` is the offset added on top of it (bias centering for short
 * content, zero anchor for tall content). Returns `null` when the viewport height
 * is not positive or the rest arithmetic overflows; callers fall back to `0L`
 * via the rest's `gridOriginY`.
 */
internal fun restFor(
    viewportHeightPx: Int,
    contentHeightPx: Int,
    rowStridePx: Long,
): GridRest? {
    if (viewportHeightPx <= 0) return null
    if (rowStridePx <= 0L) return null
    return try {
        GridRestOrigin.rest(
            viewportHeightPx = viewportHeightPx.toLong(),
            viewportTopPx = 0L,
            contentHeightPx = contentHeightPx.toLong(),
            minRow = 0L,
            cellStridePx = rowStridePx,
        )
    } catch (e: ArithmeticException) {
        null
    }
}

/**
 * Derives a safe minimum content height in pixels from the loaded extent only.
 *
 * Height is `totalScrollableRows * cellSizePx + (totalScrollableRows - 1) * rowGapPx`,
 * a row-count height measured from logical row zero, so leading empty rows count and
 * no origin offset is subtracted here. Overflow or
 * results beyond [Int] saturate to [Int.MAX_VALUE] instead of wrapping; no
 * second extent policy is computed here.
 */
internal fun contentMinHeightPx(
    extent: GridVerticalExtent,
    cellSizePx: Int,
    rowGapPx: Int,
): Int {
    require(cellSizePx > 0) { "cellSizePx must be positive" }
    require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
    return try {
        val total = extent.totalScrollableRows
        val cells = Math.multiplyExact(total, cellSizePx.toLong())
        val gaps = if (total <= 1L) {
            0L
        } else {
            Math.multiplyExact(Math.subtractExact(total, 1L), rowGapPx.toLong())
        }
        val height = Math.addExact(cells, gaps)
        if (height > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else height.toInt()
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
}

/**
 * Floating scroll-container height for the home canvas grid.
 *
 * `max(fullHeightPx, naturalHeightPx + bottomClearancePx)` with checked `Long`
 * arithmetic, saturating to [Int.MAX_VALUE] instead of wrapping: short and
 * exact-fit-safe content fills the full viewport (no tail), while taller content
 * trails exactly the bottom clearance as a transparent reserve — no opaque bottom
 * spacer — so the scroll range (`container - full`) equals the safe overflow
 * `max(0, natural - safe)` and the lowest tile lands clear of the floating pill.
 * Pure, framework-free and unit-testable.
 */
internal fun contentContainerHeightPx(
    naturalHeightPx: Int,
    fullHeightPx: Int,
    bottomClearancePx: Int,
): Int {
    require(naturalHeightPx >= 0) { "naturalHeightPx must be non-negative" }
    require(fullHeightPx >= 0) { "fullHeightPx must be non-negative" }
    require(bottomClearancePx >= 0) { "bottomClearancePx must be non-negative" }
    return try {
        val withReserve = Math.addExact(naturalHeightPx.toLong(), bottomClearancePx.toLong())
        val capped = withReserve.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        maxOf(fullHeightPx, capped)
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
}

/**
 * Effective resize-handle drag-routing side for a tile footprint, in px.
 *
 * The routing handle is the inward bottom-right square of side [handlePx],
 * except on tiles narrower than twice the touch target: a full-size square
 * would consume the whole tile and leave no reachable body (an unarmed
 * small-tile drag could never MOVE, an armed one could never miss RESIZE, and
 * even the remove control center would fall inside the handle rect). The side
 * therefore shrinks to half the tile extent so a body strip always remains
 * reachable. The 48dp tap target on the handle overlay itself is unaffected:
 * tap arm/disarm is owned by the handle's own clickable, not by this
 * drag-routing rect. Pure math, framework-free.
 */
internal fun resizeHandleSidePx(
    tileWidthPx: Int,
    tileHeightPx: Int,
    handlePx: Int,
): Int {
    require(handlePx > 0) { "handlePx must be positive" }
    require(tileWidthPx > 0) { "tileWidthPx must be positive" }
    require(tileHeightPx > 0) { "tileHeightPx must be positive" }
    return minOf(handlePx, tileWidthPx / 2, tileHeightPx / 2)
        .coerceIn(1, minOf(tileWidthPx, tileHeightPx))
}

/**
 * Hit-tests a content-local pointer position against the resize handle corner.
 *
 * The handle occupies the bottom-right square of [originBounds] with side
 * [resizeHandleSidePx] (the full [handlePx] on tiles at least twice as wide
 * and tall, shrunk to half the tile extent on smaller tiles so a body strip
 * stays reachable): `x in [right - side, right]` and
 * `y in [bottom - side, bottom]` (inclusive). The centered visual/touch target extends outward from the corner,
 * but the parent pointer route cannot receive points outside the tile at an edge.
 * Therefore the parent deliberately uses this exact inward policy: the reachable
 * portion is the full `[right - side, right] × [bottom - side, bottom]` square,
 * with no visual dead zone inside the tile. The bounds are the FIXED origin footprint snapshotted at gesture
 * start, never the live candidate bounds that move during the drag. Pure math,
 * framework-free, so drag routing stays unit-testable and `core:grid` legality
 * stays untouched.
 */
internal fun isInHandle(
    x: Float,
    y: Float,
    originBounds: PixelRect,
    handlePx: Int,
): Boolean {
    require(handlePx > 0) { "handlePx must be positive" }
    val sidePx = resizeHandleSidePx(originBounds.width, originBounds.height, handlePx)
    val handleLeft = originBounds.right - sidePx
    val handleTop = originBounds.bottom - sidePx
    return x >= handleLeft.toFloat() &&
        x <= originBounds.right.toFloat() &&
        y >= handleTop.toFloat() &&
        y <= originBounds.bottom.toFloat()
}

/**
 * Signed-frame overload of [isInHandle] for G3 renderer frames.
 *
 * Same bottom-right corner rule over a [SignedPixelFrame] (whose top may be
 * negative above the row origin), with the same [resizeHandleSidePx] small-tile
 * shrink so a body strip stays reachable. Pure math, framework-free.
 */
internal fun isInHandle(
    x: Float,
    y: Float,
    originBounds: SignedPixelFrame,
    handlePx: Int,
): Boolean {
    require(handlePx > 0) { "handlePx must be positive" }
    val sidePx = resizeHandleSidePx(originBounds.widthPx, originBounds.heightPx, handlePx)
    val handleLeft = originBounds.right - sidePx
    val handleTop = originBounds.bottom - sidePx
    return x >= handleLeft.toFloat() &&
        x <= originBounds.right.toFloat() &&
        y >= handleTop.toFloat() &&
        y <= originBounds.bottom.toFloat()
}

/**
 * Positions a corner control for the selected/active tile in absolute content pixels.
 *
 * - [ControlCorner.TOP_RIGHT] pins the control center to `(right, top)`.
 * - [ControlCorner.BOTTOM_RIGHT] pins the control center to `(right, bottom)`.
 * In both cases the returned top-left is the corner minus half the control size;
 * it may extend outside the tile because the visual contract is center-pinned.
 *
 * Pure math, framework-free; callers holding a [SignedPixelFrame] (whose top may be
 * above the viewport) use the overload below.
 * Legality stays owned by `core:grid`.
 */
internal enum class ControlCorner { TOP_RIGHT, BOTTOM_RIGHT }

internal fun cornerControlOffset(
    tileBounds: PixelRect,
    controlSizePx: Int,
    corner: ControlCorner,
): IntOffset {
    require(controlSizePx > 0) { "controlSizePx must be positive" }
    val top = if (corner == ControlCorner.TOP_RIGHT) tileBounds.top else tileBounds.bottom
    return IntOffset(tileBounds.right - controlSizePx / 2, top - controlSizePx / 2)
}

/**
 * Signed-frame overload of [cornerControlOffset] for G3 renderer frames.
 *
 * Same corner rule over a [SignedPixelFrame] (top may be negative above the row
 * origin). Pure math, framework-free.
 */
internal fun cornerControlOffset(
    tileBounds: SignedPixelFrame,
    controlSizePx: Int,
    corner: ControlCorner,
): IntOffset {
    require(controlSizePx > 0) { "controlSizePx must be positive" }
    val top = if (corner == ControlCorner.TOP_RIGHT) tileBounds.top else tileBounds.bottom
    return IntOffset(tileBounds.right - controlSizePx / 2, top - controlSizePx / 2)
}

/**
 * Committed scroll maximum in pixels: `max(0, committedHeight - usableHeight)`
 * using the intrinsic height (`maxExcl - min`), so the final tile bottom lands
 * at/above the usable bottom with no tail. Saturates to [Int]. Pure,
 * framework-free.
 */
internal fun committedScrollMaxPx(
    extent: GridVerticalExtent,
    cellSizePx: Int,
    rowGapPx: Int,
    viewportHeightPx: Int,
): Int {
    require(cellSizePx > 0) { "cellSizePx must be positive" }
    require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    val committedHeight = contentMinHeightPx(extent, cellSizePx, rowGapPx).toLong()
    return Math.subtractExact(committedHeight, viewportHeightPx.toLong())
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Clamps a real scroll offset to the committed range (Int capped): preserves
 * in-range values, clamps on shrink, rests at zero when the range is zero.
 * Feeds the actual offset, never a shadow. Pure, framework-free.
 */
internal fun clampScrollToCommitted(actualPx: Int, committedMaxPx: Int): Int {
    require(actualPx >= 0) { "actualPx must be non-negative" }
    require(committedMaxPx >= 0) { "committedMaxPx must be non-negative" }
    if (committedMaxPx == 0) return 0
    return actualPx.coerceIn(0, committedMaxPx)
}

/**
 * Whether the loaded extent is consistent with all loaded tiles: the height
 * count equals `maxExcl - min` and matches a fresh [GridVerticalLayout.measure] over the tiles. Empty tiles require a
 * zero extent. Pure, framework-free; the remeasure below uses the same grid
 * policy, never a second one.
 */
internal fun isExtentParityOk(extent: GridVerticalExtent, tiles: List<HomeRenderTile>): Boolean {
    if (tiles.isEmpty()) {
        return extent.totalScrollableRows == 0L &&
            extent.occupiedHeightRows == 0L &&
            extent.occupiedMaxRowExclusive == extent.occupiedMinRow
    }
    return try {
        val placements = tiles.map { tile ->
            GridPlacement(
                id = tile.itemId,
                position = GridPosition(tile.position.column, tile.position.row),
                span = GridSpan(tile.span.columns, tile.span.rows),
            )
        }
        // Viewport/margin args do not affect normal intrinsic measurement;
        // any positive viewport with non-negative margin validates the call.
        val remeasured = GridVerticalLayout.measure(
            placements = placements,
            viewportRows = 1L,
            workingMarginRows = 0L,
        )
        remeasured == extent
    } catch (e: RuntimeException) {
        false
    }
}

/** Committed vertical extent and the centring rest derived from it; presentation inputs only. */
internal data class HomeCommittedFrame(val extent: GridVerticalExtent, val rest: GridRest?)

/**
 * Committed extent plus its rest. The natural height is the intrinsic occupied
 * rows × cell plus inter-row gaps, no trailing gap after the final cell; it
 * drives the rest offset only, never the scroll range or legality.
 */
internal fun committedFrameFor(
    loadedExtent: GridVerticalExtent,
    tiles: List<HomeRenderTile>,
    geometry: SquareCellGeometry,
    usableHeightPx: Int,
    rowStridePx: Long,
): HomeCommittedFrame {
    val extent = committedExtentOrRemeasured(loadedExtent, tiles, usableHeightPx, rowStridePx)
    val naturalHeightPx = contentMinHeightPx(extent, geometry.cellSizePx, geometry.rowGapPx)
    return HomeCommittedFrame(
        extent = extent,
        rest = restFor(usableHeightPx, naturalHeightPx, rowStridePx),
    )
}

/**
 * Committed extent for scroll/legality math: the loaded extent when it matches
 * all loaded placements, otherwise a remeasure from those placements with the
 * same `core:grid` policy. Tiles are never moved, dropped, compacted or reset;
 * only the measurement used for range math falls back. Pure, framework-free.
 */
internal fun committedExtentOrRemeasured(
    loadedExtent: GridVerticalExtent,
    tiles: List<HomeRenderTile>,
    usableHeightPx: Int,
    rowStridePx: Long,
): GridVerticalExtent {
    require(usableHeightPx >= 0) { "usableHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    if (isExtentParityOk(loadedExtent, tiles)) return loadedExtent
    val placements = tiles.map { tile ->
        GridPlacement(
            id = tile.itemId,
            position = GridPosition(tile.position.column, tile.position.row),
            span = GridSpan(tile.span.columns, tile.span.rows),
        )
    }
    return try {
        GridVerticalLayout.measure(
            placements = placements,
            viewportRows = viewportRowsFor(usableHeightPx, rowStridePx),
            workingMarginRows = 0L,
        )
    } catch (e: RuntimeException) {
        loadedExtent
    }
}
