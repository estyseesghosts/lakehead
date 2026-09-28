package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
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
 * A3 unified edit-drag transfer contract (pure, framework-free).
 *
 * A past-slop drag on a non-selected edit-mode tile moves that tile and
 * transfers selection to it via [HomeInteractionController.selectOnTap] before
 * [HomeEditSession.enterSelectedDrag]. The transfer never inherits the
 * selected tile's resize arm and never routes RESIZE.
 */
class EditTransferDragTest {

    private val tileA = ItemId("tile-a")
    private val tileB = ItemId("tile-b")
    private val identity = AppIdentity(
        ProfileId("personal"),
        ComponentIdentity("com.app", "Main"),
    )

    private fun tile(id: ItemId, column: Int, row: Int) = HomeRenderTile(
        itemId = id,
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = id.value,
        icon = null,
        isLaunchable = true,
    )

    // ---- isEditTransferDrag ----

    @Test
    fun transfer_trueForNonSelectedTile() {
        val interaction = HomeInteractionController.Editing(selectedId = tileA)
        assertTrue(isEditTransferDrag(interaction, tileB))
    }

    @Test
    fun transfer_trueForNullSelection() {
        // Editing with no selection yet: any tile down transfers into a MOVE.
        val interaction = HomeInteractionController.Editing(selectedId = null)
        assertTrue(isEditTransferDrag(interaction, tileB))
    }

    @Test
    fun transfer_falseForSelectedTile() {
        val interaction = HomeInteractionController.Editing(selectedId = tileA)
        assertFalse(isEditTransferDrag(interaction, tileA))
    }

    @Test
    fun transfer_falseForBackground() {
        val interaction = HomeInteractionController.Editing(selectedId = tileA)
        assertFalse(isEditTransferDrag(interaction, null))
    }

    @Test
    fun transfer_falseFromNormal() {
        assertFalse(isEditTransferDrag(HomeInteractionController.Normal, tileB))
        assertFalse(isEditTransferDrag(HomeInteractionController.Normal, null))
    }

    // ---- transferDragRoute: transfer is direct MOVE, never RESIZE ----

    @Test
    fun transferRoute_arrowArmed_stillMove_neverResize() {
        // Even an armed handle-corner hit on another tile must not resize:
        // transfer drags carry no resize interpretation.
        assertEquals(
            DownDragRoute.MOVE,
            transferDragRoute(isTransfer = true, arrowHit = true, armed = true, hasTile = true),
        )
    }

    @Test
    fun transferRoute_body_isMove() {
        assertEquals(
            DownDragRoute.MOVE,
            transferDragRoute(isTransfer = false, arrowHit = false, armed = false, hasTile = true),
        )
        assertEquals(
            DownDragRoute.MOVE,
            transferDragRoute(isTransfer = true, arrowHit = false, armed = false, hasTile = true),
        )
    }

    @Test
    fun nonTransferRoute_delegatesToDownDrag() {
        assertEquals(
            DownDragRoute.RESIZE,
            transferDragRoute(isTransfer = false, arrowHit = true, armed = true, hasTile = true),
        )
        assertEquals(
            DownDragRoute.NONE,
            transferDragRoute(isTransfer = false, arrowHit = true, armed = false, hasTile = true),
        )
        assertEquals(
            DownDragRoute.NONE,
            transferDragRoute(isTransfer = false, arrowHit = false, armed = false, hasTile = false),
        )
    }

    // ---- end-to-end transfer contract: selectOnTap then enterSelectedDrag ----

    @Test
    fun transferFlow_selectOnTapThenEnterAdmitsDraggedTileInMove() {
        val tiles = listOf(tile(tileA, 0, 0), tile(tileB, 1, 0))
        val atDown: HomeInteractionController =
            HomeInteractionController.Editing(selectedId = tileA)

        // Without the transfer the non-selected tile is rejected (the bug
        // this slice fixes): enterSelectedDrag requires a selection match.
        val direct = HomeEditSession.Idle.enterSelectedDrag(
            interaction = atDown,
            tile = tiles[1],
            placements = tiles,
            columns = 6,
            rows = 10,
        )
        assertEquals(HomeEditSession.Idle, direct)

        // Past slop the grid transfers first, then enters: B in MOVE at B.
        val transferred = atDown.selectOnTap(tileB)
        assertEquals(
            HomeInteractionController.Editing(selectedId = tileB, resizeArmed = false),
            transferred,
        )
        val entered = HomeEditSession.Idle.enterSelectedDrag(
            interaction = transferred,
            tile = tiles[1],
            placements = tiles,
            columns = 6,
            rows = 10,
        )
        val active = entered as HomeEditSession.Active
        assertEquals(tileB, active.itemId)
        assertEquals(LogicalPosition(1, 0), active.origin)
        assertEquals(EditMode.MOVE, active.mode)
    }

    @Test
    fun transferFlow_neverInheritsResizeArm() {
        // An armed selection transfers disarmed: the dragged tile starts
        // unarmed, so its arrow-drag needs a fresh arrow-tap first.
        val armed: HomeInteractionController =
            HomeInteractionController.Editing(selectedId = tileA, resizeArmed = true)
        assertTrue(snapshotResizeArmed(armed, tileA))
        assertFalse(
            "a stale arm must never authorize another tile",
            snapshotResizeArmed(armed, tileB),
        )
        val transferred = armed.selectOnTap(tileB)
        assertFalse(snapshotResizeArmed(transferred, tileB))
    }

    // ---- A3 P1: transfer after a successful resize retargets to the dragged tile ----

    @Test
    fun transferAfterResize_retargetsToDraggedTile_onlyBMoves() {
        // A successful resize of A leaves an Active MOVE leftover for A
        // (post-commit snap-back state with the committed span as origin).
        // A past-slop transfer drag on B must discard that stale session and
        // start a fresh MOVE session for B: selection moves to B and a later
        // move commit names B only, never A.
        val resizedTiles = listOf(
            HomeRenderTile(
                itemId = tileA,
                appIdentity = identity,
                position = LogicalPosition(0, 0),
                span = LogicalSpan(2, 1),
                availability = ItemAvailability.AVAILABLE,
                label = "tile-a",
                icon = null,
                isLaunchable = true,
            ),
            tile(tileB, 3, 0),
        )
        val staleLeftover = HomeEditSession.Active(
            itemId = tileA,
            origin = LogicalPosition(0, 0),
            originSpan = LogicalSpan(2, 1),
            candidatePosition = LogicalPosition(0, 0),
            candidateSpan = LogicalSpan(2, 1),
            lastValidSpan = LogicalSpan(2, 1),
            lastValidPosition = LogicalPosition(0, 0),
            candidateValidity = CandidateValidity.VALID,
            mode = EditMode.MOVE,
        )

        // The stale leftover is at its origin: it commits nothing itself.
        assertEquals(null, staleLeftover.moveCommitPosition())

        // Model the live session at pointer-down: the stale Active for A is
        // still current. Driving it with B's drag deltas would misattribute
        // the move to A — this is why the transfer path must discard it.
        var currentSession: HomeEditSession = staleLeftover
        val misdriven = (currentSession as HomeEditSession.Active).updateMoveCandidate(
            LogicalPosition(3, 1),
            resizedTiles,
            6,
            10,
        ) as HomeEditSession.Active
        assertEquals(
            "a stale session driven by another tile's drag still names its own tile",
            tileA,
            misdriven.itemId,
        )
        assertEquals(LogicalPosition(3, 1), misdriven.moveCommitPosition())

        // Past slop the grid transfers selection first, then retargets the
        // session from Idle — the prior Active is discarded, not driven.
        val atDown: HomeInteractionController =
            HomeInteractionController.Editing(selectedId = tileA)
        assertTrue(isEditTransferDrag(atDown, tileB))
        val transferred = atDown.selectOnTap(tileB)
        assertEquals(
            HomeInteractionController.Editing(selectedId = tileB, resizeArmed = false),
            transferred,
        )
        // The transfer path discards the prior session: retarget enters from
        // Idle, so the stale leftover is never passed in or driven.
        currentSession = retargetTransferSession(
            interaction = transferred,
            tile = resizedTiles[1],
            placements = resizedTiles,
            columns = 6,
            rows = 10,
        )
        assertTrue("transfer must discard the stale session", currentSession !== staleLeftover)
        val active = currentSession as HomeEditSession.Active
        assertEquals(tileB, active.itemId)
        assertEquals(LogicalPosition(3, 0), active.origin)
        assertEquals(EditMode.MOVE, active.mode)

        // The retargeted session drives B's move: the release commit pair is
        // (B, new position) — the onMoveTile payload names only B.
        val moved = currentSession.updateMoveCandidate(
            LogicalPosition(3, 1),
            resizedTiles,
            6,
            10,
        ) as HomeEditSession.Active
        assertEquals(tileB, moved.itemId)
        assertEquals(LogicalPosition(3, 1), moved.moveCommitPosition())

        // The discarded leftover is untouched: still names A, still commits nothing.
        assertEquals(tileA, staleLeftover.itemId)
        assertEquals(null, staleLeftover.moveCommitPosition())
    }

    @Test
    fun transferAfterResize_staleSelectedRoute_keepsOwnSession() {
        // The non-transfer (selected-tile) path is untouched by the retarget:
        // it keeps driving its own post-resize Active MOVE leftover for A,
        // and the armed arrow route still resolves RESIZE for A.
        val resizedTiles = listOf(
            HomeRenderTile(
                itemId = tileA,
                appIdentity = identity,
                position = LogicalPosition(0, 0),
                span = LogicalSpan(2, 1),
                availability = ItemAvailability.AVAILABLE,
                label = "tile-a",
                icon = null,
                isLaunchable = true,
            ),
            tile(tileB, 3, 0),
        )
        val staleLeftover = HomeEditSession.Active(
            itemId = tileA,
            origin = LogicalPosition(0, 0),
            originSpan = LogicalSpan(2, 1),
            candidatePosition = LogicalPosition(0, 0),
            candidateSpan = LogicalSpan(2, 1),
            lastValidSpan = LogicalSpan(2, 1),
            lastValidPosition = LogicalPosition(0, 0),
            candidateValidity = CandidateValidity.VALID,
            mode = EditMode.MOVE,
        )
        val selected: HomeInteractionController =
            HomeInteractionController.Editing(selectedId = tileA)
        assertFalse(isEditTransferDrag(selected, tileA))

        // Selected-tile body drag keeps its own session: driving the leftover
        // moves A itself — no fresh Idle enter, no transfer, no retarget.
        val continued = staleLeftover.updateMoveCandidate(
            LogicalPosition(0, 1),
            resizedTiles,
            6,
            10,
        ) as HomeEditSession.Active
        assertEquals(tileA, continued.itemId)
        assertEquals(EditMode.MOVE, continued.mode)
        assertEquals(LogicalPosition(0, 1), continued.moveCommitPosition())

        // The armed selected-tile arrow route still resolves RESIZE, and the
        // leftover's own session still arms into RESIZE for A.
        assertEquals(
            DownDragRoute.RESIZE,
            transferDragRoute(isTransfer = false, arrowHit = true, armed = true, hasTile = true),
        )
        val resizing = staleLeftover.beginResize(armedAtDown = true) as HomeEditSession.Active
        assertEquals(tileA, resizing.itemId)
        assertEquals(EditMode.RESIZE, resizing.mode)
    }
}
