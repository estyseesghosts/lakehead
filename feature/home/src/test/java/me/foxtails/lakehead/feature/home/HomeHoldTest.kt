package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1 truth table for [isHomeHold]: hold iff an edit session is active, wiggle
 * editing is engaged, or a shortcut surface is shown.
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
                SwipeSurfaceState.Hidden,
            ),
        )
    }

    @Test
    fun activeOnly_holds() {
        assertTrue(
            isHomeHold(
                activeSession(),
                HomeInteractionController.Normal,
                SwipeSurfaceState.Hidden,
            ),
        )
    }

    @Test
    fun editingOnly_holds() {
        assertTrue(
            isHomeHold(
                null,
                HomeInteractionController.Editing(selectedId = tileId),
                SwipeSurfaceState.Hidden,
            ),
        )
    }

    @Test
    fun shownOnly_holds() {
        assertTrue(
            isHomeHold(
                null,
                HomeInteractionController.Normal,
                SwipeSurfaceState.Shown(tileId),
            ),
        )
    }

    @Test
    fun activeAndEditing_holds() {
        assertTrue(
            isHomeHold(
                activeSession(),
                HomeInteractionController.Editing(selectedId = tileId),
                SwipeSurfaceState.Hidden,
            ),
        )
    }

    @Test
    fun activeAndShown_holds() {
        assertTrue(
            isHomeHold(
                activeSession(),
                HomeInteractionController.Normal,
                SwipeSurfaceState.Shown(tileId),
            ),
        )
    }

    @Test
    fun editingAndShown_holds() {
        assertTrue(
            isHomeHold(
                null,
                HomeInteractionController.Editing(selectedId = tileId),
                SwipeSurfaceState.Shown(tileId),
            ),
        )
    }

    @Test
    fun allThree_holds() {
        assertTrue(
            isHomeHold(
                activeSession(),
                HomeInteractionController.Editing(selectedId = tileId),
                SwipeSurfaceState.Shown(tileId),
            ),
        )
    }

    @Test
    fun resizeActiveOnly_holds() {
        assertTrue(
            isHomeHold(
                activeResizeSession(),
                HomeInteractionController.Normal,
                SwipeSurfaceState.Hidden,
            ),
        )
    }

    @Test
    fun editingNullSelected_holds() {
        assertTrue(
            isHomeHold(
                null,
                HomeInteractionController.Editing(selectedId = null),
                SwipeSurfaceState.Hidden,
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
                surface = SwipeSurfaceState.Hidden,
            ),
        )
    }

    @Test
    fun gridHoldSignal_emptyWithAllThree_holdsFalse() {
        assertFalse(
            gridHoldSignal(
                tilesEmpty = true,
                active = activeResizeSession(),
                interaction = HomeInteractionController.Editing(selectedId = tileId),
                surface = SwipeSurfaceState.Shown(tileId),
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
                surface = SwipeSurfaceState.Hidden,
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
                surface = SwipeSurfaceState.Hidden,
            ),
        )
        assertTrue(
            gridHoldSignal(
                tilesEmpty = false,
                active = activeSession(),
                interaction = HomeInteractionController.Normal,
                surface = SwipeSurfaceState.Hidden,
            ),
        )
        assertTrue(
            gridHoldSignal(
                tilesEmpty = false,
                active = activeResizeSession(),
                interaction = HomeInteractionController.Normal,
                surface = SwipeSurfaceState.Hidden,
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
