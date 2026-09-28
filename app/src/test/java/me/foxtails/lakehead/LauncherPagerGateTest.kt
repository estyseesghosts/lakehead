package me.foxtails.lakehead

import me.foxtails.lakehead.core.designsystem.GestureArbitration.ArbWinner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1 gate tests for [isPagerSwipeEnabled]: swipe is enabled only when the
 * picker is not blocking, arbitration did not award the gesture to the picker,
 * and home is not holding. Pill programmatic navigation bypasses this gate.
 * F3 adds the feed-start exception, which bypasses only the A3 region latch.
 */
class LauncherPagerGateTest {

    @Test
    fun allClear_swipeEnabled() {
        assertTrue(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
            ),
        )
    }

    @Test
    fun pickerBlocking_disablesSwipe() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = true,
                pagerWinner = ArbWinner.PICKER,
                homeHold = false,
            ),
        )
    }

    @Test
    fun pickerWinner_disablesSwipe() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.PICKER,
                homeHold = false,
            ),
        )
    }

    @Test
    fun homeHold_disablesSwipe() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = true,
            ),
        )
    }

    @Test
    fun pickerAndHold_disablesSwipe() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = true,
                pagerWinner = ArbWinner.PICKER,
                homeHold = true,
            ),
        )
    }

    @Test
    fun horizontalWinner_withoutHold_swipeEnabled() {
        assertTrue(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.HORIZONTAL,
                homeHold = false,
            ),
        )
    }

    @Test
    fun horizontalWinner_withHold_disablesSwipe() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.HORIZONTAL,
                homeHold = true,
            ),
        )
    }

    // S1 settings-menu hold.

    @Test
    fun settingsClosed_keepsSwipeEnabled() {
        assertTrue(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                isSettingsOpen = false,
            ),
        )
    }

    @Test
    fun settingsOpen_disablesSwipe() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                isSettingsOpen = true,
            ),
        )
    }

    @Test
    fun settingsOpen_disablesSwipeEvenWhenAllOtherGuardsClear() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.HORIZONTAL,
                homeHold = false,
                pagerHold = false,
                pagerRegionAllowed = true,
                isSettingsOpen = true,
            ),
        )
    }

    @Test
    fun defaultGate_omitsSettingsHold() {
        // Callers that do not hoist the menu keep the A3 behavior exactly.
        assertTrue(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                pagerHold = false,
                pagerRegionAllowed = true,
            ),
        )
    }

    // F3 feed-start exception: bypasses only the region latch.

    @Test
    fun feedStart_bypassesRegionLatch() {
        assertTrue(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                pagerHold = false,
                pagerRegionAllowed = false,
                isSettingsOpen = false,
                feedStartAllowed = true,
            ),
        )
    }

    @Test
    fun defaultGate_omitsFeedException() {
        // Callers that do not track the feed page keep the S1 behavior exactly:
        // a top-67% start still never pages.
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                pagerHold = false,
                pagerRegionAllowed = false,
                isSettingsOpen = false,
            ),
        )
    }

    @Test
    fun feedStart_doesNotBypassPicker() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = true,
                pagerWinner = ArbWinner.PICKER,
                homeHold = false,
                pagerHold = false,
                pagerRegionAllowed = false,
                isSettingsOpen = false,
                feedStartAllowed = true,
            ),
        )
    }

    @Test
    fun feedStart_doesNotBypassHolds() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = true,
                pagerHold = false,
                pagerRegionAllowed = false,
                isSettingsOpen = false,
                feedStartAllowed = true,
            ),
        )
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                pagerHold = true,
                pagerRegionAllowed = false,
                isSettingsOpen = false,
                feedStartAllowed = true,
            ),
        )
    }

    @Test
    fun feedStart_doesNotBypassSettingsMenu() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                pagerHold = false,
                pagerRegionAllowed = false,
                isSettingsOpen = true,
                feedStartAllowed = true,
            ),
        )
    }

    // A3 tile-touch hold: blocks swipe while held, restores on release.

    @Test
    fun pagerHold_blocksSwipe_restoresOnRelease() {
        assertFalse(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                pagerHold = true,
                pagerRegionAllowed = true,
            ),
        )
        assertTrue(
            isPagerSwipeEnabled(
                pickerBlocksPager = false,
                pagerWinner = ArbWinner.UNDECIDED,
                homeHold = false,
                pagerHold = false,
                pagerRegionAllowed = true,
            ),
        )
    }
}
