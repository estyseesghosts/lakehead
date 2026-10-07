package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridVerticalExtent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A3 normal-viewport policy coverage for the home canvas.
 *
 * - [usableCanvasHeightPx]: raw viewport minus bottom clearance, floored at zero.
 * - Exact overflow through [signedRestFor]: short/exact-fit content is not scrollable
 *   (range 0, no scroll), one stride of overflow scrolls exactly one stride (the last
 *   cell is reachable with no tail).
 * - Floating pill through [contentContainerHeightPx]: the container is
 *   `max(full, natural + clearance)` (transparent reserve, no opaque spacer) and the
 *   range is the safe overflow, so short/exact-fit fills full with no scroll, one
 *   stride of overflow scrolls exactly one stride with the lowest tile clearing the
 *   pill, and viewport changes shrink the range without touching content.
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
    fun usableHeight_clearanceExceedsRaw_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            usableCanvasHeightPx(rawHeightPx = 100, bottomClearancePx = 200)
        }
        assertEquals(0, usableCanvasHeightPx(rawHeightPx = 0, bottomClearancePx = 0))
    }

    @Test
    fun usableHeight_clearanceEqualsPositiveRaw_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            usableCanvasHeightPx(rawHeightPx = 200, bottomClearancePx = 200)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun usableHeight_negativeRaw_throws() {
        usableCanvasHeightPx(rawHeightPx = -1, bottomClearancePx = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun usableHeight_negativeClearance_throws() {
        usableCanvasHeightPx(rawHeightPx = 2000, bottomClearancePx = -1)
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

    // ── floating pill: raw-vs-safe + transparent container ──────────

    @Test
    fun floatingShortContent_fillsFullWithZeroRange() {
        // Raw 2000, clearance 280 → safe 1720. Short 208px content fits safe:
        // container fills the full viewport, range 0, not scrollable.
        val raw = 2000
        val clearance = 280
        val safe = usableCanvasHeightPx(rawHeightPx = raw, bottomClearancePx = clearance)
        assertEquals(1720, safe)
        assertEquals(raw, contentContainerHeightPx(208, raw, clearance))
        val rest = signedRestFor(safe, 208, 108L)
        assertEquals(0L, rest?.scrollRangePx)
        assertFalse(rest?.scrollable ?: true)
    }

    @Test
    fun floatingExactSafeFit_fillsFullWithZeroRange() {
        // Natural exactly equals safe (1720): container is max(2000, 1720+280)=2000,
        // range 0 — the pill floats over background, no scroll.
        val raw = 2000
        val clearance = 280
        val safe = usableCanvasHeightPx(rawHeightPx = raw, bottomClearancePx = clearance)
        assertEquals(raw, contentContainerHeightPx(safe, raw, clearance))
        val rest = signedRestFor(safe, safe, 108L)
        assertEquals(0L, rest?.scrollRangePx)
        assertFalse(rest?.scrollable ?: true)
    }

    @Test
    fun floatingOverflow_containerAddsClearanceAndLowestClearsPill() {
        // Natural 1828 over safe 1720 by one stride: container 2108, and the
        // container-minus-full range equals the safe overflow, so the lowest
        // tile lands exactly the clearance above the viewport bottom.
        val raw = 2000
        val clearance = 280
        val safe = usableCanvasHeightPx(rawHeightPx = raw, bottomClearancePx = clearance)
        val container = contentContainerHeightPx(1828, raw, clearance)
        assertEquals(2108, container)
        assertEquals(108, container - raw)
        val rest = signedRestFor(safe, 1828, 108L)
        assertEquals(108L, rest?.scrollRangePx)
        assertTrue(rest?.scrollable ?: false)
        assertEquals(rest?.scrollRangePx?.toInt(), container - raw)
    }

    @Test
    fun floatingViewportChange_sameContentShrinksRange() {
        // Same 1828px content: widening raw 2000 → 2200 grows safe 1720 → 1920,
        // so the range collapses from 108 to 0 without touching the content.
        val clearance = 280
        val safeBefore = usableCanvasHeightPx(rawHeightPx = 2000, bottomClearancePx = clearance)
        val safeAfter = usableCanvasHeightPx(rawHeightPx = 2200, bottomClearancePx = clearance)
        assertEquals(108L, signedRestFor(safeBefore, 1828, 108L)?.scrollRangePx)
        assertEquals(0L, signedRestFor(safeAfter, 1828, 108L)?.scrollRangePx)
        assertEquals(2108, contentContainerHeightPx(1828, 2000, clearance))
        assertEquals(2200, contentContainerHeightPx(1828, 2200, clearance))
    }

    @Test
    fun floatingScrollEnabled_onlyWhenBeyondSafe() {
        // 1px over safe scrolls exactly 1px; 1px under safe never scrolls, even
        // though both exceed/fill the visible window differently.
        val raw = 2000
        val clearance = 280
        val safe = usableCanvasHeightPx(rawHeightPx = raw, bottomClearancePx = clearance)
        assertEquals(1L, signedRestFor(safe, safe + 1, 108L)?.scrollRangePx)
        assertTrue(signedRestFor(safe, safe + 1, 108L)?.scrollable ?: false)
        assertEquals(0L, signedRestFor(safe, safe - 1, 108L)?.scrollRangePx)
        assertFalse(signedRestFor(safe, safe - 1, 108L)?.scrollable ?: true)
    }

    @Test
    fun container_zeroClearance_fallsBackToMaxOfFullAndNatural() {
        assertEquals(2000, contentContainerHeightPx(208, 2000, 0))
        assertEquals(2152, contentContainerHeightPx(2152, 2000, 0))
        assertEquals(2000, contentContainerHeightPx(2000, 2000, 0))
    }

    @Test
    fun container_saturatesInsteadOfWrapping() {
        assertEquals(Int.MAX_VALUE, contentContainerHeightPx(Int.MAX_VALUE, 2000, 280))
        assertEquals(Int.MAX_VALUE, contentContainerHeightPx(Int.MAX_VALUE - 10, 100, 20))
    }

    @Test(expected = IllegalArgumentException::class)
    fun container_negativeNatural_throws() {
        contentContainerHeightPx(-1, 2000, 280)
    }

    @Test(expected = IllegalArgumentException::class)
    fun container_negativeFull_throws() {
        contentContainerHeightPx(208, -1, 280)
    }

    @Test(expected = IllegalArgumentException::class)
    fun container_negativeClearance_throws() {
        contentContainerHeightPx(208, 2000, -1)
    }
}
