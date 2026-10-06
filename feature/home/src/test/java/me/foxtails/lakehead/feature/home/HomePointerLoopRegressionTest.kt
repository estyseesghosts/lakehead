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
 * ([HomeEditState.finish] gated by [releaseCommit], [shouldInitMoveFrontier])
 * with ticker-remap states — not just the remap primitive:
 *
 * - P1: a stationary-hold-then-release (ticker scrolls with no further
 *   pointer events) commits the post-scroll cell from the one edit state.
 * - P2: hiding the page mid-gesture aborts both MOVE and RESIZE with zero
 *   writes, and a cleared (exited) state never commits through a stale
 *   handler local.
 * - P2: the transient frontier initializes for MOVE only — RESIZE never
 *   creates it.
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
            tiles,
        )

    private fun resized(): HomeEditState = HomeEditState.Normal
        .enterOnLongPress(ItemId("b"))
        .toggleResizeArmed()
        .beginResize(resizeTiles[1], metrics())
        .updateResize(LogicalSpan(2, 2), resizeTiles)

    private fun HomeEditState.releaseMove(
        stolen: Boolean = false,
        isVisible: Boolean = true,
        completedByUp: Boolean = true,
    ): LogicalPosition? = (
        releaseCommit(finish(), stolen, isVisible, completedByUp) as? HomeEditCommit.Move
        )?.position

    private fun HomeEditState.releaseResize(
        stolen: Boolean = false,
        isVisible: Boolean = true,
        completedByUp: Boolean = true,
    ): LogicalSpan? = (
        releaseCommit(finish(), stolen, isVisible, completedByUp) as? HomeEditCommit.Resize
        )?.span

    // ── P1: stationary-hold-then-release commits the post-scroll cell ──

    @Test
    fun stationaryHoldThenRelease_commitsPostScrollCell_notPreScroll() {
        // Slop event mapped the held pointer at scroll 0 (pre-scroll cell),
        // then the ticker advanced one stride with no further pointer events.
        // Both write the one edit state, so release sees the ticker's cell.
        val atSlop = moving().moveTo(250f, 1700f, 0f, tiles)
        assertEquals(LogicalPosition(2, 15), atSlop.move().previewPosition)

        val afterTicker = atSlop.moveTo(250f, 1700f, 108f, tiles)

        assertEquals(LogicalPosition(2, 16), afterTicker.releaseMove())
    }

    @Test
    fun moveTo_isPureInPointerAndScroll_eventPathAndTickerAgree() {
        // The drag event loop and the stationary ticker both call moveTo; for
        // identical pointer + scroll they land on the same state regardless of
        // what the other did before, including a scrolled-during-hold delta.
        val viaEventOnly = moving().moveTo(250f, 1700f, 108f, tiles)
        val viaTickerAfterEvent = moving()
            .moveTo(250f, 1700f, 0f, tiles)
            .moveTo(250f, 1700f, 108f, tiles)
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
    fun stolenMoveRelease_suppressesCommit() {
        assertNull(movedTo(3, 3).releaseMove(stolen = true))
    }

    @Test
    fun stolenResizeRelease_suppressesCommit() {
        assertNull(resized().releaseResize(stolen = true))
    }

    @Test
    fun release_alwaysClearsTheDrag_evenWhenSuppressed() {
        val state = movedTo(3, 3)
        assertEquals(DragState.None, state.finish().state.drag)
        assertEquals(ItemId("b"), state.finish().state.selectedTileId)
    }

    // ── P2: RESIZE never creates the MOVE frontier ──

    @Test
    fun frontierInit_moveRouteWithoutFrontier_initializes() {
        assertEquals(true, shouldInitMoveFrontier(DownDragRoute.MOVE, null))
    }

    @Test
    fun frontierInit_resizeRoute_neverInitializes() {
        // Armed RESIZE drags keep the normal bound: no frontier, no expanded
        // layout retained after resize.
        assertEquals(false, shouldInitMoveFrontier(DownDragRoute.RESIZE, null))
    }

    @Test
    fun frontierInit_noneRoute_neverInitializes() {
        assertEquals(false, shouldInitMoveFrontier(DownDragRoute.NONE, null))
    }

    @Test
    fun frontierInit_retainedFrontier_neverReinitializes() {
        val retained = initEditFrontier(extent(2L, 2L), usableHeightPx = 1800, rowStridePx = 108L)
        assertEquals(false, shouldInitMoveFrontier(DownDragRoute.MOVE, retained))
        assertEquals(false, shouldInitMoveFrontier(DownDragRoute.RESIZE, retained))
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

    // ── P2: pending MOVE lease survives hide, reconciles vs loaded ──

    @Test
    fun pendingLease_unsatisfiedStaleLoad_retained_blocksSecondMove() {
        // Leaving/returning before the commit's reload lands: loaded tiles
        // still show the old cell, so the lease is retained and the MOVE
        // entry gate stays closed (isPendingCommitSatisfied false).
        val pending = PendingMoveCommit(ItemId("b"), LogicalPosition(3, 3))
        val staleTiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        assertEquals(pending, retainedPendingLease(staleTiles, pending))
        assertEquals(false, isPendingCommitSatisfied(staleTiles, pending))
    }

    @Test
    fun pendingLease_loadReflectsTarget_satisfied_clearsLease() {
        // The async reload lands (same or post-hide): loaded shows the target,
        // the lease clears and the gate reopens.
        val pending = PendingMoveCommit(ItemId("b"), LogicalPosition(3, 3))
        val loadedTiles = listOf(tile("a", 0, 0), tile("b", 3, 3))
        assertNull(retainedPendingLease(loadedTiles, pending))
        assertEquals(true, isPendingCommitSatisfied(loadedTiles, pending))
        assertNull(retainedPendingLease(loadedTiles, null))
    }

    // ── B3 P2 steal cleanup: lease exits ──

    @Test
    fun leaseExit_moveCancel_clearsLease() {
        // The MOVE cancel site passes (cancelled=true): the explicit cancel
        // releases the lease alongside the frontier with zero writes.
        assertTrue(shouldClearPendingLeaseOnExit(cancelled = true, stolen = false))
    }

    @Test
    fun leaseExit_moveSteal_clearsLease() {
        // The MOVE steal site passes (stolen=true): a pager/scroll steal is
        // an explicit failure and releases the lease with the frontier,
        // while the release commit itself stays zero-write.
        assertTrue(shouldClearPendingLeaseOnExit(cancelled = false, stolen = true))
    }

    @Test
    fun leaseExit_resizeCancel_clearsLease() {
        // Same shared exit decision as the MOVE path (single source): the
        // RESIZE cancel site passes (cancelled=true, stolen=false) and
        // releases a retained lease while leaving the frontier untouched
        // (RESIZE never owns it).
        assertTrue(shouldClearPendingLeaseOnExit(cancelled = true, stolen = false))
    }

    @Test
    fun leaseExit_resizeSteal_clearsLease() {
        // The RESIZE steal branch passes (cancelled=false, stolen=true):
        // a steal releases the lease; a hide or stale break in the same
        // branch passes stolen=false and retains it.
        assertTrue(shouldClearPendingLeaseOnExit(cancelled = false, stolen = true))
    }

    @Test
    fun leaseExit_pagerHide_retainsLeaseForRebase() {
        // Hide maps to (cancelled=false, stolen=false) at both MOVE and
        // RESIZE sites: the gesture/frontier/edge/hold state is discarded
        // but the lease survives, so a leave-before-loaded cannot admit a
        // stale second move on return. End-to-end through the reconcile:
        // a stale load retains the lease and keeps the MOVE entry gate
        // closed until loaded confirms the target.
        assertFalse(shouldClearPendingLeaseOnExit(cancelled = false, stolen = false))
        val pending = PendingMoveCommit(ItemId("b"), LogicalPosition(3, 3))
        val staleTiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        assertEquals(pending, retainedPendingLease(staleTiles, pending))
        assertFalse(isPendingCommitSatisfied(staleTiles, pending))
    }

    @Test
    fun leaseExit_staleBreak_retainsLeaseForRebase() {
        // A loop break with no lift and no cancel/steal flags (stale live
        // ref cleared concurrently) maps to (false, false): the frontier is
        // discarded with the gesture but the lease is retained for the
        // rebase reconcile, which clears it once loaded reflects the target.
        assertFalse(shouldClearPendingLeaseOnExit(cancelled = false, stolen = false))
        val pending = PendingMoveCommit(ItemId("b"), LogicalPosition(3, 3))
        val loadedTiles = listOf(tile("a", 0, 0), tile("b", 3, 3))
        assertNull(retainedPendingLease(loadedTiles, pending))
        assertTrue(isPendingCommitSatisfied(loadedTiles, pending))
    }

    // ── Layout drift: only the rebase bridge may change a drag's metrics ──

    @Test
    fun rebase_columnsShrink_remapsSamePointerKeepingGrabOffsets() {
        // Six-col metrics, pointer centers cell (2,15) at scroll 0. Narrowing
        // to five columns adopts the new metrics and remaps the same pointer;
        // the down-captured grab offsets stay.
        val state = moving(grabX = 10f, grabY = 20f)
        val rebased = state.rebaseAndMoveTo(metrics(columns = 5), 250f, 1700f, 0f, tiles)
        assertEquals(5, rebased.move().metrics.columns)
        assertEquals(10f, rebased.move().grabOffsetXPx)
        assertEquals(20f, rebased.move().grabOffsetYPx)
        assertEquals(tiles[1].span, rebased.move().span)
    }

    @Test
    fun rebase_unchangedMetrics_returnsSameState() {
        val state = moving().moveTo(250f, 1700f, 0f, tiles)
        assertEquals(state, state.rebaseAndMoveTo(metrics(), 250f, 1700f, 108f, tiles))
    }

    @Test
    fun rebase_scrollOnly_keepsFrameWithLiveDelta() {
        // One stride of scroll advances one row through the same frame.
        val atRest = moving().moveTo(250f, 1700f, 0f, tiles)
        val scrolled = moving().moveTo(250f, 1700f, 108f, tiles)
        assertEquals(LogicalPosition(2, 15), atRest.move().previewPosition)
        assertEquals(LogicalPosition(2, 16), scrolled.move().previewPosition)
        assertEquals(atRest.move().metrics, scrolled.move().metrics)
    }

    @Test
    fun rebase_originChange_remapsOntoActualScrollAndPreservesGrab() {
        val state = moving(grabX = 10f, grabY = 20f)
        val rebased = state.rebaseAndMoveTo(metrics(originX = 50f), 250f, 1700f, 216f, tiles)
        assertEquals(50f, rebased.move().metrics.originXPx)
        assertEquals(10f, rebased.move().grabOffsetXPx)
        assertEquals(20f, rebased.move().grabOffsetYPx)
        assertEquals(LogicalPosition(1, 17), rebased.move().previewPosition)
    }

    @Test
    fun workspaceRowBoundDoesNotChangeMoveCandidate() {
        // The old finite frontier clamp is no longer part of legality: the
        // same pointer maps to the same absolute row regardless of metadata.
        assertEquals(
            LogicalPosition(2, 15),
            moving().moveTo(250f, 1700f, 0f, tiles).move().previewPosition,
        )
    }

    @Test
    fun rebase_blockedRemap_preservesLastValid() {
        // Re-mapping onto a cell occupied by another tile: validity reports
        // the collision while the preview holds the last valid position.
        val three = listOf(tile("a", 0, 0), tile("b", 2, 2), tile("c", 3, 0))
        val atValid = moving(three).updateMove(
            MoveCellResult.Valid(RequestedCell(1, 0), LogicalPosition(1, 0)),
            three,
        )
        val rebased = atValid.rebaseAndMoveTo(metrics(originX = 1f), 350f, 50f, 0f, three)
        assertEquals(CandidateValidity.COLLIDING, rebased.move().validity)
        assertEquals(LogicalPosition(1, 0), rebased.move().previewPosition)
        assertNull(rebased.releaseMove())
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
            .moveTo(550f, 50f, 0f, placements)
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, state.move().validity)
        assertEquals(wide.position, state.move().previewPosition)
        assertEquals(LogicalSpan(2, 1), state.move().span)
        assertNull(state.releaseMove())
    }

    private fun HomeEditState.move(): DragState.Move = drag as DragState.Move
}
