package me.foxtails.lakehead.data.repository

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.PlacementRect
import me.foxtails.lakehead.core.grid.firstFreeRect
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.data.database.PlacementDao
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.database.PlacementEntity
import me.foxtails.lakehead.data.database.toDomain

/** What the auto-dissolve step did after a member left a folder. */
sealed interface Dissolve {
    data object None : Dissolve

    /** One member was left; [survivorId] took the folder's place on Home and the folder row is gone. */
    data class Replaced(val folderId: ItemId, val survivorId: ItemId) : Dissolve

    /** No member was left; the folder row was deleted. */
    data class Deleted(val folderId: ItemId) : Dissolve
}

sealed interface CreateFolderResult {
    data class Created(val folderId: ItemId) : CreateFolderResult
    data object NotFound : CreateFolderResult
    data object WrongHome : CreateFolderResult
    data object ItemIdAlreadyExists : CreateFolderResult

    /** The source is a folder or already sits in one; folders do not nest. */
    data object WouldNest : CreateFolderResult
    data class Rejected(val gridResult: GridRules.Result.Rejected) : CreateFolderResult
}

sealed interface LoadFolderResult {
    /** Members in persisted order with normalized positions. */
    data class Loaded(val members: List<HomePlacement>) : LoadFolderResult
    data object NotFound : LoadFolderResult
    data object WrongHome : LoadFolderResult
    data object NotAFolder : LoadFolderResult
}

sealed interface TransferResult {
    data class Done(val dissolve: Dissolve) : TransferResult
    data object NotFound : TransferResult
    data object WrongHome : TransferResult
    data object NotAFolder : TransferResult

    /** The item is a folder, or already sits in a folder; only top-level non-folders move in. */
    data object WouldNest : TransferResult
    data object NotAMember : TransferResult
    data class Rejected(val gridResult: GridRules.Result.Rejected) : TransferResult
}

sealed interface RemoveFolderResult {
    /** [droppedAppWidgetIds] belong to deleted widget members; the caller releases them. */
    data class Removed(val droppedAppWidgetIds: List<Int>) : RemoveFolderResult
    data object NotFound : RemoveFolderResult
    data object WrongHome : RemoveFolderResult
    data object NotAFolder : RemoveFolderResult
}

sealed interface RemoveMemberResult {
    data class Removed(val dissolve: Dissolve) : RemoveMemberResult
    data object NotFound : RemoveMemberResult
    data object WrongHome : RemoveMemberResult
    data object NotAMember : RemoveMemberResult
}

/**
 * Folder operations over the placements database. Every operation is one transaction;
 * bindings follow the moved item because only its parent and position change.
 */
interface FolderRepository {
    /** Replaces [fromItemId] on Home with a 1×1 folder [folderId] at its origin and moves the tile in at (0, 0). */
    fun createFolder(homeId: HomeId, fromItemId: ItemId, folderId: ItemId, columns: Int): CreateFolderResult

    fun loadFolder(homeId: HomeId, folderId: ItemId, columns: Int): LoadFolderResult

    /** Members of every folder on [homeId], grouped by folder id, for the closed tiles' previews. */
    fun loadAllMembers(homeId: HomeId): Map<ItemId, List<HomePlacement>>

    /** Moves a top-level tile into the first free rectangle of the folder's grid. */
    fun moveIntoFolder(itemId: ItemId, folderId: ItemId, columns: Int): TransferResult

    /** Moves a member to the first free rectangle on Home, then applies auto-dissolve. */
    fun moveToHome(itemId: ItemId, columns: Int): TransferResult

    /** Deletes one member with its bindings, then applies auto-dissolve. */
    fun removeMember(homeId: HomeId, itemId: ItemId, columns: Int): RemoveMemberResult

    /** Deletes the folder, its members and their bindings. */
    fun removeFolder(homeId: HomeId, folderId: ItemId): RemoveFolderResult
}

class RoomFolderRepository(
    private val database: PlacementDatabase,
) : FolderRepository {
    private val dao get() = database.placementDao()
    private val siblings get() = SiblingGrid(dao)

    override fun createFolder(
        homeId: HomeId,
        fromItemId: ItemId,
        folderId: ItemId,
        columns: Int,
    ): CreateFolderResult = database.runInTransaction<CreateFolderResult> {
        val tile = dao.placement(fromItemId.value) ?: return@runInTransaction CreateFolderResult.NotFound
        if (tile.homeId != homeId.value) return@runInTransaction CreateFolderResult.WrongHome
        if (tile.itemType == FOLDER || tile.parentFolderId != null) {
            return@runInTransaction CreateFolderResult.WouldNest
        }
        if (dao.placement(folderId.value) != null) return@runInTransaction CreateFolderResult.ItemIdAlreadyExists

        val folderCell = GridPlacement(folderId, GridPosition(tile.column, tile.row), GridSpan(1, 1))
        val topLevel = siblings.grid(dao.topLevelPlacements(homeId.value))
        when (val result = GridRules.place(topLevel, folderCell, columns, replacingId = fromItemId)) {
            is GridRules.Result.Rejected -> CreateFolderResult.Rejected(result)
            is GridRules.Result.Accepted -> {
                dao.insertPlacement(
                    PlacementEntity(
                        folderId.value, homeId.value, FOLDER, null, null, null,
                        tile.column, tile.row, 1, 1, tile.availability, null,
                    ),
                )
                dao.reparentPlacement(fromItemId.value, folderId.value, 0, 0)
                siblings.normalizePersisted(homeId.value, null)
                CreateFolderResult.Created(folderId)
            }
        }
    }

    override fun loadFolder(homeId: HomeId, folderId: ItemId, columns: Int): LoadFolderResult =
        database.runInTransaction<LoadFolderResult> {
            val folder = dao.placement(folderId.value) ?: return@runInTransaction LoadFolderResult.NotFound
            if (folder.homeId != homeId.value) return@runInTransaction LoadFolderResult.WrongHome
            if (folder.itemType != FOLDER) return@runInTransaction LoadFolderResult.NotAFolder
            siblings.normalizePersisted(homeId.value, folderId.value)
            val bindings = dao.widgetBindingsForHome(homeId.value).associateBy { it.itemId }
            LoadFolderResult.Loaded(
                dao.folderMembers(homeId.value, folderId.value).map { it.toDomain(bindings[it.itemId]) },
            )
        }

    override fun loadAllMembers(homeId: HomeId): Map<ItemId, List<HomePlacement>> =
        database.runInTransaction<Map<ItemId, List<HomePlacement>>> {
            val bindings = dao.widgetBindingsForHome(homeId.value).associateBy { it.itemId }
            dao.membersForHome(homeId.value)
                .map { it.toDomain(bindings[it.itemId]) }
                .groupBy { requireNotNull(it.parentFolderId) }
        }

    override fun moveIntoFolder(itemId: ItemId, folderId: ItemId, columns: Int): TransferResult =
        database.runInTransaction<TransferResult> {
            val item = dao.placement(itemId.value) ?: return@runInTransaction TransferResult.NotFound
            val folder = dao.placement(folderId.value) ?: return@runInTransaction TransferResult.NotFound
            if (item.homeId != folder.homeId) return@runInTransaction TransferResult.WrongHome
            if (folder.itemType != FOLDER) return@runInTransaction TransferResult.NotAFolder
            if (item.itemType == FOLDER || item.parentFolderId != null) {
                return@runInTransaction TransferResult.WouldNest
            }
            val target = firstFree(item, dao.folderMembers(item.homeId, folderId.value), columns)
                ?: return@runInTransaction TransferResult.Rejected(OUT_OF_BOUNDS)
            dao.reparentPlacement(itemId.value, folderId.value, target.column, target.row)
            siblings.normalizePersisted(item.homeId, null)
            siblings.normalizePersisted(item.homeId, folderId.value)
            TransferResult.Done(Dissolve.None)
        }

    override fun moveToHome(itemId: ItemId, columns: Int): TransferResult =
        database.runInTransaction<TransferResult> {
            val item = dao.placement(itemId.value) ?: return@runInTransaction TransferResult.NotFound
            val folderId = item.parentFolderId ?: return@runInTransaction TransferResult.NotAMember
            val target = firstFree(item, dao.topLevelPlacements(item.homeId), columns)
                ?: return@runInTransaction TransferResult.Rejected(OUT_OF_BOUNDS)
            dao.reparentPlacement(itemId.value, null, target.column, target.row)
            TransferResult.Done(FolderDissolver(dao, siblings).afterMemberLeft(item.homeId, folderId, columns))
        }

    override fun removeMember(homeId: HomeId, itemId: ItemId, columns: Int): RemoveMemberResult =
        database.runInTransaction<RemoveMemberResult> {
            val item = dao.placement(itemId.value) ?: return@runInTransaction RemoveMemberResult.NotFound
            if (item.homeId != homeId.value) return@runInTransaction RemoveMemberResult.WrongHome
            val folderId = item.parentFolderId ?: return@runInTransaction RemoveMemberResult.NotAMember
            deleteWithBindings(itemId.value)
            RemoveMemberResult.Removed(FolderDissolver(dao, siblings).afterMemberLeft(homeId.value, folderId, columns))
        }

    override fun removeFolder(homeId: HomeId, folderId: ItemId): RemoveFolderResult =
        database.runInTransaction<RemoveFolderResult> {
            val folder = dao.placement(folderId.value) ?: return@runInTransaction RemoveFolderResult.NotFound
            if (folder.homeId != homeId.value) return@runInTransaction RemoveFolderResult.WrongHome
            if (folder.itemType != FOLDER) return@runInTransaction RemoveFolderResult.NotAFolder
            val widgetIds = dao.folderMemberWidgetIds(folderId.value)
            // Explicit deletes keep the contract correct with foreign keys disabled.
            dao.folderMembers(homeId.value, folderId.value).forEach { deleteWithBindings(it.itemId) }
            deleteWithBindings(folderId.value)
            siblings.normalizePersisted(homeId.value, null)
            RemoveFolderResult.Removed(widgetIds)
        }

    private fun deleteWithBindings(itemId: String) {
        dao.deleteShortcutBinding(itemId)
        dao.deleteWidgetBinding(itemId)
        dao.deleteAlbumBinding(itemId)
        dao.deletePlacement(itemId)
    }

    /** The first free rectangle for [item]'s span among [occupiedRows], or null if it exceeds [columns]. */
    private fun firstFree(item: PlacementEntity, occupiedRows: List<PlacementEntity>, columns: Int): LogicalPosition? {
        if (item.columnSpan > columns) return null
        val occupied = occupiedRows.filter { it.itemId != item.itemId }.map {
            PlacementRect(LogicalPosition(it.column, it.row), LogicalSpan(it.columnSpan, it.rowSpan))
        }
        return firstFreeRect(occupied, columns, LogicalSpan(item.columnSpan, item.rowSpan))
    }

    private companion object {
        const val FOLDER = "FOLDER"
        val OUT_OF_BOUNDS = GridRules.Result.Rejected(GridRules.Rejection.OUT_OF_BOUNDS)
    }
}

/** Auto-dissolve, applied inside the transaction of the operation that took a member out. */
internal class FolderDissolver(
    private val dao: PlacementDao,
    private val siblings: SiblingGrid,
) {
    fun afterMemberLeft(homeId: String, folderId: String, columns: Int): Dissolve {
        val folder = dao.placement(folderId) ?: return Dissolve.None
        val members = dao.folderMembers(homeId, folderId)
        return when {
            members.size >= 2 -> {
                siblings.normalizePersisted(homeId, folderId)
                Dissolve.None
            }
            members.isEmpty() -> {
                dao.deletePlacement(folderId)
                siblings.normalizePersisted(homeId, null)
                Dissolve.Deleted(ItemId(folderId))
            }
            else -> replaceWithSurvivor(homeId, folder, members.single(), columns)
        }
    }

    private fun replaceWithSurvivor(
        homeId: String,
        folder: PlacementEntity,
        survivor: PlacementEntity,
        columns: Int,
    ): Dissolve {
        val topLevel = siblings.grid(dao.topLevelPlacements(homeId))
        val survivorId = ItemId(survivor.itemId)
        val folderId = ItemId(folder.itemId)
        val span = LogicalSpan(survivor.columnSpan, survivor.rowSpan)
        val atFolder = GridPosition(folder.column, folder.row)
        val fits = GridRules.place(topLevel, GridPlacement(survivorId, atFolder, span.toGrid()), columns, folderId)
        val position = if (fits is GridRules.Result.Accepted) {
            LogicalPosition(folder.column, folder.row)
        } else {
            val occupied = topLevel.filter { it.id != folderId }.map {
                PlacementRect(it.position.toLogical(), it.span.toLogical())
            }
            firstFreeRect(occupied, columns, span)
        }
        // Detach the survivor before the folder row goes, or the parent cascade would take it too.
        dao.reparentPlacement(survivor.itemId, null, position.column, position.row)
        dao.deletePlacement(folder.itemId)
        siblings.normalizePersisted(homeId, null)
        return Dissolve.Replaced(folderId, survivorId)
    }
}
