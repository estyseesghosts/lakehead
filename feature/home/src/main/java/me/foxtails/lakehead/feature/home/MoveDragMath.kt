package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition

/**
 * Pixel-to-cell conversion for move drag tracking.
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
     * @return the logical cell position (may be out-of-bounds; GridRules will validate)
     */
    fun cellForPixel(
        xPx: Float,
        yPx: Float,
        cellSizePx: Float,
        columnGapPx: Float,
        rowGapPx: Float,
        windowFirstRow: Long,
    ): LogicalPosition {
        val cellWidth = cellSizePx + columnGapPx
        val cellHeight = cellSizePx + rowGapPx
        val column = if (cellWidth > 0f) (xPx / cellWidth).toInt().coerceAtLeast(0) else 0
        val row = if (cellHeight > 0f) (windowFirstRow + (yPx / cellHeight).toLong()).toInt().coerceAtLeast(0) else 0
        return LogicalPosition(column, row)
    }
}