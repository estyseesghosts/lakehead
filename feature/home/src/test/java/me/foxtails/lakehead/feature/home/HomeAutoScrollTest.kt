package me.foxtails.lakehead.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers auto-scroll helpers (`autoScrollZone`, `autoScrollStepPx`,
 * `autoScrollIntervalMs`) and `viewportRowsFor` division behaviour.
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
        assertEquals(HomeAutoScroll.AUTO_SCROLL_MIN_INTERVAL_MS, deep)
        assertTrue(inner!! > mid!! && mid > deep!!)
        assertTrue(inner in HomeAutoScroll.AUTO_SCROLL_MIN_INTERVAL_MS..
            HomeAutoScroll.AUTO_SCROLL_MAX_INTERVAL_MS)
        val innerBottom = autoScrollIntervalMs(1705f, viewportHeightPx = 1800, edgePx = 96)
        val deepBottom = autoScrollIntervalMs(1800f, viewportHeightPx = 1800, edgePx = 96)
        assertEquals(HomeAutoScroll.AUTO_SCROLL_MIN_INTERVAL_MS, deepBottom)
        assertEquals(inner, innerBottom)
    }
}