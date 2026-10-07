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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slices 4.2 / 4.3: MOVE and RESIZE routing at the pointer boundary.
 *
 * Pointers are derived from the metrics, so every case asserts a real
 * pointer-to-cell mapping. MOVE never changes the span and RESIZE never
 * changes the position.
 */
class HomeDragRoutingTest {

    private val identity = AppIdentity(ProfileId("personal"), ComponentIdentity("com.app", "Main"))

    // Cell stride 100 px horizontally, 108 px vertically (8 px row gap).
    private val metrics = HomeGridMetrics(
        columns = 6,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = 0f,
        restOffsetYPx = 0f,
    )

    private fun tile(id: String, column: Int, row: Int, columns: Int = 1, rows: Int = 1) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(columns, rows),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )

    private fun beginMove(
        moving: HomeRenderTile,
        others: List<HomeRenderTile> = emptyList(),
        grabX: Float,
        grabY: Float,
    ): HomeEditState = HomeEditState.Normal
        .enterOnLongPress(moving.itemId)
        .beginMove(moving, listOf(moving) + others, metrics, grabX, grabY)

    /** Pointer that puts a tile grabbed at ([grabX], [grabY]) with its top-left on cell ([column], [row]). */
    private fun pointerFor(column: Int, row: Int, grabX: Float, grabY: Float, scroll: Float = 0f) =
        Pair(metrics.left(column) + grabX, metrics.top(row) + grabY - scroll)

    private fun HomeEditState.dragTo(column: Int, row: Int, grabX: Float, grabY: Float, scroll: Float = 0f): HomeEditState {
        val (x, y) = pointerFor(column, row, grabX, grabY, scroll)
        return moveTo(x, y, scroll)
    }

    // ---- 4.2 MOVE ----------------------------------------------------------------

    @Test
    fun move_leftRightUpDown_requestExactCellAndKeepSpan() {
        val moving = tile("m", 2, 3)
        val start = beginMove(moving, grabX = 50f, grabY = 50f)
        listOf(1 to 3, 3 to 3, 2 to 2, 2 to 4).forEach { (c, r) ->
            val move = start.dragTo(c, r, 50f, 50f).moveDrag!!
            assertEquals(RequestedCell(c.toLong(), r.toLong()), move.requestedCell)
            assertEquals(LogicalPosition(c, r), move.previewPosition)
            assertEquals(CandidateValidity.VALID, move.validity)
            assertEquals(LogicalSpan(1, 1), move.span)
        }
    }

    @Test
    fun move_diagonal_keepsSpanAndRequestsExactCell() {
        val moving = tile("m", 1, 1, columns = 2, rows = 2)
        listOf(3 to 4, 0 to 0, 4 to 0, 0 to 6).forEach { (c, r) ->
            val move = beginMove(moving, grabX = 80f, grabY = 120f).dragTo(c, r, 80f, 120f).moveDrag!!
            assertEquals(LogicalPosition(c, r), move.previewPosition)
            assertEquals(LogicalSpan(2, 2), move.span)
        }
    }

    @Test
    fun move_grabAtCenterAndEachCorner_mapsToTheSameCell() {
        listOf(
            tile("small", 2, 2) to listOf(50f to 50f, 0f to 0f, 99f to 0f, 0f to 99f, 99f to 99f),
            tile("large", 0, 1, columns = 4, rows = 2) to
                listOf(200f to 108f, 0f to 0f, 399f to 0f, 0f to 207f, 399f to 207f),
        ).forEach { (moving, grabs) ->
            grabs.forEach { (gx, gy) ->
                val move = beginMove(moving, grabX = gx, grabY = gy).dragTo(1, 3, gx, gy).moveDrag!!
                assertEquals("grab $gx,$gy", LogicalPosition(1, 3), move.previewPosition)
                assertEquals(moving.span, move.span)
            }
        }
    }

    @Test
    fun move_staysAbsolute_notAccumulated() {
        val moving = tile("m", 0, 0)
        var state = beginMove(moving, grabX = 10f, grabY = 10f)
        // Wander, then return: the result depends only on the final pointer.
        listOf(5 to 5, 1 to 9, 4 to 2).forEach { (c, r) -> state = state.dragTo(c, r, 10f, 10f) }
        state = state.dragTo(2, 1, 10f, 10f)
        assertEquals(LogicalPosition(2, 1), state.moveDrag!!.previewPosition)
    }

    @Test
    fun move_usesCurrentScrollForTheSamePointerCell() {
        val moving = tile("m", 0, 0)
        val scroll = 216f
        val move = beginMove(moving, grabX = 40f, grabY = 40f).dragTo(3, 5, 40f, 40f, scroll).moveDrag!!
        assertEquals(LogicalPosition(3, 5), move.previewPosition)
    }

    @Test
    fun move_collision_holdsLastValidAndKeepsSpan() {
        val moving = tile("m", 0, 0, columns = 2)
        val obstacle = tile("o", 3, 2)
        val valid = beginMove(moving, listOf(obstacle), 50f, 50f).dragTo(1, 1, 50f, 50f)
        val blocked = valid.dragTo(2, 2, 50f, 50f).moveDrag!!
        assertEquals(CandidateValidity.COLLIDING, blocked.validity)
        assertEquals(LogicalPosition(1, 1), blocked.previewPosition)
        assertEquals(RequestedCell(2, 2), blocked.requestedCell)
        assertEquals(LogicalSpan(2, 1), blocked.span)
    }

    @Test
    fun move_rowAboveZero_isRejectedRawAndNeverClamped() {
        val moving = tile("m", 2, 0)
        val start = beginMove(moving, grabX = 50f, grabY = 50f).dragTo(2, 1, 50f, 50f)
        // A pointer far above the grid requests a negative row. RequestedCell
        // keeps it raw; no LogicalPosition/GridPosition can represent it.
        val above = start.moveTo(pointerXPx = 250f, pointerYPxViewport = -300f, scrollYPx = 0f).moveDrag!!
        assertTrue(above.requestedCell.row < 0)
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, above.validity)
        assertEquals(LogicalPosition(2, 1), above.previewPosition)
        assertEquals(LogicalSpan(1, 1), above.span)
        assertEquals(
            MoveCellResult.Rejected(me.foxtails.lakehead.core.grid.GridRules.Rejection.OUT_OF_BOUNDS, above.requestedCell),
            MoveDragMath.cellForPixel(above.frame, 250f, -300f, 0f),
        )
    }

    @Test
    fun move_rightEdgeOverflow_isRejectedForEverySpan() {
        listOf(1 to 1, 2 to 1, 2 to 2, 4 to 2).forEach { (cols, rows) ->
            val moving = tile("m", 0, 0, columns = cols, rows = rows)
            val lastFit = 6 - cols
            val state = beginMove(moving, grabX = 10f, grabY = 10f)
            val fits = state.dragTo(lastFit, 2, 10f, 10f).moveDrag!!
            assertEquals(CandidateValidity.VALID, fits.validity)
            val over = state.dragTo(lastFit + 1, 2, 10f, 10f).moveDrag!!
            assertEquals("span $cols×$rows", CandidateValidity.OUT_OF_BOUNDS, over.validity)
            assertEquals(LogicalPosition(0, 0), over.previewPosition)
            assertEquals(LogicalSpan(cols, rows), over.span)
        }
    }

    @Test
    fun move_finish_commitsPositionOnly() {
        val moving = tile("m", 0, 0, columns = 2, rows = 2)
        val finished = beginMove(moving, grabX = 10f, grabY = 10f).dragTo(3, 3, 10f, 10f).finish()
        assertEquals(HomeEditCommit.Move(ItemId("m"), LogicalPosition(3, 3)), finished.commit)
        assertNull(finished.state.moveDrag)
    }

    // ---- 4.3 RESIZE --------------------------------------------------------------

    private fun armedResize(resizing: HomeRenderTile, others: List<HomeRenderTile> = emptyList()): HomeEditState =
        HomeEditState.Normal
            .enterOnLongPress(resizing.itemId)
            .toggleResizeArmed()
            .beginResize(resizing, listOf(resizing) + others, metrics)

    private fun HomeEditState.resizeToSpan(columns: Int, rows: Int): HomeEditState {
        val origin = resizeDrag!!.position
        return resizeTo(
            pointerXPx = metrics.left(origin.column) + (columns - 1) * 100f + 1f,
            pointerYPxViewport = metrics.top(origin.row) + (rows - 1) * 108f + 1f,
            scrollYPx = 0f,
        )
    }

    @Test
    fun resize_positionIsUnchangedAcrossGrowShrinkAndInvalidCandidates() {
        val resizing = tile("r", 2, 1)
        val blocker = tile("b", 3, 1)
        var state = armedResize(resizing, listOf(blocker))
        val origin = LogicalPosition(2, 1)
        listOf(1 to 1, 1 to 3, 1 to 2, 2 to 1, 9 to 1, 6 to 6, 1 to 1).forEach { (c, r) ->
            state = state.resizeToSpan(c, r)
            assertEquals("after ($c,$r)", origin, state.resizeDrag!!.position)
        }
    }

    @Test
    fun resize_invalidCandidate_holdsLastValidSpan() {
        val resizing = tile("r", 2, 1)
        val blocker = tile("b", 3, 1)
        val grown = armedResize(resizing, listOf(blocker)).resizeToSpan(1, 2)
        assertEquals(CandidateValidity.VALID, grown.resizeDrag!!.validity)

        val collides = grown.resizeToSpan(2, 1).resizeDrag!!
        assertEquals(CandidateValidity.COLLIDING, collides.validity)
        assertEquals(LogicalSpan(1, 2), collides.previewSpan)
        assertEquals(LogicalPosition(2, 1), collides.position)

    }

    @Test
    fun resize_rightEdgeOverflow_isRejectedNotClamped() {
        val resizing = tile("r", 4, 1)
        val overflow = armedResize(resizing).resizeToSpan(5, 2).resizeDrag!!
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, overflow.validity)
        assertEquals(LogicalSpan(5, 2), overflow.requestedSpan)
        assertEquals(LogicalSpan(1, 1), overflow.previewSpan)
        assertEquals(LogicalPosition(4, 1), overflow.position)
    }

    @Test
    fun resize_finish_commitsSpanOnlyAndRetainsSelectionAndArm() {
        val resizing = tile("r", 4, 2)
        val finished = armedResize(resizing).resizeToSpan(2, 2).finish()
        assertEquals(HomeEditCommit.Resize(ItemId("r"), LogicalSpan(2, 2)), finished.commit)
        assertEquals(ItemId("r"), finished.state.selectedTileId)
        assertTrue(finished.state.resizeArmed)
        assertFalse(finished.state.isDragging)
    }

    @Test
    fun resize_invalidFinish_commitsNothing() {
        val resizing = tile("r", 4, 2)
        val finished = armedResize(resizing).resizeToSpan(5, 2).finish()
        assertNull(finished.commit)
    }

    @Test
    fun resize_cancel_keepsPositionDropsPreviewAndKeepsArm() {
        val resizing = tile("r", 4, 2)
        val dragging = armedResize(resizing).resizeToSpan(2, 2)
        assertEquals(LogicalPosition(4, 2), dragging.resizeDrag!!.position)
        val cancelled = dragging.cancelDrag()
        assertFalse(cancelled.isDragging)
        assertTrue(cancelled.resizeArmed)
        assertEquals(ItemId("r"), cancelled.selectedTileId)
        assertEquals(resizing.span, cancelled.renderedSpan(resizing.itemId, resizing.span))
    }

    @Test
    fun resize_isUnreachableUnlessArmedAtTheHandle() {
        val selected = HomeEditState.Normal.enterOnLongPress(ItemId("r"))
        val resizing = tile("r", 0, 0)
        assertSame(selected, selected.beginResize(resizing, listOf(resizing), metrics))
        // Unarmed handle hit: no route. Armed handle hit: RESIZE. Body hit: MOVE.
        assertEquals(DownDragRoute.NONE, transferDragRoute(false, arrowHit = true, armed = false, hasTile = true))
        assertEquals(DownDragRoute.RESIZE, transferDragRoute(false, arrowHit = true, armed = true, hasTile = true))
        assertEquals(DownDragRoute.MOVE, transferDragRoute(false, arrowHit = false, armed = true, hasTile = true))
        // A transfer drag never inherits the selected tile's arm.
        assertEquals(DownDragRoute.MOVE, transferDragRoute(true, arrowHit = true, armed = true, hasTile = true))
    }

    @Test
    fun resize_beginAndDragNeverToggleTheArm() {
        val resizing = tile("r", 0, 0)
        val begun = armedResize(resizing)
        assertTrue(begun.resizeArmed)
        assertSame(begun, begun.toggleResizeArmed())
        assertTrue(begun.resizeToSpan(2, 2).resizeArmed)
    }

    @Test
    fun routeIsFixedOnceDragging_moveCannotBecomeResize() {
        val moving = tile("m", 0, 0)
        val move = beginMove(moving, grabX = 90f, grabY = 90f)
        assertNotNull(move.moveDrag)
        // Resize updates and begins are inert while a MOVE owns the drag.
        assertSame(move, move.resizeTo(500f, 500f, 0f))
        assertSame(move, move.beginResize(moving, listOf(moving), metrics))
        assertNull(move.toggleResizeArmed().resizeDrag)
    }

    // ---- 4.1 end resolution ------------------------------------------------------

    private fun finishedMove(): HomeEditFinish =
        beginMove(tile("m", 0, 0), grabX = 10f, grabY = 10f).dragTo(2, 2, 10f, 10f).finish()

    @Test
    fun resolveDragEnd_onlyAVisibleLiftCommits() {
        val finished = finishedMove()
        val commit = HomeEditCommit.Move(ItemId("m"), LogicalPosition(2, 2))
        assertEquals(HomeDragEnd.Commit(commit), resolveDragEnd(DragExit.LIFTED, finished, isVisible = true))
        assertEquals(
            HomeDragEnd.Discarded(HomeDragDiscard.ABORTED),
            resolveDragEnd(DragExit.LIFTED, finished, isVisible = false),
        )
        assertEquals(
            HomeDragEnd.Discarded(HomeDragDiscard.CANCELLED),
            resolveDragEnd(DragExit.CANCELLED, finished, isVisible = true),
        )
        assertEquals(
            HomeDragEnd.Discarded(HomeDragDiscard.ABORTED),
            resolveDragEnd(DragExit.STALE, finished, isVisible = true),
        )
    }

    @Test
    fun resolveDragEnd_liftWithNothingToCommitIsNoChange() {
        val untouched = beginMove(tile("m", 0, 0), grabX = 10f, grabY = 10f).finish()
        assertEquals(
            HomeDragEnd.Discarded(HomeDragDiscard.NO_CHANGE),
            resolveDragEnd(DragExit.LIFTED, untouched, isVisible = true),
        )
    }
}
