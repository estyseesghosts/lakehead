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
 * ([releaseMoveCommit]/[releaseResizeCommit]/[shouldInitMoveFrontier]) with
 * session-machine + ticker-remap states — not just the remap primitive:
 *
 * - P1: a stationary-hold-then-release (ticker scrolls with no further pointer
 *   events) commits the post-scroll cell from the live session, never the
 *   handler local's pre-scroll cell.
 * - P2: hiding the page mid-gesture aborts both MOVE and RESIZE with zero
 *   writes, and a cancelled (Idle) live session never commits through a stale
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

    // ── P1: stationary-hold-then-release commits the post-scroll cell ──

    @Test
    fun stationaryHoldThenRelease_commitsPostScrollCell_notPreScroll() {
        // Pointer loop state: slop event mapped the held pointer at scroll 0
        // (pre-scroll cell), then the ticker advanced one stride with no
        // further pointer events. The handler local still holds the pre-scroll
        // session; the live ref holds the ticker-remapped session.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        val entered: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        val handlerStale = remapMoveCandidateAfterScroll(
            session = entered,
            pointerXPx = 250f,
            pointerYPx = 1700f,
            scrollPx = 0,
            cellSizePx = 100,
            columnGapPx = 0,
            rowGapPx = 8,
            rowOrigin = 0L,
            originXPx = 0,
            originYPx = 0L,
            grabOffsetXPx = 0f,
            grabOffsetYPx = 0f,
            tiles = tiles,
            columns = 6,
            moveRows = 30,
        )
        val livePostScroll = remapMoveCandidateAfterScroll(
            session = handlerStale,
            pointerXPx = 250f,
            pointerYPx = 1700f,
            scrollPx = 108,
            cellSizePx = 100,
            columnGapPx = 0,
            rowGapPx = 8,
            rowOrigin = 0L,
            originXPx = 0,
            originYPx = 0L,
            grabOffsetXPx = 0f,
            grabOffsetYPx = 0f,
            tiles = tiles,
            columns = 6,
            moveRows = 30,
        )
        // The stale local alone would commit the pre-scroll cell (the bug).
        assertEquals(LogicalPosition(2, 15), handlerStale.moveCommitPosition())
        // The release path reads the live session: post-scroll cell.
        assertEquals(
            LogicalPosition(2, 16),
            releaseMoveCommit(
                handlerSession = handlerStale,
                liveSession = livePostScroll,
                stolen = false,
                isVisible = true,
            ),
        )
    }

    @Test
    fun moveRelease_noTickerProgress_commitsHandlerCell() {
        // No auto-scroll happened: live and handler agree, release commits it.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        assertEquals(
            LogicalPosition(3, 3),
            releaseMoveCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
            ),
        )
    }

    // ── P2: hide aborts in-flight gestures with zero writes ──

    @Test
    fun hiddenMoveRelease_suppressesCommit_zeroWrite() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        assertNull(
            releaseMoveCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = false,
            ),
        )
    }

    @Test
    fun hiddenResizeRelease_suppressesCommit_zeroWrite() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 0, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.beginResize(armedAtDown = true)
        session = session.updateResizeCandidate(LogicalSpan(2, 2), tiles, 6, 30)
        // Sanity: visible release would commit the grown span.
        assertEquals(
            LogicalSpan(2, 2),
            releaseResizeCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
            ),
        )
        // Hidden: zero writes.
        assertNull(
            releaseResizeCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = false,
            ),
        )
    }

    @Test
    fun cancelledLiveSession_staleHandlerLocal_neverCommits() {
        // Hide/back/empty-tap clears the live ref to Idle; if the cancelled
        // await still unwinds to release with its stale Active local, the
        // release path must still write nothing.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var handlerStale: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        handlerStale = handlerStale.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        val liveCancelled: HomeEditSession = HomeEditSession.Idle
        assertNull(
            releaseMoveCommit(
                handlerSession = handlerStale,
                liveSession = liveCancelled,
                stolen = false,
                isVisible = true,
            ),
        )
    }

    @Test
    fun moveCancellationThroughLoop_activeRef_zeroWrite() {
        // B3 re-review P1: the loop exited via null/consumed/cancel — not an
        // explicit pointer-up — while the live ref still holds an Active
        // session and the page is visible. The release path must write
        // nothing (never call onMoveTile through this helper).
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        // Sanity: an explicit lift with the same Active session commits.
        assertEquals(
            LogicalPosition(3, 3),
            releaseMoveCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
                completedByUp = true,
            ),
        )
        // Cancelled loop exit with the identical Active ref: zero writes.
        assertNull(
            releaseMoveCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
                completedByUp = false,
            ),
        )
    }

    @Test
    fun resizeCancellationThroughLoop_activeRef_zeroWrite() {
        // Same cancelled-through-loop contract for RESIZE: Active live span,
        // visible page, but no explicit lift → zero writes.
        val tiles = listOf(tile("a", 0, 0), tile("b", 0, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.beginResize(armedAtDown = true)
        session = session.updateResizeCandidate(LogicalSpan(2, 2), tiles, 6, 30)
        // Sanity: an explicit lift with the same Active session commits.
        assertEquals(
            LogicalSpan(2, 2),
            releaseResizeCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
                completedByUp = true,
            ),
        )
        // Cancelled loop exit with the identical Active ref: zero writes.
        assertNull(
            releaseResizeCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
                completedByUp = false,
            ),
        )
    }

    @Test
    fun stolenMoveRelease_suppressesCommit() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        assertNull(
            releaseMoveCommit(
                handlerSession = session,
                liveSession = session,
                stolen = true,
                isVisible = true,
            ),
        )
    }

    @Test
    fun stolenResizeRelease_suppressesCommit() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 0, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.beginResize(armedAtDown = true)
        session = session.updateResizeCandidate(LogicalSpan(2, 2), tiles, 6, 30)
        assertNull(
            releaseResizeCommit(
                handlerSession = session,
                liveSession = session,
                stolen = true,
                isVisible = true,
            ),
        )
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
        // path (completedByUp = false), which writes nothing even with an
        // Active live session on a visible page.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        val nullDecision = classifyPointerEnd(changeNull = true, pressed = true, consumed = false)
        val consumedDecision = classifyPointerEnd(changeNull = false, pressed = true, consumed = true)
        assertEquals(PointerEndDecision.CANCEL, nullDecision)
        assertEquals(PointerEndDecision.CANCEL, consumedDecision)
        for (decision in listOf(nullDecision, consumedDecision)) {
            val completedByUp = decision == PointerEndDecision.LIFT
            assertNull(
                releaseMoveCommit(
                    handlerSession = session,
                    liveSession = session,
                    stolen = false,
                    isVisible = true,
                    completedByUp = completedByUp,
                ),
            )
        }
        // And a LIFT decision on the same session commits.
        assertEquals(
            LogicalPosition(3, 3),
            releaseMoveCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
                completedByUp = classifyPointerEnd(
                    changeNull = false,
                    pressed = false,
                    consumed = false,
                ) == PointerEndDecision.LIFT,
            ),
        )
    }

    @Test
    fun classifyPointerEnd_cancelDecision_suppressesResizeCommit() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 0, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.beginResize(armedAtDown = true)
        session = session.updateResizeCandidate(LogicalSpan(2, 2), tiles, 6, 30)
        val decision = classifyPointerEnd(changeNull = false, pressed = true, consumed = true)
        assertEquals(PointerEndDecision.CANCEL, decision)
        assertNull(
            releaseResizeCommit(
                handlerSession = session,
                liveSession = session,
                stolen = false,
                isVisible = true,
                completedByUp = decision == PointerEndDecision.LIFT,
            ),
        )
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
}
