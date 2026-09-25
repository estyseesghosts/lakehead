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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEditSessionTest {

    private val profile = ProfileId("personal")
    private val identity = AppIdentity(profile, ComponentIdentity("com.app", "Main"))

    private fun tile(
        id: String,
        column: Int = 0,
        row: Int = 0,
        spanColumns: Int = 1,
        spanRows: Int = 1,
        launchable: Boolean = true,
    ) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(spanColumns, spanRows),
        availability = if (launchable) ItemAvailability.AVAILABLE else ItemAvailability.UNAVAILABLE,
        label = if (launchable) id else null,
        icon = null,
        isLaunchable = launchable,
    )

    // ---- enterIfLaunchable ----

    @Test
    fun enterIfLaunchable_launchableTile_becomesActiveWithOriginEqualToCandidate() {
        val t = tile("app-a", column = 2, row = 3)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)

        assertTrue(session is HomeEditSession.Active)
        val active = session as HomeEditSession.Active
        assertEquals(ItemId("app-a"), active.itemId)
        assertEquals(LogicalPosition(2, 3), active.origin)
        assertEquals(LogicalSpan(1, 1), active.originSpan)
        assertEquals(LogicalPosition(2, 3), active.candidatePosition)
        assertEquals(LogicalSpan(1, 1), active.candidateSpan)
        assertEquals(CandidateValidity.VALID, active.candidateValidity)
        assertEquals(EditMode.MOVE, active.mode)
    }

    @Test
    fun enterIfLaunchable_nonLaunchableTile_staysIdle() {
        val t = tile("app-u", launchable = false)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)

        assertTrue(session is HomeEditSession.Idle)
    }

    // ---- beginResize ----

    @Test
    fun beginResize_fromMoveMode_switchesToResizeMode() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()

        assertTrue(session is HomeEditSession.Active)
        val active = session as HomeEditSession.Active
        assertEquals(EditMode.RESIZE, active.mode)
        assertEquals(LogicalPosition(0, 0), active.candidatePosition)
        assertEquals(LogicalSpan(1, 1), active.candidateSpan)
        assertEquals(CandidateValidity.VALID, active.candidateValidity)
    }

    @Test
    fun beginResize_alreadyInResizeMode_noOp() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
        val same = session.beginResize()

        assertSame(session, same)
    }

    @Test
    fun beginResize_idleSession_noOp() {
        val result = HomeEditSession.Idle.beginResize()
        assertTrue(result is HomeEditSession.Idle)
    }

    // ---- updateMoveCandidate ----

    @Test
    fun updateMoveCandidate_freeCell_valid() {
        val moving = tile("app-a", column = 0, row = 0)
        val obstacle = tile("app-b", column = 3, row = 3)
        val placements = listOf(moving, obstacle)

        val session = HomeEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateMoveCandidate(LogicalPosition(4, 0), placements, columns = 6, rows = 10)

        val active = session as HomeEditSession.Active
        assertEquals(LogicalPosition(4, 0), active.candidatePosition)
        assertEquals(CandidateValidity.VALID, active.candidateValidity)
    }

    @Test
    fun updateMoveCandidate_occupiedCell_colliding() {
        val moving = tile("app-a", column = 0, row = 0)
        val obstacle = tile("app-b", column = 2, row = 2)
        val placements = listOf(moving, obstacle)

        val session = HomeEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateMoveCandidate(LogicalPosition(2, 2), placements, columns = 6, rows = 10)

        val active = session as HomeEditSession.Active
        assertEquals(CandidateValidity.COLLIDING, active.candidateValidity)
    }

    @Test
    fun updateMoveCandidate_pastColumns_outOfBounds() {
        val moving = tile("app-a", column = 0, row = 0)
        val placements = listOf(moving)

        val session = HomeEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateMoveCandidate(LogicalPosition(6, 0), placements, columns = 6, rows = 10)

        val active = session as HomeEditSession.Active
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, active.candidateValidity)
    }

    @Test
    fun updateMoveCandidate_inResizeMode_noOp() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
        val result = session.updateMoveCandidate(LogicalPosition(3, 3), placements, columns = 6, rows = 10)

        assertSame(session, result)
    }

    @Test
    fun updateMoveCandidate_idleSession_returnsIdleUnchanged() {
        val result = HomeEditSession.Idle.updateMoveCandidate(LogicalPosition(1, 1), emptyList(), 6, 10)
        assertTrue(result is HomeEditSession.Idle)
        assertSame(HomeEditSession.Idle, result)
    }

    // ---- updateResizeCandidate ----

    @Test
    fun updateResizeCandidate_validSpan_updatesSpan() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(2, 2), placements, columns = 6, rows = 10)

        val active = session as HomeEditSession.Active
        assertEquals(LogicalSpan(2, 2), active.candidateSpan)
        assertEquals(CandidateValidity.VALID, active.candidateValidity)
    }

    @Test
    fun updateResizeCandidate_outOfBounds_reportsOutOfBounds() {
        val t = tile("app-a", column = 5, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(2, 1), placements, columns = 6, rows = 10)

        val active = session as HomeEditSession.Active
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, active.candidateValidity)
    }

    @Test
    fun updateResizeCandidate_collision_reportsColliding() {
        val t = tile("app-a", column = 0, row = 0)
        val obstacle = tile("app-b", column = 1, row = 0)
        val placements = listOf(t, obstacle)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(2, 1), placements, columns = 6, rows = 10)

        val active = session as HomeEditSession.Active
        assertEquals(CandidateValidity.COLLIDING, active.candidateValidity)
    }

    @Test
    fun updateResizeCandidate_inMoveMode_noOp() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
        val result = session.updateResizeCandidate(LogicalSpan(2, 2), placements, columns = 6, rows = 10)

        assertSame(session, result)
    }

    @Test
    fun updateResizeCandidate_idleSession_returnsIdle() {
        val result = HomeEditSession.Idle.updateResizeCandidate(LogicalSpan(2, 2), emptyList(), 6, 10)
        assertTrue(result is HomeEditSession.Idle)
    }

    // ---- cancel ----

    @Test
    fun cancel_returnsIdle() {
        val t = tile("app-a")
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .cancel()

        assertTrue(session is HomeEditSession.Idle)
    }

    @Test
    fun cancel_idleSession_returnsIdle() {
        val session = HomeEditSession.Idle.cancel()
        assertTrue(session is HomeEditSession.Idle)
    }

    @Test
    fun cancel_resizeSession_returnsIdle() {
        val t = tile("app-a")
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
            .cancel()

        assertTrue(session is HomeEditSession.Idle)
    }

    // ---- hasMoved ----

    @Test
    fun hasMoved_candidateEqualsOrigin_false() {
        val t = tile("app-a", column = 2, row = 3)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)

        assertFalse(session.hasMoved())
    }

    @Test
    fun hasMoved_candidateDiffersFromOrigin_true() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .updateMoveCandidate(LogicalPosition(1, 0), placements, columns = 6, rows = 10)

        assertTrue(session.hasMoved())
    }

    @Test
    fun hasMoved_idleSession_false() {
        assertFalse(HomeEditSession.Idle.hasMoved())
    }

    @Test
    fun hasMoved_inResizeMode_false() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()

        assertFalse(session.hasMoved())
    }

    // ---- hasResized ----

    @Test
    fun hasResized_spanDiffers_true() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(2, 2), placements, columns = 6, rows = 10)

        assertTrue(session.hasResized())
    }

    @Test
    fun hasResized_spanSame_false() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()

        assertFalse(session.hasResized())
    }

    @Test
    fun hasResized_idleSession_false() {
        assertFalse(HomeEditSession.Idle.hasResized())
    }

    @Test
    fun hasResized_inMoveMode_false() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .updateMoveCandidate(LogicalPosition(2, 0), placements, columns = 6, rows = 10)

        assertFalse(session.hasResized())
    }

    // ---- multiple tiles as obstacles ----

    @Test
    fun multipleTiles_otherTilesBecomeObstaclesForMoveValidity() {
        val moving = tile("app-a", column = 0, row = 0)
        val obstacle1 = tile("app-b", column = 2, row = 0)
        val obstacle2 = tile("app-c", column = 4, row = 0, spanColumns = 2)
        val placements = listOf(moving, obstacle1, obstacle2)

        val enter = HomeEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)

        // Free cell at (1,0) → VALID
        val atFree = enter.updateMoveCandidate(LogicalPosition(1, 0), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.VALID, (atFree as HomeEditSession.Active).candidateValidity)

        // Obstacle1 at (2,0) → COLLIDING
        val atObstacle1 = enter.updateMoveCandidate(LogicalPosition(2, 0), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.COLLIDING, (atObstacle1 as HomeEditSession.Active).candidateValidity)

        // Obstacle2 spans (4,0)..(5,0), so (5,0) is within its span → COLLIDING
        val atObstacle2Edge = enter.updateMoveCandidate(LogicalPosition(5, 0), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.COLLIDING, (atObstacle2Edge as HomeEditSession.Active).candidateValidity)

        // Cell beyond all obstacles → VALID
        val atFreeEnd = enter.updateMoveCandidate(LogicalPosition(3, 5), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.VALID, (atFreeEnd as HomeEditSession.Active).candidateValidity)
    }

    // ---- mode persistence ----

    @Test
    fun mode_staysMoveDuringMoveUpdates() {
        val t = tile("app-a", column = 0, row = 0)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .updateMoveCandidate(LogicalPosition(2, 0), placements, columns = 6, rows = 10)

        assertEquals(EditMode.MOVE, (session as HomeEditSession.Active).mode)
    }

    @Test
    fun mode_staysResizeDuringResizeUpdates() {
        val t = tile("app-a", column = 0, row = 0, spanColumns = 1, spanRows = 1)
        val placements = listOf(t)

        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(2, 2), placements, columns = 6, rows = 10)

        assertEquals(EditMode.RESIZE, (session as HomeEditSession.Active).mode)
    }
}