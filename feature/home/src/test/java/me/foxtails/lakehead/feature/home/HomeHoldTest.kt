package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1 truth table for [isHomeHold]: hold iff edit mode is engaged (a drag
 * implies edit mode). B1 removed the shown-surface arm: no idle/editing flick
 * opens a surface, so there is no shown state left to hold.
 *
 * P0 guards: [gridHoldSignal] forces false on empty tiles (populated→empty
 * must release shell `homeHold`), and [shouldResetCanvasHold] forces false on
 * Loading/NotFound (which never compose the grid).
 */
class HomeHoldTest {

    private val tileId = ItemId("tile-1")

    private val tile = HomeRenderTile(
        itemId = tileId,
        appIdentity = AppIdentity(ProfileId("personal"), ComponentIdentity("com.app", "Main")),
        position = LogicalPosition(0, 0),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = "tile",
        icon = null,
        isLaunchable = true,
    )

    private val metrics = HomeGridMetrics(
        columns = 5,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = 0f,
        restOffsetYPx = 0f,
    )

    private val selected = HomeEditState.Normal.enterOnLongPress(tileId)

    private fun moving() = selected.beginMove(tile, listOf(tile), metrics, 0f, 0f)

    private fun resizing() = selected.toggleResizeArmed().beginResize(tile, listOf(tile), metrics)

    private fun loadedEmpty(): HomeRenderState.Loaded {
        val homeId = HomeId("home-1")
        return HomeRenderState.Loaded(
            requestedHomeId = homeId,
            tiles = emptyList(),
            columns = GridColumns(5),
            extent = GridVerticalExtent(
                occupiedBottomRowExclusive = 0L,
                totalScrollableRows = 1L,
            ),
        )
    }

    @Test
    fun normal_doesNotHold() {
        assertFalse(isHomeHold(HomeEditState.Normal))
    }

    @Test
    fun editingWithSelection_holds() {
        assertTrue(isHomeHold(selected))
    }

    @Test
    fun moveDrag_holds() {
        assertTrue(moving().isDragging)
        assertTrue(isHomeHold(moving()))
    }

    @Test
    fun resizeDrag_holds() {
        assertTrue(resizing().isDragging)
        assertTrue(isHomeHold(resizing()))
    }

    @Test
    fun editingNullSelected_holds() {
        val unselected = selected.onTileRemoved(tileId)
        assertEquals(null, unselected.selectedTileId)
        assertTrue(isHomeHold(unselected))
    }

    @Test
    fun exit_releasesTheHold() {
        assertFalse(isHomeHold(moving().exit()))
    }

    // B1 regression: swipe-to-open is removed. An idle tile swipe that
    // completes at the arbitration layer (COMPLETE under the retired S2
    // thresholds, inlined here as history) must neither hold the pager via
    // a surface nor reach any surface callback: the grid exposes no surface
    // rows/execute/pin API, and the hold covers edit mode only.
    @Test
    fun b1_completeIdleSwipe_showsNoSurface() {
        val settle = GestureArbitration.settleDrag(
            displacedFraction = 0.30f,
            velocityPxPerSec = 0f,
            completeFraction = 0.22f,
            flickVelocityPxPerSec = 1000f * 2f,
            cancelFraction = 0.08f,
        )
        assertEquals(GestureArbitration.Settle.COMPLETE, settle)
        assertFalse(isHomeHold(HomeEditState.Normal))
    }

    @Test
    fun isHomeHold_emptyBackgroundEdit_holds() {
        assertTrue(isHomeHold(HomeEditState.Normal.enterOnBackgroundLongPress()))
    }

    @Test
    fun isHomeHold_delegatesToEditMode() {
        assertFalse(isHomeHold(HomeEditState.Normal))
        assertTrue(isHomeHold(selected))
        assertTrue(isHomeHold(moving()))
        assertTrue(isHomeHold(resizing()))
    }

    // P0: Loading/NotFound never compose the grid, so the canvas must reset
    // shell `homeHold` itself; Loaded stays owned by the grid.
    @Test
    fun canvasReset_loadingResets() {
        assertTrue(shouldResetCanvasHold(HomeRenderState.Loading(HomeId("home-1"))))
    }

    @Test
    fun canvasReset_notFoundResets() {
        assertTrue(shouldResetCanvasHold(HomeRenderState.NotFound(HomeId("home-1"))))
    }

    @Test
    fun canvasReset_loadedDoesNotReset() {
        assertFalse(shouldResetCanvasHold(loadedEmpty()))
    }
}
