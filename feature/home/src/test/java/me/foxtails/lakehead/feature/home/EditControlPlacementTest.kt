package me.foxtails.lakehead.feature.home

import androidx.compose.ui.unit.IntOffset
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class EditControlPlacementTest {
    @Test
    fun smallSpans_pinTopRight() {
        assertEquals(RemoveControlPlacement.TOP_RIGHT, removeControlPlacement(1, 1))
        assertEquals(RemoveControlPlacement.TOP_RIGHT, removeControlPlacement(1, 2))
        assertEquals(RemoveControlPlacement.TOP_RIGHT, removeControlPlacement(2, 1))
    }

    @Test
    fun boundaryAndLarger_centered() {
        // 2x2 is centered (the corner→center flip boundary); anything with a
        // side above 2 is centered as well.
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(2, 2))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(3, 1))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(1, 3))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(3, 2))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(2, 3))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(4, 2))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(3, 3))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(6, 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroColumns_throws() {
        removeControlPlacement(0, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroRows_throws() {
        removeControlPlacement(1, 0)
    }

    @Test
    fun topRight_matchesCornerContract() {
        // TOP_RIGHT placement is exactly the existing corner contract: control
        // center pinned on (right, top).
        val frame = SignedPixelFrame(left = 0, top = 200, widthPx = 100, heightPx = 100)
        assertEquals(
            cornerControlOffset(frame, 48, ControlCorner.TOP_RIGHT),
            removeControlOffset(frame, 48, RemoveControlPlacement.TOP_RIGHT),
        )
        assertEquals(IntOffset(100 - 24, 200 - 24), removeControlOffset(frame, 48, RemoveControlPlacement.TOP_RIGHT))
    }

    @Test
    fun center_midAlignsControlInFootprint() {
        val frame = SignedPixelFrame(left = 10, top = 20, widthPx = 200, heightPx = 200)
        // (200 - 48) / 2 = 76 inside each axis.
        assertEquals(IntOffset(10 + 76, 20 + 76), removeControlOffset(frame, 48, RemoveControlPlacement.CENTER))
    }

    @Test
    fun center_pixelRectOverload_midAligns() {
        val tile = PixelRect(left = 0, top = 0, right = 200, bottom = 300)
        assertEquals(
            IntOffset((200 - 48) / 2, (300 - 48) / 2),
            removeControlOffset(tile, 48, RemoveControlPlacement.CENTER),
        )
        assertEquals(
            cornerControlOffset(tile, 48, ControlCorner.TOP_RIGHT),
            removeControlOffset(tile, 48, RemoveControlPlacement.TOP_RIGHT),
        )
    }

    @Test
    fun center_preservesSignedTop() {
        // Signed frame above the row origin stays rebased: the centered control
        // tracks the negative top instead of clamping to zero.
        val frame = SignedPixelFrame(left = 0, top = -40, widthPx = 100, heightPx = 100)
        assertEquals(IntOffset(26, -14), removeControlOffset(frame, 48, RemoveControlPlacement.CENTER))
    }

    @Test
    fun offset_tracksMove_sameSpanShiftedOrigin() {
        // A move that shifts the tile 50px right moves both placements by 50px:
        // the affordance follows the live frame.
        val before = SignedPixelFrame(left = 0, top = 0, widthPx = 100, heightPx = 100)
        val after = SignedPixelFrame(left = 50, top = 0, widthPx = 100, heightPx = 100)
        assertEquals(
            50,
            removeControlOffset(after, 48, RemoveControlPlacement.TOP_RIGHT).x -
                removeControlOffset(before, 48, RemoveControlPlacement.TOP_RIGHT).x,
        )
        assertEquals(
            50,
            removeControlOffset(after, 48, RemoveControlPlacement.CENTER).x -
                removeControlOffset(before, 48, RemoveControlPlacement.CENTER).x,
        )
    }

    @Test
    fun placement_flipsAtResizeBoundary() {
        // A resize crossing 1x1→2x2 re-anchors the affordance: the grid drives
        // this from the live preview span, so the button flips mid-drag.
        assertEquals(RemoveControlPlacement.TOP_RIGHT, removeControlPlacement(1, 1))
        assertEquals(RemoveControlPlacement.CENTER, removeControlPlacement(2, 2))
        val tile1x1 = PixelRect(left = 0, top = 0, right = 100, bottom = 100)
        val tile2x2 = PixelRect(left = 0, top = 0, right = 200, bottom = 200)
        val cornered = removeControlOffset(tile1x1, 48, removeControlPlacement(1, 1))
        assertEquals(IntOffset(100 - 24, 0 - 24), cornered)
        val centered = removeControlOffset(tile2x2, 48, removeControlPlacement(2, 2))
        assertEquals(IntOffset((200 - 48) / 2, (200 - 48) / 2), centered)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveControl_throws() {
        removeControlOffset(
            SignedPixelFrame(left = 0, top = 0, widthPx = 100, heightPx = 100),
            0,
            RemoveControlPlacement.CENTER,
        )
    }
}
