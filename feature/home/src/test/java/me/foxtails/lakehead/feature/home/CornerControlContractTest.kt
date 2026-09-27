package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_VISUAL_SIZE
import me.foxtails.lakehead.core.designsystem.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerControlContractTest {
    @Test
    fun controls_shareVisualAndTouchContract() {
        assertEquals(32, CORNER_CONTROL_VISUAL_SIZE)
        assertEquals(48, CORNER_CONTROL_TOUCH_SIZE)
        assertEquals(CORNER_CONTROL_TOUCH_SIZE, RESIZE_TOUCH_DP)
    }

    // ---- A2-S1: 48dp corner vs body vs remove ----

    @Test
    fun handleCorner_bodyAndRemoveRouteSeparately() {
        // 200x200 tile with the 48px touch target: the parent routes
        // [152,200] x [152,200] (inward bottom-right) to the resize arrow.
        val tile = PixelRect(left = 0, top = 0, right = 200, bottom = 200)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE

        // Arrow corner routes to the handle.
        assertTrue(isInHandle(180f, 180f, tile, touchPx))
        assertTrue(isInHandle(200f, 200f, tile, touchPx))

        // Body stays body: center, top-left and top edge never route resize.
        assertFalse(isInHandle(50f, 50f, tile, touchPx))
        assertFalse(isInHandle(10f, 10f, tile, touchPx))
        assertFalse(isInHandle(100f, 10f, tile, touchPx))

        // Remove control is top-right center-pinned: its center sits at
        // (right, top), far above the handle routing rect on a tile taller
        // than the touch target, so remove taps never route resize.
        val removeOffset = cornerControlOffset(
            tileBounds = tile,
            controlSizePx = touchPx,
            corner = ControlCorner.TOP_RIGHT,
        )
        val removeCenterX = removeOffset.x + touchPx / 2f
        val removeCenterY = removeOffset.y + touchPx / 2f
        assertEquals(200f, removeCenterX)
        assertEquals(0f, removeCenterY)
        assertFalse(isInHandle(removeCenterX, removeCenterY, tile, touchPx))
    }

    @Test
    fun removeAndHandle_anchorsTrackOppositeVerticalEdges() {
        // The remove (top-right) and resize (bottom-right) overlays pin to
        // opposite vertical edges of the same tile footprint: their top-left
        // anchors differ by exactly the tile height, so the controls stay
        // attached to their own corners during move/resize.
        val tile = PixelRect(left = 0, top = 0, right = 200, bottom = 200)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        val removeOffset = cornerControlOffset(
            tileBounds = tile,
            controlSizePx = touchPx,
            corner = ControlCorner.TOP_RIGHT,
        )
        val handleOffset = cornerControlOffset(
            tileBounds = tile,
            controlSizePx = touchPx,
            corner = ControlCorner.BOTTOM_RIGHT,
        )
        assertEquals(removeOffset.x, handleOffset.x)
        assertEquals(200, handleOffset.y - removeOffset.y)
        // The handle anchor center is inside the routing rect; the remove
        // anchor center is not.
        assertTrue(
            isInHandle(
                handleOffset.x + touchPx / 2f,
                handleOffset.y + touchPx / 2f,
                tile,
                touchPx,
            ),
        )
        assertFalse(
            isInHandle(
                removeOffset.x + touchPx / 2f,
                removeOffset.y + touchPx / 2f,
                tile,
                touchPx,
            ),
        )
    }

    // ---- A2-S1 P2: tiles at/below the 48px touch target keep a body strip ----

    @Test
    fun smallTile_atTouchSize_keepsBodyAndCornerReachable() {
        // 48x48 tile with the 48px touch target: the routing square shrinks to
        // the 24px bottom-right corner ([24,48] x [24,48]) instead of consuming
        // the whole tile.
        val tile = PixelRect(left = 0, top = 0, right = 48, bottom = 48)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        assertEquals(24, resizeHandleSidePx(tile.width, tile.height, touchPx))

        // Arrow corner still routes resize.
        assertTrue(isInHandle(40f, 40f, tile, touchPx))
        assertTrue(isInHandle(48f, 48f, tile, touchPx))

        // Body stays body: top-left, center-adjacent and top edge never route.
        assertFalse(isInHandle(8f, 8f, tile, touchPx))
        assertFalse(isInHandle(23f, 23f, tile, touchPx))
        assertFalse(isInHandle(24f, 10f, tile, touchPx))
        assertFalse(isInHandle(10f, 24f, tile, touchPx))

        // Remove control center (right, top) no longer falls in the handle rect.
        assertFalse(isInHandle(48f, 0f, tile, touchPx))
    }

    @Test
    fun smallTile_belowTouchSize_keepsBodyAndCornerReachable() {
        // 32x32 tile with the 48px touch target: the routing square shrinks to
        // the 16px bottom-right corner ([16,32] x [16,32]).
        val tile = PixelRect(left = 0, top = 0, right = 32, bottom = 32)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        assertEquals(16, resizeHandleSidePx(tile.width, tile.height, touchPx))

        assertTrue(isInHandle(28f, 28f, tile, touchPx))
        assertTrue(isInHandle(32f, 32f, tile, touchPx))

        assertFalse(isInHandle(8f, 8f, tile, touchPx))
        assertFalse(isInHandle(15f, 15f, tile, touchPx))
        assertFalse(isInHandle(16f, 4f, tile, touchPx))

        assertFalse(isInHandle(32f, 0f, tile, touchPx))
    }

    @Test
    fun smallTile_narrowFootprint_shrinksToSmallerExtent() {
        // 48x32 tile: the square side follows the smaller extent (16px), so
        // the top strip and the left body stay reachable.
        val tile = PixelRect(left = 0, top = 0, right = 48, bottom = 32)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        assertEquals(16, resizeHandleSidePx(tile.width, tile.height, touchPx))

        assertTrue(isInHandle(44f, 28f, tile, touchPx))
        assertFalse(isInHandle(8f, 8f, tile, touchPx))
        assertFalse(isInHandle(48f, 0f, tile, touchPx))
    }

    @Test
    fun largeTile_keepsFullTouchSquare() {
        // Regression guard: tiles at least twice the touch target keep the
        // full 48px routing square.
        assertEquals(48, resizeHandleSidePx(200, 200, CORNER_CONTROL_TOUCH_SIZE))
        assertEquals(48, resizeHandleSidePx(96, 96, CORNER_CONTROL_TOUCH_SIZE))
    }
}
