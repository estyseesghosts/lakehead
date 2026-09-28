package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.grid.GridRules
import kotlin.math.floor

/** Signed cell coordinates produced while a move is still being evaluated. */
data class MoveCellCandidate(
    val column: Long,
    val row: Long,
)

/** The only point at which a signed move candidate becomes a domain position. */
sealed class MoveCellResult {
    data class Valid(
        val candidate: MoveCellCandidate,
        val position: LogicalPosition,
    ) : MoveCellResult()

    data class Rejected(val reason: GridRules.Rejection) : MoveCellResult()
}

/**
 * Pixel-to-cell conversion for move-drag gestures.
 * Pure math, framework-free: takes raw Float values.
 */
object MoveDragMath {

    /**
     * Convert pixels to a signed candidate without constructing [LogicalPosition].
     *
     * [cellForPixel] validates this candidate, including its occupied span, and
     * returns [MoveCellResult.Valid] or [MoveCellResult.Rejected]. This helper
     * preserves negative coordinates so they become rejected results rather than
     * being silently converted into positions. The caller's viewport, origin,
     * scroll and grab offset are all applied exactly once.
     */
    internal fun rawCandidateForPixel(
        xPx: Float,
        yPx: Float,
        cellSizePx: Float,
        columnGapPx: Float,
        rowGapPx: Float,
        windowFirstRow: Long,
        originXPx: Float = 0f,
        originYPxPx: Float = 0f,
        scrollXPx: Float = 0f,
        scrollYPx: Float = 0f,
        grabOffsetXPx: Float = 0f,
        grabOffsetYPx: Float = 0f,
    ): MoveCellCandidate {
        val cellWidth = cellSizePx + columnGapPx
        val cellHeight = cellSizePx + rowGapPx
        val contentX = xPx + scrollXPx - grabOffsetXPx
        val contentY = yPx + scrollYPx - grabOffsetYPx
        val column = if (cellWidth > 0f) {
            floor((contentX - originXPx) / cellWidth).toLong()
        } else {
            0L
        }
        val row = if (cellHeight > 0f) {
            windowFirstRow + floor((contentY - originYPxPx) / cellHeight).toLong()
        } else {
            0L
        }
        return MoveCellCandidate(column, row)
    }

    /**
     * Validate a signed candidate, including the complete occupied span, before
     * creating the non-negative [LogicalPosition] required by the model.
     */
    internal fun resultForCandidate(
        candidate: MoveCellCandidate,
        columns: Int,
        rows: Long,
        spanColumns: Int = 1,
        spanRows: Int = 1,
    ): MoveCellResult {
        if (!GridRules.isWithinBounds(
                candidate.column,
                candidate.row,
                spanColumns,
                spanRows,
                columns,
                rows,
            )
        ) {
            return MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS)
        }
        val position = LogicalPosition(candidate.column.toInt(), candidate.row.toInt())
        return MoveCellResult.Valid(candidate, position)
    }

    /** Convert pixels and apply grid/span bounds without clamping. */
    fun cellForPixel(
        xPx: Float,
        yPx: Float,
        cellSizePx: Float,
        columnGapPx: Float,
        rowGapPx: Float,
        windowFirstRow: Long,
        columns: Int,
        rows: Long,
        spanColumns: Int = 1,
        spanRows: Int = 1,
        originXPx: Float = 0f,
        originYPxPx: Float = 0f,
        scrollXPx: Float = 0f,
        scrollYPx: Float = 0f,
        grabOffsetXPx: Float = 0f,
        grabOffsetYPx: Float = 0f,
    ): MoveCellResult = resultForCandidate(
        rawCandidateForPixel(
            xPx = xPx,
            yPx = yPx,
            cellSizePx = cellSizePx,
            columnGapPx = columnGapPx,
            rowGapPx = rowGapPx,
            windowFirstRow = windowFirstRow,
            originXPx = originXPx,
            originYPxPx = originYPxPx,
            scrollXPx = scrollXPx,
            scrollYPx = scrollYPx,
            grabOffsetXPx = grabOffsetXPx,
            grabOffsetYPx = grabOffsetYPx,
        ),
        columns = columns,
        rows = rows,
        spanColumns = spanColumns,
        spanRows = spanRows,
    )

     /*
      * The raw helper is intentionally the only other pixel mapping implementation.
       * Keep it internal to prevent production callers from bypassing validation;
       * callers should use cellForPixel, which validates bounds and returns a
       * MoveCellResult rather than an unchecked LogicalPosition.
     * @param xPx horizontal pixel offset from the grid's left edge
     * @param yPx vertical pixel offset from the grid's top edge (within the visible window)
     * @param cellSizePx cell size in pixels (from SquareCellGeometry)
     * @param columnGapPx gap between columns in pixels
     * @param rowGapPx gap between rows in pixels
     * @param windowFirstRow the first visible row index (for windowed grids)
     * @param originXPx horizontal pixel origin of column 0's left edge (caller-centered
     * remainder split); defaults to 0 for edge-to-edge callers
     * @param originYPxPx vertical pixel origin of the signed renderer frame (G2 rest
     * `gridOriginY` in rebased coordinates); defaults to 0 for callers without rest offset
     * @param scrollXPx horizontal content scroll in pixels. The value is added to the
     * viewport-local pointer position; defaults to 0.
     * @param scrollYPx vertical content scroll in pixels. The value is added to the
     * viewport-local pointer position; defaults to 0.
     * @param grabOffsetXPx horizontal distance from the tile origin to the pointer at
     * drag start; defaults to 0.
     * @param grabOffsetYPx vertical distance from the tile origin to the pointer at
     * drag start; defaults to 0.
      * @return MoveCellResult.Valid for an in-bounds candidate, or
      * MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS) otherwise,
      * including negative coordinates
      */
 }
