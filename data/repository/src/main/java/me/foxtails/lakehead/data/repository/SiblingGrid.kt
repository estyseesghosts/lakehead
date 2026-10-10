package me.foxtails.lakehead.data.repository

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRowGapNormalizer
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.data.database.PlacementDao
import me.foxtails.lakehead.data.database.PlacementEntity

/**
 * One grid of siblings: the top-level rows of a Home, or the members of one folder.
 * Home and folder repositories share this so both grids obey the same rules and
 * normalization. Callers run it inside a database transaction.
 */
internal class SiblingGrid(private val dao: PlacementDao) {
    /** The rows of the grid owned by [parentFolderId] (null for Home's top level) in [homeId]. */
    fun entities(homeId: String, parentFolderId: String?): List<PlacementEntity> =
        if (parentFolderId == null) {
            dao.topLevelPlacements(homeId)
        } else {
            dao.folderMembers(homeId, parentFolderId)
        }

    fun grid(entities: List<PlacementEntity>): List<GridPlacement<ItemId>> =
        entities.map { it.toGridPlacement() }

    fun <T : Any> normalize(placements: List<GridPlacement<T>>): List<GridPlacement<T>> =
        GridRowGapNormalizer.normalize(placements)

    /** Re-reads the grid, closes excess empty row gaps and persists only the rows that moved. */
    fun normalizePersisted(homeId: String, parentFolderId: String?) {
        val rows = entities(homeId, parentFolderId)
        persistShifted(rows, normalize(grid(rows)))
    }

    /** Updates only rows changed by normalization; transaction rollback covers the trigger too. */
    fun persistShifted(entities: List<PlacementEntity>, normalized: List<GridPlacement<ItemId>>) {
        val byId = normalized.associateBy { it.id }
        entities.forEach { entity ->
            val placement = byId[ItemId(entity.itemId)] ?: return@forEach
            val position = placement.position.toLogical()
            val span = placement.span.toLogical()
            if (position != LogicalPosition(entity.column, entity.row) ||
                span != LogicalSpan(entity.columnSpan, entity.rowSpan)
            ) {
                dao.updatePlacement(
                    PlacementEntity(
                        entity.itemId, entity.homeId, entity.itemType, entity.profileId,
                        entity.packageName, entity.className, position.column, position.row,
                        span.columns, span.rows, entity.availability, entity.parentFolderId,
                    ),
                )
            }
        }
    }

    private fun PlacementEntity.toGridPlacement() =
        GridPlacement(ItemId(itemId), GridPosition(column, row), GridSpan(columnSpan, rowSpan))
}

internal fun LogicalPosition.toGrid() = GridPosition(column, row)
internal fun LogicalSpan.toGrid() = GridSpan(columns, rows)
internal fun GridPosition.toLogical() = LogicalPosition(column, row)
internal fun GridSpan.toLogical() = LogicalSpan(columns, rows)
