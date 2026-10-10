package me.foxtails.lakehead.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.foxtails.lakehead.core.designsystem.FolderTile
import me.foxtails.lakehead.core.model.ItemId

/**
 * Body of a closed folder tile on Home: the shared [FolderTile] primitive fed with the
 * pre-decoded symbols of the tile's preview members. A tap opens the folder outside edit mode and
 * selects the tile in edit mode; a long-press enters or retargets edit mode. While a drag runs the
 * tile is inert so the drag wins the slop race, like every other tile.
 */
@Composable
internal fun FolderTileBody(
    tile: HomeRenderTile,
    iconsByIdentity: HomeCanvasIcons,
    isEditing: Boolean,
    isSelected: Boolean,
    isDragging: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = requireNotNull(tile.folderPreview) { "not a folder tile" }
    FolderTile(
        label = tile.label ?: FOLDER_LABEL,
        previews = preview.map { iconsByIdentity[it] },
        onClick = { if (isEditing) onSelect() else onOpen() },
        onLongClick = onLongPress,
        isSelected = isSelected,
        isInteractionEnabled = !isDragging,
        modifier = modifier,
    )
}
