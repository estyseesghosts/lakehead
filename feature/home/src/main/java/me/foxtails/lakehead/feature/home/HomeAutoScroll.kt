package me.foxtails.lakehead.feature.home

/**
 * MOVE edge-auto-scroll constants and zone/velocity helpers.
 *
 * This object holds only auto-scroll tuning constants and the pure functions
 * that classify edge-proximity zones and turn depth into a scroll velocity. It
 * contains no legality checks, no workspace height and no synthetic scroll
 * maximum; workspace extent is owned by `HomeEditWorkspace.kt`.
 */
object HomeAutoScroll {
    /** Edge-proximity band for auto-scroll, measured from the real viewport edge, in dp. */
    const val AUTO_SCROLL_EDGE_DP: Int = 72

    /** Scroll speed with the pointer at (or past) the viewport edge, in dp per second. */
    const val AUTO_SCROLL_MAX_SPEED_DP_PER_SEC: Int = 720
}

/** Edge-proximity zone for MOVE auto-scroll, from a viewport-local pointer Y. */
enum class AutoScrollZone { NONE, TOP, BOTTOM }

/**
 * Classifies [pointerYPx] (viewport-local, zero at the viewport top) into an
 * auto-scroll zone: within [edgePx] of the top scrolls up, within [edgePx] of
 * the bottom scrolls down, otherwise no scroll. The top wins when the viewport
 * is shorter than twice the edge band. A zero-height viewport never scrolls.
 */
fun autoScrollZone(pointerYPx: Float, viewportHeightPx: Int, edgePx: Int): AutoScrollZone {
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    require(edgePx > 0) { "edgePx must be positive" }
    if (viewportHeightPx == 0) return AutoScrollZone.NONE
    if (pointerYPx < edgePx.toFloat()) return AutoScrollZone.TOP
    if (pointerYPx > (viewportHeightPx - edgePx).toFloat()) return AutoScrollZone.BOTTOM
    return AutoScrollZone.NONE
}

/**
 * Signed scroll velocity in px/s for a viewport-local pointer Y: negative scrolls
 * up, positive scrolls down, zero outside both bands. Speed grows linearly with
 * depth into the band from zero at its inner boundary to [maxSpeedPxPerSec] at
 * the viewport edge; pointers past the edge clamp to the maximum. Top and bottom
 * are symmetric for equal depths. Pure, framework-free.
 */
fun autoScrollVelocityPxPerSec(
    pointerYPx: Float,
    viewportHeightPx: Int,
    edgePx: Int,
    maxSpeedPxPerSec: Float,
): Float {
    require(maxSpeedPxPerSec >= 0f) { "maxSpeedPxPerSec must be non-negative" }
    val zone = autoScrollZone(pointerYPx, viewportHeightPx, edgePx)
    val depthPx = when (zone) {
        AutoScrollZone.NONE -> return 0f
        AutoScrollZone.TOP -> edgePx.toFloat() - pointerYPx
        AutoScrollZone.BOTTOM -> pointerYPx - (viewportHeightPx - edgePx).toFloat()
    }.coerceIn(0f, edgePx.toFloat())
    val speed = depthPx / edgePx.toFloat() * maxSpeedPxPerSec
    return if (zone == AutoScrollZone.TOP) -speed else speed
}
