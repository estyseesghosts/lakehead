package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridSpan
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
 * MOVE auto-scroll, lease and remap primitives for the home canvas. The B3
 * frontier (init/advance/rebase, edit layout rows/height/scroll-max, steal
 * baseline) is gone; its replacement is covered by `HomeEditWorkspaceTest`.
 *
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

    @Test
    fun autoScrollInterval_outsideBand_null() {
        assertNull(autoScrollIntervalMs(900f, viewportHeightPx = 1800, edgePx = 96))
        assertNull(autoScrollIntervalMs(0f, viewportHeightPx = 0, edgePx = 96))
    }

    @Test
    fun autoScrollInterval_symmetricTopBottom_equal() {
        val top = autoScrollIntervalMs(10f, viewportHeightPx = 1800, edgePx = 96)
        val bottom = autoScrollIntervalMs(1790f, viewportHeightPx = 1800, edgePx = 96)
        assertEquals(top, bottom)
    }

    @Test
    fun autoScrollInterval_monotonic_fasterDeeper() {
        // Just inside the band scrolls slowest (near 300ms), the viewport
        // edge scrolls fastest (120ms), with a mid-band value strictly between.
        val inner = autoScrollIntervalMs(95f, viewportHeightPx = 1800, edgePx = 96)
        val mid = autoScrollIntervalMs(48f, viewportHeightPx = 1800, edgePx = 96)
        val deep = autoScrollIntervalMs(0f, viewportHeightPx = 1800, edgePx = 96)
        assertEquals(HomeEditFrontier.AUTO_SCROLL_MIN_INTERVAL_MS, deep)
        assertTrue(inner!! > mid!! && mid > deep!!)
        assertTrue(inner in HomeEditFrontier.AUTO_SCROLL_MIN_INTERVAL_MS..
            HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS)
        val innerBottom = autoScrollIntervalMs(1705f, viewportHeightPx = 1800, edgePx = 96)
        val deepBottom = autoScrollIntervalMs(1800f, viewportHeightPx = 1800, edgePx = 96)
        assertEquals(HomeEditFrontier.AUTO_SCROLL_MIN_INTERVAL_MS, deepBottom)
        assertEquals(inner, innerBottom)
    }

    // ── stationary remap primitive ─────────────────────────────────────

    private fun testMetrics() = HomeGridMetrics(
        columns = 6,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = 0f,
        restOffsetYPx = 0f,
    )

    private fun testFrame() = MoveCoordinateFrame(testMetrics(), GridSpan(1, 1), 0f, 0f)

    private fun entered(tile: HomeRenderTile, tiles: List<HomeRenderTile>): HomeEditState =
        HomeEditState.Normal
            .enterOnLongPress(tile.itemId)
            .beginMove(tile, tiles, testMetrics(), 0f, 0f)

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

    private fun HomeEditState.move(): DragState.Move = drag as DragState.Move

    @Test
    fun stationaryRemap_samePointer_advancingScroll_movesRow() {
        // The same viewport-local pointer maps to a new row when the CURRENT
        // scroll offset advances (auto-scroll).
        val frame = testFrame()
        val atRest = MoveDragMath.cellForPixel(frame, 50f, 50f, 0f)
        val scrolled = MoveDragMath.cellForPixel(frame, 50f, 50f, 108f)
        assertEquals(
            MoveCellResult.Valid(RequestedCell(0, 0), LogicalPosition(0, 0)),
            atRest,
        )
        assertEquals(
            MoveCellResult.Valid(RequestedCell(0, 1), LogicalPosition(0, 1)),
            scrolled,
        )
    }

    @Test
    fun stationaryRemap_sameScroll_isDeterministicAcrossIntermediateScroll() {
        // Absolute mapping: no accumulated state, so returning to a scroll
        // value returns the original answer. (Replaces the vacuous
        // frozen-scroll test that compared two identical calls.)
        val frame = testFrame()
        val first = MoveDragMath.cellForPixel(frame, 50f, 50f, 0f)
        MoveDragMath.cellForPixel(frame, 50f, 50f, 216f)
        assertEquals(first, MoveDragMath.cellForPixel(frame, 50f, 50f, 0f))
    }

    // ── P1 ticker integration: stationary remap ────────────────────────

    @Test
    fun tickerRemap_stationaryHold_advancingScroll_updatesCandidate() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        val entered = entered(tiles[1], tiles)
        val atRest = remap(entered, 250f, 1700f, scroll = 0)
        val scrolled = remap(atRest, 250f, 1700f, scroll = 108)
        assertEquals(LogicalPosition(2, 15), atRest.move().previewPosition)
        assertEquals(LogicalPosition(2, 16), scrolled.move().previewPosition)
        assertEquals(
            HomeEditCommit.Move(ItemId("b"), LogicalPosition(2, 16)),
            scrolled.finish().commit,
        )
    }

    @Test
    fun tickerRemap_blockedPostScrollCell_holdsLastValid() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        val entered = entered(tiles[0], tiles)
        val atRest = remap(entered, 50f, 50f, scroll = 0)
        // content y 50 + 200 = 250 → row 2, column 2 → tile b's cell.
        val scrolled = remap(atRest, 250f, 50f, scroll = 200)
        assertEquals(CandidateValidity.COLLIDING, scrolled.move().validity)
        assertEquals(atRest.move().previewPosition, scrolled.move().previewPosition)
    }

    @Test
    fun tickerRemap_pastEdge_isRejectedNotClamped_keepsLastValid() {
        // Retired contract: the old test expected the request to be clamped to
        // the last column and committable. Past the right edge is now an
        // invalid request that holds the last-valid preview and cannot commit.
        val tiles = listOf(tile("a", 0, 0))
        val entered = entered(tiles[0], tiles)
        val atRest = remap(entered, 150f, 50f, scroll = 0)
        val past = remap(atRest, 650f, 50f, scroll = 0)

        assertEquals(LogicalPosition(1, 0), past.move().previewPosition)
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, past.move().validity)
        assertEquals(null, past.finish().commit)
    }

    // ── release-only commit / zero-write cancel ────────────────────────
    @Test
    fun releaseCommit_singlePositionFromLastValid() {
        // Scrolling/edge steps never commit: only finish() at release yields
        // a position, exactly once, from the last valid cell.
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        val state = entered(tiles[1], tiles).updateMove(
            MoveCellResult.Valid(RequestedCell(3, 3), LogicalPosition(3, 3)),
        )
        assertEquals(HomeEditCommit.Move(ItemId("b"), LogicalPosition(3, 3)), state.finish().commit)
    }

    @Test
    fun cancel_zeroWrite_noCommitPosition() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 2, 2))
        val state = entered(tiles[1], tiles).updateMove(
            MoveCellResult.Valid(RequestedCell(3, 3), LogicalPosition(3, 3)),
        )
        // Cancel (back/empty-tap/pager steal): no drag carries no commit.
        assertNull(state.cancelDrag().finish().commit)
    }

    @Test
    fun noMoveRelease_noCommitPosition() {
        // A drag that never leaves the origin commits nothing.
        val tiles = listOf(tile("a", 0, 0))
        assertNull(entered(tiles[0], tiles).finish().commit)
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
