package me.foxtails.lakehead.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RebasedOverlayTopTest {

    @Test
    fun originZero_returnsAbsoluteTopUnchanged() {
        assertEquals(240, rebasedOverlayTop(absoluteTopPx = 240, rowOrigin = 0L, rowStridePx = 120L))
    }

    @Test
    fun positiveOrigin_subtractsOriginStride() {
        // Absolute top for row 5 with stride 120 is 600; origin row 2 rebases to 360.
        assertEquals(360, rebasedOverlayTop(absoluteTopPx = 600, rowOrigin = 2L, rowStridePx = 120L))
    }

    @Test
    fun candidateAboveOrigin_keepsNegativeOffset() {
        // Candidate row 1 (top 120) with origin row 3 rebases to -240: still representable.
        assertEquals(-240, rebasedOverlayTop(absoluteTopPx = 120, rowOrigin = 3L, rowStridePx = 120L))
    }

    @Test
    fun hugeOrigin_saturatesInsteadOfWrapping() {
        val result = rebasedOverlayTop(
            absoluteTopPx = 0,
            rowOrigin = Long.MAX_VALUE,
            rowStridePx = Long.MAX_VALUE,
        )
        assertEquals(Int.MIN_VALUE, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAbsoluteTop_throws() {
        rebasedOverlayTop(absoluteTopPx = -1, rowOrigin = 0L, rowStridePx = 120L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeOrigin_throws() {
        rebasedOverlayTop(absoluteTopPx = 0, rowOrigin = -1L, rowStridePx = 120L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveStride_throws() {
        rebasedOverlayTop(absoluteTopPx = 0, rowOrigin = 0L, rowStridePx = 0L)
    }
}
