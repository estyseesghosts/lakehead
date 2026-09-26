package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutStripGeometryTest {

    // ── isNarrow ──────────────────────────────────────────────────────────

    @Test
    fun wideSpansAreNotNarrow() {
        assertFalse(ShortcutStripGeometry.isNarrow(GridSpan(1, 1)))
        assertFalse(ShortcutStripGeometry.isNarrow(GridSpan(2, 1)))
        assertFalse(ShortcutStripGeometry.isNarrow(GridSpan(3, 1)))
        assertFalse(ShortcutStripGeometry.isNarrow(GridSpan(2, 2)))
        assertFalse(ShortcutStripGeometry.isNarrow(GridSpan(3, 2)))
        assertFalse(ShortcutStripGeometry.isNarrow(GridSpan(4, 2)))
        assertFalse(ShortcutStripGeometry.isNarrow(GridSpan(3, 3)))
    }

    @Test
    fun narrowSpansAreSingleColumnMultiRow() {
        assertTrue(ShortcutStripGeometry.isNarrow(GridSpan(1, 2)))
        assertTrue(ShortcutStripGeometry.isNarrow(GridSpan(1, 3)))
        assertTrue(ShortcutStripGeometry.isNarrow(GridSpan(1, 4)))
    }

    // ── anchorFor ─────────────────────────────────────────────────────────

    @Test
    fun wideSpansAnchorBottomRight() {
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
            ShortcutStripGeometry.anchorFor(GridSpan(1, 1)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
            ShortcutStripGeometry.anchorFor(GridSpan(2, 1)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
            ShortcutStripGeometry.anchorFor(GridSpan(3, 1)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
            ShortcutStripGeometry.anchorFor(GridSpan(2, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
            ShortcutStripGeometry.anchorFor(GridSpan(3, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
            ShortcutStripGeometry.anchorFor(GridSpan(4, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
            ShortcutStripGeometry.anchorFor(GridSpan(3, 3)),
        )
    }

    @Test
    fun narrowSpansAnchorBottomCenter() {
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_CENTER,
            ShortcutStripGeometry.anchorFor(GridSpan(1, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_CENTER,
            ShortcutStripGeometry.anchorFor(GridSpan(1, 3)),
        )
        assertEquals(
            ShortcutStripGeometry.ShortcutAnchor.BOTTOM_CENTER,
            ShortcutStripGeometry.anchorFor(GridSpan(1, 4)),
        )
    }

    // ── directionFor ──────────────────────────────────────────────────────

    @Test
    fun wideSpansExtendLeftward() {
        assertEquals(
            ShortcutStripGeometry.StripDirection.LEFTWARD,
            ShortcutStripGeometry.directionFor(GridSpan(1, 1)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.LEFTWARD,
            ShortcutStripGeometry.directionFor(GridSpan(2, 1)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.LEFTWARD,
            ShortcutStripGeometry.directionFor(GridSpan(3, 1)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.LEFTWARD,
            ShortcutStripGeometry.directionFor(GridSpan(2, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.LEFTWARD,
            ShortcutStripGeometry.directionFor(GridSpan(3, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.LEFTWARD,
            ShortcutStripGeometry.directionFor(GridSpan(4, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.LEFTWARD,
            ShortcutStripGeometry.directionFor(GridSpan(3, 3)),
        )
    }

    @Test
    fun narrowSpansExtendUpward() {
        assertEquals(
            ShortcutStripGeometry.StripDirection.UPWARD,
            ShortcutStripGeometry.directionFor(GridSpan(1, 2)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.UPWARD,
            ShortcutStripGeometry.directionFor(GridSpan(1, 3)),
        )
        assertEquals(
            ShortcutStripGeometry.StripDirection.UPWARD,
            ShortcutStripGeometry.directionFor(GridSpan(1, 4)),
        )
    }

    // ── maxSlots ──────────────────────────────────────────────────────────

    @Test
    fun singleBySingleHasNoSlots() {
        assertEquals(0, ShortcutStripGeometry.maxSlots(GridSpan(1, 1)))
    }

    @Test
    fun singleByTwoHasOneSlot() {
        assertEquals(1, ShortcutStripGeometry.maxSlots(GridSpan(1, 2)))
    }

    @Test
    fun singleByThreeHasTwoSlots() {
        assertEquals(2, ShortcutStripGeometry.maxSlots(GridSpan(1, 3)))
    }

    @Test
    fun twoByAnyHasOneSlot() {
        assertEquals(1, ShortcutStripGeometry.maxSlots(GridSpan(2, 1)))
        assertEquals(1, ShortcutStripGeometry.maxSlots(GridSpan(2, 2)))
        assertEquals(1, ShortcutStripGeometry.maxSlots(GridSpan(2, 3)))
        assertEquals(1, ShortcutStripGeometry.maxSlots(GridSpan(2, 5)))
    }

    @Test
    fun threeByAnyHasTwoSlots() {
        assertEquals(2, ShortcutStripGeometry.maxSlots(GridSpan(3, 1)))
        assertEquals(2, ShortcutStripGeometry.maxSlots(GridSpan(3, 2)))
        assertEquals(2, ShortcutStripGeometry.maxSlots(GridSpan(3, 3)))
    }

    @Test
    fun fourByAnyHasThreeSlots() {
        assertEquals(3, ShortcutStripGeometry.maxSlots(GridSpan(4, 1)))
        assertEquals(3, ShortcutStripGeometry.maxSlots(GridSpan(4, 2)))
        assertEquals(3, ShortcutStripGeometry.maxSlots(GridSpan(4, 3)))
    }

    @Test
    fun maxSlotsExplicitColumnsRows() {
        assertEquals(0, ShortcutStripGeometry.maxSlots(1, 1))
        assertEquals(1, ShortcutStripGeometry.maxSlots(1, 2))
        assertEquals(2, ShortcutStripGeometry.maxSlots(1, 3))
        assertEquals(1, ShortcutStripGeometry.maxSlots(2, 1))
        assertEquals(2, ShortcutStripGeometry.maxSlots(3, 1))
        assertEquals(3, ShortcutStripGeometry.maxSlots(4, 1))
    }

    @Test
    fun maxSlotsReturnsZeroForNonPositiveDimensions() {
        assertEquals(0, ShortcutStripGeometry.maxSlots(0, 4))
        assertEquals(0, ShortcutStripGeometry.maxSlots(4, 0))
        assertEquals(0, ShortcutStripGeometry.maxSlots(0, 0))
        assertEquals(0, ShortcutStripGeometry.maxSlots(-1, 4))
        assertEquals(0, ShortcutStripGeometry.maxSlots(4, -1))
    }

    // ── slotPitchPx ───────────────────────────────────────────────────────

    @Test
    fun pitchSumsCellAndGap() {
        assertEquals(187L + 8L, ShortcutStripGeometry.slotPitchPx(187L, 8L))
        assertEquals(100L + 0L, ShortcutStripGeometry.slotPitchPx(100L, 0L))
    }

    @Test
    fun pitchIsLongSafe() {
        val largeCell = 1_000_000_000L
        val largeGap = 500_000_000L
        assertEquals(1_500_000_000L, ShortcutStripGeometry.slotPitchPx(largeCell, largeGap))
    }

    @Test
    fun pitchRejectsNonPositiveCell() {
        assertIllegalArgument { ShortcutStripGeometry.slotPitchPx(0L, 8L) }
        assertIllegalArgument { ShortcutStripGeometry.slotPitchPx(-1L, 8L) }
    }

    @Test
    fun pitchRejectsNegativeGap() {
        assertIllegalArgument { ShortcutStripGeometry.slotPitchPx(100L, -1L) }
    }

    @Test
    fun pitchThrowsOnOverflow() {
        assertArithmeticException {
            ShortcutStripGeometry.slotPitchPx(Long.MAX_VALUE, 1L)
        }
    }

    // ── slotCenterPx ──────────────────────────────────────────────────────

    @Test
    fun slotZeroIsAtAnchor() {
        assertEquals(500L, ShortcutStripGeometry.slotCenterPx(500L, 0, 195L))
    }

    @Test
    fun slotOneIsOnePitchAwayFromAnchor() {
        assertEquals(500L - 195L, ShortcutStripGeometry.slotCenterPx(500L, 1, 195L))
    }

    @Test
    fun slotTwoIsTwoPitchesAwayFromAnchor() {
        assertEquals(500L - 2 * 195L, ShortcutStripGeometry.slotCenterPx(500L, 2, 195L))
    }

    @Test
    fun slotZeroNearestStability() {
        // Slot 0 at anchor 1000 with pitch 187 should stay at 1000
        assertEquals(1000L, ShortcutStripGeometry.slotCenterPx(1000L, 0, 187L))
    }

    @Test
    fun slotCenterPxIsLongSafe() {
        val anchor = 10_000_000_000L
        val pitch = 195L
        assertEquals(
            anchor - 5L * pitch,
            ShortcutStripGeometry.slotCenterPx(anchor, 5, pitch),
        )
    }

    @Test
    fun slotCenterRejectsNegativeIndex() {
        assertIllegalArgument { ShortcutStripGeometry.slotCenterPx(500L, -1, 195L) }
    }

    @Test
    fun slotCenterRejectsNonPositivePitch() {
        assertIllegalArgument { ShortcutStripGeometry.slotCenterPx(500L, 0, 0L) }
        assertIllegalArgument { ShortcutStripGeometry.slotCenterPx(500L, 0, -1L) }
    }

    @Test
    fun slotCenterThrowsOnOverflow() {
        assertArithmeticException {
            ShortcutStripGeometry.slotCenterPx(0L, Int.MAX_VALUE, Long.MAX_VALUE)
        }
    }

    // ── visibleCount ──────────────────────────────────────────────────────

    @Test
    fun visibleCountClampsToMaxSlots() {
        // 1x1 → maxSlots=0, any pinned clamps to 0
        assertEquals(0, ShortcutStripGeometry.visibleCount(0, GridSpan(1, 1)))
        assertEquals(0, ShortcutStripGeometry.visibleCount(5, GridSpan(1, 1)))
    }

    @Test
    fun visibleCountPassesThroughWhenInRange() {
        assertEquals(0, ShortcutStripGeometry.visibleCount(0, GridSpan(3, 2)))
        assertEquals(1, ShortcutStripGeometry.visibleCount(1, GridSpan(3, 2)))
        assertEquals(2, ShortcutStripGeometry.visibleCount(2, GridSpan(3, 2)))
    }

    @Test
    fun visibleCountClampsUpperBoundForVariousSpans() {
        assertEquals(1, ShortcutStripGeometry.visibleCount(10, GridSpan(1, 2)))
        assertEquals(2, ShortcutStripGeometry.visibleCount(10, GridSpan(1, 3)))
        assertEquals(1, ShortcutStripGeometry.visibleCount(10, GridSpan(2, 2)))
        assertEquals(2, ShortcutStripGeometry.visibleCount(10, GridSpan(3, 2)))
        assertEquals(3, ShortcutStripGeometry.visibleCount(10, GridSpan(4, 2)))
    }

    @Test
    fun visibleCountClampsNegativePinnedToZero() {
        assertEquals(0, ShortcutStripGeometry.visibleCount(-1, GridSpan(3, 2)))
        assertEquals(0, ShortcutStripGeometry.visibleCount(-5, GridSpan(1, 4)))
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    private fun assertArithmeticException(block: () -> Unit) {
        try {
            block()
        } catch (_: ArithmeticException) {
            return
        }
        throw AssertionError("expected ArithmeticException")
    }
}