package me.foxtails.lakehead.feature.home

/**
 * Edge auto-scroll timing and zone helpers.
 *
 * Auto-scroll is deliberately simple: while a MOVE pointer stays in an edge
 * band the grid scrolls the real `ScrollState` one row stride per interval and
 * recomputes the candidate from the unchanged pointer and the new scroll. Edit
 * reachability below the content is owned by the workspace in
 * `HomeEditWorkspace.kt`, never by a synthetic scroll maximum here.
 */
object HomeEditFrontier {
    /** Edge-proximity band for auto-scroll, in dp. */
    const val AUTO_SCROLL_EDGE_DP: Int = 32

    /** Dwell on edge entry before the first auto-scroll step, in milliseconds. */
    const val AUTO_SCROLL_DWELL_MS: Long = 200L

    /** Fastest auto-scroll interval (deep at the viewport edge), in milliseconds. */
    const val AUTO_SCROLL_MIN_INTERVAL_MS: Long = 120L

    /** Slowest auto-scroll interval (at the inner band boundary), in milliseconds. */
    const val AUTO_SCROLL_MAX_INTERVAL_MS: Long = 300L
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

/** Auto-scroll step in pixels: one row stride, coerced into [Int]. */
fun autoScrollStepPx(rowStridePx: Long): Int {
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    return rowStridePx.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Proximity-scaled auto-scroll interval for a viewport-local pointer Y.
 *
 * Returns null when [pointerYPx] is outside both edge bands ([autoScrollZone]
 * reports NONE). Inside a band the interval scales linearly with depth: the
 * inner boundary scrolls slowest ([HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS])
 * and the viewport edge scrolls fastest
 * ([HomeEditFrontier.AUTO_SCROLL_MIN_INTERVAL_MS]). Top and bottom are
 * symmetric for equal depths. Pointers past the viewport edge clamp to the
 * fastest interval. Pure, framework-free.
 */
fun autoScrollIntervalMs(
    pointerYPx: Float,
    viewportHeightPx: Int,
    edgePx: Int,
): Long? {
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    require(edgePx > 0) { "edgePx must be positive" }
    val zone = autoScrollZone(pointerYPx, viewportHeightPx, edgePx)
    if (zone == AutoScrollZone.NONE) return null
    val depthPx = when (zone) {
        AutoScrollZone.TOP -> edgePx.toFloat() - pointerYPx
        AutoScrollZone.BOTTOM -> pointerYPx - (viewportHeightPx - edgePx).toFloat()
        AutoScrollZone.NONE -> return null
    }.coerceIn(0f, edgePx.toFloat())
    val fraction = depthPx / edgePx.toFloat()
    val max = HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS
    val min = HomeEditFrontier.AUTO_SCROLL_MIN_INTERVAL_MS
    return (max - fraction * (max - min).toFloat()).toLong()
}
