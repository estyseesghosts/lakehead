package me.foxtails.lakehead.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import me.foxtails.lakehead.core.model.LogicalPosition
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
import me.foxtails.lakehead.core.designsystem.AppSymbol
import me.foxtails.lakehead.core.designsystem.AppTile
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
import me.foxtails.lakehead.core.designsystem.AppTileState
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.TileNotifications
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId

/**
 * Maps one [HomeRenderTile] to the reusable [AppTile] primitive.
 *
 * Presentation only: the caller supplies the already-decoded [iconBitmap] (or null when no
 * snapshot was supplied). This composable performs no decode, catalogue/platform lookup,
 * database access, or gesture handling beyond click and move-entry long-press.
 *
 * - Label falls back to `"Unavailable app"` when the tile has no joined metadata label.
 * - Span comes directly from the persisted tile; legality stays owned by `core:grid`.
 * - The icon slot renders the supplied snapshot directly with
 *   `Image(bitmap = ..., contentDescription = null)` when present, otherwise label-only.
 * - Body and symbol clicks both dispatch the tile's exact [AppIdentity] only when
 *   [HomeRenderTile.isLaunchable]; unavailable tiles expose no-op clicks and
 *   `stateDescription = "Unavailable"`.
 * - Long-press on a launchable tile calls [onEnterWiggle] to enter/select
 *   transient W1 wiggle-editing from every tile region (TileBody, app
 *   launch/strip glyph and each shortcut glyph forward the same shared
 *   callback via their own `combinedClickable`; see `AppTile`). Unavailable
 *   tiles expose no long-press action. The pre-W1
 *   move-session entry is handled by the grid when a selected tile is dragged.
 * - While [HomeEditState.isDragging] is true every tile is non-interactive
 *   (`isInteractionEnabled = false` on [AppTile]: TileBody keeps its
 *   `combinedClickable(enabled = false)` so its click reads disabled, the
 *   glyph regions install no detector, and the shared long-press is nulled)
 *   and [onBodyClick]/[onSymbolClick]/strip taps are no-ops, so taps during
 *   a drag never launch and never retarget. The drag wins over the held
 *   selection — interaction stays disabled, so neither tap-select nor
 *   long-press retarget can fire mid-drag. Edit mode keeps interaction
 *   enabled only when no drag runs, so taps select instead of launching.
 * - W1 recess/select (render-only, no occupancy change): [editState] carries
 *   the transient edit mode and selection owned by the caller (never modified
 *   here). Outside edit mode, body/symbol
 *   clicks launch when [HomeRenderTile.isLaunchable] (unchanged) and launchable long-press
 *   calls [onEnterWiggle]. In edit mode, body/symbol clicks call
 *   [onSelect] with the tapped tile's id and never launch, and launchable
 *   long-press retargets via [onEnterWiggle] (selection transfer, no launch).
 *   `isSelected = selectedTileId == itemId` drives the [AppTileState.isSelected]
 *   border; the tile renders at full scale when selected (or outside edit mode) and at
 *   [WIGGLE_RECESSED_SCALE] when recessed, with selected tiles at `zIndex = 1`.
 *   Scale transitions share one [animateFloatAsState] interval so selection swaps
 *   animate simultaneously with no `Normal` round-trip flash.
 * - While this tile is the dragged tile, the tile renders the
 *   caller-driven [AppTileState.isSelected] border and announces
 *   `stateDescription = "Editing <label>, row R column C"`. The same treatment
 *   applies when W1-selected; both keep the id-keyed contract (never pos/index).
 * - [pinnedShortcuts] carries the tile's joined pinned shortcuts in pin order
 *   (as produced by [TilePinnedStrip.resolve] from the persisted binding and
 *   live shortcut metadata; default empty renders no strip).  The tile derives
 *   the render window via [TilePinnedStrip.arrange] on its live span — visible
 *   glyphs plus a defined overflow — so resize/move previews recompute the
 *   strip every frame without touching persistence.  Count-only placeholder
 *   fixtures are retired: there is no count param, only this real list.
 * - [onShortcutClick] reports a strip glyph tap with the shortcut's opaque id;
 *   execution stays with the caller.  Strip glyphs are disabled while
 *   a drag (the tile drags as one object, no select retarget) and taps
 *   select while effective W1 wiggle-editing instead of launching, so shortcut
 *   invocation never interferes with tile editing.
 */
@Composable
internal fun HomeCanvasTile(
    tile: HomeRenderTile,
    iconBitmap: ImageBitmap?,
    onLaunchApp: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
    editState: HomeEditState = HomeEditState.Normal,
    pinnedShortcuts: List<PinnedTileShortcut> = emptyList(),
    notifications: TileNotifications? = null,
    // Album slideshow photo for this tile (resolved by `:app`); null renders
    // the normal icon. The tile shows it only on spans of at least 2x2.
    albumPhoto: ImageBitmap? = null,
    onShortcutClick: (String) -> Unit = {},
    onSelect: (ItemId) -> Unit = {},
    onEnterWiggle: (HomeRenderTile) -> Unit = {},
    lift: (() -> Offset)? = null,
    // Replaces the app body (used by widget tiles); the wrapper, selection,
    // recess, lift and glide stay shared.
    content: (@Composable (isEditing: Boolean) -> Unit)? = null,
) {
    val rawLabel = tile.label
    val label = if (rawLabel.isNullOrBlank()) UNAVAILABLE_LABEL else rawLabel
    // A running drag owns every gesture: while [isEditActive] every tile is
    // inert (no select retarget, no launch, no shortcut invoke, no long-press
    // retarget) even though the selection is still held.
    val isEditActive = editState.isDragging
    val isEditing = isEditActive && tile.itemId == editState.drag.itemId
    val isInteractionEditing = editState.isEditing
    val isEffectiveEditing = isInteractionEditing && !isEditActive
    val isSelected = editState.selectedTileId == tile.itemId
    // W1 recess scale target: full scale in Normal and when selected, recessed
    // otherwise. Single animateFloatAsState interval so A→B selection swaps move
    // simultaneously (A 1.00→0.92 / B 0.92→1.00) with no Normal round-trip flash.
    // Render-only: logical cells/spans are untouched.
    val scaleTarget = when {
        !isInteractionEditing -> 1f
        isSelected -> 1f
        else -> WIGGLE_RECESSED_SCALE
    }
    val animatedScale by animateFloatAsState(targetValue = scaleTarget, label = "wiggleRecessScale")
    // Pinned strip arrangement on the live span: visible glyphs plus defined
    // overflow, recomputed from the same resolved list on every span change
    // (including the resize preview, whose span arrives via the copied tile).
    // Edit-selected tiles yield the +N node to the floating edit controls;
    // the count stays announced through TileBody semantics. The tile itself
    // additionally fits the strip to its measured bounds (grid-unit pitch,
    // control-inset reserves) via isEditSelected below, withholding glyphs
    // rather than overlapping; the binding retains every pin.
    val editSelected = isSelected || isEditing
    val stripArrangement = TilePinnedStrip.arrange(
        span = tile.span,
        resolved = pinnedShortcuts,
        isEditSelected = editSelected,
    )
    val tileModifier = when {
        isSelected || isEditing -> modifier.semantics {
            stateDescription =
                "Editing $label, row ${tile.position.row} column ${tile.position.column}"
        }
        !tile.isLaunchable -> modifier.semantics { stateDescription = UNAVAILABLE_STATE_DESCRIPTION }
        else -> modifier
    }
    // W1 wrapper: recess scale + selected-above z-order. Wallpaper < non-selected
    // < selected; handles/overlays stay siblings above (owned by the grid).
    // A lifted tile follows the finger: the translation is read inside the layer
    // block, so pointer events invalidate only this layer, never the grid.
    val liftTarget = if (lift != null && isEditing) 1f else 0f
    val liftProgress by animateFloatAsState(targetValue = liftTarget, label = "moveLift")
    val liftShadowPx = with(LocalDensity.current) { LIFTED_ELEVATION_DP.dp.toPx() }
    // Placement glide: a logical-cell change (drop, neighbour shuffle, rejected
    // reload) glides from the old visual spot to the new one instead of jumping.
    // A released lift hands its last translation over to the same glide, so the
    // dropped tile travels from the finger to its cell.
    val glide = remember { PlacementGlide() }
    val tracker = remember { GlideTracker() }
    val liftActive = lift != null && isEditing
    SideEffect {
        if (tracker.wasLifted && !liftActive) glide.retarget(tracker.lastLift)
        tracker.wasLifted = liftActive
    }
    LaunchedEffect(glide.generation) {
        if (glide.generation > 0) {
            animate(0f, 1f, animationSpec = tween(PLACEMENT_GLIDE_MS)) { value, _ ->
                glide.progress = value
            }
        }
    }
    val currentCell by rememberUpdatedState(tile.position)
    Box(
        modifier = Modifier
            .onPlaced { coordinates ->
                val placedAt = coordinates.positionInParent()
                val previous = tracker.lastPlaced
                if (previous != null && tracker.lastCell != currentCell) {
                    glide.retarget(previous - placedAt)
                }
                tracker.lastPlaced = placedAt
                tracker.lastCell = currentCell
            }
            .graphicsLayer {
                val raised = 1f + (LIFTED_SCALE - 1f) * liftProgress
                scaleX = animatedScale * raised
                scaleY = animatedScale * raised
                var shift = glide.offset
                if (lift != null && isEditing) {
                    val offset = lift()
                    tracker.lastLift = offset
                    shift += offset
                }
                translationX = shift.x
                translationY = shift.y
                if (liftProgress > 0f) {
                    shadowElevation = liftShadowPx * liftProgress
                    shape = AppTileDefaults.TileShape
                    clip = false
                }
            }
            .zIndex(if (isEditing || liftProgress > 0f) 2f else if (isSelected) 1f else 0f),
    ) {
    if (content != null) {
        Box(modifier = tileModifier) { content(isInteractionEditing) }
    } else AppTile(
        state = AppTileState(
            label = label,
            span = GridSpan(tile.span.columns, tile.span.rows),
            isSelected = isSelected || isEditing,
        ),
        onBodyClick = {
            // diverge later: Body→notification surface per grid.md §10; both launch today.
            // In edit mode, taps select and never launch. While a drag runs
            // every tap stays inert so the drag wins the slop race.
            if (isEffectiveEditing) {
                onSelect(tile.itemId)
            } else if (!isEditActive && tile.isLaunchable) {
                tile.appIdentity?.let(onLaunchApp)
            }
        },
        onSymbolClick = {
            if (isEffectiveEditing) {
                onSelect(tile.itemId)
            } else if (!isEditActive && tile.isLaunchable) {
                tile.appIdentity?.let(onLaunchApp)
            }
        },
        modifier = tileModifier,
        icon = if (iconBitmap != null) {
            { extent ->
                AppSymbol(
                    bitmap = iconBitmap,
                    modifier = Modifier.testTag(HOME_TILE_ICON_TAG),
                    extent = extent,
                )
            }
        } else {
            null
        },
        contentDescription = null,
        // W1 retarget: launchable long-press enters/selects edit mode whether or
        // not it is already on (the state replaces selection directly, no Normal
        // round-trip). Drag entry is handled by the grid's selected-tile
        // drag bridge. Every region of a launchable tile forwards this shared
        // callback (see AppTile). Unavailable tiles intentionally expose no
        // long-click action.
        // While a drag runs the tile exposes no long-press (null strips the
        // action) so a mid-drag press cannot retarget.
        onLongClick = if (isEditActive || !tile.isLaunchable) {
            null
        } else {
            { onEnterWiggle(tile) }
        },
        pinnedShortcuts = stripArrangement.visible,
        overflowCount = stripArrangement.overflowCount,
        showOverflowIndicator = stripArrangement.showOverflowIndicator,
        onShortcutClick = { shortcutId ->
            // Strip taps execute while interactive; in edit mode taps select
            // the tile instead (never launch a shortcut mid-edit), matching
            // the body/symbol tap contract above. While a drag runs taps stay
            // inert even though the selection is held.
            if (isEffectiveEditing) {
                onSelect(tile.itemId)
            } else if (!isEditActive && tile.isLaunchable) {
                onShortcutClick(shortcutId)
            }
        },
        // Edit mode keeps interaction enabled so taps select. A running drag
        // always wins — interaction stays disabled even though the selection is
        // held, so no child region exposes click/long-click actions mid-drag.
        isInteractionEnabled = !isEditActive,
        isEditSelected = editSelected,
        notificationCount = notifications?.count ?: 0,
        notificationPreviews = notifications?.previews.orEmpty(),
        albumPhoto = albumPhoto,
    )
    }
}

/** Duration of the glide to a new logical cell, in milliseconds. */
internal const val PLACEMENT_GLIDE_MS: Int = 220

/** Non-state bookkeeping for one tile's glide; read and written outside composition. */
private class GlideTracker {
    var wasLifted: Boolean = false
    var lastLift: Offset = Offset.Zero
    var lastPlaced: Offset? = null
    var lastCell: LogicalPosition? = null
}

/** Render-only scale of the tile lifted by a move. */
internal const val LIFTED_SCALE: Float = 1.06f

/** Shadow elevation of the tile lifted by a move, in dp. */
internal const val LIFTED_ELEVATION_DP: Int = 8

/** Label fallback when a tile has no joined metadata label. */
internal const val UNAVAILABLE_LABEL: String = "Unavailable app"

/**
 * W1 recess scale for non-selected tiles while wiggle-editing.
 *
 * Render-only single tuning value: logical cells, spans and occupancy are
 * untouched; the selected tile stays at full scale.
 */
internal const val WIGGLE_RECESSED_SCALE: Float = 0.92f

/** State description exposed on non-launchable tiles. */
internal const val UNAVAILABLE_STATE_DESCRIPTION: String = "Unavailable"

/** Test tag on the directly rendered pre-decoded icon slot. */
internal const val HOME_TILE_ICON_TAG: String = "home-tile-icon"
