package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.FloatingOverlayFrame
import me.foxtails.lakehead.core.designsystem.FrameAnchor
import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.designsystem.HorizontalFrameAnchor
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.designsystem.VerticalFrameAnchor
import me.foxtails.lakehead.core.model.ItemId

/**
 * Transient pop-out shortcut surface for one home tile (S2).
 *
 * Presentation state only: which tile's shortcut surface is currently exposed, if any.
 * No inventory, platform access, persistence or grid legality lives here. The caller
 * (the home grid) owns the [SwipeSurfaceState] value, resolves rank-ordered rows for
 * the shown tile, and reports execute/pin callbacks upward; persistence of pin choices
 * lands separately.
 *
 * - [Hidden] exposes no surface.
 * - [Shown] exposes the surface for [tileId]; any swipe direction (left/right/up/down)
 *   opens the same surface and swipe direction never reorders rows.
 */
sealed interface SwipeSurfaceState {
    /** No shortcut surface is exposed. */
    data object Hidden : SwipeSurfaceState

    /** The shortcut surface for [tileId] is exposed. */
    data class Shown(val tileId: ItemId) : SwipeSurfaceState
}

/**
 * Exposes the shortcut surface for [tileId].
 *
 * Any tile replaces any other: showing B while A is shown transfers directly to B
 * with no intermediate hidden state.
 */
fun SwipeSurfaceState.showSurface(tileId: ItemId): SwipeSurfaceState =
    SwipeSurfaceState.Shown(tileId)

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
 * Displacement fraction at or above which a tile swipe commits to [GestureArbitration.Settle.COMPLETE].
 *
 * Kept inside the 0.18–0.25 band: a short deliberate swipe opens the surface while a
 * tap (well below slop) still cancels. Sign is irrelevant; [GestureArbitration.settleDrag]
 * uses the absolute value so every swipe direction is equivalent.
 */
const val SWIPE_SURFACE_COMPLETE_FRACTION: Float = 0.22f

/**
 * Displacement fraction below [SWIPE_SURFACE_COMPLETE_FRACTION] at which a slow swipe
 * still cancels when velocity is also below flick. Must stay below
 * [SWIPE_SURFACE_COMPLETE_FRACTION]; enforced by [GestureArbitration.settleDrag].
 */
const val SWIPE_SURFACE_CANCEL_FRACTION: Float = 0.08f

/**
 * Deliberate-flick velocity threshold in dp/s, scaled by screen density at classification
 * time (`px/s = this * density`) so the feel is density-independent rather than a
 * hard-coded pixel constant.
 */
const val SWIPE_SURFACE_FLICK_DP_PER_SEC: Float = 1000f

/**
 * Scroll movement in dp during a swipe that proves sustained scrolling over a flick.
 *
 * The idle gesture observer suppresses the surface show when the scroll offset moved
 * more than this between pointer-down and pointer-up: a deliberate flick displaces the
 * finger only a short distance (most of it absorbed by slop), while a sustained
 * vertical drag travels much farther. Heuristic pending on-device tuning; the
 * scroll/pager dismiss effects stay authoritative for gestures after a show.
 */
const val SWIPE_SURFACE_SCROLL_SUPPRESS_DP: Int = 48

/**
 * Classifies a tile swipe as a surface flick ([GestureArbitration.Settle.COMPLETE]) or a
 * non-open ([GestureArbitration.Settle.CANCEL]).
 *
 * Thin delegate to [GestureArbitration.settleDrag] with the S2 thresholds: complete at
 * [SWIPE_SURFACE_COMPLETE_FRACTION], cancel below at [SWIPE_SURFACE_CANCEL_FRACTION],
 * flick velocity [SWIPE_SURFACE_FLICK_DP_PER_SEC] density-scaled to px/s. Both
 * [displacedFraction] (fraction of one grid cell, sign irrelevant) and [velocityPxPerSec]
 * (signed px/s, absolute value used) follow the `settleDrag` contract, so any swipe
 * direction classifies identically.
 *
 * The caller decides the track (one grid cell) and suppresses competing gestures
 * (edit session active, sustained scroll/pager movement); this function only applies
 * the thresholds and never consumes input.
 */
fun classifySwipeFlick(
    displacedFraction: Float,
    velocityPxPerSec: Float,
    density: Float,
): GestureArbitration.Settle {
    require(density > 0f) { "density must be positive, was $density" }
    return GestureArbitration.settleDrag(
        displacedFraction = displacedFraction,
        velocityPxPerSec = velocityPxPerSec,
        completeFraction = SWIPE_SURFACE_COMPLETE_FRACTION,
        flickVelocityPxPerSec = SWIPE_SURFACE_FLICK_DP_PER_SEC * density,
        cancelFraction = SWIPE_SURFACE_CANCEL_FRACTION,
    )
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
