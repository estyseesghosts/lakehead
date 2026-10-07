package me.foxtails.lakehead.feature.home

import androidx.compose.ui.geometry.Offset
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.LogicalPosition

/**
 * Immutable inputs of a MOVE mapping: the grid metrics, the span being moved
 * (never changed by a move) and the point grabbed inside the tile, measured
 * from its top-left in content space.
 */
data class MoveCoordinateFrame(
    val metrics: HomeGridMetrics,
    val span: GridSpan,
    val grabOffsetXPx: Float,
    val grabOffsetYPx: Float,
) {
    val columns: Int get() = metrics.columns
}

/** A requested cell is either legal, or rejected while keeping the raw (possibly negative) request. */
sealed class MoveCellResult {
    data class Valid(
        val candidate: RequestedCell,
        val position: LogicalPosition,
    ) : MoveCellResult()

    data class Rejected(
        val reason: GridRules.Rejection,
        val request: RequestedCell,
    ) : MoveCellResult()
}

/**
 * Pointer-to-cell mapping for move-drag gestures. Pure and absolute: the
 * requested cell depends only on the current pointer, the current scroll and the
 * frame's immutable grab offset and metrics. Nothing is clamped; legality is
 * judged separately from the raw request.
 */
object MoveDragMath {

    /** `tileTopLeft = (pointer + scroll) − grabOffset`, snapped to the nearest cell. Never clamped. */
    fun requestedCell(
        frame: MoveCoordinateFrame,
        pointerXPx: Float,
        pointerYPxViewport: Float,
        currentScrollPx: Float,
    ): RequestedCell = frame.metrics.nearestTopLeftCell(
        contentX = pointerXPx - frame.grabOffsetXPx,
        contentY = frame.metrics.contentY(pointerYPxViewport, currentScrollPx) - frame.grabOffsetYPx,
    )

    /**
     * Translation that carries the lifted tile from its committed cell to the
     * finger: `(pointer + scroll) − grabOffset − committedTopLeft`. Unsnapped and
     * unclamped (the tile follows the finger; the outline shows where it lands).
     */
    fun liftTranslation(
        move: DragState.Move,
        pointerXPx: Float,
        pointerYPxViewport: Float,
        currentScrollPx: Float,
    ): Offset {
        val committed = move.metrics.rect(move.originalPosition, move.span)
        return Offset(
            x = pointerXPx - move.grabOffsetXPx - committed.left,
            y = move.metrics.contentY(pointerYPxViewport, currentScrollPx) -
                move.grabOffsetYPx - committed.top,
        )
    }

    /** Validates the complete occupied span of [request] before building a domain position. */
    internal fun validate(request: RequestedCell, columns: Int, span: GridSpan): MoveCellResult {
        val withinBounds = GridRules.isWithinBounds(
            request.column,
            request.row,
            span.columns,
            span.rows,
            columns,
        )
        if (!withinBounds || request.column > Int.MAX_VALUE || request.row > Int.MAX_VALUE) {
            return MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS, request)
        }
        return MoveCellResult.Valid(request, LogicalPosition(request.column.toInt(), request.row.toInt()))
    }

    fun cellForPixel(
        frame: MoveCoordinateFrame,
        pointerXPx: Float,
        pointerYPxViewport: Float,
        currentScrollPx: Float,
    ): MoveCellResult = validate(
        request = requestedCell(frame, pointerXPx, pointerYPxViewport, currentScrollPx),
        columns = frame.columns,
        span = frame.span,
    )
}
