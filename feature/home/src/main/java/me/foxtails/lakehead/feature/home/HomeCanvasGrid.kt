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
import me.foxtails.lakehead.core.designsystem.RemoveButton
import me.foxtails.lakehead.core.designsystem.ResizeCandidateOverlay
import me.foxtails.lakehead.core.designsystem.ResizeHandle
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.designsystem.SpanChild
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.TileBounds
import me.foxtails.lakehead.core.designsystem.WindowedSpanContainer
import me.foxtails.lakehead.core.grid.ContentBounds
import me.foxtails.lakehead.core.grid.GridContentBounds
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRest
import me.foxtails.lakehead.core.grid.GridRestOrigin
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.SignedGridPlacement
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
 * - A remove affordance floats as a sibling overlay over the active tile in both MOVE
 *   and RESIZE modes: a 48dp [RemoveButton] (`"Remove {label}"`) positioned via
 *   [removeButtonOffset] (1×1 center pinned to the tile's top-left corner, larger
 *   spans centered on the tile). Tapping it reports [onRemoveTile] with the active
 *   [ItemId] only; the grid performs no persistence delete and issues no move/resize.
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
    onRemoveTile: (ItemId) -> Unit = {},
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
            // Signed renderer origin (G3): the logical row the windowed container
            // rebases pixel frames around plus the G2 rest pixel offset computed in
            // rebased coordinates. `rowOrigin` is the signed content `minRow` (may be
            // negative for rows above zero); `originYPx` is the G2 `gridOriginY`
            // (bias centering for short content, zero anchor for tall content).
            // Tiles use `TileBounds.signedBounds(..., originRow = rowOrigin,
            // originYPx = originYPx)`; the container is shifted by `originYPx` so its
            // rebased tiles land on the same Box coordinates as the signed overlays.
            // The visible window is absolute: scroll 0 shows `rowOrigin` at the top,
            // so it must be computed after the origin.
            val contentBounds = contentBoundsForTiles(loaded.tiles, columns)
            val rowOrigin = rendererRowOrigin(contentBounds)
            val window = visibleWindowFor(
                scrollPx = scrollState.value,
                viewportHeightPx = availableHeightPx,
                rowStridePx = rowStridePx,
                overscanRows = overscanRows,
                rowOrigin = rowOrigin,
            )
            val baseContentHeightPx = contentMinHeightPxSigned(
                extent = loaded.extent,
                cellSizePx = geometry.cellSizePx,
                rowGapPx = geometry.rowGapPx,
            )
            val rest = signedRestFor(
                viewportHeightPx = availableHeightPx,
                contentHeightPx = baseContentHeightPx,
                rowStridePx = rowStridePx,
            )
            val originYPx = rendererOriginYPx(rest)
            val originYOffsetPx = originYPx.coerceIn(
                Int.MIN_VALUE.toLong(),
                Int.MAX_VALUE.toLong(),
            ).toInt()
            val minHeightPx = baseContentHeightPx.coerceAtLeast(availableHeightPx)
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
                    val originBounds = TileBounds.signedBounds(
                        column = sessionAtDown.origin.column,
                        row = sessionAtDown.origin.row.toLong(),
                        span = GridSpan(
                            sessionAtDown.originSpan.columns,
                            sessionAtDown.originSpan.rows,
                        ),
                        geometry = geometry,
                        originX = originX,
                        originRow = rowOrigin,
                        originYPx = originYPx,
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
                                rowOrigin = rowOrigin,
                                originYPxRestPx = originYPx.toFloat(),
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
                            originYPxPx = originYPx.toFloat(),
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
                                    rowOrigin = rowOrigin,
                                    originYPxRestPx = originYPx.toFloat(),
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
                                originYPxPx = originYPx.toFloat(),
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
                                    rowOrigin = rowOrigin,
                                    originYPxRestPx = originYPx.toFloat(),
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
                    modifier = Modifier.offset { IntOffset(0, originYOffsetPx) },
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
                        originYPx = originYPx,
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
                        originYPx = originYPx,
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
                        // Signed frame: already rebased around `rowOrigin` with the G2
                        // rest offset, so its top is final Box content coordinates.
                        val tileBounds = TileBounds.signedBounds(
                            column = active.origin.column,
                            row = active.origin.row.toLong(),
                            span = GridSpan(active.candidateSpan.columns, active.candidateSpan.rows),
                            geometry = geometry,
                            originX = originX,
                            originRow = rowOrigin,
                            originYPx = originYPx,
                        )
                        val handleTopPx = tileBounds.bottom - with(density) { 48.dp.roundToPx() }
                        val handleLeftPx = tileBounds.right - with(density) { 48.dp.roundToPx() }
                        val rebaseTop = handleTopPx
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
                        // Remove affordance: sibling overlay over the active tile in
                        // both MOVE and RESIZE modes. Tracks the live candidate span
                        // (same footprint as the handle) so it stays attached during
                        // a resize drag. Tap reports onRemoveTile only; the grid
                        // performs no persistence delete and issues no move/resize.
                        val removeLabel = if (activeTile.label.isNullOrBlank()) {
                            UNAVAILABLE_LABEL
                        } else {
                            activeTile.label
                        }
                        val buttonPx = with(density) { 48.dp.roundToPx() }
                        val removeAbsolute = removeButtonOffset(
                            tileBounds = tileBounds,
                            buttonSizePx = buttonPx,
                            span = GridSpan(
                                active.candidateSpan.columns,
                                active.candidateSpan.rows,
                            ),
                        )
                        // Signed frame already carries the row origin + rest offset, so
                        // the remove anchor is final Box content coordinates.
                        val removeTop = removeAbsolute.y
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(removeAbsolute.x.coerceAtLeast(0), removeTop) }
                                .size(48.dp),
                        ) {
                            RemoveButton(
                                label = removeLabel,
                                onRemove = { onRemoveTile(active.itemId) },
                            )
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
 * `firstRow = rowOrigin + scrollPx / rowStridePx` (floor) is an absolute content
 * row: the scroll offset measured from the content top advances from the signed
 * renderer origin ([rendererRowOrigin]). Scroll 0 shows `rowOrigin` at the
 * viewport top, so content rebased around a non-zero origin (for example tiles
 * on rows 5-6 with origin 5) stays selected instead of being culled by a
 * zero-based window. The visible count covers the
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
    rowOrigin: Long = 0L,
): GridRowWindow {
    require(scrollPx >= 0) { "scrollPx must be non-negative" }
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
    val firstRow: Long = saturatingAdd(rowOrigin, scrollPx.toLong() / rowStridePx)
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
 * Signed renderer row origin (G3): the logical row the windowed container rebases
 * pixel frames around.
 *
 * Returns the signed content [ContentBounds.minRow], or `0L` when no bounds are
 * available (empty content). The value may be negative for content above row zero.
 * It must NOT track the scroll offset: the scroll position is measured from the
 * content top, so a scroll-derived origin would feed the rebased offset back into
 * placement and selection.
 */
internal fun rendererRowOrigin(bounds: ContentBounds?): Long = bounds?.minRow ?: 0L

/**
 * Signed renderer vertical pixel origin (G3, from G2): the pixel offset applied on
 * top of the rebased `(row - originRow) * stride` frame.
 *
 * Returns the G2 rest [GridRest.gridOriginY], or `0L` when no rest is available.
 * The value may be negative (tall content anchored above) or positive (short
 * content bias centering). Callers pass it as `originYPx` to
 * [TileBounds.signedBounds].
 */
internal fun rendererOriginYPx(rest: GridRest?): Long = rest?.gridOriginY ?: 0L

/**
 * Derives signed content bounds from the loaded render tiles for the renderer
 * origin.
 *
 * Maps each tile to a [SignedGridPlacement] and delegates to
 * [GridContentBounds.contentBoundsOf]; returns `null` for empty input. Column
 * legality stays owned by `core:grid`.
 */
internal fun contentBoundsForTiles(tiles: List<HomeRenderTile>, columns: Int): ContentBounds? {
    if (tiles.isEmpty()) return null
    val placements = tiles.map { tile ->
        SignedGridPlacement(
            id = tile.itemId,
            column = tile.position.column,
            row = tile.position.row,
            span = GridSpan(tile.span.columns, tile.span.rows),
        )
    }
    return GridContentBounds.contentBoundsOf(placements, columns)
}

/**
 * Computes the G2 rest for signed renderer content in rebased coordinates.
 *
 * The rest is computed with `minRow = 0` because the container already rebases
 * rows around [rendererRowOrigin]: `gridOriginY` is the container offset applied
 * on top of local `(row - origin) * stride` frames (bias centering for short
 * content, zero anchor for tall content). Returns `null` when the viewport height
 * is not positive or the rest arithmetic overflows; callers fall back to `0L`
 * via [rendererOriginYPx].
 */
internal fun signedRestFor(
    viewportHeightPx: Int,
    contentHeightPx: Int,
    rowStridePx: Long,
): GridRest? {
    if (viewportHeightPx <= 0) return null
    if (rowStridePx <= 0L) return null
    return try {
        GridRestOrigin.rest(
            viewportHeightPx = viewportHeightPx.toLong(),
            viewportTopPx = 0L,
            contentHeightPx = contentHeightPx.toLong(),
            minRow = 0L,
            cellStridePx = rowStridePx,
        )
    } catch (e: ArithmeticException) {
        null
    }
}

/**
 * Derives a safe minimum content height in pixels from the loaded extent only.
 *
 * Kept for backward compatibility; delegates to [contentMinHeightPxSigned].
 * See [contentMinHeightPxSigned] for the row-count height contract.
 */
internal fun contentMinHeightPx(
    extent: GridVerticalExtent,
    cellSizePx: Int,
    rowGapPx: Int,
): Int = contentMinHeightPxSigned(extent, cellSizePx, rowGapPx)

/**
 * Derives a safe minimum content height in pixels from the loaded extent only,
 * for use with the signed renderer origin.
 *
 * Height is `totalScrollableRows * cellSizePx + (totalScrollableRows - 1) * rowGapPx`,
 * a row-count height independent of the signed origin: rebasing around `minRow`
 * preserves row counts, so no origin offset is subtracted here. Overflow or
 * results beyond [Int] saturate to [Int.MAX_VALUE] instead of wrapping; no
 * second extent policy is computed here.
 */
internal fun contentMinHeightPxSigned(
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
    try {
        Math.addExact(value, increment)
    } catch (e: ArithmeticException) {
        if (value >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
    }

/** Test tag on the scrolling canvas grid (testing hook only; no behavior change). */
internal const val HOME_CANVAS_GRID_TAG: String = "home-canvas-grid"

/**
 * Fixed logical row origin for the home canvas content: content y=0 is always row 0.
 *
 * @deprecated The signed G3 renderer derives the origin from the content bounds via
 * [rendererRowOrigin] (signed `minRow`, may be negative) plus the G2 rest offset via
 * [rendererOriginYPx]. This constant is kept only for backward compatibility and is
 * no longer used by [HomeCanvasGrid].
 */
@Deprecated("Use rendererRowOrigin(contentBounds) with the signed content minRow.")
internal const val HOME_CANVAS_ROW_ORIGIN: Long = 0L

/** Test tag on the move-candidate validity outline (testing hook only; no behavior change). */
internal const val MOVE_CANDIDATE_OVERLAY_TAG: String = "move-candidate-overlay"

/**
 * Non-interactive outline marking the active move candidate cell.
 *
 * Position uses [TileBounds.signedBounds] with the signed [rowOrigin] plus the G2
 * rest [originYPx] offset, so the outline lands on the same Box content coordinates
 * the windowed tiles are placed at (rebased container plus rest offset). A valid
 * candidate draws a 2dp outline in `scheme.primary`; any other validity draws
 * `scheme.error`.
 */
@Composable
private fun MoveCandidateOverlay(
    active: HomeEditSession.Active,
    geometry: SquareCellGeometry,
    rowOrigin: Long,
    modifier: Modifier = Modifier,
    originX: Int = 0,
    originYPx: Long = 0L,
) {
    val density = LocalDensity.current
    val frame = TileBounds.signedBounds(
        column = active.candidatePosition.column,
        row = active.candidatePosition.row.toLong(),
        span = GridSpan(active.candidateSpan.columns, active.candidateSpan.rows),
        geometry = geometry,
        originX = originX,
        originRow = rowOrigin,
        originYPx = originYPx,
    )
    val topPx = frame.top
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (active.candidateValidity == CandidateValidity.VALID) {
        scheme.primary
    } else {
        scheme.error
    }
    val widthDp = with(density) { frame.widthPx.toDp() }
    val heightDp = with(density) { frame.heightPx.toDp() }
    Box(
        modifier = modifier
            .offset { IntOffset(frame.left, topPx) }
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
 * Signed-frame overload of [isInHandle] for G3 renderer frames.
 *
 * Same bottom-right corner rule over a [SignedPixelFrame] (whose top may be
 * negative above the row origin). Pure math, framework-free.
 */
internal fun isInHandle(
    x: Float,
    y: Float,
    originBounds: SignedPixelFrame,
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
 * Signed-tolerant (G3): both [absoluteTopPx] and [rowOrigin] may be negative (content
 * above row zero, signed `minRow` origin). The container places rows at
 * `(row - origin) * stride`, so the overlay subtracts the same origin stride from
 * the absolute frame. Results outside [Int] saturate instead of wrapping; callers
 * treat the outline as best-effort feedback.
 */
internal fun rebasedOverlayTop(
    absoluteTopPx: Int,
    rowOrigin: Long,
    rowStridePx: Long,
): Int {
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

/**
 * Positions the remove affordance for the active tile in absolute content pixels.
 *
 * - 1×1 spans pin the button's center to the tile's top-left corner:
 *   `(left - button/2, top - button/2)` (integer division; may be negative on the
 *   first row/column — callers clamp for rendering so the target stays visible).
 * - Larger spans center the button on the tile:
 *   `(left + (width - button)/2, top + (height - button)/2)`.
 *
 * Pure math, framework-free; signed G3 callers pass a [SignedPixelFrame] via the
 * overload below (already rebased around the signed origin plus the rest offset).
 * [span] selects the rule; legality stays owned by `core:grid`.
 */
internal fun removeButtonOffset(
    tileBounds: PixelRect,
    buttonSizePx: Int,
    span: GridSpan,
): IntOffset {
    require(buttonSizePx > 0) { "buttonSizePx must be positive" }
    return if (span == GridSpan(1, 1)) {
        IntOffset(
            tileBounds.left - buttonSizePx / 2,
            tileBounds.top - buttonSizePx / 2,
        )
    } else {
        IntOffset(
            tileBounds.left + (tileBounds.width - buttonSizePx) / 2,
            tileBounds.top + (tileBounds.height - buttonSizePx) / 2,
        )
    }
}

/**
 * Signed-frame overload of [removeButtonOffset] for G3 renderer frames.
 *
 * Same 1×1 pin vs larger-span center rule over a [SignedPixelFrame] (top may be
 * negative above the row origin). Pure math, framework-free.
 */
internal fun removeButtonOffset(
    tileBounds: SignedPixelFrame,
    buttonSizePx: Int,
    span: GridSpan,
): IntOffset {
    require(buttonSizePx > 0) { "buttonSizePx must be positive" }
    return if (span == GridSpan(1, 1)) {
        IntOffset(
            tileBounds.left - buttonSizePx / 2,
            tileBounds.top - buttonSizePx / 2,
        )
    } else {
        IntOffset(
            tileBounds.left + (tileBounds.widthPx - buttonSizePx) / 2,
            tileBounds.top + (tileBounds.heightPx - buttonSizePx) / 2,
        )
    }
}
