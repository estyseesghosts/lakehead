package me.foxtails.lakehead.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Radial touch slop: a tile drag starts in any direction, with no axis preference. */
class TouchSlopTest {

    private val slop = 10f

    @Test
    fun withinSlop_doesNotStart() {
        assertFalse(exceedsTouchSlop(0f, 0f, slop))
        assertFalse(exceedsTouchSlop(7f, 7f, slop))
        assertFalse(exceedsTouchSlop(10f, 0f, slop))
    }

    @Test
    fun everyDirectionStartsOnceFarEnough() {
        assertTrue(exceedsTouchSlop(11f, 0f, slop))
        assertTrue(exceedsTouchSlop(-11f, 0f, slop))
        assertTrue(exceedsTouchSlop(0f, 11f, slop))
        assertTrue(exceedsTouchSlop(0f, -11f, slop))
    }

    @Test
    fun trueDiagonalStarts_whichTheAxisClassifierWouldHoldUndecided() {
        // 45°: neither axis is 1.25x the other, so classifyHomeAxis stays undecided.
        assertTrue(exceedsTouchSlop(8f, 8f, slop))
        assertTrue(exceedsTouchSlop(-8f, 8f, slop))
        assertTrue(exceedsTouchSlop(8f, -8f, slop))
    }
}
