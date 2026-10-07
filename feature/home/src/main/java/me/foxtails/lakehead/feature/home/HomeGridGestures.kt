package me.foxtails.lakehead.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis

/**
 * What the edit-drag pointer loop needs from its surroundings, and nothing more.
 *
 * The loop reads fresh values through this host and hands outcomes outward as
 * [HomeDragEnd]; it never touches persistence, acknowledgement or reconciliation.
 */
internal interface HomeDragHost {
    var editState: HomeEditState

    /** Latest loaded tiles and geometry for the down hit test. */
    val hit: IdleHitSnapshot

    val scrollYPx: Int

    val isVisible: Boolean

    /** Outer pager-versus-grid owner; the drag only claims and releases it. */
    val pager: HomePagerGate

    /** Latest viewport pointer of a MOVE, retained for the edge ticker. */
    fun onMovePointer(pointerXPx: Float, pointerYPx: Float)

    /** The single outward emission per drag: a commit intent or a discard. */
    fun onDragEnded(route: DownDragRoute, end: HomeDragEnd)
}

/** Why a drag that had begun produced no commit. */
internal enum class HomeDragDiscard { CANCELLED, ABORTED, NO_CHANGE }

/** The commit intent or the discard reason emitted once when a drag ends. */
internal sealed interface HomeDragEnd {
    data class Commit(val commit: HomeEditCommit) : HomeDragEnd
    data class Discarded(val reason: HomeDragDiscard) : HomeDragEnd
}

/** How the pointer loop stopped. */
internal enum class DragExit { LIFTED, CANCELLED, STALE }

/** Pure: maps a loop exit and the finished edit state to the one outward end. */
internal fun resolveDragEnd(
    exit: DragExit,
    finished: HomeEditFinish,
    isVisible: Boolean,
): HomeDragEnd {
    if (exit == DragExit.CANCELLED) return HomeDragEnd.Discarded(HomeDragDiscard.CANCELLED)
    val commit = releaseCommit(
        finished = finished,
        isVisible = isVisible,
        completedByUp = exit == DragExit.LIFTED,
    )
    if (commit != null) return HomeDragEnd.Commit(commit)
    return HomeDragEnd.Discarded(
        when {
            !isVisible || exit != DragExit.LIFTED -> HomeDragDiscard.ABORTED
            else -> HomeDragDiscard.NO_CHANGE
        },
    )
}

/** Everything fixed at pointer down: the hit tile, the grab point and the arm snapshot. */
internal class DragDown(
    val pointerId: PointerId,
    val position: Offset,
    val tile: HomeRenderTile,
    val isTransfer: Boolean,
    val armedAtDown: Boolean,
    val inHandle: Boolean,
    val grabOffsetXPx: Float,
    val grabOffsetYPx: Float,
)

/**
 * The edit-drag gesture, read top to bottom:
 * down → hit test → wait for slop → choose MOVE or RESIZE once → update that
 * operation until up/cancel → emit one [HomeDragEnd].
 *
 * Runs as the body of `awaitEachGesture`. The pointer is already down from the
 * long-press that entered edit mode.
 */
internal suspend fun AwaitPointerEventScope.homeDragGesture(
    host: HomeDragHost,
    handleSizePx: Int,
) {
    val down = awaitDownTile(host, handleSizePx) ?: return
    host.pager.claim(HomeGestureWinner.SELECT_DRAG)
    try {
        val slop = awaitSlop(down) ?: return
        val route = chooseRouteOnce(down)
        if (route == DownDragRoute.NONE) return
        if (!beginDrag(host, down, route)) return
        host.pager.claim(dragWinner(route))
        val exit = trackDrag(host, down.pointerId, route, slop)
        val finished = host.editState.finish()
        host.editState = finished.state
        host.onDragEnded(route, resolveDragEnd(exit, finished, host.isVisible))
    } finally {
        host.pager.resetAxis()
        host.pager.release()
    }
}

/** Pointer down: hit test with the grid metrics. Null when no edit drag can start. */
private suspend fun AwaitPointerEventScope.awaitDownTile(
    host: HomeDragHost,
    handleSizePx: Int,
): DragDown? {
    val first = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return null
    val start = first.position
    // Pointer Y is viewport-local; adding the scroll read at down converts it to content space.
    val scrollYPx = host.scrollYPx.toFloat()
    val edit = host.editState
    val snapshot = host.hit
    val metrics = snapshot.metrics
    val downTile = metrics.tileAt(snapshot.tiles, start.x, metrics.contentY(start.y, scrollYPx))
    val selectedTile = edit.selectedTileId?.let { id -> snapshot.tiles.firstOrNull { it.itemId == id } }
    val selectedInHandle = selectedTile?.let {
        isInHandle(start.x, start.y + scrollYPx, metrics.pixelFrame(it.position, it.span), handleSizePx)
    } == true
    if (!edit.isEditing || (downTile == null && !selectedInHandle)) return null
    val tile = downTile ?: selectedTile ?: return null
    val bounds = metrics.pixelFrame(tile.position, tile.span)
    return DragDown(
        pointerId = first.id,
        position = start,
        tile = tile,
        isTransfer = isEditTransferDrag(edit, downTile?.itemId),
        armedAtDown = edit.isResizeArmedFor(tile.itemId),
        inHandle = isInHandle(start.x, start.y + scrollYPx, bounds, handleSizePx),
        grabOffsetXPx = start.x - bounds.left.toFloat(),
        grabOffsetYPx = start.y + scrollYPx - bounds.top.toFloat(),
    )
}

/** Waits until the pressed pointer passes drag slop. Null on release before slop. */
private suspend fun AwaitPointerEventScope.awaitSlop(
    down: DragDown,
): PointerInputChange? {
    val slopPx = viewConfiguration.touchSlop.toFloat()
    var axis = LockedAxis.UNDECIDED
    while (true) {
        val change = awaitPointerEvent().changes.find { it.id == down.pointerId }
        if (change == null || !change.pressed) return null
        axis = classifyHomeAxis(
            dxPx = change.position.x - down.position.x,
            dyPx = change.position.y - down.position.y,
            slopPx = slopPx,
            jitterPx = slopPx,
            currentLock = axis,
        )
        if (axis != LockedAxis.UNDECIDED) {
            change.consume()
            return change
        }
    }
}

/** The one route decision: armed handle → RESIZE, tile body or transfer → MOVE, else NONE. */
private fun chooseRouteOnce(down: DragDown): DownDragRoute =
    transferDragRoute(
        isTransfer = down.isTransfer,
        arrowHit = down.inHandle,
        armed = down.armedAtDown,
        hasTile = true,
    )

/** Starts the chosen operation; a transfer drag selects its tile first. */
private fun beginDrag(host: HomeDragHost, down: DragDown, route: DownDragRoute): Boolean {
    if (down.isTransfer) host.editState = host.editState.selectOnTap(down.tile.itemId)
    host.editState = when (route) {
        DownDragRoute.RESIZE -> host.editState.beginResize(down.tile, host.hit.tiles, host.hit.metrics)
        else -> host.editState.beginMove(
            tile = down.tile,
            placements = host.hit.tiles,
            metrics = host.hit.metrics,
            grabOffsetXPx = down.grabOffsetXPx,
            grabOffsetYPx = down.grabOffsetYPx,
        )
    }
    return host.editState.isDragging
}

private fun dragWinner(route: DownDragRoute): HomeGestureWinner = resolveHomeGesture(
    isOnHandle = route == DownDragRoute.RESIZE,
    isActiveResize = route == DownDragRoute.RESIZE,
    isActiveMove = route == DownDragRoute.MOVE,
)

/**
 * Updates the already-chosen operation until lift, cancel or loss of the
 * drag. The route is never re-tested here.
 */
private suspend fun AwaitPointerEventScope.trackDrag(
    host: HomeDragHost,
    pointerId: PointerId,
    route: DownDragRoute,
    slop: PointerInputChange,
): DragExit {
    if (route == DownDragRoute.MOVE) updateDrag(host, route, slop.position)
    while (true) {
        // Scroll is never compared with a remembered value: every update maps the
        // pointer through the current scroll, so a scroll from any source simply
        // moves the candidate. Pager state is never consulted.
        val change = awaitPointerEvent().changes.find { it.id == pointerId }
        when (classifyPointerEnd(change == null, change?.pressed == true, change?.isConsumed == true)) {
            PointerEndDecision.CANCEL -> return DragExit.CANCELLED
            PointerEndDecision.LIFT -> return DragExit.LIFTED
            PointerEndDecision.CONTINUE -> Unit
        }
        if (change == null) return DragExit.STALE
        change.consume()
        if (!ownsDrag(host.editState, route)) return DragExit.STALE
        updateDrag(host, route, change.position)
    }
}

/**
 * Idle (non-drag) pager arbitration: observes the gesture without consuming,
 * holds the pager for a tile-origin touch and tracks the axis lock so vertical
 * scroll and the pager never fight over a background swipe. A tile touch while
 * editing belongs to [homeDragGesture], so this loop yields for it.
 */
internal suspend fun AwaitPointerEventScope.homePagerGesture(
    pager: HomePagerGate,
    hit: () -> IdleHitSnapshot,
    isEditing: () -> Boolean,
) {
    val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return
    val start = down.position
    val slopPx = viewConfiguration.touchSlop.toFloat()
    val snapshot = hit()
    val downTile = snapshot.metrics.tileAt(
        snapshot.tiles,
        start.x,
        snapshot.metrics.contentY(start.y, snapshot.scrollYPx),
    )
    if (isEditing() && downTile != null) return
    pager.claim(
        resolveHomeGesture(
            isSelectDrag = isEditing() && downTile != null,
            isTap = downTile != null,
            isBackground = downTile == null,
        ),
    )
    var lock = LockedAxis.UNDECIDED
    while (true) {
        val change = awaitPointerEvent().changes.find { it.id == down.id }
        if (change == null || !change.pressed) break
        lock = classifyHomeAxis(
            dxPx = change.position.x - start.x,
            dyPx = change.position.y - start.y,
            slopPx = slopPx,
            jitterPx = slopPx,
            currentLock = lock,
        )
        pager.lock(lock)
    }
    if (lock != LockedAxis.UNDECIDED) pager.resetAxis()
    pager.release()
}

private fun ownsDrag(state: HomeEditState, route: DownDragRoute): Boolean =
    if (route == DownDragRoute.MOVE) state.moveDrag != null else state.resizeDrag != null

private fun updateDrag(host: HomeDragHost, route: DownDragRoute, pointer: Offset) {
    val state = host.editState
    if (route == DownDragRoute.MOVE) host.onMovePointer(pointer.x, pointer.y)
    host.editState = if (route == DownDragRoute.MOVE) {
        state.moveTo(pointer.x, pointer.y, host.scrollYPx.toFloat())
    } else {
        state.resizeTo(pointer.x, pointer.y, host.scrollYPx.toFloat())
    }
}

/**
 * A2-S1 down-snapshot drag route: decided once past slop, never switched mid-gesture.
 *
 * - [RESIZE]: arrow-origin drag while the selection is armed. The caller gates
 *   `beginResize` on this route, so an unarmed arrow can never resize.
 * - [MOVE]: body-origin drag (armed or not). The move candidate keeps the span
 *   snapshotted at gesture start; the caller clears the arm.
 * - [NONE]: unarmed arrow-origin (or no tile at all): neither move nor resize,
 *   zero writes, no session progress.
 */
internal enum class DownDragRoute { RESIZE, MOVE, NONE }

/**
 * Routes a past-slop drag once from the down snapshot.
 *
 * The arrow region wins over the body: an arrow hit routes [DownDragRoute.RESIZE]
 * only when [armed], otherwise [DownDragRoute.NONE] (an unarmed arrow-drag never
 * resizes and never falls back to move). A non-arrow tile hit routes
 * [DownDragRoute.MOVE] regardless of [armed] (the caller disarms). No tile at
 * all routes [DownDragRoute.NONE]. Pure math-free predicate, framework-free and
 * unit-testable; legality stays owned by `core:grid`.
 */
internal fun routeDownDrag(
    arrowHit: Boolean,
    armed: Boolean,
    hasTile: Boolean,
): DownDragRoute = when {
    arrowHit && armed -> DownDragRoute.RESIZE
    arrowHit -> DownDragRoute.NONE
    hasTile -> DownDragRoute.MOVE
    else -> DownDragRoute.NONE
}

/**
 * A3 transfer-aware past-slop route: a transfer drag routes direct
 * [DownDragRoute.MOVE] with no resize interpretation (the selected-tile
 * resize-arm snapshot never authorizes another tile); otherwise delegates to
 * [routeDownDrag]. Pure, framework-free and unit-testable.
 */
internal fun transferDragRoute(
    isTransfer: Boolean,
    arrowHit: Boolean,
    armed: Boolean,
    hasTile: Boolean,
): DownDragRoute = if (isTransfer) {
    DownDragRoute.MOVE
} else {
    routeDownDrag(arrowHit = arrowHit, armed = armed, hasTile = hasTile)
}

/**
 * Pointer-loop end decision for the MOVE/RESIZE drag loops (B3 P2).
 *
 * Pure, framework-free classification of a loop-exit observation so the
 * null/consumed branches are unit-testable without a gesture scope:
 * - a lost pointer id (null await) is always [CANCEL], never a lift;
 * - an observed lift (non-null, not consumed, no longer pressed) is [LIFT];
 * - a consumed change is [CANCEL], never a lift;
 * - an unconsumed pressed change is [CONTINUE] (keep looping).
 * Lift takes precedence over consumed (matches the loop order: the pressed
 * check runs before the consumed check), so a lifted-but-consumed change
 * still completes toward commit gating instead of cancelling.
 */
internal enum class PointerEndDecision { CONTINUE, LIFT, CANCEL }

internal fun classifyPointerEnd(
    changeNull: Boolean,
    pressed: Boolean,
    consumed: Boolean,
): PointerEndDecision = when {
    changeNull -> PointerEndDecision.CANCEL
    !pressed -> PointerEndDecision.LIFT
    consumed -> PointerEndDecision.CANCEL
    else -> PointerEndDecision.CONTINUE
}
