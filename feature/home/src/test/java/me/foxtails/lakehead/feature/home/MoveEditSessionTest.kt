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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveEditSessionTest {

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

        val session = MoveEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)

        assertTrue(session is MoveEditSession.Active)
        val active = session as MoveEditSession.Active
        assertEquals(ItemId("app-a"), active.itemId)
        assertEquals(LogicalPosition(2, 3), active.origin)
        assertEquals(LogicalSpan(1, 1), active.span)
        assertEquals(LogicalPosition(2, 3), active.candidate)
        assertEquals(CandidateValidity.VALID, active.candidateValidity)
    }

    @Test
    fun enterIfLaunchable_nonLaunchableTile_staysIdle() {
        val t = tile("app-u", launchable = false)
        val placements = listOf(t)

        val session = MoveEditSession.Idle.enterIfLaunchable(t, placements, columns = 6, rows = 10)

        assertTrue(session is MoveEditSession.Idle)
    }

    // ---- updateCandidate ----

    @Test
    fun updateCandidate_freeCell_valid() {
        val moving = tile("app-a", column = 0, row = 0)
        val obstacle = tile("app-b", column = 3, row = 3)
        val placements = listOf(moving, obstacle)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateCandidate(LogicalPosition(4, 0), placements, columns = 6, rows = 10)

        val active = session as MoveEditSession.Active
        assertEquals(LogicalPosition(4, 0), active.candidate)
        assertEquals(CandidateValidity.VALID, active.candidateValidity)
    }

    @Test
    fun updateCandidate_occupiedCell_colliding() {
        val moving = tile("app-a", column = 0, row = 0)
        val obstacle = tile("app-b", column = 2, row = 2)
        val placements = listOf(moving, obstacle)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateCandidate(LogicalPosition(2, 2), placements, columns = 6, rows = 10)

        val active = session as MoveEditSession.Active
        assertEquals(CandidateValidity.COLLIDING, active.candidateValidity)
    }

    @Test
    fun updateCandidate_pastColumns_outOfBounds() {
        val moving = tile("app-a", column = 0, row = 0)
        val placements = listOf(moving)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateCandidate(LogicalPosition(6, 0), placements, columns = 6, rows = 10)

        val active = session as MoveEditSession.Active
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, active.candidateValidity)
    }

    @Test
    fun updateCandidate_pastRows_outOfBounds() {
        val moving = tile("app-a", column = 0, row = 0)
        val placements = listOf(moving)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateCandidate(LogicalPosition(0, 10), placements, columns = 6, rows = 10)

        val active = session as MoveEditSession.Active
        assertEquals(CandidateValidity.OUT_OF_BOUNDS, active.candidateValidity)
    }

    @Test
    fun updateCandidate_ownOrigin_valid_selfFootprintIgnored() {
        val moving = tile("app-a", column = 1, row = 1)
        val obstacle = tile("app-b", column = 3, row = 3)
        val placements = listOf(moving, obstacle)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateCandidate(LogicalPosition(1, 1), placements, columns = 6, rows = 10)

        val active = session as MoveEditSession.Active
        assertEquals(CandidateValidity.VALID, active.candidateValidity)
    }

    // ---- cancel ----

    @Test
    fun cancel_returnsIdle() {
        val moving = tile("app-a")
        val placements = listOf(moving)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .cancel()

        assertTrue(session is MoveEditSession.Idle)
    }

    @Test
    fun cancel_idleSession_returnsIdle() {
        val session = MoveEditSession.Idle.cancel()
        assertTrue(session is MoveEditSession.Idle)
    }

    // ---- hasMoved ----

    @Test
    fun hasMoved_candidateEqualsOrigin_false() {
        val moving = tile("app-a", column = 2, row = 3)
        val placements = listOf(moving)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)

        assertFalse(session.hasMoved())
    }

    @Test
    fun hasMoved_candidateDiffersFromOrigin_true() {
        val moving = tile("app-a", column = 0, row = 0)
        val placements = listOf(moving)

        val session = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)
            .updateCandidate(LogicalPosition(1, 0), placements, columns = 6, rows = 10)

        assertTrue(session.hasMoved())
    }

    @Test
    fun hasMoved_idleSession_false() {
        assertFalse(MoveEditSession.Idle.hasMoved())
    }

    // ---- multiple tiles as obstacles ----

    @Test
    fun multipleTiles_otherTilesBecomeObstaclesForValidity() {
        val moving = tile("app-a", column = 0, row = 0)
        val obstacle1 = tile("app-b", column = 2, row = 0)
        val obstacle2 = tile("app-c", column = 4, row = 0, spanColumns = 2)
        val placements = listOf(moving, obstacle1, obstacle2)

        val enter = MoveEditSession.Idle.enterIfLaunchable(moving, placements, columns = 6, rows = 10)

        // Free cell at (1,0) → VALID
        val atFree = enter.updateCandidate(LogicalPosition(1, 0), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.VALID, (atFree as MoveEditSession.Active).candidateValidity)

        // Obstacle1 at (2,0) → COLLIDING
        val atObstacle1 = enter.updateCandidate(LogicalPosition(2, 0), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.COLLIDING, (atObstacle1 as MoveEditSession.Active).candidateValidity)

        // Obstacle2 spans (4,0)..(5,0), so (5,0) is within its span → COLLIDING
        val atObstacle2Edge = enter.updateCandidate(LogicalPosition(5, 0), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.COLLIDING, (atObstacle2Edge as MoveEditSession.Active).candidateValidity)

        // Cell beyond all obstacles → VALID
        val atFreeEnd = enter.updateCandidate(LogicalPosition(3, 5), placements, columns = 6, rows = 10)
        assertEquals(CandidateValidity.VALID, (atFreeEnd as MoveEditSession.Active).candidateValidity)
    }

    // ---- Idle is unchanged on no-op ----

    @Test
    fun updateCandidate_idleSession_returnsIdleUnchanged() {
        val result = MoveEditSession.Idle.updateCandidate(LogicalPosition(1, 1), emptyList(), 6, 10)
        assertTrue(result is MoveEditSession.Idle)
        assertSame(MoveEditSession.Idle, result)
    }
}