package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.GestureArbitration.Settle
import me.foxtails.lakehead.core.model.ItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeSurfaceStateTest {

    private val tileA = ItemId("tile-a")
    private val tileB = ItemId("tile-b")

    // ---- show/dismiss ----

    @Test
    fun showSurface_hiddenBecomesShownForTile() {
        val state = SwipeSurfaceState.Hidden.showSurface(tileA)
        assertEquals(SwipeSurfaceState.Shown(tileA), state)
    }

    @Test
    fun showSurface_shownTransfersDirectlyToOtherTile() {
        val state = SwipeSurfaceState.Shown(tileA).showSurface(tileB)
        assertEquals(SwipeSurfaceState.Shown(tileB), state)
    }

    @Test
    fun dismiss_shownBecomesHidden() {
        val state = SwipeSurfaceState.Shown(tileA).dismiss()
        assertEquals(SwipeSurfaceState.Hidden, state)
    }

    @Test
    fun dismiss_hiddenStaysHidden() {
        assertEquals(SwipeSurfaceState.Hidden, SwipeSurfaceState.Hidden.dismiss())
    }

    // ---- dismissOnTileRemoved ----

    @Test
    fun dismissOnTileRemoved_matchingTileDismisses() {
        val state = SwipeSurfaceState.Shown(tileA).dismissOnTileRemoved(tileA)
        assertEquals(SwipeSurfaceState.Hidden, state)
    }

    @Test
    fun dismissOnTileRemoved_otherTileKeepsSurface() {
        val state = SwipeSurfaceState.Shown(tileA).dismissOnTileRemoved(tileB)
        assertEquals(SwipeSurfaceState.Shown(tileA), state)
    }

    @Test
    fun dismissOnTileRemoved_hiddenStaysHidden() {
        assertEquals(
            SwipeSurfaceState.Hidden,
            SwipeSurfaceState.Hidden.dismissOnTileRemoved(tileA),
        )
    }

    // ---- thresholds ----

    @Test
    fun completeFraction_isInsideShortDeliberateBand() {
        assertTrue(
            "complete fraction must stay in 0.18..0.25, was $SWIPE_SURFACE_COMPLETE_FRACTION",
            SWIPE_SURFACE_COMPLETE_FRACTION in 0.18f..0.25f,
        )
    }

    @Test
    fun cancelFraction_isBelowCompleteFraction() {
        assertTrue(
            "cancel $SWIPE_SURFACE_CANCEL_FRACTION must be below complete $SWIPE_SURFACE_COMPLETE_FRACTION",
            SWIPE_SURFACE_CANCEL_FRACTION < SWIPE_SURFACE_COMPLETE_FRACTION,
        )
    }

    // ---- classifySwipeFlick ----

    @Test
    fun flick_beyondCompleteFractionCompletes() {
        assertEquals(
            Settle.COMPLETE,
            classifySwipeFlick(
                displacedFraction = SWIPE_SURFACE_COMPLETE_FRACTION + 0.05f,
                velocityPxPerSec = 0f,
                density = 2f,
            ),
        )
    }

    @Test
    fun flick_exactCompleteFractionCompletes() {
        assertEquals(
            Settle.COMPLETE,
            classifySwipeFlick(
                displacedFraction = SWIPE_SURFACE_COMPLETE_FRACTION,
                velocityPxPerSec = 0f,
                density = 2f,
            ),
        )
    }

    @Test
    fun flick_fastVelocityCompletesBelowFraction() {
        assertEquals(
            Settle.COMPLETE,
            classifySwipeFlick(
                displacedFraction = 0.02f,
                velocityPxPerSec = SWIPE_SURFACE_FLICK_DP_PER_SEC * 2f + 100f,
                density = 2f,
            ),
        )
    }

    @Test
    fun flick_slowShortSwipeCancels() {
        assertEquals(
            Settle.CANCEL,
            classifySwipeFlick(
                displacedFraction = SWIPE_SURFACE_CANCEL_FRACTION,
                velocityPxPerSec = 10f,
                density = 2f,
            ),
        )
    }

    @Test
    fun flick_tapCancels() {
        assertEquals(
            Settle.CANCEL,
            classifySwipeFlick(displacedFraction = 0.01f, velocityPxPerSec = 0f, density = 3f),
        )
    }

    @Test
    fun flick_anyDirectionIsEquivalent() {
        val density = 2.5f
        // Negative fraction (left/up swipe) classifies exactly like positive (right/down).
        assertEquals(
            classifySwipeFlick(0.30f, 0f, density),
            classifySwipeFlick(-0.30f, 0f, density),
        )
        assertEquals(
            Settle.COMPLETE,
            classifySwipeFlick(-0.30f, 0f, density),
        )
        assertEquals(
            classifySwipeFlick(0.02f, -5000f, density),
            classifySwipeFlick(0.02f, 5000f, density),
        )
    }

    @Test
    fun flick_velocityThresholdScalesWithDensity() {
        // 1500px/s completes at density 1 (threshold 1000) but cancels at density 2 (threshold 2000).
        assertEquals(
            Settle.COMPLETE,
            classifySwipeFlick(displacedFraction = 0.01f, velocityPxPerSec = 1500f, density = 1f),
        )
        assertEquals(
            Settle.CANCEL,
            classifySwipeFlick(displacedFraction = 0.01f, velocityPxPerSec = 1500f, density = 2f),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun flick_zeroDensityThrows() {
        classifySwipeFlick(displacedFraction = 0.5f, velocityPxPerSec = 0f, density = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun flick_negativeDensityThrows() {
        classifySwipeFlick(displacedFraction = 0.5f, velocityPxPerSec = 0f, density = -1f)
    }
}
