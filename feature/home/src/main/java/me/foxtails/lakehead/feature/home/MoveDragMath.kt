package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import kotlin.math.floor

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
     * @param scrollXPx horizontal content scroll in pixels. The value is added to the
     * viewport-local pointer position; defaults to 0.
     * @param scrollYPx vertical content scroll in pixels. The value is added to the
     * viewport-local pointer position; defaults to 0.
     * @param grabOffsetXPx horizontal distance from the tile origin to the pointer at
     * drag start; defaults to 0.
     * @param grabOffsetYPx vertical distance from the tile origin to the pointer at
     * drag start; defaults to 0.
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
        scrollXPx: Float = 0f,
        scrollYPx: Float = 0f,
        grabOffsetXPx: Float = 0f,
        grabOffsetYPx: Float = 0f,
    ): LogicalPosition {
        val cellWidth = cellSizePx + columnGapPx
        val cellHeight = cellSizePx + rowGapPx
        // Pointer coordinates are local to the viewport. Restore content coordinates
        // before applying the grid frame, then remove the grab point so the returned
        // cell is the dragged tile's top-left cell rather than the finger's cell.
        val contentX = xPx + scrollXPx - grabOffsetXPx
        val contentY = yPx + scrollYPx - grabOffsetYPx
        val column = if (cellWidth > 0f) ((contentX - originXPx) / cellWidth).toInt().coerceAtLeast(0) else 0
        val row = if (cellHeight > 0f) {
            val rowOffset = floor((contentY - originYPxPx) / cellHeight).toLong()
            (windowFirstRow + rowOffset).toInt().coerceAtLeast(0)
        } else {
            0
        }
        return LogicalPosition(column, row)
    }
}
