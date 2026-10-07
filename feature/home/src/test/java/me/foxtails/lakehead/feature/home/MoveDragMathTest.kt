package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.LogicalPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveDragMathTest {
    private val size = 48f
    private val gap = 4f
    private val stride = size + gap

    private fun metrics(
        originX: Float = 0f,
        rest: Float = 0f,
        cellHeight: Float = size,
        columns: Int = 6,
    ) = HomeGridMetrics(columns, size, cellHeight, gap, gap, originX, rest)

    private fun frame(
        span: GridSpan = GridSpan(1, 1),
        metrics: HomeGridMetrics = metrics(),
        grabX: Float = 0f,
        grabY: Float = 0f,
    ) = MoveCoordinateFrame(metrics, span, grabX, grabY)

    private fun map(frame: MoveCoordinateFrame, x: Float, y: Float, scroll: Float = 0f) =
        MoveDragMath.cellForPixel(frame, x, y, scroll)

    private fun valid(column: Int, row: Int) = MoveCellResult.Valid(
        RequestedCell(column.toLong(), row.toLong()),
        LogicalPosition(column, row),
    )

    private fun outOfBounds(column: Long, row: Long) = MoveCellResult.Rejected(
        GridRules.Rejection.OUT_OF_BOUNDS,
        RequestedCell(column, row),
    )

    @Test
    fun validMappingIncludesRequestAndPosition() {
        assertEquals(valid(3, 4), map(frame(), 156f, 208f))
    }

    @Test
    fun strideGapOriginRestScrollAndGrabAreAppliedOnce() {
        // Tile top-left should land on cell (2, 4): content (10 + 2*52, 26 + 4*52).
        // Pointer holds the tile 30/40px inside it, with 52px of scroll applied.
        val f = frame(metrics = metrics(originX = 10f, rest = 26f), grabX = 30f, grabY = 40f)
        assertEquals(valid(2, 4), map(f, 114f + 30f, 234f + 40f - 52f, scroll = 52f))
    }

    @Test
    fun requestAboveRowZeroIsNegativeAndRejectedNotClampedToRowZero() {
        assertEquals(outOfBounds(0, -1), map(frame(), 0f, -0.6f * stride))
        assertEquals(outOfBounds(0, -3), map(frame(), 0f, -3 * stride))
    }

    @Test
    fun requestLeftOfGridIsNegativeAndRejected() {
        assertEquals(outOfBounds(-1, 0), map(frame(), -0.6f * stride, 0f))
    }

    @Test
    fun snapIsToTheNearestCellAndSymmetricAroundACellBoundary() {
        // Less than half a stride past a cell in any direction keeps that cell;
        // more than half switches. 1px up/left must not jump a whole cell.
        val row3 = 3 * stride
        assertEquals(valid(2, 3), map(frame(), 2 * stride - 1f, row3 - 1f))
        assertEquals(valid(2, 3), map(frame(), 2 * stride + 1f, row3 + 1f))
        assertEquals(valid(2, 3), map(frame(), 2 * stride - 0.4f * stride, row3 - 0.4f * stride))
        assertEquals(valid(2, 3), map(frame(), 2 * stride + 0.4f * stride, row3 + 0.4f * stride))
        assertEquals(valid(1, 2), map(frame(), 2 * stride - 0.6f * stride, row3 - 0.6f * stride))
        assertEquals(valid(3, 4), map(frame(), 2 * stride + 0.6f * stride, row3 + 0.6f * stride))
    }

    @Test
    fun hitTestingKeepsTheFloorMapping() {
        // The nearest rule is for moves only; a touch point still belongs to the cell it is inside.
        val m = metrics()
        assertEquals(RequestedCell(2, 3), m.requestedTopLeftCell(2 * stride + 0.9f * stride, 3 * stride + 0.9f * stride))
        assertEquals(RequestedCell(3, 4), m.nearestTopLeftCell(2 * stride + 0.9f * stride, 3 * stride + 0.9f * stride))
    }

    @Test
    fun grabOffsetAboveTheTileTopCanPushTheRequestAboveRowZero() {
        // Pointer on row 0 but grabbed 60px down the tile: tile top is above the grid.
        assertEquals(outOfBounds(0, -2), map(frame(grabY = 60f + stride), 0f, 10f))
    }

    @Test
    fun pastRightEdgeIsRejectedNotSnappedToLastLegalColumn() {
        assertEquals(outOfBounds(6, 0), map(frame(), 6 * stride, 0f))
        assertEquals(outOfBounds(5, 0), map(frame(span = GridSpan(2, 1)), 5 * stride, 0f))
        assertEquals(
            outOfBounds(5, 0),
            map(frame(span = GridSpan(4, 2)), 5 * stride, 0f),
        )
    }

    @Test
    fun lastLegalColumnForTheSpanIsStillValid() {
        assertEquals(valid(2, 0), map(frame(span = GridSpan(4, 2)), 2 * stride, 0f))
    }

    @Test
    fun rowBeyondIntRepresentationIsRejected() {
        val result = map(frame(), 0f, Float.MAX_VALUE) as MoveCellResult.Rejected
        assertEquals(GridRules.Rejection.OUT_OF_BOUNDS, result.reason)
    }

    @Test
    fun farRowIsValidBecauseVerticalSpaceIsUnbounded() {
        assertEquals(valid(0, 100), map(frame(), 0f, 5200f))
    }

    @Test
    fun tallSpanHasNoFiniteBottomEdge() {
        assertEquals(valid(0, 19), map(frame(span = GridSpan(1, 2)), 0f, 1000f))
        assertEquals(valid(0, 0), map(frame(span = GridSpan(1, 2)), 0f, 0f))
    }

    @Test
    fun restOffsetShiftsPixelsButAddsNoRows() {
        val f = frame(metrics = metrics(rest = 40f))
        assertEquals(valid(0, 0), map(f, 0f, 40f))
        assertEquals(valid(0, 1), map(f, 0f, 40f + stride))
    }

    @Test
    fun rowMappingUsesRowStride() {
        assertEquals(valid(0, 1), map(frame(metrics = metrics(cellHeight = 76f)), 0f, 104f))
    }

    @Test
    fun unfittableSpanIsRejected() {
        assertEquals(
            GridRules.Rejection.OUT_OF_BOUNDS,
            (map(frame(span = GridSpan(7, 2)), 0f, 0f) as MoveCellResult.Rejected).reason,
        )
    }

    @Test
    fun candidateDependsOnlyOnCurrentPointerAndCurrentScroll() {
        val f = frame()
        assertEquals(valid(0, 2), map(f, 0f, 104f, scroll = 0f))
        assertEquals(valid(0, 3), map(f, 0f, 104f, scroll = stride))
        // Returning to the first scroll value returns the first answer: no accumulated state.
        assertEquals(valid(0, 2), map(f, 0f, 104f, scroll = 0f))
    }

    @Test
    fun spanIsNeverAnOutputOfTheMapping() {
        val result = map(frame(span = GridSpan(2, 2)), stride, stride) as MoveCellResult.Valid
        assertEquals(LogicalPosition(1, 1), result.position)
    }
}
