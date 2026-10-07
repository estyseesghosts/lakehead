package me.foxtails.lakehead.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers auto-scroll helpers (`autoScrollZone`, `autoScrollVelocityPxPerSec`) and `viewportRowsFor` division behaviour.
 */
class HomeAutoScrollTest {

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

    private val maxSpeed = 1000f

    @Test
    fun autoScrollVelocity_outsideBand_zero() {
        assertEquals(0f, autoScrollVelocityPxPerSec(900f, 1800, 96, maxSpeed), 0f)
        assertEquals(0f, autoScrollVelocityPxPerSec(0f, 0, 96, maxSpeed), 0f)
    }

    @Test
    fun autoScrollVelocity_signedAndSymmetric() {
        val up = autoScrollVelocityPxPerSec(24f, 1800, 96, maxSpeed)
        val down = autoScrollVelocityPxPerSec(1776f, 1800, 96, maxSpeed)
        assertTrue(up < 0f && down > 0f)
        assertEquals(-up, down, 0.001f)
    }

    @Test
    fun autoScrollVelocity_proportionalToDepth_zeroAtInnerBoundary_maxAtEdge() {
        assertEquals(0f, autoScrollVelocityPxPerSec(96f, 1800, 96, maxSpeed), 0f)
        assertEquals(500f, autoScrollVelocityPxPerSec(1752f, 1800, 96, maxSpeed), 0.001f)
        assertEquals(maxSpeed, autoScrollVelocityPxPerSec(1800f, 1800, 96, maxSpeed), 0.001f)
        // Past the real viewport edge clamps rather than accelerating further.
        assertEquals(maxSpeed, autoScrollVelocityPxPerSec(1900f, 1800, 96, maxSpeed), 0.001f)
        assertEquals(-maxSpeed, autoScrollVelocityPxPerSec(-50f, 1800, 96, maxSpeed), 0.001f)
    }

    @Test
    fun autoScrollBand_isMeasuredFromTheViewportEdge() {
        // A pointer 40px above the true bottom of a 2000px viewport is in the band,
        // even though it would be mid-screen against a shorter usable height.
        assertEquals(AutoScrollZone.BOTTOM, autoScrollZone(1960f, 2000, 96))
        assertEquals(AutoScrollZone.NONE, autoScrollZone(1960f, 2400, 96))
    }
}
