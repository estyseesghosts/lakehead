package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Unified transient editing session for move and resize. Pure framework-free state machine.
 * No persistence, no Android imports.
 *
 * States: [Idle] or [Active] (with [EditMode.MOVE] or [EditMode.RESIZE]).
 */
sealed interface HomeEditSession {
    /** No edit in progress. */
    data object Idle : HomeEditSession

    /** Active edit for [itemId]. [mode] distinguishes move vs resize gestures. */
    data class Active(
        val itemId: ItemId,
        val origin: LogicalPosition,
        /** The tile's span at session entry. For resize, this is the original span. */
        val originSpan: LogicalSpan,
        /** Current candidate position (move mode) or origin (resize mode). */
        val candidatePosition: LogicalPosition,
        /** Current candidate span (move mode keeps originSpan; resize mode changes). */
        val candidateSpan: LogicalSpan,
        val candidateValidity: CandidateValidity,
        val mode: EditMode,
    ) : HomeEditSession
}

enum class EditMode { MOVE, RESIZE }

enum class CandidateValidity { VALID, COLLIDING, OUT_OF_BOUNDS, UNKNOWN_ITEM }

/**
 * Enter edit mode if the tile is launchable. Returns Active at the tile's
 * current position/span with MOVE mode (body-drag default), or this (unchanged) if not launchable.
 */
fun HomeEditSession.enterIfLaunchable(
    tile: HomeRenderTile,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): HomeEditSession {
    if (!tile.isLaunchable) return this
    val origin = tile.position
    val span = tile.span
    val validity = computeMoveValidity(tile.itemId, origin, span, placements, columns, rows)
    return HomeEditSession.Active(
        itemId = tile.itemId,
        origin = origin,
        originSpan = span,
        candidatePosition = origin,
        candidateSpan = span,
        candidateValidity = validity,
        mode = EditMode.MOVE,
    )
}

/**
 * Switch to resize mode. No-op if not Active or already in resize mode.
 */
fun HomeEditSession.beginResize(): HomeEditSession {
    val active = this as? HomeEditSession.Active ?: return this
    if (active.mode == EditMode.RESIZE) return this
    return active.copy(
        mode = EditMode.RESIZE,
        // Reset candidate to origin position/span for fresh resize drag
        candidatePosition = active.origin,
        candidateSpan = active.originSpan,
        // Re-evaluate at original footprint (always valid = same as entry)
        candidateValidity = CandidateValidity.VALID,
    )
}

/**
 * Update the candidate position during a move drag. No-op if not Active or in RESIZE mode.
 */
fun HomeEditSession.updateMoveCandidate(
    candidate: LogicalPosition,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): HomeEditSession {
    val active = this as? HomeEditSession.Active ?: return this
    if (active.mode != EditMode.MOVE) return this
    val validity = computeMoveValidity(active.itemId, candidate, active.candidateSpan, placements, columns, rows)
    return active.copy(candidatePosition = candidate, candidateValidity = validity)
}

/**
 * Update the candidate span during a resize drag. No-op if not Active or in MOVE mode.
 */
fun HomeEditSession.updateResizeCandidate(
    candidateSpan: LogicalSpan,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): HomeEditSession {
    val active = this as? HomeEditSession.Active ?: return this
    if (active.mode != EditMode.RESIZE) return this
    val validity = computeResizeValidity(active.itemId, active.origin, candidateSpan, placements, columns, rows)
    return active.copy(candidateSpan = candidateSpan, candidateValidity = validity)
}

/**
 * Cancel the edit session. Returns Idle. Zero writes.
 */
fun HomeEditSession.cancel(): HomeEditSession = HomeEditSession.Idle

/**
 * Returns true if this session is Active in MOVE mode and the candidate position differs from origin.
 */
fun HomeEditSession.hasMoved(): Boolean {
    val active = this as? HomeEditSession.Active ?: return false
    return active.mode == EditMode.MOVE && active.candidatePosition != active.origin
}

/**
 * Returns true if this session is Active in RESIZE mode and the candidate span differs from originSpan.
 */
fun HomeEditSession.hasResized(): Boolean {
    val active = this as? HomeEditSession.Active ?: return false
    return active.mode == EditMode.RESIZE && active.candidateSpan != active.originSpan
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
 * Compute move candidate validity by delegating to GridRules.move.
 * GridRules.move ignores the item's own footprint.
 */
private fun computeMoveValidity(
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
    return mapResult(result)
}

/**
 * Compute resize candidate validity by delegating to GridRules.resize.
 * GridRules.resize ignores the item's own footprint.
 */
private fun computeResizeValidity(
    itemId: ItemId,
    position: LogicalPosition,
    span: LogicalSpan,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): CandidateValidity {
    val gridPlacements = placements.toGridPlacements()
    val result = GridRules.resize(
        gridPlacements,
        itemId,
        GridSpan(span.columns, span.rows),
        columns,
        rows,
    )
    return mapResult(result)
}

private fun mapResult(result: GridRules.Result<ItemId>): CandidateValidity = when (result) {
    is GridRules.Result.Accepted -> CandidateValidity.VALID
    is GridRules.Result.Rejected -> when (result.reason) {
        GridRules.Rejection.OUT_OF_BOUNDS -> CandidateValidity.OUT_OF_BOUNDS
        GridRules.Rejection.COLLISION -> CandidateValidity.COLLIDING
        GridRules.Rejection.UNKNOWN_ITEM -> CandidateValidity.UNKNOWN_ITEM
    }
}