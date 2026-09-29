package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.LogicalPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveDragMathTest {
    private val size = 48f
    private val gap = 4f

    private fun frame(
        span: GridSpan = GridSpan(1, 1),
        baseline: Float = 0f,
        legalityRows: Long = 20,
        rowOrigin: Long = 0,
        rowStride: Float = size + gap,
    ) = MoveCoordinateFrame(
        frozenScrollBaselinePx = baseline,
        originXPx = 0f,
        originYPx = 0f,
        rowOrigin = rowOrigin,
        columnStridePx = size + gap,
        rowStridePx = rowStride,
        columns = 6,
        span = span,
        legalityRows = legalityRows,
        grabOffsetXPx = 0f,
        grabOffsetYPx = 0f,
    )

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

    @Test
    fun frozenFrameUsesLiveScrollDeltaForRow() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(0, 3), LogicalPosition(0, 3)),
            MoveDragMath.cellForPixel(frame(baseline = 0f), 0f, 104f, 52f),
        )
    }

    @Test
    fun fittingSpanClampsAtRightEdge() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(2, 0), LogicalPosition(2, 0)),
            MoveDragMath.cellForPixel(frame(span = GridSpan(4, 2)), 5 * 52f, 0f, 0f),
        )
    }

    @Test
    fun fittingSpanClampsAtBottomEdge() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(0, 2), LogicalPosition(0, 2)),
            MoveDragMath.cellForPixel(
                frame(span = GridSpan(1, 2), legalityRows = 4),
                0f,
                1000f,
                0f,
            ),
        )
    }

    @Test
    fun unfittableHeightIsOutOfBounds() {
        assertEquals(
            MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS),
            MoveDragMath.cellForPixel(
                frame(span = GridSpan(1, 2), legalityRows = 1),
                0f,
                0f,
                0f,
            ),
        )
    }

    @Test
    fun nonzeroRowOriginIsNotAddedTwice() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(0, 6), LogicalPosition(0, 6)),
            MoveDragMath.cellForPixel(
                frame(rowOrigin = 5),
                0f,
                52f,
                0f,
            ),
        )
    }

    @Test
    fun rowMappingUsesRowStride() {
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(0, 1), LogicalPosition(0, 1)),
            MoveDragMath.cellForPixel(
                frame(rowStride = 80f),
                0f,
                104f,
                0f,
            ),
        )
    }

    @Test
    fun unfittableSpanIsOutOfBounds() {
        assertEquals(
            MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS),
            MoveDragMath.cellForPixel(frame(span = GridSpan(7, 2)), 0f, 0f, 0f),
        )
    }

    @Test
    fun scrollOnlyChangeRemapsWithoutChangingFrame() {
        val frozen = frame(baseline = 0f)
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(0, 2), LogicalPosition(0, 2)),
            MoveDragMath.cellForPixel(frozen, 0f, 104f, 0f),
        )
        assertEquals(
            MoveCellResult.Valid(MoveCellCandidate(0, 3), LogicalPosition(0, 3)),
            MoveDragMath.cellForPixel(frozen, 0f, 104f, 52f),
        )
    }
}
