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

    @Test
    fun move_preservesSpan_andChangesOnlyPosition() {
        val moving = tile("moving", spanColumns = 2, spanRows = 1)
        val placements = listOf(moving)
        val session = HomeEditSession.Idle
            .enterIfLaunchable(moving, placements, columns = 6, rows = 6)
            .updateMoveCandidate(LogicalPosition(3, 2), placements, 6, 6)

        assertEquals(LogicalSpan(2, 1), (session as HomeEditSession.Active).candidateSpan)
        val result = GridRules.move(
            listOf(placement("moving", 0, 0, 2)),
            ItemId("moving"),
            GridPosition(3, 2),
            6,
            6,
        ) as GridRules.Result.Accepted
        assertEquals(GridPosition(3, 2), result.placements.single().position)
        assertEquals(GridSpan(2, 1), result.placements.single().span)
    }

    @Test
    fun resize_preservesPosition_andChangesOnlySpan() {
        val resizing = tile("resizing", column = 2, row = 1)
        val placements = listOf(resizing)
        val session = HomeEditSession.Idle
            .enterIfLaunchable(resizing, placements, columns = 6, rows = 6)
            .beginResize(armedAtDown = true)
            .updateResizeCandidate(LogicalSpan(2, 3), placements, 6, 6)

        assertEquals(LogicalPosition(2, 1), (session as HomeEditSession.Active).origin)
        val result = GridRules.resize(
            listOf(placement("resizing", 2, 1)),
            ItemId("resizing"),
            GridSpan(2, 3),
            6,
            6,
        ) as GridRules.Result.Accepted
        assertEquals(GridPosition(2, 1), result.placements.single().position)
        assertEquals(GridSpan(2, 3), result.placements.single().span)
    }

    @Test
    fun resize_requiresArmedArrow() {
        val resizing = tile("resizing")
        val session = HomeEditSession.Idle.enterIfLaunchable(resizing, listOf(resizing), 6, 6)

        assertSame(session, session.beginResize(armedAtDown = false))
        assertEquals(EditMode.RESIZE, session.beginResize(armedAtDown = true).let {
            (it as HomeEditSession.Active).mode
        })
    }

    @Test
    fun move_allowsDiagonalRowCrossingFreePlacement() {
        val moving = tile("moving", spanColumns = 2, spanRows = 2)
        val placements = listOf(moving)
        val session = HomeEditSession.Idle
            .enterIfLaunchable(moving, placements, 6, 6)
            .updateMoveCandidate(LogicalPosition(3, 2), placements, 6, 6)

        assertTrue(session is HomeEditSession.Active)
        assertEquals(CandidateValidity.VALID, (session as HomeEditSession.Active).candidateValidity)
        assertTrue(
            GridRules.move(
                listOf(placement("moving", 0, 0, 2, 2)),
                ItemId("moving"),
                GridPosition(3, 2),
                6,
                6,
            ) is GridRules.Result.Accepted,
        )
    }

    @Test
    fun collision_isRejected_bySessionAndGridRules() {
        val moving = tile("moving", spanColumns = 2)
        val obstacle = tile("obstacle", column = 3, row = 1)
        val placements = listOf(moving, obstacle)
        val session = HomeEditSession.Idle
            .enterIfLaunchable(moving, placements, 6, 6)
            .updateMoveCandidate(LogicalPosition(2, 1), placements, 6, 6)

        assertEquals(CandidateValidity.COLLIDING, (session as HomeEditSession.Active).candidateValidity)
        val result = GridRules.move(
            listOf(placement("moving", 0, 0, 2), placement("obstacle", 3, 1)),
            ItemId("moving"),
            GridPosition(2, 1),
            6,
            6,
        ) as GridRules.Result.Rejected
        assertEquals(GridRules.Rejection.COLLISION, result.reason)
    }

    @Test
    fun bounds_areRejected_bySessionAndGridRules() {
        val moving = tile("moving", spanColumns = 2)
        val placements = listOf(moving)
        val session = HomeEditSession.Idle
            .enterIfLaunchable(moving, placements, 6, 6)
            .updateMoveCandidate(LogicalPosition(5, 5), placements, 6, 6)

        assertEquals(CandidateValidity.OUT_OF_BOUNDS, (session as HomeEditSession.Active).candidateValidity)
        val result = GridRules.move(
            listOf(placement("moving", 0, 0, 2)),
            ItemId("moving"),
            GridPosition(5, 5),
            6,
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
        val result = GridRules.move(current, ItemId("moving"), GridPosition(4, 4), 6, 6)
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
        val result = GridRules.resize(current, ItemId("resizing"), GridSpan(2, 2), 6, 6)
            as GridRules.Result.Accepted

        assertEquals(GridPosition(1, 1), result.placements[0].position)
        assertEquals(GridSpan(2, 2), result.placements[0].span)
        assertEquals(GridPosition(4, 0), result.placements[1].position)
        assertEquals(GridPosition(0, 4), result.placements[2].position)
        assertEquals(3, result.placements.size)
    }
}
