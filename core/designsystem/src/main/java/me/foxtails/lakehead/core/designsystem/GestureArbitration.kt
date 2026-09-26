package me.foxtails.lakehead.core.designsystem

/**
 * Pure framework-free gesture arbitration predicates.
 *
 * Every parameter is a primitive (Float, Int, Long, Boolean); no Compose, Android,
 * ViewConfiguration or framework types are referenced. Thresholds are always passed
 * in by the caller so the caller's platform boundary owns the source of those values.
 *
 * This object decides **who wins** a multi-gesture conflict on the home screen:
 * horizontal page scroll, vertical overscroll / app-drawer gesture, tile drag,
 * picker scroll and tap. The predicates are deterministic and side-effect-free.
 */
object GestureArbitration {

    // ── enums ──────────────────────────────────────────────────────────────────

    /** Lock-state of the initial drag axis before a committed gesture direction. */
    enum class LockedAxis {
        /** No dominant direction detected yet. */
        UNDECIDED,
        /** Horizontal intent locked. */
        HORIZONTAL,
        /** Vertical intent locked. */
        VERTICAL,
    }

    /** Outcome of a drag that has been evaluated for settlement. */
    enum class Settle {
        /** Drag crosses displacement or velocity threshold → commit the action. */
        COMPLETE,
        /** Drag stays below both thresholds → snap back / cancel. */
        CANCEL,
    }

    /**
     * Final arbitration winner when multiple gesture consumers compete.
     *
     * Priority order (highest first): PICKER > TAP > VERTICAL > HORIZONTAL > UNDECIDED.
     * Pager/scroll falls under HORIZONTAL; the caller may map it as needed.
     */
    enum class ArbWinner {
        /** Alphabet picker or overlay scroll captured the gesture. */
        PICKER,
        /** Gesture qualified as a tap (below slop, no lock). */
        TAP,
        /** Vertical gesture (overscroll, drawer) won. */
        VERTICAL,
        /** Horizontal gesture (pager scroll, page swipe) won. */
        HORIZONTAL,
        /** No consumer claimed the gesture. */
        UNDECIDED,
    }

    // ── axis locking ───────────────────────────────────────────────────────────

    /**
     * Locks the drag axis once the cumulative displacement in either direction
     * exceeds [slopPx].
     *
     * When the current state is already [LockedAxis.HORIZONTAL] or
     * [LockedAxis.VERTICAL] the locked value is preserved — once committed, the
     * axis does not flip mid-gesture.
     *
     * @param dxPx        Cumulative horizontal displacement in pixels (signed).
     * @param dyPx        Cumulative vertical displacement in pixels (signed).
     * @param slopPx      Touch-slop threshold; must be ≥ 0. A value of 0 locks on
     *                    any non-zero displacement.
     * @param currentLock The axis lock state to preserve if already decided.
     * @return The updated [LockedAxis].
     */
    fun lockAxis(
        dxPx: Float,
        dyPx: Float,
        slopPx: Float,
        currentLock: LockedAxis = LockedAxis.UNDECIDED,
    ): LockedAxis {
        require(slopPx >= 0f) { "slopPx must be non-negative, was $slopPx" }
        if (currentLock != LockedAxis.UNDECIDED) return currentLock
        val adx = if (dxPx < 0f) -dxPx else dxPx
        val ady = if (dyPx < 0f) -dyPx else dyPx
        // Zero displacement is always undecided, even with zero slop.
        if (adx == 0f && ady == 0f) return LockedAxis.UNDECIDED
        if (adx < slopPx && ady < slopPx) return LockedAxis.UNDECIDED
        return if (adx >= ady) LockedAxis.HORIZONTAL else LockedAxis.VERTICAL
    }

    // ── dominant axis ──────────────────────────────────────────────────────────

    /**
     * Determines the dominant axis of a drag that has moved beyond jitter but
     * has not yet locked.
     *
     * A direction is "dominant" only when it exceeds the other by at least
     * [ratio] and both exceed [jitterPx]. If neither direction wins the ratio
     * test, [LockedAxis.UNDECIDED] is returned — the caller must not use this
     * result to steal a gesture from a diagonal drag.
     *
     * @param dxPx      Cumulative horizontal displacement in pixels (signed).
     * @param dyPx      Cumulative vertical displacement in pixels (signed).
     * @param jitterPx  Minimum displacement before either axis is considered;
     *                  must be ≥ 0.
     * @param ratio     Multiplier the winning axis must exceed the losing by;
     *                  defaults to 1.25f, must be > 1.
     * @return The dominant [LockedAxis] or [LockedAxis.UNDECIDED].
     */
    fun dominantAxis(
        dxPx: Float,
        dyPx: Float,
        jitterPx: Float,
        ratio: Float = 1.25f,
    ): LockedAxis {
        require(jitterPx >= 0f) { "jitterPx must be non-negative, was $jitterPx" }
        require(ratio > 1f) { "ratio must be greater than 1, was $ratio" }
        val adx = if (dxPx < 0f) -dxPx else dxPx
        val ady = if (dyPx < 0f) -dyPx else dyPx
        // If either axis is below jitter the direction is too noisy to claim.
        if (adx < jitterPx || ady < jitterPx) return LockedAxis.UNDECIDED
        // A diagonal drag where neither axis is ratio × the other must not steal.
        if (adx >= ady * ratio) return LockedAxis.HORIZONTAL
        if (ady >= adx * ratio) return LockedAxis.VERTICAL
        return LockedAxis.UNDECIDED
    }

    // ── settle ─────────────────────────────────────────────────────────────────

    /**
     * Decides whether a drag should settle (commit) or cancel (snap back).
     *
     * Settlement requires **either**:
     *  1. |[displacedFraction]| ≥ [completeFraction], **or**
     *  2. |[velocityPxPerSec]|  ≥ [flickVelocityPxPerSec].
     *
     * Cancellation is the default when neither threshold is met and the
     * displacement is below [cancelFraction].
     *
     * When displacement is between [cancelFraction] and [completeFraction]
     * **and** velocity is below flick, the drag cancels — there is no ambiguous
     * middle ground.
     *
     * @param displacedFraction     Current drag offset as a fraction of the
     *                              total track, in `[-1, 1]`. Sign is irrelevant;
     *                              the absolute value is used.
     * @param velocityPxPerSec      Current fling velocity in px/s (signed);
     *                              absolute value is used.
     * @param completeFraction      Fraction threshold at which the drag is
     *                              considered committed; must be in `(0, 1]`.
     * @param flickVelocityPxPerSec Velocity threshold for an immediate flick
     *                              settlement; must be ≥ 0.
     * @param cancelFraction        Fraction below which the drag is too small
     *                              to commit; must be in `[0, completeFraction)`.
     * @return [Settle.COMPLETE] or [Settle.CANCEL].
     */
    fun settleDrag(
        displacedFraction: Float,
        velocityPxPerSec: Float,
        completeFraction: Float,
        flickVelocityPxPerSec: Float,
        cancelFraction: Float,
    ): Settle {
        require(completeFraction > 0f && completeFraction <= 1f) {
            "completeFraction must be in (0,1], was $completeFraction"
        }
        require(flickVelocityPxPerSec >= 0f) {
            "flickVelocityPxPerSec must be non-negative, was $flickVelocityPxPerSec"
        }
        require(cancelFraction >= 0f && cancelFraction < completeFraction) {
            "cancelFraction must be in [0, completeFraction), was $cancelFraction"
        }
        val adf = if (displacedFraction < 0f) -displacedFraction else displacedFraction
        val av = if (velocityPxPerSec < 0f) -velocityPxPerSec else velocityPxPerSec
        if (adf >= completeFraction) return Settle.COMPLETE
        if (av >= flickVelocityPxPerSec) return Settle.COMPLETE
        return Settle.CANCEL
    }

    // ── arbitration priority ───────────────────────────────────────────────────

    /**
     * Resolves gesture-winner priority when multiple consumers compete.
     *
     * Priority order (highest → lowest):
     *  1. [isPickerOpen] → [ArbWinner.PICKER]
     *  2. [isTap]        → [ArbWinner.TAP]
     *  3. [locked] == [LockedAxis.VERTICAL]   → [ArbWinner.VERTICAL]
     *  4. [locked] == [LockedAxis.HORIZONTAL]  → [ArbWinner.HORIZONTAL]
     *  5. otherwise                             → [ArbWinner.UNDECIDED]
     *
     * Pager/scroll gestures are subsumed under [ArbWinner.HORIZONTAL]; callers
     * may map to a more specific type if needed.
     *
     * @param isPickerOpen Whether the alphabet picker or overlay is currently open.
     * @param isTap        Whether the gesture was classified as a tap (below slop,
     *                     no axis lock, pointer up within tap timeout).
     * @param locked       The axis lock state determined by [lockAxis] or
     *                     [dominantAxis].
     * @return The winning [ArbWinner].
     */
    fun arbPriority(
        isPickerOpen: Boolean,
        isTap: Boolean,
        locked: LockedAxis,
    ): ArbWinner {
        if (isPickerOpen) return ArbWinner.PICKER
        if (isTap) return ArbWinner.TAP
        return when (locked) {
            LockedAxis.VERTICAL -> ArbWinner.VERTICAL
            LockedAxis.HORIZONTAL -> ArbWinner.HORIZONTAL
            LockedAxis.UNDECIDED -> ArbWinner.UNDECIDED
        }
    }
}