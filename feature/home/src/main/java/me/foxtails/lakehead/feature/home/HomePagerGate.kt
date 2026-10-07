package me.foxtails.lakehead.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis

/**
 * The only owner of pager-versus-grid gesture ownership for one Home grid.
 *
 * It holds two transient signals and nothing else: [hold] (a tile touch owns
 * the pointer, so the pager must not swipe) and [axisLock] (an idle background
 * gesture committed to a horizontal swipe, so vertical scroll must yield).
 * The edit drag only claims and releases [hold]; it never reads [axisLock],
 * so pager state cannot alter cell math, edit state or a drag's outcome.
 */
internal class HomePagerGate {
    var hold: Boolean by mutableStateOf(false)
        private set

    var axisLock: LockedAxis by mutableStateOf(LockedAxis.UNDECIDED)
        private set

    /** A tile-origin winner holds the pager for the gesture; background/scroll leave it free. */
    fun claim(winner: HomeGestureWinner) {
        hold = winner.holdsPager()
    }

    /** Ends the tile-touch hold. Idempotent. */
    fun release() {
        hold = false
    }

    /** Records the idle gesture's axis decision; writes only on a transition. */
    fun lock(axis: LockedAxis) {
        if (axisLock != axis) axisLock = axis
    }

    fun resetAxis() = lock(LockedAxis.UNDECIDED)

    /** Whether vertical scroll must yield to a horizontal pager swipe. */
    val yieldsToPager: Boolean get() = axisLock == LockedAxis.HORIZONTAL
}
