package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.model.LogicalPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveDragMathTest {
    private val size = 48f
    private val gap = 4f

    private fun result(
        x: Float,
        y: Float,
        columns: Int = 6,
        rows: Long = 20,
        spanColumns: Int = 1,
        spanRows: Int = 1,
        originX: Float = 0f,
        originY: Float = 0f,
        scrollX: Float = 0f,
        scrollY: Float = 0f,
        grabX: Float = 0f,
        grabY: Float = 0f,
        firstRow: Long = 0,
    ) = MoveDragMath.cellForPixel(
        x, y, size, gap, gap, firstRow, columns, rows,
        spanColumns, spanRows, originX, originY, scrollX, scrollY, grabX, grabY,
    )

    @Test
    fun validMappingIncludesCandidateAndPosition() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(3, 4), LogicalPosition(3, 4)),
            result(156f, 208f),
        )
    }

    @Test
    fun strideGapOriginScrollGrabAndDiagonalMapOnce() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(2, 4), LogicalPosition(2, 4)),
            MoveDragMath.cellForPixel(
                10f + 104f - 20f + 30f,
                26f + 104f - 52f + 40f,
                size, gap, gap, 2L, 6, 20L, 1, 1,
                10f, 26f, 20f, 52f, 30f, 40f,
            ),
        )
    }

    @Test
    fun negativeBeyondAndSpanCrossingAreRejectedWithoutPosition() {
        val expected = MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS)
        assertEquals(expected, result(-1f, 0f))
        assertEquals(expected, result(0f, 0f, rows = 2, spanRows = 3))
        assertEquals(expected, result(5 * 52f, 0f, spanColumns = 2))
        assertEquals(expected, result(0f, 20 * 52f))
    }

    @Test
    fun farRowIsValidWhenBoundsIncludeIt() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(0, 100), LogicalPosition(0, 100)),
            result(0f, 5200f, rows = 101),
        )
    }

    @Test
    fun diagonalScrolledMappingReturnsLogicalPosition() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(3, 4), LogicalPosition(3, 4)),
            result(104f, 156f, scrollX = 52f, scrollY = 52f),
        )
    }
}
