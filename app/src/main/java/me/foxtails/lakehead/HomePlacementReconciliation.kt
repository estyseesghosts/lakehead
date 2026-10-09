package me.foxtails.lakehead

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRowGapNormalizer
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Pure rules the Home state owner uses to keep its in-memory placements in step
 * with a persisted mutation. They never read or write the repository.
 *
 * The requested placement is only a guess at the final layout: row-gap
 * normalization in the repository can shift other tiles, so the accepted grid
 * (not the request) is what finally replaces the optimistic state.
 */

/**
 * Optimistic apply of a requested move, computed exactly as the repository will
 * (`GridRules.move`, then `GridRowGapNormalizer.normalize`) so the first emission
 * already equals the accepted grid. Null when [itemId] is not placed or the move
 * would be rejected; the projected state then stays as it was.
 */
internal fun withRequestedMove(
    placements: List<HomePlacement>,
    itemId: ItemId,
    position: LogicalPosition,
    columns: Int,
): List<HomePlacement>? {
    if (placements.none { it.itemId == itemId }) return null
    val grid = placements.map {
        GridPlacement(
            it.itemId,
            GridPosition(it.position.column, it.position.row),
            GridSpan(it.span.columns, it.span.rows),
        )
    }
    val moved = GridRules.move(grid, itemId, GridPosition(position.column, position.row), columns)
    val accepted = moved as? GridRules.Result.Accepted ?: return null
    return withAcceptedGrid(
        placements,
        GridRules.Result.Accepted(GridRowGapNormalizer.normalize(accepted.placements)),
    )
}

/** Optimistic apply of a requested resize; null when [itemId] is not placed. */
internal fun withRequestedSpan(
    placements: List<HomePlacement>,
    itemId: ItemId,
    span: LogicalSpan,
): List<HomePlacement>? {
    if (placements.none { it.itemId == itemId }) return null
    return placements.map { if (it.itemId == itemId) it.withGeometry(it.position, span) else it }
}

/**
 * Replaces every position and span with the accepted, normalized grid.
 *
 * Order and identity of [placements] are kept; a placement the accepted grid no
 * longer contains is dropped. Returns null when the accepted grid names an item
 * this state has never seen (for example a placement added elsewhere), because
 * a [HomePlacement] cannot be built from grid data alone: the caller must
 * reload instead.
 */
internal fun withAcceptedGrid(
    placements: List<HomePlacement>,
    accepted: GridRules.Result.Accepted<ItemId>,
): List<HomePlacement>? {
    val finalById = accepted.placements.associateBy { it.id }
    val known = placements.mapTo(HashSet()) { it.itemId }
    if (finalById.keys.any { it !in known }) return null
    return placements.mapNotNull { placement ->
        val final = finalById[placement.itemId] ?: return@mapNotNull null
        placement.withGeometry(
            LogicalPosition(final.position.column, final.position.row),
            LogicalSpan(final.span.columns, final.span.rows),
        )
    }
}
