package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridVerticalExtent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A3 normal-viewport policy coverage for the home canvas.
 *
 * - [usableCanvasHeightPx]: raw viewport minus bottom clearance, floored at zero.
 * - [normalLegalityRows]: signed occupied bottom or usable viewport rows, never a margin.
 * - Exact overflow through [signedRestFor]: short/exact-fit content is not scrollable
 *   (range 0, no scroll), one stride of overflow scrolls exactly one stride (the last
 *   cell is reachable with no tail).
 */
class HomeNormalViewportTest {

    // ── usableCanvasHeightPx ─────────────────────────────────────────────

    @Test
    fun usableHeight_subtractsClearance() {
        assertEquals(1800, usableCanvasHeightPx(rawHeightPx = 2000, bottomClearancePx = 200))
    }

    @Test
    fun usableHeight_zeroClearance_returnsRaw() {
        assertEquals(2000, usableCanvasHeightPx(rawHeightPx = 2000, bottomClearancePx = 0))
    }

    @Test
    fun usableHeight_clearanceExceedsRaw_floorsAtZero() {
        assertEquals(0, usableCanvasHeightPx(rawHeightPx = 100, bottomClearancePx = 200))
        assertEquals(0, usableCanvasHeightPx(rawHeightPx = 0, bottomClearancePx = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun usableHeight_negativeRaw_throws() {
        usableCanvasHeightPx(rawHeightPx = -1, bottomClearancePx = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun usableHeight_negativeClearance_throws() {
        usableCanvasHeightPx(rawHeightPx = 2000, bottomClearancePx = -1)
    }

    // ── normalLegalityRows ───────────────────────────────────────────────

    @Test
    fun legalityRows_shortContent_coversUsableViewport() {
        // 2 occupied rows (bottom-exclusive 2) in an 1800px usable viewport with stride
        // 108: 1800 / 108 = 16 usable rows dominates, so empty visible rows stay
        // draggable-to.
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 2L,
            totalScrollableRows = 2L,
        )
        assertEquals(16, normalLegalityRows(extent, usableHeightPx = 1800, rowStridePx = 108L))
    }

    @Test
    fun legalityRows_tallContent_coversOccupiedBottom() {
        // 30 occupied rows overflows a 1000px usable viewport (9 rows): the occupied
        // bottom dominates so every existing tile stays in-bounds.
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 30L,
            totalScrollableRows = 30L,
        )
        assertEquals(30, normalLegalityRows(extent, usableHeightPx = 1000, rowStridePx = 108L))
    }

    @Test
    fun legalityRows_signedOrigin_keepsAbsoluteBottom() {
        // Tiles on rows 18..20 (minRow 18): the height count is 2 but the bound must
        // stay absolute (20, or the usable 16 — whichever is larger) or the existing
        // tiles fall out-of-bounds.
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 20L,
            totalScrollableRows = 2L,
            occupiedMinRow = 18L,
        )
        assertEquals(20, normalLegalityRows(extent, usableHeightPx = 1800, rowStridePx = 108L))
    }

    @Test
    fun legalityRows_zeroUsableAndEmpty_floorsAtOne() {
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 0L,
            totalScrollableRows = 0L,
        )
        assertEquals(1, normalLegalityRows(extent, usableHeightPx = 0, rowStridePx = 108L))
    }

    @Test
    fun legalityRows_fullyNegativeContent_neverFailsSizeValidation() {
        // Degenerate all-negative content: the bound floors at 1 instead of failing
        // GridRules' positive-rows requirement.
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = -1L,
            totalScrollableRows = 2L,
            occupiedMinRow = -3L,
        )
        assertEquals(1, normalLegalityRows(extent, usableHeightPx = 0, rowStridePx = 108L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun legalityRows_negativeUsable_throws() {
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 2L,
            totalScrollableRows = 2L,
        )
        normalLegalityRows(extent, usableHeightPx = -1, rowStridePx = 108L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun legalityRows_nonPositiveStride_throws() {
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 2L,
            totalScrollableRows = 2L,
        )
        normalLegalityRows(extent, usableHeightPx = 1800, rowStridePx = 0L)
    }

    // ── exact overflow: short / exact-fit / tall ─────────────────────────

    @Test
    fun exactFitContent_hasZeroRangeAndCannotScroll() {
        // Natural 1800 in usable 1800: exact fit, range 0, not scrollable.
        val rest = signedRestFor(
            viewportHeightPx = 1800,
            contentHeightPx = 1800,
            rowStridePx = 108L,
        )
        assertEquals(0L, rest?.scrollRangePx)
        assertFalse(rest?.scrollable ?: true)
    }

    @Test
    fun shortContent_hasZeroRangeAndCannotScroll() {
        val rest = signedRestFor(
            viewportHeightPx = 1800,
            contentHeightPx = 424,
            rowStridePx = 108L,
        )
        assertEquals(0L, rest?.scrollRangePx)
        assertFalse(rest?.scrollable ?: true)
    }

    @Test
    fun oneStrideOverflow_scrollsExactlyOneStrideWithNoTail() {
        // Natural one stride over usable: the range is exactly that stride, so
        // scrolling to the maximum lands the last cell flush with no tail.
        // 17 rows × 100px cells + 16 × 8px gaps = 1828; usable 1720 → range 108.
        val rest = signedRestFor(
            viewportHeightPx = 1720,
            contentHeightPx = 1828,
            rowStridePx = 108L,
        )
        assertEquals(108L, rest?.scrollRangePx)
        assertTrue(rest?.scrollable ?: false)
    }

    @Test
    fun onePixelOverflow_scrollsExactlyOnePixel() {
        // Committed range is max(0, height - usable): a 1px surplus scrolls 1px.
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 17L,
            totalScrollableRows = 17L,
        )
        // 17 rows × 100 + 16 × 8 = 1828; usable 1827 → range 1.
        assertEquals(1, committedScrollMaxPx(extent, 100, 8, 1827))
        val rest = signedRestFor(1827, 1828, 108L)
        assertEquals(1L, rest?.scrollRangePx)
        assertTrue(rest?.scrollable ?: false)
    }

    @Test
    fun multiStrideOverflow_rangeIsExactSurplus() {
        // 20 rows × 100 + 19 × 8 = 2152; usable 1720 → range 432 (4 strides).
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 20L,
            totalScrollableRows = 20L,
        )
        assertEquals(432, committedScrollMaxPx(extent, 100, 8, 1720))
        val rest = signedRestFor(1720, 2152, 108L)
        assertEquals(432L, rest?.scrollRangePx)
    }

    @Test
    fun bottomClearance_usableHeightDrivesCommittedRange() {
        // Raw 2000px viewport with 280px pill/gesture clearance → usable 1720.
        // Same 1828px content → committed range 108 (pill never covers last row).
        val usable = usableCanvasHeightPx(rawHeightPx = 2000, bottomClearancePx = 280)
        assertEquals(1720, usable)
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 17L,
            totalScrollableRows = 17L,
        )
        assertEquals(108, committedScrollMaxPx(extent, 100, 8, usable))
    }

    @Test
    fun committedMax_shortContent_isZero() {
        val extent = GridVerticalExtent(
            occupiedBottomRowExclusive = 2L,
            totalScrollableRows = 2L,
        )
        // 2 × 100 + 8 = 208 in usable 1800 → fits, range 0.
        assertEquals(0, committedScrollMaxPx(extent, 100, 8, 1800))
    }
}
