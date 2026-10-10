package me.foxtails.lakehead

import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.data.repository.CreateFolderResult
import me.foxtails.lakehead.data.repository.FolderRepository
import me.foxtails.lakehead.data.repository.LoadFolderResult
import me.foxtails.lakehead.data.repository.RemoveFolderResult
import me.foxtails.lakehead.data.repository.RemoveMemberResult
import me.foxtails.lakehead.data.repository.TransferResult

/** A [FolderRepository] for tests that have no folders: Home loads see none and nothing writes. */
internal object EmptyFolderRepository : FolderRepository {
    override fun createFolder(homeId: HomeId, fromItemId: ItemId, folderId: ItemId, columns: Int): CreateFolderResult =
        error("not expected")

    override fun loadFolder(homeId: HomeId, folderId: ItemId, columns: Int): LoadFolderResult = LoadFolderResult.NotFound

    override fun loadAllMembers(homeId: HomeId): Map<ItemId, List<HomePlacement>> = emptyMap()

    override fun moveIntoFolder(itemId: ItemId, folderId: ItemId, columns: Int): TransferResult = error("not expected")

    override fun moveToHome(itemId: ItemId, columns: Int): TransferResult = error("not expected")

    override fun removeMember(homeId: HomeId, itemId: ItemId, columns: Int): RemoveMemberResult = error("not expected")

    override fun removeFolder(homeId: HomeId, folderId: ItemId): RemoveFolderResult = error("not expected")
}
