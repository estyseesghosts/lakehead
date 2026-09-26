package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.GestureArbitration

/**
 * Pure home-screen gesture arbitration. Strict priority ordering when
 * multiple gesture consumers compete on the home canvas.
 *
 * Priority (highest → lowest):
 *  1. UNPIN          – shortcut pin toggle while editing
 *  2. HANDLE         – resize handle press
 *  3. ACTIVE_RESIZE  – ongoing resize drag
 *  4. ACTIVE_MOVE    – ongoing move drag
 *  5. SELECT_DRAG    – wiggle-mode selection drag
 *  6. TAP            – tap on tile (select or launch)
 *  7. SCROLL         – vertical scroll / pager swipe
 *  8. BACKGROUND     – tap on empty grid space
 *
 * Reuses [GestureArbitration.lockAxis] and [GestureArbitration.dominantAxis]
 * predicates only; does not depend on [GestureArbitration.arbPriority].
 */
enum class HomeGestureWinner {
    UNPIN,
    HANDLE,
    ACTIVE_RESIZE,
    ACTIVE_MOVE,
    SELECT_DRAG,
    TAP,
    SCROLL,
    BACKGROUND,
}

/**
 * Resolves the winning gesture consumer by strict priority order.
 *
 * Exactly one winner is returned based on the first (highest-priority) true
 * flag. Callers pass boolean flags indicating which consumers are currently
 * eligible for the observed gesture.
 *
 * @param isUnpin        True when the gesture targets a pinned shortcut for unpin.
 * @param isOnHandle     True when the pointer is in the resize handle zone.
 * @param isActiveResize True when an active resize drag is in progress.
 * @param isActiveMove   True when an active move drag is in progress.
 * @param isSelectDrag   True when a wiggle-mode select drag is in progress.
 * @param isTap          True when the gesture qualifies as a tap (below slop).
 * @param isScroll       True when a scroll or pager gesture is active.
 * @param isBackground   True when the gesture targets empty grid space.
 * @return The highest-priority [HomeGestureWinner].
 */
fun resolveHomeGesture(
    isUnpin: Boolean = false,
    isOnHandle: Boolean = false,
    isActiveResize: Boolean = false,
    isActiveMove: Boolean = false,
    isSelectDrag: Boolean = false,
    isTap: Boolean = false,
    isScroll: Boolean = false,
    isBackground: Boolean = false,
): HomeGestureWinner {
    if (isUnpin) return HomeGestureWinner.UNPIN
    if (isOnHandle) return HomeGestureWinner.HANDLE
    if (isActiveResize) return HomeGestureWinner.ACTIVE_RESIZE
    if (isActiveMove) return HomeGestureWinner.ACTIVE_MOVE
    if (isSelectDrag) return HomeGestureWinner.SELECT_DRAG
    if (isTap) return HomeGestureWinner.TAP
    if (isScroll) return HomeGestureWinner.SCROLL
    return HomeGestureWinner.BACKGROUND
}

/**
 * Classifies the drag axis for home-screen arbitration, applying the diagonal
 * guard on top of [GestureArbitration.lockAxis].
 *
 * When the initial lock is [GestureArbitration.LockedAxis.UNDECIDED] and
 * [GestureArbitration.lockAxis] would commit, the diagonal guard checks
 * [GestureArbitration.dominantAxis]: if both axes exceed [jitterPx] but
 * neither dominates (ratio not met), the lock holds UNDECIDED so a diagonal
 * drag never steals the gesture from either axis consumer. Once committed
 * (non-UNDECIDED currentLock), the lock is preserved per the [lockAxis]
 * contract.
 *
 * This predicate is the single source of the "diagonal never steals"
 * invariant for the home canvas.
 *
 * @param dxPx        Cumulative horizontal displacement in pixels (signed).
 * @param dyPx        Cumulative vertical displacement in pixels (signed).
 * @param slopPx      Touch-slop threshold; must be ≥ 0.
 * @param jitterPx    Minimum displacement for dominant-axis evaluation;
 *                    must be ≥ 0.
 * @param currentLock The axis lock state to preserve if already decided.
 * @return The classified [GestureArbitration.LockedAxis].
 */
fun classifyHomeAxis(
    dxPx: Float,
    dyPx: Float,
    slopPx: Float,
    jitterPx: Float,
    currentLock: GestureArbitration.LockedAxis = GestureArbitration.LockedAxis.UNDECIDED,
): GestureArbitration.LockedAxis {
    val locked = GestureArbitration.lockAxis(
        dxPx = dxPx,
        dyPx = dyPx,
        slopPx = slopPx,
        currentLock = currentLock,
    )
    // Diagonal guard: only applies on the initial commit (currentLock == UNDECIDED).
    // If both axes exceed jitter and neither dominates, hold UNDECIDED.
    if (currentLock == GestureArbitration.LockedAxis.UNDECIDED &&
        locked != GestureArbitration.LockedAxis.UNDECIDED
    ) {
        val adx = if (dxPx < 0f) -dxPx else dxPx
        val ady = if (dyPx < 0f) -dyPx else dyPx
        if (adx >= jitterPx && ady >= jitterPx) {
            val dominant = GestureArbitration.dominantAxis(
                dxPx = dxPx,
                dyPx = dyPx,
                jitterPx = jitterPx,
            )
            if (dominant == GestureArbitration.LockedAxis.UNDECIDED) {
                return GestureArbitration.LockedAxis.UNDECIDED
            }
        }
    }
    return locked
}