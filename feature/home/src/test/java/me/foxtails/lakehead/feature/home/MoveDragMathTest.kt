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
}