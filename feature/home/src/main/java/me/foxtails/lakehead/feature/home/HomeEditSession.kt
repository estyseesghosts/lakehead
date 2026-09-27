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
        /**
         * Current resize preview span (resize mode) or origin span (move mode).
         *
         * W3 single-BR rule: this always holds the last VALID span. An invalid
         * (colliding/out-of-bounds) drag proposal never overwrite it — the
         * preview stays at the last valid size ("blocked stays") while
         * [candidateValidity] reports the error for the overlay. [lastValidSpan]
         * mirrors it for explicit release-commit reads.
         */
        val candidateSpan: LogicalSpan,
        /**
         * Last valid resize preview. Updated only when a resize proposal passes
         * `core:grid` validation; invalid proposals leave it untouched. Release
         * commits this span when it differs from [originSpan].
         */
        val lastValidSpan: LogicalSpan,
        /**
         * Last position whose move validity was [CandidateValidity.VALID].
         * Updated only when a move candidate evaluates to VALID; the render ghost
         * uses this to show the tile's snap-back target. Initialized to [origin].
         * Colliding or out-of-bounds proposals leave it unchanged ("blocked holds
         * last-valid").
         */
        val lastValidPosition: LogicalPosition,
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
        lastValidSpan = span,
        lastValidPosition = origin,
        candidateValidity = validity,
        mode = EditMode.MOVE,
    )
}

/**
 * Starts manipulation only for the tile selected by the W1 editing state.
 * The caller snapshots [interaction] and [tile] at pointer-down, then calls
 * this after slop so an editing tap remains a selection, not a move.
 */
internal fun HomeEditSession.enterSelectedDrag(
    interaction: HomeInteractionController,
    tile: HomeRenderTile?,
    placements: List<HomeRenderTile>,
    columns: Int,
    rows: Int,
): HomeEditSession {
    if (tile == null || interaction !is HomeInteractionController.Editing ||
        interaction.selectedId != tile.itemId
    ) return this
    return enterIfLaunchable(tile, placements, columns, rows)
}

/**
 * Switch to resize mode when the resize affordance was armed at pointer-down.
 * No-op if not Active, already in resize mode, or the affordance was not armed.
 */
fun HomeEditSession.beginResize(armedAtDown: Boolean): HomeEditSession {
    val active = this as? HomeEditSession.Active ?: return this
    if (!armedAtDown || active.mode == EditMode.RESIZE) return this
    return active.copy(
        mode = EditMode.RESIZE,
        // Reset candidate to origin position/span for fresh resize drag
        candidatePosition = active.origin,
        candidateSpan = active.originSpan,
        lastValidSpan = active.originSpan,
        // Preserve lastValidPosition — not relevant in resize mode but must not
        // be clobbered for a round-trip back to move mode.
        // Re-evaluate at original footprint (always valid = same as entry)
        candidateValidity = CandidateValidity.VALID,
    )
}

/**
 * Compatibility entry point for callers that have already performed the
 * pointer-down arm check. New callers should pass [armedAtDown] explicitly.
 */
fun HomeEditSession.beginResize(): HomeEditSession = beginResize(armedAtDown = true)

/**
 * Update the candidate position during a move drag. No-op if not Active or in RESIZE mode.
 *
 * W2 last-valid hold: when the candidate evaluates to VALID, both
 * [HomeEditSession.Active.candidatePosition] and
 * [HomeEditSession.Active.lastValidPosition] update. A colliding or out-of-bounds
 * proposal updates [HomeEditSession.Active.candidatePosition] and
 * [HomeEditSession.Active.candidateValidity] only — [lastValidPosition] stays at
 * the last valid cell so the render ghost can snap back. UNKNOWN_ITEM exits
 * to Idle (the item no longer exists in the placement list).
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
    if (validity == CandidateValidity.UNKNOWN_ITEM) return HomeEditSession.Idle
    return if (validity == CandidateValidity.VALID) {
        active.copy(candidatePosition = candidate, lastValidPosition = candidate, candidateValidity = validity)
    } else {
        // Blocked: hold the last valid position, report the error.
        active.copy(candidatePosition = active.lastValidPosition, candidateValidity = validity)
    }
}

/**
 * Update the candidate span during a resize drag. No-op if not Active or in MOVE mode.
 *
 * W3 last-valid hold: a proposal that passes `core:grid` validation replaces both
 * [HomeEditSession.Active.candidateSpan] and
 * [HomeEditSession.Active.lastValidSpan]; an invalid (colliding/out-of-bounds)
 * proposal leaves both spans at the last valid size and only flips
 * [HomeEditSession.Active.candidateValidity] so the overlay can render the error
 * state. Sub-threshold drags that quantize to the current preview are VALID
 * no-ops (preview holds, no pointer lag).
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
    return if (validity == CandidateValidity.VALID) {
        active.copy(candidateSpan = candidateSpan, lastValidSpan = candidateSpan, candidateValidity = validity)
    } else {
        // Blocked: hold the last valid preview, report the error.
        active.copy(candidateValidity = validity)
    }
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
 * Returns true if this session is Active in RESIZE mode and the last valid
 * preview span differs from originSpan.
 */
fun HomeEditSession.hasResized(): Boolean {
    val active = this as? HomeEditSession.Active ?: return false
    return active.mode == EditMode.RESIZE && active.lastValidSpan != active.originSpan
}

/**
 * Position to render for the move ghost tile (`renderedPosition = lastValid`).
 *
 * In MOVE mode the ghost snaps to [HomeEditSession.Active.lastValidPosition]
 * so that colliding/out-of-bounds drags show the tile at its last confirmed
 * valid cell rather than following the pointer into illegal territory. Idle
 * sessions or non-move sessions return the persisted position unchanged.
 */
fun HomeEditSession.renderedPosition(itemId: ItemId, persisted: LogicalPosition): LogicalPosition {
    val active = this as? HomeEditSession.Active ?: return persisted
    return if (active.mode == EditMode.MOVE && active.itemId == itemId) {
        active.lastValidPosition
    } else {
        persisted
    }
}

/**
 * Position to commit on move-drag release, or null when nothing should persist.
 *
 * Returns [HomeEditSession.Active.lastValidPosition] iff the session is Active
 * in MOVE mode and the last valid position differs from the origin; the caller
 * commits it via the repository and exits move mode. Null covers no-change,
 * blocked-only, and non-move sessions: the caller snaps back and stays editing
 * with zero writes.
 */
fun HomeEditSession.moveCommitPosition(): LogicalPosition? {
    val active = this as? HomeEditSession.Active ?: return null
    if (active.mode != EditMode.MOVE) return null
    if (active.lastValidPosition == active.origin) return null
    return active.lastValidPosition
}

/**
 * Span to render for the active tile (`renderedSpan = preview ?: persisted`).
 *
 * In RESIZE mode the tile content adapts to the live preview ([lastValidSpan],
 * which holds the last valid size while blocked); otherwise the persisted
 * [HomeEditSession.Active.originSpan] renders. Idle sessions have no preview.
 */
fun HomeEditSession.renderedSpan(itemId: ItemId, persisted: LogicalSpan): LogicalSpan {
    val active = this as? HomeEditSession.Active ?: return persisted
    return if (active.mode == EditMode.RESIZE && active.itemId == itemId) {
        active.lastValidSpan
    } else {
        persisted
    }
}

/**
 * Span to commit on resize-drag release, or null when nothing should persist.
 *
 * Returns [HomeEditSession.Active.lastValidSpan] iff the session is Active in
 * RESIZE mode and the last valid preview differs from the origin span; the
 * caller commits it via `onResizeTile` and stays in edit mode. Null covers
 * no-change, blocked-only (preview never left origin), and non-resize sessions:
 * the caller snaps back and stays editing with zero writes.
 */
fun HomeEditSession.resizeCommitSpan(): LogicalSpan? {
    val active = this as? HomeEditSession.Active ?: return null
    if (active.mode != EditMode.RESIZE) return null
    if (active.lastValidSpan == active.originSpan) return null
    return active.lastValidSpan
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
