package me.foxtails.lakehead.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.ResizeCandidateOverlay
import me.foxtails.lakehead.core.designsystem.ResizeHandle
import me.foxtails.lakehead.core.designsystem.SpanChild
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.TileBounds
import me.foxtails.lakehead.core.designsystem.WindowedSpanContainer
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Viewport padding/gap inputs for the home canvas grid, in pixels.
 *
 * Values are resolved by the caller outside composition and passed through to
 * [SquareCellGeometry.fromViewport] unchanged. All values are non-negative pixel counts;
 * zero means edge-to-edge/touching. The visual column count itself comes from the loaded
 * render state's adapted `columns.count` and is never clamped here.
 */
data class HomeCanvasViewportStyle(
    val horizontalPaddingPx: Int = 0,
    val columnGapPx: Int = 0,
    val rowGapPx: Int = 0,
) {
    init {
        require(horizontalPaddingPx >= 0) { "horizontalPaddingPx must be non-negative" }
        require(columnGapPx >= 0) { "columnGapPx must be non-negative" }
        require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
    }
}

/**
 * Internal scrolling grid arrangement for one loaded home.
 *
 * - Geometry comes from `BoxWithConstraints`/density measured width/height plus
 *   [SquareCellGeometry.fromViewport] with the loaded state's adapted
 *   `columns.count` used exactly (five is only the normal-phone reference, never a clamp).
 * - Transient scroll state is keyed by the loaded `requestedHomeId` so each home keeps its
 *   own position and a home switch resets scrolling.
 * - The visible [GridRowWindow] is derived from the scroll offset, measured viewport height,
 *   and row stride with checked/saturating `Long` arithmetic; the window plus [overscanRows]
 *   is passed to [WindowedSpanContainer], which owns overscan expansion and sparse selection.
 * - The loaded [GridVerticalExtent] is used only to derive a safe minimum content height;
 *   no second extent is computed here.
 * - Tiles map to [SpanChild] in persisted order; icons come only from [iconsByIdentity]
 *   via [Map.get] on the exact [AppIdentity]. No decode, catalogue/platform lookup, or
 *   database access occurs in any composable.
 * - Transient editing is hoisted here: long-press on a launchable tile enters a
 *   [HomeEditSession.Active] session in MOVE mode, vertical scroll is disabled while active,
 *   pointer drags update the candidate via [MoveDragMath] with validity from `core:grid`, a
 *   non-interactive outline marks the candidate, back cancels, and drag end commits via
 *   [onMoveTile] when the candidate differs from the origin. A resize handle on the
 *   bottom-right corner switches to RESIZE mode; handle drags update the candidate span
 *   via [ResizeDragMath] and [RenderableSpans.quantize], and drag end commits via
 *   [onResizeTile] when the span differs from the origin span. Persistence itself stays
 *   with the caller: this grid only reports the committed [ItemId] and [LogicalPosition] or
 *   [LogicalSpan].
 */
@Composable
internal fun HomeCanvasGrid(
    loaded: HomeRenderState.Loaded,
    iconsByIdentity: HomeCanvasIcons,
    onLaunchApp: (AppIdentity) -> Unit,
    overscanRows: Long,
    viewportStyle: HomeCanvasViewportStyle,
    modifier: Modifier = Modifier,
    onMoveTile: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
) {
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
    if (loaded.tiles.isEmpty()) {
        BasicText(
            text = "Home is empty",
            modifier = modifier,
        )
        return
    }
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val availableWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val availableHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(0)
        val geometry = SquareCellGeometry.fromViewport(
            availableWidthPx = availableWidthPx,
            horizontalPaddingPx = viewportStyle.horizontalPaddingPx,
            columnGapPx = viewportStyle.columnGapPx,
            rowGapPx = viewportStyle.rowGapPx,
            columnCount = loaded.columns.count,
        )
        // Centered horizontal origin: column 0's left edge, splitting the leftover
        // (available width minus the grid width) evenly so left/right slack stays
        // equal within 1px. Geometry formula itself is unchanged.
        val originX = centeredOriginX(
            availableWidthPx = availableWidthPx,
            geometry = geometry,
        )
        key(loaded.requestedHomeId) {
            val scrollState = rememberScrollState()
            var editSession by remember { mutableStateOf<HomeEditSession>(HomeEditSession.Idle) }
            val columns = loaded.columns.count
            val rows = loaded.extent.totalScrollableRows.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val active = editSession as? HomeEditSession.Active
            BackHandler(enabled = active != null) {
                editSession = editSession.cancel()
            }
            val rowStridePx: Long =
                geometry.cellSizePx.toLong() + geometry.rowGapPx.toLong()
            val window = visibleWindowFor(
                scrollPx = scrollState.value,
                viewportHeightPx = availableHeightPx,
                rowStridePx = rowStridePx,
                overscanRows = overscanRows,
            )
            // Logical row origin the windowed container rebases pixel frames around:
            // content y=0 corresponds to this row. This MUST stay fixed at row 0 and
            // must not track the scroll offset: deriving it from the scroll-derived
            // window feeds the rebased scroll position back into the layout origin,
            // shifting tiles by one extra stride per scrolled stride and driving the
            // selection window away from the visible rows once scrolled past overscan.
            // With a fixed origin, content coordinates are absolute: scrollPx maps to
            // absolute row scrollPx/stride, minHeightPx is the absolute content height,
            // and overlays/drag math share the same origin through this value.
            val rowOrigin = HOME_CANVAS_ROW_ORIGIN
            val minHeightPx = contentMinHeightPx(
                extent = loaded.extent,
                cellSizePx = geometry.cellSizePx,
                rowGapPx = geometry.rowGapPx,
            ).coerceAtLeast(availableHeightPx)
            val children: List<SpanChild<ItemId>> = loaded.tiles.map { tile ->
                val iconBitmap = iconsByIdentity[tile.appIdentity]
                SpanChild(
                    placement = GridPlacement(
                        id = tile.itemId,
                        position = GridPosition(tile.position.column, tile.position.row),
                        span = GridSpan(tile.span.columns, tile.span.rows),
                    ),
                    content = {
                        HomeCanvasTile(
                            tile = tile,
                            iconBitmap = iconBitmap,
                            onLaunchApp = onLaunchApp,
                            isEditActive = active != null,
                            activeItemId = active?.itemId,
                            onEnterEdit = { entered ->
                                editSession = editSession.enterIfLaunchable(
                                    entered,
                                    loaded.tiles,
                                    columns,
                                    rows,
                                )
                            },
                        )
                    },
                )
            }
            // Latest session for drag callbacks: the pointerInput key stays stable for the
            // session lifetime (item id) so in-flight drags survive candidate recompositions.
            val sessionState = rememberUpdatedState(editSession)
            // Tap on the grid background (outside any tile) cancels an active edit session.
            // Tiles use combinedClickable which consumes their own taps; this handler only
            // fires for taps on empty grid area.
            val tapCancelModifier = Modifier.pointerInput(active?.itemId) {
                if (active?.itemId == null) return@pointerInput
                detectTapGestures(
                    onTap = { editSession = sessionState.value.cancel() },
                )
            }
            // Unified move/resize drag handler on the parent Box. The resize handle is
            // visual-only; this handler hit-tests the pointer position against the handle
            // corner once at gesture start (after slop) and routes to RESIZE or MOVE.
            // A single detector avoids the slop competition that made a separate
            // handle detector unreachable. Keyed on the active item id (stable for the
            // session lifetime) so in-flight drags survive candidate recompositions;
            // scroll is disabled while active so scrollState/geometry stay fixed.
            //
            // This handler is only installed while a session is active (entered via the
            // tile's long-press). The pointer is therefore already down from the
            // long-press when this block starts, so it must NOT use awaitFirstDown()
            // (which waits for a fresh down that never arrives). Instead the first
            // awaitPointerEvent() observes the already-pressed pointer and tracks it
            // past touch slop for a continuous press-hold-drag.
            val dragModifier = Modifier.pointerInput(active?.itemId) {
                awaitEachGesture {
                    // Pointer is already down from the long-press that entered edit mode.
                    // Observe the current pressed pointer instead of waiting for a new down.
                    val firstEvent = awaitPointerEvent()
                    val down = firstEvent.changes.firstOrNull { it.pressed }
                        ?: return@awaitEachGesture
                    val downId = down.id
                    val startPos = down.position
                    val sessionAtDown =
                        sessionState.value as? HomeEditSession.Active ?: return@awaitEachGesture
                    // Snapshot the origin corner at gesture start (fixed anchor, not the
                    // live candidate bounds which move during the drag).
                    val originBounds = TileBounds.bounds(
                        position = GridPosition(
                            sessionAtDown.origin.column,
                            sessionAtDown.origin.row,
                        ),
                        span = GridSpan(
                            sessionAtDown.originSpan.columns,
                            sessionAtDown.originSpan.rows,
                        ),
                        geometry = geometry,
                        originX = originX,
                    )
                    val handleSizePx = with(density) { 48.dp.roundToPx() }
                    // Hit-test in content-local coords: the pointer position observed
                    // inside the scrolling content (after verticalScroll in the
                    // modifier chain) already includes the scroll offset, so no
                    // scroll addition is needed. Scroll is disabled while a
                    // session is active.
                    // startPos is the pointer's position at the first observed event
                    // after entering edit mode — effectively the hold position since
                    // the finger is stationary after the long-press.
                    val startInHandle =
                        isInHandle(startPos.x, startPos.y, originBounds, handleSizePx)
                    // Wait until the already-pressed pointer moves past touch slop.
                    // Release before slop means no drag (a tap; tapCancel handles it).
                    val touchSlopPx = viewConfiguration.touchSlop
                    var slop: PointerInputChange? = null
                    while (slop == null) {
                        val nextEvent = awaitPointerEvent()
                        val nextChange = nextEvent.changes.find { it.id == downId }
                        if (nextChange == null || !nextChange.pressed) {
                            return@awaitEachGesture
                        }
                        val dx = nextChange.position.x - startPos.x
                        val dy = nextChange.position.y - startPos.y
                        if (dx * dx + dy * dy > touchSlopPx * touchSlopPx) {
                            nextChange.consume()
                            slop = nextChange
                        }
                    }
                    val slopChange = slop ?: return@awaitEachGesture
                    // Slop-position check: a press that starts on the body but crosses
                    // slop inside the handle corner still routes to RESIZE, so
                    // body-vs-handle competition is decided at drag start, not only
                    // at the hold position.
                    val slopInHandle = isInHandle(
                        slopChange.position.x,
                        slopChange.position.y,
                        originBounds,
                        handleSizePx,
                    )
                    val isOnHandle = startInHandle || slopInHandle
                    if (isOnHandle) {
                        // RESIZE mode: corner-anchored span updates.
                        editSession = sessionState.value.beginResize()
                        var current: PointerInputChange = slopChange
                        while (true) {
                            val next = awaitDragOrCancellation(current.id) ?: break
                            next.consume()
                            current = next
                            if (sessionState.value as? HomeEditSession.Active == null) break
                            val pointerXPx = next.position.x
                            val pointerYPx = next.position.y
                            val rawSpan = ResizeDragMath.rawSpan(
                                pointerXPx = pointerXPx,
                                pointerYPx = pointerYPx,
                                originPosition = sessionAtDown.origin,
                                cellSizePx = geometry.cellSizePx.toFloat(),
                                columnGapPx = geometry.columnGapPx.toFloat(),
                                rowGapPx = geometry.rowGapPx.toFloat(),
                                originXPx = originX.toFloat(),
                            )
                            val quantized = RenderableSpans.quantize(rawSpan, columns)
                            editSession = sessionState.value.updateResizeCandidate(
                                quantized,
                                loaded.tiles,
                                columns,
                                rows,
                            )
                        }
                        val final = sessionState.value as? HomeEditSession.Active
                        if (final != null && sessionState.value.hasResized()) {
                            onResizeTile(final.itemId, final.candidateSpan)
                            // Keep session active in MOVE with the committed span as origin.
                            editSession = HomeEditSession.Active(
                                itemId = final.itemId,
                                origin = final.origin,
                                originSpan = final.candidateSpan,
                                candidatePosition = final.origin,
                                candidateSpan = final.candidateSpan,
                                candidateValidity = CandidateValidity.VALID,
                                mode = EditMode.MOVE,
                            )
                        } else if (final != null) {
                            // No change or invalid — snap back, stay in MOVE with original origin.
                            editSession = HomeEditSession.Active(
                                itemId = final.itemId,
                                origin = final.origin,
                                originSpan = final.originSpan,
                                candidatePosition = final.origin,
                                candidateSpan = final.originSpan,
                                candidateValidity = CandidateValidity.VALID,
                                mode = EditMode.MOVE,
                            )
                        } else {
                            editSession = HomeEditSession.Idle
                        }
                    } else {
                        // MOVE mode: pointer position maps to a candidate cell.
                        // A drag that later enters the fixed handle corner promotes
                        // live to RESIZE so a body-started press can still resize
                        // without lifting the finger.
                        val slopCell = MoveDragMath.cellForPixel(
                            xPx = slopChange.position.x,
                            yPx = slopChange.position.y,
                            cellSizePx = geometry.cellSizePx.toFloat(),
                            columnGapPx = geometry.columnGapPx.toFloat(),
                            rowGapPx = geometry.rowGapPx.toFloat(),
                            windowFirstRow = rowOrigin,
                            originXPx = originX.toFloat(),
                        )
                        editSession = sessionState.value.updateMoveCandidate(
                            slopCell,
                            loaded.tiles,
                            columns,
                            rows,
                        )
                        var current: PointerInputChange = slopChange
                        var promotedToResize = false
                        while (!promotedToResize) {
                            val next = awaitDragOrCancellation(current.id) ?: break
                            next.consume()
                            current = next
                            if (sessionState.value as? HomeEditSession.Active == null) break
                            if (isInHandle(
                                    next.position.x,
                                    next.position.y,
                                    originBounds,
                                    handleSizePx,
                                )
                            ) {
                                editSession = sessionState.value.beginResize()
                                val rawSpan = ResizeDragMath.rawSpan(
                                    pointerXPx = next.position.x,
                                    pointerYPx = next.position.y,
                                    originPosition = sessionAtDown.origin,
                                    cellSizePx = geometry.cellSizePx.toFloat(),
                                    columnGapPx = geometry.columnGapPx.toFloat(),
                                    rowGapPx = geometry.rowGapPx.toFloat(),
                                    originXPx = originX.toFloat(),
                                )
                                val quantized = RenderableSpans.quantize(rawSpan, columns)
                                editSession = sessionState.value.updateResizeCandidate(
                                    quantized,
                                    loaded.tiles,
                                    columns,
                                    rows,
                                )
                                promotedToResize = true
                                break
                            }
                            val cell = MoveDragMath.cellForPixel(
                                xPx = next.position.x,
                                yPx = next.position.y,
                                cellSizePx = geometry.cellSizePx.toFloat(),
                                columnGapPx = geometry.columnGapPx.toFloat(),
                                rowGapPx = geometry.rowGapPx.toFloat(),
                                windowFirstRow = rowOrigin,
                                originXPx = originX.toFloat(),
                            )
                            editSession = sessionState.value.updateMoveCandidate(
                                cell,
                                loaded.tiles,
                                columns,
                                rows,
                            )
                        }
                        if (promotedToResize) {
                            // Continue tracking as a resize drag from the promotion point.
                            while (true) {
                                val next = awaitDragOrCancellation(current.id) ?: break
                                next.consume()
                                current = next
                                if (sessionState.value as? HomeEditSession.Active == null) break
                                val rawSpan = ResizeDragMath.rawSpan(
                                    pointerXPx = next.position.x,
                                    pointerYPx = next.position.y,
                                    originPosition = sessionAtDown.origin,
                                    cellSizePx = geometry.cellSizePx.toFloat(),
                                    columnGapPx = geometry.columnGapPx.toFloat(),
                                    rowGapPx = geometry.rowGapPx.toFloat(),
                                    originXPx = originX.toFloat(),
                                )
                                val quantized = RenderableSpans.quantize(rawSpan, columns)
                                editSession = sessionState.value.updateResizeCandidate(
                                    quantized,
                                    loaded.tiles,
                                    columns,
                                    rows,
                                )
                            }
                            val final = sessionState.value as? HomeEditSession.Active
                            if (final != null && sessionState.value.hasResized()) {
                                onResizeTile(final.itemId, final.candidateSpan)
                                // Keep session active in MOVE with the committed span as origin.
                                editSession = HomeEditSession.Active(
                                    itemId = final.itemId,
                                    origin = final.origin,
                                    originSpan = final.candidateSpan,
                                    candidatePosition = final.origin,
                                    candidateSpan = final.candidateSpan,
                                    candidateValidity = CandidateValidity.VALID,
                                    mode = EditMode.MOVE,
                                )
                            } else if (final != null) {
                                // No change or invalid — snap back, stay in MOVE.
                                editSession = HomeEditSession.Active(
                                    itemId = final.itemId,
                                    origin = final.origin,
                                    originSpan = final.originSpan,
                                    candidatePosition = final.origin,
                                    candidateSpan = final.originSpan,
                                    candidateValidity = CandidateValidity.VALID,
                                    mode = EditMode.MOVE,
                                )
                            } else {
                                editSession = HomeEditSession.Idle
                            }
                        } else {
                            val final = sessionState.value as? HomeEditSession.Active
                            if (final != null && sessionState.value.hasMoved()) {
                                onMoveTile(final.itemId, final.candidatePosition)
                            }
                            editSession = HomeEditSession.Idle
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .verticalScroll(scrollState, enabled = active == null)
                    .then(tapCancelModifier)
                    .then(if (active != null) dragModifier else Modifier)
                    .testTag(HOME_CANVAS_GRID_TAG),
            ) {
                WindowedSpanContainer(
                    children = children,
                    window = window,
                    overscanRows = overscanRows,
                    geometry = geometry,
                    originX = originX,
                    rowOrigin = rowOrigin,
                    minHeightPx = minHeightPx,
                )
                if (active != null && active.mode == EditMode.MOVE) {
                    MoveCandidateOverlay(
                        active = active,
                        geometry = geometry,
                        rowOrigin = rowOrigin,
                        originX = originX,
                    )
                }
                if (active != null && active.mode == EditMode.RESIZE) {
                    ResizeCandidateOverlay(
                        originPosition = GridPosition(active.origin.column, active.origin.row),
                        candidateSpan = GridSpan(active.candidateSpan.columns, active.candidateSpan.rows),
                        geometry = geometry,
                        rowOrigin = rowOrigin,
                        isValid = active.candidateValidity == CandidateValidity.VALID,
                        originX = originX,
                    )
                }
                // Resize handle overlay: positioned over the active tile's bottom-right
                // corner INSIDE the scrolling content so it shares the tiles/overlays
                // coordinate space and tracks scroll.
                if (active != null) {
                    val activeTile = loaded.tiles.find { it.itemId == active.itemId }
                    if (activeTile != null) {
                        // Handle tracks the live candidate span (not the persisted span)
                        // so it stays on the growing/shrinking corner during a resize drag.
                        val tileBounds = TileBounds.bounds(
                            position = GridPosition(active.origin.column, active.origin.row),
                            span = GridSpan(active.candidateSpan.columns, active.candidateSpan.rows),
                            geometry = geometry,
                            originX = originX,
                        )
                        val handleTopPx = tileBounds.bottom - with(density) { 48.dp.roundToPx() }
                        val handleLeftPx = tileBounds.right - with(density) { 48.dp.roundToPx() }
                        val rebaseTop = rebasedOverlayTop(handleTopPx, rowOrigin, rowStridePx)
                        // Visual-only handle: the parent Box's unified drag handler
                        // hit-tests this corner and routes to RESIZE mode. No gesture
                        // detector here (a second detector loses the slop race).
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(handleLeftPx, rebaseTop) }
                                .size(48.dp),
                        ) {
                            ResizeHandle()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Centered horizontal origin (column 0 left edge) for the home canvas grid.
 *
 * Rule: `originX = (availableWidthPx - (count * cell + (count - 1) * gap)) / 2`,
 * computed with [Long] intermediates and flooring division, so the leftover slack
 * is split evenly and left/right slack stays equal within 1px. The geometry formula
 * itself is unchanged; this only centers the already-derived grid.
 */
internal fun centeredOriginX(
    availableWidthPx: Int,
    geometry: SquareCellGeometry,
): Int {
    require(availableWidthPx > 0) { "availableWidthPx must be positive" }
    val count = geometry.columnCount.toLong()
    val gridWidthPx = count * geometry.cellSizePx.toLong() +
        (count - 1L) * geometry.columnGapPx.toLong()
    val originLong = Math.floorDiv(availableWidthPx.toLong() - gridWidthPx, 2L)
    return originLong.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Derives the visible logical row window from scroll/viewport/stride inputs.
 *
 * `firstRow = scrollPx / rowStridePx` (floor) is an ABSOLUTE row: the canvas content
 * origin is fixed at row 0 ([HOME_CANVAS_ROW_ORIGIN]), so the scroll offset measured
 * from the content top maps directly to absolute rows. The visible count covers the
 * viewport height with ceiling division plus one extra row for a partially visible
 * boundary row. The upper boundary saturates at [Long.MAX_VALUE] instead of wrapping.
 * Overscan is not applied here; the caller passes it separately to
 * [WindowedSpanContainer].
 */
internal fun visibleWindowFor(
    scrollPx: Int,
    viewportHeightPx: Int,
    rowStridePx: Long,
    overscanRows: Long,
): GridRowWindow {
    require(scrollPx >= 0) { "scrollPx must be non-negative" }
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
    val firstRow: Long = scrollPx.toLong() / rowStridePx
    val visibleCount: Long = if (viewportHeightPx == 0) {
        1L
    } else {
        val heightLong = viewportHeightPx.toLong()
        // ceil(height / stride) + 1, with checked arithmetic.
        val ceil = Math.addExact(heightLong, Math.subtractExact(rowStridePx, 1L)) / rowStridePx
        Math.addExact(ceil, 1L)
    }
    val lastExclusive: Long = saturatingAdd(firstRow, visibleCount)
    return GridRowWindow(firstRow = firstRow, lastRowExclusive = lastExclusive)
}

/**
 * Derives a safe minimum content height in pixels from the loaded extent only.
 *
 * Height is `totalScrollableRows * cellSizePx + (totalScrollableRows - 1) * rowGapPx`,
 * measured in ABSOLUTE content coordinates: the canvas origin is fixed at row 0
 * ([HOME_CANVAS_ROW_ORIGIN]), so no origin offset is subtracted here. Overflow or
 * results beyond [Int] saturate to [Int.MAX_VALUE] instead of wrapping; no
 * second extent policy is computed here.
 */
internal fun contentMinHeightPx(
    extent: GridVerticalExtent,
    cellSizePx: Int,
    rowGapPx: Int,
): Int {
    require(cellSizePx > 0) { "cellSizePx must be positive" }
    require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
    return try {
        val total = extent.totalScrollableRows
        val cells = Math.multiplyExact(total, cellSizePx.toLong())
        val gaps = if (total <= 1L) {
            0L
        } else {
            Math.multiplyExact(Math.subtractExact(total, 1L), rowGapPx.toLong())
        }
        val height = Math.addExact(cells, gaps)
        if (height > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else height.toInt()
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
}

private fun saturatingAdd(value: Long, increment: Long): Long =
    if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment

/** Test tag on the scrolling canvas grid (testing hook only; no behavior change). */
internal const val HOME_CANVAS_GRID_TAG: String = "home-canvas-grid"

/**
 * Fixed logical row origin for the home canvas content: content y=0 is always row 0.
 *
 * The origin is deliberately NOT derived from the scroll position. The scroll offset
 * is measured from the content top, so an origin that tracks scroll would feed the
 * rebased offset back into tile placement, overlay rebasing, drag mapping, and the
 * container height, shifting tiles and losing visible-row selection once scrolled
 * past overscan. A fixed zero origin keeps every consumer in absolute coordinates.
 */
internal const val HOME_CANVAS_ROW_ORIGIN: Long = 0L

/** Test tag on the move-candidate validity outline (testing hook only; no behavior change). */
internal const val MOVE_CANDIDATE_OVERLAY_TAG: String = "move-candidate-overlay"

/**
 * Non-interactive outline marking the active move candidate cell.
 *
 * Position reuses [TileBounds.bounds] for the absolute candidate frame, rebased around the
 * windowed container's [rowOrigin] via [rebasedOverlayTop] so the outline lands on the same
 * content coordinates the tiles are placed at. A valid candidate draws a 2dp outline in
 * `scheme.primary`; any other validity draws `scheme.error`.
 */
@Composable
private fun MoveCandidateOverlay(
    active: HomeEditSession.Active,
    geometry: SquareCellGeometry,
    rowOrigin: Long,
    modifier: Modifier = Modifier,
    originX: Int = 0,
) {
    val density = LocalDensity.current
    val absolute = TileBounds.bounds(
        position = GridPosition(active.candidatePosition.column, active.candidatePosition.row),
        span = GridSpan(active.candidateSpan.columns, active.candidateSpan.rows),
        geometry = geometry,
        originX = originX,
    )
    val rowStridePx = geometry.cellSizePx.toLong() + geometry.rowGapPx.toLong()
    val topPx = rebasedOverlayTop(
        absoluteTopPx = absolute.top,
        rowOrigin = rowOrigin,
        rowStridePx = rowStridePx,
    )
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (active.candidateValidity == CandidateValidity.VALID) {
        scheme.primary
    } else {
        scheme.error
    }
    val widthDp = with(density) { absolute.width.toDp() }
    val heightDp = with(density) { absolute.height.toDp() }
    Box(
        modifier = modifier
            .offset { IntOffset(absolute.left, topPx) }
            .size(widthDp, heightDp)
            .border(2.dp, borderColor, AppTileDefaults.TileShape)
            .testTag(MOVE_CANDIDATE_OVERLAY_TAG),
    )
}

/**
 * Hit-tests a content-local pointer position against the resize handle corner.
 *
 * The handle occupies the bottom-right [handlePx] square of [originBounds]:
 * `x in [right - handlePx, right]` and `y in [bottom - handlePx, bottom]`
 * (inclusive). The bounds are the FIXED origin footprint snapshotted at gesture
 * start, never the live candidate bounds that move during the drag. Pure math,
 * framework-free, so drag routing stays unit-testable and `core:grid` legality
 * stays untouched.
 */
internal fun isInHandle(
    x: Float,
    y: Float,
    originBounds: PixelRect,
    handlePx: Int,
): Boolean {
    require(handlePx > 0) { "handlePx must be positive" }
    val handleLeft = originBounds.right - handlePx
    val handleTop = originBounds.bottom - handlePx
    return x >= handleLeft.toFloat() &&
        x <= originBounds.right.toFloat() &&
        y >= handleTop.toFloat() &&
        y <= originBounds.bottom.toFloat()
}

/**
 * Rebases an absolute [TileBounds] top edge into windowed-container content coordinates.
 *
 * The container places rows at `(row - origin) * stride`, so the overlay subtracts the same
 * origin stride from the absolute frame. Results outside [Int] saturate instead of wrapping;
 * callers treat the outline as best-effort feedback.
 */
internal fun rebasedOverlayTop(
    absoluteTopPx: Int,
    rowOrigin: Long,
    rowStridePx: Long,
): Int {
    require(absoluteTopPx >= 0) { "absoluteTopPx must be non-negative" }
    require(rowOrigin >= 0L) { "rowOrigin must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    return try {
        val rebased = Math.subtractExact(
            absoluteTopPx.toLong(),
            Math.multiplyExact(rowOrigin, rowStridePx),
        )
        rebased.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    } catch (e: ArithmeticException) {
        Int.MIN_VALUE
    }
}
