package me.foxtails.lakehead.data.repository

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRowGapNormalizer
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.WidgetPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.database.HomeEntity
import me.foxtails.lakehead.data.database.PlacementEntity
import me.foxtails.lakehead.data.database.referenceIdentity
import me.foxtails.lakehead.data.database.toBindingEntity
import me.foxtails.lakehead.data.database.toDomain
import me.foxtails.lakehead.data.database.toEntity

/** Explicitly Home-scoped persistence operations; this contract has no platform types. */
interface HomePlacementRepository {
    /** Loads a home and normalizes persisted excess empty row gaps in the same transaction. */
    fun load(homeId: HomeId, columns: Int): LoadResult

    /** Accepted mutations return the final (possibly row-shifted) placements. */
    fun place(
        homeId: HomeId,
        itemId: ItemId,
        appIdentity: AppIdentity,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
        replacingId: ItemId? = null,
    ): MutationResult

    /** Places a bound widget; [appWidgetId] is persisted beside the provider reference. */
    fun placeWidget(
        homeId: HomeId,
        itemId: ItemId,
        provider: AppIdentity,
        appWidgetId: Int,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
    ): MutationResult

    /** Every `appWidgetId` that has a persisted placement, for the startup host sweep. */
    fun persistedAppWidgetIds(): Set<Int>

    fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int): MutationResult

    fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int): MutationResult

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
    data class Loaded(val placements: List<HomePlacement>) : LoadResult
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

/** Caller supplies horizontal dimensions; the database remains the serialized state owner. */
class RoomHomePlacementRepository(
    private val database: me.foxtails.lakehead.data.database.PlacementDatabase,
) : HomePlacementRepository {
    private val dao get() = database.placementDao()

    override fun load(homeId: HomeId, columns: Int): LoadResult {
        require(columns > 0) { "columns must be positive" }
        return database.runInTransaction<LoadResult> {
            if (dao.home(homeId.value) == null) return@runInTransaction LoadResult.NotFound
            val entities = dao.placementsForHome(homeId.value)
            val normalized = normalize(entities.map { it.toGridPlacement() })
            persistShifted(entities, normalized)
            val bindings = dao.widgetBindingsForHome(homeId.value).associateBy { it.itemId }
            LoadResult.Loaded(entities.map { entity ->
                entity.toDomain(bindings[entity.itemId]).let { placement ->
                    placement.withGeometry(
                        normalized.first { it.id == placement.itemId }.position.toDomain(),
                        placement.span,
                    )
                }
            })
        }
    }

    override fun place(
        homeId: HomeId,
        itemId: ItemId,
        appIdentity: AppIdentity,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
        replacingId: ItemId?,
    ): MutationResult = placeItem(homeId, itemId, appIdentity, position, span, columns, replacingId) { at, size ->
        AppPlacement(homeId, itemId, appIdentity, at, size, ItemAvailability.AVAILABLE)
    }

    override fun placeWidget(
        homeId: HomeId,
        itemId: ItemId,
        provider: AppIdentity,
        appWidgetId: Int,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
    ): MutationResult = placeItem(homeId, itemId, provider, position, span, columns, null) { at, size ->
        WidgetPlacement(homeId, itemId, provider, appWidgetId, at, size, ItemAvailability.AVAILABLE)
    }

    override fun persistedAppWidgetIds(): Set<Int> = dao.allAppWidgetIds().toSet()

    private fun placeItem(
        homeId: HomeId,
        itemId: ItemId,
        reference: AppIdentity,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
        replacingId: ItemId?,
        build: (LogicalPosition, LogicalSpan) -> HomePlacement,
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
        when (val result = GridRules.place(current, candidate, columns, replacingId)) {
            is GridRules.Result.Rejected -> MutationResult.Rejected(result)
            is GridRules.Result.Accepted -> {
                ensureAppReference(reference)
                val normalized = normalize(result.placements)
                val acceptedPlacement = normalized.first { it.id == itemId }
                val placement = build(
                    acceptedPlacement.position.toDomain(),
                    acceptedPlacement.span.toDomain(),
                )
                val identity = placement.referenceIdentity()
                if (replacingId != null && replacingId != itemId) {
                    dao.replacePlacement(
                        replacingId.value,
                        itemId.value,
                        placement.homeId.value,
                        placement.type.name,
                        identity.profileId.value,
                        identity.component.packageName,
                        identity.component.className,
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
                if (placement is WidgetPlacement) dao.upsertWidgetBinding(placement.toBindingEntity())
                persistShifted(dao.placementsForHome(homeId.value), normalized)
                MutationResult.Accepted(GridRules.Result.Accepted(normalized))
            }
        }
    }

    override fun move(
        homeId: HomeId,
        itemId: ItemId,
        position: LogicalPosition,
        columns: Int,
    ): MutationResult = edit(homeId, itemId, { current ->
        GridRules.move(current, itemId, position.toGrid(), columns)
        }, { accepted ->
        val normalized = normalize(accepted.placements)
        persistShifted(dao.placementsForHome(homeId.value), normalized)
        normalized
    })

    override fun resize(
        homeId: HomeId,
        itemId: ItemId,
        span: LogicalSpan,
        columns: Int,
    ): MutationResult = edit(homeId, itemId, { current ->
        GridRules.resize(current, itemId, span.toGrid(), columns)
        }, { accepted ->
        val normalized = normalize(accepted.placements)
        persistShifted(dao.placementsForHome(homeId.value), normalized)
        normalized
    })

    override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = database.runInTransaction<RemoveResult> {
        if (dao.home(homeId.value) == null) return@runInTransaction RemoveResult.NotFound
        val target = dao.placement(itemId.value) ?: return@runInTransaction RemoveResult.NotFound
        if (target.homeId != homeId.value) return@runInTransaction RemoveResult.WrongHome
        dao.deletePlacement(itemId.value)
        // Keep the cascade explicit as well as declared in Room, so the
        // repository contract remains correct with foreign-key enforcement
        // disabled in test/in-memory configurations.
        dao.deleteShortcutBinding(itemId.value)
        dao.deleteWidgetBinding(itemId.value)
        val remaining = dao.placementsForHome(homeId.value)
        persistShifted(remaining, normalize(remaining.map { it.toGridPlacement() }))
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
        persist: (GridRules.Result.Accepted<ItemId>) -> List<GridPlacement<ItemId>>,
    ): MutationResult = database.runInTransaction<MutationResult> {
        if (dao.home(homeId.value) == null) return@runInTransaction MutationResult.NotFound
        val target = dao.placement(itemId.value) ?: return@runInTransaction MutationResult.NotFound
        if (target.homeId != homeId.value) return@runInTransaction MutationResult.WrongHome
        when (val result = evaluate(homeGrid(homeId))) {
            is GridRules.Result.Rejected -> MutationResult.Rejected(result)
            is GridRules.Result.Accepted -> {
                MutationResult.Accepted(GridRules.Result.Accepted(persist(result)))
            }
        }
    }

    private fun homeGrid(homeId: HomeId): List<GridPlacement<ItemId>> =
        dao.placementsForHome(homeId.value).map { it.toGridPlacement() }

    private fun <T : Any> normalize(placements: List<GridPlacement<T>>): List<GridPlacement<T>> =
        GridRowGapNormalizer.normalize(placements)

    /** Updates only rows changed by normalization; Room transaction rollback covers the trigger too. */
    private fun persistShifted(
        entities: List<PlacementEntity>,
        normalized: List<GridPlacement<ItemId>>,
    ) {
        val byId = normalized.associateBy { it.id }
        entities.forEach { entity ->
            val placement = byId[ItemId(entity.itemId)] ?: return@forEach
            val position = placement.position.toDomain()
            val span = placement.span.toDomain()
            if (position != LogicalPosition(entity.column, entity.row) ||
                span != LogicalSpan(entity.columnSpan, entity.rowSpan)
            ) {
                dao.updatePlacement(
                    PlacementEntity(
                        entity.itemId, entity.homeId, entity.itemType, entity.profileId,
                        entity.packageName, entity.className, position.column, position.row,
                        span.columns, span.rows, entity.availability,
                    ),
                )
            }
        }
    }

    private fun PlacementEntity.toGridPlacement() =
        GridPlacement(ItemId(itemId), GridPosition(column, row), GridSpan(columnSpan, rowSpan))

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
