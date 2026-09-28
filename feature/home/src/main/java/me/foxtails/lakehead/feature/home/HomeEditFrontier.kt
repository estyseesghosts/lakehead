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
 * discarded on cancel/exit. It is a legality bound ONLY for MOVE candidates;
 * RESIZE keeps the normal bound ([resizeLegalityRows] ignores it). No second
 * placement engine lives here: every helper below delegates row arithmetic to
 * `core:grid` or performs saturating pixel math.
 */
object HomeEditFrontier {
    /** Bounded working margin appended to the normal boundary at MOVE entry. */
    const val MARGIN_ROWS: Long = 4L

    /** Edge-proximity band for auto-scroll, in dp. */
    const val AUTO_SCROLL_EDGE_DP: Int = 64

    /** Auto-scroll ticker period in milliseconds (stationary-hold continuation). */
    const val AUTO_SCROLL_TICK_MS: Long = 80L
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

/**
 * Creates the transient MOVE frontier from the normal intrinsic extent plus the
 * bounded working margin. The 4096-row cap inside `core:grid` bounds only the
 * temporary margin ahead of the frontier, never the logical grid.
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
 * MOVE legality row bound: the transient frontier's exclusive end when present
 * (saturating to [Int]), otherwise the normal bound. Floors at 1 so
 * `core:grid` size validation never fails.
 */
fun moveLegalityRows(normalRows: Int, frontier: GridVerticalLayout.EditBoundary?): Int {
    require(normalRows > 0) { "normalRows must be positive" }
    val boundary = frontier ?: return normalRows
    return boundary.exclusiveRow.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * RESIZE legality row bound: always the normal bound. The transient frontier
 * never authorizes resize expansion; the parameter is accepted only so call
 * sites document that the frontier was considered and deliberately ignored.
 */
@Suppress("UNUSED_PARAMETER")
fun resizeLegalityRows(normalRows: Int, frontier: GridVerticalLayout.EditBoundary?): Int {
    require(normalRows > 0) { "normalRows must be positive" }
    return normalRows
}

/**
 * Edit-only layout row count: the frontier's absolute exclusive end measured
 * from the signed occupied minimum (rebasing preserves counts, so a non-zero
 * `minRow` is subtracted, not ignored), or the intrinsic count when larger.
 * Null frontier yields the intrinsic count. Saturates instead of overflowing.
 */
fun editLayoutRows(extent: GridVerticalExtent, frontier: GridVerticalLayout.EditBoundary?): Long {
    val boundary = frontier ?: return extent.totalScrollableRows
    val fromMin = saturatingSubtract(boundary.exclusiveRow, extent.occupiedMinRow)
        .coerceAtLeast(0L)
    return maxOf(extent.totalScrollableRows, fromMin)
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
 */
data class PendingMoveCommit(
    val itemId: ItemId,
    val position: LogicalPosition,
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
 * P1 stationary-hold ticker remap: maps the retained viewport-local pointer
 * ([pointerXPx]/[pointerYPx]) with the CURRENT [scrollPx] (post-step actual)
 * and folds it into [session] via MOVE validity (`core:grid` owns legality).
 * The caller retains the latest pointer position across ticks (including ticks
 * with no new pointer events) and invokes this after every scroll step, so a
 * stationary hold's candidate follows the viewport instead of freezing at the
 * pre-scroll cell that release would otherwise commit. Pure and
 * framework-free; the drag loop's per-event path stays authoritative while the
 * pointer moves.
 */
fun remapMoveCandidateAfterScroll(
    session: HomeEditSession,
    pointerXPx: Float,
    pointerYPx: Float,
    scrollPx: Int,
    cellSizePx: Int,
    columnGapPx: Int,
    rowGapPx: Int,
    rowOrigin: Long,
    originXPx: Int,
    originYPx: Long,
    grabOffsetXPx: Float,
    grabOffsetYPx: Float,
    tiles: List<HomeRenderTile>,
    columns: Int,
    moveRows: Int,
): HomeEditSession {
    require(scrollPx >= 0) { "scrollPx must be non-negative" }
    val result = MoveDragMath.cellForPixel(
        xPx = pointerXPx,
        yPx = pointerYPx,
        cellSizePx = cellSizePx.toFloat(),
        columnGapPx = columnGapPx.toFloat(),
        rowGapPx = rowGapPx.toFloat(),
        windowFirstRow = rowOrigin,
        originXPx = originXPx.toFloat(),
        originYPxPx = originYPx.toFloat(),
        scrollYPx = scrollPx.toFloat(),
        grabOffsetXPx = grabOffsetXPx,
        grabOffsetYPx = grabOffsetYPx,
        columns = columns,
        rows = moveRows.toLong(),
        spanColumns = (session as? HomeEditSession.Active)?.candidateSpan?.columns ?: 1,
        spanRows = (session as? HomeEditSession.Active)?.candidateSpan?.rows ?: 1,
    )
    return session.updateMoveCandidate(result, tiles, columns, moveRows)
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

private fun saturatingSubtract(value: Long, decrement: Long): Long =
    try {
        Math.subtractExact(value, decrement)
    } catch (e: ArithmeticException) {
        if (value >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
    }
