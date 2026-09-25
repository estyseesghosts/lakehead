package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GridViewportAdaptationTest {
    @Test
    fun normalPhoneUsesSixColumnBaseline() {
        assertEquals(
            GridColumns(6),
            GridViewportAdaptation.columnsFor(viewportWidthPx = 600, minimumCellWidthPx = 100),
        )
        assertEquals(6, GridColumns.NORMAL_PHONE_REFERENCE_COUNT)
    }

    @Test
    fun narrowWidthsAdaptToOneThroughFiveColumns() {
        assertEquals(
            listOf(1, 2, 3, 4, 5).map(::GridColumns),
            (100..500 step 100).map { width ->
                GridViewportAdaptation.columnsFor(width, minimumCellWidthPx = 100)
            },
        )
    }

    @Test
    fun positiveEffectiveWidthBelowOneMinimumCellStillProducesOneColumn() {
        assertEquals(
            GridColumns(1),
            GridViewportAdaptation.columnsFor(viewportWidthPx = 99, minimumCellWidthPx = 100),
        )
    }

    @Test
    fun wideAndFoldableWidthsReturnDeterministicAdditionalColumns() {
        assertEquals(
            GridColumns(12),
            GridViewportAdaptation.columnsFor(viewportWidthPx = 1_200, minimumCellWidthPx = 100),
        )
        assertEquals(
            GridColumns(18),
            GridViewportAdaptation.columnsFor(viewportWidthPx = 1_800, minimumCellWidthPx = 100),
        )
    }

    @Test
    fun insetsAndPaddingReduceTheEffectiveWidthExactly() {
        assertEquals(
            GridColumns(7),
            GridViewportAdaptation.columnsFor(
                viewportWidthPx = 1_000,
                horizontalInsetPx = 100,
                horizontalPaddingPx = 20,
                columnGapPx = 10,
                minimumCellWidthPx = 100,
            ),
        )
    }

    @Test
    fun widthChangesAreDeterministicAndReturningToAWidthRestoresItsCount() {
        val widths = listOf(600, 1_200, 1_800, 600)
        val results = widths.map { width ->
            GridViewportAdaptation.columnsFor(width, minimumCellWidthPx = 100)
        }

        assertEquals(listOf(GridColumns(6), GridColumns(12), GridColumns(18), GridColumns(6)), results)
        assertEquals(
            results,
            widths.map { width ->
                GridViewportAdaptation.columnsFor(width, minimumCellWidthPx = 100)
            },
        )
    }

    @Test
    fun adaptationLeavesTheSourcePlacementListAndEntriesUntouched() {
        val first = GridPlacement("first", GridPosition(2, 0), GridSpan(2, 1))
        val second = GridPlacement("second", GridPosition(0, 4), GridSpan(1, 2))
        val sourcePlacements = mutableListOf(first, second)
        val originalEntries = sourcePlacements.toList()

        GridViewportAdaptation.columnsFor(1_200, minimumCellWidthPx = 100)
        GridViewportAdaptation.columnsFor(600, minimumCellWidthPx = 100)

        assertSame(first, sourcePlacements[0])
        assertSame(second, sourcePlacements[1])
        assertEquals(originalEntries, sourcePlacements)
    }

    @Test
    fun rejectsEveryInvalidMetricAndNonPositiveEffectiveContentWidth() {
        assertIllegalArgument { GridViewportAdaptation.columnsFor(0, minimumCellWidthPx = 1) }
        assertIllegalArgument { GridViewportAdaptation.columnsFor(-1, minimumCellWidthPx = 1) }
        assertIllegalArgument {
            GridViewportAdaptation.columnsFor(100, horizontalInsetPx = -1, minimumCellWidthPx = 1)
        }
        assertIllegalArgument {
            GridViewportAdaptation.columnsFor(100, horizontalPaddingPx = -1, minimumCellWidthPx = 1)
        }
        assertIllegalArgument {
            GridViewportAdaptation.columnsFor(100, columnGapPx = -1, minimumCellWidthPx = 1)
        }
        assertIllegalArgument { GridViewportAdaptation.columnsFor(100, minimumCellWidthPx = 0) }
        assertIllegalArgument { GridViewportAdaptation.columnsFor(100, minimumCellWidthPx = -1) }
        assertIllegalArgument {
            GridViewportAdaptation.columnsFor(
                viewportWidthPx = 100,
                horizontalInsetPx = 100,
                minimumCellWidthPx = 1,
            )
        }
        assertIllegalArgument {
            GridViewportAdaptation.columnsFor(
                viewportWidthPx = 100,
                horizontalPaddingPx = 50,
                minimumCellWidthPx = 1,
            )
        }
    }

    @Test
    fun calculationsRemainSafeAtIntegerBoundaries() {
        assertEquals(
            GridColumns(Int.MAX_VALUE),
            GridViewportAdaptation.columnsFor(
                viewportWidthPx = Int.MAX_VALUE,
                minimumCellWidthPx = 1,
            ),
        )
        assertEquals(
            GridColumns(1),
            GridViewportAdaptation.columnsFor(
                viewportWidthPx = Int.MAX_VALUE,
                columnGapPx = Int.MAX_VALUE,
                minimumCellWidthPx = 1,
            ),
        )
        assertIllegalArgument {
            GridViewportAdaptation.columnsFor(
                viewportWidthPx = Int.MAX_VALUE,
                horizontalPaddingPx = Int.MAX_VALUE,
                minimumCellWidthPx = 1,
            )
        }
    }

    @Test
    fun gridColumnsAcceptsEveryPositiveCountAndRejectsNonPositiveCounts() {
        assertIllegalArgument { GridColumns(0) }
        assertIllegalArgument { GridColumns(-1) }
        assertEquals(GridColumns(1), GridColumns(1))
        assertEquals(GridColumns(5), GridColumns(5))
        assertEquals(GridColumns(6), GridColumns(GridColumns.NORMAL_PHONE_REFERENCE_COUNT))
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}
