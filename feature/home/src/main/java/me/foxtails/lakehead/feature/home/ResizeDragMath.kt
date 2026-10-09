package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Pointer → raw span conversion for resize gestures.
 *
 * The tile's top-left stays fixed at [originPosition]; the pointer (viewport
 * position plus current scroll, applied once) determines only the requested
 * span. The position is never an output. Any span is reachable; [clampSpan]
 * only bounds it to the grid width and legality stays with core:grid. Pure
 * math over [HomeGridMetrics].
 */
object ResizeDragMath {

    /** Bounds [raw] columns to at most [columns]; rows and shape are untouched. */
    fun clampSpan(raw: LogicalSpan, columns: Int): LogicalSpan =
        if (raw.columns <= columns) raw else LogicalSpan(maxOf(1, columns), raw.rows)

    /** Raw logical span, minimum 1×1; may exceed what the product renders. */
    fun rawSpan(
        metrics: HomeGridMetrics,
        originPosition: LogicalPosition,
        pointerXPx: Float,
        pointerYPxViewport: Float,
        currentScrollXPx: Float = 0f,
        currentScrollYPx: Float = 0f,
    ): LogicalSpan = metrics.requestedSpan(
        origin = originPosition,
        contentX = pointerXPx + currentScrollXPx,
        contentY = metrics.contentY(pointerYPxViewport, currentScrollYPx),
    )
}
