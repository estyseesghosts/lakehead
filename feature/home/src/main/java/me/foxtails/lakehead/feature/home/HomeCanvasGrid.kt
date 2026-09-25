package me.foxtails.lakehead.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
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
 *   `columns.count` used exactly (six is only the normal-phone reference, never a clamp).
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
 * - Transient move editing is hoisted here: long-press on a launchable tile enters a
 *   [MoveEditSession.Active] session, vertical scroll is disabled while active, pointer
 *   drags update the candidate via [MoveDragMath] with validity from `core:grid`, a
 *   non-interactive outline marks the candidate, back cancels, and drag end commits via
 *   [onMoveTile] when the candidate differs from the origin. Persistence itself stays
 *   with the caller: this grid only reports the committed [ItemId] and [LogicalPosition].
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
        key(loaded.requestedHomeId) {
            val scrollState = rememberScrollState()
            var moveSession by remember { mutableStateOf<MoveEditSession>(MoveEditSession.Idle) }
            val columns = loaded.columns.count
            val rows = loaded.extent.totalScrollableRows.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val active = moveSession as? MoveEditSession.Active
            BackHandler(enabled = active != null) {
                moveSession = moveSession.cancel()
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
            // content y=0 corresponds to this row.
            val rowOrigin = window.withOverscan(overscanRows).firstRow
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
                            isMoveActive = active != null,
                            activeItemId = active?.itemId,
                            onEnterMove = { entered ->
                                moveSession = moveSession.enterIfLaunchable(
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
            val sessionState = rememberUpdatedState(moveSession)
            // Tap on the grid background (outside any tile) cancels an active move session.
            // Tiles use combinedClickable which consumes their own taps; this handler only
            // fires for taps on empty grid area.
            val tapCancelModifier = Modifier.pointerInput(active?.itemId) {
                if (active?.itemId == null) return@pointerInput
                detectTapGestures(
                    onTap = { moveSession = sessionState.value.cancel() },
                )
            }
            val dragModifier = Modifier.pointerInput(active?.itemId, geometry, rowOrigin, columns, rows) {
                if (active?.itemId == null) return@pointerInput
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val cell = MoveDragMath.cellForPixel(
                            xPx = change.position.x,
                            yPx = change.position.y + scrollState.value.toFloat(),
                            cellSizePx = geometry.cellSizePx.toFloat(),
                            columnGapPx = geometry.columnGapPx.toFloat(),
                            rowGapPx = geometry.rowGapPx.toFloat(),
                            windowFirstRow = rowOrigin,
                        )
                        moveSession = sessionState.value.updateCandidate(
                            cell,
                            loaded.tiles,
                            columns,
                            rows,
                        )
                    },
                    onDragEnd = {
                        val current = sessionState.value as? MoveEditSession.Active
                        if (current != null && sessionState.value.hasMoved()) {
                            onMoveTile(current.itemId, current.candidate)
                        }
                        moveSession = MoveEditSession.Idle
                    },
                    onDragCancel = {
                        moveSession = MoveEditSession.Idle
                    },
                )
            }
            Box(
                modifier = Modifier
                    .verticalScroll(scrollState, enabled = active == null)
                    .then(tapCancelModifier)
                    .then(dragModifier)
                    .testTag(HOME_CANVAS_GRID_TAG),
            ) {
                WindowedSpanContainer(
                    children = children,
                    window = window,
                    overscanRows = overscanRows,
                    geometry = geometry,
                    minHeightPx = minHeightPx,
                )
                if (active != null) {
                    MoveCandidateOverlay(
                        active = active,
                        geometry = geometry,
                        rowOrigin = rowOrigin,
                    )
                }
            }
        }
    }
}

/**
 * Derives the visible logical row window from scroll/viewport/stride inputs.
 *
 * `firstRow = scrollPx / rowStridePx` (floor); the visible count covers the viewport height
 * with ceiling division plus one extra row for a partially visible boundary row. The upper
 * boundary saturates at [Long.MAX_VALUE] instead of wrapping. Overscan is not applied here;
 * the caller passes it separately to [WindowedSpanContainer].
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
 * Height is `totalScrollableRows * cellSizePx + (totalScrollableRows - 1) * rowGapPx`.
 * Overflow or results beyond [Int] saturate to [Int.MAX_VALUE] instead of wrapping; no
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

/** Test tag on the move-candidate validity outline (testing hook only; no behavior change). */
internal const val MOVE_CANDIDATE_OVERLAY_TAG: String = "move-candidate-overlay"

/**
 * Non-interactive outline marking the active move candidate cell.
 *
 * Position reuses [TileBounds.bounds] for the absolute candidate frame, rebased around the
 * windowed container's [rowOrigin] via [rebasedOverlayTop] so the outline lands on the same
 * content coordinates the tiles are placed at. A valid candidate draws a 2dp outline in the
 * selected border color; any other validity draws the pressed color.
 */
@Composable
private fun MoveCandidateOverlay(
    active: MoveEditSession.Active,
    geometry: SquareCellGeometry,
    rowOrigin: Long,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val absolute = TileBounds.bounds(
        position = GridPosition(active.candidate.column, active.candidate.row),
        span = GridSpan(active.span.columns, active.span.rows),
        geometry = geometry,
    )
    val rowStridePx = geometry.cellSizePx.toLong() + geometry.rowGapPx.toLong()
    val topPx = rebasedOverlayTop(
        absoluteTopPx = absolute.top,
        rowOrigin = rowOrigin,
        rowStridePx = rowStridePx,
    )
    val borderColor = if (active.candidateValidity == CandidateValidity.VALID) {
        AppTileDefaults.SelectedBorderColor
    } else {
        AppTileDefaults.PressedContainerColor
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
