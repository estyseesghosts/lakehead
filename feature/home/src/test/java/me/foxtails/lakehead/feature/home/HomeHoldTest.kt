package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1 truth table for [isHomeHold]: hold iff an edit session is active or wiggle
 * editing is engaged. B1 removed the shown-surface arm: no idle/editing flick
 * opens a surface, so there is no shown state left to hold.
 *
 * P0 guards: [gridHoldSignal] forces false on empty tiles (populated→empty
 * must release shell `homeHold`), and [shouldResetCanvasHold] forces false on
 * Loading/NotFound (which never compose the grid).
 */
class HomeHoldTest {

    private val tileId = ItemId("tile-1")

    private fun activeSession(): HomeEditSession.Active = HomeEditSession.Active(
        itemId = tileId,
        origin = LogicalPosition(0, 0),
        originSpan = LogicalSpan(1, 1),
        candidatePosition = LogicalPosition(0, 0),
        candidateSpan = LogicalSpan(1, 1),
        lastValidSpan = LogicalSpan(1, 1),
        lastValidPosition = LogicalPosition(0, 0),
        candidateValidity = CandidateValidity.VALID,
        mode = EditMode.MOVE,
    )

    private fun activeResizeSession(): HomeEditSession.Active = activeSession().copy(
        mode = EditMode.RESIZE,
    )

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
            window = GridRowWindow(0L, 1L),
        )
    }

    @Test
    fun idleNormalHidden_doesNotHold() {
        assertFalse(
            isHomeHold(
                null,
                HomeInteractionController.Normal,
            ),
        )
    }

    @Test
    fun activeOnly_holds() {
        assertTrue(
            isHomeHold(
                activeSession(),
                HomeInteractionController.Normal,
            ),
        )
    }

    @Test
    fun editingOnly_holds() {
        assertTrue(
            isHomeHold(
                null,
                HomeInteractionController.Editing(selectedId = tileId),
            ),
        )
    }

    @Test
    fun activeAndEditing_holds() {
        assertTrue(
            isHomeHold(
                activeSession(),
                HomeInteractionController.Editing(selectedId = tileId),
            ),
        )
    }

    @Test
    fun resizeActiveOnly_holds() {
        assertTrue(
            isHomeHold(
                activeResizeSession(),
                HomeInteractionController.Normal,
            ),
        )
    }

    @Test
    fun editingNullSelected_holds() {
        assertTrue(
            isHomeHold(
                null,
                HomeInteractionController.Editing(selectedId = null),
            ),
        )
    }

    // B1 regression: swipe-to-open is removed. An idle tile swipe that
    // completes at the arbitration layer (COMPLETE under the retired S2
    // thresholds, inlined here as history) must neither hold the pager via
    // a surface nor reach any surface callback: the grid exposes no surface
    // rows/execute/pin API, and the hold covers active/editing only.
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
        assertFalse(
            gridHoldSignal(
                tilesEmpty = false,
                active = null,
                interaction = HomeInteractionController.Normal,
            ),
        )
    }

    // P0: empty grid never holds, even with stale transient inputs that would
    // otherwise hold. Covers the populated→empty transition where the grid
    // early-returns before transient state exists and must emit false.
    @Test
    fun gridHoldSignal_emptyWithActive_holdsFalse() {
        assertFalse(
            gridHoldSignal(
                tilesEmpty = true,
                active = activeSession(),
                interaction = HomeInteractionController.Normal,
            ),
        )
    }

    @Test
    fun gridHoldSignal_emptyWithActiveAndEditing_holdsFalse() {
        assertFalse(
            gridHoldSignal(
                tilesEmpty = true,
                active = activeResizeSession(),
                interaction = HomeInteractionController.Editing(selectedId = tileId),
            ),
        )
    }

    @Test
    fun gridHoldSignal_emptyWithEditingNull_holdsFalse() {
        assertFalse(
            gridHoldSignal(
                tilesEmpty = true,
                active = null,
                interaction = HomeInteractionController.Editing(selectedId = null),
            ),
        )
    }

    @Test
    fun gridHoldSignal_nonEmptyDelegatesToPredicate() {
        assertFalse(
            gridHoldSignal(
                tilesEmpty = false,
                active = null,
                interaction = HomeInteractionController.Normal,
            ),
        )
        assertTrue(
            gridHoldSignal(
                tilesEmpty = false,
                active = activeSession(),
                interaction = HomeInteractionController.Normal,
            ),
        )
        assertTrue(
            gridHoldSignal(
                tilesEmpty = false,
                active = activeResizeSession(),
                interaction = HomeInteractionController.Normal,
            ),
        )
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
