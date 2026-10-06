package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.GridVerticalLayout
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition

/**
 * B3 transient edit-frontier policy for move-drag expansion.
 *
 * The frontier is an edit-only [GridVerticalLayout.EditBoundary] owned by
 * `:feature:home` (transient composable state in [HomeCanvasGrid], reset by the
 * home key). It is created from the normal boundary plus a bounded margin when a
 * MOVE drag becomes active, extended one bounded margin at a time via
 * [GridVerticalLayout.advanceEditBoundary] during bottom-edge auto-scroll, and
 * discarded on cancel/exit. It controls edit-time scroll reachability only; grid
 * legality remains owned by `core:grid`. No second placement engine lives here:
 * every helper below delegates row arithmetic to `core:grid` or performs
 * saturating pixel math.
 */
object HomeEditFrontier {
    /** Bounded working margin appended to the normal boundary at MOVE entry. */
// Budget belongs to the MOVE ticker generation; the frontier is only the
// legality extent. A step request advances it by one margin per call, while
// candidate remapping remains a separate post-step operation.
const val MARGIN_ROWS: Long = 4L

    /** Edge-proximity band for auto-scroll, in dp. */
    const val AUTO_SCROLL_EDGE_DP: Int = 32

    /** Dwell on edge entry before the first auto-scroll step, in milliseconds. */
    const val AUTO_SCROLL_DWELL_MS: Long = 200L

    /** Fastest auto-scroll interval (deep at the viewport edge), in milliseconds. */
    const val AUTO_SCROLL_MIN_INTERVAL_MS: Long = 120L

    /** Slowest auto-scroll interval (at the inner band boundary), in milliseconds. */
    const val AUTO_SCROLL_MAX_INTERVAL_MS: Long = 300L
}

/**
 * Creates the transient MOVE frontier from the normal intrinsic extent plus the
 * bounded working margin. The margin bounds temporary edit-time scroll reach,
 * never the logical grid.
 */
fun initEditFrontier(
    extent: GridVerticalExtent,
    usableHeightPx: Int,
    rowStridePx: Long,
    marginRows: Long = HomeEditFrontier.MARGIN_ROWS,
): GridVerticalLayout.EditBoundary = GridVerticalLayout.createEditBoundary(
    extent = extent,
    viewportRows = viewportRowsFor(usableHeightPx, rowStridePx),
    workingMarginRows = marginRows,
)

/**
 * Extends [boundary] by one bounded margin when the moved tile's bottom
 * ([candidate] row plus [spanRows]) reaches the trailing margin. A distant or
 * malformed candidate advances only one margin per call; callers invoke this
 * per auto-scroll step. Pure delegation to `core:grid`.
 */
fun advanceFrontierForMove(
    boundary: GridVerticalLayout.EditBoundary,
    candidate: LogicalPosition,
    spanRows: Int,
): GridVerticalLayout.EditBoundary {
    require(spanRows > 0) { "spanRows must be positive" }
    val bottomExclusive = Math.addExact(candidate.row.toLong(), spanRows.toLong())
    return GridVerticalLayout.advanceEditBoundary(boundary, bottomExclusive)
}

/**
 * Recomputes the frontier after the loaded extent, tile set, or viewport
 * changed, without shrinking an active session unexpectedly: the result keeps
 * the larger of the retained [current] boundary and a fresh init, so growth
 * (commit landed, tile added, viewport shrank) is honored while transient
 * extra rows are never pulled out from under an in-flight drag. A null
 * [current] yields a fresh init.
 */
fun rebaseEditFrontier(
    current: GridVerticalLayout.EditBoundary?,
    extent: GridVerticalExtent,
    usableHeightPx: Int,
    rowStridePx: Long,
    marginRows: Long = HomeEditFrontier.MARGIN_ROWS,
): GridVerticalLayout.EditBoundary {
    val fresh = initEditFrontier(extent, usableHeightPx, rowStridePx, marginRows)
    if (current == null) return fresh
    return if (fresh.exclusiveRow > current.exclusiveRow) fresh else current
}

/**
 * Edit-only layout row count: the frontier's absolute exclusive end measured
 * from logical row zero, or the intrinsic count when larger.
 * Null frontier yields the intrinsic count. Saturates instead of overflowing.
 */
fun editLayoutRows(extent: GridVerticalExtent, frontier: GridVerticalLayout.EditBoundary?): Long {
    val boundary = frontier ?: return extent.totalScrollableRows
    return maxOf(extent.totalScrollableRows, boundary.exclusiveRow.coerceAtLeast(0L))
}

/**
 * Edit-only content height in pixels for [layoutRows] rows: rows × cell plus
 * inter-row gaps with no trailing gap after the final cell. Saturates to
 * [Int.MAX_VALUE] instead of wrapping.
 */
fun editContentHeightPx(layoutRows: Long, cellSizePx: Int, rowGapPx: Int): Int {
    require(layoutRows >= 0L) { "layoutRows must be non-negative" }
    require(cellSizePx > 0) { "cellSizePx must be positive" }
    require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
    return try {
        val cells = Math.multiplyExact(layoutRows, cellSizePx.toLong())
        val gaps = if (layoutRows <= 1L) {
            0L
        } else {
            Math.multiplyExact(Math.subtractExact(layoutRows, 1L), rowGapPx.toLong())
        }
        val height = Math.addExact(cells, gaps)
        if (height > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else height.toInt()
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
}

/**
 * Edit-only scroll maximum in pixels: the edit content height minus the
 * viewport, floored at zero. Saturates instead of wrapping.
 */
fun editScrollMaxPx(
    layoutRows: Long,
    cellSizePx: Int,
    rowGapPx: Int,
    viewportHeightPx: Int,
): Int {
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    val height = editContentHeightPx(layoutRows, cellSizePx, rowGapPx).toLong()
    return Math.subtractExact(height, viewportHeightPx.toLong())
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Pending move-commit lease: the committed target retained until the loaded
 * tile set reflects it. While unsatisfied, another MOVE must not start (the
 * frontier stays leased to the in-flight commit); once satisfied, the caller
 * recomputes the frontier and discards the transient extra.
 *
 * Each move attempt carries a unique [requestId] (assigned by the grid from a
 * monotonically increasing sequence) so the commit-result ack can name the
 * exact attempt it answers: equal item/position acks for different attempts
 * are distinct values and each retriggers ack handling, while a stale ack
 * naming an older attempt never clears a newer lease.
 */
data class PendingMoveCommit(
    val itemId: ItemId,
    val position: LogicalPosition,
    val requestId: Long = 0L,
)

/**
 * Returns true when there is no pending commit ([pending] is null) or the
 * loaded tiles show [pending]'s item at its committed position.
 */
fun isPendingCommitSatisfied(tiles: List<HomeRenderTile>, pending: PendingMoveCommit?): Boolean {
    if (pending == null) return true
    return tiles.any { it.itemId == pending.itemId && it.position == pending.position }
}

/**
 * Reconciles the pending MOVE lease against freshly loaded tiles (B3 P2 hide).
 *
 * Returns null when there is no lease or the loaded tiles already reflect the
 * committed target (lease satisfied — the caller clears it); otherwise returns
 * [pending] unchanged so the MOVE entry gate keeps blocking a stale second
 * move until the load confirms the target or an explicit failure path clears
 * it. The pager-hide path preserves the lease (never clears it); only this
 * satisfaction check and the explicit cancel/failure paths release it. Pure,
 * framework-free.
 */
fun retainedPendingLease(
    tiles: List<HomeRenderTile>,
    pending: PendingMoveCommit?,
): PendingMoveCommit? {
    if (pending == null) return null
    return if (isPendingCommitSatisfied(tiles, pending)) null else pending
}

/** Edge-proximity zone for MOVE auto-scroll, from a viewport-local pointer Y. */
enum class AutoScrollZone { NONE, TOP, BOTTOM }

/**
 * Classifies [pointerYPx] (viewport-local, zero at the viewport top) into an
 * auto-scroll zone: within [edgePx] of the top scrolls up, within [edgePx] of
 * the bottom scrolls down, otherwise no scroll. The top wins when the viewport
 * is shorter than twice the edge band. A zero-height viewport never scrolls.
 */
fun autoScrollZone(pointerYPx: Float, viewportHeightPx: Int, edgePx: Int): AutoScrollZone {
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    require(edgePx > 0) { "edgePx must be positive" }
    if (viewportHeightPx == 0) return AutoScrollZone.NONE
    if (pointerYPx < edgePx.toFloat()) return AutoScrollZone.TOP
    if (pointerYPx > (viewportHeightPx - edgePx).toFloat()) return AutoScrollZone.BOTTOM
    return AutoScrollZone.NONE
}

/** Auto-scroll step in pixels: one row stride, coerced into [Int]. */
fun autoScrollStepPx(rowStridePx: Long): Int {
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    return rowStridePx.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Proximity-scaled auto-scroll interval for a viewport-local pointer Y.
 *
 * Returns null when [pointerYPx] is outside both edge bands ([autoScrollZone]
 * reports NONE). Inside a band the interval scales linearly with depth: the
 * inner boundary scrolls slowest ([HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS])
 * and the viewport edge scrolls fastest
 * ([HomeEditFrontier.AUTO_SCROLL_MIN_INTERVAL_MS]). Top and bottom are
 * symmetric for equal depths. Pointers past the viewport edge clamp to the
 * fastest interval. Pure, framework-free.
 */
fun autoScrollIntervalMs(
    pointerYPx: Float,
    viewportHeightPx: Int,
    edgePx: Int,
): Long? {
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    require(edgePx > 0) { "edgePx must be positive" }
    val zone = autoScrollZone(pointerYPx, viewportHeightPx, edgePx)
    if (zone == AutoScrollZone.NONE) return null
    val depthPx = when (zone) {
        AutoScrollZone.TOP -> edgePx.toFloat() - pointerYPx
        AutoScrollZone.BOTTOM -> pointerYPx - (viewportHeightPx - edgePx).toFloat()
        AutoScrollZone.NONE -> return null
    }.coerceIn(0f, edgePx.toFloat())
    val fraction = depthPx / edgePx.toFloat()
    val max = HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS
    val min = HomeEditFrontier.AUTO_SCROLL_MIN_INTERVAL_MS
    return (max - fraction * (max - min).toFloat()).toLong()
}

/**
 * P1 ticker steal-baseline: the post-step baseline is the ACTUAL
 * `ScrollState.value` after the scroll, never the requested target.
 *
 * The ticker clamps its request to the edit maximum, but the live `ScrollState`
 * still clamps to the older (smaller) normal maximum until the frontier rebase
 * lands — so the actual position can sit below the request. Baselines keyed to
 * the request would read back as a foreign-scroll delta and steal-cancel the
 * drag; baselining the actual keeps auto-scroll-owned steps silent. Pure.
 *
 * @param actualScrollPx the live scroll value observed after the step.
 * @param requestedTargetPx the clamped edit-max target that was requested
 * (accepted only to document the divergence; never returned).
 */
fun tickerScrollBaseline(actualScrollPx: Int, @Suppress("UNUSED_PARAMETER") requestedTargetPx: Int): Int {
    require(actualScrollPx >= 0) { "actualScrollPx must be non-negative" }
    return actualScrollPx
}
