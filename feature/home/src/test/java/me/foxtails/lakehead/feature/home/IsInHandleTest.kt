package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import org.junit.Assert.assertEquals
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
    fun centeredVisualTarget_hasReachableInwardHalfAtTileEdge() {
        // The 32dp visual circle and 48dp touch target are centered on (right,
        // bottom). Only the inward half can be routed by the parent at an edge;
        // the full inward 48px square remains reachable without a dead zone.
        assertTrue(isInHandle(200f, 200f, bounds, handlePx))
        assertTrue(isInHandle(176f, 176f, bounds, handlePx))
        assertTrue(isInHandle(152f, 152f, bounds, handlePx))
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

    // ---- B3 hardening: signed-frame parity ----

    @Test
    fun signedFrame_parityWithPixelRect() {
        // B3: the G3 SignedPixelFrame overload applies the identical
        // bottom-right inward rule. Same 200x200 footprint, same 48px handle:
        // every verdict must agree with the PixelRect overload above.
        val signed = SignedPixelFrame(left = 0, top = 0, widthPx = 200, heightPx = 200)
        assertEquals(isInHandle(50f, 50f, bounds, handlePx), isInHandle(50f, 50f, signed, handlePx))
        assertEquals(isInHandle(180f, 180f, bounds, handlePx), isInHandle(180f, 180f, signed, handlePx))
        assertEquals(isInHandle(152f, 152f, bounds, handlePx), isInHandle(152f, 152f, signed, handlePx))
        assertEquals(isInHandle(200f, 200f, bounds, handlePx), isInHandle(200f, 200f, signed, handlePx))
        assertEquals(isInHandle(151f, 180f, bounds, handlePx), isInHandle(151f, 180f, signed, handlePx))
        assertEquals(isInHandle(201f, 200f, bounds, handlePx), isInHandle(201f, 200f, signed, handlePx))
        assertTrue(isInHandle(180f, 180f, signed, handlePx))
        assertFalse(isInHandle(50f, 50f, signed, handlePx))
    }

    @Test
    fun signedFrame_negativeTop_stillInward() {
        // B3: content above the row origin carries a negative top. A frame at
        // top=-100 (height 200 → bottom=100): the handle is [52,100] in y,
        // still inward over the corner, never outward past the tile.
        val above = SignedPixelFrame(left = 0, top = -100, widthPx = 200, heightPx = 200)
        assertTrue(isInHandle(180f, 80f, above, handlePx))
        assertTrue(isInHandle(200f, 100f, above, handlePx))
        assertFalse(isInHandle(180f, 51f, above, handlePx))
        assertFalse(isInHandle(180f, 101f, above, handlePx))
        assertFalse(isInHandle(201f, 80f, above, handlePx))
    }

    @Test(expected = IllegalArgumentException::class)
    fun signedFrame_nonPositiveHandle_throws() {
        val signed = SignedPixelFrame(left = 0, top = 0, widthPx = 200, heightPx = 200)
        isInHandle(180f, 180f, signed, 0)
    }

    // ---- B3 hardening: origin-vs-live distinction ----

    @Test
    fun routing_mustUseOriginSnapshot_notLiveBounds() {
        // B3: drag routing hit-tests the ORIGIN snapshot (fixed at gesture
        // start), never the live candidate bounds that grow during the drag.
        // A 1x1 origin (0,0,100,100, handle [52,100]) resized toward 2x2 live
        // bounds (0,0,208,208, handle [160,208]): point (170,170) sits inside
        // the LIVE handle but outside the ORIGIN footprint — routing it as
        // RESIZE would promote a body-origin MOVE mid-gesture. The grid passes
        // the origin snapshot, so this point routes MOVE (false here).
        val originBounds = PixelRect(left = 0, top = 0, right = 100, bottom = 100)
        val liveBounds = PixelRect(left = 0, top = 0, right = 208, bottom = 208)
        assertFalse(isInHandle(170f, 170f, originBounds, handlePx))
        assertTrue(isInHandle(170f, 170f, liveBounds, handlePx))
        // Conversely a point in the origin handle (90,90 in [52,100]) falls
        // OUTSIDE the grown live handle ([160,208]) — re-testing against
        // live bounds mid-gesture would wrongly demote a true RESIZE. Both
        // directions prove routing must snapshot once at gesture start.
        assertTrue(isInHandle(90f, 90f, originBounds, handlePx))
        assertFalse(isInHandle(90f, 90f, liveBounds, handlePx))
    }
}
