package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.FloatingOverlayFrame
import me.foxtails.lakehead.core.designsystem.FrameAnchor
import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.HorizontalFrameAnchor
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.designsystem.VerticalFrameAnchor
import me.foxtails.lakehead.core.model.ItemId

/**
 * Transient pop-out shortcut surface state for one home tile (S2).
 *
 * B1: swipe-to-open is removed — no idle/editing flick ever shows a surface,
 * so the flick classifier, its thresholds and the show trigger are gone
 * (deleted: `classifySwipeFlick`, the `SWIPE_SURFACE_*` threshold constants
 * and `SwipeSurfaceState.showSurface`). The show/hide
 * machine ([Hidden]/[Shown]/[dismiss]/[dismissOnTileRemoved]) and the
 * [swipeSurfaceFrame] geometry below are intentionally retained: B4
 * (edit-mode shortcut menu above the selection) reuses the frame measurement
 * and dismiss semantics with a selection-driven trigger.
 *
 * Presentation state only: which tile's surface is currently exposed, if any.
 * No inventory, platform access, persistence or grid legality lives here.
 *
 * - [Hidden] exposes no surface.
 * - [Shown] exposes the surface for [tileId].
 */
sealed interface SwipeSurfaceState {
    /** No shortcut surface is exposed. */
    data object Hidden : SwipeSurfaceState

    /** The shortcut surface for [tileId] is exposed. */
    data class Shown(val tileId: ItemId) : SwipeSurfaceState
}

/**
 * Hides any exposed surface: background tap, Back, scroll, pager lock, edit entry.
 */
fun SwipeSurfaceState.dismiss(): SwipeSurfaceState = SwipeSurfaceState.Hidden

/**
 * Hides the surface when its tile disappears (uninstall, removal, regen without it).
 *
 * Returns [Hidden] only when this state is [SwipeSurfaceState.Shown] for exactly
 * [removedId]; every other state (including [SwipeSurfaceState.Shown] for a different
 * tile) is returned unchanged.
 */
fun SwipeSurfaceState.dismissOnTileRemoved(removedId: ItemId): SwipeSurfaceState =
    if (this is SwipeSurfaceState.Shown && tileId == removedId) {
        SwipeSurfaceState.Hidden
    } else {
        this
    }

/**
 * Measures the shortcut-surface overlay frame beside a tile: try beside, flip, clamp.
 *
 * Pure geometry; no composition, no reflow, no tile mutation. The caller renders the
 * returned frame as a sibling above the tiles so neighbouring tiles are overlaid,
 * never moved.
 *
 * Attempt order, all validated through [FloatingOverlayFrame.measure] against
 * [viewport]:
 *
 * 1. Beside the tile on the trailing side (`tileBounds.right + gapPx`, top-aligned).
 * 2. Flipped to the leading side (`tileBounds.left - gapPx - width`, top-aligned)
 *    when the trailing side would leave the viewport (right-edge tiles).
 * 3. Clamped into the viewport (leading/top edges coerced so the frame fits) when
 *    neither side fits unclamped (corners, tiles wider than the remaining space).
 *
 * [tileBounds] is the tile's content-local frame (may start above the content top for
 * rows above the signed origin; such candidates fail validation and fall through to
 * clamping). [viewport] is the content-local overlay region (origin at 0,0). [gapPx]
 * separates tile and surface. The overlay must fit the viewport on its own; oversized
 * requests throw instead of silently overflowing.
 */
fun swipeSurfaceFrame(
    viewport: PixelRect,
    tileBounds: SignedPixelFrame,
    overlaySize: FrameSize,
    gapPx: Int = 0,
): PixelRect {
    require(gapPx >= 0) { "gapPx must be non-negative, was $gapPx" }
    require(overlaySize.widthPx <= viewport.width && overlaySize.heightPx <= viewport.height) {
        "overlay $overlaySize does not fit viewport $viewport"
    }
    val besideLeft = tileBounds.right.toLong() + gapPx
    val flippedLeft = tileBounds.left.toLong() - gapPx - overlaySize.widthPx
    val top = tileBounds.top.toLong()
    tryMeasureAt(viewport, overlaySize, besideLeft, top)?.let { return it }
    tryMeasureAt(viewport, overlaySize, flippedLeft, top)?.let { return it }
    val clampedLeft = besideLeft.coerceIn(
        viewport.left.toLong(),
        viewport.right.toLong() - overlaySize.widthPx,
    )
    val clampedTop = top.coerceIn(
        viewport.top.toLong(),
        viewport.bottom.toLong() - overlaySize.heightPx,
    )
    return FloatingOverlayFrame.measure(
        viewport = viewport,
        size = overlaySize,
        anchor = FrameAnchor(
            horizontal = HorizontalFrameAnchor.LEFT,
            vertical = VerticalFrameAnchor.TOP,
            offsetXPx = (clampedLeft - viewport.left).toInt(),
            offsetYPx = (clampedTop - viewport.top).toInt(),
        ),
    )
}

/**
 * [PixelRect] tile overload of [swipeSurfaceFrame] for callers without signed frames.
 */
fun swipeSurfaceFrame(
    viewport: PixelRect,
    tileBounds: PixelRect,
    overlaySize: FrameSize,
    gapPx: Int = 0,
): PixelRect = swipeSurfaceFrame(
    viewport = viewport,
    tileBounds = SignedPixelFrame(
        left = tileBounds.left,
        top = tileBounds.top,
        widthPx = tileBounds.width,
        heightPx = tileBounds.height,
    ),
    overlaySize = overlaySize,
    gapPx = gapPx,
)

private fun tryMeasureAt(
    viewport: PixelRect,
    size: FrameSize,
    left: Long,
    top: Long,
): PixelRect? {
    if (left < Int.MIN_VALUE.toLong() || left > Int.MAX_VALUE.toLong()) return null
    if (top < Int.MIN_VALUE.toLong() || top > Int.MAX_VALUE.toLong()) return null
    return try {
        FloatingOverlayFrame.measure(
            viewport = viewport,
            size = size,
            anchor = FrameAnchor(
                horizontal = HorizontalFrameAnchor.LEFT,
                vertical = VerticalFrameAnchor.TOP,
                offsetXPx = (left - viewport.left).toInt(),
                offsetYPx = (top - viewport.top).toInt(),
            ),
        )
    } catch (e: IllegalArgumentException) {
        null
    }
}
