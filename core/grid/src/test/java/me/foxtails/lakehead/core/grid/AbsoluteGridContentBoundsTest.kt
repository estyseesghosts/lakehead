package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbsoluteGridContentBoundsTest {
    @Test
    fun leadingRowsAndFullSpanRemainContentFromRowZero() {
        for (spanRows in listOf(1, 2)) {
            val bounds = GridContentBounds.absoluteContentBoundsOf(
                listOf(tile("only", column = 2, row = 5, spanRows = spanRows)),
                columns = 6,
            )!!

            assertEquals(ContentBounds(0L, 5L + spanRows, 2L, 3L), bounds)
            assertEquals(5L + spanRows, GridContentBounds.contentHeightRows(bounds))
        }
    }

    @Test
    fun sparseBoundsUseFarthestExclusiveBottomNotCountOrOccupiedMinimum() {
        val bounds = GridContentBounds.absoluteContentBoundsOf(
            listOf(
                tile("early", column = 4, row = 5),
                tile("far", column = 1, row = 50, spanColumns = 2, spanRows = 3),
            ),
            columns = 6,
        )!!

        assertEquals(ContentBounds(0L, 53L, 1L, 5L), bounds)
    }

    @Test
    fun emptyPlacementsHaveNoBounds() {
        assertNull(GridContentBounds.absoluteContentBoundsOf(emptyList<GridPlacement<String>>(), 6))
    }

    @Test
    fun horizontalEdgeIsExclusiveAndMayTouchGridBoundary() {
        val bounds = GridContentBounds.absoluteContentBoundsOf(
            listOf(tile("wide", column = 2, row = 5, spanColumns = 4)),
            columns = 6,
        )!!

        assertEquals(6L, bounds.maxColExclusive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun horizontalOverflowIsRejected() {
        GridContentBounds.absoluteContentBoundsOf(
            listOf(tile("outside", column = 3, row = 5, spanColumns = 4)),
            columns = 6,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidColumnCountIsRejectedEvenForEmptyContent() {
        GridContentBounds.absoluteContentBoundsOf(emptyList<GridPlacement<String>>(), columns = 0)
    }

    @Test
    fun exclusiveBottomBeyondIntRangeDoesNotWrap() {
        val bounds = GridContentBounds.absoluteContentBoundsOf(
            listOf(tile("large", column = 0, row = Int.MAX_VALUE, spanRows = Int.MAX_VALUE)),
            columns = 6,
        )!!

        assertEquals(2L * Int.MAX_VALUE, bounds.maxRowExclusive)
        assertEquals(2L * Int.MAX_VALUE, GridContentBounds.contentHeightRows(bounds))
    }

    private fun tile(
        id: String,
        column: Int,
        row: Int,
        spanColumns: Int = 1,
        spanRows: Int = 1,
    ) = GridPlacement(id, GridPosition(column, row), GridSpan(spanColumns, spanRows))
}
