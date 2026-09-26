package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridRestOriginTest {

    // ── 1. Golden 0.45 vs 0.5 ─────────────────────────────────────────────

    @Test
    fun defaultBias045PlacesContentAboveCentreWhileBias05Centres() {
        val b45 = GridRestOrigin.rest(
            viewportHeightPx = 2_000L,
            viewportTopPx = 0L,
            contentHeightPx = 800L,
            minRow = 0L,
            cellStridePx = 200L,
            bias = 0.45,
        )
        val b50 = GridRestOrigin.rest(
            viewportHeightPx = 2_000L,
            viewportTopPx = 0L,
            contentHeightPx = 800L,
            minRow = 0L,
            cellStridePx = 200L,
            bias = 0.50,
        )

        assertEquals(540L, b45.gridOriginY)
        assertFalse(b45.scrollable)
        assertEquals(0L, b45.scrollRangePx)

        assertEquals(600L, b50.gridOriginY)
        assertFalse(b50.scrollable)
        assertEquals(0L, b50.scrollRangePx)
    }

    // ── 2. Bias 0 (top) and bias 1 (bottom) ────────────────────────────────

    @Test
    fun biasZeroAnchorsTopAndBiasOneAnchorsBottom() {
        val top = GridRestOrigin.rest(
            viewportHeightPx = 2_000L, viewportTopPx = 0L,
            contentHeightPx = 800L, minRow = 0L, cellStridePx = 200L, bias = 0.0,
        )
        assertEquals(0L, top.gridOriginY)
        assertFalse(top.scrollable)

        val bottom = GridRestOrigin.rest(
            viewportHeightPx = 2_000L, viewportTopPx = 0L,
            contentHeightPx = 800L, minRow = 0L, cellStridePx = 200L, bias = 1.0,
        )
        assertEquals(1_200L, bottom.gridOriginY)
        assertFalse(bottom.scrollable)
    }

    // ── 3. Boundary: content == viewport (short branch, freeSpace == 0) ────

    @Test
    fun contentExactlyEqualsViewportProducesZeroOriginAndNotScrollable() {
        val result = GridRestOrigin.rest(
            viewportHeightPx = 2_400L,
            viewportTopPx = 100L,
            contentHeightPx = 2_400L,
            minRow = 0L,
            cellStridePx = 160L,
        )

        assertEquals(100L, result.gridOriginY)
        assertEquals(0L, result.scrollRangePx)
        assertFalse(result.scrollable)
    }

    // ── 4. Tall: range == content − view, anchor at viewport top ───────────

    @Test
    fun tallContentScrollsAndAnchorsMinRowAtViewportTop() {
        val result = GridRestOrigin.rest(
            viewportHeightPx = 2_000L,
            viewportTopPx = 0L,
            contentHeightPx = 6_000L,
            minRow = 0L,
            cellStridePx = 200L,
        )

        assertEquals(4_000L, result.scrollRangePx)
        assertTrue(result.scrollable)
        assertEquals(0L, result.gridOriginY)
    }

    // ── 5. Negative minRow shifts origin down ──────────────────────────────

    @Test
    fun negativeMinRowShiftsOriginDownByMinRowTimesStride() {
        val result = GridRestOrigin.rest(
            viewportHeightPx = 2_000L,
            viewportTopPx = 0L,
            contentHeightPx = 800L,
            minRow = -2L,
            cellStridePx = 200L,
        )

        // origin = 0 + round(1200 * 0.45) - (-2 * 200) = 540 + 400 = 940
        assertEquals(940L, result.gridOriginY)
        assertFalse(result.scrollable)
    }

    // ── 6. Bad inputs throw IllegalArgumentException ────────────────────────

    @Test
    fun rejectsInvalidInputs() {
        assertIllegalArgument {
            GridRestOrigin.rest(0L, 0L, 0L, 0L, 200L) // viewportHeightPx == 0
        }
        assertIllegalArgument {
            GridRestOrigin.rest(-1L, 0L, 0L, 0L, 200L) // viewportHeightPx < 0
        }
        assertIllegalArgument {
            GridRestOrigin.rest(2_000L, 0L, 0L, 0L, 0L) // cellStridePx == 0
        }
        assertIllegalArgument {
            GridRestOrigin.rest(2_000L, 0L, 0L, 0L, -1L) // cellStridePx < 0
        }
        assertIllegalArgument {
            GridRestOrigin.rest(2_000L, 0L, -1L, 0L, 200L) // contentHeightPx < 0
        }
        assertIllegalArgument {
            GridRestOrigin.rest(2_000L, 0L, 0L, 0L, 200L, bias = -0.1) // bias < 0
        }
        assertIllegalArgument {
            GridRestOrigin.rest(2_000L, 0L, 0L, 0L, 200L, bias = 1.1) // bias > 1
        }
    }

    // ── 7. Overflow throws ArithmeticException ─────────────────────────────

    @Test
    fun overflowInOriginCalculationThrowsArithmeticException() {
        assertArithmeticException {
            // minRow * cellStridePx overflows Long
            GridRestOrigin.rest(
                viewportHeightPx = 1L,
                viewportTopPx = 0L,
                contentHeightPx = 0L,
                minRow = Long.MIN_VALUE,
                cellStridePx = Long.MAX_VALUE,
                bias = 0.0,
            )
        }
    }

    // ── 8. Determinism ─────────────────────────────────────────────────────

    @Test
    fun repeatedCallsWithSameInputsReturnIdenticalResults() {
        val first = GridRestOrigin.rest(
            viewportHeightPx = 2_000L, viewportTopPx = 100L,
            contentHeightPx = 1_200L, minRow = 1L, cellStridePx = 200L, bias = 0.45,
        )
        val second = GridRestOrigin.rest(
            viewportHeightPx = 2_000L, viewportTopPx = 100L,
            contentHeightPx = 1_200L, minRow = 1L, cellStridePx = 200L, bias = 0.45,
        )

        assertEquals(first, second)
        assertNotEquals(
            first,
            GridRestOrigin.rest(
                viewportHeightPx = 2_000L, viewportTopPx = 100L,
                contentHeightPx = 1_200L, minRow = 1L, cellStridePx = 200L, bias = 0.5,
            ),
        )
    }

    // ── helpers ────────────────────────────────────────────────────────────

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