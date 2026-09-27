package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.model.ItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A2-S1 tap-vs-drag route-once contract: the past-slop drag route is decided
 * once from the down snapshot (arrow-hit + armed + tile hit) and never
 * switches mid-gesture.
 */
class MoveResizeRoutingTest {

    private val tileA = ItemId("tile-a")
    private val tileB = ItemId("tile-b")

    // ---- routeDownDrag: arrow + armed → RESIZE ----

    @Test
    fun arrowArmed_routesResize() {
        assertEquals(
            DownDragRoute.RESIZE,
            routeDownDrag(arrowHit = true, armed = true, hasTile = true),
        )
    }

    @Test
    fun arrowArmed_routesResize_evenWithoutBodyHit() {
        // Corner-overflow downs (down cell outside any tile but inside the
        // selected corner) still resize when armed.
        assertEquals(
            DownDragRoute.RESIZE,
            routeDownDrag(arrowHit = true, armed = true, hasTile = false),
        )
    }

    // ---- routeDownDrag: unarmed arrow → neither (never resize, never move) ----

    @Test
    fun arrowUnarmed_routesNeither_notResize() {
        assertEquals(
            DownDragRoute.NONE,
            routeDownDrag(arrowHit = true, armed = false, hasTile = true),
        )
    }

    @Test
    fun arrowUnarmed_routesNeither_evenWithBodyHit() {
        // The arrow region wins over the body: an unarmed arrow-drag must not
        // fall back to move.
        assertEquals(
            DownDragRoute.NONE,
            routeDownDrag(arrowHit = true, armed = false, hasTile = true),
        )
    }

    // ---- routeDownDrag: body → MOVE (armed or not; caller disarms) ----

    @Test
    fun bodyUnarmed_routesMove() {
        assertEquals(
            DownDragRoute.MOVE,
            routeDownDrag(arrowHit = false, armed = false, hasTile = true),
        )
    }

    @Test
    fun bodyArmed_routesMove_notResize() {
        // A body-drag while armed stays move (plain drags never change span);
        // the grid clears the arm on this route.
        assertEquals(
            DownDragRoute.MOVE,
            routeDownDrag(arrowHit = false, armed = true, hasTile = true),
        )
    }

    // ---- routeDownDrag: background → NONE ----

    @Test
    fun background_routesNeither() {
        assertEquals(
            DownDragRoute.NONE,
            routeDownDrag(arrowHit = false, armed = false, hasTile = false),
        )
    }

    @Test
    fun background_armedFlagCannotAuthorize() {
        // Armed without an arrow hit and without a tile still routes neither.
        assertEquals(
            DownDragRoute.NONE,
            routeDownDrag(arrowHit = false, armed = true, hasTile = false),
        )
    }

    // ---- route-once: same snapshot always yields the same route ----

    @Test
    fun routing_isSnapshotStable_noMidGestureSwitch() {
        // Routing inputs are captured at down; re-evaluating with the same
        // snapshot (e.g. the pointer later drifts into the handle corner or
        // out of it) must not change the verdict.
        val moveRoute = routeDownDrag(arrowHit = false, armed = false, hasTile = true)
        assertEquals(DownDragRoute.MOVE, moveRoute)
        assertEquals(moveRoute, routeDownDrag(arrowHit = false, armed = false, hasTile = true))
        val noneRoute = routeDownDrag(arrowHit = true, armed = false, hasTile = true)
        assertEquals(DownDragRoute.NONE, noneRoute)
        assertEquals(noneRoute, routeDownDrag(arrowHit = true, armed = false, hasTile = true))
        val resizeRoute = routeDownDrag(arrowHit = true, armed = true, hasTile = true)
        assertEquals(DownDragRoute.RESIZE, resizeRoute)
        assertEquals(resizeRoute, routeDownDrag(arrowHit = true, armed = true, hasTile = true))
    }

    // ---- snapshotResizeArmed: arm owned by the selected item ----

    @Test
    fun snapshotArmed_trueForArmedSelection() {
        val interaction = HomeInteractionController.Editing(selectedId = tileA, resizeArmed = true)
        assertTrue(snapshotResizeArmed(interaction, tileA))
    }

    @Test
    fun snapshotArmed_falseForDifferentTile() {
        // A stale arm never authorizes another tile.
        val interaction = HomeInteractionController.Editing(selectedId = tileA, resizeArmed = true)
        assertFalse(snapshotResizeArmed(interaction, tileB))
    }

    @Test
    fun snapshotArmed_falseWhenDisarmed() {
        val interaction = HomeInteractionController.Editing(selectedId = tileA)
        assertFalse(snapshotResizeArmed(interaction, tileA))
    }

    @Test
    fun snapshotArmed_falseWhenNoSelection() {
        val interaction = HomeInteractionController.Editing(selectedId = null, resizeArmed = false)
        assertFalse(snapshotResizeArmed(interaction, tileA))
        assertFalse(snapshotResizeArmed(interaction, null))
    }

    @Test
    fun snapshotArmed_falseFromNormal() {
        assertFalse(snapshotResizeArmed(HomeInteractionController.Normal, tileA))
    }

    @Test
    fun snapshotArmed_falseForNullTile() {
        val interaction = HomeInteractionController.Editing(selectedId = tileA, resizeArmed = true)
        assertFalse(snapshotResizeArmed(interaction, null))
    }

    // ---- A2-S1 P2: small-tile (at/below 48px) hit + route end to end ----

    @Test
    fun smallTile_unarmedBodyDrag_routesMove() {
        // 48x48 tile: without the handle shrink the inward 48px square would
        // consume the whole tile and this body drag would route NONE. With the
        // shrink the top-left stays body and still MOVEs while unarmed.
        val tile = PixelRect(left = 0, top = 0, right = 48, bottom = 48)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        val arrowHit = isInHandle(8f, 8f, tile, touchPx)
        assertFalse(arrowHit)
        assertEquals(
            DownDragRoute.MOVE,
            routeDownDrag(arrowHit = arrowHit, armed = false, hasTile = true),
        )
    }

    @Test
    fun smallTile_armedCornerDrag_routesResize() {
        // The shrunk corner stays reachable: an armed corner drag on a 48x48
        // tile still resizes.
        val tile = PixelRect(left = 0, top = 0, right = 48, bottom = 48)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        val arrowHit = isInHandle(40f, 40f, tile, touchPx)
        assertTrue(arrowHit)
        assertEquals(
            DownDragRoute.RESIZE,
            routeDownDrag(arrowHit = arrowHit, armed = true, hasTile = true),
        )
    }

    @Test
    fun smallTile_unarmedCornerDrag_routesNeither() {
        // The shrunk corner is still the arrow region: unarmed it routes to
        // neither move nor resize, even on a small tile.
        val tile = PixelRect(left = 0, top = 0, right = 48, bottom = 48)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        val arrowHit = isInHandle(40f, 40f, tile, touchPx)
        assertTrue(arrowHit)
        assertEquals(
            DownDragRoute.NONE,
            routeDownDrag(arrowHit = arrowHit, armed = false, hasTile = true),
        )
    }

    @Test
    fun tinyTile_belowTouchSize_bodyDrag_routesMove() {
        // 32x32 tile (below the 48px target): body stays reachable and MOVEs.
        val tile = PixelRect(left = 0, top = 0, right = 32, bottom = 32)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        val arrowHit = isInHandle(8f, 8f, tile, touchPx)
        assertFalse(arrowHit)
        assertEquals(
            DownDragRoute.MOVE,
            routeDownDrag(arrowHit = arrowHit, armed = false, hasTile = true),
        )
        // And the shrunk corner still resizes when armed.
        val cornerHit = isInHandle(28f, 28f, tile, touchPx)
        assertTrue(cornerHit)
        assertEquals(
            DownDragRoute.RESIZE,
            routeDownDrag(arrowHit = cornerHit, armed = true, hasTile = true),
        )
    }

    @Test
    fun smallTile_signedFrame_bodyDrag_routesMove() {
        // Signed G3 frame overload shrinks identically: body above the row
        // origin stays body.
        val frame = SignedPixelFrame(left = 0, top = -100, widthPx = 48, heightPx = 48)
        val touchPx = CORNER_CONTROL_TOUCH_SIZE
        val arrowHit = isInHandle(8f, -92f, frame, touchPx)
        assertFalse(arrowHit)
        assertEquals(
            DownDragRoute.MOVE,
            routeDownDrag(arrowHit = arrowHit, armed = false, hasTile = true),
        )
        val cornerHit = isInHandle(40f, -60f, frame, touchPx)
        assertTrue(cornerHit)
        assertEquals(
            DownDragRoute.RESIZE,
            routeDownDrag(arrowHit = cornerHit, armed = true, hasTile = true),
        )
    }
}
