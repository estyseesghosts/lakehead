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
 * transfers selection to it via [HomeEditState.selectOnTap] before
 * [HomeEditState.beginMove]. The transfer never inherits the selected tile's
 * resize arm and never routes RESIZE.
 */
class EditTransferDragTest {

    private val tileA = ItemId("tile-a")
    private val tileB = ItemId("tile-b")
    private val identity = AppIdentity(
        ProfileId("personal"),
        ComponentIdentity("com.app", "Main"),
    )

    private fun tile(id: ItemId, column: Int, row: Int, columns: Int = 1) = HomeRenderTile(
        itemId = id,
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(columns, 1),
        availability = ItemAvailability.AVAILABLE,
        label = id.value,
        icon = null,
        isLaunchable = true,
    )

    private val metrics = HomeGridMetrics(
        columns = 6,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = 0f,
        restOffsetYPx = 0f,
    )

    private fun valid(column: Int, row: Int) =
        MoveCellResult.Valid(RequestedCell(column.toLong(), row.toLong()), LogicalPosition(column, row))

    private val editingA = HomeEditState.Normal.enterOnLongPress(tileA)

    // ---- isEditTransferDrag ----

    @Test
    fun transfer_trueForNonSelectedTile() {
        assertTrue(isEditTransferDrag(editingA, tileB))
    }

    @Test
    fun transfer_trueForNullSelection() {
        // Editing with no selection yet: any tile down transfers into a MOVE.
        assertTrue(isEditTransferDrag(editingA.onTileRemoved(tileA), tileB))
    }

    @Test
    fun transfer_falseForSelectedTile() {
        assertFalse(isEditTransferDrag(editingA, tileA))
    }

    @Test
    fun transfer_falseForBackground() {
        assertFalse(isEditTransferDrag(editingA, null))
    }

    @Test
    fun transfer_falseFromNormal() {
        assertFalse(isEditTransferDrag(HomeEditState.Normal, tileB))
        assertFalse(isEditTransferDrag(HomeEditState.Normal, null))
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

    // ---- end-to-end transfer contract: selectOnTap then beginMove ----

    @Test
    fun transferFlow_selectOnTapThenBeginMoveAdmitsDraggedTileInMove() {
        val tiles = listOf(tile(tileA, 0, 0), tile(tileB, 1, 0))

        val transferred = editingA.selectOnTap(tileB)
        assertEquals(tileB, transferred.selectedTileId)
        assertFalse(transferred.resizeArmed)

        val moving = transferred.beginMove(tiles[1], tiles, metrics, 0f, 0f)
        val move = moving.moveDrag!!
        assertEquals(tileB, move.itemId)
        assertEquals(LogicalPosition(1, 0), move.originalPosition)
        assertEquals(tileB, moving.selectedTileId)
    }

    @Test
    fun transferFlow_neverInheritsResizeArm() {
        // An armed selection transfers disarmed: the dragged tile starts
        // unarmed, so its arrow-drag needs a fresh arrow-tap first.
        val armed = editingA.toggleResizeArmed()
        assertTrue(armed.isResizeArmedFor(tileA))
        assertFalse(
            "a stale arm must never authorize another tile",
            armed.isResizeArmedFor(tileB),
        )
        val transferred = armed.selectOnTap(tileB)
        assertFalse(transferred.isResizeArmedFor(tileB))
        assertFalse(transferred.isResizeArmedFor(tileA))
    }

    @Test
    fun beginMoveOnAnotherTile_transfersSelectionAndClearsArm() {
        val tiles = listOf(tile(tileA, 0, 0), tile(tileB, 1, 0))
        val moving = editingA.toggleResizeArmed().beginMove(tiles[1], tiles, metrics, 0f, 0f)
        assertEquals(tileB, moving.selectedTileId)
        assertFalse(moving.resizeArmed)
    }

    // ---- A3 P1: transfer after a successful resize names only the dragged tile ----

    @Test
    fun transferAfterResize_onlyDraggedTileMoves() {
        val tiles = listOf(tile(tileA, 0, 0), tile(tileB, 3, 0))

        // Resize A to 2x1, release: selection and arm are kept, no drag remains.
        val resized = editingA.toggleResizeArmed()
            .beginResize(tiles[0], metrics)
            .updateResize(LogicalSpan(2, 1), tiles)
            .finish()
        assertEquals(HomeEditCommit.Resize(tileA, LogicalSpan(2, 1)), resized.commit)
        assertEquals(DragState.None, resized.state.drag)

        // Grid now shows A at 2x1; a transfer drag on B starts a fresh move.
        val after = listOf(tile(tileA, 0, 0, columns = 2), tiles[1])
        val moved = resized.state
            .selectOnTap(tileB)
            .beginMove(after[1], after, metrics, 0f, 0f)
            .updateMove(valid(3, 1), after)
            .finish()

        assertEquals(HomeEditCommit.Move(tileB, LogicalPosition(3, 1)), moved.commit)
        assertEquals(tileB, moved.state.selectedTileId)
    }

    @Test
    fun selectedTileRoute_keepsResizeForArmedArrow() {
        // The non-transfer (selected-tile) path is unaffected by a transfer:
        // the armed selected-tile arrow route still resolves RESIZE for A.
        assertFalse(isEditTransferDrag(editingA, tileA))
        assertEquals(
            DownDragRoute.RESIZE,
            transferDragRoute(isTransfer = false, arrowHit = true, armed = true, hasTile = true),
        )
        val tiles = listOf(tile(tileA, 0, 0), tile(tileB, 3, 0))
        val resizing = editingA.toggleResizeArmed().beginResize(tiles[0], metrics)
        assertEquals(tileA, resizing.resizeDrag!!.itemId)
    }
}
