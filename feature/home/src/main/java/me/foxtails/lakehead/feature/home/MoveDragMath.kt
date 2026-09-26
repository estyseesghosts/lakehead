package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition

/**
 * Pixel-to-cell conversion for move-drag gestures.
 * Pure math, framework-free: takes raw Float values.
 */
object MoveDragMath {

    /**
     * Convert a pixel offset to a logical cell position.
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
     * @return the logical cell position (may be out-of-bounds; GridRules will validate)
     */
    fun cellForPixel(
        xPx: Float,
        yPx: Float,
        cellSizePx: Float,
        columnGapPx: Float,
        rowGapPx: Float,
        windowFirstRow: Long,
        originXPx: Float = 0f,
        originYPxPx: Float = 0f,
    ): LogicalPosition {
        val cellWidth = cellSizePx + columnGapPx
        val cellHeight = cellSizePx + rowGapPx
        val column = if (cellWidth > 0f) ((xPx - originXPx) / cellWidth).toInt().coerceAtLeast(0) else 0
        val row = if (cellHeight > 0f) (windowFirstRow + ((yPx - originYPxPx) / cellHeight).toLong()).toInt().coerceAtLeast(0) else 0
        return LogicalPosition(column, row)
    }
}