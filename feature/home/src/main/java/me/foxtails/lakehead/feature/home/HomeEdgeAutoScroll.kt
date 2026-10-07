package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay

/**
 * Pure scroll target for one auto-scroll step: [AutoScrollZone.BOTTOM] moves
 * down by [stepPx], [AutoScrollZone.TOP] moves up by [stepPx] and stops at the
 * top, [AutoScrollZone.NONE] stays put. The real `ScrollState` clamps the
 * downward end to its own measured maximum.
 */
internal fun nextAutoScrollTarget(zone: AutoScrollZone, scrollPx: Int, stepPx: Int): Int {
    require(scrollPx >= 0) { "scrollPx must be non-negative" }
    require(stepPx > 0) { "stepPx must be positive" }
    return when (zone) {
        AutoScrollZone.BOTTOM -> (scrollPx.toLong() + stepPx).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        AutoScrollZone.TOP -> (scrollPx - stepPx).coerceAtLeast(0)
        AutoScrollZone.NONE -> scrollPx
    }
}

/**
 * Edge auto-scroll for a held MOVE: while the pointer stays in an edge band the
 * real [scrollState] moves one [stepPx] per proximity-scaled interval after an
 * entry dwell, and the candidate is then recomputed from the unchanged pointer
 * and the new current scroll through [onScrolled]. Nothing is remembered about
 * earlier steps: no budget, no baseline, no copy of the frame. It runs until
 * [isMoveActive] turns false (the caller cancels it with the MOVE) and a
 * downward step scrolls only as far as the measured content allows, so reaching
 * the top or the end of the workspace simply stops moving.
 *
 * Before each downward step [extendWorkspace] is given the content Y the step
 * will reveal, so the workspace (and with it the real scroll range) already
 * covers it. Scrolling never commits anything.
 */
internal suspend fun runMoveEdgeAutoScroll(
    scrollState: ScrollState,
    isMoveActive: () -> Boolean,
    pointer: () -> Offset?,
    bandHeightPx: () -> Int,
    scrollViewportPx: () -> Int,
    edgePx: Int,
    stepPx: () -> Int,
    extendWorkspace: (revealedBottomPx: Long) -> Unit,
    onScrolled: (pointer: Offset) -> Unit,
) {
    var dwellZone = AutoScrollZone.NONE
    while (isMoveActive()) {
        val held = pointer()
        val zone = if (held == null) {
            AutoScrollZone.NONE
        } else {
            autoScrollZone(held.y, bandHeightPx(), edgePx)
        }
        if (held == null || zone == AutoScrollZone.NONE) {
            dwellZone = AutoScrollZone.NONE
            delay(HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS)
            continue
        }
        if (zone != dwellZone) {
            // Edge entry or zone change: dwell, then re-read the pointer.
            dwellZone = zone
            delay(HomeEditFrontier.AUTO_SCROLL_DWELL_MS)
            continue
        }
        val step = stepPx()
        if (zone == AutoScrollZone.BOTTOM) {
            extendWorkspace(scrollState.value.toLong() + scrollViewportPx() + step)
        }
        val before = scrollState.value
        scrollState.scrollTo(nextAutoScrollTarget(zone, before, step))
        if (scrollState.value != before) onScrolled(held)
        delay(
            autoScrollIntervalMs(held.y, bandHeightPx(), edgePx)
                ?: HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS,
        )
    }
}
