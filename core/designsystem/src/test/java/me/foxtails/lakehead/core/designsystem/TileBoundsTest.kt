package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TileBoundsTest {

    private val geometry = SixCellGeometry(cellSizePx = 10, columnGapPx = 2, rowGapPx = 3)

    @Test
    fun oneByOneAtOriginHasExclusiveCellBounds() {
        assertEquals(
            PixelRect(left = 0, top = 0, right = 10, bottom = 10),
            TileBounds.bounds(GridPosition(0, 0), GridSpan(1, 1), geometry),
        )
    }

    @Test
    fun positionAndOriginBothOffsetBounds() {
        assertEquals(
            PixelRect(left = 41, top = 30, right = 51, bottom = 40),
            TileBounds.bounds(
                position = GridPosition(3, 2),
                span = GridSpan(1, 1),
                geometry = geometry,
                originX = 5,
                originY = 4,
            ),
        )
    }

    @Test
    fun twoByTwoExtentIncludesOnlyInternalGaps() {
        assertEquals(
            PixelRect(left = 0, top = 0, right = 22, bottom = 23),
            TileBounds.bounds(GridPosition(0, 0), GridSpan(2, 2), geometry),
        )
    }

    @Test
    fun fourByTwoBoundsIncludeHorizontalAndVerticalGaps() {
        assertEquals(
            PixelRect(left = 0, top = 0, right = 46, bottom = 23),
            TileBounds.bounds(GridPosition(0, 0), GridSpan(4, 2), geometry),
        )
    }

    @Test
    fun boundsAreExclusiveAndExposePositiveSize() {
        val bounds = TileBounds.bounds(GridPosition(1, 1), GridSpan(1, 1), geometry)

        assertEquals(12, bounds.left)
        assertEquals(13, bounds.top)
        assertEquals(22, bounds.right)
        assertEquals(23, bounds.bottom)
        assertEquals(10, bounds.width)
        assertEquals(10, bounds.height)
    }

    @Test
    fun negativeOriginsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.bounds(GridPosition(0, 0), GridSpan(1, 1), geometry, originX = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.bounds(GridPosition(0, 0), GridSpan(1, 1), geometry, originY = -1)
        }
    }

    @Test
    fun boundsRejectCoordinateOverflow() {
        val largeCell = SixCellGeometry(cellSizePx = Int.MAX_VALUE, columnGapPx = 0, rowGapPx = 0)

        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.bounds(GridPosition(1, 0), GridSpan(1, 1), largeCell)
        }
    }

    @Test
    fun pixelRectRejectsNegativeOrZeroSizeCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { PixelRect(-1, 0, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PixelRect(0, -1, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PixelRect(1, 0, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PixelRect(0, 1, 1, 1) }
    }

    @Test
    fun zeroDimensionsAreRejectedByGridSpan() {
        assertThrows(IllegalArgumentException::class.java) { GridSpan(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { GridSpan(1, 0) }
    }

    @Test
    fun genericPositiveDimensionsRemainMeasurable() {
        val span = GridSpan(3, 3)

        assertEquals(
            PixelRect(left = 0, top = 0, right = 34, bottom = 36),
            TileBounds.bounds(GridPosition(0, 0), span, geometry),
        )
    }
}
