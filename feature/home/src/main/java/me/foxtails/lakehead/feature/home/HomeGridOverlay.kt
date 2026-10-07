package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
import me.foxtails.lakehead.core.designsystem.RemoveButton
import me.foxtails.lakehead.core.designsystem.ResizeHandle
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Overlay layer for the home canvas grid: move/resize candidate outlines,
 * resize handle, remove button and edit-mode shortcut menu.
 *
 * Renders from state and makes NO legality decisions or edit transitions. All
 * mutation is performed by the caller through the supplied callbacks. Read-only
 * inputs reflect the current [HomeEditState] snapshot.
 */
@Composable
internal fun HomeGridOverlay(
    moveDrag: DragState.Move?,
    resizeDrag: DragState.Resize?,
    editState: HomeEditState,
    tiles: List<HomeRenderTile>,
    gridMetrics: HomeGridMetrics,
    reducedMotion: Boolean,
    dragging: Boolean,
    surfaceEntries: List<ShortcutSurfaceEntry>,
    surfaceEntriesTileId: ItemId?,
    availableWidthPx: Int,
    availableHeightPx: Int,
    scrollYPx: Int,
    onToggleResizeArmed: (ItemId) -> Unit,
    onRemove: (ItemId) -> Unit,
    onSurfaceExecute: (ItemId, String) -> Unit,
    onSurfaceTogglePin: (ItemId, String) -> Unit,
) {
    if (moveDrag != null) {
        MoveCandidateOverlay(move = moveDrag, metrics = gridMetrics)
    }
    if (resizeDrag != null) {
        // W3 preview overlay: frame at the held last-valid span (blocked
        // proposals keep the frame; validity flips the valid/error
        // color). Animated and reduced-motion branches share
        // [resizePreviewSpan], so geometry is identical.
        val previewSpan = resizePreviewSpan(
            resize = resizeDrag,
            reducedMotion = reducedMotion,
        )
        ResizeCandidateOverlay(
            originPosition = resizeDrag.position,
            candidateSpan = LogicalSpan(previewSpan.columns, previewSpan.rows),
            metrics = gridMetrics,
            isValid = resizeDrag.validity == CandidateValidity.VALID,
        )
    }
    // Resize handle overlay: the centered 48dp visual/touch Box is positioned
    // over the selected/active tile's bottom-right corner. It is separate from
    // the parent's inward-only 48dp routing hit rect: the parent receives only
    // points in the tile's [right - touch, right] × [bottom - touch, bottom]
    // corner, while this Box remains center-pinned and may extend outward.
    // It is INSIDE the scrolling content so it shares the tiles/overlays
    // coordinate space and tracks scroll. W3 single-BR-only: the touch
    // target is exactly RESIZE_TOUCH_DP inward over the corner
    // ([right - touch, right] x [bottom - touch, bottom]), never
    // outward, so edge tiles never clip.
    val controlItemId = editState.selectedTileId
    if (controlItemId != null) {
        val activeTile = tiles.find { it.itemId == controlItemId }
        if (activeTile != null) {
            val density = LocalDensity.current
            // Handle tracks the live last-valid preview span (not the
            // persisted span) so it stays on the growing/shrinking
            // corner during a resize drag.
            // Frame from `gridMetrics`: its top is final Box content coordinates.
            val previewSpan = editState.renderedSpan(controlItemId, activeTile.span)
            val tileBounds = gridMetrics.pixelFrame(
                moveDrag?.originalPosition ?: activeTile.position,
                previewSpan,
            )
            val touchPx = with(density) { RESIZE_TOUCH_DP.dp.roundToPx() }
            val handleOffset = cornerControlOffset(
                tileBounds = tileBounds,
                controlSizePx = touchPx,
                corner = ControlCorner.BOTTOM_RIGHT,
            )
            // Resize handle: tap-only arm/disarm for the selected tile.
            // The tap consumes the arrow-tap so the tile below never
            // transfer-clears it (below slop toggles armed with no write
            // and no enterSelectedDrag); past slop the tap cancels and
            // the parent Box's unified drag handler owns routing. No drag
            // detector is installed here — a second one would lose the
            // slop race and leave the handle unreachable.
            val handleLabel = if (activeTile.label.isNullOrBlank()) {
                UNAVAILABLE_LABEL
            } else {
                activeTile.label
            }
            Box(
                modifier = Modifier
                    .offset { handleOffset }
                    .size(RESIZE_TOUCH_DP.dp),
            ) {
                ResizeHandle(
                    label = handleLabel,
                    armed = editState.isResizeArmedFor(controlItemId),
                    onToggleArmed = {
                        onToggleResizeArmed(controlItemId)
                    },
                )
            }
            // Remove affordance: sibling overlay over the active tile in
            // both MOVE and RESIZE modes. Tracks the live candidate span
            // (same footprint as the handle) so it stays attached during
            // a resize drag. A5 placement: 1x1/1x2/2x1 pin top-right,
            // any larger preview span centers the control in the tile
            // footprint; the flip is driven by the live preview span so
            // a resize across the boundary re-anchors mid-drag. Tap
            // reports onRemoveTile only; the grid performs no
            // persistence delete and issues no move/resize.
            val removeLabel = handleLabel
            val buttonPx = with(density) { CORNER_CONTROL_TOUCH_SIZE.dp.roundToPx() }
            val removePlacement = removeControlPlacement(
                columns = previewSpan.columns,
                rows = previewSpan.rows,
            )
            val removeAbsolute = removeControlOffset(
                tileBounds = tileBounds,
                controlSizePx = buttonPx,
                placement = removePlacement,
            )
            // Signed frame already carries the row origin + rest offset, so
            // the remove anchor is final Box content coordinates.
            val removeTop = removeAbsolute.y
            Box(
                modifier = Modifier
                    .offset { IntOffset(removeAbsolute.x, removeTop) }
                    .size(CORNER_CONTROL_TOUCH_SIZE.dp),
            ) {
                RemoveButton(
                    label = removeLabel,
                    onRemove = {
                        onRemove(controlItemId)
                    },
                )
            }
        }
    }
    // B4 edit-mode shortcut menu: sibling overlay above the W1-selected
    // tile, inside the scrolling content so it shares the tiles'
    // coordinate space and tracks scroll. Follows
    // (interaction as? Editing)?.selectedId: long-press shows,
    // tap-other transfers with no Normal round-trip, and every
    // dismiss path (empty-tap/Back/navigate to Normal, move/resize
    // start to Active, removal/uninstall via a null selection or a
    // tile-lookup miss, zero shortcuts) uncomposes the menu with
    // zero activation. Only a row tap reports onSurfaceExecute/
    // onSurfaceTogglePin with the selected ItemId and entry id;
    // the menu never moves, resizes or pages tiles. The visible
    // window is the grid scroll offset plus the app-safe usable
    // height (pill/gesture reserve already excluded upstream).
    val menuSelectedId = editState.selectedTileId
    // B1 owner gate: rows scoped to another tile (stale A rows after
    // an A→B switch) hide honestly until the caller's rows for B
    // arrive; unscoped (null owner) rows keep the B4 behavior.
    if (isEditShortcutMenuVisible(dragging, menuSelectedId, tiles, surfaceEntries) &&
        isSurfaceEntriesForSelection(surfaceEntriesTileId, menuSelectedId)
    ) {
        val menuTile = tiles.find { it.itemId == menuSelectedId }
        if (menuTile != null) {
            EditShortcutMenu(
                tile = menuTile,
                entries = surfaceEntries,
                metrics = gridMetrics,
                availableWidthPx = availableWidthPx,
                visibleTopPx = scrollYPx,
                visibleBottomPx = scrollYPx + availableHeightPx.coerceAtLeast(1),
                onSurfaceExecute = onSurfaceExecute,
                onSurfaceTogglePin = onSurfaceTogglePin,
            )
        }
    }
}

/**
 * Resize preview span for the RESIZE overlay, handle, and tile content.
 *
 * W3 `renderedSpan = preview ?: persisted`: returns the held last-valid span
 * ([DragState.Resize.previewSpan]) for a RESIZE drag, which is the
 * frame both the animated path and the reduced-motion snap path render — the
 * [reducedMotion] flag only selects snap-vs-animate presentation, never a
 * different span, so geometry is identical either way. Pure math,
 * framework-free. Legality stays owned by `core:grid`.
 */
internal fun resizePreviewSpan(
    resize: DragState.Resize,
    reducedMotion: Boolean = false,
): GridSpan {
    // Snap branch (reduced-motion) and animated branch share one source: the
    // held last-valid preview. No separate span computation per branch.
    val preview = resize.previewSpan
    return if (reducedMotion) {
        // Snap: render the validated span immediately, no rapid animation.
        GridSpan(preview.columns, preview.rows)
    } else {
        // Animated path target: the same validated span.
        GridSpan(preview.columns, preview.rows)
    }
}