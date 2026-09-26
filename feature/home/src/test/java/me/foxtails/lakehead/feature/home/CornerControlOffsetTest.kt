package me.foxtails.lakehead.feature.home

import androidx.compose.ui.unit.IntOffset
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class CornerControlOffsetTest {
    @Test
    fun topRight_pinsCenter_forSmallAndWideTiles() {
        assertEquals(
            IntOffset(100 - 24, 200 - 24),
            cornerControlOffset(PixelRect(0, 200, 100, 300), 48, ControlCorner.TOP_RIGHT),
        )
        assertEquals(
            IntOffset(500 - 24, 200 - 24),
            cornerControlOffset(PixelRect(100, 200, 500, 400), 48, ControlCorner.TOP_RIGHT),
        )
    }

    @Test
    fun bottomRight_pinsCenter_forSmallAndTallTiles() {
        assertEquals(
            IntOffset(100 - 24, 100 - 24),
            cornerControlOffset(PixelRect(0, 0, 100, 100), 48, ControlCorner.BOTTOM_RIGHT),
        )
        assertEquals(
            IntOffset(200 - 24, 500 - 24),
            cornerControlOffset(PixelRect(0, 0, 200, 500), 48, ControlCorner.BOTTOM_RIGHT),
        )
    }

    @Test
    fun corners_preserveSignedOrigins_andReachEdges() {
        val bounds = SignedPixelFrame(0, -40, 100, 100)
        assertEquals(IntOffset(76, -64), cornerControlOffset(bounds, 48, ControlCorner.TOP_RIGHT))
        assertEquals(IntOffset(76, 36), cornerControlOffset(bounds, 48, ControlCorner.BOTTOM_RIGHT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveControl_throws() {
        cornerControlOffset(PixelRect(0, 0, 100, 100), 0, ControlCorner.TOP_RIGHT)
    }
}
