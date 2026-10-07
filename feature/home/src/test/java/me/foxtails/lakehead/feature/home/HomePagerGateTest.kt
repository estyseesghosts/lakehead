package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePagerGateTest {

    @Test
    fun tileOriginWinnersHoldAndBackgroundStaysFree() {
        val gate = HomePagerGate()
        listOf(
            HomeGestureWinner.UNPIN, HomeGestureWinner.HANDLE, HomeGestureWinner.ACTIVE_RESIZE,
            HomeGestureWinner.ACTIVE_MOVE, HomeGestureWinner.SELECT_DRAG, HomeGestureWinner.TAP,
        ).forEach {
            gate.claim(it)
            assertTrue("$it holds", gate.hold)
        }
        gate.claim(HomeGestureWinner.BACKGROUND)
        assertFalse(gate.hold)
    }

    @Test
    fun releaseIsIdempotentAndEndsTheHold() {
        val gate = HomePagerGate()
        gate.claim(HomeGestureWinner.ACTIVE_MOVE)
        gate.release()
        gate.release()
        assertFalse(gate.hold)
    }

    @Test
    fun axisLockYieldsOnlyWhenHorizontalAndResets() {
        val gate = HomePagerGate()
        assertFalse(gate.yieldsToPager)
        gate.lock(LockedAxis.VERTICAL)
        assertFalse(gate.yieldsToPager)
        gate.lock(LockedAxis.HORIZONTAL)
        assertTrue(gate.yieldsToPager)
        gate.resetAxis()
        assertEquals(LockedAxis.UNDECIDED, gate.axisLock)
    }

    @Test
    fun claimingAndReleasingNeverTouchesTheAxisLock() {
        val gate = HomePagerGate()
        gate.lock(LockedAxis.HORIZONTAL)
        gate.claim(HomeGestureWinner.ACTIVE_MOVE)
        gate.release()
        assertEquals(LockedAxis.HORIZONTAL, gate.axisLock)
    }
}
