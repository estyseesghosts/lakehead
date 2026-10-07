package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class GridVerticalLayoutTest {
    @Test
    fun measuresDistantRowsAcrossLargeSparseGaps() {
        val placements = listOf(
            placement("near", row = 2),
            placement("distant", row = 1_000_000),
        )

        assertEquals(
            GridVerticalExtent(
                occupiedBottomRowExclusive = 1_000_001L,
                totalScrollableRows = 1_000_001L,
                occupiedMinRow = 2L,
                occupiedMaxRowExclusive = 1_000_001L,
                occupiedHeightRows = 1_000_001L,
            ),
            GridVerticalLayout.measure(placements, viewportRows = 8L, workingMarginRows = 3L),
        )
    }

    @Test
    fun allowsRowsFarBeyondAnArbitrarySmallLimit() {
        val distantRow = Int.MAX_VALUE
        val result = GridVerticalLayout.measure(
            listOf(placement("far", row = distantRow)),
            viewportRows = 1L,
            workingMarginRows = 0L,
        )

        assertEquals(distantRow.toLong() + 1L, result.occupiedBottomRowExclusive)
        assertEquals(distantRow.toLong() + 1L, result.totalScrollableRows)
    }

    @Test
    fun rowZeroIsThePermanentLogicalTopForSparseContent() {
        val result = GridVerticalLayout.measure(
            listOf(placement("row-five", row = 5)),
            viewportRows = 1L,
            workingMarginRows = 0L,
        )

        assertEquals(6L, result.totalScrollableRows)
        assertEquals(6L, result.occupiedHeightRows)
    }

    @Test
    fun contentRowsIncludeTheFullSpanFromAbsoluteRowZero() {
        val result = GridVerticalLayout.measure(
            listOf(placement("row-five-span-two", row = 5, spanRows = 2)),
            viewportRows = 1L,
            workingMarginRows = 0L,
        )

        assertEquals(7L, result.totalScrollableRows)
        assertEquals(7L, result.occupiedHeightRows)
    }

    @Test
    fun emptyLayoutHasZeroIntrinsicHeightAndDoesNotUseViewportOrMargin() {
        assertEquals(
            GridVerticalExtent(0L, 0L),
            GridVerticalLayout.measure(
                emptyList<GridPlacement<String>>(),
                viewportRows = 12L,
                workingMarginRows = 0L,
            ),
        )
        assertEquals(
            GridVerticalExtent(4L, 4L, 3L, 4L, 4L),
            GridVerticalLayout.measure(
                listOf(placement("item", row = 3)),
                viewportRows = 4L,
                workingMarginRows = 3L,
            ),
        )
        assertEquals(
            GridVerticalExtent(4L, 4L, 3L, 4L, 4L),
            GridVerticalLayout.measure(
                listOf(placement("item", row = 3)),
                viewportRows = 20L,
                workingMarginRows = 3L,
            ),
        )
    }

    @Test
    fun placementSpanSetsExclusiveBottomBoundary() {
        val result = GridVerticalLayout.measure(
            listOf(placement("tall", row = 7, spanRows = 4)),
            viewportRows = 2L,
            workingMarginRows = 1L,
        )

        assertEquals(GridVerticalExtent(11L, 11L, 7L, 11L, 11L), result)
    }

    @Test
    fun maximumOccupiedBottomIsDeterministicAndRepeatedCallsMatch() {
        val placements = listOf(
            placement("highest-first", row = 15, spanRows = 2),
            placement("lower", row = 4, spanRows = 3),
            placement("highest-later", row = 12, spanRows = 5),
        )

        val first = GridVerticalLayout.measure(placements, viewportRows = 5L, workingMarginRows = 2L)
        val second = GridVerticalLayout.measure(placements, viewportRows = 5L, workingMarginRows = 2L)

        assertEquals(GridVerticalExtent(17L, 17L, 4L, 17L, 17L), first)
        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun measurementDoesNotMutateOrReorderInputPlacements() {
        val first = placement("first", row = 9)
        val second = placement("second", row = 1)
        val input = mutableListOf(first, second)
        val before = input.toList()

        GridVerticalLayout.measure(input, viewportRows = 3L, workingMarginRows = 1L)

        assertEquals(before, input)
    }

    @Test
    fun rejectsInvalidViewportAndWorkingMargin() {
        assertIllegalArgument {
            GridVerticalLayout.measure(
                emptyList<GridPlacement<String>>(),
                viewportRows = 0L,
                workingMarginRows = 0L,
            )
        }
        assertIllegalArgument {
            GridVerticalLayout.measure(
                emptyList<GridPlacement<String>>(),
                viewportRows = -1L,
                workingMarginRows = 0L,
            )
        }
        assertIllegalArgument {
            GridVerticalLayout.measure(
                emptyList<GridPlacement<String>>(),
                viewportRows = 1L,
                workingMarginRows = -1L,
            )
        }
    }

    @Test
    fun verticalMeasurementIgnoresColumnsAndNeverRejectsWideGridPlacements() {
        val result = GridVerticalLayout.measure(
            listOf(placement("wide-grid-item", row = 7, spanRows = 3, column = 5)),
            viewportRows = 1L,
            workingMarginRows = 0L,
        )

        assertEquals(GridVerticalExtent(10L, 10L, 7L, 10L, 10L), result)
    }

    private fun placement(id: String, row: Int, spanRows: Int = 1, column: Int = 0) =
        GridPlacement(id, GridPosition(column = column, row = row), GridSpan(columns = 1, rows = spanRows))

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

}
