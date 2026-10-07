package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Edge auto-scroll for a held MOVE: every frame the real [scrollState] moves by
 * the pointer's depth-proportional velocity ([autoScrollVelocityPxPerSec]) and
 * the candidate is then recomputed from the unchanged pointer and the new
 * scroll through [onScrolled]. Sub-pixel remainders carry to the next frame, so
 * speed is independent of frame rate. It runs until [isMoveActive] turns false
 * (the caller cancels it with the MOVE). The scroll range is whatever the
 * measured workspace allows, so reaching either end simply stops moving.
 * Scrolling never commits anything.
 */
internal suspend fun runMoveEdgeAutoScroll(
    scrollState: ScrollState,
    isMoveActive: () -> Boolean,
    pointer: () -> Offset?,
    viewportPx: () -> Int,
    edgePx: Int,
    maxSpeedPxPerSec: Float,
    onScrolled: (pointer: Offset) -> Unit,
) {
    fun velocityPxPerSec(): Float {
        val held = pointer() ?: return 0f
        val velocity = autoScrollVelocityPxPerSec(held.y, viewportPx(), edgePx, maxSpeedPxPerSec)
        // At either end there is nothing to scroll, so no frames are requested.
        val room = if (velocity > 0f) scrollState.value < scrollState.maxValue else scrollState.value > 0
        return if (room) velocity else 0f
    }
    while (isMoveActive()) {
        // Frames are requested only while the pointer is inside a band; outside
        // it the loop suspends on the snapshot, costing nothing per frame.
        snapshotFlow { velocityPxPerSec() }.first { it != 0f }
        var lastFrameNanos = 0L
        var carryPx = 0f
        while (isMoveActive()) {
            val frameNanos = withFrameNanos { it }
            val elapsedSec = if (lastFrameNanos == 0L) 0f else (frameNanos - lastFrameNanos) / 1e9f
            lastFrameNanos = frameNanos
            val held = pointer()
            val velocity = velocityPxPerSec()
            if (held == null || velocity == 0f) break
            carryPx += velocity * elapsedSec
            val wholePx = carryPx.toInt()
            if (wholePx == 0) continue
            carryPx -= wholePx
            val consumed = scrollState.scrollBy(wholePx.toFloat())
            if (abs(consumed) > 0f) onScrolled(held)
        }
    }
}
