package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVisiblePlacements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleWindowForTest {

    @Test
    fun scrollOffset_mapsToAbsoluteFirstRow() {
        // stride 100: scroll 300 shows absolute row 3 at the viewport top.
        val window = visibleWindowFor(
            scrollPx = 300,
            viewportHeightPx = 500,
            rowStridePx = 100L,
            overscanRows = 2L,
        )
        assertEquals(3L, window.firstRow)
    }

    @Test
    fun visibleCount_coversViewportPlusPartialRow() {
        // ceil(500/100) + 1 = 6 rows.
        val window = visibleWindowFor(
            scrollPx = 0,
            viewportHeightPx = 500,
            rowStridePx = 100L,
            overscanRows = 2L,
        )
        assertEquals(0L, window.firstRow)
        assertEquals(6L, window.lastRowExclusive - window.firstRow)
    }

    @Test
    fun partialViewportHeight_roundsUp() {
        // ceil(450/100) + 1 = 5 + 1 = 6 rows.
        val window = visibleWindowFor(
            scrollPx = 0,
            viewportHeightPx = 450,
            rowStridePx = 100L,
            overscanRows = 0L,
        )
        assertEquals(6L, window.lastRowExclusive - window.firstRow)
    }

    @Test
    fun overscan_notAppliedToReturnedWindow() {
        // Overscan belongs to the container; the window itself is the raw visible range.
        val window = visibleWindowFor(
            scrollPx = 200,
            viewportHeightPx = 100,
            rowStridePx = 100L,
            overscanRows = 5L,
        )
        assertEquals(2L, window.firstRow)
        assertEquals(2L + 2L, window.lastRowExclusive)
    }

    @Test
    fun deepScroll_selectionStillCoversVisibleAbsoluteRows() {
        // Regression: with a fixed zero content origin, the rows visible at a deep
        // scroll offset must be selected. stride 100, viewport 500, scroll 2000 →
        // absolute rows 20..25 visible; overscan 2 selects 18..28.
        val stride = 100L
        val scrollPx = 2000
        val viewportHeightPx = 500
        val overscan = 2L
        val window = visibleWindowFor(
            scrollPx = scrollPx,
            viewportHeightPx = viewportHeightPx,
            rowStridePx = stride,
            overscanRows = overscan,
        )
        val placements = (0 until 30).map { row ->
            GridPlacement(
                id = "tile-$row",
                position = GridPosition(0, row),
                span = GridSpan(1, 1),
            )
        }
        val selectedRows = GridVisiblePlacements.select(
            placements = placements,
            visibleRows = window,
            overscanRows = overscan,
        ).map { it.position.row }.toSet()
        val firstVisible = scrollPx.toLong() / stride
        val lastVisible = (scrollPx.toLong() + viewportHeightPx) / stride
        for (row in firstVisible..lastVisible) {
            assertTrue("absolute row $row must be selected at scroll $scrollPx", selectedRows.contains(row.toInt()))
        }
    }

    @Test
    fun hugeInputs_saturateInsteadOfWrapping() {
        val window = visibleWindowFor(
            scrollPx = Int.MAX_VALUE,
            viewportHeightPx = Int.MAX_VALUE,
            rowStridePx = 1L,
            overscanRows = 1L,
        )
        assertTrue(window.firstRow >= 0L)
        assertTrue(window.lastRowExclusive >= window.firstRow)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeScroll_throws() {
        visibleWindowFor(scrollPx = -1, viewportHeightPx = 100, rowStridePx = 100L, overscanRows = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveStride_throws() {
        visibleWindowFor(scrollPx = 0, viewportHeightPx = 100, rowStridePx = 0L, overscanRows = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeOverscan_throws() {
        visibleWindowFor(scrollPx = 0, viewportHeightPx = 100, rowStridePx = 100L, overscanRows = -1L)
    }
}
