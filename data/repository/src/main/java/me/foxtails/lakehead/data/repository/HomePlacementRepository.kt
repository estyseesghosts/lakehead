package me.foxtails.lakehead.data.repository

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.database.HomeEntity
import me.foxtails.lakehead.data.database.toDomain
import me.foxtails.lakehead.data.database.toEntity

/** Explicitly Home-scoped persistence operations; this contract has no platform types. */
interface HomePlacementRepository {
    fun load(homeId: HomeId, columns: Int, rows: Int): LoadResult

    fun place(
        homeId: HomeId,
        itemId: ItemId,
        appIdentity: AppIdentity,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
        rows: Int,
        replacingId: ItemId? = null,
    ): MutationResult

    fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int, rows: Int): MutationResult

    fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int, rows: Int): MutationResult

    fun remove(homeId: HomeId, itemId: ItemId): RemoveResult

    /** Ensures a home row exists for the given HomeId. Creates it if absent. Returns true. */
    fun ensureHome(homeId: HomeId): Boolean

    /** Loads the shortcut binding for [tileId], or an empty binding if none exists. */
    fun loadBinding(tileId: ItemId): TileBinding

    /** Persists (upserts) a shortcut binding. The referenced tile must exist. */
    fun saveBinding(binding: TileBinding)

    /** Deletes the shortcut binding for [tileId]. No-op if none exists. */
    fun deleteBinding(tileId: ItemId)
}

sealed interface LoadResult {
    data class Loaded(val placements: List<AppPlacement>) : LoadResult
    data object NotFound : LoadResult
}

sealed interface MutationResult {
    data class Accepted(val gridResult: GridRules.Result.Accepted<ItemId>) : MutationResult
    data class Rejected(val gridResult: GridRules.Result.Rejected) : MutationResult
    data object NotFound : MutationResult
    data object WrongHome : MutationResult
    data object ItemIdAlreadyExists : MutationResult
}

sealed interface RemoveResult {
    data object Removed : RemoveResult
    data object NotFound : RemoveResult
    data object WrongHome : RemoveResult
}

/** Caller supplies finite grid dimensions; the database remains the serialized state owner. */
class RoomHomePlacementRepository(
    private val database: me.foxtails.lakehead.data.database.PlacementDatabase,
) : HomePlacementRepository {
    private val dao get() = database.placementDao()

    override fun load(homeId: HomeId, columns: Int, rows: Int): LoadResult {
        require(columns > 0) { "columns must be positive" }
        require(rows > 0) { "rows must be positive" }
        if (dao.home(homeId.value) == null) return LoadResult.NotFound
        return LoadResult.Loaded(dao.placementsForHome(homeId.value).map { it.toDomain() })
    }

    override fun place(
        homeId: HomeId,
        itemId: ItemId,
        appIdentity: AppIdentity,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
        rows: Int,
        replacingId: ItemId?,
    ): MutationResult = database.runInTransaction<MutationResult> {
        if (dao.home(homeId.value) == null) return@runInTransaction MutationResult.NotFound
        val existingItem = dao.placement(itemId.value)
        if (existingItem != null && itemId != replacingId) {
            return@runInTransaction if (existingItem.homeId == homeId.value) {
                MutationResult.ItemIdAlreadyExists
            } else {
                MutationResult.WrongHome
            }
        }
        val replacing = replacingId?.let { id -> dao.placement(id.value) }
        if (replacingId != null && replacing == null) return@runInTransaction MutationResult.NotFound
        if (replacing != null && replacing.homeId != homeId.value) return@runInTransaction MutationResult.WrongHome

        val current = homeGrid(homeId)
        val candidate = GridPlacement(itemId, position.toGrid(), span.toGrid())
        when (val result = GridRules.place(current, candidate, columns, rows, replacingId)) {
            is GridRules.Result.Rejected -> MutationResult.Rejected(result)
            is GridRules.Result.Accepted -> {
                ensureAppReference(appIdentity)
                val acceptedPlacement = result.placements.first { it.id == itemId }
                val placement = AppPlacement(
                    homeId,
                    itemId,
                    appIdentity,
                    acceptedPlacement.position.toDomain(),
                    acceptedPlacement.span.toDomain(),
                    ItemAvailability.AVAILABLE,
                )
                if (replacingId != null && replacingId != itemId) {
                    dao.replacePlacement(
                        replacingId.value,
                        itemId.value,
                        placement.homeId.value,
                        placement.type.name,
                        placement.appIdentity.profileId.value,
                        placement.appIdentity.component.packageName,
                        placement.appIdentity.component.className,
                        placement.position.column,
                        placement.position.row,
                        placement.span.columns,
                        placement.span.rows,
                        placement.availability.name,
                    )
                } else if (existingItem != null) {
                    dao.updatePlacement(placement.toEntity())
                } else {
                    dao.insertPlacement(placement.toEntity())
                }
                MutationResult.Accepted(result)
            }
        }
    }

    override fun move(
        homeId: HomeId,
        itemId: ItemId,
        position: LogicalPosition,
        columns: Int,
        rows: Int,
    ): MutationResult = edit(homeId, itemId, { current ->
        GridRules.move(current, itemId, position.toGrid(), columns, rows)
    }, { accepted ->
        val target = requireNotNull(dao.placement(itemId.value)).toDomain()
        dao.updatePlacement(target.copy(position = accepted.placements.first { it.id == itemId }.position.toDomain()).toEntity())
    })

    override fun resize(
        homeId: HomeId,
        itemId: ItemId,
        span: LogicalSpan,
        columns: Int,
        rows: Int,
    ): MutationResult = edit(homeId, itemId, { current ->
        GridRules.resize(current, itemId, span.toGrid(), columns, rows)
    }, { accepted ->
        val target = requireNotNull(dao.placement(itemId.value)).toDomain()
        dao.updatePlacement(target.copy(span = accepted.placements.first { it.id == itemId }.span.toDomain()).toEntity())
    })

    override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = database.runInTransaction<RemoveResult> {
        if (dao.home(homeId.value) == null) return@runInTransaction RemoveResult.NotFound
        val target = dao.placement(itemId.value) ?: return@runInTransaction RemoveResult.NotFound
        if (target.homeId != homeId.value) return@runInTransaction RemoveResult.WrongHome
        dao.deletePlacement(itemId.value)
        RemoveResult.Removed
    }

    override fun ensureHome(homeId: HomeId): Boolean = database.runInTransaction<Boolean> {
        if (dao.home(homeId.value) != null) {
            true
        } else {
            dao.insertHome(HomeEntity(homeId.value))
            true
        }
    }

    override fun loadBinding(tileId: ItemId): TileBinding {
        val entity = dao.shortcutBinding(tileId.value)
        return entity?.toDomain() ?: TileBinding(tileId)
    }

    override fun saveBinding(binding: TileBinding): Unit = database.runInTransaction {
        val entity = binding.toEntity()
        dao.upsertShortcutBinding(entity)
    }

    override fun deleteBinding(tileId: ItemId) {
        dao.deleteShortcutBinding(tileId.value)
    }

    private fun edit(
        homeId: HomeId,
        itemId: ItemId,
        evaluate: (List<GridPlacement<ItemId>>) -> GridRules.Result<ItemId>,
        persist: (GridRules.Result.Accepted<ItemId>) -> Unit,
    ): MutationResult = database.runInTransaction<MutationResult> {
        if (dao.home(homeId.value) == null) return@runInTransaction MutationResult.NotFound
        val target = dao.placement(itemId.value) ?: return@runInTransaction MutationResult.NotFound
        if (target.homeId != homeId.value) return@runInTransaction MutationResult.WrongHome
        when (val result = evaluate(homeGrid(homeId))) {
            is GridRules.Result.Rejected -> MutationResult.Rejected(result)
            is GridRules.Result.Accepted -> {
                persist(result)
                MutationResult.Accepted(result)
            }
        }
    }

    private fun homeGrid(homeId: HomeId): List<GridPlacement<ItemId>> =
        dao.placementsForHome(homeId.value).map { entity ->
            entity.toDomain().let { GridPlacement(it.itemId, it.position.toGrid(), it.span.toGrid()) }
        }

    private fun ensureAppReference(identity: AppIdentity) {
        if (dao.appReference(
                identity.profileId.value,
                identity.component.packageName,
                identity.component.className,
            ) == null
        ) {
            dao.insertAppReference(identity.toEntity())
        }
    }
}

private fun LogicalPosition.toGrid() = GridPosition(column, row)
private fun LogicalSpan.toGrid() = GridSpan(columns, rows)
private fun GridPosition.toDomain() = LogicalPosition(column, row)
private fun GridSpan.toDomain() = LogicalSpan(columns, rows)
