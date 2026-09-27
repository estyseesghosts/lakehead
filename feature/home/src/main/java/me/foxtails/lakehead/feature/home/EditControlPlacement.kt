package me.foxtails.lakehead.feature.home

import androidx.compose.ui.unit.IntOffset
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame

/**
 * A5 unpin (`×`) affordance placement for the selected/active edit tile.
 *
 * Small tiles keep the corner control: `1×1`, `1×2` and `2×1` pin the remove
 * affordance top-right (center-pinned on the tile's top-right corner via
 * [cornerControlOffset]). Any larger span — including `2×2`, `3×1` and `1×3` —
 * centers the affordance in the tile footprint so it never collides with the
 * bottom-right resize handle on a tall tile or dangles off a narrow one.
 *
 * Pure math, framework-free: no composables, no Android types, no placement
 * legality (spans are already validated by `core:grid`). The caller
 * ([HomeCanvasGrid]) owns positioning from the live preview frame; the
 * [RemoveButton][me.foxtails.lakehead.core.designsystem.RemoveButton]
 * composable itself stays placement-free.
 */
enum class RemoveControlPlacement { TOP_RIGHT, CENTER }

/**
 * Selects the remove-affordance placement for a tile span.
 *
 * Returns [RemoveControlPlacement.TOP_RIGHT] only for `1×1`, `1×2` and `2×1`;
 * every other positive span returns [RemoveControlPlacement.CENTER].
 */
fun removeControlPlacement(columns: Int, rows: Int): RemoveControlPlacement {
    require(columns > 0) { "columns must be positive" }
    require(rows > 0) { "rows must be positive" }
    return if ((columns == 1 || rows == 1) && columns <= 2 && rows <= 2) {
        RemoveControlPlacement.TOP_RIGHT
    } else {
        RemoveControlPlacement.CENTER
    }
}

/**
 * Positions the remove affordance for a signed renderer frame.
 *
 * [RemoveControlPlacement.TOP_RIGHT] delegates to [cornerControlOffset] (control
 * center pinned on the tile's top-right corner, may extend outside the tile);
 * [RemoveControlPlacement.CENTER] centers the control in the tile footprint
 * (`left + (width - control) / 2`, `top + (height - control) / 2`). Pure math,
 * framework-free.
 */
fun removeControlOffset(
    tileBounds: SignedPixelFrame,
    controlSizePx: Int,
    placement: RemoveControlPlacement,
): IntOffset {
    require(controlSizePx > 0) { "controlSizePx must be positive" }
    return when (placement) {
        RemoveControlPlacement.TOP_RIGHT ->
            cornerControlOffset(tileBounds, controlSizePx, ControlCorner.TOP_RIGHT)
        RemoveControlPlacement.CENTER -> IntOffset(
            tileBounds.left + (tileBounds.widthPx - controlSizePx) / 2,
            tileBounds.top + (tileBounds.heightPx - controlSizePx) / 2,
        )
    }
}

/**
 * Non-signed overload of [removeControlOffset] for absolute [PixelRect] frames.
 *
 * Same rule: top-right delegates to [cornerControlOffset], centered mid-aligns
 * the control in the tile footprint. Pure math, framework-free.
 */
fun removeControlOffset(
    tileBounds: PixelRect,
    controlSizePx: Int,
    placement: RemoveControlPlacement,
): IntOffset {
    require(controlSizePx > 0) { "controlSizePx must be positive" }
    return when (placement) {
        RemoveControlPlacement.TOP_RIGHT ->
            cornerControlOffset(tileBounds, controlSizePx, ControlCorner.TOP_RIGHT)
        RemoveControlPlacement.CENTER -> IntOffset(
            tileBounds.left + (tileBounds.width - controlSizePx) / 2,
            tileBounds.top + (tileBounds.height - controlSizePx) / 2,
        )
    }
}
