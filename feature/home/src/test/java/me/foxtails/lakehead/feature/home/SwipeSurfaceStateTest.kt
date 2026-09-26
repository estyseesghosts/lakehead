package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.ItemId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B1: swipe-to-open is removed — the flick classifier, its thresholds and the
 * `showSurface` trigger are deleted, so the show/threshold/flick tests that
 * covered them are deleted with them (no NO-SOURCE stub kept). The show/hide
 * machine ([SwipeSurfaceState.dismiss]/`dismissOnTileRemoved`) and the
 * `swipeSurfaceFrame` geometry (see `SwipeSurfacePlacementTest`) are retained
 * for B4 edit-menu reuse and stay covered.
 */
class SwipeSurfaceStateTest {

    private val tileA = ItemId("tile-a")
    private val tileB = ItemId("tile-b")

    // ---- dismiss ----

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
}
