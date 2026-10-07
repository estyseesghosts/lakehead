package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridVerticalExtent
import kotlin.math.roundToLong

/**
 * The transient edit workspace of a MOVE: one row count, `workspaceBottomRow`
 * (exclusive, counted from logical row zero), that says how tall the scrollable
 * content must be so the rows below the committed tiles can be reached.
 *
 * It controls **only measured content height**. Candidate cells and their
 * legality are computed from the pointer, the scroll and the occupancy
 * snapshot, none of which read this value, so growing the workspace can never
 * change which cell is requested or whether it is legal. Compose measures the
 * content from it and the real `ScrollState.maxValue` follows; there is no
 * second, synthetic scroll maximum.
 */
internal object HomeEditWorkspace {
    /** Rows kept measured beyond the area an auto-scroll step is about to reveal. */
    const val LOOKAHEAD_ROWS: Long = 4L

    /** Upper bound on workspace height, kept well under Compose's measure constraint limit. */
    const val MAX_HEIGHT_PX: Long = 200_000L
}

/** Most rows a workspace may span at [rowStridePx], never below one. */
internal fun workspaceRowCap(rowStridePx: Long): Long {
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    return maxOf(1L, HomeEditWorkspace.MAX_HEIGHT_PX / rowStridePx)
}

/**
 * Workspace at MOVE start: the committed content bottom plus the rows of one
 * visible edit area and the lookahead, so the user can drag below the last tile
 * and the first auto-scroll step already has measured room. The committed
 * bottom is never cut by the cap; only the added room is.
 */
internal fun initialWorkspaceBottomRow(
    extent: GridVerticalExtent,
    usableHeightPx: Int,
    rowStridePx: Long,
): Long {
    val committedBottom = extent.occupiedMaxRowExclusive.coerceAtLeast(0L)
    val withRoom = committedBottom + viewportRowsFor(usableHeightPx, rowStridePx) +
        HomeEditWorkspace.LOOKAHEAD_ROWS
    return maxOf(committedBottom, minOf(withRoom, workspaceRowCap(rowStridePx)))
}

/**
 * Smallest row count whose content, with [clearancePx] after it, reaches
 * [contentBottomPx] (a content-space Y). The rest offset is included, exactly as
 * [workspaceBottomPx] includes it.
 */
internal fun workspaceRowsToCover(
    contentBottomPx: Long,
    restOffsetYPx: Int,
    rowStridePx: Long,
    rowGapPx: Int,
    clearancePx: Int,
): Long {
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    val need = contentBottomPx - clearancePx + rowGapPx - restOffsetYPx
    if (need <= 0L) return 0L
    return (need + rowStridePx - 1L) / rowStridePx
}

/**
 * Next workspace for a bottom auto-scroll step that will reveal content down to
 * [revealedBottomPx]: grows to cover it plus [HomeEditWorkspace.LOOKAHEAD_ROWS],
 * never shrinks, never exceeds [workspaceRowCap]. Returns [current] when it
 * already covers the step.
 */
internal fun extendedWorkspaceBottomRow(
    current: Long,
    revealedBottomPx: Long,
    metrics: HomeGridMetrics,
    clearancePx: Int,
): Long {
    val stride = metrics.rowStrideCells
    val cover = workspaceRowsToCover(
        contentBottomPx = revealedBottomPx,
        restOffsetYPx = metrics.restOffsetYPx.roundToLong().toInt(),
        rowStridePx = stride,
        rowGapPx = metrics.rowGapPx.toInt(),
        clearancePx = clearancePx,
    )
    val wanted = minOf(cover + HomeEditWorkspace.LOOKAHEAD_ROWS, workspaceRowCap(stride))
    return maxOf(current, wanted)
}

/**
 * Measured-content floor: the full viewport, or while a MOVE is active the
 * edit workspace's bottom (plus clearance) when that is taller. Pure
 * derivation; the workspace row itself stays owned by the composable.
 */
internal fun moveWorkspaceFloorPx(
    workspaceBottomRow: Long?,
    moveActive: Boolean,
    metrics: HomeGridMetrics,
    fullViewportPx: Int,
    bottomClearancePx: Int,
): Int = workspaceBottomRow?.takeIf { moveActive }?.let { rows ->
    contentContainerHeightPx(
        naturalHeightPx = workspaceBottomPx(rows, metrics),
        fullHeightPx = fullViewportPx,
        bottomClearancePx = bottomClearancePx,
    )
} ?: fullViewportPx

/**
 * Pixel Y of the bottom edge of row `bottomRowExclusive - 1` in content
 * coordinates (rest offset included), saturating to [Int.MAX_VALUE]. Zero rows
 * is the rest offset itself.
 */
internal fun workspaceBottomPx(bottomRowExclusive: Long, metrics: HomeGridMetrics): Int {
    require(bottomRowExclusive >= 0L) { "bottomRowExclusive must be non-negative" }
    val rest = metrics.restOffsetYPx.roundToLong()
    if (bottomRowExclusive == 0L) return rest.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val stride = metrics.rowStrideCells
    val cell = metrics.cellHeightPx.toLong()
    return try {
        val top = Math.addExact(rest, Math.multiplyExact(bottomRowExclusive - 1L, stride))
        Math.addExact(top, cell).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
}
