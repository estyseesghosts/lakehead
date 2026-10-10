package me.foxtails.lakehead

import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.feature.home.HomeRenderTile

/**
 * Folder UI state and verbs handed to [LauncherShell]; the [FolderCoordinator] owns the state
 * and the shell only renders it. A non-null [openMembers] opens the folder overlay, a non-null
 * [chooserFolders] opens the "add to folder" chooser and [removePromptOpen] opens the removal
 * confirmation. While any is open the pager does not swipe.
 */
internal data class FolderShell(
    val openMembers: List<HomeRenderTile>? = null,
    val chooserFolders: List<HomeRenderTile>? = null,
    val removePromptOpen: Boolean = false,
    val onOpen: (ItemId) -> Unit = {},
    val onClose: () -> Unit = {},
    val onLaunchMember: (AppIdentity) -> Unit = {},
    val onMemberToHome: (ItemId) -> Unit = {},
    val onMemberRemove: (ItemId) -> Unit = {},
    val onMemberMove: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    val onMemberResize: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    // Shortcut data for the open folder's members, exactly as Home gets it.
    val pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
    val surfaceEntries: List<ShortcutSurfaceEntry> = emptyList(),
    val surfaceEntriesTileId: ItemId? = null,
    val onSelectedTileChange: (ItemId?) -> Unit = {},
    val onSurfaceExecute: (ItemId, String) -> Unit = { _, _ -> },
    val onSurfaceTogglePin: (ItemId, String) -> Unit = { _, _ -> },
    val onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
    val onNewFolder: (ItemId) -> Unit = {},
    val onRequestAddToFolder: (ItemId) -> Unit = {},
    val onDropOnFolder: (ItemId, ItemId) -> Unit = { _, _ -> },
    val onPickFolder: (ItemId) -> Unit = {},
    val onChooserDismiss: () -> Unit = {},
    val onRemoveConfirm: () -> Unit = {},
    val onRemoveDismiss: () -> Unit = {},
) {
    val holdsPager: Boolean get() = openMembers != null || chooserFolders != null || removePromptOpen

    companion object {
        val None = FolderShell()
    }
}
