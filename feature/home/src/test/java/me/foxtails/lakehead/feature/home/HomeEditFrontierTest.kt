package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.GridVerticalLayout
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
 * B3 transient edit-frontier coverage for the home canvas.
 *
 * - Frontier init/advance/rebase delegate to `core:grid` policy (bounded
 *   margin, single-step growth, never-shrink rebase, 4096 margin cap).
 * - MOVE uses the frontier bound; RESIZE keeps the normal bound (no expansion).
 * - Edit-only layout rows/height/scroll-max size from loaded extent + frontier.
 * - Pending-commit lease gates a second MOVE until loaded reflects the target.
 * - Edge-zone classification for MOVE auto-scroll (top limited by range,
 *   bottom extends first).
 * - Stationary remap primitive: the same viewport-local pointer maps to a new
 *   row when the current scroll offset advances.
 * - Release-only commit and zero-write cancel/pager-steal session semantics.
 * - Exit collapse/clamp mirrors the viewport helpers (no production change).
 */
class HomeEditFrontierTest {

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

    private fun extent(bottomExclusive: Long, totalRows: Long, minRow: Long = 0L) =
        GridVerticalExtent(
            occupiedBottomRowExclusive = bottomExclusive,
            totalScrollableRows = totalRows,
            occupiedMinRow = minRow,
            occupiedMaxRowExclusive = bottomExclusive,
            occupiedHeightRows = totalRows,
        )

    // ── viewportRowsFor ────────────────────────────────────────────────

    @Test
    fun viewportRows_floorsDivision() {
        assertEquals(16L, viewportRowsFor(usableHeightPx = 1800, rowStridePx = 108L))
    }

    @Test
    fun viewportRows_zeroUsable_returnsOne() {
        assertEquals(1L, viewportRowsFor(usableHeightPx = 0, rowStridePx = 108L))
    }

    @Test
    fun viewportRows_subStrideUsable_returnsOne() {
        assertEquals(1L, viewportRowsFor(usableHeightPx = 50, rowStridePx = 108L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun viewportRows_negativeUsable_throws() {
        viewportRowsFor(usableHeightPx = -1, rowStridePx = 108L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun viewportRows_nonPositiveStride_throws() {
        viewportRowsFor(usableHeightPx = 1800, rowStridePx = 0L)
    }

    // ── initEditFrontier ───────────────────────────────────────────────

    @Test
    fun init_shortContent_viewportPlusMargin() {
        // 2 occupied rows in an 1800px viewport (16 rows): 16 + 4 margin.
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        assertEquals(20L, boundary.exclusiveRow)
        assertEquals(HomeEditFrontier.MARGIN_ROWS, boundary.marginRows)
    }

    @Test
    fun init_tallContent_occupiedBottomPlusMargin() {
        // 30 occupied rows overflows a 1000px viewport (9 rows): 30 + 4.
        val boundary = initEditFrontier(extent(30L, 30L), 1000, 108L)
        assertEquals(34L, boundary.exclusiveRow)
    }

    @Test
    fun init_hugeMargin_cappedAtGridBound() {
        // The 4096 cap bounds only the temporary margin, never the grid: the
        // frontier still initializes instead of failing.
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L, marginRows = Long.MAX_VALUE)
        assertEquals(16L + GridVerticalLayout.MAX_WORKING_MARGIN_ROWS, boundary.exclusiveRow)
        assertEquals(GridVerticalLayout.MAX_WORKING_MARGIN_ROWS, boundary.marginRows)
    }

    @Test
    fun init_zeroMargin_noExpansion() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L, marginRows = 0L)
        assertEquals(16L, boundary.exclusiveRow)
    }

    // ── advanceFrontierForMove ─────────────────────────────────────────

    @Test
    fun advance_belowTrailingMargin_unchanged() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        // Exclusive 20, trailing edge 16: a bottom of 10 stays put.
        val advanced = advanceFrontierForMove(boundary, LogicalPosition(0, 9), spanRows = 1)
        assertEquals(boundary, advanced)
    }

    @Test
    fun advance_atTrailingMargin_singleBoundedStep() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        // Bottom exactly at the trailing edge 16 advances one margin only.
        val advanced = advanceFrontierForMove(boundary, LogicalPosition(0, 15), spanRows = 1)
        assertEquals(24L, advanced.exclusiveRow)
        assertEquals(boundary.marginRows, advanced.marginRows)
    }

    @Test
    fun advance_farBeyondFrontier_stillSingleStep() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        // A distant/malformed candidate must not allocate an unbounded area.
        val advanced = advanceFrontierForMove(boundary, LogicalPosition(0, 500), spanRows = 1)
        assertEquals(24L, advanced.exclusiveRow)
    }

    @Test
    fun advance_repeatedSteps_growWithoutBlocking() {
        // The 4096 margin bound never blocks frontier growth: repeated steps
        // keep extending one margin at a time.
        var boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        repeat(3) {
            val bottom = boundary.exclusiveRow
            boundary = advanceFrontierForMove(
                boundary,
                LogicalPosition(0, (bottom - 1L).toInt()),
                spanRows = 1,
            )
        }
        assertEquals(20L + 3L * HomeEditFrontier.MARGIN_ROWS, boundary.exclusiveRow)
    }

    @Test
    fun advance_zeroMargin_neverGrows() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L, marginRows = 0L)
        val advanced = advanceFrontierForMove(boundary, LogicalPosition(0, 100), spanRows = 1)
        assertEquals(boundary, advanced)
    }

    // ── rebaseEditFrontier ─────────────────────────────────────────────

    @Test
    fun rebase_nullCurrent_freshInit() {
        val rebased = rebaseEditFrontier(null, extent(2L, 2L), 1800, 108L)
        assertEquals(20L, rebased.exclusiveRow)
    }

    @Test
    fun rebase_loadedShrank_retainedBoundaryNeverShrinks() {
        // Active drag holds 24 rows; a same-home reload with fewer rows must
        // not pull the extra rows out from under the gesture.
        val current = initEditFrontier(extent(2L, 2L), 1800, 108L)
        val grown = advanceFrontierForMove(current, LogicalPosition(0, 19), spanRows = 1)
        assertEquals(24L, grown.exclusiveRow)
        val rebased = rebaseEditFrontier(grown, extent(1L, 1L), 1800, 108L)
        assertEquals(24L, rebased.exclusiveRow)
    }

    @Test
    fun rebase_loadedGrew_adoptsFreshBoundary() {
        // A committed tile landed: loaded grows past the retained boundary, so
        // the fresh (larger) boundary wins.
        val current = initEditFrontier(extent(2L, 2L), 1800, 108L)
        val rebased = rebaseEditFrontier(current, extent(30L, 30L), 1000, 108L)
        assertEquals(34L, rebased.exclusiveRow)
    }

    @Test
    fun rebase_viewportGrew_adoptsFreshBoundary() {
        val current = initEditFrontier(extent(2L, 2L), 1000, 108L)
        assertEquals(13L, current.exclusiveRow)
        val rebased = rebaseEditFrontier(current, extent(2L, 2L), 1800, 108L)
        assertEquals(20L, rebased.exclusiveRow)
    }

    // ── move/resize legality rows ──────────────────────────────────────

    @Test
    fun moveLegality_noFrontier_normalBound() {
        assertEquals(16, moveLegalityRows(16, null))
    }

    @Test
    fun moveLegality_withFrontier_frontierBound() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        assertEquals(20, moveLegalityRows(16, boundary))
    }

    @Test
    fun moveLegality_hugeFrontier_saturatesToIntMax() {
        val boundary = GridVerticalLayout.EditBoundary(
            exclusiveRow = Long.MAX_VALUE,
            marginRows = HomeEditFrontier.MARGIN_ROWS,
        )
        assertEquals(Int.MAX_VALUE, moveLegalityRows(16, boundary))
    }

    @Test
    fun resizeLegality_ignoresFrontier_noExpansion() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        assertEquals(16, resizeLegalityRows(16, boundary))
        assertEquals(16, resizeLegalityRows(16, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun moveLegality_nonPositiveNormal_throws() {
        moveLegalityRows(0, null)
    }

    // ── edit-only layout rows/height/scroll-max ────────────────────────

    @Test
    fun editLayoutRows_noFrontier_intrinsicCount() {
        assertEquals(2L, editLayoutRows(extent(2L, 2L), null))
    }

    @Test
    fun editLayoutRows_frontierLarger_frontierCount() {
        val boundary = initEditFrontier(extent(2L, 2L), 1800, 108L)
        assertEquals(20L, editLayoutRows(extent(2L, 2L), boundary))
    }

    @Test
    fun editLayoutRows_signedMinRow_countsFromMin() {
        // Tiles on rows 18..20 (minRow 18): the frontier end stays absolute
        // (20) while the row count subtracts the signed minimum.
        val signed = extent(bottomExclusive = 20L, totalRows = 2L, minRow = 18L)
        val boundary = initEditFrontier(signed, 1800, 108L)
        assertEquals(24L, boundary.exclusiveRow)
        assertEquals(6L, editLayoutRows(signed, boundary))
    }

    @Test
    fun editContentHeight_noTrailingGap() {
        // 2 rows × 100px cells + 1 × 8px gap: the last cell ends the content.
        assertEquals(208, editContentHeightPx(2L, 100, 8))
        assertEquals(100, editContentHeightPx(1L, 100, 8))
        assertEquals(0, editContentHeightPx(0L, 100, 8))
    }

    @Test
    fun editContentHeight_overflow_saturates() {
        assertEquals(Int.MAX_VALUE, editContentHeightPx(Long.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun editScrollMax_fitsViewport_zero() {
        assertEquals(0, editScrollMaxPx(2L, 100, 8, 1800))
    }

    @Test
    fun editScrollMax_overflow_exactOverflow() {
        // 17 rows × 100 + 16 × 8 = 1828 in a 1720 viewport → range 108.
        assertEquals(108, editScrollMaxPx(17L, 100, 8, 1720))
    }

    // ── pending-commit lease ───────────────────────────────────────────

    @Test
    fun pending_null_satisfied() {
        assertTrue(isPendingCommitSatisfied(listOf(tile("a")), null))
    }

    @Test
    fun pending_targetVisible_satisfied() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 1, 1))
        assertTrue(
            isPendingCommitSatisfied(tiles, PendingMoveCommit(ItemId("b"), LogicalPosition(1, 1))),
        )
    }

    @Test
    fun pending_targetNotYetReflected_unsatisfied() {
        // Loaded still shows the origin: another MOVE stays gated.
        val tiles = listOf(tile("a", 0, 0))
        assertFalse(
            isPendingCommitSatisfied(tiles, PendingMoveCommit(ItemId("a"), LogicalPosition(0, 5))),
        )
    }

    @Test
    fun pending_unknownItem_unsatisfied() {
        val tiles = listOf(tile("a", 0, 0))
        assertFalse(
            isPendingCommitSatisfied(tiles, PendingMoveCommit(ItemId("gone"), LogicalPosition(0, 0))),
        )
    }

    // ── auto-scroll zones ──────────────────────────────────────────────

    @Test
    fun autoScrollZone_topBand_scrollsUp() {
        assertEquals(
            AutoScrollZone.TOP,
            autoScrollZone(10f, viewportHeightPx = 1800, edgePx = 96),
        )
    }

    @Test
    fun autoScrollZone_bottomBand_scrollsDown() {
        assertEquals(
            AutoScrollZone.BOTTOM,
            autoScrollZone(1790f, viewportHeightPx = 1800, edgePx = 96),
        )
    }

    @Test
    fun autoScrollZone_middle_noScroll() {
        assertEquals(
            AutoScrollZone.NONE,
            autoScrollZone(900f, viewportHeightPx = 1800, edgePx = 96),
        )
    }

    @Test
    fun autoScrollZone_zeroViewport_neverScrolls() {
        assertEquals(AutoScrollZone.NONE, autoScrollZone(0f, viewportHeightPx = 0, edgePx = 96))
    }

    @Test
    fun autoScrollZone_tinyViewport_topWins() {
        assertEquals(AutoScrollZone.TOP, autoScrollZone(10f, viewportHeightPx = 100, edgePx = 96))
    }

    @Test
    fun autoScrollStep_oneStride() {
        assertEquals(108, autoScrollStepPx(108L))
    }

    // ── stationary remap primitive ─────────────────────────────────────

    @Test
    fun stationaryRemap_samePointer_advancingScroll_movesRow() {
        // The same viewport-local pointer maps to a new row when the CURRENT
        // scroll offset advances (auto-scroll), with frozen grab/origin.
        val atRest = MoveDragMath.cellForPixel(
            xPx = 50f,
            yPx = 50f,
            cellSizePx = 100f,
            columnGapPx = 0f,
            rowGapPx = 8f,
            windowFirstRow = 0L,
            columns = 6,
            rows = 20L,
            scrollYPx = 0f,
        )
        val scrolled = MoveDragMath.cellForPixel(
            xPx = 50f,
            yPx = 50f,
            cellSizePx = 100f,
            columnGapPx = 0f,
            rowGapPx = 8f,
            windowFirstRow = 0L,
            columns = 6,
            rows = 20L,
            scrollYPx = 108f,
        )
        assertEquals(MoveCellResult.Valid(MoveCellCandidate(0, 0), LogicalPosition(0, 0)), atRest)
        assertEquals(MoveCellResult.Valid(MoveCellCandidate(0, 1), LogicalPosition(0, 1)), scrolled)
    }

    @Test
    fun stationaryRemap_frozenScroll_holdsRow() {
        // A frozen down-time offset keeps the row: only CURRENT-scroll mapping
        // follows the viewport.
        val first = MoveDragMath.cellForPixel(
            xPx = 50f,
            yPx = 50f,
            cellSizePx = 100f,
            columnGapPx = 0f,
            rowGapPx = 8f,
            windowFirstRow = 0L,
            columns = 6,
            rows = 20L,
            scrollYPx = 0f,
        )
        val second = MoveDragMath.cellForPixel(
            xPx = 50f,
            yPx = 50f,
            cellSizePx = 100f,
            columnGapPx = 0f,
            rowGapPx = 8f,
            windowFirstRow = 0L,
            columns = 6,
            rows = 20L,
            scrollYPx = 0f,
        )
        assertEquals(first, second)
    }

    // ── P1 ticker integration: stationary remap + actual baseline ──────

    @Test
    fun tickerRemap_stationaryHold_advancingScroll_updatesCandidate() {
        // A held pointer with no move events must still follow the viewport:
        // the same viewport-local pointer maps to the pre-scroll cell at rest
        // and to the post-scroll cell after one stride step, and the session
        // candidate tracks it — so release commits the post-scroll cell.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        val entered = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        val atRest = remapMoveCandidateAfterScroll(
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
        val scrolled = remapMoveCandidateAfterScroll(
            session = atRest,
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
        val restActive = atRest as HomeEditSession.Active
        val scrolledActive = scrolled as HomeEditSession.Active
        assertEquals(LogicalPosition(2, 15), restActive.candidatePosition)
        assertEquals(LogicalPosition(2, 16), scrolledActive.candidatePosition)
        assertEquals(LogicalPosition(2, 16), scrolled.moveCommitPosition())
    }

    @Test
    fun tickerRemap_blockedPostScrollCell_holdsLastValid() {
        // A post-scroll cell occupied by another tile must not move the ghost:
        // the candidate reports the collision while lastValid (and the release
        // commit) stays at the pre-scroll cell.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        val entered = HomeEditSession.Idle
            .enterIfLaunchable(tiles[0], tiles, columns = 6, rows = 30)
        val atRest = remapMoveCandidateAfterScroll(
            session = entered,
            pointerXPx = 50f,
            pointerYPx = 50f,
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
        // Scroll so the same pointer lands on tile b's cell (2, 2): content y
        // 50 + scroll maps to row floor((50)/108) + origin; solve scroll for
        // row 2 → scroll in [166, 274). Use 200f-equivalent 200px.
        val scrolled = remapMoveCandidateAfterScroll(
            session = atRest,
            pointerXPx = 250f,
            pointerYPx = 50f,
            scrollPx = 200,
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
        val scrolledActive = scrolled as HomeEditSession.Active
        assertEquals(CandidateValidity.COLLIDING, scrolledActive.candidateValidity)
        assertEquals(
            (atRest as HomeEditSession.Active).lastValidPosition,
            scrolledActive.lastValidPosition,
        )
    }

    @Test
    fun tickerRemap_rejectedOutOfBounds_holdsLastValidAndCannotCommit() {
        val tiles = listOf(tile("a", 0, 0))
        val entered = HomeEditSession.Idle
            .enterIfLaunchable(tiles[0], tiles, columns = 6, rows = 30)
        val atRest = remapMoveCandidateAfterScroll(
            session = entered,
            pointerXPx = 150f,
            pointerYPx = 50f,
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
        val rejected = remapMoveCandidateAfterScroll(
            session = atRest,
            pointerXPx = 650f,
            pointerYPx = 50f,
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
        ) as HomeEditSession.Active

        assertEquals(LogicalPosition(1, 0), rejected.lastValidPosition)
        assertEquals(LogicalPosition(1, 0), rejected.candidatePosition)
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, rejected.candidateValidity)
        assertNull(rejected.moveCommitPosition())
    }

    @Test
    fun tickerBaseline_clampedOldMax_returnsActualNotTarget() {
        // The ticker requests the edit-max-clamped target (e.g. 524) but the
        // live ScrollState still clamps to the older normal maximum (e.g.
        // 500). The baseline must be the actual 500: keying it to 524 would
        // read back as a foreign-scroll delta and steal-cancel the drag.
        assertEquals(500, tickerScrollBaseline(actualScrollPx = 500, requestedTargetPx = 524))
        assertEquals(0, tickerScrollBaseline(actualScrollPx = 0, requestedTargetPx = 108))
    }

    @Test
    fun tickerBaseline_unclampedStep_returnsTarget() {
        // When nothing clamps, actual equals the request and the baseline is
        // exactly the stepped position.
        assertEquals(108, tickerScrollBaseline(actualScrollPx = 108, requestedTargetPx = 108))
    }

    // ── release-only commit / zero-write cancel ────────────────────────
    @Test
    fun releaseCommit_singlePositionFromLastValid() {
        // Scrolling/edge steps never commit: only moveCommitPosition at release
        // yields a position, exactly once, from the last valid cell.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        assertEquals(LogicalPosition(3, 3), session.moveCommitPosition())
    }

    @Test
    fun cancel_zeroWrite_noCommitPosition() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        var session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[1], tiles, columns = 6, rows = 30)
        session = session.updateMoveCandidate(LogicalPosition(3, 3), tiles, 6, 30)
        // Cancel (back/empty-tap/pager steal): Idle carries no commit.
        session = session.cancel()
        assertNull(session.moveCommitPosition())
    }

    @Test
    fun noMoveRelease_noCommitPosition() {
        // A drag that never leaves the origin commits nothing.
        val tiles = listOf(tile("a", 0, 0))
        val session: HomeEditSession = HomeEditSession.Idle
            .enterIfLaunchable(tiles[0], tiles, columns = 6, rows = 30)
        assertNull(session.moveCommitPosition())
    }

    // ── exit collapse/clamp (viewport helpers, no production change) ───

    @Test
    fun exitCollapse_scrollClampsToNormalMax() {
        // Edit grew the range; on exit the ScrollState collapses to the normal
        // maximum like preserveAnchorOnResize bottom-pins tall→tall.
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -4500L, scrollRangePx = 5000L)
        val collapsed = preserveAnchorOnResize(
            old,
            me.foxtails.lakehead.core.grid.GridRest(
                gridOriginY = 0L,
                scrollRangePx = 2000L,
                scrollable = true,
            ),
        )
        assertEquals(-2000L, collapsed.viewportOffsetY)
        assertEquals(-2000L, clampViewportOffset(-4500L, 2000L))
    }

    @Test
    fun exitCollapse_fitsViewport_zeroes() {
        // Tall→short on exit: offset collapses to zero and a zero range
        // clamps any offset to zero.
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -2000L, scrollRangePx = 4000L)
        val collapsed = preserveAnchorOnResize(
            old,
            me.foxtails.lakehead.core.grid.GridRest(
                gridOriginY = 540L,
                scrollRangePx = 0L,
                scrollable = false,
            ),
        )
        assertEquals(0L, collapsed.viewportOffsetY)
        assertEquals(0L, clampViewportOffset(-2000L, 0L))
    }
}
