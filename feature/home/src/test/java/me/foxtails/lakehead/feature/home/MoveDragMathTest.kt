package me.foxtails.lakehead.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveDragMathTest {
    private val cellSize = 48f
    private val gap = 4f
    private val stride = cellSize + gap // 52f

    @Test
    fun originCell_atPixelZero_returnsPositionZero() {
        val pos = MoveDragMath.cellForPixel(0f, 0f, cellSize, gap, gap, 0L)
        assertEquals(0, pos.column)
        assertEquals(0, pos.row)
    }

    @Test
    fun exactStridePixel_mapsToColumnOne() {
        // pixel 52 → 52/52 = 1.0 → column 1
        val pos = MoveDragMath.cellForPixel(stride, 0f, cellSize, gap, gap, 0L)
        assertEquals(1, pos.column)
        assertEquals(0, pos.row)
    }

    @Test
    fun gapPixelBeforeStride_mapsToColumnZero() {
        // pixel 50 → 50/52 = 0.96 → floors to 0
        val pos = MoveDragMath.cellForPixel(50f, 0f, cellSize, gap, gap, 0L)
        assertEquals(0, pos.column)
    }

    @Test
    fun farPixel_mapsToHighRow_notClamped() {
        // yPx = 5200 → 5200/52 = 100 → row 100 (GridRules validates bounds)
        val pos = MoveDragMath.cellForPixel(0f, 5200f, cellSize, gap, gap, 0L)
        assertEquals(0, pos.column)
        assertEquals(100, pos.row)
    }

    @Test
    fun negativePixel_coercedToZero() {
        val pos = MoveDragMath.cellForPixel(-10f, -20f, cellSize, gap, gap, 0L)
        assertEquals(0, pos.column)
        assertEquals(0, pos.row)
    }

    @Test
    fun windowFirstRow_offsetsRowCalculation() {
        // windowFirstRow = 5, yPx = 0 → row = 5
        val pos = MoveDragMath.cellForPixel(0f, 0f, cellSize, gap, gap, 5L)
        assertEquals(0, pos.column)
        assertEquals(5, pos.row)
    }

    @Test
    fun windowFirstRowWithPixelOffset() {
        // windowFirstRow = 3, yPx = 104 → 104/52 = 2 → row = 3 + 2 = 5
        val pos = MoveDragMath.cellForPixel(0f, 104f, cellSize, gap, gap, 3L)
        assertEquals(0, pos.column)
        assertEquals(5, pos.row)
    }

    @Test
    fun zeroCellSize_returnsZero() {
        // cellSize=0 means cellWidth=0 → column=0 (guard branch)
        val pos = MoveDragMath.cellForPixel(100f, 100f, 0f, 0f, 0f, 0L)
        assertEquals(0, pos.column)
        assertEquals(0, pos.row)
    }

    @Test
    fun multiColumnMapping() {
        // pixel 156 → 156/52 = 3.0 → column 3
        val pos = MoveDragMath.cellForPixel(156f, 0f, cellSize, gap, gap, 0L)
        assertEquals(3, pos.column)
    }

    @Test
    fun nonZeroOriginYPx_subtractsRestBeforeRowDivision() {
        // Signed frame: top(row) = originYPx + (row - rowOrigin) * stride.
        // rowOrigin = 5, rest = 104f (2 strides): row 5's top is y=104.
        // Pointer at the row top must map back to row 5; without subtracting
        // the rest it would read as 5 + 104/52 = 7.
        val atTop = MoveDragMath.cellForPixel(0f, 104f, cellSize, gap, gap, 5L, originYPxPx = 104f)
        assertEquals(5, atTop.row)
    }

    @Test
    fun nonZeroOriginYPx_oneStrideBelowTop_mapsToNextRow() {
        // Same origin: one stride below the row-5 top (y=156) → row 6.
        // Naive (no rest subtraction) would give 5 + 156/52 = 8.
        val next = MoveDragMath.cellForPixel(0f, 156f, cellSize, gap, gap, 5L, originYPxPx = 104f)
        assertEquals(6, next.row)
    }

    @Test
    fun nonZeroRowOriginWithRest_roundTripsSignedFrame() {
        // rowOrigin = 2, rest = 26f: row 4's signed top = 26 + (4-2)*52 = 130.
        // Pointer at y=130 must map to row 4.
        val pos = MoveDragMath.cellForPixel(0f, 130f, cellSize, gap, gap, 2L, originYPxPx = 26f)
        assertEquals(4, pos.row)
    }

    @Test
    fun nonZeroOriginX_offsetsColumnMapping() {
        // Centered grid: column 0's left edge at originX = 10.
        // Pointer at the origin maps to column 0; one stride right maps to 1.
        val atOrigin = MoveDragMath.cellForPixel(10f, 0f, cellSize, gap, gap, 0L, originXPx = 10f)
        assertEquals(0, atOrigin.column)
        val oneStride = MoveDragMath.cellForPixel(
            10f + stride, 0f, cellSize, gap, gap, 0L, originXPx = 10f,
        )
        assertEquals(1, oneStride.column)
    }

    @Test
    fun originXBelowOrigin_coercedToZero() {
        // Pointer left of the centered origin is negative in grid coords → coerced.
        val pos = MoveDragMath.cellForPixel(4f, 0f, cellSize, gap, gap, 0L, originXPx = 10f)
        assertEquals(0, pos.column)
    }

    @Test
    fun gapStraddle_staysInLeadingCellUntilStride() {
        // Stride 52: mid-gap (x=50) still floors to column 0; exact stride flips to 1.
        val midGap = MoveDragMath.cellForPixel(50f, 0f, cellSize, gap, gap, 0L)
        assertEquals(0, midGap.column)
        val atStride = MoveDragMath.cellForPixel(52f, 0f, cellSize, gap, gap, 0L)
        assertEquals(1, atStride.column)
        // Same rule holds with a centered origin: gap pixel before the next
        // stride stays in the leading column.
        val centeredGap = MoveDragMath.cellForPixel(
            10f + 50f, 0f, cellSize, gap, gap, 0L, originXPx = 10f,
        )
        assertEquals(0, centeredGap.column)
    }

    @Test
    fun largeRowWithRest_offsetsBeforeDivision() {
        // Large window origin plus rest: row = 1000 + ((5200 - 260) / 52).
        // (5200 - 260) = 4940 → 4940 / 52 = 95 → row 1095.
        // Without subtracting the rest it would read as 1000 + 100 = 1100.
        val pos = MoveDragMath.cellForPixel(
            0f, 5200f, cellSize, gap, gap, 1000L, originYPxPx = 260f,
        )
        assertEquals(1095, pos.row)
    }
}