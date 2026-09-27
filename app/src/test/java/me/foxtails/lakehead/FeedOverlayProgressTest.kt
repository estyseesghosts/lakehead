package me.foxtails.lakehead

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F3 unit cover for the framework-free feed-driving helpers in
 * `LauncherShell.kt`: [feedOverlayProgress] maps the pager position to the
 * 0..1 overlay range, and [isFeedOverlayVisible] fires only on the settled
 * feed page.
 */
class FeedOverlayProgressTest {

    @Test
    fun settledFeed_fullProgress() {
        assertEquals(1f, feedOverlayProgress(LAUNCHER_PAGE_FEED, 0f), 0f)
    }

    @Test
    fun settledHome_zeroProgress() {
        assertEquals(0f, feedOverlayProgress(LAUNCHER_PAGE_TILE, 0f), 0f)
    }

    @Test
    fun settledAllApps_zeroProgress() {
        assertEquals(0f, feedOverlayProgress(LAUNCHER_PAGE_ALL_APPS, 0f), 0f)
    }

    @Test
    fun halfwayFeedToHome_halfProgress() {
        // currentPage + offset is the absolute scroll position in pages: feed
        // is page 0, so halfway toward home reads 0.5 regardless of which side
        // reports the offset.
        assertEquals(0.5f, feedOverlayProgress(LAUNCHER_PAGE_FEED, 0.5f), 0.0001f)
        assertEquals(0.5f, feedOverlayProgress(LAUNCHER_PAGE_TILE, -0.5f), 0.0001f)
    }

    @Test
    fun overscrollBeyondFeed_clampsToOne() {
        // Dragging right past the feed page drives the absolute position
        // negative, so the raw progress exceeds 1 and clamps.
        assertEquals(1f, feedOverlayProgress(LAUNCHER_PAGE_FEED, -0.4f), 0f)
    }

    @Test
    fun scrollTowardAllApps_clampsToZero() {
        assertEquals(0f, feedOverlayProgress(LAUNCHER_PAGE_TILE, 0.5f), 0f)
    }

    @Test
    fun nanOffset_mapsToZero() {
        assertEquals(0f, feedOverlayProgress(LAUNCHER_PAGE_FEED, Float.NaN), 0f)
    }

    @Test
    fun visible_onlyOnSettledFeedPage() {
        assertTrue(isFeedOverlayVisible(LAUNCHER_PAGE_FEED))
        assertFalse(isFeedOverlayVisible(LAUNCHER_PAGE_TILE))
        assertFalse(isFeedOverlayVisible(LAUNCHER_PAGE_ALL_APPS))
    }

    // P2 settled visibility: currentPage alone flaps mid-swipe (it names the
    // nearest snap during drag/settle animation), so visibility additionally
    // gates on scroll state + zero offset.

    @Test
    fun visible_falseWhileScrollInProgressOnFeed() {
        // Dragging feed→home mid-swipe: the snap is still the feed page but the
        // gesture is in flight — must not emit visible true.
        assertFalse(isFeedOverlayVisible(LAUNCHER_PAGE_FEED, isScrollInProgress = true))
        assertFalse(
            isFeedOverlayVisible(
                LAUNCHER_PAGE_FEED,
                isScrollInProgress = true,
                pageOffsetFraction = 0.5f,
            ),
        )
    }

    @Test
    fun visible_falseOnPeekOffsetWithoutScrollFlag() {
        // Settling animation frames report offset != 0 with the feed snap: still
        // not settled, so no visible flap on arrival/departure.
        assertFalse(isFeedOverlayVisible(LAUNCHER_PAGE_FEED, pageOffsetFraction = 0.3f))
        assertFalse(isFeedOverlayVisible(LAUNCHER_PAGE_FEED, pageOffsetFraction = -0.2f))
    }

    @Test
    fun visible_trueOnlyWhenSettledOnFeed() {
        assertTrue(
            isFeedOverlayVisible(
                LAUNCHER_PAGE_FEED,
                isScrollInProgress = false,
                pageOffsetFraction = 0f,
            ),
        )
    }

    @Test
    fun visible_falseOnOtherPagesEvenWhenSettled() {
        assertFalse(
            isFeedOverlayVisible(
                LAUNCHER_PAGE_TILE,
                isScrollInProgress = false,
                pageOffsetFraction = 0f,
            ),
        )
        assertFalse(
            isFeedOverlayVisible(
                LAUNCHER_PAGE_ALL_APPS,
                isScrollInProgress = false,
                pageOffsetFraction = 0f,
            ),
        )
    }

    @Test
    fun visible_defaultsPreserveSettledPageCheck() {
        // Callers that do not track scroll state (down-time latch, previews) keep
        // the exact single-arg behavior.
        assertTrue(isFeedOverlayVisible(LAUNCHER_PAGE_FEED))
        assertFalse(isFeedOverlayVisible(LAUNCHER_PAGE_TILE))
    }
}
