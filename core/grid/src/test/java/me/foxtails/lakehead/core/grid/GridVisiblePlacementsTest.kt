package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Test

class GridVisiblePlacementsTest {
    @Test
    fun selectsRowsInsideTopAndBottomWindows() {
        val placements = listOf(
            placement("above", row = 0),
            placement("top", row = 1),
            placement("middle", row = 5),
            placement("bottom", row = 9),
        )

        assertEquals(
            listOf("above", "top"),
            ids(GridVisiblePlacements.select(placements, GridRowWindow(0L, 2L))),
        )
        assertEquals(
            listOf("bottom"),
            ids(GridVisiblePlacements.select(placements, GridRowWindow(9L, 10L))),
        )
    }

    @Test
    fun emptyWindowSelectsNothingUnlessOverscanExpandsIt() {
        val placements = listOf(
            placement("before", row = 3),
            placement("at", row = 4),
            placement("after", row = 5),
        )

        assertEquals(
            emptyList<String>(),
            ids(GridVisiblePlacements.select(placements, GridRowWindow(4L, 4L))),
        )
        assertEquals(
            listOf("before", "at"),
            ids(GridVisiblePlacements.select(placements, GridRowWindow(4L, 4L), overscanRows = 1L)),
        )
    }

    @Test
    fun overscanExtendsVisibleRangeAndClampsAtTop() {
        val placements = listOf(
            placement("outside", row = 0),
            placement("near", row = 2),
            placement("visible", row = 3),
            placement("far", row = 5),
        )

        assertEquals(
            listOf("outside", "near", "visible"),
            ids(GridVisiblePlacements.select(placements, GridRowWindow(1L, 2L), overscanRows = 2L)),
        )
    }

    @Test
    fun includesSpansCrossingEitherBoundaryAndUsesHalfOpenIntersection() {
        val placements = listOf(
            placement("ends-at-start", row = 1, spanRows = 2),
            placement("crosses-start", row = 2, spanRows = 2),
            placement("inside", row = 3),
            placement("crosses-end", row = 4, spanRows = 2),
            placement("starts-at-end", row = 6),
        )

        assertEquals(
            listOf("crosses-start", "inside", "crosses-end"),
            ids(GridVisiblePlacements.select(placements, GridRowWindow(3L, 5L))),
        )
    }

    @Test
    fun sparseRowsPreserveInputOrderAndStableIds() {
        val distant = placement("distant-id", row = Int.MAX_VALUE)
        val near = placement("near-id", row = 1)
        val placements = listOf(distant, near)

        val selected = GridVisiblePlacements.select(
            placements,
            GridRowWindow(Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong() + 1L),
        )

        assertEquals(listOf("distant-id"), ids(selected))
        assertEquals(listOf(distant), selected)
        assertEquals(listOf(distant, near), placements)
    }

    @Test
    fun rejectsReversedRangesAndNegativeOverscan() {
        assertIllegalArgument { GridRowWindow(2L, 1L) }
        assertIllegalArgument { GridRowWindow(0L, -1L) }
        assertIllegalArgument { GridRowWindow(-5L, -10L) }
        assertIllegalArgument { GridRowWindow(0L, 1L).withOverscan(-1L) }
        assertIllegalArgument {
            GridVisiblePlacements.select(
                emptyList<GridPlacement<String>>(),
                GridRowWindow(0L, 1L),
                overscanRows = -1L,
            )
        }
    }

    @Test
    fun overscanSaturatesInsteadOfWrappingNearLongMaximum() {
        val window = GridRowWindow(Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L)
        assertEquals(
            GridRowWindow(Long.MAX_VALUE - 5L, Long.MAX_VALUE),
            window.withOverscan(3L),
        )
        assertEquals(
            GridRowWindow(-(Long.MAX_VALUE), Long.MAX_VALUE),
            GridRowWindow(0L, Long.MAX_VALUE).withOverscan(Long.MAX_VALUE),
        )
    }

    @Test
    fun saturatedLongRangeDoesNotWrapIntoOrdinaryRows() {
        val placements = listOf(placement("ordinary", row = 0))
        val window = GridRowWindow(Long.MAX_VALUE - 1L, Long.MAX_VALUE).withOverscan(2L)

        assertEquals(GridRowWindow(Long.MAX_VALUE - 3L, Long.MAX_VALUE), window)
        assertEquals(
            emptyList<String>(),
            ids(GridVisiblePlacements.select(placements, window)),
        )
    }

    private fun placement(id: String, row: Int, spanRows: Int = 1) =
        GridPlacement(id, GridPosition(column = 0, row = row), GridSpan(columns = 1, rows = spanRows))

    private fun ids(placements: List<GridPlacement<String>>): List<String> = placements.map { it.id }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}
