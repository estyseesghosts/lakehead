package me.foxtails.lakehead.feature.home

import androidx.compose.ui.unit.IntOffset
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoveButtonOffsetTest {

    @Test
    fun singleCell_pinsCenterToTopLeftCorner() {
        val bounds = PixelRect(left = 100, top = 200, right = 300, bottom = 400)
        val offset = removeButtonOffset(
            tileBounds = bounds,
            buttonSizePx = 48,
            span = GridSpan(1, 1),
        )
        assertEquals(IntOffset(100 - 24, 200 - 24), offset)
    }

    @Test
    fun singleCell_firstRowAllowsNegativeTop() {
        val bounds = PixelRect(left = 50, top = 0, right = 150, bottom = 100)
        val offset = removeButtonOffset(
            tileBounds = bounds,
            buttonSizePx = 48,
            span = GridSpan(1, 1),
        )
        // Raw math stays exact; callers clamp for rendering.
        assertEquals(IntOffset(50 - 24, 0 - 24), offset)
    }

    @Test
    fun largerSpan_centersButtonOnTile() {
        val bounds = PixelRect(left = 100, top = 200, right = 500, bottom = 400)
        val offset = removeButtonOffset(
            tileBounds = bounds,
            buttonSizePx = 48,
            span = GridSpan(2, 1),
        )
        assertEquals(
            IntOffset(
                100 + (400 - 48) / 2,
                200 + (200 - 48) / 2,
            ),
            offset,
        )
    }

    @Test
    fun tallSpan_centersButtonOnTile() {
        val bounds = PixelRect(left = 0, top = 0, right = 200, bottom = 500)
        val offset = removeButtonOffset(
            tileBounds = bounds,
            buttonSizePx = 48,
            span = GridSpan(1, 2),
        )
        assertEquals(
            IntOffset(
                (200 - 48) / 2,
                (500 - 48) / 2,
            ),
            offset,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveButton_throws() {
        val bounds = PixelRect(left = 0, top = 0, right = 100, bottom = 100)
        removeButtonOffset(bounds, 0, GridSpan(1, 1))
    }
}
