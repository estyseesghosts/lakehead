package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Test

class GridRowWindowSignedTest {

    // ── 1. negative firstRow construction ───────────────────────────────────

    @Test
    fun negativeFirstRowConstructsSuccessfully() {
        val window = GridRowWindow(-5L, -3L)
        assertEquals(-5L, window.firstRow)
        assertEquals(-3L, window.lastRowExclusive)
    }

    @Test
    fun negativeFirstRowWithZeroLastRow() {
        val window = GridRowWindow(-10L, 0L)
        assertEquals(-10L, window.firstRow)
        assertEquals(0L, window.lastRowExclusive)
    }

    @Test
    fun negativeFirstRowSpanningPositiveRange() {
        val window = GridRowWindow(-3L, 5L)
        assertEquals(-3L, window.firstRow)
        assertEquals(5L, window.lastRowExclusive)
    }

    @Test
    fun reversedNegativeRangeThrows() {
        assertIllegalArgument { GridRowWindow(-5L, -10L) }
    }

    // ── 2. withOverscan: no clamp to zero ───────────────────────────────────

    @Test
    fun withOverscanDoesNotClampToZero() {
        // (-3, 2) + 2 = (-5, 4)
        val window = GridRowWindow(-3L, 2L)
        val result = window.withOverscan(2L)
        assertEquals(GridRowWindow(-5L, 4L), result)
    }

    @Test
    fun withOverscanOnFullyNegativeWindow() {
        // (-10, -5) + 3 = (-13, -2)
        val window = GridRowWindow(-10L, -5L)
        val result = window.withOverscan(3L)
        assertEquals(GridRowWindow(-13L, -2L), result)
    }

    @Test
    fun withOverscanOnSpanningWindow() {
        // (-2, 3) + 1 = (-3, 4)
        val window = GridRowWindow(-2L, 3L)
        val result = window.withOverscan(1L)
        assertEquals(GridRowWindow(-3L, 4L), result)
    }

    @Test
    fun withOverscanZeroIsIdentity() {
        val window = GridRowWindow(-5L, 5L)
        assertEquals(window, window.withOverscan(0L))
    }

    @Test
    fun withOverscanOnEmptyWindowExpandsSymmetrically() {
        // (0, 0) + 3 = (-3, 3)
        val window = GridRowWindow(0L, 0L)
        assertEquals(GridRowWindow(-3L, 3L), window.withOverscan(3L))
    }

    // ── 3. withOverscan: saturation ─────────────────────────────────────────

    @Test
    fun withOverscanSaturatesLowerAtLongMin() {
        // Long.MIN_VALUE + 2 with overscan 5: MIN_VALUE + 2 - 5 = MIN_VALUE - 3 → overflow → MIN_VALUE
        val window = GridRowWindow(Long.MIN_VALUE + 2L, Long.MIN_VALUE + 3L)
        val result = window.withOverscan(5L)
        assertEquals(Long.MIN_VALUE, result.firstRow)
        assertEquals(Long.MIN_VALUE + 8L, result.lastRowExclusive)
    }

    @Test
    fun withOverscanSaturatesUpperAtLongMax() {
        val window = GridRowWindow(Long.MAX_VALUE - 5L, Long.MAX_VALUE - 1L)
        val result = window.withOverscan(3L)
        assertEquals(Long.MAX_VALUE - 8L, result.firstRow)
        assertEquals(Long.MAX_VALUE, result.lastRowExclusive)
    }

    @Test
    fun withOverscanSaturatesBothDirections() {
        // Near both extremes with enough overscan to saturate both sides
        val window = GridRowWindow(Long.MIN_VALUE + 2L, Long.MAX_VALUE - 2L)
        val result = window.withOverscan(5L)
        assertEquals(Long.MIN_VALUE, result.firstRow)
        assertEquals(Long.MAX_VALUE, result.lastRowExclusive)
    }

    @Test
    fun withOverscanSaturatingSubtractDoesNotClampToZero() {
        // (0, 2) + MAX_VALUE: firstRow = 0 - MAX_VALUE = -(MAX_VALUE) = MIN_VALUE + 1
        // (no ArithmeticException, just negative — not clamped to 0)
        val window = GridRowWindow(0L, 2L)
        val result = window.withOverscan(Long.MAX_VALUE)
        assertEquals(-(Long.MAX_VALUE), result.firstRow)
        assertEquals(Long.MAX_VALUE, result.lastRowExclusive)
    }

    // ── 4. selectSigned: intersect ──────────────────────────────────────────

    @Test
    fun selectSignedIncludesIntersectingPlacements() {
        val placements = listOf(
            signedPlacement("above", row = -5),
            signedPlacement("inside", row = -1),
            signedPlacement("below", row = 3),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-2L, 2L),
        )

        assertEquals(listOf("inside"), ids(selected))
    }

    @Test
    fun selectSignedIncludesSpanCrossingLowerBoundary() {
        val placements = listOf(
            signedPlacement("crosses-low", row = -3, spanRows = 4), // [-3, 1)
            signedPlacement("below", row = 2),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-2L, 2L),
        )

        assertEquals(listOf("crosses-low"), ids(selected))
    }

    @Test
    fun selectSignedIncludesSpanCrossingUpperBoundary() {
        val placements = listOf(
            signedPlacement("crosses-high", row = 1, spanRows = 3), // [1, 4)
            signedPlacement("above", row = -5),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-2L, 2L),
        )

        assertEquals(listOf("crosses-high"), ids(selected))
    }

    // ── 5. selectSigned: edge-touch (half-open exclusion) ───────────────────

    @Test
    fun selectSignedExcludesEdgeTouchAtUpperBoundary() {
        // Placement [2, 3) — its firstRow == window.lastRowExclusive → no intersection
        val placements = listOf(
            signedPlacement("starts-at-end", row = 2),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-2L, 2L),
        )

        assertEquals(emptyList<String>(), ids(selected))
    }

    @Test
    fun selectSignedExcludesEdgeTouchAtLowerBoundary() {
        // Placement [-4, -2) — its lastRowExclusive == window.firstRow → no intersection
        val placements = listOf(
            signedPlacement("ends-at-start", row = -4, spanRows = 2),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-2L, 2L),
        )

        assertEquals(emptyList<String>(), ids(selected))
    }

    // ── 6. selectSigned: boundary with negative rows ────────────────────────

    @Test
    fun selectSignedWithFullyNegativeWindow() {
        val placements = listOf(
            signedPlacement("inside", row = -8),
            signedPlacement("outside-low", row = -12),
            signedPlacement("outside-high", row = -4),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-10L, -6L),
        )

        assertEquals(listOf("inside"), ids(selected))
    }

    @Test
    fun selectSignedPreservesInputOrder() {
        val placements = listOf(
            signedPlacement("third", row = 1),
            signedPlacement("first", row = -3),
            signedPlacement("second", row = -1),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-4L, 2L),
        )

        assertEquals(listOf("third", "first", "second"), ids(selected))
    }

    @Test
    fun selectSignedEmptyWindowReturnsEmpty() {
        val placements = listOf(
            signedPlacement("only", row = 0),
        )

        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(0L, 0L),
        )

        assertEquals(emptyList<String>(), ids(selected))
    }

    // ── 7. selectSigned: with overscan ──────────────────────────────────────

    @Test
    fun selectSignedWithOverscanExtendsIntoNegative() {
        val placements = listOf(
            signedPlacement("near", row = -1),
            signedPlacement("far", row = -5),
        )

        // Window [0, 2) with overscan 2 → [-2, 4)
        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(0L, 2L),
            overscanRows = 2L,
        )

        assertEquals(listOf("near"), ids(selected))
    }

    @Test
    fun selectSignedWithOverscanIncludesSpanCrossingExtendedBoundary() {
        val placements = listOf(
            signedPlacement("cross", row = -4, spanRows = 3), // [-4, -1)
            signedPlacement("out", row = -6, spanRows = 1),  // [-6, -5)
        )

        // Window [-2, 1) with overscan 2 → [-4, 3)
        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-2L, 1L),
            overscanRows = 2L,
        )

        assertEquals(listOf("cross"), ids(selected))
    }

    @Test
    fun selectSignedWithOverscanOnNegativeWindow() {
        val placements = listOf(
            signedPlacement("a", row = -15),
            signedPlacement("b", row = -10),
            signedPlacement("c", row = -5),
        )

        // Window [-10, -8) with overscan 3 → [-13, -5)
        // a: [-15,-14) — lastExclusive -14 > firstRow -13? No → excluded (edge-touch)
        // b: [-10,-9) — -10 < -5 && -9 > -13 → included
        // c: [-5,-4) — -5 < -5? No → excluded (edge-touch)
        val selected = GridVisiblePlacements.selectSigned(
            placements,
            GridRowWindow(-10L, -8L),
            overscanRows = 3L,
        )

        assertEquals(listOf("b"), ids(selected))
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun signedPlacement(
        id: String,
        row: Int,
        spanRows: Int = 1,
    ) = SignedGridPlacement(
        id = id,
        column = 0,
        row = row,
        span = GridSpan(columns = 1, rows = spanRows),
    )

    private fun ids(placements: List<SignedGridPlacement<String>>): List<String> =
        placements.map { it.id }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}