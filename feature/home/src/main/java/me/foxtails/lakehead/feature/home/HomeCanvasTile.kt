package me.foxtails.lakehead.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import me.foxtails.lakehead.core.designsystem.AppSymbol
import me.foxtails.lakehead.core.designsystem.AppTile
import me.foxtails.lakehead.core.designsystem.AppTileState
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalSpan

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
 * - Long-press on a launchable tile calls [onEnterEdit] to enter transient edit mode
 *   from TileBody only (AppLaunchButton never exposes long-click); unavailable tiles
 *   expose no long-click action. Long-press entry is pre-edit only: when
 *   [isEditActive] is true [onLongClick] is null so the in-edit press-hold-drag
 *   owns the gesture without child competition.
 * - While [isEditActive] is true every tile is non-interactive
 *   (`isInteractionEnabled = false` on [AppTile], so TileBody
 *   `combinedClickable(enabled = false)` and the symbol `clickable(enabled = false)`
 *   keep minima/mergeDescendants/[Role]/descriptions/border visuals but strip click
 *   and long-click actions) and [onBodyClick]/[onSymbolClick] are no-ops, so taps
 *   during an edit session never launch.
 * - When [isEditActive] and this tile's id matches [activeItemId], the tile renders the
 *   caller-driven [AppTileState.isSelected] border and announces
 *   `stateDescription = "Editing <label>, row R column C"`.
 * - [shortcutPlaceholders] forwards test/mock-only visual placeholder dots to
 *   [AppTile] (default 0 renders none), clamped to
 *   `0..TileShortcutPolicy.maxShortcutSlots(tile.span)` so over-counts render
 *   max instead of crashing; no shortcut inventory, platform wiring or
 *   slot click handling lives here.
 * - [pinnedCount] forwards the launcher-tile pinned shortcut count to [AppTile]
 *   (default 0 → centered symbol anchor), validated `>= 0` and passed through
 *   unclamped: it is never derived from [shortcutPlaceholders] (placeholder dots
 *   are test/mock-only visuals, not pin state) and never clamped to
 *   [TileShortcutPolicy] capacity (capacity bounds the visible strip, not the
 *   anchor-driving pin state). Until the pin model lands there is no persisted
 *   `pinnedIds` source, so callers leave the default 0; swipe/menu/persistence
 *   wiring is deferred.
 */
@Composable
internal fun HomeCanvasTile(
    tile: HomeRenderTile,
    iconBitmap: ImageBitmap?,
    onLaunchApp: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
    isEditActive: Boolean = false,
    activeItemId: ItemId? = null,
    onEnterEdit: (HomeRenderTile) -> Unit = {},
    shortcutPlaceholders: Int = 0,
    pinnedCount: Int = 0,
) {
    val rawLabel = tile.label
    val label = if (rawLabel.isNullOrBlank()) UNAVAILABLE_LABEL else rawLabel
    val isEditing = isEditActive && tile.itemId == activeItemId
    // Bottom-edge capacity: the icon occupies one slot, so clamp forwarded
    // placeholders to 0..max (over-counts render max, never crash).
    // Pin state stays independent: validated pass-through, never derived from
    // placeholders and never clamped to strip capacity.
    val clampedPlaceholders = clampedShortcutPlaceholders(shortcutPlaceholders, tile.span)
    val resolvedPinnedCount = validatedPinnedCount(pinnedCount)
    val tileModifier = when {
        isEditing -> modifier.semantics {
            stateDescription =
                "Editing $label, row ${tile.position.row} column ${tile.position.column}"
        }
        !tile.isLaunchable -> modifier.semantics { stateDescription = UNAVAILABLE_STATE_DESCRIPTION }
        else -> modifier
    }
    AppTile(
        state = AppTileState(
            label = label,
            span = GridSpan(tile.span.columns, tile.span.rows),
            isSelected = isEditing,
        ),
        onBodyClick = {
            // diverge later: Body→notification surface per grid.md §10; both launch today.
            // In-edit taps are inert so drag gestures win the slop race.
            if (!isEditActive && tile.isLaunchable) {
                onLaunchApp(tile.appIdentity)
            }
        },
        onSymbolClick = {
            if (!isEditActive && tile.isLaunchable) {
                onLaunchApp(tile.appIdentity)
            }
        },
        modifier = tileModifier,
        icon = if (iconBitmap != null) {
            {
                AppSymbol(
                    bitmap = iconBitmap,
                    modifier = Modifier.testTag(HOME_TILE_ICON_TAG),
                )
            }
        } else {
            null
        },
        contentDescription = null,
        onLongClick = if (tile.isLaunchable && !isEditActive) {
            { onEnterEdit(tile) }
        } else {
            null
        },
        shortcutPlaceholders = clampedPlaceholders,
        pinnedCount = resolvedPinnedCount,
        isInteractionEnabled = !isEditActive,
    )
}

/**
 * Validates the caller-supplied pinned shortcut count for [HomeCanvasTile].
 *
 * Pass-through: returns [pinnedCount] unchanged so `APP_WITH_PINNED` (`> 0`)
 * reaches [AppTile] exactly as supplied. Never derived from placeholder dots
 * and never clamped to [TileShortcutPolicy] capacity — capacity bounds the
 * visible strip, not the anchor-driving pin state (a 1×1 tile keeps its pin
 * state even though its strip capacity is 0).
 *
 * Until the pin model (persisted `pinnedIds`) lands there is no production
 * source; callers leave the default 0.
 *
 * @throws IllegalArgumentException when [pinnedCount] is negative.
 */
internal fun validatedPinnedCount(pinnedCount: Int): Int {
    require(pinnedCount >= 0) { "pinnedCount must be >= 0" }
    return pinnedCount
}

/**
 * Clamps requested visual placeholder dots into `0..max` strip capacity for
 * [span] via [TileShortcutPolicy]; over-counts render max instead of crashing.
 * Independent of [validatedPinnedCount]: placeholders are test/mock-only
 * visuals, never pin state.
 */
internal fun clampedShortcutPlaceholders(requested: Int, span: LogicalSpan): Int =
    requested.coerceIn(0, TileShortcutPolicy.maxShortcutSlots(span))

/** Label fallback when a tile has no joined metadata label. */
internal const val UNAVAILABLE_LABEL: String = "Unavailable app"

/** State description exposed on non-launchable tiles. */
internal const val UNAVAILABLE_STATE_DESCRIPTION: String = "Unavailable"

/** Test tag on the directly rendered pre-decoded icon slot. */
internal const val HOME_TILE_ICON_TAG: String = "home-tile-icon"
