package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class CenteredOriginXTest {

    private fun geometry(
        columnCount: Int = 2,
        cellSizePx: Int = 100,
        gapPx: Int = 10,
    ): SquareCellGeometry = SquareCellGeometry(
        columnCount = columnCount,
        cellSizePx = cellSizePx,
        columnGapPx = gapPx,
        rowGapPx = gapPx,
    )

    @Test
    fun evenRemainder_splitsSlackSymmetrically() {
        // Grid width = 2 * 100 + 1 * 10 = 210; available 230 leaves 20 -> origin 10.
        val origin = centeredOriginX(availableWidthPx = 230, geometry = geometry())
        assertEquals(10, origin)
    }

    @Test
    fun oddRemainder_rightSideHeavyByOnePixel() {
        // Leftover 21 floors to origin 10, leaving 11px on the right.
        val origin = centeredOriginX(availableWidthPx = 231, geometry = geometry())
        assertEquals(10, origin)
        val gridWidth = 2 * 100 + 10
        val rightSlack = 231 - gridWidth - origin
        assertEquals(origin + 1, rightSlack)
    }

    @Test
    fun overWideGrid_clampsOriginToZero() {
        // Grid width 210 exceeds available 100; negative origin saturates to 0.
        val origin = centeredOriginX(availableWidthPx = 100, geometry = geometry())
        assertEquals(0, origin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveAvailableWidth_throws() {
        centeredOriginX(availableWidthPx = 0, geometry = geometry())
    }
}
