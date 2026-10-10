package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Folder actions offered for the selected Home tile while editing. */
internal enum class TileEditAction(val label: String, val tag: String) {
    NEW_FOLDER("New folder", TILE_ACTION_NEW_FOLDER_TAG),
    ADD_TO_FOLDER("Add to folder", TILE_ACTION_ADD_TO_FOLDER_TAG),
}

/**
 * Actions for the selected [tile]. App and widget tiles can start a folder; they can also join an
 * existing one when [hasFolders]. A folder tile offers none here: moving members out happens in
 * the open folder and removing it is the tile's remove control.
 */
internal fun tileEditActionsFor(tile: HomeRenderTile, hasFolders: Boolean): List<TileEditAction> = when {
    tile.isFolder -> emptyList()
    hasFolders -> listOf(TileEditAction.NEW_FOLDER, TileEditAction.ADD_TO_FOLDER)
    else -> listOf(TileEditAction.NEW_FOLDER)
}

/** Floating row of text actions for the selected tile, above the nav pill. */
@Composable
internal fun TileActionBar(
    actions: List<TileEditAction>,
    onAction: (TileEditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .background(scheme.surfaceContainerHigh)
            .testTag(TILE_ACTION_BAR_TAG),
    ) {
        actions.forEach { action ->
            BasicText(
                text = action.label,
                style = TextStyle(color = scheme.onSurface, fontSize = 15.sp),
                modifier = Modifier
                    .testTag(action.tag)
                    .clickable(role = Role.Button) { onAction(action) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

const val TILE_ACTION_BAR_TAG: String = "tile-action-bar"
const val TILE_ACTION_NEW_FOLDER_TAG: String = "tile-action-new-folder"
const val TILE_ACTION_ADD_TO_FOLDER_TAG: String = "tile-action-add-to-folder"
