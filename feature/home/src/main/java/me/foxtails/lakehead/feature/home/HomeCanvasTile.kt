package me.foxtails.lakehead.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
import me.foxtails.lakehead.core.designsystem.AppSymbol
import me.foxtails.lakehead.core.designsystem.AppTile
import me.foxtails.lakehead.core.designsystem.AppTileState
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
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
 *   transient W1 wiggle-editing from TileBody only (AppLaunchButton never exposes
 *   long-click); unavailable tiles expose no long-click action. The pre-W1
 *   move-session entry is handled by the grid when a selected tile is dragged.
 * - While [isEditActive] is true every tile is non-interactive
 *   (`isInteractionEnabled = false` on [AppTile], so TileBody
 *   `combinedClickable(enabled = false)` and the symbol `clickable(enabled = false)`
 *   keep minima/mergeDescendants/[Role]/descriptions/border visuals but strip click
 *   and long-click actions) and [onBodyClick]/[onSymbolClick] are no-ops, so taps
 *   during an edit session never launch. W1 wiggle-editing overrides this: when
 *   [interactionState] is [HomeInteractionController.Editing], interaction stays
 *   enabled so taps select instead of launching.
 * - W1 recess/select (render-only, no occupancy change): [interactionState] carries
 *   the transient `Normal | Editing(selectedId?)` state owned by the caller
 *   ([HomeInteractionController], never modified here). In `Normal`, body/symbol
 *   clicks launch when [HomeRenderTile.isLaunchable] (unchanged) and long-press on
 *   a launchable tile calls [onEnterWiggle]. In `Editing`, body/symbol clicks call
 *   [onSelect] with the tapped tile's id and never launch, and long-press on a
 *   launchable tile retargets via [onEnterWiggle] (selection transfer, no launch).
 *   `isSelected = selectedId == itemId` drives the [AppTileState.isSelected]
 *   border; the tile renders at full scale when selected (or in `Normal`) and at
 *   [WIGGLE_RECESSED_SCALE] when recessed, with selected tiles at `zIndex = 1`.
 *   Scale transitions share one [animateFloatAsState] interval so selection swaps
 *   animate simultaneously with no `Normal` round-trip flash.
 * - When [isEditActive] and this tile's id matches [activeItemId], the tile renders the
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
 *   [isEditActive] (the tile drags as one object) and taps select while
 *   W1 wiggle-editing instead of launching, so shortcut invocation never
 *   interferes with tile editing.
 */
@Composable
internal fun HomeCanvasTile(
    tile: HomeRenderTile,
    iconBitmap: ImageBitmap?,
    onLaunchApp: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
    isEditActive: Boolean = false,
    activeItemId: ItemId? = null,
    pinnedShortcuts: List<PinnedTileShortcut> = emptyList(),
    onShortcutClick: (String) -> Unit = {},
    interactionState: HomeInteractionController = HomeInteractionController.Normal,
    onSelect: (ItemId) -> Unit = {},
    onEnterWiggle: (HomeRenderTile) -> Unit = {},
) {
    val rawLabel = tile.label
    val label = if (rawLabel.isNullOrBlank()) UNAVAILABLE_LABEL else rawLabel
    val isEditing = isEditActive && tile.itemId == activeItemId
    // W1 transient selection, id-keyed (never position/index). Null when Normal or
    // Editing(null): no tile selected.
    val interactionEditing = interactionState as? HomeInteractionController.Editing
    val isInteractionEditing = interactionEditing != null
    val isSelected = interactionEditing?.selectedId == tile.itemId
    // W1 recess scale target: full scale in Normal and when selected, recessed
    // otherwise. Single animateFloatAsState interval so A→B selection swaps move
    // simultaneously (A 1.00→0.92 / B 0.92→1.00) with no Normal round-trip flash.
    // Render-only: logical cells/spans are untouched.
    val scaleTarget = when {
        interactionState is HomeInteractionController.Normal -> 1f
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
    Box(
        modifier = Modifier
            .graphicsLayer(
                scaleX = animatedScale,
                scaleY = animatedScale,
            )
            .zIndex(if (isSelected) 1f else 0f),
    ) {
    AppTile(
        state = AppTileState(
            label = label,
            span = GridSpan(tile.span.columns, tile.span.rows),
            isSelected = isSelected || isEditing,
        ),
        onBodyClick = {
            // diverge later: Body→notification surface per grid.md §10; both launch today.
            // In W1 Editing, taps select and never launch. In Normal, in-edit taps
            // of a move session stay inert so drag gestures win the slop race.
            if (isInteractionEditing) {
                onSelect(tile.itemId)
            } else if (!isEditActive && tile.isLaunchable) {
                onLaunchApp(tile.appIdentity)
            }
        },
        onSymbolClick = {
            if (isInteractionEditing) {
                onSelect(tile.itemId)
            } else if (!isEditActive && tile.isLaunchable) {
                onLaunchApp(tile.appIdentity)
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
        // W1 retarget: long-press enters/selects wiggle-editing from both Normal
        // and Editing (controller replaces selection directly, no Normal
        // round-trip). Move-session entry is handled by the grid's selected-tile
        // drag bridge. Unavailable tiles expose none.
        onLongClick = if (tile.isLaunchable) {
            { onEnterWiggle(tile) }
        } else {
            null
        },
        pinnedShortcuts = stripArrangement.visible,
        overflowCount = stripArrangement.overflowCount,
        showOverflowIndicator = stripArrangement.showOverflowIndicator,
        onShortcutClick = { shortcutId ->
            // Strip taps execute while interactive; in W1 Editing taps select
            // the tile instead (never launch a shortcut mid-edit), matching
            // the body/symbol tap contract above.
            if (isInteractionEditing) {
                onSelect(tile.itemId)
            } else if (!isEditActive && tile.isLaunchable) {
                onShortcutClick(shortcutId)
            }
        },
        // W1 Editing keeps interaction enabled so taps select; a move session in
        // Normal keeps tiles non-interactive so the parent drag owns the gesture.
        isInteractionEnabled = if (isInteractionEditing) true else !isEditActive,
        isEditSelected = editSelected,
    )
    }
}

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
