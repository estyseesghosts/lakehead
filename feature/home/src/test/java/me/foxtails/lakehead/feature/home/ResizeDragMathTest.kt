package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class ResizeDragMathTest {
    private val cellSize = 48f
    private val gap = 4f
    private val stride = cellSize + gap // 52f

    // ---- rawSpan ----

    @Test
    fun rawSpan_pointerAtOrigin_returns1x1() {
        // pointer at origin cell's top-left: span = 1x1
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_oneCellRight_returns2x1() {
        // pointer at column 1's top-left: span = 2x1
        val span = ResizeDragMath.rawSpan(
            pointerXPx = stride,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(2, 1), span)
    }

    @Test
    fun rawSpan_oneCellDown_returns1x2() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = stride,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 2), span)
    }

    @Test
    fun rawSpan_diagonalOneCell_returns2x2() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = stride,
            pointerYPx = stride,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    @Test
    fun rawSpan_subCellJitter_returns1x1() {
        // pointer still within the origin cell
        val span = ResizeDragMath.rawSpan(
            pointerXPx = cellSize - 1f,
            pointerYPx = cellSize - 1f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_nonZeroOrigin_offsetsCorrectly() {
        // origin at column=2, row=3; pointer at column=3, row=4 → span 2x2
        val origin = LogicalPosition(2, 3)
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 2 * stride + stride,  // column 3
            pointerYPx = 3 * stride + stride,  // row 4
            originPosition = origin,
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    @Test
    fun rawSpan_pointerBeforeOrigin_returns1x1() {
        // pointer before origin (negative delta) → clamped to 1x1
        val span = ResizeDragMath.rawSpan(
            pointerXPx = -10f,
            pointerYPx = -20f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_zeroCellSize_returns1x1() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 100f,
            pointerYPx = 100f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = 0f,
            columnGapPx = 0f,
            rowGapPx = 0f,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_threeCellsRight_returns4x1() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 3 * stride,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(4, 1), span)
    }

    // ---- RenderableSpans.quantize ----

    @Test
    fun quantize_exactMatch_returnsSame() {
        assertEquals(LogicalSpan(1, 1), RenderableSpans.quantize(LogicalSpan(1, 1)))
        assertEquals(LogicalSpan(2, 1), RenderableSpans.quantize(LogicalSpan(2, 1)))
        assertEquals(LogicalSpan(1, 2), RenderableSpans.quantize(LogicalSpan(1, 2)))
        assertEquals(LogicalSpan(2, 2), RenderableSpans.quantize(LogicalSpan(2, 2)))
        assertEquals(LogicalSpan(4, 2), RenderableSpans.quantize(LogicalSpan(4, 2)))
    }

    @Test
    fun quantize_3x1_nearestIs2x1() {
        // distance to 2x1 = 1; distance to 4x2 = 1+1=2; nearest is 2x1
        assertEquals(LogicalSpan(2, 1), RenderableSpans.quantize(LogicalSpan(3, 1)))
    }

    @Test
    fun quantize_3x2_nearestIs2x2() {
        // distance to 2x2 = 1; distance to 4x2 = 1; tie → earlier entry (2x2) wins
        assertEquals(LogicalSpan(2, 2), RenderableSpans.quantize(LogicalSpan(3, 2)))
    }

    @Test
    fun quantize_1x1_clampedFromBelow() {
        // Anything below smallest → smallest
        assertEquals(LogicalSpan(1, 1), RenderableSpans.quantize(LogicalSpan(1, 1)))
    }

    @Test
    fun quantize_5x3_nearestIs4x2() {
        // distance to 4x2 = 1+1=2; distance to 2x2 = 3+1=4
        assertEquals(LogicalSpan(4, 2), RenderableSpans.quantize(LogicalSpan(5, 3)))
    }
}