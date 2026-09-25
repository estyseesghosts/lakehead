package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridVerticalExtent
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentMinHeightPxTest {

    @Test
    fun multipleRows_countsCellsAndGaps() {
        // 4 rows × 100px cells + 3 × 10px gaps = 430.
        val height = contentMinHeightPx(
            extent = GridVerticalExtent(occupiedBottomRowExclusive = 4L, totalScrollableRows = 4L),
            cellSizePx = 100,
            rowGapPx = 10,
        )
        assertEquals(430, height)
    }

    @Test
    fun singleRow_hasNoGap() {
        val height = contentMinHeightPx(
            extent = GridVerticalExtent(occupiedBottomRowExclusive = 1L, totalScrollableRows = 1L),
            cellSizePx = 100,
            rowGapPx = 10,
        )
        assertEquals(100, height)
    }

    @Test
    fun zeroGap_sumsCellsOnly() {
        val height = contentMinHeightPx(
            extent = GridVerticalExtent(occupiedBottomRowExclusive = 3L, totalScrollableRows = 3L),
            cellSizePx = 100,
            rowGapPx = 0,
        )
        assertEquals(300, height)
    }

    @Test
    fun absoluteHeight_coversFullExtentForFixedOrigin() {
        // The canvas origin is fixed at row 0, so the absolute extent height is the
        // correct content minimum: no origin offset may be subtracted from it.
        // 30 rows × stride 110 (100 cell + 10 gap) minus the final gap = 3290.
        val height = contentMinHeightPx(
            extent = GridVerticalExtent(occupiedBottomRowExclusive = 30L, totalScrollableRows = 30L),
            cellSizePx = 100,
            rowGapPx = 10,
        )
        assertEquals(30 * 110 - 10, height)
    }

    @Test
    fun overflowingExtent_saturatesAtIntMax() {
        val height = contentMinHeightPx(
            extent = GridVerticalExtent(
                occupiedBottomRowExclusive = Long.MAX_VALUE,
                totalScrollableRows = Long.MAX_VALUE,
            ),
            cellSizePx = Int.MAX_VALUE,
            rowGapPx = Int.MAX_VALUE,
        )
        assertEquals(Int.MAX_VALUE, height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveCell_throws() {
        contentMinHeightPx(
            extent = GridVerticalExtent(occupiedBottomRowExclusive = 1L, totalScrollableRows = 1L),
            cellSizePx = 0,
            rowGapPx = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeGap_throws() {
        contentMinHeightPx(
            extent = GridVerticalExtent(occupiedBottomRowExclusive = 1L, totalScrollableRows = 1L),
            cellSizePx = 10,
            rowGapPx = -1,
        )
    }
}
