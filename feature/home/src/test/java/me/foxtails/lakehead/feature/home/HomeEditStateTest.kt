package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEditStateTest {

    private val identity = AppIdentity(ProfileId("personal"), ComponentIdentity("com.app", "Main"))

    private fun tile(
        id: String,
        column: Int = 0,
        row: Int = 0,
        columns: Int = 1,
        rows: Int = 1,
        launchable: Boolean = true,
    ) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(columns, rows),
        availability = if (launchable) ItemAvailability.AVAILABLE else ItemAvailability.UNAVAILABLE,
        label = id,
        icon = null,
        isLaunchable = launchable,
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

    private fun cell(column: Int, row: Int) = RequestedCell(column.toLong(), row.toLong())

    private fun placementsOf(vararg tiles: HomeRenderTile): List<GridPlacement<ItemId>> = tiles.map {
        GridPlacement(
            it.itemId,
            GridPosition(it.position.column, it.position.row),
            GridSpan(it.span.columns, it.span.rows),
        )
    }

    private fun valid(column: Int, row: Int) =
        MoveCellResult.Valid(cell(column, row), LogicalPosition(column, row))

    private val a = tile("a")
    private val b = tile("b", column = 3)
    private val wide = tile("w", column = 0, row = 2, columns = 4, rows = 2)
    private val all = listOf(a, b, wide)

    private fun editing(id: String = "a") = HomeEditState.Normal.enterOnLongPress(ItemId(id))

    private fun moving(t: HomeRenderTile = a) =
        editing(t.itemId.value).beginMove(t, all, metrics, 10f, 10f)

    private fun resizing(t: HomeRenderTile = a) =
        editing(t.itemId.value).toggleResizeArmed().beginResize(t, all, metrics)

    private fun HomeEditState.move() = drag as DragState.Move
    private fun HomeEditState.resize() = drag as DragState.Resize

    @Test(expected = IllegalArgumentException::class)
    fun init_selectionOutsideEditMode_rejected() {
        HomeEditState(isEditing = false, selectedTileId = ItemId("a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun init_armWithoutSelection_rejected() {
        HomeEditState(isEditing = true, resizeArmed = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun init_dragOfUnselectedTile_rejected() {
        val drag = moving().move()
        HomeEditState(isEditing = true, selectedTileId = ItemId("b"), drag = drag)
    }

    @Test
    fun selection_tapSwitchesAndClearsArm_exitClearsAll() {
        val armed = editing().toggleResizeArmed()
        assertTrue(armed.resizeArmed)
        val tapped = armed.selectOnTap(ItemId("b"))
        assertEquals(ItemId("b"), tapped.selectedTileId)
        assertTrue(!tapped.resizeArmed)
        assertEquals(HomeEditState.Normal, tapped.exit())
    }

    @Test
    fun selectOnTap_outsideEditMode_isIgnored() {
        assertEquals(HomeEditState.Normal, HomeEditState.Normal.selectOnTap(ItemId("a")))
    }

    @Test
    fun beginMove_gating() {
        assertEquals(HomeEditState.Normal, HomeEditState.Normal.beginMove(a, all, metrics, 0f, 0f))
        val notLaunchable = tile("x", column = 5, launchable = false)
        val s = editing()
        assertEquals(s, s.beginMove(notLaunchable, all + notLaunchable, metrics, 0f, 0f))
        val unknown = tile("ghost", column = 4)
        assertEquals(s, s.beginMove(unknown, all, metrics, 0f, 0f))
    }

    @Test
    fun beginMove_transfersSelectionAndClearsArm() {
        val s = editing("b").toggleResizeArmed().beginMove(a, all, metrics, 0f, 0f)
        assertEquals(ItemId("a"), s.selectedTileId)
        assertTrue(!s.resizeArmed)
        assertEquals(a.position, s.move().previewPosition)
    }

    @Test
    fun beginResize_requiresSelectedAndArmed() {
        val unarmed = editing()
        assertEquals(unarmed, unarmed.beginResize(a, all, metrics))
        val armedOther = editing("b").toggleResizeArmed()
        assertEquals(armedOther, armedOther.beginResize(a, all, metrics))
        val ok = resizing()
        assertTrue(ok.resizeArmed)
        assertEquals(a.span, ok.resize().previewSpan)
    }

    @Test
    fun move_neverChangesSpan_forEverySpanAndOutcome() {
        listOf(a, tile("t", column = 1, columns = 2, rows = 2), tile("f", column = 0, row = 5, columns = 4, rows = 2))
            .forEach { t ->
                var s = editing(t.itemId.value).beginMove(t, all + t, metrics, 0f, 0f)
                val span = s.move().span
                // collision with a (0,0), out of bounds above row 0, right-edge overflow, free cell
                s = s.updateMove(valid(0, 0))
                s = s.updateMove(MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS, cell(0, -3)))
                s = s.updateMove(valid(5, 9))
                s = s.updateMove(valid(2, 8))
                assertEquals(span, s.move().span)
                assertEquals(t.span, span)
            }
    }

    @Test
    fun move_blockedHoldsLastValidPreview() {
        var s = moving()
        s = s.updateMove(valid(1, 1))
        assertEquals(LogicalPosition(1, 1), s.move().previewPosition)
        s = s.updateMove(valid(3, 0))
        assertEquals(CandidateValidity.COLLIDING, s.move().validity)
        assertEquals(LogicalPosition(1, 1), s.move().previewPosition)
        s = s.updateMove(MoveCellResult.Rejected(GridRules.Rejection.OUT_OF_BOUNDS, cell(0, -2)))
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, s.move().validity)
        assertEquals(cell(0, -2), s.move().requestedCell)
        assertEquals(LogicalPosition(1, 1), s.move().previewPosition)
    }

    @Test
    fun moveTo_isAbsolute_notPathDependent() {
        val direct = moving().moveTo(250f, 120f, 0f)
        val wandered = moving()
            .moveTo(550f, 500f, 0f)
            .moveTo(10f, 10f, 0f)
            .moveTo(250f, 120f, 0f)
        assertEquals(direct.move().previewPosition, wandered.move().previewPosition)
        assertEquals(direct.move().requestedCell, wandered.move().requestedCell)
    }

    @Test
    fun updateMove_unknownItem_dropsDrag() {
        // A drag begun through beginMove always snapshots a collection that
        // contains the dragged tile — beginMove refuses otherwise — so
        // core:grid cannot report UNKNOWN_ITEM on the public path. This guards
        // the state against a snapshot that lost it: the drag is dropped
        // rather than previewing a cell whose legality cannot be proven.
        val orphaned = DragState.Move(
            itemId = a.itemId,
            originalPosition = a.position,
            span = a.span,
            metrics = metrics,
            grabOffsetXPx = 0f,
            grabOffsetYPx = 0f,
            requestedCell = cell(0, 0),
            previewPosition = a.position,
            validity = CandidateValidity.VALID,
            snapshot = placementsOf(b, wide),
        )
        val s = HomeEditState(isEditing = true, selectedTileId = a.itemId, drag = orphaned)
            .updateMove(valid(1, 1))
        assertEquals(DragState.None, s.drag)
        assertEquals(ItemId("a"), s.selectedTileId)
        assertTrue(s.isEditing)
    }

    @Test
    fun moveDrag_snapshotAlwaysContainsTheDraggedTile() {
        // The public path cannot reach the orphan case above.
        val move = moving().move()
        assertTrue(move.snapshot.any { it.id == move.itemId })
        assertEquals(ItemId("a"), moving().move().snapshot.single { it.id == ItemId("a") }.id)
    }

    @Test
    fun finish_move_commitsOnlyValidChangedPosition() {
        val moved = moving().updateMove(valid(1, 1)).finish()
        assertEquals(HomeEditCommit.Move(ItemId("a"), LogicalPosition(1, 1)), moved.commit)
        assertEquals(DragState.None, moved.state.drag)
        assertEquals(ItemId("a"), moved.state.selectedTileId)

        assertNull(moving().finish().commit)
        val blocked = moving().updateMove(valid(1, 1)).updateMove(valid(3, 0)).finish()
        assertNull(blocked.commit)
    }

    @Test
    fun resize_neverChangesPosition_validBlockedAndOutOfBounds() {
        var s = resizing()
        val position = s.resize().position
        s = s.updateResize(LogicalSpan(2, 2))
        assertEquals(LogicalSpan(2, 2), s.resize().previewSpan)
        s = s.updateResize(LogicalSpan(4, 1))
        assertEquals(CandidateValidity.COLLIDING, s.resize().validity)
        assertEquals(LogicalSpan(2, 2), s.resize().previewSpan)
        s = s.updateResize(LogicalSpan(8, 1))
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, s.resize().validity)
        assertEquals(position, s.resize().position)
        assertEquals(a.position, s.resize().position)
    }

    @Test
    fun finish_resize_commitsLastValidSpan_andKeepsArm() {
        val f = resizing().updateResize(LogicalSpan(2, 1)).updateResize(LogicalSpan(4, 1)).finish()
        assertEquals(HomeEditCommit.Resize(ItemId("a"), LogicalSpan(2, 1)), f.commit)
        assertTrue(f.state.resizeArmed)
        assertEquals(DragState.None, f.state.drag)
        assertNull(resizing().finish().commit)
    }

    @Test
    fun resizeTo_quantizesPointerToRenderableSpan() {
        val s = resizing().resizeTo(150f, 10f, 0f)
        assertEquals(1, s.resize().requestedSpan.rows)
        assertTrue(s.resize().requestedSpan.columns >= 1)
        assertEquals(a.position, s.resize().position)
    }

    @Test
    fun cancelDragIfFrameChanged_cancelsAnyDragWhoseFrameDiffers() {
        val shifted = metrics.copy(restOffsetYPx = 24f)
        val move = moving().updateMove(valid(1, 1))
        assertEquals(move, move.cancelDragIfFrameChanged(metrics))
        val cancelledMove = move.cancelDragIfFrameChanged(shifted)
        assertEquals(DragState.None, cancelledMove.drag)
        assertEquals(move.selectedTileId, cancelledMove.selectedTileId)
        val resize = resizing()
        assertEquals(resize, resize.cancelDragIfFrameChanged(metrics))
        val cancelledResize = resize.cancelDragIfFrameChanged(shifted)
        assertEquals(DragState.None, cancelledResize.drag)
        assertTrue("arm survives cancel", cancelledResize.resizeArmed)
    }

    @Test
    fun onTileRemoved_dropsSelectionArmAndDrag() {
        val s = moving().onTileRemoved(ItemId("a"))
        assertNull(s.selectedTileId)
        assertEquals(DragState.None, s.drag)
        assertTrue(s.isEditing)
        assertEquals(editing().toggleResizeArmed().selectedTileId, ItemId("a"))
        assertEquals(ItemId("a"), editing().onTileRemoved(ItemId("b")).selectedTileId)
    }

    // ---- ported from the retired HomeEditSession / HomeInteractionController suites ----

    @Test
    fun resizeSequence_validBlockedAndOutOfBounds_neverChangesPosition() {
        val t = tile("app-a", column = 4, row = 3)
        val obstacle = tile("app-b", column = 4, row = 5)
        val placements = listOf(t, obstacle)
        var s = HomeEditState.Normal
            .enterOnLongPress(t.itemId)
            .toggleResizeArmed()
            .beginResize(t, placements, metrics)
        for (span in listOf(LogicalSpan(2, 2), LogicalSpan(1, 3), LogicalSpan(3, 1), LogicalSpan(1, 1))) {
            s = s.updateResize(span)
            assertEquals(t.position, s.resize().position)
        }
        // 1x3 collides with the obstacle two rows below; 3x1 overflows six columns.
        assertEquals(CandidateValidity.VALID, s.resize().validity)
    }

    @Test
    fun resize_collisionAndEdge_reportValidity() {
        val t = tile("app-a", column = 5, row = 0)
        val neighbour = tile("app-b", column = 1, row = 2)
        val edge = HomeEditState.Normal.enterOnLongPress(t.itemId).toggleResizeArmed()
            .beginResize(t, listOf(t, neighbour), metrics)
            .updateResize(LogicalSpan(2, 1))
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, edge.resize().validity)
        assertEquals(t.span, edge.resize().previewSpan)
        assertNull(edge.finish().commit)

        val a = tile("app-a")
        val b = tile("app-b", column = 1)
        val blocked = HomeEditState.Normal.enterOnLongPress(a.itemId).toggleResizeArmed()
            .beginResize(a, listOf(a, b), metrics)
            .updateResize(LogicalSpan(2, 1))
        assertEquals(CandidateValidity.COLLIDING, blocked.resize().validity)
        assertNull(blocked.finish().commit)
    }

    @Test
    fun resize_2x2to3x2In5Columns_commitsPreview() {
        val five = metrics.copy(columns = 5)
        val t = tile("app-a", columns = 2, rows = 2)
        val s = HomeEditState.Normal.enterOnLongPress(t.itemId).toggleResizeArmed()
            .beginResize(t, listOf(t), five)
            .updateResize(LogicalSpan(3, 2))
        assertEquals(HomeEditCommit.Resize(t.itemId, LogicalSpan(3, 2)), s.finish().commit)
        val tooWide = s.updateResize(LogicalSpan(6, 2))
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, tooWide.resize().validity)
        assertEquals(LogicalSpan(3, 2), tooWide.resize().previewSpan)
    }

    @Test
    fun updatesForTheOtherDragKind_areNoOps() {
        val moving = moving()
        assertEquals(moving, moving.updateResize(LogicalSpan(2, 2)))
        val resizing = resizing()
        assertEquals(resizing, resizing.updateMove(valid(1, 1)))
        assertEquals(resizing, resizing.moveTo(250f, 120f, 0f))
        assertEquals(HomeEditState.Normal, HomeEditState.Normal.updateMove(valid(1, 1)))
    }

    @Test
    fun enterOnLongPress_retargetsAndClearsArm() {
        val armed = editing("a").toggleResizeArmed()
        val retargeted = armed.enterOnLongPress(ItemId("b"))
        assertEquals(ItemId("b"), retargeted.selectedTileId)
        assertTrue(!retargeted.resizeArmed)
    }

    @Test
    fun toggleResizeArmed_armsThenDisarms_andIsInertWithoutSelection() {
        val armed = editing().toggleResizeArmed()
        assertTrue(armed.resizeArmed)
        assertTrue(!armed.toggleResizeArmed().resizeArmed)
        assertEquals(HomeEditState.Normal, HomeEditState.Normal.toggleResizeArmed())
        val noSelection = editing().onTileRemoved(ItemId("a"))
        assertEquals(noSelection, noSelection.toggleResizeArmed())
    }

    @Test
    fun exit_isIdempotentAndClearsEverything() {
        val s = resizing()
        assertEquals(HomeEditState.Normal, s.exit())
        assertEquals(HomeEditState.Normal, s.exit().exit())
    }

    @Test
    fun onTileRemoved_otherTile_keepsSelectionArmAndDrag() {
        val s = resizing()
        assertEquals(s, s.onTileRemoved(ItemId("b")))
        assertEquals(s.exit(), s.exit().onTileRemoved(ItemId("a")))
    }

    @Test
    fun cancelDrag_keepsSelectionAndArm_withoutCommit() {
        val s = resizing().updateResize(LogicalSpan(2, 1)).cancelDrag()
        assertEquals(DragState.None, s.drag)
        assertEquals(ItemId("a"), s.selectedTileId)
        assertTrue(s.resizeArmed)
        assertNull(s.finish().commit)
    }

    @Test
    fun renderedSpan_usesPreviewOnlyForResizedTile() {
        val s = resizing().updateResize(LogicalSpan(2, 2))
        assertEquals(LogicalSpan(2, 2), s.renderedSpan(ItemId("a"), LogicalSpan(1, 1)))
        assertEquals(LogicalSpan(1, 1), s.renderedSpan(ItemId("b"), LogicalSpan(1, 1)))
        assertEquals(LogicalSpan(1, 1), moving().renderedSpan(ItemId("a"), LogicalSpan(1, 1)))
    }

    // ── P1 ticker integration: stationary remap via HomeEditState.moveTo ─

    private fun tickerTile(id: String, column: Int = 0, row: Int = 0) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )

    private fun tickerMetrics() = HomeGridMetrics(
        columns = 6,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = 0f,
        restOffsetYPx = 0f,
    )

    private fun enteredFrom(t: HomeRenderTile, tiles: List<HomeRenderTile>): HomeEditState =
        HomeEditState.Normal
            .enterOnLongPress(t.itemId)
            .beginMove(t, tiles, tickerMetrics(), 0f, 0f)

    private fun remap(
        state: HomeEditState,
        x: Float,
        y: Float,
        scroll: Int,
    ) = state.moveTo(
        pointerXPx = x,
        pointerYPxViewport = y,
        scrollYPx = scroll.toFloat(),
    )

    private fun HomeEditState.movePreview() = (drag as DragState.Move)

    @Test
    fun tickerRemap_stationaryHold_advancingScroll_updatesCandidate() {
        val tiles = listOf(tickerTile("a", 0, 0), tickerTile("b", 2, 2))
        val entered = enteredFrom(tiles[1], tiles)
        val atRest = remap(entered, 250f, 1700f, scroll = 0)
        val scrolled = remap(atRest, 250f, 1700f, scroll = 108)
        assertEquals(LogicalPosition(2, 15), atRest.movePreview().previewPosition)
        assertEquals(LogicalPosition(2, 16), scrolled.movePreview().previewPosition)
        assertEquals(
            HomeEditCommit.Move(ItemId("b"), LogicalPosition(2, 16)),
            scrolled.finish().commit,
        )
    }

    @Test
    fun tickerRemap_blockedPostScrollCell_holdsLastValid() {
        val tiles = listOf(tickerTile("a", 0, 0), tickerTile("b", 2, 2))
        val entered = enteredFrom(tiles[0], tiles)
        val atRest = remap(entered, 50f, 50f, scroll = 0)
        // content y 50 + 200 = 250 → row 2, column 2 → tile b's cell.
        val scrolled = remap(atRest, 250f, 50f, scroll = 200)
        assertEquals(CandidateValidity.COLLIDING, scrolled.movePreview().validity)
        assertEquals(atRest.movePreview().previewPosition, scrolled.movePreview().previewPosition)
    }

    @Test
    fun tickerRemap_pastEdge_isRejectedNotClamped_keepsLastValid() {
        val tiles = listOf(tickerTile("a", 0, 0))
        val entered = enteredFrom(tiles[0], tiles)
        val atRest = remap(entered, 150f, 50f, scroll = 0)
        val past = remap(atRest, 650f, 50f, scroll = 0)

        assertEquals(LogicalPosition(1, 0), past.movePreview().previewPosition)
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, past.movePreview().validity)
        assertEquals(null, past.finish().commit)
    }

    // ── release-only commit / zero-write cancel ─────────────────────────

    private fun movingWithSnapshot(
        t: HomeRenderTile = a,
        snapshot: List<HomeRenderTile>,
    ) = editing(t.itemId.value).beginMove(t, snapshot, metrics, 10f, 10f)

    @Test
    fun releaseCommit_singlePositionFromLastValid() {
        // Scrolling/edge steps never commit: only finish() at release yields
        // a position, exactly once, from the last valid cell.
        val tB = tile("b", column = 2, row = 2)
        val snapshot = listOf(a, tB)
        val state = movingWithSnapshot(tB, snapshot).updateMove(
            MoveCellResult.Valid(RequestedCell(3, 3), LogicalPosition(3, 3)),
        )
        assertEquals(HomeEditCommit.Move(ItemId("b"), LogicalPosition(3, 3)), state.finish().commit)
    }

    @Test
    fun cancel_zeroWrite_noCommitPosition() {
        val tB = tile("b", column = 2, row = 2)
        val snapshot = listOf(a, tB)
        val state = movingWithSnapshot(tB, snapshot).updateMove(
            MoveCellResult.Valid(RequestedCell(3, 3), LogicalPosition(3, 3)),
        )
        // Cancel (back/empty-tap/pager steal): no drag carries no commit.
        assertNull(state.cancelDrag().finish().commit)
    }

    @Test
    fun noMoveRelease_noCommitPosition() {
        // A drag that never leaves the origin commits nothing.
        assertNull(moving(a).finish().commit)
    }
}
