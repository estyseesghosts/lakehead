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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** D1 move/resize legality and preservation regressions. */
class MoveResizeRegressionTest {

    private val identity = AppIdentity(ProfileId("personal"), ComponentIdentity("com.app", "Main"))

    private fun tile(
        id: String,
        column: Int = 0,
        row: Int = 0,
        spanColumns: Int = 1,
        spanRows: Int = 1,
    ) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(spanColumns, spanRows),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )

    private fun placement(
        id: String,
        column: Int,
        row: Int,
        spanColumns: Int = 1,
        spanRows: Int = 1,
    ) = GridPlacement(
        ItemId(id),
        GridPosition(column, row),
        GridSpan(spanColumns, spanRows),
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

    private fun begin(t: HomeRenderTile, placements: List<HomeRenderTile>): HomeEditState =
        HomeEditState.Normal
            .enterOnLongPress(t.itemId)
            .beginMove(t, placements, metrics, 0f, 0f)

    private fun HomeEditState.moveRequest(column: Int, row: Int, placements: List<HomeRenderTile>) =
        updateMove(
            MoveCellResult.Valid(
                RequestedCell(column.toLong(), row.toLong()),
                LogicalPosition(column, row),
            ),
            placements,
        )

    @Test
    fun move_preservesSpan_andChangesOnlyPosition() {
        val moving = tile("moving", spanColumns = 2, spanRows = 1)
        val placements = listOf(moving)
        val state = begin(moving, placements).moveRequest(3, 2, placements)

        assertEquals(LogicalSpan(2, 1), state.moveDrag!!.span)
        val result = GridRules.move(
            listOf(placement("moving", 0, 0, 2)),
            ItemId("moving"),
            GridPosition(3, 2),
            6,
        ) as GridRules.Result.Accepted
        assertEquals(GridPosition(3, 2), result.placements.single().position)
        assertEquals(GridSpan(2, 1), result.placements.single().span)
    }

    @Test
    fun resize_preservesPosition_andChangesOnlySpan() {
        val resizing = tile("resizing", column = 2, row = 1)
        val placements = listOf(resizing)
        val state = HomeEditState.Normal
            .enterOnLongPress(resizing.itemId)
            .toggleResizeArmed()
            .beginResize(resizing, metrics)
            .updateResize(LogicalSpan(2, 3), placements)

        assertEquals(LogicalPosition(2, 1), state.resizeDrag!!.position)
        val result = GridRules.resize(
            listOf(placement("resizing", 2, 1)),
            ItemId("resizing"),
            GridSpan(2, 3),
            6,
        ) as GridRules.Result.Accepted
        assertEquals(GridPosition(2, 1), result.placements.single().position)
        assertEquals(GridSpan(2, 3), result.placements.single().span)
    }

    @Test
    fun resize_requiresArmedArrow() {
        val resizing = tile("resizing")
        val selected = HomeEditState.Normal.enterOnLongPress(resizing.itemId)

        assertSame(selected, selected.beginResize(resizing, metrics))
        assertTrue(selected.toggleResizeArmed().beginResize(resizing, metrics).resizeDrag != null)
    }

    @Test
    fun move_allowsDiagonalRowCrossingFreePlacement() {
        val moving = tile("moving", spanColumns = 2, spanRows = 2)
        val placements = listOf(moving)
        val state = begin(moving, placements).moveRequest(3, 2, placements)

        assertEquals(CandidateValidity.VALID, state.moveDrag!!.validity)
        assertTrue(
            GridRules.move(
                listOf(placement("moving", 0, 0, 2, 2)),
                ItemId("moving"),
                GridPosition(3, 2),
                6,
            ) is GridRules.Result.Accepted,
        )
    }

    @Test
    fun collision_isRejected_bySessionAndGridRules() {
        val moving = tile("moving", spanColumns = 2)
        val obstacle = tile("obstacle", column = 3, row = 1)
        val placements = listOf(moving, obstacle)
        val state = begin(moving, placements).moveRequest(2, 1, placements)

        assertEquals(CandidateValidity.COLLIDING, state.moveDrag!!.validity)
        val result = GridRules.move(
            listOf(placement("moving", 0, 0, 2), placement("obstacle", 3, 1)),
            ItemId("moving"),
            GridPosition(2, 1),
            6,
        ) as GridRules.Result.Rejected
        assertEquals(GridRules.Rejection.COLLISION, result.reason)
    }

    @Test
    fun horizontalBoundsRemainRejectedButRowsAreUnbounded() {
        val moving = tile("moving", spanColumns = 2)
        val placements = listOf(moving)
        val state = begin(moving, placements).moveRequest(5, Int.MAX_VALUE, placements)

        assertEquals(CandidateValidity.OUT_OF_BOUNDS, state.moveDrag!!.validity)
        val result = GridRules.move(
            listOf(placement("moving", 0, 0, 2)),
            ItemId("moving"),
            GridPosition(5, Int.MAX_VALUE),
            6,
        ) as GridRules.Result.Rejected
        assertEquals(GridRules.Rejection.OUT_OF_BOUNDS, result.reason)
    }

    @Test
    fun move_preservesHoles_andDoesNotPackOtherPlacements() {
        val current = listOf(
            placement("moving", 0, 0),
            placement("fixed", 2, 0),
            placement("lower", 0, 3),
        )
        val result = GridRules.move(current, ItemId("moving"), GridPosition(4, 4), 6)
            as GridRules.Result.Accepted

        assertEquals(listOf(ItemId("moving"), ItemId("fixed"), ItemId("lower")), result.placements.map { it.id })
        assertEquals(GridPosition(4, 4), result.placements[0].position)
        assertEquals(GridPosition(2, 0), result.placements[1].position)
        assertEquals(GridPosition(0, 3), result.placements[2].position)
        assertFalse(result.placements.any { it.position == GridPosition(1, 0) })
    }

    @Test
    fun resize_preservesHoles_andDoesNotPackOtherPlacements() {
        val current = listOf(
            placement("resizing", 1, 1),
            placement("fixed", 4, 0),
            placement("lower", 0, 4),
        )
        val result = GridRules.resize(current, ItemId("resizing"), GridSpan(2, 2), 6)
            as GridRules.Result.Accepted

        assertEquals(GridPosition(1, 1), result.placements[0].position)
        assertEquals(GridSpan(2, 2), result.placements[0].span)
        assertEquals(GridPosition(4, 0), result.placements[1].position)
        assertEquals(GridPosition(0, 4), result.placements[2].position)
        assertEquals(3, result.placements.size)
    }
}
