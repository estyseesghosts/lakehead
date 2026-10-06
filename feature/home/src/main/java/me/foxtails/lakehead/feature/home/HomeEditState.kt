package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * The single owner of Home edit state: edit mode, selection, resize arming and
 * the current drag. Pure and immutable; every transition returns a new value.
 *
 * A [DragState.Move] can never change its span and a [DragState.Resize] can
 * never change its position: the update operations only write the request,
 * the held preview and the validity. A drag keeps the [HomeGridMetrics] it
 * began with.
 */
data class HomeEditState(
    val isEditing: Boolean = false,
    val selectedTileId: ItemId? = null,
    val resizeArmed: Boolean = false,
    val drag: DragState = DragState.None,
) {
    init {
        require(isEditing || (selectedTileId == null && !resizeArmed && drag == DragState.None)) {
            "selection, arm and drag require edit mode"
        }
        require(selectedTileId != null || !resizeArmed) { "resize arm requires a selection" }
        require(drag == DragState.None || drag.itemId == selectedTileId) {
            "the dragged tile must be the selected tile"
        }
    }

    val isDragging: Boolean get() = drag != DragState.None

    val moveDrag: DragState.Move? get() = drag as? DragState.Move

    val resizeDrag: DragState.Resize? get() = drag as? DragState.Resize

    /** True only when [tileId] is the selected tile and its resize arrow is armed. */
    fun isResizeArmedFor(tileId: ItemId?): Boolean =
        tileId != null && resizeArmed && selectedTileId == tileId

    fun enterOnLongPress(tileId: ItemId): HomeEditState {
        if (drag != DragState.None) return this
        return HomeEditState(isEditing = true, selectedTileId = tileId)
    }

    fun selectOnTap(tileId: ItemId): HomeEditState {
        if (!isEditing || drag != DragState.None) return this
        return copy(selectedTileId = tileId, resizeArmed = false)
    }

    fun toggleResizeArmed(): HomeEditState {
        if (!isEditing || selectedTileId == null || drag != DragState.None) return this
        return copy(resizeArmed = !resizeArmed)
    }

    fun exit(): HomeEditState = Normal

    fun cancelDrag(): HomeEditState = copy(drag = DragState.None)

    fun onTileRemoved(tileId: ItemId): HomeEditState {
        if (!isEditing) return this
        val selectionRemoved = selectedTileId == tileId
        return copy(
            selectedTileId = if (selectionRemoved) null else selectedTileId,
            resizeArmed = resizeArmed && !selectionRemoved,
            drag = if (drag.itemId == tileId) DragState.None else drag,
        )
    }

    /** Starts a MOVE of [tile], selecting it and clearing the resize arm. Any earlier drag is replaced. */
    fun beginMove(
        tile: HomeRenderTile,
        placements: List<HomeRenderTile>,
        metrics: HomeGridMetrics,
        grabOffsetXPx: Float,
        grabOffsetYPx: Float,
    ): HomeEditState {
        if (!isEditing || !tile.isLaunchable) return this
        val validity = moveValidity(placements, tile.itemId, tile.position, metrics.columns)
        if (validity == CandidateValidity.UNKNOWN_ITEM) return this
        return copy(
            selectedTileId = tile.itemId,
            resizeArmed = false,
            drag = DragState.Move(
                itemId = tile.itemId,
                originalPosition = tile.position,
                span = tile.span,
                metrics = metrics,
                grabOffsetXPx = grabOffsetXPx,
                grabOffsetYPx = grabOffsetYPx,
                requestedCell = RequestedCell(tile.position.column.toLong(), tile.position.row.toLong()),
                previewPosition = tile.position,
                validity = validity,
            ),
        )
    }

    /** Starts a RESIZE of the armed, selected [tile]; the arm is retained. */
    fun beginResize(
        tile: HomeRenderTile,
        metrics: HomeGridMetrics,
    ): HomeEditState {
        if (!isEditing || selectedTileId != tile.itemId || !resizeArmed || !tile.isLaunchable) return this
        return copy(
            drag = DragState.Resize(
                itemId = tile.itemId,
                position = tile.position,
                originalSpan = tile.span,
                metrics = metrics,
                requestedSpan = tile.span,
                previewSpan = tile.span,
                validity = CandidateValidity.VALID,
            ),
        )
    }

    fun updateMove(result: MoveCellResult, placements: List<HomeRenderTile>): HomeEditState {
        val move = drag as? DragState.Move ?: return this
        return when (result) {
            is MoveCellResult.Rejected -> copy(
                drag = move.copy(requestedCell = result.request, validity = CandidateValidity.OUT_OF_BOUNDS),
            )
            is MoveCellResult.Valid -> {
                val validity = moveValidity(placements, move.itemId, result.position, move.metrics.columns)
                when (validity) {
                    CandidateValidity.UNKNOWN_ITEM -> cancelDrag()
                    CandidateValidity.VALID -> copy(
                        drag = move.copy(
                            requestedCell = result.candidate,
                            previewPosition = result.position,
                            validity = validity,
                        ),
                    )
                    else -> copy(drag = move.copy(requestedCell = result.candidate, validity = validity))
                }
            }
        }
    }

    fun moveTo(
        pointerXPx: Float,
        pointerYPxViewport: Float,
        scrollYPx: Float,
        placements: List<HomeRenderTile>,
    ): HomeEditState {
        val move = drag as? DragState.Move ?: return this
        val result = MoveDragMath.cellForPixel(
            frame = move.frame,
            pointerXPx = pointerXPx,
            pointerYPxViewport = pointerYPxViewport,
            currentScrollPx = scrollYPx,
        )
        return updateMove(result, placements)
    }

    fun updateResize(requested: LogicalSpan, placements: List<HomeRenderTile>): HomeEditState {
        val resize = drag as? DragState.Resize ?: return this
        val validity = resizeValidity(placements, resize.itemId, requested, resize.metrics.columns)
        return copy(
            drag = if (validity == CandidateValidity.VALID) {
                resize.copy(requestedSpan = requested, previewSpan = requested, validity = validity)
            } else {
                resize.copy(requestedSpan = requested, validity = validity)
            },
        )
    }

    fun resizeTo(
        pointerXPx: Float,
        pointerYPxViewport: Float,
        scrollYPx: Float,
        placements: List<HomeRenderTile>,
    ): HomeEditState {
        val resize = drag as? DragState.Resize ?: return this
        val raw = ResizeDragMath.rawSpan(
            metrics = resize.metrics,
            originPosition = resize.position,
            pointerXPx = pointerXPx,
            pointerYPxViewport = pointerYPxViewport,
            currentScrollYPx = scrollYPx,
        )
        return updateResize(RenderableSpans.quantize(raw, resize.metrics.columns), placements)
    }

    /**
     * Temporary bridge: swaps the drag's metrics when the animated rest offset
     * moves. Delete when the rendered rest offset is frozen per edit session
     * (plan Slice 5.3). The only operation that may change drag metrics.
     */
    fun rebaseMetrics(metrics: HomeGridMetrics): HomeEditState = when (val current = drag) {
        is DragState.Move -> if (current.metrics == metrics) this else copy(drag = current.copy(metrics = metrics))
        is DragState.Resize -> if (current.metrics == metrics) this else copy(drag = current.copy(metrics = metrics))
        DragState.None -> this
    }

    /**
     * Adopts [metrics] when they differ from the MOVE drag's, then re-maps the
     * held pointer through them. Returns this when nothing changed.
     */
    fun rebaseAndMoveTo(
        metrics: HomeGridMetrics,
        pointerXPx: Float,
        pointerYPxViewport: Float,
        scrollYPx: Float,
        placements: List<HomeRenderTile>,
    ): HomeEditState {
        val move = moveDrag ?: return this
        if (move.metrics == metrics) return this
        return rebaseMetrics(metrics).moveTo(pointerXPx, pointerYPxViewport, scrollYPx, placements)
    }

    /** Ends the drag, keeping selection and arm, and reports what (if anything) to commit. */
    fun finish(): HomeEditFinish {
        val commit: HomeEditCommit? = when (val current = drag) {
            is DragState.Move ->
                if (current.validity == CandidateValidity.VALID &&
                    current.previewPosition != current.originalPosition
                ) {
                    HomeEditCommit.Move(current.itemId, current.previewPosition)
                } else {
                    null
                }
            is DragState.Resize ->
                if (current.previewSpan != current.originalSpan) {
                    HomeEditCommit.Resize(current.itemId, current.previewSpan)
                } else {
                    null
                }
            DragState.None -> null
        }
        return HomeEditFinish(copy(drag = DragState.None), commit)
    }

    fun renderedSpan(itemId: ItemId, persisted: LogicalSpan): LogicalSpan {
        val resize = drag as? DragState.Resize ?: return persisted
        return if (resize.itemId == itemId) resize.previewSpan else persisted
    }

    companion object {
        val Normal = HomeEditState()
    }
}

/** Legality of the last drag request, judged by `core:grid`. */
enum class CandidateValidity { VALID, COLLIDING, OUT_OF_BOUNDS, UNKNOWN_ITEM }

sealed interface DragState {
    data object None : DragState

    data class Move(
        val itemId: ItemId,
        val originalPosition: LogicalPosition,
        val span: LogicalSpan,
        val metrics: HomeGridMetrics,
        val grabOffsetXPx: Float,
        val grabOffsetYPx: Float,
        /** Raw last request; may lie outside the grid. */
        val requestedCell: RequestedCell,
        /** Last valid position, held while requests are blocked. */
        val previewPosition: LogicalPosition,
        /** Validity of the last request. */
        val validity: CandidateValidity,
    ) : DragState {
        val frame: MoveCoordinateFrame
            get() = MoveCoordinateFrame(metrics, GridSpan(span.columns, span.rows), grabOffsetXPx, grabOffsetYPx)
    }

    data class Resize(
        val itemId: ItemId,
        val position: LogicalPosition,
        val originalSpan: LogicalSpan,
        val metrics: HomeGridMetrics,
        val requestedSpan: LogicalSpan,
        /** Last valid span, held while requests are blocked. */
        val previewSpan: LogicalSpan,
        val validity: CandidateValidity,
    ) : DragState
}

val DragState.itemId: ItemId?
    get() = when (this) {
        is DragState.Move -> itemId
        is DragState.Resize -> itemId
        DragState.None -> null
    }

sealed interface HomeEditCommit {
    data class Move(val itemId: ItemId, val position: LogicalPosition) : HomeEditCommit
    data class Resize(val itemId: ItemId, val span: LogicalSpan) : HomeEditCommit
}

data class HomeEditFinish(val state: HomeEditState, val commit: HomeEditCommit?)

private fun List<HomeRenderTile>.toGridPlacements(): List<GridPlacement<ItemId>> =
    map {
        GridPlacement(
            it.itemId,
            GridPosition(it.position.column, it.position.row),
            GridSpan(it.span.columns, it.span.rows),
        )
    }

private fun moveValidity(
    placements: List<HomeRenderTile>,
    itemId: ItemId,
    position: LogicalPosition,
    columns: Int,
): CandidateValidity = validityOf(
    GridRules.move(placements.toGridPlacements(), itemId, GridPosition(position.column, position.row), columns),
)

private fun resizeValidity(
    placements: List<HomeRenderTile>,
    itemId: ItemId,
    span: LogicalSpan,
    columns: Int,
): CandidateValidity = validityOf(
    GridRules.resize(placements.toGridPlacements(), itemId, GridSpan(span.columns, span.rows), columns),
)

private fun validityOf(result: GridRules.Result<ItemId>): CandidateValidity = when (result) {
    is GridRules.Result.Accepted -> CandidateValidity.VALID
    is GridRules.Result.Rejected -> when (result.reason) {
        GridRules.Rejection.OUT_OF_BOUNDS -> CandidateValidity.OUT_OF_BOUNDS
        GridRules.Rejection.COLLISION -> CandidateValidity.COLLIDING
        GridRules.Rejection.UNKNOWN_ITEM -> CandidateValidity.UNKNOWN_ITEM
    }
}
