package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.PixelRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsInHandleTest {

    private val bounds = PixelRect(left = 0, top = 0, right = 200, bottom = 200)
    private val handlePx = 48

    @Test
    fun center_isNotInHandle() {
        assertFalse(isInHandle(50f, 50f, bounds, handlePx))
    }

    @Test
    fun bottomRightCorner_isInHandle() {
        assertTrue(isInHandle(180f, 180f, bounds, handlePx))
    }

    @Test
    fun handleTopLeftEdge_isInclusive() {
        // Handle rect is [152, 200] x [152, 200].
        assertTrue(isInHandle(152f, 152f, bounds, handlePx))
    }

    @Test
    fun handleBottomRightEdge_isInclusive() {
        assertTrue(isInHandle(200f, 200f, bounds, handlePx))
    }

    @Test
    fun justOutsideHandle_isNotInHandle() {
        assertFalse(isInHandle(151f, 180f, bounds, handlePx))
        assertFalse(isInHandle(180f, 151f, bounds, handlePx))
        assertFalse(isInHandle(201f, 200f, bounds, handlePx))
        assertFalse(isInHandle(200f, 201f, bounds, handlePx))
    }

    @Test
    fun topLeftOfTile_isNotInHandle() {
        assertFalse(isInHandle(10f, 10f, bounds, handlePx))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveHandle_throws() {
        isInHandle(180f, 180f, bounds, 0)
    }
}
