package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 re-review pointer-loop regressions for the home canvas gesture release path.
 *
 * These tests exercise the exact release/commit gating the pointer loop calls
 * ([HomeEditState.finish] gated by [releaseCommit])
 * with ticker-remap states — not just the remap primitive:
 *
 * - P1: a stationary-hold-then-release (ticker scrolls with no further
 *   pointer events) commits the post-scroll cell from the one edit state.
 * - P2: hiding the page mid-gesture aborts both MOVE and RESIZE with zero
 *   writes, and a cleared (exited) state never commits through a stale
 *   handler local.
 */
class HomePointerLoopRegressionTest {

    private val profile = ProfileId("personal")
    private val identity = AppIdentity(profile, ComponentIdentity("com.app", "Main"))

    private fun tile(id: String, column: Int = 0, row: Int = 0) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )

    private fun extent(bottomExclusive: Long, totalRows: Long) = GridVerticalExtent(
        occupiedBottomRowExclusive = bottomExclusive,
        totalScrollableRows = totalRows,
        occupiedMinRow = 0L,
        occupiedMaxRowExclusive = bottomExclusive,
        occupiedHeightRows = totalRows,
    )

    private fun metrics(columns: Int = 6, originX: Float = 0f) = HomeGridMetrics(
        columns = columns,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = originX,
        restOffsetYPx = 0f,
    )

    private val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
    private val resizeTiles = listOf(tile("a", 0, 0), tile("b", 0, 2))

    private fun moving(
        placements: List<HomeRenderTile> = tiles,
        metrics: HomeGridMetrics = metrics(),
        grabX: Float = 0f,
        grabY: Float = 0f,
    ): HomeEditState = HomeEditState.Normal
        .enterOnLongPress(ItemId("b"))
        .beginMove(placements[1], placements, metrics, grabX, grabY)

    private fun movedTo(column: Int, row: Int): HomeEditState =
        moving().updateMove(
            MoveCellResult.Valid(
                RequestedCell(column.toLong(), row.toLong()),
                LogicalPosition(column, row),
            ),
        )

    private fun resized(): HomeEditState = HomeEditState.Normal
        .enterOnLongPress(ItemId("b"))
        .toggleResizeArmed()
        .beginResize(resizeTiles[1], resizeTiles, metrics())
        .updateResize(LogicalSpan(2, 2))

    private fun HomeEditState.releaseMove(
        isVisible: Boolean = true,
        completedByUp: Boolean = true,
    ): LogicalPosition? = (
        releaseCommit(finish(), isVisible, completedByUp) as? HomeEditCommit.Move
        )?.position

    private fun HomeEditState.releaseResize(
        isVisible: Boolean = true,
        completedByUp: Boolean = true,
    ): LogicalSpan? = (
        releaseCommit(finish(), isVisible, completedByUp) as? HomeEditCommit.Resize
        )?.span

    // ── P1: stationary-hold-then-release commits the post-scroll cell ──

    @Test
    fun stationaryHoldThenRelease_commitsPostScrollCell_notPreScroll() {
        // Slop event mapped the held pointer at scroll 0 (pre-scroll cell),
        // then the ticker advanced one stride with no further pointer events.
        // Both write the one edit state, so release sees the ticker's cell.
        val atSlop = moving().moveTo(250f, 1700f, 0f)
        assertEquals(LogicalPosition(2, 15), atSlop.move().previewPosition)

        val afterTicker = atSlop.moveTo(250f, 1700f, 108f)

        assertEquals(LogicalPosition(2, 16), afterTicker.releaseMove())
    }

    @Test
    fun moveTo_isPureInPointerAndScroll_eventPathAndTickerAgree() {
        // The drag event loop and the stationary ticker both call moveTo; for
        // identical pointer + scroll they land on the same state regardless of
        // what the other did before, including a scrolled-during-hold delta.
        val viaEventOnly = moving().moveTo(250f, 1700f, 108f)
        val viaTickerAfterEvent = moving()
            .moveTo(250f, 1700f, 0f)
            .moveTo(250f, 1700f, 108f)
        assertEquals(viaEventOnly, viaTickerAfterEvent)
        assertEquals(LogicalPosition(2, 16), viaEventOnly.move().previewPosition)
    }

    @Test
    fun moveRelease_noTickerProgress_commitsHandlerCell() {
        assertEquals(LogicalPosition(3, 3), movedTo(3, 3).releaseMove())
    }

    // ── P2: hide aborts in-flight gestures with zero writes ──

    @Test
    fun hiddenMoveRelease_suppressesCommit_zeroWrite() {
        assertNull(movedTo(3, 3).releaseMove(isVisible = false))
    }

    @Test
    fun hiddenResizeRelease_suppressesCommit_zeroWrite() {
        // Sanity: visible release would commit the grown span.
        assertEquals(LogicalSpan(2, 2), resized().releaseResize())
        assertNull(resized().releaseResize(isVisible = false))
    }

    @Test
    fun exitedState_neverCommits() {
        // Hide/back/empty-tap exits the state; if the cancelled await still
        // unwinds to release, the exited state has no drag and writes nothing.
        val exited = movedTo(3, 3).exit()
        assertNull(exited.releaseMove())
        assertEquals(HomeEditState.Normal, exited)
    }

    @Test
    fun moveCancellationThroughLoop_zeroWrite() {
        // The loop exited via null/consumed/cancel — not an explicit pointer-up —
        // while the state still holds a drag and the page is visible. The
        // release path must write nothing.
        val state = movedTo(3, 3)
        assertEquals(LogicalPosition(3, 3), state.releaseMove(completedByUp = true))
        assertNull(state.releaseMove(completedByUp = false))
    }

    @Test
    fun resizeCancellationThroughLoop_zeroWrite() {
        val state = resized()
        assertEquals(LogicalSpan(2, 2), state.releaseResize(completedByUp = true))
        assertNull(state.releaseResize(completedByUp = false))
    }

    @Test
    fun release_alwaysClearsTheDrag_evenWhenSuppressed() {
        val state = movedTo(3, 3)
        assertEquals(DragState.None, state.finish().state.drag)
        assertEquals(ItemId("b"), state.finish().state.selectedTileId)
    }

    // ── P2: pure pointer-end decision drives the null/consumed branches ──

    @Test
    fun classifyPointerEnd_nullChange_isCancel_neverLift() {
        // Lost pointer id (await finds no change for downId): the loop must
        // cancel with zero writes, never classify as a lift toward commit.
        assertEquals(
            PointerEndDecision.CANCEL,
            classifyPointerEnd(changeNull = true, pressed = false, consumed = false),
        )
        assertEquals(
            PointerEndDecision.CANCEL,
            classifyPointerEnd(changeNull = true, pressed = true, consumed = false),
        )
    }

    @Test
    fun classifyPointerEnd_consumedChange_isCancel_neverLift() {
        // Consumed change (another consumer stole the gesture): cancel even
        // though the pointer is still pressed — classifying this as a lift
        // would commit a stolen drag.
        assertEquals(
            PointerEndDecision.CANCEL,
            classifyPointerEnd(changeNull = false, pressed = true, consumed = true),
        )
    }

    @Test
    fun classifyPointerEnd_observedLift_isLift() {
        assertEquals(
            PointerEndDecision.LIFT,
            classifyPointerEnd(changeNull = false, pressed = false, consumed = false),
        )
    }

    @Test
    fun classifyPointerEnd_liftPrecedence_overConsumed() {
        // Matches loop order (pressed check before consumed check): a lifted
        // change still completes toward commit gating even if marked consumed.
        assertEquals(
            PointerEndDecision.LIFT,
            classifyPointerEnd(changeNull = false, pressed = false, consumed = true),
        )
    }

    @Test
    fun classifyPointerEnd_pressedUnconsumed_continues() {
        assertEquals(
            PointerEndDecision.CONTINUE,
            classifyPointerEnd(changeNull = false, pressed = true, consumed = false),
        )
    }

    @Test
    fun classifyPointerEnd_cancelDecision_suppressesMoveCommit() {
        // End-to-end: every CANCEL observation maps to the cancelled release
        // path (completedByUp = false), which writes nothing even with a
        // live drag on a visible page.
        val state = movedTo(3, 3)
        val nullDecision = classifyPointerEnd(changeNull = true, pressed = true, consumed = false)
        val consumedDecision = classifyPointerEnd(changeNull = false, pressed = true, consumed = true)
        assertEquals(PointerEndDecision.CANCEL, nullDecision)
        assertEquals(PointerEndDecision.CANCEL, consumedDecision)
        for (decision in listOf(nullDecision, consumedDecision)) {
            assertNull(state.releaseMove(completedByUp = decision == PointerEndDecision.LIFT))
        }
        // And a LIFT decision on the same state commits.
        val lift = classifyPointerEnd(changeNull = false, pressed = false, consumed = false)
        assertEquals(
            LogicalPosition(3, 3),
            state.releaseMove(completedByUp = lift == PointerEndDecision.LIFT),
        )
    }

    @Test
    fun classifyPointerEnd_cancelDecision_suppressesResizeCommit() {
        val decision = classifyPointerEnd(changeNull = false, pressed = true, consumed = true)
        assertEquals(PointerEndDecision.CANCEL, decision)
        assertNull(resized().releaseResize(completedByUp = decision == PointerEndDecision.LIFT))
    }

    // ── Layout drift: a drag keeps its frame or is cancelled, never rebased ──

    @Test
    fun scrollOnly_keepsFrameWithLiveDelta() {
        // One stride of scroll advances one row through the same frame.
        val atRest = moving().moveTo(250f, 1700f, 0f)
        val scrolled = moving().moveTo(250f, 1700f, 108f)
        assertEquals(LogicalPosition(2, 15), atRest.move().previewPosition)
        assertEquals(LogicalPosition(2, 16), scrolled.move().previewPosition)
        assertEquals(atRest.move().metrics, scrolled.move().metrics)
    }

    @Test
    fun moveUpdates_neverChangeTheFrameTheDragBeganIn() {
        val began = moving(grabX = 10f, grabY = 20f)
        val updated = began.moveTo(250f, 1700f, 108f).moveTo(40f, 90f, 0f)
        assertEquals(began.move().metrics, updated.move().metrics)
        assertEquals(10f, updated.move().grabOffsetXPx)
        assertEquals(20f, updated.move().grabOffsetYPx)
    }

    @Test
    fun cancelDragIfFrameChanged_sameFrame_isUnchanged() {
        val state = moving().moveTo(250f, 1700f, 0f)
        assertEquals(state, state.cancelDragIfFrameChanged(metrics()))
    }

    @Test
    fun cancelDragIfFrameChanged_columnsOrOriginChange_cancelsMoveWithoutCommit() {
        listOf(metrics(columns = 5), metrics(originX = 50f)).forEach { changed ->
            val state = moving().moveTo(250f, 1700f, 0f)
            val cancelled = state.cancelDragIfFrameChanged(changed)
            assertEquals(DragState.None, cancelled.drag)
            assertNull(cancelled.finish().commit)
            assertEquals("selection survives", state.selectedTileId, cancelled.selectedTileId)
        }
    }

    @Test
    fun cancelDragIfFrameChanged_withNoDrag_isUnchanged() {
        val idle = HomeEditState.Normal.enterOnLongPress(tiles[0].itemId)
        assertEquals(idle, idle.cancelDragIfFrameChanged(metrics(columns = 5)))
    }

    @Test
    fun workspaceRowBoundDoesNotChangeMoveCandidate() {
        // The old finite frontier clamp is no longer part of legality: the
        // same pointer maps to the same absolute row regardless of metadata.
        assertEquals(
            LogicalPosition(2, 15),
            moving().moveTo(250f, 1700f, 0f).move().previewPosition,
        )
    }

    @Test
    fun spanEdge_isRejectedNotClamped() {
        // A 2-wide tile aimed at the last column used to clamp left into
        // (4, 0). It is an out-of-bounds request that holds the last-valid
        // position, and the span never changes.
        val wide = HomeRenderTile(
            itemId = ItemId("w"),
            appIdentity = identity,
            position = LogicalPosition(2, 2),
            span = LogicalSpan(2, 1),
            availability = ItemAvailability.AVAILABLE,
            label = "w",
            icon = null,
            isLaunchable = true,
        )
        val placements = listOf(tile("a", 0, 0), wide)
        val state = HomeEditState.Normal
            .enterOnLongPress(wide.itemId)
            .beginMove(wide, placements, metrics(), 0f, 0f)
            .moveTo(550f, 50f, 0f)
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, state.move().validity)
        assertEquals(wide.position, state.move().previewPosition)
        assertEquals(LogicalSpan(2, 1), state.move().span)
        assertNull(state.releaseMove())
    }

    private fun HomeEditState.move(): DragState.Move = drag as DragState.Move
}
