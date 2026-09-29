package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import kotlin.math.floor

data class MoveCoordinateFrame(
    val frozenScrollBaselinePx: Float,
    val originXPx: Float,
    val originYPx: Float,
    val rowOrigin: Long,
    val columnStridePx: Float,
    val rowStridePx: Float,
    val columns: Int,
    val span: GridSpan,
    val legalityRows: Long,
    val grabOffsetXPx: Float,
    val grabOffsetYPx: Float,
)

/**
 * Signed cell coordinates produced while a move is being evaluated.
 *
 * The mapper keeps the frame's structural geometry stable for the gesture.
 */
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
        rowCellSizePx: Float = cellSizePx,
        windowFirstRow: Long,
        originXPx: Float = 0f,
        originYPxPx: Float = 0f,
        scrollXPx: Float = 0f,
        scrollYPx: Float = 0f,
        grabOffsetXPx: Float = 0f,
        grabOffsetYPx: Float = 0f,
    ): MoveCellCandidate {
        val cellWidth = cellSizePx + columnGapPx
        val cellHeight = rowCellSizePx + rowGapPx
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

/**
 * Convert pixels to a fitting grid/span candidate. The fitting span
 * candidate is clamped to
 * `[0, columns - span.columns] x [0, legalityRows - span.rows]`.
 *
 * `OUT_OF_BOUNDS` is returned only when the span cannot fit at all (for
 * example, 4x2 in fewer than 4 columns or fewer than 2 legal rows).
 * There is no free-cell search. Collision at the clamped location remains
 * a collision.
 */
    fun cellForPixel(
        frame: MoveCoordinateFrame,
        pointerXPx: Float,
        pointerYPxViewport: Float,
        currentScrollPx: Float,
    ): MoveCellResult {
        if (frame.span.columns > frame.columns || frame.span.rows.toLong() > frame.legalityRows) {
            return MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS)
        }
        val effectiveScrollPx =
            frame.frozenScrollBaselinePx + (currentScrollPx - frame.frozenScrollBaselinePx)
        val candidate = rawCandidateForPixel(
            xPx = pointerXPx,
            yPx = pointerYPxViewport,
            cellSizePx = frame.columnStridePx,
            rowCellSizePx = frame.rowStridePx,
            columnGapPx = 0f,
            rowGapPx = 0f,
            windowFirstRow = frame.rowOrigin,
            originXPx = frame.originXPx,
            originYPxPx = frame.originYPx,
            scrollXPx = 0f,
            scrollYPx = effectiveScrollPx,
            grabOffsetXPx = frame.grabOffsetXPx,
            grabOffsetYPx = frame.grabOffsetYPx,
        )
        val clamped = MoveCellCandidate(
            candidate.column.coerceIn(0L, (frame.columns - frame.span.columns).toLong()),
            candidate.row.coerceIn(0L, frame.legalityRows - frame.span.rows),
        )
        return resultForCandidate(
            candidate = clamped,
            columns = frame.columns,
            rows = frame.legalityRows,
            spanColumns = frame.span.columns,
            spanRows = frame.span.rows,
        )
    }

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

     /* Raw pixel mapping is used by validated cellForPixel overloads. */
 }
