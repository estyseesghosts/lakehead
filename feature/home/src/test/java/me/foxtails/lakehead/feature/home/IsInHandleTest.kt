package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.PixelRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsInHandleTest {

    private val bounds = PixelRect(left = 0, top = 0, right = 200, bottom = 200)
    private val handlePx = 48

    @Test
    fun center_isNotInHandle() {
        assertFalse(isInHandle(50f, 50f, bounds, handlePx))
    }

    @Test
    fun bottomRightCorner_isInHandle() {
        assertTrue(isInHandle(180f, 180f, bounds, handlePx))
    }

    @Test
    fun handleTopLeftEdge_isInclusive() {
        // Handle rect is [152, 200] x [152, 200].
        assertTrue(isInHandle(152f, 152f, bounds, handlePx))
    }

    @Test
    fun handleBottomRightEdge_isInclusive() {
        assertTrue(isInHandle(200f, 200f, bounds, handlePx))
    }

    @Test
    fun justOutsideHandle_isNotInHandle() {
        assertFalse(isInHandle(151f, 180f, bounds, handlePx))
        assertFalse(isInHandle(180f, 151f, bounds, handlePx))
        assertFalse(isInHandle(201f, 200f, bounds, handlePx))
        assertFalse(isInHandle(200f, 201f, bounds, handlePx))
    }

    @Test
    fun topLeftOfTile_isNotInHandle() {
        assertFalse(isInHandle(10f, 10f, bounds, handlePx))
    }

    // ---- W3 single-BR inward rule ----

    @Test
    fun touchRect_isExactly48Inward_noClip() {
        // Handle rect is [152, 200] x [152, 200]: strictly inside the tile,
        // never extending past right/bottom (no-clip for edge tiles).
        assertTrue(isInHandle(152f, 200f, bounds, handlePx))
        assertTrue(isInHandle(200f, 152f, bounds, handlePx))
        assertFalse(isInHandle(201f, 152f, bounds, handlePx))
        assertFalse(isInHandle(152f, 201f, bounds, handlePx))
        // Left/top neighbours of the rect are body, never handle.
        assertFalse(isInHandle(151f, 152f, bounds, handlePx))
        assertFalse(isInHandle(152f, 151f, bounds, handlePx))
    }

    @Test
    fun minimum40dpTouch_stillInward() {
        // The ≥40dp accessibility minimum also resolves inward: a 40px handle
        // on the same tile covers [160, 200] x [160, 200].
        assertTrue(isInHandle(160f, 160f, bounds, 40))
        assertTrue(isInHandle(200f, 200f, bounds, 40))
        assertFalse(isInHandle(159f, 180f, bounds, 40))
        assertFalse(isInHandle(201f, 200f, bounds, 40))
    }

    @Test
    fun smallTile_handleCoversTileButNeverOutside() {
        // A 48x48 tile with a 48px touch target: the whole tile is handle,
        // yet positions outside the tile stay outside (edge-tile no-clip).
        val small = PixelRect(left = 0, top = 0, right = 48, bottom = 48)
        assertTrue(isInHandle(0f, 0f, small, 48))
        assertTrue(isInHandle(24f, 24f, small, 48))
        assertTrue(isInHandle(48f, 48f, small, 48))
        assertFalse(isInHandle(-1f, 24f, small, 48))
        assertFalse(isInHandle(24f, -1f, small, 48))
        assertFalse(isInHandle(49f, 48f, small, 48))
        assertFalse(isInHandle(48f, 49f, small, 48))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveHandle_throws() {
        isInHandle(180f, 180f, bounds, 0)
    }
}
