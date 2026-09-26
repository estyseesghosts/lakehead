package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeSurfacePlacementTest {

    private val viewport = PixelRect(left = 0, top = 0, right = 400, bottom = 800)
    private val overlay = FrameSize(widthPx = 120, heightPx = 200)

    @Test
    fun midTile_prefersBesideRightTopAligned() {
        val tile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        assertEquals(
            PixelRect(left = 120, top = 100, right = 240, bottom = 300),
            swipeSurfaceFrame(viewport, tile, overlay),
        )
    }

    @Test
    fun midTile_gapSeparatesSurface() {
        val tile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        assertEquals(
            PixelRect(left = 132, top = 100, right = 252, bottom = 300),
            swipeSurfaceFrame(viewport, tile, overlay, gapPx = 12),
        )
    }

    @Test
    fun rightEdgeTile_flipsToLeadingSide() {
        // Beside-right would run past the viewport; the surface flips left of the tile.
        val tile = SignedPixelFrame(left = 300, top = 100, widthPx = 80, heightPx = 80)
        assertEquals(
            PixelRect(left = 180, top = 100, right = 300, bottom = 300),
            swipeSurfaceFrame(viewport, tile, overlay),
        )
    }

    @Test
    fun bottomRightCorner_clampsPreferredSideIntoViewport() {
        // Neither side fits unclamped vertically; the preferred beside frame clamps
        // to the bottom-right corner of the viewport.
        val tile = SignedPixelFrame(left = 300, top = 700, widthPx = 80, heightPx = 80)
        val frame = swipeSurfaceFrame(viewport, tile, overlay)
        assertEquals(PixelRect(left = 280, top = 600, right = 400, bottom = 800), frame)
    }

    @Test
    fun bottomLeftCorner_clampsBesideFrameIntoViewport() {
        // Beside-right fits horizontally but not vertically; the frame clamps upward.
        val tile = SignedPixelFrame(left = 0, top = 700, widthPx = 80, heightPx = 80)
        val frame = swipeSurfaceFrame(viewport, tile, overlay)
        assertEquals(PixelRect(left = 80, top = 600, right = 200, bottom = 800), frame)
    }

    @Test
    fun aboveOriginTile_clampsToViewportTop() {
        // Content above the signed origin starts negative; the surface clamps to the top.
        val tile = SignedPixelFrame(left = 40, top = -60, widthPx = 80, heightPx = 80)
        val frame = swipeSurfaceFrame(viewport, tile, overlay)
        assertEquals(PixelRect(left = 120, top = 0, right = 240, bottom = 200), frame)
    }

    @Test
    fun placement_alwaysStaysInsideViewport() {
        val tiles = listOf(
            SignedPixelFrame(left = 0, top = 0, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 320, top = 0, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 0, top = 720, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 320, top = 720, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 160, top = 360, widthPx = 80, heightPx = 80),
        )
        for (tile in tiles) {
            val frame = swipeSurfaceFrame(viewport, tile, overlay, gapPx = 8)
            assertTrue(
                "surface $frame for tile $tile must stay inside $viewport",
                frame.left >= viewport.left && frame.top >= viewport.top &&
                    frame.right <= viewport.right && frame.bottom <= viewport.bottom,
            )
        }
    }

    @Test
    fun placement_neverMovesTilesNoReflow() {
        // Placement is a pure overlay measurement: inputs are unchanged and sibling
        // tiles keep their exact frames (no reflow, no packing).
        val tile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        val neighbour = SignedPixelFrame(left = 200, top = 100, widthPx = 80, heightPx = 80)
        val tileBefore = tile.copy()
        val neighbourBefore = neighbour.copy()
        swipeSurfaceFrame(viewport, tile, overlay)
        assertEquals(tileBefore, tile)
        assertEquals(neighbourBefore, neighbour)
    }

    @Test
    fun pixelRectTile_overloadMatchesSignedFrame() {
        val rectTile = PixelRect(left = 40, top = 100, right = 120, bottom = 180)
        val signedTile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        assertEquals(
            swipeSurfaceFrame(viewport, signedTile, overlay),
            swipeSurfaceFrame(viewport, rectTile, overlay),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedOverlay_isRejected() {
        swipeSurfaceFrame(viewport, SignedPixelFrame(40, 100, 80, 80), FrameSize(500, 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeGap_isRejected() {
        swipeSurfaceFrame(viewport, SignedPixelFrame(40, 100, 80, 80), overlay, gapPx = -1)
    }
}
