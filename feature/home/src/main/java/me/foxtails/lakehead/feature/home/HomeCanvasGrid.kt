package me.foxtails.lakehead.feature.home

import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.foxtails.lakehead.core.designsystem.AddButton
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.TileBounds
import me.foxtails.lakehead.core.designsystem.TileNotifications
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import kotlin.math.abs

/**
 * Viewport padding/gap/clearance inputs for the home canvas grid, in pixels.
 *
 * Values are resolved by the caller outside composition and passed through to
 * [SquareCellGeometry.fromViewport] unchanged. All values are non-negative pixel counts;
 * zero means edge-to-edge/touching. The visual column count itself comes from the loaded
 * render state's adapted `columns.count` and is never clamped here.
 *
 * [bottomClearancePx] reserves space for the floating nav pill / system-gesture inset at
 * the viewport bottom. It is owned by `:app` (resolved from Dp/WindowInsets there) and
 * consumed as a plain pixel count here so no Android object enters the grid: the safe
 * canvas height is `max(0, full - bottomClearancePx)`, and the scroll container extends
 * the natural content by exactly this reserve (`max(full, natural + bottomClearancePx)`)
 * so the lowest tile scrolls clear of the pill while tiles stay visible behind it.
 */
data class HomeCanvasViewportStyle(
    val horizontalPaddingPx: Int = 0,
    val columnGapPx: Int = 0,
    val rowGapPx: Int = 0,
    val bottomClearancePx: Int = 0,
) {
    init {
        require(horizontalPaddingPx >= 0) { "horizontalPaddingPx must be non-negative" }
        require(columnGapPx >= 0) { "columnGapPx must be non-negative" }
        require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
        require(bottomClearancePx >= 0) { "bottomClearancePx must be non-negative" }
    }
}

/**
 * Internal scrolling grid arrangement for one loaded home.
 *
 * - Geometry comes from the caller-supplied full height ([fullHeightPx], the raw
 *   viewport) plus `BoxWithConstraints`/density measured width and
 *   [SquareCellGeometry.fromViewport] with the loaded state's
 *   adapted `columns.count` used exactly (five is only the normal-phone reference, never
 *   a clamp). The grid is laid out at exactly the full height so tiles render
 *   continuously behind the floating pill; the safe height ([usableHeightPx], raw
 *   minus [HomeCanvasViewportStyle.bottomClearancePx]) sizes only
 *   fit/scrollability/rest math, so the scroll range is the exact safe overflow.
 * - Normal scrolling: the natural content height is the intrinsic signed occupied height
 *   (rows × cell + gaps with no trailing gap after the final cell); the container minimum
 *   is `max(full, natural + clearance)` so short/exact-fit content fills the full
 *   viewport with no tail and tall content trails a transparent reserve that lands
 *   the lowest tile clear of the pill.
 *   `verticalScroll` is enabled only when the exact safe overflow is positive (plus the
 *   active-edit and pager axis guards), and the Compose `ScrollState` is clamped to the
 *   new maximum on shrink and collapsed to zero when content fits the safe height.
 * - Transient scroll state is keyed by the loaded `requestedHomeId` so each home keeps its
 *   own position and a home switch resets scrolling.
 * - Every tile is composed and placed by [HomeGridLayout] at its absolute logical row
 *   and column through the one [HomeGridMetrics]; there is no row window. The measured
 *   content height is the lowest tile bottom plus the bottom clearance, so the real
 *   `ScrollState` range is the content that exists.
 * - The loaded [GridVerticalExtent] feeds the centring rest offset and the initial
 *   edit workspace only; no second extent is computed here.
 * - Icons come only from [iconsByIdentity] via [Map.get] on the exact [AppIdentity].
 *   No decode, catalogue/platform lookup, or database access occurs in any composable.
 * - Transient editing is hoisted here: long-press on any tile (independent of
 *   launchability) enters W1 wiggle-editing selection; a selected-tile drag enters a
 *   [DragState.Move] in the one [HomeEditState], vertical scroll is disabled while dragging
 *   (and while idle a confident horizontal axis lock yields to the pager, so a
 *   horizontal swipe pages instead of scrolling), pointer drags update the
 *   candidate via [MoveDragMath] with validity from `core:grid`, a
 *   non-interactive outline marks the candidate, back cancels, and drag end commits via
 *   [onMoveTile] when the candidate differs from the origin. A single bottom-right,
 *   center-pinned resize handle resizes only when armed: tapping the arrow toggles
 *   [HomeEditState.resizeArmed] with no write, and only an armed
 *   arrow-origin drag switches to RESIZE mode when the pointer-down/start
 *   position is in its [RESIZE_TOUCH_DP] inward corner rect; an unarmed arrow-drag
 *   routes to neither move nor resize, and a body drag moves with the snapshotted
 *   span while clearing the arm. Routing is decided once at drag start from the
 *   down snapshot (selection + arrow-hit + armed) and never switches mid-gesture;
 *   the handle itself installs no drag detector (it would lose the slop race).
 *   drags update the preview span via [ResizeDragMath] (free span, clamped to the grid width)
 *   with last-valid hold (blocked proposals keep the preview, overlay flips to
 *   error), and drag end commits the held last-valid span via [onResizeTile]
 *   when it differs from the origin span, staying in edit mode either way.
 *   Routing is decided once at drag start: a MOVE drag never promotes to RESIZE
 *   mid-gesture. Persistence itself stays with the caller: this grid only
 *   reports the committed [ItemId] with a [LogicalPosition] or [LogicalSpan].
 * - A MOVE drag owns one transient edit workspace (`workspaceBottomRow`, see
 *   `HomeEditWorkspace.kt`) that only sizes the measured content, so the real
 *   `ScrollState` range grows below the last tile while the MOVE is active.
 *   Edge proximity auto-scrolls that `ScrollState` ([runMoveEdgeAutoScroll]),
 *   extending the workspace before each downward step, and every MOVE candidate
 *   is recomputed from the held pointer and the current scroll; release is the
 *   sole commit. RESIZE never expands. A release only emits an intent
 *   ([onMoveTile]/[onResizeTile]) and the drag state is already cleared; the
 *   caller owns acknowledgement and reconciliation of the persisted placements.
 * - Corner controls appear for the tile selected in [HomeEditState], including while it
 *   is being dragged. Each uses a 32dp visual circle in a 48dp touch
 *   target: [ResizeHandle] is bottom-right center pinned via [cornerControlOffset],
 *   while [RemoveButton] follows [removeControlPlacement] from the live preview
 *   span — top-right center pinned on 1×1/1×2/2×1 tiles, centered in the tile
 *   footprint on any larger span. The remove affordance floats as a sibling
 *   overlay in both MOVE and RESIZE modes and is announced as `"Remove {label}"`.
 *   Tapping it reports [onRemoveTile] with the selected/active [ItemId] only; the
 *   grid performs no persistence delete and issues no move/resize.
 * - B1: swipe-to-open shortcuts are removed — no idle/editing flick opens a
 *   shortcut surface and this grid exposes no swipe rows. Shortcuts return in
 *   B4 as an edit-mode menu above the selected tile: [EditShortcutMenu] follows
 *   [HomeEditState.selectedTileId] (long-press shows, tap-other
 *   transfers, empty-tap/Back/navigate/move-start/resize-start/removal
 *   dismisses with zero activation) and renders [surfaceEntries] in caller
 *   rank order via the reused [swipeSurfaceFrame]-pattern geometry
 *   ([editShortcutMenuFrame]); no reflow, no packing, no grid legality.  Tile
 *   strips are NOT the menu: they render [pinnedByTile] on every tile via
 *   [HomeCanvasTile] + [TilePinnedStrip] arrangement (centered icon when empty,
 *   compact strip + defined overflow when pinned).
 */
@Composable
internal fun HomeCanvasGrid(
    loaded: HomeRenderState.Loaded,
    iconsByIdentity: HomeCanvasIcons,
    onLaunchApp: (AppIdentity) -> Unit,
    viewportStyle: HomeCanvasViewportStyle,
    usableHeightPx: Int,
    // A3 floating viewport: the raw full render height (scroll host, visible window
    // and composition). The safe height ([usableHeightPx], raw minus clearance)
    // keeps fit/scrollability/rest/edit-boundary math. Defaults to the safe height
    // so callers that do not track the full viewport keep the exact prior behavior.
    fullHeightPx: Int = usableHeightPx,
    modifier: Modifier = Modifier,
    onMoveTile: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    onRemoveTile: (ItemId) -> Unit = {},
    // B4 edit-menu rows: rank-ordered shortcut entries for the currently
    // selected tile (empty until shortcut discovery lands). Row taps report
    // onSurfaceExecute/onSurfaceTogglePin with the selected ItemId and entry
    // id; execution and pin persistence stay with the caller.
    // B1 selection scoping: [surfaceEntriesTileId] names the tile [surfaceEntries]
    // was loaded for (null = unscoped legacy rows for previews/tests). The menu
    // shows only when the owner matches the selection, so an A→B tap-switch
    // hides honestly until B's rows arrive instead of flashing A's rows on B.
    // [onSelectedTileChange] reports selection transitions (long-press enter,
    // tap-other transfer, empty-tap/Back/navigate clear) so the caller can
    // query-on-select and scope rows; invoked with null when no tile is selected.
    surfaceEntries: List<ShortcutSurfaceEntry> = emptyList(),
    surfaceEntriesTileId: ItemId? = null,
    onSelectedTileChange: (ItemId?) -> Unit = {},
    onSurfaceExecute: (ItemId, String) -> Unit = { _, _ -> },
    onSurfaceTogglePin: (ItemId, String) -> Unit = { _, _ -> },
    // C1 tile strip: joined pinned shortcuts per tile (as produced by
    // TilePinnedStrip.resolve from the persisted binding + live metadata),
    // keyed by tile id.  Missing entries render no strip.  Strip taps report
    // onPinnedShortcutClick with the owning tile id and shortcut id;
    // execution stays with the caller.  Defaults keep previews/tests
    // pin-free; :app joins the B2 sources when it wires this through.
    pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
    notificationsByTile: Map<ItemId, TileNotifications> = emptyMap(),
    // Album slideshow photo per tile, resolved by `:app` (slice 6 wires the
    // coordinator). Missing entries render the normal icon. Defaults keep
    // previews/tests photo-free.
    albumPhotoByTile: Map<ItemId, ImageBitmap> = emptyMap(),
    onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
    // W3 reduced-motion: when true the resize preview snaps to the validated
    // span immediately instead of animating rapidly toward it. Both branches
    // share [resizePreviewFrame] geometry, so the rendered bounds are identical.
    reducedMotion: Boolean = false,
    // A1 hold signal: reports whether transient home state (active edit or
    // wiggle editing) should hold pager swipe. Emitted via
    // LaunchedEffect; the caller (shell) owns the pager gate.
    onTransientHoldChange: (Boolean) -> Unit = {},
    // A2 tile-touch hold: true from a tile-origin down until release (tap,
    // select-drag, active move/resize) so the gesture never becomes a page
    // swipe; background-origin gestures leave it false. Separate from the A1
    // hold above; the shell ANDs both negations into userScrollEnabled.
    onPagerHoldChange: (Boolean) -> Unit = {},
    // P2 navigation visibility: true while the home page is the visible pager
    // destination. The pager keeps adjacent pages composed
    // (beyondViewportPageCount), so pager navigation away never disposes this
    // grid and the home-switch clearing below never runs. When false the
    // transient edit is discarded with zero writes (see the effect below).
    // Defaults to true so callers that do not track pager visibility keep the
    // exact prior behavior. Supplied by the shell; owned here.
    isVisible: Boolean = true,
    // Widget tiles render through this slot; `:app` fills it with the hosted view.
    widgetContent: HomeWidgetContent = NoHomeWidgetContent,
    // The (+) button shown while editing reports here; the caller opens the picker.
    onRequestAddWidget: () -> Unit = {},
    onOpenFolder: (ItemId) -> Unit = {},
    // Folder actions for the selected tile while editing (see [tileEditActionsFor]).
    onNewFolder: (ItemId) -> Unit = {},
    onRequestAddToFolder: (ItemId) -> Unit = {},
    // Folder interior mode: no add-widget button and no folder actions (a folder holds no
    // folders), and a MOVE released outside the grid bounds reports [onDropOutside] instead
    // of committing. [onMoveActiveChange] and [onDragOutsideChange] let the host grow its
    // frame while a tile is held and show where a release would go.
    interior: Boolean = false,
    onDropOutside: (ItemId) -> Unit = {},
    onMoveActiveChange: (Boolean) -> Unit = {},
    onDragOutsideChange: (Boolean) -> Unit = {},
    // Releasing a held tile over a folder tile adds it to that folder instead of moving it.
    onDropOnFolder: (ItemId, ItemId) -> Unit = { _, _ -> },
) {
    require(usableHeightPx >= 0) { "usableHeightPx must be non-negative" }
    require(fullHeightPx >= 0) { "fullHeightPx must be non-negative" }
    require(usableHeightPx <= fullHeightPx) {
        "usableHeightPx must not exceed fullHeightPx"
    }
    // P0 stale-hold guard: the transient hold pair (active edit, wiggle
    // editing) lives inside BoxWithConstraints/key below, which
    // never composes when tiles are empty. Hoist a false emission + dispose
    // reset above the early return so shell `homeHold` cannot stick true after
    // a populated→empty transition or grid disposal.
    val latestHoldCallback = rememberUpdatedState(onTransientHoldChange)
    val latestPagerHoldCallback = rememberUpdatedState(onPagerHoldChange)
    DisposableEffect(loaded.requestedHomeId) {
        onDispose {
            latestHoldCallback.value(false)
            latestPagerHoldCallback.value(false)
        }
    }
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val availableWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        // A3 floating viewport: the full raw height hosts scrolling, the visible
        // window and composition (tiles render behind the pill); the safe height
        // (raw minus bottom clearance) keeps fit/scrollability/rest math. Neither
        // is re-measured here so Dp round-trips cannot skew the overflow math.
        val fullViewportPx = fullHeightPx
        val availableHeightPx = usableHeightPx
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
            // The single owner of edit mode, selection, resize arming and the
            // current drag. Immutable value; the ticker and the pointer loop
            // read and write it directly, so a write is visible to the other
            // immediately. Reset per home by key(requestedHomeId).
            var editState by remember { mutableStateOf(HomeEditState.Normal) }
            // P2 scroll-vs-pager arbitration: idle-gesture axis lock shared with
            // verticalScroll below. UNDECIDED by default; set to HORIZONTAL only on
            // a confident horizontal lock so the pager wins the swipe, otherwise
            // the vertical scroll keeps the gesture. Diagonal drags hold
            // UNDECIDED (never steal). Reset at gesture end; writes happen only on
            // lock transitions so scroll moves don't recompose per frame.
            // A2 tile-touch hold: true from a tile-origin down until release so
            // the pager never steals the gesture. Both signals live in the one
            // [HomePagerGate] (gesture state, never persisted), reset per home
            // via key(requestedHomeId); separate from the A1 isHold above.
            val pagerGate = remember { HomePagerGate() }
            // B3 edge auto-scroll: single owner of the held viewport-local
            // pointer during a MOVE drag (see MoveEdgePointer). Grab offsets
            // live in the MOVE frame beside the live session.
            val moveEdgePointer = remember { MoveEdgePointer() }
            val columns = loaded.columns.count
            val moveDrag = editState.moveDrag
            val dragging = editState.isDragging
            // A1 hold signal: edit mode holds pager swipe. Emitted via
            // LaunchedEffect with the hoisted latest callback so recompositions
            // of the lambda never restart the emission. Tiles are non-empty here
            // (empty returned above), so this delegates to the same predicate
            // the empty guard forces false.
            val isHold = isHomeHold(editState)
            LaunchedEffect(isHold) {
                latestHoldCallback.value(isHold)
            }
            // A2 tile-touch hold emission: forwards pagerHold to the shell via
            // the hoisted latest callback so lambda recompositions never restart
            // the emission. The top-level empty/dispose guards above force false
            // on populated→empty transitions and grid disposal.
            LaunchedEffect(pagerGate.hold) {
                latestPagerHoldCallback.value(pagerGate.hold)
            }
            // B1 selection signal: reports the W1-selected tile id (or null when
            // no tile is selected) so the caller can query-on-select and scope
            // menu rows to the selection. Hoisted latest callback; emission is
            // idempotent per selection value. Never drives selection itself.
            val latestSelectionCallback = rememberUpdatedState(onSelectedTileChange)
            LaunchedEffect(editState.selectedTileId) {
                latestSelectionCallback.value(editState.selectedTileId)
            }
            BackHandler(enabled = editState.isEditing) {
                editState = if (editState.isDragging) editState.cancelDrag() else editState.exit()
                moveEdgePointer.clear()
            }
            // W1 navigate/Home reset: leaving the tile-grid surface exits
            // wiggle-editing via the pure transition (idempotent from Normal).
            // key(requestedHomeId) already resets on home switch; this covers
            // navigate-away reloads of the same home.
            LaunchedEffect(loaded.requestedHomeId) {
                editState = editState.exit()
                moveEdgePointer.clear()
            }
            // P2 pager-navigation exit: navigating to another pager page leaves
            // this grid composed (adjacent pages stay alive), so neither the
            // home-switch reset above nor disposal runs. While hidden the
            // transient MOVE/RESIZE session, selection, edge hold
            // and tile-touch hold are discarded with zero writes. A commit that
            // was already reported before the hide is the caller's to reconcile.
            LaunchedEffect(isVisible) {
                if (!isVisible) {
                    editState = editState.exit()
                    moveEdgePointer.clear()
                    pagerGate.release()
                }
            }
            val layoutMetrics = homeGridMetricsFor(geometry, columns, originX, restOffsetYPx = 0f)
            val rowStridePx: Long = layoutMetrics.rowStrideCells
            // Softlock committed extent: validated against all loaded placements
            // (invalid falls back to a remeasure with the same grid policy);
            // tiles are never moved/dropped for scroll recovery.
            val committed = committedFrameFor(
                loadedExtent = loaded.extent,
                tiles = loaded.tiles,
                geometry = geometry,
                usableHeightPx = availableHeightPx,
                rowStridePx = rowStridePx,
            )
            val committedExtent = committed.extent
            val moveTickItemId = moveDrag?.itemId
            val latestOnDropOutside = rememberUpdatedState(onDropOutside)
            val latestOnDropOnFolder = rememberUpdatedState(onDropOnFolder)
            val latestMoveActiveCallback = rememberUpdatedState(onMoveActiveChange)
            val latestOutsideCallback = rememberUpdatedState(onDragOutsideChange)
            val lastMoveId = remember { arrayOfNulls<ItemId>(1) }
            if (moveTickItemId != null) lastMoveId[0] = moveTickItemId
            LaunchedEffect(moveTickItemId != null) {
                latestMoveActiveCallback.value(moveTickItemId != null)
            }
            val heldPointer = moveEdgePointer.position
            val isOutside = interior && heldPointer != null &&
                isOutsideGrid(heldPointer, availableWidthPx, fullViewportPx)
            LaunchedEffect(isOutside) { latestOutsideCallback.value(isOutside) }
            // Transient edit workspace: set when a MOVE starts, held through the
            // release settle (the scroll animates back inside the committed range
            // first) and then dropped. It sizes the measured content only;
            // candidates and legality never read it.
            var workspaceBottomRow by remember { mutableStateOf<Long?>(null) }
            val committedRest = committed.rest
            // Edge auto-scroll for a held MOVE (the ONLY scroll driver): while the
            // pointer is within the edge band of the real viewport the real
            // `ScrollState` scrolls every frame at a speed proportional to depth,
            // and the candidate is recomputed from the unchanged pointer and the
            // new scroll. The scroll range is the transient workspace: committed
            // bottom + the empty rows the normaliser allows + the tile's span.
            // Scrolling never calls onMoveTile: release stays the sole commit.
            val autoScrollEdgePx = with(density) {
                HomeAutoScroll.AUTO_SCROLL_EDGE_DP.dp.roundToPx()
            }.coerceAtLeast(1)
            // Rest offset (centring of short content): presentation only, from the
            // viewport and the committed absolute content height. It glides while
            // not editing and is frozen for the whole edit session, so the one
            // [gridMetrics] used to render tiles and overlays and to map pointers
            // never changes under a drag. A drag that began in an earlier frame
            // (width change, rotation) is cancelled below instead of re-mapped.
            val restOffsetYPx = rememberRestOffsetYPx(
                targetPx = originTargetFor(committedRest),
                snapToTarget = shouldSnapOrigin(committedRest, availableHeightPx, isFirstFrame = false),
                isEditing = editState.isEditing,
            )
            val gridMetrics = layoutMetrics.copy(restOffsetYPx = restOffsetYPx)
            // The folder tile under the held pointer, if a non-folder tile is being dragged.
            val hoverFolderId = if (!interior && moveDrag != null && heldPointer != null) {
                folderUnderPointer(
                    tiles = loaded.tiles,
                    metrics = gridMetrics,
                    draggedId = moveDrag.itemId,
                    pointer = heldPointer,
                    scrollYPx = scrollState.value.toFloat(),
                )
            } else {
                null
            }
            LaunchedEffect(gridMetrics) {
                editState = editState.cancelDragIfFrameChanged(gridMetrics)
            }
            // Ticker inputs stay fresh through snapshot holders; the ticker itself
            // restarts only with the moved item (see [runMoveEdgeAutoScroll]).
            val latestGridMetrics = rememberUpdatedState(gridMetrics)
            val scrollViewportPx = rememberUpdatedState(fullViewportPx)
            val bottomClearancePx = rememberUpdatedState(viewportStyle.bottomClearancePx)
            val autoScrollMaxSpeedPx = with(density) {
                HomeAutoScroll.AUTO_SCROLL_MAX_SPEED_DP_PER_SEC.dp.toPx()
            }
            val moveSpanRows = moveDrag?.span?.rows ?: 1
            LaunchedEffect(moveTickItemId) {
                if (moveTickItemId != null) {
                    workspaceBottomRow = moveWorkspaceBottomRow(committedExtent, moveSpanRows)
                    runMoveEdgeAutoScroll(
                        scrollState = scrollState,
                        isMoveActive = { editState.moveDrag?.itemId == moveTickItemId },
                        pointer = { moveEdgePointer.position },
                        viewportPx = { scrollViewportPx.value },
                        edgePx = autoScrollEdgePx,
                        maxSpeedPxPerSec = autoScrollMaxSpeedPx,
                        onScrolled = { held ->
                            editState = editState.moveTo(
                                pointerXPx = held.x,
                                pointerYPxViewport = held.y,
                                scrollYPx = scrollState.value.toFloat(),
                            )
                        },
                    )
                } else if (workspaceBottomRow != null) {
                    // Release: glide back inside the committed range before the
                    // workspace collapses, so the content never jumps.
                    val limit = committedMaxScrollPx(
                        extent = committedExtent,
                        metrics = latestGridMetrics.value,
                        fullViewportPx = scrollViewportPx.value,
                        bottomClearancePx = bottomClearancePx.value,
                    )
                    if (scrollState.value > limit) scrollState.animateScrollTo(limit)
                    workspaceBottomRow = null
                }
            }
            // A3 floating container: HomeGridLayout measures the lowest tile bottom
            // plus exactly the bottom clearance, floored at the full viewport, so
            // short content fills the viewport (no tail), tall content trails a
            // transparent reserve that scrolls the lowest tile clear of the pill,
            // and tiles stay visible behind the pill with no opaque bottom spacer.
            // While a MOVE is active the floor also covers the edit workspace
            // (6.2), so measured height is max(committed content bottom, workspace
            // bottom) and the real ScrollState range grows with it. There is no
            // separately computed scroll maximum anywhere.
            val minHeightPx = moveWorkspaceFloorPx(
                workspaceBottomRow = workspaceBottomRow,
                metrics = gridMetrics,
                fullViewportPx = fullViewportPx,
                bottomClearancePx = viewportStyle.bottomClearancePx,
            )
            // W3 renderedSpan = preview ?: persisted: while RESIZE-dragging the
            // active tile, its placement and content use the live last-valid
            // preview span so content adapts during the drag. Reduced-motion
            // snaps to the same span (no rapid animation); geometry identical.
            val renderedSpanOf: (HomeRenderTile) -> LogicalSpan =
                { tile -> editState.renderedSpan(tile.itemId, tile.span) }
            // Latest session for drag callbacks: drag pointerInput is keyed by
            // loaded.requestedHomeId (not item id), so it stays stable through candidate
            // recomposition and resets on home switch.
            // P2 hide-abort read: the drag release path suppresses commits
            // when the page is no longer visible. Hoisted latest value so the
            // Unit/home-keyed gesture scope observes hides without restart
            // races; the drag scope itself is also keyed on isVisible below so
            // a hide cancels the in-flight await.
            val isVisibleState = rememberUpdatedState(isVisible)
            val latestOnMoveTile = rememberUpdatedState(onMoveTile)
            val latestOnResizeTile = rememberUpdatedState(onResizeTile)
            // Snapshot holder for the Unit-keyed idle gesture observer below: tiles,
            // geometry and origins stay fresh without restarting in-flight gestures.
            // B1: the flick-to-show detector is removed, but the down hit-test is
            // retained for A2 tile-vs-background pager-hold arbitration.
            val idleHitInputs = rememberUpdatedState(
                IdleHitSnapshot(
                    tiles = loaded.tiles,
                    metrics = gridMetrics,
                    scrollYPx = scrollState.value.toFloat(),
                ),
            )
            // Tap on the grid background (outside any tile) cancels an active edit
            // session and exits W1 wiggle-editing via the pure exitOnEmptyTap
            // transition. Tiles use combinedClickable which consumes their own
            // taps; this handler only fires for taps on empty grid area.
            // Outside edit mode the same detector turns a long-press on empty
            // background into `Editing(selected = null)`; a press that lands on a
            // tile is the tile's own long-press and is ignored here.
            val tapCancelModifier = Modifier.pointerInput(editState.isEditing) {
                detectTapGestures(
                    onTap = {
                        if (editState.isEditing) {
                            editState = editState.exit()
                            moveEdgePointer.clear()
                        }
                    },
                    onLongPress = { press ->
                        if (editState.isEditing) return@detectTapGestures
                        val snapshot = idleHitInputs.value
                        val viewportY = nodeLocalToViewportY(press.y, snapshot.scrollYPx)
                        val onTile = snapshot.metrics.tileAt(
                            snapshot.tiles,
                            press.x,
                            snapshot.metrics.contentY(viewportY, snapshot.scrollYPx),
                        ) != null
                        if (!onTile) editState = editState.enterOnBackgroundLongPress()
                    },
                )
            }
            // Idle (non-drag) pager arbitration; see [homePagerGesture]. Observes
            // without consuming, so verticalScroll, tapCancel and tile taps are
            // unaffected, and yields to the edit drag for tile downs.
            val idleGestureModifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    homePagerGesture(
                        pager = pagerGate,
                        hit = { idleHitInputs.value },
                        isEditing = { editState.isEditing },
                    )
                }
            }
            // Unified move/resize drag handler on the parent Box. The resize handle is
            // tap-active (its own tap toggles the resize arm) but installs no drag
            // detector; this handler hit-tests the pointer position against the handle
            // corner once at gesture start (after slop) and routes to RESIZE or MOVE.
            // A single detector avoids the slop competition that made a separate
            // handle detector unreachable. Drag pointerInput is keyed by
            // loaded.requestedHomeId (not item id), so the gesture scope stays stable
            // through candidate recomposition and resets on home switch;
            // scroll is disabled while active so scrollState/geometry stay fixed.
            //
            // A move starts two ways, both from this one handler: a tile touched
            // in edit mode drags past plain radial touch slop in any direction,
            // and a launchable tile touched before edit mode is picked up with no
            // slop when its long-press fires with the finger still down. The
            // loop reads the down itself (the first awaitPointerEvent()) and
            // consumes nothing until a move starts, so taps and scrolling keep
            // working.
            // P2 hide-abort: keyed on isVisible as well as the home id so a
            // pager navigation away cancels the in-flight await (zero-write);
            // the LaunchedEffect(isVisible) above already discards Compose
            // session/edge/hold state. Release commits below
            // additionally suppress onResize/onMove when !isVisible.
            val dragModifier = Modifier.pointerInput(loaded.requestedHomeId, isVisible) {
                val handleSizePx = RESIZE_TOUCH_DP.dp.roundToPx()
                val host = object : HomeDragHost {
                    override var editState: HomeEditState
                        get() = editState
                        set(value) { editState = value }
                    override val hit: IdleHitSnapshot get() = idleHitInputs.value
                    override val scrollYPx: Int get() = scrollState.value
                    override val isVisible: Boolean get() = isVisibleState.value
                    override val pager: HomePagerGate = pagerGate
                    override fun onMovePointer(pointerXPx: Float, pointerYPx: Float) {
                        moveEdgePointer.set(pointerXPx, pointerYPx)
                    }

                    // The drag state is already cleared by the loop when this runs;
                    // a commit is only an intent. Acknowledgement and reconciliation
                    // belong to the caller that owns the placements.
                    override fun onDragEnded(route: DownDragRoute, end: HomeDragEnd) {
                        val held = moveEdgePointer.position
                        if (route == DownDragRoute.MOVE) {
                            moveEdgePointer.clear()
                        }
                        if (!interior && route == DownDragRoute.MOVE && held != null &&
                            !(end is HomeDragEnd.Discarded && end.reason == HomeDragDiscard.CANCELLED)
                        ) {
                            val snapshot = idleHitInputs.value
                            val moved = lastMoveId[0]
                            val folder = moved?.let {
                                folderUnderPointer(snapshot.tiles, snapshot.metrics, it, held, snapshot.scrollYPx)
                            }
                            if (moved != null && folder != null) {
                                latestOnDropOnFolder.value(moved, folder)
                                return
                            }
                        }
                        if (interior && route == DownDragRoute.MOVE && held != null &&
                            !(end is HomeDragEnd.Discarded && end.reason == HomeDragDiscard.CANCELLED) &&
                            isOutsideGrid(held, availableWidthPx, scrollViewportPx.value)
                        ) {
                            lastMoveId[0]?.let { latestOnDropOutside.value(it) }
                            return
                        }
                        if (end !is HomeDragEnd.Commit) return
                        when (val commit = end.commit) {
                            is HomeEditCommit.Move ->
                                latestOnMoveTile.value(commit.itemId, commit.position)
                            is HomeEditCommit.Resize ->
                                latestOnResizeTile.value(commit.itemId, commit.span)
                        }
                    }
                }
                awaitEachGesture { homeDragGesture(host, handleSizePx) }
            }
            // A3: vertical scroll only when the measured content overflows the
            // viewport (the real ScrollState range), plus the active-drag and
            // horizontal-pager guards; short/exact-fit content never scrolls.
            Box(
                modifier = Modifier
                    .verticalScroll(
                        scrollState,
                        enabled = scrollState.maxValue > 0 && !dragging &&
                            !pagerGate.yieldsToPager,
                    )
                    .then(tapCancelModifier)
                    .then(dragModifier)
                    .then(if (dragging) Modifier else idleGestureModifier)
                    .testTag(HOME_CANVAS_GRID_TAG),
            ) {
                HomeGridLayout(
                    tiles = loaded.tiles,
                    metrics = gridMetrics,
                    renderedSpan = renderedSpanOf,
                    minHeightPx = minHeightPx,
                    tailPx = viewportStyle.bottomClearancePx,
                ) { tile, renderedSpan ->
                    HomeCanvasTile(
                        tile = if (renderedSpan != tile.span) tile.copy(span = renderedSpan) else tile,
                        iconBitmap = tile.appIdentity?.let { iconsByIdentity[it] },
                        onLaunchApp = onLaunchApp,
                        editState = editState,
                        // C1: per-tile joined pins (empty = centered icon);
                        // arranged on the live preview span inside the tile.
                        pinnedShortcuts = pinnedByTile[tile.itemId].orEmpty(),
                        notifications = notificationsByTile[tile.itemId],
                        albumPhoto = albumPhotoByTile[tile.itemId],
                        onShortcutClick = { shortcutId ->
                            onPinnedShortcutClick(tile.itemId, shortcutId)
                        },
                        // W1 recess/select: tap transfers selection directly
                        // (selectOnTap, never launch); long-press enters
                        // wiggle-editing (enterOnLongPress). Never enters Active here.
                        onSelect = { selectedId ->
                            editState = editState.selectOnTap(selectedId)
                        },
                        onEnterWiggle = { entered ->
                            editState = editState.enterOnLongPress(entered.itemId)
                        },
                        content = if (tile.isWidget) {
                            { editing ->
                                WidgetTileBody(
                                    tile = tile,
                                    isEditing = editing,
                                    widgetContent = widgetContent,
                                    onSelect = { editState = editState.selectOnTap(tile.itemId) },
                                    onLongPress = { editState = editState.enterOnLongPress(tile.itemId) },
                                )
                            }
                        } else if (tile.isFolder) {
                            { editing ->
                                FolderTileBody(
                                    tile = tile,
                                    iconsByIdentity = iconsByIdentity,
                                    isEditing = editing,
                                    isSelected = editState.selectedTileId == tile.itemId ||
                                        hoverFolderId == tile.itemId,
                                    isDragging = editState.isDragging,
                                    onOpen = { onOpenFolder(tile.itemId) },
                                    onSelect = { editState = editState.selectOnTap(tile.itemId) },
                                    onLongPress = { editState = editState.enterOnLongPress(tile.itemId) },
                                )
                            }
                        } else {
                            null
                        },
                        lift = if (moveDrag != null && moveDrag.itemId == tile.itemId) {
                            {
                                val held = moveEdgePointer.position
                                if (held == null) {
                                    Offset.Zero
                                } else {
                                    MoveDragMath.liftTranslation(
                                        move = moveDrag,
                                        pointerXPx = held.x,
                                        pointerYPxViewport = held.y,
                                        currentScrollPx = scrollState.value.toFloat(),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                // Overlay layer: move/resize candidate outlines, resize handle,
                // remove button and edit-mode shortcut menu. Reads the current
                // editState snapshot; mutation callbacks are passed through from
                // the owning composable.
                HomeGridOverlay(
                    moveDrag = moveDrag,
                    resizeDrag = editState.resizeDrag,
                    editState = editState,
                    tiles = loaded.tiles,
                    gridMetrics = gridMetrics,
                    reducedMotion = reducedMotion,
                    dragging = dragging,
                    surfaceEntries = surfaceEntries,
                    surfaceEntriesTileId = surfaceEntriesTileId,
                    availableWidthPx = availableWidthPx,
                    availableHeightPx = availableHeightPx,
                    scrollYPx = scrollState.value,
                    onToggleResizeArmed = { controlItemId ->
                        if (editState.selectedTileId == controlItemId) {
                            editState = editState.toggleResizeArmed()
                        }
                    },
                    onRemove = { controlItemId ->
                        onRemoveTile(controlItemId)
                        // B2: unpin selection → Editing(null) + cancel
                        // Active → Idle; stays in wiggle-edit.
                        editState = editState.onTileRemoved(controlItemId)
                        // B3: removal ends the transient edit too.
                        moveEdgePointer.clear()
                    },
                    onSurfaceExecute = onSurfaceExecute,
                    onSurfaceTogglePin = onSurfaceTogglePin,
                )
            }
            // Viewport-fixed folder actions for the selected tile, stacked above the add button.
            val selectedTile = editState.selectedTileId?.let { selectedId ->
                loaded.tiles.firstOrNull { it.itemId == selectedId }
            }
            if (!interior && editState.isEditing && !dragging && selectedTile != null) {
                val actions = tileEditActionsFor(selectedTile, hasFolders = loaded.tiles.any { it.isFolder })
                TileActionBar(
                    actions = actions,
                    onAction = { action ->
                        when (action) {
                            TileEditAction.NEW_FOLDER -> onNewFolder(selectedTile.itemId)
                            TileEditAction.ADD_TO_FOLDER -> onRequestAddToFolder(selectedTile.itemId)
                        }
                        editState = editState.exit()
                        moveEdgePointer.clear()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = with(density) { viewportStyle.bottomClearancePx.toDp() } + 16.dp + 56.dp + 8.dp,
                        ),
                )
            }
            // Viewport-fixed, outside the scrolling content: bottom-end above the nav pill.
            if (!interior && editState.isEditing && !dragging) {
                AddButton(
                    description = "Add widget",
                    onClick = onRequestAddWidget,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = with(density) { viewportStyle.bottomClearancePx.toDp() } + 16.dp,
                        ),
                )
            }
        }
    }
}

/**
 * W3 single-BR resize touch target, in dp.
 *
 * The resize handle's hit rect is exactly this size, extending INWARD over the
 * active tile's bottom-right corner (`[right - touch, right] x [bottom - touch,
 * bottom]`), never outward — edge tiles never clip. On tiles narrower than
 * twice this size the drag-routing square shrinks to half the tile extent so a
 * body strip stays reachable (see [resizeHandleSidePx]); the handle overlay's
 * own 48dp tap target is unaffected. The visual marker is the shared
 * 32dp circle (see `ResizeHandle`); the surrounding touch square is the shared 48dp
 * target.
 */
internal const val RESIZE_TOUCH_DP: Int = CORNER_CONTROL_TOUCH_SIZE

/**
 * The folder tile under a held viewport-local [pointer] while [draggedId] is dragged, or null.
 * A folder is never dropped onto itself or another folder (no nesting).
 */
internal fun folderUnderPointer(
    tiles: List<HomeRenderTile>,
    metrics: HomeGridMetrics,
    draggedId: ItemId,
    pointer: Offset,
    scrollYPx: Float,
): ItemId? {
    val dragged = tiles.firstOrNull { it.itemId == draggedId } ?: return null
    if (dragged.isFolder) return null
    val under = metrics.tileAt(tiles, pointer.x, metrics.contentY(pointer.y, scrollYPx)) ?: return null
    return under.takeIf { it.isFolder && it.itemId != draggedId }?.itemId
}

/** True when a viewport-local pointer lies outside the grid's own bounds (a folder drop-out). */
internal fun isOutsideGrid(pointer: Offset, widthPx: Int, heightPx: Int): Boolean =
    pointer.x < 0f || pointer.x > widthPx || pointer.y < 0f || pointer.y > heightPx

/** Test tag on the scrolling canvas grid (testing hook only; no behavior change). */
internal const val HOME_CANVAS_GRID_TAG: String = "home-canvas-grid"

/**
 * Idle-gesture down hit-test snapshot for A2 tile-vs-background arbitration.
 *
 * B1 renamed this from `SwipeShowSnapshot` when the flick-to-show detector was
 * removed: the snapshot no longer feeds a surface show, only the tile-origin
 * hit-test that holds the pager for tile touches. Carried through
 * `rememberUpdatedState` so the Unit-keyed idle observer always hit-tests
 * current tiles, geometry and origins without restarting in-flight gestures.
 */
internal data class IdleHitSnapshot(
    val tiles: List<HomeRenderTile>,
    val metrics: HomeGridMetrics,
    val scrollYPx: Float,
)
