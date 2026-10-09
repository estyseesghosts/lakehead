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
    /** True for a launchable tile touched before edit mode: the long-press may pick it up. */
    val awaitsLongPress: Boolean = false,
)

/** Pure: true once the pointer is farther than [slopPx] from its down point, in any direction. */
internal fun exceedsTouchSlop(dxPx: Float, dyPx: Float, slopPx: Float): Boolean =
    dxPx * dxPx + dyPx * dyPx > slopPx * slopPx

/**
 * Converts a pointer Y read inside the scrolled content node to viewport-local Y.
 *
 * The drag/idle modifiers are chained after `verticalScroll`, so `change.position`
 * is already in content space (it includes the scroll offset). Downstream math
 * ([HomeGridMetrics.contentY], [MoveDragMath], [HomeEditState.moveTo]/`resizeTo`,
 * [MoveEdgePointer], `HomeAutoScroll`) expects viewport-local Y and applies scroll
 * itself exactly once. Subtract here so the boundary holds: viewport + scroll =
 * the node-local content Y.
 */
internal fun nodeLocalToViewportY(nodeYPx: Float, scrollYPx: Float): Float = nodeYPx - scrollYPx

/**
 * The edit-drag gesture, read top to bottom:
 * down → hit test → (long-press pickup | wait for slop) → choose MOVE or
 * RESIZE once → update that operation until up/cancel → emit one [HomeDragEnd].
 *
 * Runs as the body of `awaitEachGesture`. A move starts either with plain
 * radial touch slop on a tile touched in edit mode, or immediately, with no
 * slop, when the long-press that enters edit mode fires with the finger still
 * down on a tile.
 */
internal suspend fun AwaitPointerEventScope.homeDragGesture(
    host: HomeDragHost,
    handleSizePx: Int,
) {
    val down = awaitDownTile(host, handleSizePx) ?: return
    // Before edit mode the idle pager loop owns the hold; only a pickup claims it.
    val pickedUp = if (down.awaitsLongPress) awaitLongPressPickup(host, down) ?: return else null
    host.pager.claim(HomeGestureWinner.SELECT_DRAG)
    try {
        val slop = pickedUp ?: awaitSlop(down)?.position ?: return
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
    // Gesture modifiers sit after `verticalScroll`, so `start.y` is node-local
    // content space (scroll already included). Convert once to viewport-local;
    // `contentY(viewport, scroll)` below then recovers the same content Y without
    // double-counting scroll.
    val scrollYPx = host.scrollYPx.toFloat()
    val viewportY = nodeLocalToViewportY(start.y, scrollYPx)
    val edit = host.editState
    val snapshot = host.hit
    val metrics = snapshot.metrics
    val contentY = metrics.contentY(viewportY, scrollYPx)
    val downTile = metrics.tileAt(snapshot.tiles, start.x, contentY)
    val selectedTile = edit.selectedTileId?.let { id -> snapshot.tiles.firstOrNull { it.itemId == id } }
    val selectedInHandle = selectedTile?.let {
        isInHandle(start.x, contentY, metrics.pixelFrame(it.position, it.span), handleSizePx)
    } == true
    if (!edit.isEditing && downTile?.isLaunchable != true) return null
    if (edit.isEditing && downTile == null && !selectedInHandle) return null
    val tile = downTile ?: selectedTile ?: return null
    val bounds = metrics.pixelFrame(tile.position, tile.span)
    return DragDown(
        awaitsLongPress = !edit.isEditing,
        pointerId = first.id,
        position = start,
        tile = tile,
        isTransfer = isEditTransferDrag(edit, downTile?.itemId),
        armedAtDown = edit.isResizeArmedFor(tile.itemId),
        inHandle = isInHandle(start.x, contentY, bounds, handleSizePx),
        grabOffsetXPx = start.x - bounds.left.toFloat(),
        grabOffsetYPx = contentY - bounds.top.toFloat(),
    )
}

/**
 * Waits until the pressed pointer passes drag slop in any direction. Dragging a
 * tile has no axis preference (scroll-versus-pager arbitration is
 * [classifyHomeAxis]'s job, for the idle gesture only). Null on release before slop.
 */
private suspend fun AwaitPointerEventScope.awaitSlop(
    down: DragDown,
): PointerInputChange? {
    val slopPx = viewConfiguration.touchSlop.toFloat()
    while (true) {
        val change = awaitPointerEvent().changes.find { it.id == down.pointerId }
        if (change == null || !change.pressed) return null
        if (exceedsTouchSlop(
                dxPx = change.position.x - down.position.x,
                dyPx = change.position.y - down.position.y,
                slopPx = slopPx,
            )
        ) {
            change.consume()
            return change
        }
    }
}

/**
 * Pre-edit pointer on a launchable tile: if the finger stays within slop for the
 * long-press timeout, enters edit mode with that tile selected and returns the
 * latest change so the move starts at once, no slop. Null when the finger
 * lifted, vanished or moved past slop first (a tap or a scroll, not a pickup).
 * Nothing is consumed before the pickup, so taps and scrolling are unaffected.
 */
private suspend fun AwaitPointerEventScope.awaitLongPressPickup(
    host: HomeDragHost,
    down: DragDown,
): Offset? {
    val slopPx = viewConfiguration.touchSlop.toFloat()
    var latest = down.position
    var aborted = false
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val change = awaitPointerEvent().changes.find { it.id == down.pointerId }
            if (change == null || !change.pressed ||
                exceedsTouchSlop(
                    dxPx = change.position.x - down.position.x,
                    dyPx = change.position.y - down.position.y,
                    slopPx = slopPx,
                )
            ) {
                aborted = true
                return@withTimeoutOrNull
            }
            latest = change.position
        }
    }
    if (aborted) return null
    // The tile's own long-press selects the same tile at the same moment; entering
    // here too is idempotent and makes the pickup independent of that race.
    val edit = host.editState
    if (!edit.isEditing || edit.selectedTileId != down.tile.itemId) {
        host.editState = edit.enterOnLongPress(down.tile.itemId)
    }
    return latest
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
    start: Offset,
): DragExit {
    if (route == DownDragRoute.MOVE) updateDrag(host, route, start)
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
    // Same node-local → viewport conversion as the drag down path: `start.y`
    // already includes scroll, so `contentY(viewport, scroll)` recovers content
    // without double-counting.
    val startViewportY = nodeLocalToViewportY(start.y, snapshot.scrollYPx)
    val downTile = snapshot.metrics.tileAt(
        snapshot.tiles,
        start.x,
        snapshot.metrics.contentY(startViewportY, snapshot.scrollYPx),
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
    // `pointer` is node-local (scrolled content space). Convert once to
    // viewport-local so `onMovePointer` holds a true viewport Y for the edge
    // ticker and `moveTo`/`resizeTo` apply scroll exactly once. Read scroll once
    // so the conversion and the mapping use the same value.
    val scrollYPx = host.scrollYPx.toFloat()
    val viewportY = nodeLocalToViewportY(pointer.y, scrollYPx)
    val state = host.editState
    if (route == DownDragRoute.MOVE) host.onMovePointer(pointer.x, viewportY)
    host.editState = if (route == DownDragRoute.MOVE) {
        state.moveTo(pointer.x, viewportY, scrollYPx)
    } else {
        state.resizeTo(pointer.x, viewportY, scrollYPx)
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
