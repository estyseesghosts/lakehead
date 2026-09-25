package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Pixel drag delta → raw span conversion for resize gestures.
 *
 * During a resize drag the tile's top-left corner (origin) stays fixed and the
 * bottom-right corner follows the pointer. The raw span is computed from the
 * pointer's pixel position relative to the origin cell's top-left.
 *
 * Pure math, framework-free: takes raw Float/Int values.
 */
object ResizeDragMath {

    /**
     * Compute the raw (un-quantized) span from a drag pointer position.
     *
     * @param pointerXPx horizontal pixel position of the drag pointer (content coordinates)
     * @param pointerYPx vertical pixel position of the drag pointer (content coordinates)
     * @param originPosition the tile's fixed top-left logical cell
     * @param cellSizePx cell size in pixels
     * @param columnGapPx gap between columns in pixels
     * @param rowGapPx gap between rows in pixels
     * @return raw logical span (may be fractional-cell; caller quantizes). Minimum 1×1.
     */
    fun rawSpan(
        pointerXPx: Float,
        pointerYPx: Float,
        originPosition: LogicalPosition,
        cellSizePx: Float,
        columnGapPx: Float,
        rowGapPx: Float,
    ): LogicalSpan {
        val cellWidth = cellSizePx + columnGapPx
        val cellHeight = cellSizePx + rowGapPx

        // Origin cell's top-left pixel coordinate
        val originXPx = originPosition.column * cellWidth
        val originYPx = originPosition.row * cellHeight

        // Pointer offset from origin cell's top-left
        val dxPx = pointerXPx - originXPx
        val dyPx = pointerYPx - originYPx

        // Raw span = ceil(offset / stride), minimum 1
        val rawColumns = if (cellWidth > 0f) {
            ((dxPx / cellWidth) + 1).toInt().coerceAtLeast(1)
        } else {
            1
        }
        val rawRows = if (cellHeight > 0f) {
            ((dyPx / cellHeight) + 1).toInt().coerceAtLeast(1)
        } else {
            1
        }

        return LogicalSpan(rawColumns, rawRows)
    }
}