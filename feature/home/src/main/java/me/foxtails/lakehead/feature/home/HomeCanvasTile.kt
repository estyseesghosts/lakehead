package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import me.foxtails.lakehead.core.designsystem.AppTile
import me.foxtails.lakehead.core.designsystem.AppTileState
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity

/**
 * Maps one [HomeRenderTile] to the reusable [AppTile] primitive.
 *
 * Presentation only: the caller supplies the already-decoded [iconBitmap] (or null when no
 * snapshot was supplied). This composable performs no decode, catalogue/platform lookup,
 * database access, or gesture handling beyond a plain click.
 *
 * - Label falls back to `"Unavailable app"` when the tile has no joined metadata label.
 * - Span comes directly from the persisted tile; legality stays owned by `core:grid`.
 * - The icon slot renders the supplied snapshot directly with
 *   `Image(bitmap = ..., contentDescription = null)` when present, otherwise label-only.
 * - Click dispatches the tile's exact [AppIdentity] only when [HomeRenderTile.isLaunchable];
 *   unavailable tiles expose a no-op click and `stateDescription = "Unavailable"`.
 * - [onLongClick] is always null: D4-D6 gestures are explicitly out of scope.
 */
@Composable
internal fun HomeCanvasTile(
    tile: HomeRenderTile,
    iconBitmap: ImageBitmap?,
    onLaunchApp: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rawLabel = tile.label
    val label = if (rawLabel.isNullOrBlank()) UNAVAILABLE_LABEL else rawLabel
    val tileModifier = if (tile.isLaunchable) {
        modifier
    } else {
        modifier.semantics { stateDescription = UNAVAILABLE_STATE_DESCRIPTION }
    }
    AppTile(
        state = AppTileState(
            label = label,
            span = GridSpan(tile.span.columns, tile.span.rows),
        ),
        onClick = {
            if (tile.isLaunchable) {
                onLaunchApp(tile.appIdentity)
            }
        },
        modifier = tileModifier,
        icon = if (iconBitmap != null) {
            {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.testTag(HOME_TILE_ICON_TAG),
                )
            }
        } else {
            null
        },
        contentDescription = null,
        onLongClick = null,
    )
}

/** Label fallback when a tile has no joined metadata label. */
internal const val UNAVAILABLE_LABEL: String = "Unavailable app"

/** State description exposed on non-launchable tiles. */
internal const val UNAVAILABLE_STATE_DESCRIPTION: String = "Unavailable"

/** Test tag on the directly rendered pre-decoded icon slot. */
internal const val HOME_TILE_ICON_TAG: String = "home-tile-icon"
