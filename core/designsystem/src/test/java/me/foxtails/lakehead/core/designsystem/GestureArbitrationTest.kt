package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.designsystem.GestureArbitration.ArbWinner
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
import me.foxtails.lakehead.core.designsystem.GestureArbitration.Settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GestureArbitrationTest {

    // ── lockAxis ───────────────────────────────────────────────────────────────

    @Test
    fun lockAxisBelowSlopRemainsUndecided() {
        // Both axes below 10px slop.
        assertEquals(
            LockedAxis.UNDECIDED,
            GestureArbitration.lockAxis(dxPx = 5f, dyPx = 3f, slopPx = 10f),
        )
    }

    @Test
    fun lockAxisExactlyAtSlopLocks() {
        // dx == slop → horizontal lock.
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.lockAxis(dxPx = 10f, dyPx = 0f, slopPx = 10f),
        )
        // dy == slop, dx below → vertical lock.
        assertEquals(
            LockedAxis.VERTICAL,
            GestureArbitration.lockAxis(dxPx = 0f, dyPx = 10f, slopPx = 10f),
        )
    }

    @Test
    fun lockAxisBeyondSlopLocksToLarger() {
        // Both exceed slop, horizontal larger.
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.lockAxis(dxPx = 50f, dyPx = 20f, slopPx = 10f),
        )
        // Both exceed slop, vertical larger.
        assertEquals(
            LockedAxis.VERTICAL,
            GestureArbitration.lockAxis(dxPx = 20f, dyPx = 50f, slopPx = 10f),
        )
    }

    @Test
    fun lockAxisEqualMagnitudesLocksHorizontal() {
        // Tie-break: adx >= ady → HORIZONTAL.
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.lockAxis(dxPx = 30f, dyPx = 30f, slopPx = 10f),
        )
    }

    @Test
    fun lockAxisNegativeDisplacementsLockCorrectly() {
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.lockAxis(dxPx = -40f, dyPx = -10f, slopPx = 8f),
        )
        assertEquals(
            LockedAxis.VERTICAL,
            GestureArbitration.lockAxis(dxPx = -10f, dyPx = -40f, slopPx = 8f),
        )
    }

    @Test
    fun lockAxisPreservesExistingLock() {
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.lockAxis(
                dxPx = 5f, dyPx = 100f, slopPx = 10f,
                currentLock = LockedAxis.HORIZONTAL,
            ),
        )
        assertEquals(
            LockedAxis.VERTICAL,
            GestureArbitration.lockAxis(
                dxPx = 100f, dyPx = 5f, slopPx = 10f,
                currentLock = LockedAxis.VERTICAL,
            ),
        )
    }

    @Test
    fun lockAxisZeroSlopLocksOnAnyDisplacement() {
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.lockAxis(dxPx = 0.001f, dyPx = 0f, slopPx = 0f),
        )
        // Zero displacement with zero slop stays undecided.
        assertEquals(
            LockedAxis.UNDECIDED,
            GestureArbitration.lockAxis(dxPx = 0f, dyPx = 0f, slopPx = 0f),
        )
    }

    @Test
    fun lockAxisRejectsNegativeSlop() {
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.lockAxis(dxPx = 10f, dyPx = 10f, slopPx = -1f)
        }
    }

    // ── dominantAxis ───────────────────────────────────────────────────────────

    @Test
    fun dominantAxisHorizontalWinsWhenRatioMet() {
        // 50 / 20 = 2.5 ≥ 1.25 → HORIZONTAL.
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.dominantAxis(dxPx = 50f, dyPx = 20f, jitterPx = 5f),
        )
    }

    @Test
    fun dominantAxisVerticalWinsWhenRatioMet() {
        // 60 / 10 = 6.0 ≥ 1.25 → VERTICAL.
        assertEquals(
            LockedAxis.VERTICAL,
            GestureArbitration.dominantAxis(dxPx = 10f, dyPx = 60f, jitterPx = 5f),
        )
    }

    @Test
    fun dominantAxisDiagonalNeverSteals() {
        // 40 / 35 = 1.14 < 1.25 → UNDECIDED. The critical "diagonal jitter
        // never steals a gesture" invariant.
        assertEquals(
            LockedAxis.UNDECIDED,
            GestureArbitration.dominantAxis(dxPx = 40f, dyPx = 35f, jitterPx = 5f),
        )
    }

    @Test
    fun dominantAxisDiagonalNearRatioStillUndecided() {
        // 50 / 41 = 1.22 < 1.25 → still undecided.
        assertEquals(
            LockedAxis.UNDECIDED,
            GestureArbitration.dominantAxis(dxPx = 50f, dyPx = 41f, jitterPx = 5f),
        )
    }

    @Test
    fun dominantAxisExactRatioBoundaryWins() {
        // 50 / 40 = 1.25 exactly → HORIZONTAL (>=).
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.dominantAxis(dxPx = 50f, dyPx = 40f, jitterPx = 5f),
        )
    }

    @Test
    fun dominantAxisBelowJitterIsUndecided() {
        // Both below 20px jitter.
        assertEquals(
            LockedAxis.UNDECIDED,
            GestureArbitration.dominantAxis(dxPx = 15f, dyPx = 15f, jitterPx = 20f),
        )
    }

    @Test
    fun dominantAxisOneBelowJitterIsUndecided() {
        // dx below jitter even though ratio would win.
        assertEquals(
            LockedAxis.UNDECIDED,
            GestureArbitration.dominantAxis(dxPx = 2f, dyPx = 100f, jitterPx = 5f),
        )
    }

    @Test
    fun dominantAxisCustomRatio() {
        // With ratio 3.0, 50 / 20 = 2.5 < 3.0 → undecided.
        assertEquals(
            LockedAxis.UNDECIDED,
            GestureArbitration.dominantAxis(dxPx = 50f, dyPx = 20f, jitterPx = 5f, ratio = 3f),
        )
        // 60 / 20 = 3.0 >= 3.0 → HORIZONTAL.
        assertEquals(
            LockedAxis.HORIZONTAL,
            GestureArbitration.dominantAxis(dxPx = 60f, dyPx = 20f, jitterPx = 5f, ratio = 3f),
        )
    }

    @Test
    fun dominantAxisRejectsInvalidArgs() {
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.dominantAxis(dxPx = 10f, dyPx = 10f, jitterPx = -1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.dominantAxis(dxPx = 10f, dyPx = 10f, jitterPx = 5f, ratio = 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.dominantAxis(dxPx = 10f, dyPx = 10f, jitterPx = 5f, ratio = 0.5f)
        }
    }

    // ── settleDrag ─────────────────────────────────────────────────────────────

    @Test
    fun settleDragCompleteWhenDisplacementExceedsThreshold() {
        assertEquals(
            Settle.COMPLETE,
            GestureArbitration.settleDrag(
                displacedFraction = 0.5f,
                velocityPxPerSec = 0f,
                completeFraction = 0.4f,
                flickVelocityPxPerSec = 3000f,
                cancelFraction = 0.1f,
            ),
        )
    }

    @Test
    fun settleDragCompleteOnExactThreshold() {
        assertEquals(
            Settle.COMPLETE,
            GestureArbitration.settleDrag(
                displacedFraction = 0.4f,
                velocityPxPerSec = 0f,
                completeFraction = 0.4f,
                flickVelocityPxPerSec = 3000f,
                cancelFraction = 0.1f,
            ),
        )
    }

    @Test
    fun settleDragFlickOverridesLowDisplacement() {
        // Displacement below complete but velocity above flick → COMPLETE.
        assertEquals(
            Settle.COMPLETE,
            GestureArbitration.settleDrag(
                displacedFraction = 0.2f,
                velocityPxPerSec = 3500f,
                completeFraction = 0.4f,
                flickVelocityPxPerSec = 3000f,
                cancelFraction = 0.1f,
            ),
        )
    }

    @Test
    fun settleDragFlickExactVelocityCompletes() {
        assertEquals(
            Settle.COMPLETE,
            GestureArbitration.settleDrag(
                displacedFraction = 0.05f,
                velocityPxPerSec = 3000f,
                completeFraction = 0.4f,
                flickVelocityPxPerSec = 3000f,
                cancelFraction = 0.1f,
            ),
        )
    }

    @Test
    fun settleDragCancelWhenBelowBothThresholds() {
        // Displacement between cancel and complete, velocity below flick → CANCEL.
        assertEquals(
            Settle.CANCEL,
            GestureArbitration.settleDrag(
                displacedFraction = 0.25f,
                velocityPxPerSec = 1000f,
                completeFraction = 0.4f,
                flickVelocityPxPerSec = 3000f,
                cancelFraction = 0.1f,
            ),
        )
    }

    @Test
    fun settleDragCancelWhenBelowCancelFraction() {
        // Tiny displacement and low velocity → CANCEL.
        assertEquals(
            Settle.CANCEL,
            GestureArbitration.settleDrag(
                displacedFraction = 0.05f,
                velocityPxPerSec = 500f,
                completeFraction = 0.4f,
                flickVelocityPxPerSec = 3000f,
                cancelFraction = 0.1f,
            ),
        )
    }

    @Test
    fun settleDragNegativeDisplacementAndVelocityUseAbsoluteValue() {
        assertEquals(
            Settle.COMPLETE,
            GestureArbitration.settleDrag(
                displacedFraction = -0.6f,
                velocityPxPerSec = -5000f,
                completeFraction = 0.4f,
                flickVelocityPxPerSec = 3000f,
                cancelFraction = 0.1f,
            ),
        )
    }

    @Test
    fun settleDragZeroFlickVelocityMeansAnyVelocityCompletes() {
        assertEquals(
            Settle.COMPLETE,
            GestureArbitration.settleDrag(
                displacedFraction = 0.01f,
                velocityPxPerSec = 1f,
                completeFraction = 0.5f,
                flickVelocityPxPerSec = 0f,
                cancelFraction = 0f,
            ),
        )
    }

    @Test
    fun settleDragRejectsInvalidArgs() {
        // completeFraction ≤ 0.
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.settleDrag(0.5f, 1000f, 0f, 3000f, 0.1f)
        }
        // completeFraction > 1.
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.settleDrag(0.5f, 1000f, 1.5f, 3000f, 0.1f)
        }
        // flickVelocity < 0.
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.settleDrag(0.5f, 1000f, 0.4f, -1f, 0.1f)
        }
        // cancelFraction < 0.
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.settleDrag(0.5f, 1000f, 0.4f, 3000f, -0.1f)
        }
        // cancelFraction >= completeFraction.
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.settleDrag(0.5f, 1000f, 0.4f, 3000f, 0.4f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureArbitration.settleDrag(0.5f, 1000f, 0.4f, 3000f, 0.5f)
        }
    }

    // ── arbPriority ────────────────────────────────────────────────────────────

    @Test
    fun arbPriorityPickerWinsOverEverything() {
        // Even with tap and horizontal lock, picker wins.
        assertEquals(
            ArbWinner.PICKER,
            GestureArbitration.arbPriority(
                isPickerOpen = true, isTap = true, locked = LockedAxis.HORIZONTAL,
            ),
        )
        assertEquals(
            ArbWinner.PICKER,
            GestureArbitration.arbPriority(
                isPickerOpen = true, isTap = false, locked = LockedAxis.VERTICAL,
            ),
        )
    }

    @Test
    fun arbPriorityTapWinsOverAxis() {
        assertEquals(
            ArbWinner.TAP,
            GestureArbitration.arbPriority(
                isPickerOpen = false, isTap = true, locked = LockedAxis.HORIZONTAL,
            ),
        )
        assertEquals(
            ArbWinner.TAP,
            GestureArbitration.arbPriority(
                isPickerOpen = false, isTap = true, locked = LockedAxis.VERTICAL,
            ),
        )
        // Tap wins even with undecided axis.
        assertEquals(
            ArbWinner.TAP,
            GestureArbitration.arbPriority(
                isPickerOpen = false, isTap = true, locked = LockedAxis.UNDECIDED,
            ),
        )
    }

    @Test
    fun arbPriorityVerticalWinsOverHorizontal() {
        assertEquals(
            ArbWinner.VERTICAL,
            GestureArbitration.arbPriority(
                isPickerOpen = false, isTap = false, locked = LockedAxis.VERTICAL,
            ),
        )
    }

    @Test
    fun arbPriorityHorizontalWinsOverUndecided() {
        // Horizontal includes pager/scroll.
        assertEquals(
            ArbWinner.HORIZONTAL,
            GestureArbitration.arbPriority(
                isPickerOpen = false, isTap = false, locked = LockedAxis.HORIZONTAL,
            ),
        )
    }

    @Test
    fun arbPriorityUndecidedWhenNoConsumer() {
        assertEquals(
            ArbWinner.UNDECIDED,
            GestureArbitration.arbPriority(
                isPickerOpen = false, isTap = false, locked = LockedAxis.UNDECIDED,
            ),
        )
    }

    @Test
    fun arbPriorityOrderingIsPickerTapAxisPagerScroll() {
        // Systematic priority ordering check.
        val scenarios = listOf(
            // (isPickerOpen, isTap, locked) → expected
            Triple(true, true, LockedAxis.HORIZONTAL) to ArbWinner.PICKER,
            Triple(true, false, LockedAxis.VERTICAL) to ArbWinner.PICKER,
            Triple(true, false, LockedAxis.UNDECIDED) to ArbWinner.PICKER,
            Triple(false, true, LockedAxis.HORIZONTAL) to ArbWinner.TAP,
            Triple(false, true, LockedAxis.VERTICAL) to ArbWinner.TAP,
            Triple(false, true, LockedAxis.UNDECIDED) to ArbWinner.TAP,
            Triple(false, false, LockedAxis.VERTICAL) to ArbWinner.VERTICAL,
            Triple(false, false, LockedAxis.HORIZONTAL) to ArbWinner.HORIZONTAL,
            Triple(false, false, LockedAxis.UNDECIDED) to ArbWinner.UNDECIDED,
        )
        for ((input, expected) in scenarios) {
            val (picker, tap, axis) = input
            assertEquals(
                "picker=$picker tap=$tap axis=$axis",
                expected,
                GestureArbitration.arbPriority(picker, tap, axis),
            )
        }
    }
}