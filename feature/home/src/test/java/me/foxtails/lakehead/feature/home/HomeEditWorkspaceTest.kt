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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 6: the transient workspace sizes content only; it never touches candidates. */
class HomeEditWorkspaceTest {

    private val metrics = HomeGridMetrics(
        columns = 6,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = 0f,
        restOffsetYPx = 0f,
    )

    private fun extent(bottom: Long) = GridVerticalExtent(
        occupiedBottomRowExclusive = bottom,
        totalScrollableRows = bottom,
        occupiedMinRow = 0L,
        occupiedMaxRowExclusive = bottom,
        occupiedHeightRows = bottom,
    )

    private val moving = HomeRenderTile(
        itemId = ItemId("m"),
        appIdentity = AppIdentity(ProfileId("personal"), ComponentIdentity("com.app", "Main")),
        position = LogicalPosition(0, 0),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = "m",
        icon = null,
        isLaunchable = true,
    )

    @Test
    fun candidate_atRow30_isIdenticalBeforeAndAfterWorkspaceGrowth() {
        val state = HomeEditState.Normal.enterOnLongPress(moving.itemId)
            .beginMove(moving, listOf(moving), metrics, 50f, 50f)
        val y = metrics.top(30) + 50f
        val before = state.moveTo(metrics.left(2) + 50f, y, 0f)
        // Growing the workspace re-measures the content but the frame is unchanged.
        val after = before.cancelDragIfFrameChanged(metrics).moveTo(metrics.left(2) + 50f, y, 0f)
        assertEquals(before.moveDrag!!.requestedCell, after.moveDrag!!.requestedCell)
        assertEquals(RequestedCell(2L, 30L), after.moveDrag!!.requestedCell)
        assertEquals(before.moveDrag!!.validity, after.moveDrag!!.validity)
    }

    @Test
    fun stationaryPointer_scrollOneRowStride_requestsNextRow() {
        val state = HomeEditState.Normal.enterOnLongPress(moving.itemId)
            .beginMove(moving, listOf(moving), metrics, 50f, 50f)
        val x = metrics.left(1) + 50f
        val y = metrics.top(5) + 50f
        val at0 = state.moveTo(x, y, 0f).moveDrag!!
        val at1 = state.moveTo(x, y, metrics.rowStrideCells.toFloat()).moveDrag!!
        assertEquals(RequestedCell(1L, 5L), at0.requestedCell)
        assertEquals(RequestedCell(1L, 6L), at1.requestedCell)
    }

    @Test
    fun workspaceBottomPx_mapsRowsToContentHeight() {
        assertEquals(0, workspaceBottomPx(0L, metrics))
        assertEquals(100, workspaceBottomPx(1L, metrics))
        assertEquals(108 * 9 + 100, workspaceBottomPx(10L, metrics))
    }

    @Test
    fun moveWorkspace_isCommittedBottomPlusAllowedGapPlusSpan() {
        assertEquals(3L + 2L + 1L, moveWorkspaceBottomRow(extent(3L), 1))
        assertEquals(3L + 2L + 2L, moveWorkspaceBottomRow(extent(3L), 2))
        assertEquals(0L + 2L + 1L, moveWorkspaceBottomRow(extent(0L), 1))
    }

    @Test
    fun moveWorkspace_neverCutsTheCommittedBottom() {
        assertTrue(moveWorkspaceBottomRow(extent(5_000L), 2) >= 5_000L)
    }

    @Test
    fun committedMaxScroll_isZeroWhenContentFitsAndPositiveWhenItOverflows() {
        assertEquals(0, committedMaxScrollPx(extent(2L), metrics, fullViewportPx = 1000, bottomClearancePx = 0))
        assertTrue(committedMaxScrollPx(extent(30L), metrics, fullViewportPx = 1000, bottomClearancePx = 0) > 0)
    }

    @Test
    fun moveWorkspaceFloor_coversTheWorkspaceOnlyWhileOneIsHeld() {
        val held = moveWorkspaceFloorPx(12L, metrics, fullViewportPx = 500, bottomClearancePx = 0)
        assertTrue(held > 500)
        assertEquals(500, moveWorkspaceFloorPx(null, metrics, fullViewportPx = 500, bottomClearancePx = 0))
    }
}
