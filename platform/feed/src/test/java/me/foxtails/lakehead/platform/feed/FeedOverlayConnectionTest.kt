package me.foxtails.lakehead.platform.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the framework-free progress contract in [FeedOverlayConnection].
 *
 * Only the pure [coerceOverlayProgress] helper is exercised here: the binder paths in
 * [AidlFeedBridge] need a live `android.os.Binder` and are device-verified with F2/F3.
 */
class FeedOverlayConnectionTest {

    @Test
    fun nanMapsToZero() {
        assertEquals(0f, coerceOverlayProgress(Float.NaN), 0f)
    }

    @Test
    fun clampsAboveOne() {
        assertEquals(1f, coerceOverlayProgress(2f), 0f)
        assertEquals(1f, coerceOverlayProgress(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun clampsBelowZero() {
        assertEquals(0f, coerceOverlayProgress(-0.5f), 0f)
        assertEquals(0f, coerceOverlayProgress(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun interiorValuesPassThrough() {
        assertEquals(0f, coerceOverlayProgress(0f), 0f)
        assertEquals(0.25f, coerceOverlayProgress(0.25f), 0f)
        assertEquals(1f, coerceOverlayProgress(1f), 0f)
    }
}
