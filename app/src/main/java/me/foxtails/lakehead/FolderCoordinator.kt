package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.data.repository.CreateFolderResult
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.data.repository.Dissolve
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.data.repository.FolderRepository
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.LoadFolderResult
import me.foxtails.lakehead.data.repository.RemoveFolderResult
import me.foxtails.lakehead.data.repository.RemoveMemberResult
import me.foxtails.lakehead.data.repository.TransferResult
import me.foxtails.lakehead.feature.home.HomeAppMetadata
import me.foxtails.lakehead.feature.home.HomeRenderProjection
import me.foxtails.lakehead.feature.home.HomeRenderTile
import java.util.UUID

/**
 * Owns which folder is open and every folder action the UI can request.
 *
 * Repository calls run on [ioDispatcher]; the persisted structure stays in the data layer and
 * this class keeps only transient UI state: the open folder's projected member tiles, the
 * pending "add to folder" source tile, and the pending remove-folder confirmation. Nothing here
 * is persisted, so a restart returns to a closed overlay.
 *
 * Every action that changes Home calls [onHomeChanged] so Home reloads; an action whose
 * auto-dissolve removed the open folder closes the overlay. Widget ids dropped by
 * [removeFolder] are released through [releaseWidget] only after the delete committed.
 */
internal class FolderCoordinator(
    private val homeId: HomeId,
    private val folders: FolderRepository,
    private val placements: HomePlacementRepository,
    private val columns: Int,
    private val metadata: () -> Map<AppIdentity, HomeAppMetadata>,
    private val onHomeChanged: () -> Unit,
    private val releaseWidget: (Int) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val newFolderId: () -> ItemId = { ItemId(UUID.randomUUID().toString()) },
) {
    private val mutableOpenFolder = MutableStateFlow<ItemId?>(null)
    private val mutableMembers = MutableStateFlow<List<HomeRenderTile>>(emptyList())
    private val mutableAddSource = MutableStateFlow<ItemId?>(null)
    private val mutableRemovePrompt = MutableStateFlow<ItemId?>(null)

    /** The folder shown in the overlay, or null when closed. */
    val openFolder: StateFlow<ItemId?> = mutableOpenFolder.asStateFlow()

    /** Member tiles of [openFolder], at their persisted cells and spans. */
    val members: StateFlow<List<HomeRenderTile>> = mutableMembers.asStateFlow()

    /** The Home tile waiting for a target folder from the chooser, or null. */
    val addSource: StateFlow<ItemId?> = mutableAddSource.asStateFlow()

    /** The non-empty folder waiting for remove confirmation, or null. */
    val removePrompt: StateFlow<ItemId?> = mutableRemovePrompt.asStateFlow()

    suspend fun open(folderId: ItemId) {
        val loaded = loadMembers(folderId) ?: return
        mutableMembers.value = loaded
        mutableOpenFolder.value = folderId
    }

    fun close() {
        mutableOpenFolder.value = null
        mutableMembers.value = emptyList()
    }

    /** Re-reads the open folder after Home changed underneath it; closes it if it is gone. */
    suspend fun refreshOpen() {
        val folderId = mutableOpenFolder.value ?: return
        val loaded = loadMembers(folderId)
        if (loaded == null) close() else mutableMembers.value = loaded
    }

    /** Turns [fromItemId] into a one-member folder at its place on Home. */
    suspend fun createFolder(fromItemId: ItemId): Boolean {
        val result = withContext(ioDispatcher) {
            folders.createFolder(homeId, fromItemId, newFolderId(), columns)
        }
        if (result is CreateFolderResult.Created) onHomeChanged()
        return result is CreateFolderResult.Created
    }

    fun requestAddToFolder(itemId: ItemId) {
        mutableAddSource.value = itemId
    }

    fun dismissChooser() {
        mutableAddSource.value = null
    }

    suspend fun pickFolder(folderId: ItemId): Boolean {
        val source = mutableAddSource.value ?: return false
        mutableAddSource.value = null
        return addToFolder(source, folderId)
    }

    /** Adds the Home tile [itemId] to [folderId]; used by the chooser and by dropping a tile on a folder. */
    suspend fun addToFolder(itemId: ItemId, folderId: ItemId): Boolean {
        val result = withContext(ioDispatcher) { folders.moveIntoFolder(itemId, folderId, columns) }
        if (result is TransferResult.Done) onHomeChanged()
        return result is TransferResult.Done
    }

    suspend fun moveToHome(itemId: ItemId): Boolean {
        val result = withContext(ioDispatcher) { folders.moveToHome(itemId, columns) }
        if (result !is TransferResult.Done) return false
        afterMemberLeft(result.dissolve)
        return true
    }

    /** Moves a member inside the open folder; the folder is re-read either way, so a rejected drop snaps back. */
    suspend fun moveMember(itemId: ItemId, position: LogicalPosition): Boolean {
        val result = withContext(ioDispatcher) { placements.move(homeId, itemId, position, columns) }
        refreshOpen()
        return result is MutationResult.Accepted
    }

    /** Resizes a member inside the open folder, with the same re-read as [moveMember]. */
    suspend fun resizeMember(itemId: ItemId, span: LogicalSpan): Boolean {
        val result = withContext(ioDispatcher) { placements.resize(homeId, itemId, span, columns) }
        refreshOpen()
        return result is MutationResult.Accepted
    }

    suspend fun removeMember(itemId: ItemId): Boolean {
        val result = withContext(ioDispatcher) { folders.removeMember(homeId, itemId, columns) }
        if (result !is RemoveMemberResult.Removed) return false
        afterMemberLeft(result.dissolve)
        return true
    }

    /**
     * Starts removing [folderId]: a folder with members asks for confirmation first; one without
     * any (not expected, auto-dissolve deletes it) is removed straight away.
     */
    suspend fun requestRemoveFolder(folderId: ItemId) {
        val count = (withContext(ioDispatcher) { folders.loadFolder(homeId, folderId, columns) }
            as? LoadFolderResult.Loaded)?.members?.size ?: return
        if (count == 0) removeFolder(folderId) else mutableRemovePrompt.value = folderId
    }

    fun dismissRemovePrompt() {
        mutableRemovePrompt.value = null
    }

    suspend fun confirmRemoveFolder(): Boolean {
        val folderId = mutableRemovePrompt.value ?: return false
        mutableRemovePrompt.value = null
        return removeFolder(folderId)
    }

    private suspend fun removeFolder(folderId: ItemId): Boolean {
        val result = withContext(ioDispatcher) { folders.removeFolder(homeId, folderId) }
        if (result !is RemoveFolderResult.Removed) return false
        result.droppedAppWidgetIds.forEach(releaseWidget)
        if (mutableOpenFolder.value == folderId) close()
        onHomeChanged()
        return true
    }

    private suspend fun afterMemberLeft(dissolve: Dissolve) {
        val openId = mutableOpenFolder.value
        val dissolvedId = when (dissolve) {
            is Dissolve.Replaced -> dissolve.folderId
            is Dissolve.Deleted -> dissolve.folderId
            Dissolve.None -> null
        }
        if (dissolvedId != null && dissolvedId == openId) close() else refreshOpen()
        onHomeChanged()
    }

    private suspend fun loadMembers(folderId: ItemId): List<HomeRenderTile>? {
        val loaded = withContext(ioDispatcher) { folders.loadFolder(homeId, folderId, columns) }
        if (loaded !is LoadFolderResult.Loaded) return null
        return HomeRenderProjection.resolveTiles(loaded.members, metadata())
    }
}
