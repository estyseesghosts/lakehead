package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridRest

/**
 * Transient vertical viewport state for a scrolling home canvas.
 *
 * Tracks the grid's vertical origin ([gridOriginY]), the current scroll
 * position within the scroll range ([viewportOffsetY]), and the available
 * scroll range ([scrollRangePx]). This is presentation-only state that lives
 * in the feature layer; it is never persisted.
 *
 * - [gridOriginY] is the pixel offset of the content's top edge relative to
 *   the viewport top at the rest position. It is negative when content is
 *   anchored above the viewport (tall content with non-zero minRow), positive
 *   when biased below (short content with free space), or zero for tall
 *   content anchored at the top.
 * - [viewportOffsetY] is in `[-scrollRangePx, 0]`. Zero means the content is
 *   at its rest position; negative values represent scrolling down (content
 *   shifted up). It is zero when content fits within the viewport.
 * - [scrollRangePx] is the maximum scroll distance in pixels; zero when
 *   content fits within the viewport.
 */
data class Viewport(
    val gridOriginY: Long,
    val viewportOffsetY: Long,
    val scrollRangePx: Long,
) {
    init {
        require(scrollRangePx >= 0L) { "scrollRangePx must be non-negative" }
        require(viewportOffsetY in -scrollRangePx..0L) {
            "viewportOffsetY ($viewportOffsetY) must be in [-$scrollRangePx, 0]"
        }
    }

    companion object {
        /**
         * Minimum new scroll range (in pixels) to preserve the anchor when
         * transitioning from short (non-scrollable) to tall (scrollable)
         * content. If the new scroll range would be below this threshold,
         * the viewport snaps to the top instead of preserving an unusable
         * tiny scroll range.
         */
        internal const val PRESERVE_ANCHOR_EPSILON: Long = 1L
    }
}

/**
 * Clamps [offset] to the valid range `[-range, 0]`.
 *
 * Returns 0 when [range] is zero (content fits viewport; no scroll needed).
 */
fun clampViewportOffset(offset: Long, range: Long): Long {
    require(range >= 0L) { "range must be non-negative" }
    return if (range == 0L) 0L else offset.coerceIn(-range, 0L)
}

/**
 * Creates a [Viewport] from a [GridRest] position.
 *
 * Returns a zero [Viewport] when [rest] is `null` (invalid viewport height
 * or rest arithmetic overflow).
 *
 * For scrollable (tall) content, [GridRest.gridOriginY] is the anchored
 * origin (typically 0) and the viewport starts at the rest position
 * (offset 0). For non-scrollable (short) content, [GridRest.gridOriginY]
 * is the bias centering offset and the viewport also starts at offset 0
 * since no scrolling is available.
 */
fun viewportFor(rest: GridRest?): Viewport {
    if (rest == null) return Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = 0L)
    return Viewport(
        gridOriginY = rest.gridOriginY,
        viewportOffsetY = 0L,
        scrollRangePx = rest.scrollRangePx,
    )
}

/**
 * Derives an updated [Viewport] after a resize that changed the rest position.
 *
 * Preserves the visible anchor when the content was tall and remains tall,
 * collapses scroll when the content becomes short (fits viewport), and snaps
 * to the top when a short-to-tall transition produces an unusable tiny scroll
 * range. The [Viewport.gridOriginY] of the returned viewport always reflects
 * the new rest's origin so the container offset stays synchronized.
 *
 * Transition semantics:
 * - **null → zero**: [newRest] is `null`; returns a zero viewport.
 * - **short/initial → tall**: [old] viewport is at offset 0 (either short
 *   content or initial state). If the new scroll range is at least
 *   [Viewport.PRESERVE_ANCHOR_EPSILON], preserves the anchor by adjusting
 *   the offset for the origin delta; otherwise snaps to the top.
 * - **tall → short**: [newRest] is not scrollable. Content now fits;
 *   collapses scroll to 0.
 * - **tall → tall**: Both scrollable. Preserves the anchor by adjusting the
 *   offset for the origin delta, clamped to the new range. Bottom-pins when
 *   the content grows.
 * - **short → short**: [old] was at offset 0. New rest is also at offset 0.
 *   Returns the new rest viewport.
 */
fun preserveAnchorOnResize(old: Viewport, newRest: GridRest?): Viewport {
    if (newRest == null) return Viewport(gridOriginY = 0L, viewportOffsetY = 0L, scrollRangePx = 0L)

    val newRange = newRest.scrollRangePx
    val newOrigin = newRest.gridOriginY

    // Short/initial → tall: old viewport was at offset 0 (content fit or
    // initial state) and new content is scrollable.
    if (old.viewportOffsetY == 0L && old.scrollRangePx == 0L && newRange > 0L) {
        return if (newRange <= Viewport.PRESERVE_ANCHOR_EPSILON) {
            // Tiny scroll range: snap to the top for usability.
            Viewport(gridOriginY = newOrigin, viewportOffsetY = 0L, scrollRangePx = newRange)
        } else {
            // Preserve the anchor: the content was positioned at old.gridOriginY
            // from the viewport top. After resize, the new rest origin is
            // newOrigin. The content top moves by (newOrigin - old.gridOriginY);
            // compensate with the opposite offset to keep it in place.
            val anchorOffset = old.gridOriginY - newOrigin
            val clamped = clampViewportOffset(anchorOffset, newRange)
            Viewport(gridOriginY = newOrigin, viewportOffsetY = clamped, scrollRangePx = newRange)
        }
    }

    // Tall → short: new content fits the viewport.
    if (old.scrollRangePx > 0L && newRange == 0L) {
        return Viewport(gridOriginY = newOrigin, viewportOffsetY = 0L, scrollRangePx = 0L)
    }

    // Tall → tall: preserve the anchor, clamped to the new range.
    if (old.scrollRangePx > 0L && newRange > 0L) {
        val anchorOffset = old.gridOriginY - newOrigin + old.viewportOffsetY
        val clamped = clampViewportOffset(anchorOffset, newRange)
        return Viewport(gridOriginY = newOrigin, viewportOffsetY = clamped, scrollRangePx = newRange)
    }

    // Short → short or other zero-range transitions.
    return Viewport(gridOriginY = newOrigin, viewportOffsetY = 0L, scrollRangePx = 0L)
}