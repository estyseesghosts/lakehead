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
            GridVerticalExtent(1_000_001L, 1_000_004L),
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
    fun emptyLayoutUsesViewportAndExactWorkingMarginMath() {
        assertEquals(
            GridVerticalExtent(0L, 12L),
            GridVerticalLayout.measure(
                emptyList<GridPlacement<String>>(),
                viewportRows = 12L,
                workingMarginRows = 0L,
            ),
        )
        assertEquals(
            GridVerticalExtent(4L, 7L),
            GridVerticalLayout.measure(
                listOf(placement("item", row = 3)),
                viewportRows = 4L,
                workingMarginRows = 3L,
            ),
        )
        assertEquals(
            GridVerticalExtent(4L, 20L),
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

        assertEquals(GridVerticalExtent(11L, 12L), result)
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

        assertEquals(GridVerticalExtent(17L, 19L), first)
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
    fun rejectsExtentThatCannotBeRepresentedAsLong() {
        assertArithmeticException {
            GridVerticalLayout.measure(
                listOf(placement("item", row = Int.MAX_VALUE)),
                viewportRows = 1L,
                workingMarginRows = Long.MAX_VALUE,
            )
        }
    }

    private fun placement(id: String, row: Int, spanRows: Int = 1) =
        GridPlacement(id, GridPosition(column = 0, row = row), GridSpan(columns = 1, rows = spanRows))

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    private fun assertArithmeticException(block: () -> Unit) {
        try {
            block()
        } catch (_: ArithmeticException) {
            return
        }
        throw AssertionError("expected ArithmeticException")
    }
}
