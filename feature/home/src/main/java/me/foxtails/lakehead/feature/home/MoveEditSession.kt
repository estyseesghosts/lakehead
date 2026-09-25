package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Transient move-editing session. Pure framework-free state machine.
 * No persistence, no Android imports.
 */
sealed interface MoveEditSession {
    /** No move in progress. */
    data object Idle : MoveEditSession

    /** Active move for [itemId] from [origin] to [candidate]. */
    data class Active(
        val itemId: ItemId,
        val origin: LogicalPosition,
        val span: LogicalSpan,
        val candidate: LogicalPosition,
        val candidateValidity: CandidateValidity,
    ) : MoveEditSession
}

enum class CandidateValidity { VALID, COLLIDING, OUT_OF_BOUNDS, UNKNOWN_ITEM }

/**
 * Enter move mode if the tile is launchable. Returns Active at the tile's
 * current position, or this (unchanged) if not launchable.
 */
fun MoveEditSession.enterIfLaunchable(
    tile: HomeRenderTile,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): MoveEditSession {
    if (!tile.isLaunchable) return this
    val origin = tile.position
    val span = tile.span
    // Initial candidate = origin, validity = VALID (self footprint ignored)
    val validity = computeValidity(tile.itemId, origin, span, placements, columns, rows)
    return MoveEditSession.Active(
        itemId = tile.itemId,
        origin = origin,
        span = span,
        candidate = origin,
        candidateValidity = validity,
    )
}

/**
 * Update the candidate position and recompute validity.
 * No-op if not Active.
 */
fun MoveEditSession.updateCandidate(
    candidate: LogicalPosition,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): MoveEditSession {
    val active = this as? MoveEditSession.Active ?: return this
    val validity = computeValidity(active.itemId, candidate, active.span, placements, columns, rows)
    return active.copy(candidate = candidate, candidateValidity = validity)
}

/**
 * Cancel the move session. Returns Idle. Zero writes.
 */
fun MoveEditSession.cancel(): MoveEditSession = MoveEditSession.Idle

/**
 * Returns true if this session is Active and the candidate differs from origin.
 */
fun MoveEditSession.hasMoved(): Boolean {
    val active = this as? MoveEditSession.Active ?: return false
    return active.candidate != active.origin
}

/**
 * Convert HomeRenderTile list to GridPlacement list for GridRules.
 */
private fun List<HomeRenderTile>.toGridPlacements(): List<GridPlacement<ItemId>> =
    map {
        GridPlacement(
            it.itemId,
            GridPosition(it.position.column, it.position.row),
            GridSpan(it.span.columns, it.span.rows),
        )
    }

/**
 * Compute candidate validity by delegating to GridRules.move.
 * GridRules.move ignores the item's own footprint.
 */
private fun computeValidity(
    itemId: ItemId,
    candidate: LogicalPosition,
    span: LogicalSpan,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): CandidateValidity {
    val gridPlacements = placements.toGridPlacements()
    val result = GridRules.move(
        gridPlacements,
        itemId,
        GridPosition(candidate.column, candidate.row),
        columns,
        rows,
    )
    return when (result) {
        is GridRules.Result.Accepted -> CandidateValidity.VALID
        is GridRules.Result.Rejected -> when (result.reason) {
            GridRules.Rejection.OUT_OF_BOUNDS -> CandidateValidity.OUT_OF_BOUNDS
            GridRules.Rejection.COLLISION -> CandidateValidity.COLLIDING
            GridRules.Rejection.UNKNOWN_ITEM -> CandidateValidity.UNKNOWN_ITEM
        }
    }
}