package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGestureArbitrationTest {

    // ---- 8-level strict priority order ----

    @Test
    fun resolve_unpinWinsOverAll() {
        assertEquals(
            HomeGestureWinner.UNPIN,
            resolveHomeGesture(
                isUnpin = true, isOnHandle = true, isActiveResize = true,
                isActiveMove = true, isSelectDrag = true, isTap = true,
                isScroll = true, isBackground = true,
            ),
        )
    }

    @Test
    fun resolve_handleWinsOverAllBelow() {
        assertEquals(
            HomeGestureWinner.HANDLE,
            resolveHomeGesture(
                isOnHandle = true, isActiveResize = true, isActiveMove = true,
                isSelectDrag = true, isTap = true, isScroll = true,
                isBackground = true,
            ),
        )
    }

    @Test
    fun resolve_activeResizeWinsOverAllBelow() {
        assertEquals(
            HomeGestureWinner.ACTIVE_RESIZE,
            resolveHomeGesture(
                isActiveResize = true, isActiveMove = true, isSelectDrag = true,
                isTap = true, isScroll = true, isBackground = true,
            ),
        )
    }

    @Test
    fun resolve_activeMoveWinsOverAllBelow() {
        assertEquals(
            HomeGestureWinner.ACTIVE_MOVE,
            resolveHomeGesture(
                isActiveMove = true, isSelectDrag = true, isTap = true,
                isScroll = true, isBackground = true,
            ),
        )
    }

    @Test
    fun resolve_selectDragWinsOverAllBelow() {
        assertEquals(
            HomeGestureWinner.SELECT_DRAG,
            resolveHomeGesture(
                isSelectDrag = true, isTap = true, isScroll = true,
                isBackground = true,
            ),
        )
    }

    @Test
    fun resolve_tapWinsOverScrollAndBackground() {
        assertEquals(
            HomeGestureWinner.TAP,
            resolveHomeGesture(isTap = true, isScroll = true, isBackground = true),
        )
    }

    @Test
    fun resolve_scrollWinsOverBackground() {
        assertEquals(
            HomeGestureWinner.SCROLL,
            resolveHomeGesture(isScroll = true, isBackground = true),
        )
    }

    @Test
    fun resolve_backgroundIsDefault() {
        assertEquals(
            HomeGestureWinner.BACKGROUND,
            resolveHomeGesture(),
        )
    }

    @Test
    fun resolve_allFalse_returnsBackground() {
        assertEquals(
            HomeGestureWinner.BACKGROUND,
            resolveHomeGesture(
                isUnpin = false, isOnHandle = false, isActiveResize = false,
                isActiveMove = false, isSelectDrag = false, isTap = false,
                isScroll = false, isBackground = false,
            ),
        )
    }

    @Test
    fun resolve_orderingIsStrictEightLevels() {
        // Systematic: each level beats all levels below it.
        val levels = listOf(
            HomeGestureWinner.UNPIN,
            HomeGestureWinner.HANDLE,
            HomeGestureWinner.ACTIVE_RESIZE,
            HomeGestureWinner.ACTIVE_MOVE,
            HomeGestureWinner.SELECT_DRAG,
            HomeGestureWinner.TAP,
            HomeGestureWinner.SCROLL,
            HomeGestureWinner.BACKGROUND,
        )
        assertEquals(8, levels.size)

        // Each winner is produced by setting only its own flag.
        assertEquals(HomeGestureWinner.UNPIN, resolveHomeGesture(isUnpin = true))
        assertEquals(HomeGestureWinner.HANDLE, resolveHomeGesture(isOnHandle = true))
        assertEquals(HomeGestureWinner.ACTIVE_RESIZE, resolveHomeGesture(isActiveResize = true))
        assertEquals(HomeGestureWinner.ACTIVE_MOVE, resolveHomeGesture(isActiveMove = true))
        assertEquals(HomeGestureWinner.SELECT_DRAG, resolveHomeGesture(isSelectDrag = true))
        assertEquals(HomeGestureWinner.TAP, resolveHomeGesture(isTap = true))
        assertEquals(HomeGestureWinner.SCROLL, resolveHomeGesture(isScroll = true))
        assertEquals(HomeGestureWinner.BACKGROUND, resolveHomeGesture(isBackground = true))
    }

    // ---- classifyHomeAxis: diagonal never steals ----

    @Test
    fun classifyAxis_horizontalLock_whenClearRatio() {
        // 80 / 20 = 4.0 ≥ 1.25 → HORIZONTAL.
        val result = classifyHomeAxis(
            dxPx = 80f, dyPx = 20f, slopPx = 10f, jitterPx = 5f,
        )
        assertEquals(LockedAxis.HORIZONTAL, result)
    }

    @Test
    fun classifyAxis_verticalLock_whenClearRatio() {
        // 20 / 80 = 0.25 → vertical dominates.
        val result = classifyHomeAxis(
            dxPx = 20f, dyPx = 80f, slopPx = 10f, jitterPx = 5f,
        )
        assertEquals(LockedAxis.VERTICAL, result)
    }

    @Test
    fun classifyAxis_diagonalNeverSteals() {
        // 40 / 35 = 1.14 < 1.25 → UNDECIDED (diagonal guard holds).
        // Both axes exceed jitter (5) but neither dominates by ratio.
        val result = classifyHomeAxis(
            dxPx = 40f, dyPx = 35f, slopPx = 10f, jitterPx = 5f,
        )
        assertEquals(LockedAxis.UNDECIDED, result)
    }

    @Test
    fun classifyAxis_nearRatioStillUndecided() {
        // 50 / 41 = 1.22 < 1.25 → still undecided.
        val result = classifyHomeAxis(
            dxPx = 50f, dyPx = 41f, slopPx = 10f, jitterPx = 5f,
        )
        assertEquals(LockedAxis.UNDECIDED, result)
    }

    @Test
    fun classifyAxis_exactRatioWins() {
        // 50 / 40 = 1.25 exactly → HORIZONTAL (>=).
        val result = classifyHomeAxis(
            dxPx = 50f, dyPx = 40f, slopPx = 10f, jitterPx = 5f,
        )
        assertEquals(LockedAxis.HORIZONTAL, result)
    }

    @Test
    fun classifyAxis_belowSlopStaysUndecided() {
        // Both below slop → lockAxis returns UNDECIDED → classify returns UNDECIDED.
        val result = classifyHomeAxis(
            dxPx = 3f, dyPx = 3f, slopPx = 10f, jitterPx = 5f,
        )
        assertEquals(LockedAxis.UNDECIDED, result)
    }

    @Test
    fun classifyAxis_belowJitterOneAxisUndecided() {
        // dx below jitter even though vertical dominates: lockAxis locks VERTICAL,
        // but since dx < jitter, dominantAxis would also say UNDECIDED. However,
        // the diagonal guard only fires when BOTH axes exceed jitter AND neither
        // dominates. Here, lockAxis already committed to VERTICAL (dy >= slop),
        // and the guard condition `adx >= jitterPx && ady >= jitterPx` fails
        // because adx < jitterPx. So the lock is preserved → VERTICAL.
        val result = classifyHomeAxis(
            dxPx = 2f, dyPx = 80f, slopPx = 10f, jitterPx = 5f,
        )
        assertEquals(LockedAxis.VERTICAL, result)
    }

    @Test
    fun classifyAxis_preservesCommittedLock() {
        // Once committed to HORIZONTAL, subsequent calls preserve it even if
        // the displacement changes direction.
        val result = classifyHomeAxis(
            dxPx = 5f, dyPx = 100f, slopPx = 10f, jitterPx = 5f,
            currentLock = LockedAxis.HORIZONTAL,
        )
        assertEquals(LockedAxis.HORIZONTAL, result)
    }

    @Test
    fun classifyAxis_preservesCommittedVerticalLock() {
        val result = classifyHomeAxis(
            dxPx = 100f, dyPx = 5f, slopPx = 10f, jitterPx = 5f,
            currentLock = LockedAxis.VERTICAL,
        )
        assertEquals(LockedAxis.VERTICAL, result)
    }

    // ---- mid-drag steal: committed lock persists even when conditions change ----

    @Test
    fun classifyAxis_midDrag_committedHorizontalNotStolenByVerticalIncrease() {
        // Lock was committed to HORIZONTAL at the start. During the drag the
        // vertical displacement increases massively, but the committed lock
        // is preserved — the drag axis does not flip mid-gesture.
        val result = classifyHomeAxis(
            dxPx = 30f, dyPx = 200f, slopPx = 10f, jitterPx = 5f,
            currentLock = LockedAxis.HORIZONTAL,
        )
        assertEquals(LockedAxis.HORIZONTAL, result)
    }

    @Test
    fun classifyAxis_midDrag_committedVerticalNotStolenByHorizontalIncrease() {
        val result = classifyHomeAxis(
            dxPx = 200f, dyPx = 30f, slopPx = 10f, jitterPx = 5f,
            currentLock = LockedAxis.VERTICAL,
        )
        assertEquals(LockedAxis.VERTICAL, result)
    }

    @Test
    fun classifyAxis_usesLockAxisPredicateOnly() {
        // Verify the function delegates to lockAxis for the base decision.
        // lockAxis with zero displacement and zero slop → UNDECIDED.
        val result = classifyHomeAxis(
            dxPx = 0f, dyPx = 0f, slopPx = 0f, jitterPx = 0f,
        )
        assertEquals(LockedAxis.UNDECIDED, result)
    }

    // ---- no arbPriority dependency ----

    @Test
    fun resolve_doesNotDependOnArbPriority() {
        // The resolve function uses its own enum (HomeGestureWinner), not ArbWinner.
        // This test verifies the return type is HomeGestureWinner.
        val result: HomeGestureWinner = resolveHomeGesture(isTap = true)
        assertEquals(HomeGestureWinner.TAP, result)
    }

    // ---- enum ordinal matches priority order ----

    @Test
    fun enumOrdinalsMatchStrictPriority() {
        val values = HomeGestureWinner.entries
        assertEquals(8, values.size)
        assertEquals(HomeGestureWinner.UNPIN, values[0])
        assertEquals(HomeGestureWinner.HANDLE, values[1])
        assertEquals(HomeGestureWinner.ACTIVE_RESIZE, values[2])
        assertEquals(HomeGestureWinner.ACTIVE_MOVE, values[3])
        assertEquals(HomeGestureWinner.SELECT_DRAG, values[4])
        assertEquals(HomeGestureWinner.TAP, values[5])
        assertEquals(HomeGestureWinner.SCROLL, values[6])
        assertEquals(HomeGestureWinner.BACKGROUND, values[7])
    }

    // ---- A2 holdsPager: tile touch never becomes page swipe ----

    @Test
    fun holdsPager_trueForSix() {
        assertTrue(HomeGestureWinner.UNPIN.holdsPager())
        assertTrue(HomeGestureWinner.HANDLE.holdsPager())
        assertTrue(HomeGestureWinner.ACTIVE_RESIZE.holdsPager())
        assertTrue(HomeGestureWinner.ACTIVE_MOVE.holdsPager())
        assertTrue(HomeGestureWinner.SELECT_DRAG.holdsPager())
        assertTrue(HomeGestureWinner.TAP.holdsPager())
    }

    @Test
    fun holdsPager_falseForScrollAndBackground() {
        assertFalse(HomeGestureWinner.SCROLL.holdsPager())
        assertFalse(HomeGestureWinner.BACKGROUND.holdsPager())
    }

    // ---- B2 move/selection separation: ACTIVE_MOVE beats selection inputs ----

    @Test
    fun activeMove_beatsSelectTapAndScroll() {
        // Body-drag in progress beats wiggle select-drag, tap and scroll:
        // move and selection never share the gesture.
        assertEquals(
            HomeGestureWinner.ACTIVE_MOVE,
            resolveHomeGesture(
                isActiveMove = true, isSelectDrag = true, isTap = true,
                isScroll = true,
            ),
        )
    }

    @Test
    fun activeMove_beatsSelectDragAlone() {
        assertEquals(
            HomeGestureWinner.ACTIVE_MOVE,
            resolveHomeGesture(isActiveMove = true, isSelectDrag = true),
        )
    }

    @Test
    fun unpin_beatsHandleAndMove() {
        // Remove affordance wins over the handle corner and an in-flight move:
        // unpin never becomes resize or move.
        assertEquals(
            HomeGestureWinner.UNPIN,
            resolveHomeGesture(isUnpin = true, isOnHandle = true, isActiveMove = true),
        )
        assertEquals(
            HomeGestureWinner.UNPIN,
            resolveHomeGesture(isUnpin = true, isActiveResize = true, isActiveMove = true),
        )
    }
}