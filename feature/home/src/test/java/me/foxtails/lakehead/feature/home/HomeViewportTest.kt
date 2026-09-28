package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridRest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewportTest {

    // ── 1. Short→tall epsilon: fails if snaps to top ───────────────────────

    @Test
    fun shortToTall_epsilonRange_snapsToTop() {
        // Old short viewport at origin 600 (bias position). New tall rest
        // with a tiny scroll range of 1px (at epsilon threshold).
        // Anchor offset = 0 - 600 = -600, but range ≤ epsilon → snap to top.
        val old = Viewport(gridOriginY = 600L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 1L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(1L, result.scrollRangePx)
    }

    @Test
    fun shortToTall_rangeBelowEpsilon_snapsToTop() {
        // Range exactly equals epsilon → still at/below threshold, so snap.
        val old = Viewport(gridOriginY = 540L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = Viewport.PRESERVE_ANCHOR_EPSILON, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(Viewport.PRESERVE_ANCHOR_EPSILON, result.scrollRangePx)
    }

    @Test
    fun shortToTall_rangeAboveEpsilon_preservesAnchor() {
        // Range comfortably above epsilon → preserve the anchor.
        // Anchor offset = old.gridOriginY - newOrigin = 540 - 0 = 540.
        // Clamped to [-4000, 0] = 0 (positive origin → content at top).
        val old = Viewport(gridOriginY = 540L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 4000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(4000L, result.scrollRangePx)
    }

    @Test
    fun shortToTall_preservesAnchor_withReasonableRange() {
        // Content was centered at origin 540 in a 2000px viewport with 800px content.
        // Now content grows to 6000px (range = 4000). Anchor offset = 540 - 0 = 540.
        // Clamped to [-4000, 0] = 0 (positive origin → content at top).
        val old = Viewport(gridOriginY = 540L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 4000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(4000L, result.scrollRangePx)
    }

    @Test
    fun shortToTall_preservesAnchor_clampedToRange() {
        // Anchor offset = 5000 - 0 = 5000, clamped to [-100, 0] = 0.
        val old = Viewport(gridOriginY = 5000L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 100L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(100L, result.scrollRangePx)
    }

    @Test
    fun shortToTall_negativeOrigin_preservesAnchor() {
        // Old origin -200, new origin 0. Anchor offset = -200 - 0 = -200.
        // Clamped to [-4000, 0] = -200 (content was above viewport, preserved).
        val old = Viewport(gridOriginY = -200L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 4000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(-200L, result.viewportOffsetY)
        assertEquals(4000L, result.scrollRangePx)
    }

    // ── 2. Tall→short range 0 ──────────────────────────────────────────────

    @Test
    fun tallToShort_rangeZero_collapsesToZero() {
        // Tall content with scroll range 4000, scrolled partway. Now content
        // fits: range becomes 0, offset collapses.
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -2000L, scrollRangePx = 4000L)
        val newRest = GridRest(gridOriginY = 540L, scrollRangePx = 0L, scrollable = false)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(540L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(0L, result.scrollRangePx)
    }

    @Test
    fun tallToShort_scrolledToBottom_collapsesToZero() {
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -4000L, scrollRangePx = 4000L)
        val newRest = GridRest(gridOriginY = 600L, scrollRangePx = 0L, scrollable = false)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(600L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(0L, result.scrollRangePx)
    }

    @Test
    fun tallToShort_atRest_collapsesToZero() {
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = 4000L)
        val newRest = GridRest(gridOriginY = 400L, scrollRangePx = 0L, scrollable = false)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(400L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(0L, result.scrollRangePx)
    }

    // ── 3. Tall→tall clamp + bottom-pin ────────────────────────────────────

    @Test
    fun tallToTall_preservesAnchor() {
        // Old: origin=0, offset=-1000, range=4000. New: origin=0, range=6000.
        // anchorOffset = (0 - 0) + (-1000) = -1000. Clamped to [-6000, 0] = -1000.
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -1000L, scrollRangePx = 4000L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 6000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(-1000L, result.viewportOffsetY)
        assertEquals(6000L, result.scrollRangePx)
    }

    @Test
    fun tallToTall_originShift_preservesAnchor() {
        // Old: origin=-200, offset=-500, range=4000. New: origin=-100, range=5000.
        // anchorOffset = (-200 - (-100)) + (-500) = -100 + (-500) = -600.
        // Clamped to [-5000, 0] = -600.
        val old = Viewport(gridOriginY = -200L, viewportOffsetY = -500L, scrollRangePx = 4000L)
        val newRest = GridRest(gridOriginY = -100L, scrollRangePx = 5000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(-100L, result.gridOriginY)
        assertEquals(-600L, result.viewportOffsetY)
        assertEquals(5000L, result.scrollRangePx)
    }

    @Test
    fun tallToTall_shrinkRange_clampsToNewRange() {
        // Old: origin=0, offset=-3000, range=4000. New: range=2000.
        // anchorOffset = (0 - 0) + (-3000) = -3000. Clamped to [-2000, 0] = -2000.
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -3000L, scrollRangePx = 4000L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 2000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(-2000L, result.viewportOffsetY)
        assertEquals(2000L, result.scrollRangePx)
    }

    @Test
    fun tallToTall_growRange_preservesAnchor() {
        // Content grows: range goes from 1000 to 5000. User was at the bottom
        // (offset = -1000). Anchor delta = 0 + (-1000) = -1000. Clamped to
        // [-5000, 0] = -1000. The user stays at the same scroll position.
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -1000L, scrollRangePx = 1000L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 5000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(-1000L, result.viewportOffsetY)
        assertEquals(5000L, result.scrollRangePx)
    }

    @Test
    fun tallToTall_rangeBoundary_preservesAnchor() {
        // Small range boundary: new range = 2. Old offset at -1.
        // anchorOffset = (0 - 0) + (-1) = -1. Clamped to [-2, 0] = -1.
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -1L, scrollRangePx = 4000L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 2L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(-1L, result.viewportOffsetY)
        assertEquals(2L, result.scrollRangePx)
    }

    @Test
    fun tallToTall_shrinkRange_bottomPinsToNewBottom() {
        // Content shrinks: range goes from 5000 to 2000. User was near bottom
        // (offset = -4500). Anchor delta = 0 + (-4500) = -4500. Clamped to
        // [-2000, 0] = -2000 (pinned to new bottom).
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -4500L, scrollRangePx = 5000L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 2000L, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(-2000L, result.viewportOffsetY)
        assertEquals(2000L, result.scrollRangePx)
    }

    // ── 4. Null→zero ───────────────────────────────────────────────────────

    @Test
    fun preserveAnchorOnResize_nullNewRest_returnsZeroViewport() {
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -1000L, scrollRangePx = 4000L)
        val result = preserveAnchorOnResize(old, null)
        assertEquals(Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = 0L), result)
    }

    @Test
    fun viewportFor_null_returnsZeroViewport() {
        val result = viewportFor(null)
        assertEquals(Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = 0L), result)
    }

    // ── 5. Empty/short → zero ──────────────────────────────────────────────

    @Test
    fun emptyToZero_preserveAnchor_returnsZeroViewport() {
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = 0L, scrollable = false)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = 0L), result)
    }

    @Test
    fun shortToShort_returnsNewRestViewport() {
        val old = Viewport(gridOriginY = 540L, viewportOffsetY = 0L, scrollRangePx = 0L)
        val newRest = GridRest(gridOriginY = 600L, scrollRangePx = 0L, scrollable = false)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(600L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(0L, result.scrollRangePx)
    }

    // ── 6. clampViewportOffset bands ───────────────────────────────────────

    @Test
    fun clampViewportOffset_zeroRange_returnsZero() {
        assertEquals(0L, clampViewportOffset(0L, 0L))
        assertEquals(0L, clampViewportOffset(-100L, 0L))
        assertEquals(0L, clampViewportOffset(100L, 0L))
    }

    @Test
    fun clampViewportOffset_inRange_unchanged() {
        assertEquals(-500L, clampViewportOffset(-500L, 1000L))
        assertEquals(-1000L, clampViewportOffset(-1000L, 1000L))
        assertEquals(0L, clampViewportOffset(0L, 1000L))
    }

    @Test
    fun clampViewportOffset_belowRange_clampedToUpperBound() {
        assertEquals(-1000L, clampViewportOffset(-2000L, 1000L))
    }

    @Test
    fun clampViewportOffset_aboveRange_clampedToLowerBound() {
        assertEquals(0L, clampViewportOffset(500L, 1000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun clampViewportOffset_negativeRange_throws() {
        clampViewportOffset(0L, -1L)
    }

    // ── 7. viewportFor ─────────────────────────────────────────────────────

    @Test
    fun viewportFor_shortContent_returnsBiasOrigin() {
        val rest = GridRest(gridOriginY = 540L, scrollRangePx = 0L, scrollable = false)
        val result = viewportFor(rest)
        assertEquals(540L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(0L, result.scrollRangePx)
    }

    @Test
    fun viewportFor_tallContent_returnsAnchoredOrigin() {
        val rest = GridRest(gridOriginY = 0L, scrollRangePx = 4000L, scrollable = true)
        val result = viewportFor(rest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(0L, result.viewportOffsetY)
        assertEquals(4000L, result.scrollRangePx)
    }

    // ── 8. Viewport init validation ────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun negativeScrollRange_throws() {
        Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun offsetAboveZero_throws() {
        Viewport(gridOriginY = 0L, viewportOffsetY = 1L, scrollRangePx = 100L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun offsetBelowRange_throws() {
        Viewport(gridOriginY = 0L, viewportOffsetY = -101L, scrollRangePx = 100L)
    }

    @Test
    fun zeroRange_zeroOffset_valid() {
        val v = Viewport(gridOriginY = 540L, viewportOffsetY = 0L, scrollRangePx = 0L)
        assertEquals(540L, v.gridOriginY)
        assertEquals(0L, v.viewportOffsetY)
        assertEquals(0L, v.scrollRangePx)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroRange_negativeOffset_throws() {
        Viewport(gridOriginY = 0L, viewportOffsetY = -1L, scrollRangePx = 0L)
    }

    // ── 9. Long safety ─────────────────────────────────────────────────────

    @Test
    fun clampViewportOffset_longBounds_handledSafely() {
        // Long.MIN_VALUE < -Long.MAX_VALUE, so coerceIn clamps it to -Long.MAX_VALUE.
        assertEquals(-Long.MAX_VALUE, clampViewportOffset(Long.MIN_VALUE, Long.MAX_VALUE))
        assertEquals(0L, clampViewportOffset(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(-Long.MAX_VALUE, clampViewportOffset(-Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun tallToTall_hugeRange_clampedSafely() {
        val old = Viewport(gridOriginY = 0L, viewportOffsetY = -1000L, scrollRangePx = Long.MAX_VALUE)
        val newRest = GridRest(gridOriginY = 0L, scrollRangePx = Long.MAX_VALUE, scrollable = true)
        val result = preserveAnchorOnResize(old, newRest)
        assertEquals(0L, result.gridOriginY)
        assertEquals(-1000L, result.viewportOffsetY)
        assertEquals(Long.MAX_VALUE, result.scrollRangePx)
    }

    // ── 10. ScrollState-owner clamp (softlock) ──────────────────────────

    @Test
    fun scrollOwner_inRange_preserved() {
        assertEquals(500, clampScrollOffsetToRange(500, 1000L))
        assertEquals(0, clampScrollOffsetToRange(0, 1000L))
        assertEquals(1000, clampScrollOffsetToRange(1000, 1000L))
    }

    @Test
    fun scrollOwner_shrink_clampsToNewCommittedMax() {
        // Real offset 3000 with a shrunk committed range 2000 bottom-pins.
        assertEquals(2000, clampScrollOffsetToRange(3000, 2000L))
    }

    @Test
    fun scrollOwner_grow_preservesOffset() {
        // Content growth never jumps: an in-range offset stays put.
        assertEquals(1000, clampScrollOffsetToRange(1000, 5000L))
    }

    @Test
    fun scrollOwner_zeroRange_restsAtZero() {
        assertEquals(0, clampScrollOffsetToRange(500, 0L))
        assertEquals(0, clampScrollOffsetToRange(0, 0L))
    }

    @Test
    fun scrollOwner_intCapped_hugeRange() {
        assertEquals(Int.MAX_VALUE, clampScrollOffsetToRange(Int.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(100, clampScrollOffsetToRange(100, Long.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scrollOwner_negativeActual_throws() {
        clampScrollOffsetToRange(-1, 100L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scrollOwner_negativeRange_throws() {
        clampScrollOffsetToRange(0, -1L)
    }

    @Test
    fun scrollForCommitted_invalidRestZero_recomputesWhenPositive() {
        // Null rest (invalid/zero viewport) rests at zero; a positive rest
        // recomputes the clamped actual.
        assertEquals(0, scrollOffsetForCommitted(500, null))
        assertEquals(0, scrollOffsetForCommitted(0, null))
        assertEquals(0L, committedScrollRangePx(null))
        val rest = GridRest(gridOriginY = 0L, scrollRangePx = 108L, scrollable = true)
        assertEquals(108L, committedScrollRangePx(rest))
        assertEquals(50, scrollOffsetForCommitted(50, rest))
        assertEquals(108, scrollOffsetForCommitted(500, rest))
    }
}