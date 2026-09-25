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
 *   expose no long-click action.
 * - When [isEditActive] and this tile's id matches [activeItemId], the tile renders the
 *   caller-driven [AppTileState.isSelected] border and announces
 *   `stateDescription = "Editing <label>, row R column C"`.
 * - [shortcutPlaceholders] forwards test/mock-only visual placeholder dots to
 *   [AppTile] (default 0 renders none); no shortcut inventory, platform wiring or
 *   slot click handling lives here.
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
) {
    val rawLabel = tile.label
    val label = if (rawLabel.isNullOrBlank()) UNAVAILABLE_LABEL else rawLabel
    val isEditing = isEditActive && tile.itemId == activeItemId
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
            if (tile.isLaunchable) {
                onLaunchApp(tile.appIdentity)
            }
        },
        onSymbolClick = {
            if (tile.isLaunchable) {
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
        onLongClick = if (tile.isLaunchable) {
            { onEnterEdit(tile) }
        } else {
            null
        },
        shortcutPlaceholders = shortcutPlaceholders,
    )
}

/** Label fallback when a tile has no joined metadata label. */
internal const val UNAVAILABLE_LABEL: String = "Unavailable app"

/** State description exposed on non-launchable tiles. */
internal const val UNAVAILABLE_STATE_DESCRIPTION: String = "Unavailable"

/** Test tag on the directly rendered pre-decoded icon slot. */
internal const val HOME_TILE_ICON_TAG: String = "home-tile-icon"
