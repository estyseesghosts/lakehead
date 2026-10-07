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
    /**
     * Empty rows the gap normaliser tolerates below the last tile. A moved tile
     * may therefore start this far under the committed bottom and no further.
     */
    const val TRAILING_EMPTY_ROWS: Long = 2L
}

/**
 * Workspace of a MOVE: the committed bottom, plus the [HomeEditWorkspace.TRAILING_EMPTY_ROWS]
 * the normaliser allows, plus the moved tile's own [spanRows]. A drop deeper than
 * that would be closed up on release, so there is nothing further to reach. The
 * committed bottom itself is never cut.
 */
internal fun moveWorkspaceBottomRow(extent: GridVerticalExtent, spanRows: Int): Long {
    val committedBottom = extent.occupiedMaxRowExclusive.coerceAtLeast(0L)
    return committedBottom + HomeEditWorkspace.TRAILING_EMPTY_ROWS + spanRows.coerceAtLeast(1)
}

/**
 * Largest scroll offset the committed content alone allows (no workspace). The
 * release path animates the scroll back to at most this before the workspace is
 * dropped, so the content never jumps when it collapses.
 */
internal fun committedMaxScrollPx(
    extent: GridVerticalExtent,
    metrics: HomeGridMetrics,
    fullViewportPx: Int,
    bottomClearancePx: Int,
): Int = (
    contentContainerHeightPx(
        naturalHeightPx = workspaceBottomPx(extent.occupiedMaxRowExclusive.coerceAtLeast(0L), metrics),
        fullHeightPx = fullViewportPx,
        bottomClearancePx = bottomClearancePx,
    ) - fullViewportPx
    ).coerceAtLeast(0)

/**
 * Measured-content floor: the full viewport, or while a MOVE (or its release settle) holds a
 * edit workspace's bottom (plus clearance) when that is taller. Pure
 * derivation; the workspace row itself stays owned by the composable.
 */
internal fun moveWorkspaceFloorPx(
    workspaceBottomRow: Long?,
    metrics: HomeGridMetrics,
    fullViewportPx: Int,
    bottomClearancePx: Int,
): Int = workspaceBottomRow?.let { rows ->
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
