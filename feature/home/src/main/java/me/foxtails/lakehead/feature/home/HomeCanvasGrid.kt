package me.foxtails.lakehead.feature.home

import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.RemoveButton
import me.foxtails.lakehead.core.designsystem.ResizeCandidateOverlay
import me.foxtails.lakehead.core.designsystem.ResizeHandle
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
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
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.GridVerticalLayout
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
 * - The visible [GridRowWindow] is derived from the scroll offset, full viewport height,
 *   and row stride with checked/saturating `Long` arithmetic; the window plus [overscanRows]
 *   is passed to [WindowedSpanContainer], which owns overscan expansion and sparse selection.
 * - The loaded [GridVerticalExtent] is used only to derive a safe minimum content height;
 *   no second extent is computed here.
 * - Tiles map to [SpanChild] in persisted order; icons come only from [iconsByIdentity]
 *   via [Map.get] on the exact [AppIdentity]. No decode, catalogue/platform lookup, or
 *   database access occurs in any composable.
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
 *   drags update the preview span via [ResizeDragMath] and [RenderableSpans.quantize]
 *   with last-valid hold (blocked proposals keep the preview, overlay flips to
 *   error), and drag end commits the held last-valid span via [onResizeTile]
 *   when it differs from the origin span, staying in edit mode either way.
 *   Routing is decided once at drag start: a MOVE drag never promotes to RESIZE
 *   mid-gesture (gated for W4). Persistence itself stays
 *   with the caller: this grid only reports the committed [ItemId],
 *   [LogicalPosition] and per-attempt request id (carried back in the
 *   commit-result ack) or [LogicalSpan].
 * - B3: a MOVE drag owns a transient edit frontier (normal boundary plus a
 *   bounded margin, extended one margin per bottom-edge auto-scroll step via
 *   `core:grid` policy). Edge proximity auto-scrolls the `ScrollState` (top
 *   limited by the range) and every MOVE candidate remaps with the current
 *   scroll offset; auto-scroll-owned scroll never steals the drag, release is
 *   the sole commit, and the frontier is leased to a pending commit until the
 *   loaded tiles reflect it, then recomputed. RESIZE never expands.
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
    overscanRows: Long,
    viewportStyle: HomeCanvasViewportStyle,
    usableHeightPx: Int,
    // A3 floating viewport: the raw full render height (scroll host, visible window
    // and composition). The safe height ([usableHeightPx], raw minus clearance)
    // keeps fit/scrollability/rest/edit-boundary math. Defaults to the safe height
    // so callers that do not track the full viewport keep the exact prior behavior.
    fullHeightPx: Int = usableHeightPx,
    modifier: Modifier = Modifier,
    onMoveTile: (ItemId, LogicalPosition, Long) -> Unit = { _, _, _ -> },
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
    // Softlock ack: commit-result for the last reported MOVE, supplied by the
    // caller after HomeRouteLoader.commitMove returns. Accepted retains the
    // pending lease until the projection reflects it; rejected/not-found
    // clears the lease + frontier. Scroll range stays committed-bounds either
    // way. Null means no ack has arrived.
    moveCommitAck: HomeMoveCommitAck? = null,
) {
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
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
    val tilesEmpty = loaded.tiles.isEmpty()
    LaunchedEffect(tilesEmpty, loaded.requestedHomeId) {
        if (tilesEmpty) {
            latestHoldCallback.value(false)
            latestPagerHoldCallback.value(false)
        }
    }
    DisposableEffect(loaded.requestedHomeId) {
        onDispose {
            latestHoldCallback.value(false)
            latestPagerHoldCallback.value(false)
        }
    }
    if (tilesEmpty) {
        BasicText(
            text = "Home is empty",
            modifier = modifier,
        )
        return
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
            var pagerAxisLock by remember { mutableStateOf(LockedAxis.UNDECIDED) }
            // A2 tile-touch hold: true from a tile-origin down until release so
            // the pager never steals the gesture. Plain remember alongside
            // pagerAxisLock (gesture state, never persisted); reset per gesture
            // and on home switch via key(requestedHomeId). Separate from the A1
            // isHold above. Set once at down via resolveHomeGesture + holdsPager
            // (snapshot-once, never flipped mid-gesture); cleared on release.
            var pagerHold by remember { mutableStateOf(false) }
            // B3 transient edit frontier: edit-only working boundary for MOVE
            // expansion, created from the normal boundary plus a bounded margin
            // when a MOVE drag becomes active. Reset by key(requestedHomeId) on
            // home switch; discarded on cancel/exit; leased to a pending commit
            // until the loaded tiles reflect it (see pendingMove below). Never
            // a legality bound for RESIZE.
            var editFrontier by remember {
                mutableStateOf<GridVerticalLayout.EditBoundary?>(null)
            }
            // B3 pending-commit lease: the release-committed move target retained
            // until loaded.tiles shows it. Gates another MOVE while unsatisfied.
            var pendingMove by remember { mutableStateOf<PendingMoveCommit?>(null) }
            // P1 attempt sequence: each reported MOVE takes the next id so the
            // pending lease and the caller's commit-result ack name the exact
            // attempt. Equal item/position retries are distinct acks (each
            // retriggers the ack effect below) and a stale ack for an older
            // attempt never clears a newer lease.
            var moveRequestSeq by remember { mutableStateOf(0L) }
            // B3 edge auto-scroll: latest viewport-local pointer position during
            // a MOVE drag (null when no MOVE drag is active). Both axes are
            // retained so the stationary ticker below can remap the held
            // pointer with the current scroll after each step; the per-frame
            // drag loop steps on the same values while the pointer moves.
            // Grab offsets live in the MOVE frame beside the live session.
            var moveEdgeYPx by remember { mutableStateOf<Float?>(null) }
            var moveEdgeXPx by remember { mutableStateOf<Float?>(null) }
            // B3 auto-scroll-owned scroll accounting: last scroll value accounted
            // for (gesture start or an auto-scroll-owned step). The MOVE steal
            // check compares against this instead of the frozen start value, so
            // intentional auto-scroll never cancels the drag while any foreign
            // scroll delta still does.
            var moveScrollBaseline by remember { mutableStateOf(0) }
            val columns = loaded.columns.count
            val moveDrag = editState.moveDrag
            val dragging = editState.isDragging
            // A1 hold signal: edit mode holds pager swipe. Emitted via
            // LaunchedEffect with the hoisted latest callback so recompositions
            // of the lambda never restart the emission. Tiles are non-empty here
            // (empty returned above), so this delegates to the same predicate
            // the empty guard forces false.
            val isHold = gridHoldSignal(
                tilesEmpty = false,
                editState = editState,
            )
            LaunchedEffect(isHold) {
                latestHoldCallback.value(isHold)
            }
            // A2 tile-touch hold emission: forwards pagerHold to the shell via
            // the hoisted latest callback so lambda recompositions never restart
            // the emission. The top-level empty/dispose guards above force false
            // on populated→empty transitions and grid disposal.
            LaunchedEffect(pagerHold) {
                latestPagerHoldCallback.value(pagerHold)
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
                // B3: explicit cancel/exit discards the transient frontier and
                // any pending lease with zero writes.
                editFrontier = null
                pendingMove = null
                moveEdgeYPx = null
                moveEdgeXPx = null
            }
            // W1 navigate/Home reset: leaving the tile-grid surface exits
            // wiggle-editing via the pure transition (idempotent from Normal).
            // key(requestedHomeId) already resets on home switch; this covers
            // navigate-away reloads of the same home.
            LaunchedEffect(loaded.requestedHomeId) {
                editState = editState.exit()
                // B3: same-home reload is an exit for the transient frontier.
                editFrontier = null
                pendingMove = null
                moveEdgeYPx = null
                moveEdgeXPx = null
            }
            // P2 pager-navigation exit: navigating to another pager page leaves
            // this grid composed (adjacent pages stay alive), so neither the
            // home-switch reset above nor disposal runs. While hidden the
            // transient MOVE/RESIZE session, selection, frontier, edge hold
            // and tile-touch hold are discarded with zero writes; the pending
            // MOVE lease is preserved across hide (never cleared here) so a
            // leave-before-loaded cannot admit a stale second move on return.
            // The rebase effect below reconciles the lease vs loaded tiles
            // (clears once loaded reflects the target); the MOVE entry gate
            // blocks a new MOVE while the lease is unsatisfied. Explicit
            // failure paths (Back, empty-tap, steal, removal) clear the lease;
            // hide is not a failure. The pill navigates back to a gated grid.
            LaunchedEffect(isVisible) {
                if (!isVisible) {
                    editState = editState.exit()
                    editFrontier = null
                    moveEdgeYPx = null
                    moveEdgeXPx = null
                    pagerHold = false
                }
            }
            val layoutMetrics = homeGridMetricsFor(geometry, columns, originX, restOffsetYPx = 0f)
            val rowStridePx: Long = layoutMetrics.rowStrideCells
            // Softlock committed extent: validated against all loaded placements
            // (invalid falls back to a remeasure with the same grid policy);
            // tiles are never moved/dropped for scroll recovery.
            val committedExtent = committedExtentOrRemeasured(
                loadedExtent = loaded.extent,
                tiles = loaded.tiles,
                usableHeightPx = availableHeightPx,
                rowStridePx = rowStridePx,
            )
            // Row zero is always the content top; `originYPx` is a presentation-only
            // rest offset applied once through `gridMetrics`.
            val window = visibleWindowFor(
                scrollPx = scrollState.value,
                viewportHeightPx = fullViewportPx,
                rowStridePx = rowStridePx,
                overscanRows = overscanRows,
            )
            // A3 natural content height: intrinsic signed occupied rows × cell plus
            // inter-row gaps, with no trailing gap after the final cell. Never
            // inflated to the viewport here; the container minimum below does that.
            // Softlock: committed geometry (loaded.extent + usable height) owns
            // the idle range; the transient frontier sizes the layout only while
            // a MOVE drag is active (never RESIZE, never idle — a retained valid
            // pending lease gates entry but must not inflate the idle range).
            val moveActive = moveDrag != null
            val committedNaturalHeightPx = contentMinHeightPxSigned(
                extent = committedExtent,
                cellSizePx = geometry.cellSizePx,
                rowGapPx = geometry.rowGapPx,
            )
            val naturalContentHeightPx = contentHeightForLayout(
                extent = committedExtent,
                frontier = editFrontier,
                moveActive = moveActive,
                cellSizePx = geometry.cellSizePx,
                rowGapPx = geometry.rowGapPx,
            )
            val committedRest = signedRestFor(
                viewportHeightPx = availableHeightPx,
                contentHeightPx = committedNaturalHeightPx,
                rowStridePx = rowStridePx,
            )
            val rest = if (shouldUseEditLayout(moveActive, editFrontier)) {
                signedRestFor(
                    viewportHeightPx = availableHeightPx,
                    contentHeightPx = naturalContentHeightPx,
                    rowStridePx = rowStridePx,
                )
            } else {
                committedRest
            }
            // G4: transient viewport state holder, reconciled when the rest
            // changes (resize, column adaptation, or extent update). The
            // viewport tracks the grid origin, scroll offset, and scroll range;
            // preserveAnchorOnResize handles short→tall (anchor/snap),
            // tall→short (collapse), tall→tall (clamp + bottom-pin), and
            // null→zero transitions.
            var viewport by remember { mutableStateOf(viewportFor(rest)) }
            LaunchedEffect(rest) {
                viewport = preserveAnchorOnResize(viewport, rest)
            }
            // A3: apply the rest transition to the real ScrollState. The shadow
            // viewport above only tracks the range; this effect owns the actual
            // position: clamp to the new maximum on shrink (bottom-pin, matching
            // preserveAnchorOnResize) and collapse to zero when content fits so
            // short/exact-fit content cannot scroll. It never moves otherwise, so
            // content growth never jumps the scroll position.
            val scrollMaxPx = viewport.scrollRangePx.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            LaunchedEffect(viewport) {
                if (viewport.scrollRangePx == 0L) {
                    if (scrollState.value != 0) scrollState.scrollTo(0)
                } else if (scrollState.value > scrollMaxPx) {
                    scrollState.scrollTo(scrollMaxPx)
                }
            }
            // B3: rebase the retained frontier when the loaded extent, tile set,
            // viewport, or geometry/stride changes, without shrinking an active
            // session unexpectedly. Geometry/stride (cell size, row stride,
            // viewport width) joins the trigger so width adaptation recomputes
            // the frontier for MOVE legality/auto-scroll. A satisfied pending
            // lease recomputes fresh (discards the transient extra); otherwise
            // the larger boundary is kept. The lease check runs even when the
            // frontier is null (pager-hide discards the frontier but preserves
            // the lease), so a load that lands while hidden still satisfies
            // the lease before return; an unsatisfied lease keeps gating new
            // MOVEs via the entry gate below until loaded confirms the target
            // or an explicit failure path clears it.
            LaunchedEffect(loaded.tiles, loaded.extent, availableHeightPx, rowStridePx, availableWidthPx) {
                val pending = pendingMove
                val retained = retainedPendingLease(loaded.tiles, pending)
                if (pending != null && retained == null) {
                    pendingMove = null
                    // Softlock: a satisfied load drops the transient extra. Only
                    // re-init while a MOVE is still active; an idle grid stays on
                    // committed geometry regardless of the (now cleared) lease.
                    if (editFrontier != null && editState.moveDrag != null) {
                        editFrontier = initEditFrontier(committedExtent, availableHeightPx, rowStridePx)
                    } else {
                        editFrontier = null
                    }
                    return@LaunchedEffect
                }
                val current = editFrontier ?: return@LaunchedEffect
                // Idle frontier never sizes the range, but an in-flight MOVE keeps
                // the larger boundary so growth is honored without shrinking.
                if (editState.moveDrag != null) {
                    editFrontier = rebaseEditFrontier(current, committedExtent, availableHeightPx, rowStridePx)
                } else {
                    editFrontier = null
                }
            }
            // Softlock ack: a rejected/not-found commit clears the lease + frontier
            // for the matching pending attempt id; an accepted ack retains the
            // lease until the projection reflects it. The ack carries the
            // per-attempt request id, so equal item/position retries are
            // distinct keys and each retriggers this effect. Range stays
            // committed either way.
            LaunchedEffect(moveCommitAck) {
                val ack = moveCommitAck ?: return@LaunchedEffect
                val pending = pendingMove ?: return@LaunchedEffect
                if (shouldClearLeaseOnAck(pending, ack)) {
                    pendingMove = null
                    editFrontier = null
                }
            }
            // B3 edge auto-scroll continuation for a stationary hold (the ONLY
            // scroll driver): while a MOVE drag reports its pointer inside an
            // edge band, advance one row stride per proximity-scaled interval
            // (extending the frontier via grid policy before scrolling down;
            // the top is limited by the range). Edge entry dwells 200ms before
            // the first step; the interval scales from 300ms at the inner band
            // boundary down to 120ms at the viewport edge. The drag event loop
            // never scrolls — it only retains the pointer and maps candidates
            // with the current scroll. Scrolling never calls onMoveTile:
            // release stays the sole commit.
            val autoScrollEdgePx = with(density) {
                HomeEditFrontier.AUTO_SCROLL_EDGE_DP.dp.roundToPx()
            }.coerceAtLeast(1)
            // G5: single animated grid origin. One Animatable moves the whole
            // field as one object: tiles keep their internal grid positions
            // while the shared origin glides to the new rest target. The target
            // is the signed rest origin; LaunchedEffect auto-cancels an
            // in-flight animateTo on retarget and restarts from the current
            // value, so a rapid add+remove converges on the latest target.
            // First composition, null rest, and zero-height viewports snap.
            // Declared above the ticker so the re-freeze holders below
            // observe the live animated value without restarting gestures.
            val originAnim = remember { Animatable(viewportFor(rest).gridOriginY.toFloat()) }
            val targetOriginY = originTargetFor(rest)
            var isFirstOriginFrame by remember { mutableStateOf(true) }
            LaunchedEffect(targetOriginY) {
                if (shouldSnapOrigin(rest, availableHeightPx, isFirstOriginFrame)) {
                    originAnim.snapTo(targetOriginY)
                    isFirstOriginFrame = false
                } else {
                    originAnim.animateTo(targetOriginY)
                }
            }
            val originYPx = originAnim.value.toLong()
            val originYOffsetPx = originYPx.coerceIn(
                Int.MIN_VALUE.toLong(),
                Int.MAX_VALUE.toLong(),
            ).toInt()
            val loadedSnapshot = rememberUpdatedState(loaded)
            val committedExtentState = rememberUpdatedState(committedExtent)
            val viewportHeightSnapshot = rememberUpdatedState(availableHeightPx)
            // Ticker-owned scroll inputs stay fresh via snapshot holders; the
            // MOVE frame beside the live session owns all mapping geometry.
            val tickGeometryState = rememberUpdatedState(geometry)
            val tickStrideState = rememberUpdatedState(rowStridePx)
            // Structural re-freeze inputs stay fresh the same way so the
            // ticker and drag loop detect layout drift without restarting.
            val gridMetrics = layoutMetrics.copy(restOffsetYPx = originYPx.toFloat())
            val tickMetricsState = rememberUpdatedState(gridMetrics)
            val moveTickItemId = moveDrag?.itemId
 // One MOVE generation budgets eight successful ScrollState changes in
 // either direction, including exits and re-entry. No-ops are free; the cap
 // stops the ticker, not the drag; lift/cancel ends the generation.
 LaunchedEffect(moveTickItemId) {
                if (moveTickItemId == null) return@LaunchedEffect
                var dwellZone = AutoScrollZone.NONE
                while (true) {
                    val entryY = moveEdgeYPx
                    val entryX = moveEdgeXPx
                    if (editState.moveDrag?.itemId != moveTickItemId) return@LaunchedEffect
                    val entryHeight = viewportHeightSnapshot.value
                    val entryZone = if (entryY == null || entryX == null) {
                        AutoScrollZone.NONE
                    } else {
                        autoScrollZone(entryY, entryHeight, autoScrollEdgePx)
                    }
                    if (entryZone == AutoScrollZone.NONE) {
                        dwellZone = AutoScrollZone.NONE
                        delay(HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS)
                        continue
                    }
                    if (entryZone != dwellZone) {
                        // Edge entry (or zone change): dwell, then recheck the
                        // active drag + zone before stepping. A zone change or
                        // drag end during the dwell cancels the pending step.
                        dwellZone = entryZone
                        delay(HomeEditFrontier.AUTO_SCROLL_DWELL_MS)
                        continue
                    }
                    // Sustained hold in the same zone past the dwell: recheck
                    // the live drag + zone immediately before each single-row
                    // step so a lift or zone exit cancels instead of scrolling.
                    val pointerY = moveEdgeYPx
                    val pointerX = moveEdgeXPx
                    val tickMove = editState.moveDrag ?: return@LaunchedEffect
                    if (tickMove.itemId != moveTickItemId) return@LaunchedEffect
                    if (pointerY == null || pointerX == null) {
                        dwellZone = AutoScrollZone.NONE
                        delay(HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS)
                        continue
                    }
                    val snapshotLoaded = loadedSnapshot.value
                    val snapshotHeight = viewportHeightSnapshot.value
                    val snapshotStride = tickStrideState.value
                    val zone = autoScrollZone(pointerY, snapshotHeight, autoScrollEdgePx)
                    if (zone == AutoScrollZone.NONE) {
                        dwellZone = AutoScrollZone.NONE
                        delay(HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS)
                        continue
                    }
                    if (zone != dwellZone) {
                        // Zone changed between the dwell and the step:
                        // cancel the pending step and dwell in the new zone.
                        dwellZone = zone
                        delay(HomeEditFrontier.AUTO_SCROLL_DWELL_MS)
                        continue
                    }
                    if (zone == AutoScrollZone.BOTTOM) {
                        val grown = editFrontier?.let { boundary ->
                            advanceFrontierForMove(
                                boundary,
                                tickMove.previewPosition,
                                tickMove.span.rows,
                            )
                        } ?: initEditFrontier(
                            committedExtentState.value,
                            snapshotHeight,
                            snapshotStride,
                        )
                        editFrontier = grown
                        // Bridge: adopt the current metrics before grown rows
                        // can map, even when the scroll step below is a no-op.
                        editState = editState.rebaseAndMoveTo(
                            metrics = tickMetricsState.value,
                            pointerXPx = pointerX,
                            pointerYPxViewport = pointerY,
                            scrollYPx = scrollState.value.toFloat(),
                            placements = snapshotLoaded.tiles,
                        )
                        val snapshotGeometry = tickGeometryState.value
                        val editMax = editScrollMaxPx(
                            layoutRows = editLayoutRows(committedExtentState.value, grown),
                            cellSizePx = snapshotGeometry.cellSizePx,
                            rowGapPx = snapshotGeometry.rowGapPx,
                            viewportHeightPx = snapshotHeight,
                        )
                        val target = (scrollState.value + autoScrollStepPx(snapshotStride))
                            .coerceAtMost(editMax)
                        if (target != scrollState.value) {
                            scrollState.scrollTo(target)
                            // P1: re-baseline to the ACTUAL clamped position —
                            // the live ScrollState still clamps to the older
                            // (smaller) normal maximum, so baselining the
                            // requested target would fake a foreign-scroll
                            // delta and steal-cancel the drag.
                            moveScrollBaseline = tickerScrollBaseline(
                                scrollState.value,
                                target,
                            )
                            // Stationary remap: the pointer handler's release
                            // reads the same state, so it observes this even
                            // with no further pointer events.
                            editState = editState.moveTo(
                                pointerXPx = pointerX,
                                pointerYPxViewport = pointerY,
                                scrollYPx = scrollState.value.toFloat(),
                                placements = snapshotLoaded.tiles,
                            )
                        }
                    } else {
                        val target = (scrollState.value - autoScrollStepPx(snapshotStride))
                            .coerceAtLeast(0)
                        if (target != scrollState.value) {
                            scrollState.scrollTo(target)
                            // P1: same actual-position baseline as above.
                            moveScrollBaseline = tickerScrollBaseline(
                                scrollState.value,
                                target,
                            )
                            // Same bridge and stationary remap as above.
                            editState = editState
                                .rebaseAndMoveTo(
                                    metrics = tickMetricsState.value,
                                    pointerXPx = pointerX,
                                    pointerYPxViewport = pointerY,
                                    scrollYPx = scrollState.value.toFloat(),
                                    placements = snapshotLoaded.tiles,
                                )
                                .moveTo(
                                    pointerXPx = pointerX,
                                    pointerYPxViewport = pointerY,
                                    scrollYPx = scrollState.value.toFloat(),
                                    placements = snapshotLoaded.tiles,
                                )
                        }
                    }
                    // One row per proximity-scaled interval: slower at the
                    // inner boundary, faster deep at the edge. Rechecks the
                    // active drag + zone at the top before the next step.
                    delay(
                        autoScrollIntervalMs(
                            pointerY,
                            snapshotHeight,
                            autoScrollEdgePx,
                        ) ?: HomeEditFrontier.AUTO_SCROLL_MAX_INTERVAL_MS,
                    )
                }
            }
            // A3 floating container: the natural content height plus exactly the
            // bottom clearance (`max(full, natural + clearance)`), so short content
            // fills the full viewport (no tail), tall content trails a transparent
            // reserve that scrolls the lowest tile clear of the pill, and tiles
            // stay visible behind the pill with no opaque bottom spacer. While a
            // MOVE drag is active the edit height sizes the container instead so
            // the frontier rows are reachable; idle always uses committed.
            val layoutHeightPx = if (shouldUseEditLayout(moveActive, editFrontier)) {
                naturalContentHeightPx
            } else {
                committedNaturalHeightPx
            }
            val minHeightPx = contentContainerHeightPx(
                naturalHeightPx = layoutHeightPx,
                fullHeightPx = fullViewportPx,
                bottomClearancePx = viewportStyle.bottomClearancePx,
            )
            val children: List<SpanChild<ItemId>> = loaded.tiles.map { tile ->
                val iconBitmap = iconsByIdentity[tile.appIdentity]
                // W3 renderedSpan = preview ?: persisted: while RESIZE-dragging the
                // active tile, its placement and content use the live last-valid
                // preview span so content adapts during the drag. Reduced-motion
                // snaps to the same span (no rapid animation); geometry identical.
                val renderedSpan = editState.renderedSpan(tile.itemId, tile.span)
                val effectiveTile = if (renderedSpan != tile.span) {
                    tile.copy(span = renderedSpan)
                } else {
                    tile
                }
                SpanChild(
                    placement = GridPlacement(
                        id = tile.itemId,
                        position = GridPosition(tile.position.column, tile.position.row),
                        span = GridSpan(renderedSpan.columns, renderedSpan.rows),
                    ),
                    content = {
                        HomeCanvasTile(
                            tile = effectiveTile,
                            iconBitmap = iconBitmap,
                            onLaunchApp = onLaunchApp,
                            editState = editState,
                            // C1: per-tile joined pins (empty = centered icon);
                            // arranged on the live preview span inside the tile.
                            pinnedShortcuts = pinnedByTile[tile.itemId].orEmpty(),
                            onShortcutClick = { shortcutId ->
                                onPinnedShortcutClick(tile.itemId, shortcutId)
                            },
                            // W1 recess/select: tap transfers selection directly
                            // (selectOnTap, never launch); long-press enters
                            // wiggle-editing (enterOnLongPress).
                            // Never enters Active here.
                            onSelect = { selectedId ->
                                editState = editState.selectOnTap(selectedId)
                            },
                            onEnterWiggle = { entered ->
                                editState = editState.enterOnLongPress(entered.itemId)
                            },
                        )
                    },
                )
            }
            // Latest session for drag callbacks: drag pointerInput is keyed by
            // loaded.requestedHomeId (not item id), so it stays stable through candidate
            // recomposition and resets on home switch.
            // P2 hide-abort read: the drag release path suppresses commits
            // when the page is no longer visible. Hoisted latest value so the
            // Unit/home-keyed gesture scope observes hides without restart
            // races; the drag scope itself is also keyed on isVisible below so
            // a hide cancels the in-flight await.
            val isVisibleState = rememberUpdatedState(isVisible)
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
            val tapCancelModifier = Modifier.pointerInput(editState.isEditing) {
                if (!editState.isEditing) return@pointerInput
                detectTapGestures(
                    onTap = {
                        editState = editState.exit()
                        // B3: empty-tap cancel discards the transient frontier
                        // and any pending lease with zero writes.
                        editFrontier = null
                        pendingMove = null
                        moveEdgeYPx = null
                        moveEdgeXPx = null
                    },
                )
            }
            // Idle gesture observer: P2 scroll-vs-pager axis tracking. Observes
            // without consuming, so verticalScroll, tapCancel and tile taps are
            // unaffected. The axis part keeps the [GestureArbitration.lockAxis]
            // slop gate and the [GestureArbitration.dominantAxis] diagonal guard:
            // when both axes clear slop but neither dominates, the pre-commit
            // lock holds UNDECIDED so a diagonal drag never steals the gesture
            // from either consumer. The selected tile is claimed by the edit
            // drag handler below, so the two observers never race for one
            // gesture.
            val idleGestureModifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val firstEvent = awaitPointerEvent()
                    val down = firstEvent.changes.firstOrNull { it.pressed }
                        ?: return@awaitEachGesture
                    val downId = down.id
                    val startPos = down.position
                    val slopPx = viewConfiguration.touchSlop.toFloat()
                    val snapshot = idleHitInputs.value
                    val downTile = snapshot.metrics.tileAt(
                        snapshot.tiles,
                        startPos.x,
                        snapshot.metrics.contentY(startPos.y, snapshot.scrollYPx),
                    )
                    // Edit-drag owns any tile-origin gesture while Editing. Yield to
                    // the drag handler below for the whole tile surface (geometric
                    // downTile hit, not subregions). Do not set/reset pager state
                    // here or flip it before slop. Normal tile/background downs
                    // stay observed here for axis arbitration; background
                    // horizontal swipes remain pager-eligible.
                    if (editState.isEditing && downTile != null) {
                        return@awaitEachGesture
                    }
                    // A2 single arbitration for the idle gesture: route the down-hit
                    // through resolveHomeGesture so a tile-origin touch holds the
                    // pager for the whole gesture (snapshot-once, never flipped
                    // mid-gesture even with horizontal motion). Wiggle-editing
                    // tile downs arbitrate as SELECT_DRAG, plain tile downs as
                    // TAP, empty-grid downs as BACKGROUND (pager stays free).
                    val idleWinner = resolveHomeGesture(
                        isSelectDrag = editState.isEditing && downTile != null,
                        isTap = downTile != null,
                        isBackground = downTile == null,
                    )
                    pagerHold = idleWinner.holdsPager()
                    var lock = LockedAxis.UNDECIDED
                    while (true) {
                        val nextEvent = awaitPointerEvent()
                        val nextChange = nextEvent.changes.find { it.id == downId }
                        if (nextChange == null) break
                        if (!nextChange.pressed) break
                        val dx = nextChange.position.x - startPos.x
                        val dy = nextChange.position.y - startPos.y
                        val locked = GestureArbitration.lockAxis(
                            dxPx = dx,
                            dyPx = dy,
                            slopPx = slopPx,
                            currentLock = lock,
                        )
                        // Diagonal guard applies only to the initial commit: once
                        // committed the lock is preserved mid-gesture per the
                        // lockAxis contract and never flips back.
                        val diagonalHold = lock == LockedAxis.UNDECIDED &&
                            locked != LockedAxis.UNDECIDED &&
                            abs(dx) >= slopPx && abs(dy) >= slopPx &&
                            GestureArbitration.dominantAxis(
                                dxPx = dx,
                                dyPx = dy,
                                jitterPx = slopPx,
                            ) == LockedAxis.UNDECIDED
                        val next = if (diagonalHold) LockedAxis.UNDECIDED else locked
                        if (next != lock) {
                            lock = next
                            pagerAxisLock = next
                        }
                    }
                    if (lock != LockedAxis.UNDECIDED) {
                        pagerAxisLock = LockedAxis.UNDECIDED
                    }
                    // A2 release: tile-touch hold ends with the gesture.
                    pagerHold = false
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
            // This handler is also used for a selected tile in wiggle editing;
            // in either route the pointer is already down when this block starts.
            // It must NOT use awaitFirstDown()
            // (which waits for a fresh down that never arrives). Instead the first
            // awaitPointerEvent() observes the already-pressed pointer and tracks it
            // past touch slop for a continuous press-hold-drag.
            // P2 hide-abort: keyed on isVisible as well as the home id so a
            // pager navigation away cancels the in-flight await (zero-write);
            // the LaunchedEffect(isVisible) above already discards Compose
            // session/frontier/lease/edge/hold state. Release commits below
            // additionally suppress onResize/onMove when !isVisible.
            val dragModifier = Modifier.pointerInput(loaded.requestedHomeId, isVisible) {
                awaitEachGesture {
                    // Pointer is already down from the long-press that entered edit mode.
                    // Observe the current pressed pointer instead of waiting for a new down.
                    val firstEvent = awaitPointerEvent()
                    val down = firstEvent.changes.firstOrNull { it.pressed }
                        ?: return@awaitEachGesture
                    val downId = down.id
                    val startPos = down.position
                    // Pointer coordinates are viewport-local. Freeze the viewport
                    // translation at down so a scroll/gesture arbitration change
                    // cannot remap the same drag underneath the pointer.
                    val gestureScrollYPx = scrollState.value.toFloat()
                    val editAtDown = editState
                    val hitSnapshot = idleHitInputs.value
                    val downTile = hitSnapshot.metrics.tileAt(
                        hitSnapshot.tiles,
                        startPos.x,
                        hitSnapshot.metrics.contentY(startPos.y, gestureScrollYPx),
                    )
                    val selectedTile = editAtDown.selectedTileId
                        ?.let { selectedId -> hitSnapshot.tiles.firstOrNull { it.itemId == selectedId } }
                    val selectedBounds = selectedTile?.let { tile ->
                        hitSnapshot.metrics.signedFrame(tile.position, tile.span)
                    }
                    val downTileBounds = downTile?.let { tile ->
                        hitSnapshot.metrics.signedFrame(tile.position, tile.span)
                    }
                    val selectedResize = selectedBounds?.let {
                        isInHandle(
                            startPos.x,
                            startPos.y + gestureScrollYPx,
                            it,
                            with(density) { RESIZE_TOUCH_DP.dp.roundToPx() },
                        )
                    } == true
                    // A3 unified edit-drag: while Editing any tile-origin down
                    // (whole tile surface via the geometric downTile hit above,
                    // never subregions) is a MOVE candidate. The selected tile
                    // keeps its resize-arm snapshot/route below; a non-selected
                    // tile transfers selection past slop into a direct MOVE
                    // with no resize interpretation.
                    val isTransferDrag = isEditTransferDrag(editAtDown, downTile?.itemId)
                    // Preserve the point grabbed inside the dragged tile. This is
                    // content-space state, while startPos remains viewport-space.
                    // Transfer drags anchor on the down tile; selected drags keep
                    // the selected bounds (identical when down hits selected).
                    val anchorBounds = downTileBounds ?: selectedBounds
                    val grabOffsetXPx = anchorBounds?.let {
                        startPos.x - it.left.toFloat()
                    } ?: 0f
                    val grabOffsetYPx = anchorBounds?.let {
                        startPos.y + gestureScrollYPx - it.top.toFloat()
                    } ?: 0f
                    val selectedDrag = editAtDown.isEditing &&
                        (downTile != null || selectedResize)
                    val dragTile = downTile ?: selectedTile?.takeIf { selectedResize }
                    // A2-S1: capture the touch target/zone at down. Sample resize
                    // arm when slop is crossed, then fix the route: armed handle
                    // => RESIZE, unarmed handle => NONE, body => MOVE. The
                    // tap-only path remains unchanged.
                    val armedAtDown = editAtDown.isResizeArmedFor(dragTile?.itemId)
                    if (!selectedDrag) return@awaitEachGesture
                    // B3: gate another MOVE while a commit is still in flight.
                    // The frontier stays leased to the pending target until the
                    // loaded tiles reflect it (rebase effect above clears it).
                    if (!isPendingCommitSatisfied(loaded.tiles, pendingMove)) {
                        if (selectedDrag) pagerHold = false
                        return@awaitEachGesture
                    }
                    if (selectedDrag) pagerHold = true
                    // Wait until the already-pressed pointer moves past touch slop.
                    // Release before slop means no drag (a tap; tapCancel handles it).
                    // A2-S1: an arrow-tap on the selected tile is consumed by the
                    // handle's own tap action, which toggles the resize arm with no
                    // write and no enterSelectedDrag — this path stays a no-op for it.
                    // P2: the slop gate is the shared [GestureArbitration.lockAxis]
                    // predicate, not an inline distance check.
                    val touchSlopPx = viewConfiguration.touchSlop.toFloat()
                    var slop: PointerInputChange? = null
                    var slopAxis = LockedAxis.UNDECIDED
                    while (slop == null) {
                        val nextEvent = awaitPointerEvent()
                        val nextChange = nextEvent.changes.find { it.id == downId }
                        if (nextChange == null || !nextChange.pressed) {
                            if (selectedDrag) pagerHold = false
                            return@awaitEachGesture
                        }
                        val dx = nextChange.position.x - startPos.x
                        val dy = nextChange.position.y - startPos.y
                        // Use the home arbitration diagonal guard rather than
                        // choosing horizontal for every selected drag. A diagonal
                        // motion remains undecided until one axis is genuinely
                        // dominant; once chosen, the owner stays stable.
                        val dragLock = classifyHomeAxis(
                            dxPx = dx,
                            dyPx = dy,
                            slopPx = touchSlopPx,
                            jitterPx = touchSlopPx,
                            currentLock = slopAxis,
                        )
                        if (dragLock != LockedAxis.UNDECIDED) {
                            slopAxis = dragLock
                            nextChange.consume()
                            slop = nextChange
                        }
                    }
 // Sample arm at slop: armed handle => RESIZE, unarmed handle => NONE,
 // body => MOVE. The target/zone captured at down fixes the route once;
 // it is never re-tested mid-gesture.
 val slopChange = slop ?: return@awaitEachGesture
                    // A3 transfer: a past-slop drag on a non-selected edit tile
                    // moves that tile and transfers selection to it. Below slop
                    // this block never runs, so taps keep the child-tap path
                    // (select without move). The slop consume above already
                    // claimed the gesture so child launch/shortcut taps cannot
                    // fire once this transfer runs.
                    if (isTransferDrag && dragTile != null) {
                        editState = editState.selectOnTap(dragTile.itemId)
                    }
                    val originTile = dragTile ?: run {
                        pagerHold = false
                        return@awaitEachGesture
                    }
                    // The transient MOVE frontier initializes only after routing
                    // confirms MOVE (see the MOVE branch below); RESIZE never
                    // creates or grows it.
                    // W3 single-BR-only: the origin corner is snapshotted ONCE at
                    // drag start from the ORIGIN span (top-left fixed anchor).
                    val originBounds = hitSnapshot.metrics.signedFrame(
                        originTile.position,
                        originTile.span,
                    )
                    val handleSizePx = with(density) { RESIZE_TOUCH_DP.dp.roundToPx() }
                    val startInHandle = isInHandle(
                        startPos.x,
                        startPos.y + gestureScrollYPx,
                        originBounds,
                        handleSizePx,
                    )
                    // A2-S1 route-once from the down snapshot (arrow-hit vs the fixed
                    // origin footprint, armed flag captured at down): an armed
                    // arrow-origin routes RESIZE via the gated beginResize below, a
                    // body-origin routes MOVE with the snapshotted span (entry fixed
                    // originSpan at gesture start, never the live preview) and clears
                    // the arm, and an unarmed arrow-origin routes to neither with
                    // zero writes. No mid-gesture switch: the branches below never
                    // re-test the pointer position.
                    // B2: RESIZE routes only on startInHandle (hold position in
                    // the handle corner). A body-origin press never upgrades to
                    // RESIZE via the slop position; MOVE stays authoritative
                    // with snapshot-once routing and no mid-gesture promotion.
                    // A3: a transfer drag (non-selected edit tile) routes direct
                    // MOVE with no resize interpretation — the selected-tile
                    // resize-arm snapshot/route above is unchanged.
                    val route = transferDragRoute(
                        isTransfer = isTransferDrag,
                        arrowHit = startInHandle,
                        armed = armedAtDown,
                        hasTile = true,
                    )
                    if (route == DownDragRoute.NONE) {
                        // Unarmed arrow-drag: neither move nor resize, zero
                        // writes; the selection stays wiggle-editing and the
                        // tile-touch hold ends with the gesture.
                        pagerHold = false
                        return@awaitEachGesture
                    }
                    val isOnHandle = route == DownDragRoute.RESIZE
                    // Body-drag while armed stays move and clears the arm (inside
                    // beginMove), so a later arrow-drag needs a fresh arrow-tap.
                    // beginResize is reachable only for an armed arrow-origin.
                    editState = if (isOnHandle) {
                        editState.beginResize(originTile, tickMetricsState.value)
                    } else {
                        editState.beginMove(
                            tile = originTile,
                            placements = loaded.tiles,
                            metrics = tickMetricsState.value,
                            grabOffsetXPx = grabOffsetXPx,
                            grabOffsetYPx = grabOffsetYPx,
                        )
                    }
                    if (!editState.isDragging) {
                        pagerHold = false
                        return@awaitEachGesture
                    }
                    // B3/P2: MOVE-only frontier init lives here, after routing
                    // confirms MOVE — never on entry, never for RESIZE.
                    if (shouldInitMoveFrontier(route, editFrontier)) {
                        editFrontier = initEditFrontier(committedExtent, availableHeightPx, rowStridePx)
                    }
                    // A2 single arbitration for the active drag: route the
                    // start-decided routing through resolveHomeGesture so the
                    // drag holds the pager for its lifetime. Handle-origin →
                    // HANDLE/ACTIVE_RESIZE, body-origin → ACTIVE_MOVE; all hold
                    // (snapshot-once, never flipped mid-gesture; steal-cancel
                    // keeps last-valid with zero-write per W4).
                    val dragWinner = resolveHomeGesture(
                        isOnHandle = isOnHandle,
                        isActiveResize = isOnHandle,
                        isActiveMove = !isOnHandle,
                    )
                    pagerHold = dragWinner.holdsPager()
                    if (isOnHandle) {
                        // RESIZE mode: corner-anchored span updates; the state
                        // can never change the position.
                        // W4: track initial scroll and pager lock for steal detection.
                        val resizeStartScroll = scrollState.value
                        val resizeStartPagerLock = pagerAxisLock
                        var resizeStolen = false
                        // B3 re-review P1: explicit pointer-up vs cancellation.
                        // Only an observed lift (non-null change with pressed
                        // false) completes the gesture toward commit. A null
                        // await (lost pointer id), a consumed change, or scope
                        // cancellation takes the zero-write cancel path below
                        // and never reaches the release commit helpers.
                        var resizeCancelled = false
                        var resizeCompletedByUp = false
                        while (true) {
                            // W4: break on pager flip or scroll delta (steal attempt).
                            // Steal fails: keeps last-valid, zero-write on cancel.
                            if (pagerAxisLock != resizeStartPagerLock ||
                                scrollState.value != resizeStartScroll
                            ) {
                                resizeStolen = true
                                break
                            }
                            val resizeEvent = awaitPointerEvent()
                            val next = resizeEvent.changes.find { it.id == downId }
                            when (classifyPointerEnd(next == null, next?.pressed == true, next?.isConsumed == true)) {
                                PointerEndDecision.CANCEL -> {
                                    resizeCancelled = true
                                    break
                                }
                                PointerEndDecision.LIFT -> {
                                    resizeCompletedByUp = true
                                    break
                                }
                                PointerEndDecision.CONTINUE -> Unit
                            }
                            val change = next ?: break
                            change.consume()
                            if (editState.resizeDrag == null) break
                            editState = editState
                                .rebaseMetrics(tickMetricsState.value)
                                .resizeTo(
                                    pointerXPx = change.position.x,
                                    pointerYPxViewport = change.position.y,
                                    scrollYPx = scrollState.value.toFloat(),
                                    placements = loaded.tiles,
                                )
                        }
                        if (resizeCancelled) {
                            // Zero-write cancel: a cancelled (never lifted)
                            // gesture commits nothing and never calls the
                            // release helpers — even with an Active live
                            // session while still visible. RESIZE never owns
                            // the MOVE frontier, so it is left untouched.
                            // B3 P2 steal cleanup: the explicit cancel still
                            // releases a retained pending MOVE lease (hide is
                            // not a failure and never reaches this branch).
                            if (shouldClearPendingLeaseOnExit(
                                    cancelled = resizeCancelled,
                                    stolen = resizeStolen,
                                )
                            ) {
                                pendingMove = null
                            }
                            editState = editState.cancelDrag()
                            pagerAxisLock = LockedAxis.UNDECIDED
                            pagerHold = false
                        } else {
                            // Release commits the held last-valid span iff it
                            // differs from the origin, suppressed when stolen,
                            // hidden or not completed by an explicit lift (a
                            // pager-hide mid-gesture is zero-write even if the
                            // cancelled await still unwinds here). Resize stays
                            // in edit mode with the selection and arm retained.
                            val finished = editState.finish()
                            val resizeCommit = releaseCommit(
                                finished = finished,
                                stolen = resizeStolen,
                                isVisible = isVisibleState.value,
                                completedByUp = resizeCompletedByUp,
                            ) as? HomeEditCommit.Resize
                            if (resizeCommit != null) {
                                onResizeTile(resizeCommit.itemId, resizeCommit.span)
                            } else if (
                                shouldClearPendingLeaseOnExit(cancelled = false, stolen = resizeStolen)
                            ) {
                                // A steal releases a retained pending MOVE lease;
                                // a hide or stale break retains it for the rebase
                                // reconcile. The frontier stays untouched: RESIZE
                                // never owns it.
                                pendingMove = null
                            }
                            editState = finished.state
                            // W4: reset pager axis lock once after resize release/cancel.
                            // A2: tile-touch hold ends with the drag; the session stays
                            // in edit so the A1 isHold signal keeps holding the pager.
                            pagerAxisLock = LockedAxis.UNDECIDED
                            pagerHold = false
                        }
                    } else {
                        // MOVE mode: pointer position maps to a candidate cell.
                        // W3 single-gesture rule (W4 owns arbitration): routing was
                        // decided at drag start above, so this branch stays MOVE
                        // until release — it never promotes into the handle corner.
                        // The frame below freezes layout origin, strides,
                        // columns, span and the scroll baseline plus
                        // the down grab offsets; every later remap (event or
                        // ticker) shares it with only the scroll value live.
                        editState = editState.moveTo(
                            pointerXPx = slopChange.position.x,
                            pointerYPxViewport = slopChange.position.y,
                            scrollYPx = scrollState.value.toFloat(),
                            placements = loaded.tiles,
                        )
                        // Hoist the latest pointer for the stationary ticker,
                        // refreshed per pointer event below.
                        moveEdgeXPx = slopChange.position.x
                        moveEdgeYPx = slopChange.position.y
                        // W4: track initial scroll and pager lock for steal detection.
                        // B3: the baseline below absorbs auto-scroll-owned steps
                        // (each intentional step re-baselines), so only foreign
                        // scroll deltas and pager flips steal.
                        moveScrollBaseline = scrollState.value
                        val moveStartPagerLock = pagerAxisLock
                        var moveStolen = false
                        // B3 re-review P1: explicit pointer-up vs cancellation —
                        // same contract as the RESIZE loop above. Only an
                        // observed lift completes toward commit; null/consumed
                        // takes the zero-write cancel path with frontier
                        // cleanup, never calling the release commit helper.
                        var moveCancelled = false
                        var moveCompletedByUp = false
                        while (true) {
                            // W4: break on pager flip or foreign scroll delta
                            // (steal attempt). Steal fails: keeps last-valid,
                            // zero-write on cancel.
                            if (pagerAxisLock != moveStartPagerLock ||
                                scrollState.value != moveScrollBaseline
                            ) {
                                moveStolen = true
                                break
                            }
                            val moveEvent = awaitPointerEvent()
                            val next = moveEvent.changes.find { it.id == downId }
                            when (classifyPointerEnd(next == null, next?.pressed == true, next?.isConsumed == true)) {
                                PointerEndDecision.CANCEL -> {
                                    moveCancelled = true
                                    break
                                }
                                PointerEndDecision.LIFT -> {
                                    moveCompletedByUp = true
                                    break
                                }
                                PointerEndDecision.CONTINUE -> Unit
                            }
                            val moveChange = next ?: break
                            moveChange.consume()
                            // The ticker's stationary remap writes the same state,
                            // so a held pointer's scroll progress is never lost.
                            if (editState.moveDrag == null) break
                            // B3 stationary ticker owns all auto-scroll: this loop
                            // only retains the latest viewport-local pointer for
                            // the ticker and maps the candidate with the current
                            // scroll. No per-event edge step, frontier growth,
                            // or raw dispatch here (prevents event+ticker
                            // double-step runaway).
                            moveEdgeXPx = moveChange.position.x
                            moveEdgeYPx = moveChange.position.y
                            // Bridge: adopt layout drift (frontier growth, origin
                            // or stride change) before mapping; the mapper then
                            // uses only the pointer, live scroll and the frame.
                            editState = editState
                                .rebaseMetrics(tickMetricsState.value)
                                .moveTo(
                                    pointerXPx = moveChange.position.x,
                                    pointerYPxViewport = moveChange.position.y,
                                    scrollYPx = scrollState.value.toFloat(),
                                    placements = loaded.tiles,
                                )
                        }
                        // P1/P2 release: commit from the one edit state — the
                        // ticker's stationary remap writes it, so a
                        // hold-scrolled-then-released drag commits the
                        // post-scroll cell even with no further pointer events.
                        // Stolen, hidden, or cancelled (never lifted) gestures
                        // are zero-write and never reach the commit helper.
                        val finished = editState.finish()
                        // W4: skip commit when stolen (zero-write on cancel).
                        // B3: release is the sole commit — auto-scroll steps above
                        // never call onMoveTile. A valid commit leases the frontier
                        // entry gate to the pending target until loaded reflects
                        // it (idle range stays committed); every no-commit end —
                        // cancel, steal, hide, stale break, invalid or no-move
                        // lift — discards the frontier with zero writes and
                        // clamps back to the committed range. Only an explicit
                        // lift commits.
                        val commit = if (moveCancelled) {
                            null
                        } else {
                            (
                                releaseCommit(
                                    finished = finished,
                                    stolen = moveStolen,
                                    isVisible = isVisibleState.value,
                                    completedByUp = moveCompletedByUp,
                                ) as? HomeEditCommit.Move
                            )?.position
                        }
                        val movedItemId = (finished.commit as? HomeEditCommit.Move)?.itemId
                        moveEdgeYPx = null
                        moveEdgeXPx = null
                        // B3 P2 steal cleanup: explicit cancel/steal exits release
                        // the pending lease with the frontier; a pager-hide or
                        // stale break discards only the frontier/gesture and
                        // retains the lease for the rebase reconcile below
                        // (hide is not a failure). A no-commit lift (invalid or
                        // no-move) discards the frontier with zero writes; a
                        // valid pending lease never inflates the idle range.
                        val clearPendingOnExit = shouldClearPendingLeaseOnExit(
                            cancelled = moveCancelled,
                            stolen = moveStolen,
                        )
                        if (moveCancelled) {
                            editFrontier = null
                            pendingMove = null
                        } else if (movedItemId != null && commit != null) {
                            moveRequestSeq += 1
                            val attemptId = moveRequestSeq
                            onMoveTile(movedItemId, commit, attemptId)
                            pendingMove = PendingMoveCommit(movedItemId, commit, attemptId)
                        } else if (moveStolen || !isVisibleState.value || !moveCompletedByUp) {
                            editFrontier = null
                            if (clearPendingOnExit) {
                                pendingMove = null
                            }
                        } else if (shouldDiscardFrontierOnMoveEnd(
                                commit = commit,
                                cancelled = moveCancelled,
                                stolen = moveStolen,
                                isVisible = isVisibleState.value,
                                completedByUp = moveCompletedByUp,
                            )
                        ) {
                            // No-commit lift (invalid candidate or no-move):
                            // zero-write discard to the committed range.
                            editFrontier = null
                        }
                        editState = finished.state
                        // W4: reset pager axis lock once after move release/cancel.
                        // A2: tile-touch hold ends with the drag; edit mode keeps
                        // holding the pager through the A1 signal.
                        pagerAxisLock = LockedAxis.UNDECIDED
                        pagerHold = false
                    }
                }
            }
            // A3: vertical scroll only on exact committed overflow (plus the
            // active-edit and horizontal-pager guards); short/exact-fit content
            // never scrolls. Idle uses the committed range even with a retained
            // valid pending lease; only an active MOVE sizes from the frontier.
            val committedScrollable = (committedRest?.scrollRangePx ?: 0L) > 0L
            val canNormalScroll = if (moveActive) {
                viewport.scrollRangePx > 0L
            } else {
                committedScrollable
            }
            Box(
                modifier = Modifier
                    .verticalScroll(
                        scrollState,
                        enabled = canNormalScroll && !dragging &&
                            pagerAxisLock != LockedAxis.HORIZONTAL,
                    )
                    .then(tapCancelModifier)
                    .then(dragModifier)
                    .then(if (dragging) Modifier else idleGestureModifier)
                    .testTag(HOME_CANVAS_GRID_TAG),
            ) {
                WindowedSpanContainer(
                    modifier = Modifier.offset { IntOffset(0, originYOffsetPx) },
                    children = children,
                    window = window,
                    overscanRows = overscanRows,
                    geometry = geometry,
                    originX = originX,
                    rowOrigin = 0L,
                    minHeightPx = minHeightPx,
                )
                if (moveDrag != null) {
                    MoveCandidateOverlay(move = moveDrag, metrics = gridMetrics)
                }
                val resizeDrag = editState.resizeDrag
                if (resizeDrag != null) {
                    // W3 preview overlay: frame at the held last-valid span (blocked
                    // proposals keep the frame; validity flips the valid/error
                    // color). Animated and reduced-motion branches share
                    // [resizePreviewSpan], so geometry is identical.
                    val previewSpan = resizePreviewSpan(
                        resize = resizeDrag,
                        reducedMotion = reducedMotion,
                    )
                    ResizeCandidateOverlay(
                        originPosition = resizeDrag.position,
                        candidateSpan = LogicalSpan(previewSpan.columns, previewSpan.rows),
                        metrics = gridMetrics,
                        isValid = resizeDrag.validity == CandidateValidity.VALID,
                    )
                }
                // Resize handle overlay: the centered 48dp visual/touch Box is positioned
                // over the selected/active tile's bottom-right corner. It is separate from
                // the parent's inward-only 48dp routing hit rect: the parent receives only
                // points in the tile's [right - touch, right] × [bottom - touch, bottom]
                // corner, while this Box remains center-pinned and may extend outward.
                // It is INSIDE the scrolling content so it shares the tiles/overlays
                // coordinate space and tracks scroll. W3 single-BR-only: the touch
                // target is exactly RESIZE_TOUCH_DP inward over the corner
                // ([right - touch, right] x [bottom - touch, bottom]), never
                // outward, so edge tiles never clip.
                val controlItemId = editState.selectedTileId
                if (controlItemId != null) {
                    val activeTile = loaded.tiles.find { it.itemId == controlItemId }
                    if (activeTile != null) {
                        // Handle tracks the live last-valid preview span (not the
                        // persisted span) so it stays on the growing/shrinking
                        // corner during a resize drag.
                        // Frame from `gridMetrics`: its top is final Box content coordinates.
                        val previewSpan = editState.renderedSpan(controlItemId, activeTile.span)
                        val tileBounds = gridMetrics.signedFrame(
                            moveDrag?.originalPosition ?: activeTile.position,
                            previewSpan,
                        )
                        val touchPx = with(density) { RESIZE_TOUCH_DP.dp.roundToPx() }
                        val handleOffset = cornerControlOffset(
                            tileBounds = tileBounds,
                            controlSizePx = touchPx,
                            corner = ControlCorner.BOTTOM_RIGHT,
                        )
                        // Resize handle: tap-only arm/disarm for the selected tile.
                        // The tap consumes the arrow-tap so the tile below never
                        // transfer-clears it (below slop toggles armed with no write
                        // and no enterSelectedDrag); past slop the tap cancels and
                        // the parent Box's unified drag handler owns routing. No drag
                        // detector is installed here — a second one would lose the
                        // slop race and leave the handle unreachable.
                        val handleLabel = if (activeTile.label.isNullOrBlank()) {
                            UNAVAILABLE_LABEL
                        } else {
                            activeTile.label
                        }
                        Box(
                            modifier = Modifier
                                .offset { handleOffset }
                                .size(RESIZE_TOUCH_DP.dp),
                        ) {
                            ResizeHandle(
                                label = handleLabel,
                                armed = editState.isResizeArmedFor(controlItemId),
                                onToggleArmed = {
                                    if (editState.selectedTileId == controlItemId) {
                                        editState = editState.toggleResizeArmed()
                                    }
                                },
                            )
                        }
                        // Remove affordance: sibling overlay over the active tile in
                        // both MOVE and RESIZE modes. Tracks the live candidate span
                        // (same footprint as the handle) so it stays attached during
                        // a resize drag. A5 placement: 1x1/1x2/2x1 pin top-right,
                        // any larger preview span centers the control in the tile
                        // footprint; the flip is driven by the live preview span so
                        // a resize across the boundary re-anchors mid-drag. Tap
                        // reports onRemoveTile only; the grid performs no
                        // persistence delete and issues no move/resize.
                        val removeLabel = handleLabel
                        val buttonPx = with(density) { CORNER_CONTROL_TOUCH_SIZE.dp.roundToPx() }
                        val removePlacement = removeControlPlacement(
                            columns = previewSpan.columns,
                            rows = previewSpan.rows,
                        )
                        val removeAbsolute = removeControlOffset(
                            tileBounds = tileBounds,
                            controlSizePx = buttonPx,
                            placement = removePlacement,
                        )
                        // Signed frame already carries the row origin + rest offset, so
                        // the remove anchor is final Box content coordinates.
                        val removeTop = removeAbsolute.y
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(removeAbsolute.x, removeTop) }
                                .size(CORNER_CONTROL_TOUCH_SIZE.dp),
                        ) {
                            RemoveButton(
                                label = removeLabel,
                                onRemove = {
                                    onRemoveTile(controlItemId)
                                    // B2: unpin selection → Editing(null) + cancel
                                    // Active → Idle; stays in wiggle-edit.
                                    editState = editState.onTileRemoved(controlItemId)
                                    // B3: removal ends the transient edit too.
                                    editFrontier = null
                                    pendingMove = null
                                    moveEdgeYPx = null
                                    moveEdgeXPx = null
                                },
                            )
                        }
                    }
                }
                // B4 edit-mode shortcut menu: sibling overlay above the W1-selected
                // tile, inside the scrolling content so it shares the tiles'
                // coordinate space and tracks scroll. Follows
                // (interaction as? Editing)?.selectedId: long-press shows,
                // tap-other transfers with no Normal round-trip, and every
                // dismiss path (empty-tap/Back/navigate to Normal, move/resize
                // start to Active, removal/uninstall via a null selection or a
                // tile-lookup miss, zero shortcuts) uncomposes the menu with
                // zero activation. Only a row tap reports onSurfaceExecute/
                // onSurfaceTogglePin with the selected ItemId and entry id;
                // the menu never moves, resizes or pages tiles. The visible
                // window is the grid scroll offset plus the app-safe usable
                // height (pill/gesture reserve already excluded upstream);
                // minHeightPx stays the WindowedSpanContainer minimum only.
                val menuSelectedId = editState.selectedTileId
                // B1 owner gate: rows scoped to another tile (stale A rows after
                // an A→B switch) hide honestly until the caller's rows for B
                // arrive; unscoped (null owner) rows keep the B4 behavior.
                if (isEditShortcutMenuVisible(dragging, menuSelectedId, loaded.tiles, surfaceEntries) &&
                    isSurfaceEntriesForSelection(surfaceEntriesTileId, menuSelectedId)
                ) {
                    val menuTile = loaded.tiles.find { it.itemId == menuSelectedId }
                    if (menuTile != null) {
                        EditShortcutMenu(
                            tile = menuTile,
                            entries = surfaceEntries,
                            metrics = gridMetrics,
                            availableWidthPx = availableWidthPx,
                            visibleTopPx = scrollState.value,
                            visibleBottomPx = scrollState.value + availableHeightPx.coerceAtLeast(1),
                            onSurfaceExecute = onSurfaceExecute,
                            onSurfaceTogglePin = onSurfaceTogglePin,
                        )
                    }
                }
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
 * A3 transfer-drag predicate: true when [editState] is in edit mode
 * and the geometric down hit names a tile
 * other than the current selection (including a null selection — any
 * edit-mode tile down transfers).
 *
 * Whole-tile surface only: callers pass the geometric downTile id (or null
 * for background), never a subregion. Pure, framework-free and unit-testable.
 */
internal fun isEditTransferDrag(
    editState: HomeEditState,
    downTileId: ItemId?,
): Boolean {
    if (!editState.isEditing || downTileId == null) return false
    return downTileId != editState.selectedTileId
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

/**
 * B3 P2 steal cleanup: whether a gesture exit clears the pending MOVE lease.
 *
 * Explicit failure exits — a cancelled (never lifted) gesture or a
 * pager/scroll steal — release the lease alongside the transient frontier
 * with zero writes. Every other exit maps to `(cancelled = false,
 * stolen = false)` at the call site and retains the lease: a pager-hide
 * mid-gesture (hide is not a failure — the rebase effect reconciles the
 * retained lease against loaded tiles), a stale loop break with no lift, and
 * a no-move lift that retains the bounded transient. The satisfied-load
 * clear lives in [retainedPendingLease], not here. Shared single source for
 * the MOVE and RESIZE exit sites; pure, framework-free.
 */
internal fun shouldClearPendingLeaseOnExit(cancelled: Boolean, stolen: Boolean): Boolean =
    cancelled || stolen

/**
 * Release commit from the pointer loop's single state.
 *
 * Returns [HomeEditFinish.commit] unless the gesture was stolen, the page hid
 * mid-gesture ([isVisible] false), or the loop exited without an explicit
 * pointer-up ([completedByUp] false — null await, coroutine cancellation, or a
 * consumed change), in which case null (zero-write).
 */
internal fun releaseCommit(
    finished: HomeEditFinish,
    stolen: Boolean,
    isVisible: Boolean,
    completedByUp: Boolean = true,
): HomeEditCommit? {
    if (stolen || !isVisible || !completedByUp) return null
    return finished.commit
}

/**
 * MOVE-only frontier init gate (B3 P2).
 *
 * The transient edit frontier initializes only after routing confirms MOVE
 * with no retained frontier yet. RESIZE (and NONE) never create it, so an
 * armed resize drag keeps the normal bound and never expands the layout.
 * Pure, framework-free.
 */
internal fun shouldInitMoveFrontier(
    route: DownDragRoute,
    existing: GridVerticalLayout.EditBoundary?,
): Boolean = route == DownDragRoute.MOVE && existing == null

/**
 * A1 hold predicate: transient home state that must hold pager swipe. Edit mode
 * (which includes any drag) holds it. The caller (shell) ANDs the negation into
 * `userScrollEnabled`; the pill's programmatic `animateScrollToPage` is unaffected.
 */
internal fun isHomeHold(editState: HomeEditState): Boolean = editState.isEditing

/**
 * P0 empty-guard hold signal for [HomeCanvasGrid].
 *
 * The grid early-returns on empty tiles before transient edit state
 * exists, so an empty grid must never hold regardless of stale inputs: empty
 * forces false, non-empty delegates to [isHomeHold]. The composable emits false
 * via `LaunchedEffect` on the empty path and on dispose, so shell `homeHold`
 * resets on populated→empty transitions and on grid removal.
 */
internal fun gridHoldSignal(
    tilesEmpty: Boolean,
    editState: HomeEditState,
): Boolean = if (tilesEmpty) false else isHomeHold(editState)

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
 * Rest [GridRest.gridOriginY], or `0L` when no rest is available. May be negative
 * (tall content anchored above) or positive (short content bias centering); it
 * becomes [HomeGridMetrics.restOffsetYPx].
 */
internal fun rendererOriginYPx(rest: GridRest?): Long = rest?.gridOriginY ?: 0L

/**
 * G5 animated-origin target: the signed rest origin as a Float for the single
 * origin [Animatable] in [HomeCanvasGrid].
 *
 * The rest is the [signedRestFor] result, so this equals the signed
 * `gridOriginY` target (bias centering for short content, zero anchor for tall
 * content); a `null` rest yields `0`. Placements are untouched — only the
 * shared origin moves.
 */
internal fun originTargetFor(rest: GridRest?): Float = viewportFor(rest).gridOriginY.toFloat()

/**
 * G5 snap predicate: snap (no animation) on the first composition, when the
 * rest is `null` (invalid viewport height or rest arithmetic overflow), or when
 * the viewport height is zero (nothing to animate against). Otherwise the
 * shared origin animates toward the new target.
 */
internal fun shouldSnapOrigin(
    rest: GridRest?,
    viewportHeightPx: Int,
    isFirstFrame: Boolean,
): Boolean = isFirstFrame || rest == null || viewportHeightPx <= 0

/**
 * Derives absolute content bounds from the loaded render tiles for the renderer
 * and row-window extent.
 *
 * Maps each tile to a [GridPlacement] once and delegates to the absolute helper;
 * returns `null` for empty input. Column legality and row extent stay owned by
 * `core:grid`.
 */
internal fun contentBoundsForTiles(tiles: List<HomeRenderTile>, columns: Int): ContentBounds? {
    if (tiles.isEmpty()) return null
    val placements = tiles.map { tile ->
        GridPlacement(
            id = tile.itemId,
            position = GridPosition(tile.position.column, tile.position.row),
            span = GridSpan(tile.span.columns, tile.span.rows),
        )
    }
    return GridContentBounds.absoluteContentBoundsOf(placements, columns)
}

/**
 * Computes the G2 rest offset for the content.
 *
 * The rest is computed with `minRow = 0`: row zero is the permanent origin and
 * `gridOriginY` is the offset added on top of it (bias centering for short
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

/**
 * Floating scroll-container height for the home canvas grid.
 *
 * `max(fullHeightPx, naturalHeightPx + bottomClearancePx)` with checked `Long`
 * arithmetic, saturating to [Int.MAX_VALUE] instead of wrapping: short and
 * exact-fit-safe content fills the full viewport (no tail), while taller content
 * trails exactly the bottom clearance as a transparent reserve — no opaque bottom
 * spacer — so the scroll range (`container - full`) equals the safe overflow
 * `max(0, natural - safe)` and the lowest tile lands clear of the floating pill.
 * Pure, framework-free and unit-testable.
 */
internal fun contentContainerHeightPx(
    naturalHeightPx: Int,
    fullHeightPx: Int,
    bottomClearancePx: Int,
): Int {
    require(naturalHeightPx >= 0) { "naturalHeightPx must be non-negative" }
    require(fullHeightPx >= 0) { "fullHeightPx must be non-negative" }
    require(bottomClearancePx >= 0) { "bottomClearancePx must be non-negative" }
    return try {
        val withReserve = Math.addExact(naturalHeightPx.toLong(), bottomClearancePx.toLong())
        val capped = withReserve.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        maxOf(fullHeightPx, capped)
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
}

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
private data class IdleHitSnapshot(
    val tiles: List<HomeRenderTile>,
    val metrics: HomeGridMetrics,
    val scrollYPx: Float,
)

/**
 * Resize preview span for the RESIZE overlay, handle, and tile content.
 *
 * W3 `renderedSpan = preview ?: persisted`: returns the held last-valid span
 * ([DragState.Resize.previewSpan]) for a RESIZE drag, which is the
 * frame both the animated path and the reduced-motion snap path render — the
 * [reducedMotion] flag only selects snap-vs-animate presentation, never a
 * different span, so geometry is identical either way. Pure math,
 * framework-free. Legality stays owned by `core:grid`.
 */
internal fun resizePreviewSpan(
    resize: DragState.Resize,
    reducedMotion: Boolean = false,
): GridSpan {
    // Snap branch (reduced-motion) and animated branch share one source: the
    // held last-valid preview. No separate span computation per branch.
    val preview = resize.previewSpan
    return if (reducedMotion) {
        // Snap: render the validated span immediately, no rapid animation.
        GridSpan(preview.columns, preview.rows)
    } else {
        // Animated path target: the same validated span.
        GridSpan(preview.columns, preview.rows)
    }
}

/**
 * Effective resize-handle drag-routing side for a tile footprint, in px.
 *
 * The routing handle is the inward bottom-right square of side [handlePx],
 * except on tiles narrower than twice the touch target: a full-size square
 * would consume the whole tile and leave no reachable body (an unarmed
 * small-tile drag could never MOVE, an armed one could never miss RESIZE, and
 * even the remove control center would fall inside the handle rect). The side
 * therefore shrinks to half the tile extent so a body strip always remains
 * reachable. The 48dp tap target on the handle overlay itself is unaffected:
 * tap arm/disarm is owned by the handle's own clickable, not by this
 * drag-routing rect. Pure math, framework-free.
 */
internal fun resizeHandleSidePx(
    tileWidthPx: Int,
    tileHeightPx: Int,
    handlePx: Int,
): Int {
    require(handlePx > 0) { "handlePx must be positive" }
    require(tileWidthPx > 0) { "tileWidthPx must be positive" }
    require(tileHeightPx > 0) { "tileHeightPx must be positive" }
    return minOf(handlePx, tileWidthPx / 2, tileHeightPx / 2)
        .coerceIn(1, minOf(tileWidthPx, tileHeightPx))
}

/**
 * Hit-tests a content-local pointer position against the resize handle corner.
 *
 * The handle occupies the bottom-right square of [originBounds] with side
 * [resizeHandleSidePx] (the full [handlePx] on tiles at least twice as wide
 * and tall, shrunk to half the tile extent on smaller tiles so a body strip
 * stays reachable): `x in [right - side, right]` and
 * `y in [bottom - side, bottom]` (inclusive). The centered visual/touch target extends outward from the corner,
 * but the parent pointer route cannot receive points outside the tile at an edge.
 * Therefore the parent deliberately uses this exact inward policy: the reachable
 * portion is the full `[right - side, right] × [bottom - side, bottom]` square,
 * with no visual dead zone inside the tile. The bounds are the FIXED origin footprint snapshotted at gesture
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
    val sidePx = resizeHandleSidePx(originBounds.width, originBounds.height, handlePx)
    val handleLeft = originBounds.right - sidePx
    val handleTop = originBounds.bottom - sidePx
    return x >= handleLeft.toFloat() &&
        x <= originBounds.right.toFloat() &&
        y >= handleTop.toFloat() &&
        y <= originBounds.bottom.toFloat()
}

/**
 * Signed-frame overload of [isInHandle] for G3 renderer frames.
 *
 * Same bottom-right corner rule over a [SignedPixelFrame] (whose top may be
 * negative above the row origin), with the same [resizeHandleSidePx] small-tile
 * shrink so a body strip stays reachable. Pure math, framework-free.
 */
internal fun isInHandle(
    x: Float,
    y: Float,
    originBounds: SignedPixelFrame,
    handlePx: Int,
): Boolean {
    require(handlePx > 0) { "handlePx must be positive" }
    val sidePx = resizeHandleSidePx(originBounds.widthPx, originBounds.heightPx, handlePx)
    val handleLeft = originBounds.right - sidePx
    val handleTop = originBounds.bottom - sidePx
    return x >= handleLeft.toFloat() &&
        x <= originBounds.right.toFloat() &&
        y >= handleTop.toFloat() &&
        y <= originBounds.bottom.toFloat()
}

/**
 * Positions a corner control for the selected/active tile in absolute content pixels.
 *
 * - [ControlCorner.TOP_RIGHT] pins the control center to `(right, top)`.
 * - [ControlCorner.BOTTOM_RIGHT] pins the control center to `(right, bottom)`.
 * In both cases the returned top-left is the corner minus half the control size;
 * it may extend outside the tile because the visual contract is center-pinned.
 *
 * Pure math, framework-free; signed G3 callers pass a [SignedPixelFrame] via the
 * overload below (already rebased around the signed origin plus the rest offset).
 * Legality stays owned by `core:grid`.
 */
internal enum class ControlCorner { TOP_RIGHT, BOTTOM_RIGHT }

internal fun cornerControlOffset(
    tileBounds: PixelRect,
    controlSizePx: Int,
    corner: ControlCorner,
): IntOffset {
    require(controlSizePx > 0) { "controlSizePx must be positive" }
    val top = if (corner == ControlCorner.TOP_RIGHT) tileBounds.top else tileBounds.bottom
    return IntOffset(tileBounds.right - controlSizePx / 2, top - controlSizePx / 2)
}

/**
 * Signed-frame overload of [cornerControlOffset] for G3 renderer frames.
 *
 * Same corner rule over a [SignedPixelFrame] (top may be negative above the row
 * origin). Pure math, framework-free.
 */
internal fun cornerControlOffset(
    tileBounds: SignedPixelFrame,
    controlSizePx: Int,
    corner: ControlCorner,
): IntOffset {
    require(controlSizePx > 0) { "controlSizePx must be positive" }
    val top = if (corner == ControlCorner.TOP_RIGHT) tileBounds.top else tileBounds.bottom
    return IntOffset(tileBounds.right - controlSizePx / 2, top - controlSizePx / 2)
}

/**
 * Softlock committed-vs-active layout separation.
 *
 * The committed extent owns the idle scroll range; the transient MOVE frontier
 * sizes the layout only while a MOVE drag is active (never RESIZE, never idle).
 * A retained valid pending lease gates MOVE entry but never inflates the idle
 * range. Pure, framework-free; legality stays owned by `core:grid`.
 */
internal fun shouldUseEditLayout(
    moveActive: Boolean?,
    frontier: GridVerticalLayout.EditBoundary?,
): Boolean = moveActive == true && frontier != null

/**
 * Layout content height for the current edit state: the edit-only height
 * (loaded extent + frontier) while a MOVE drag is active, otherwise the
 * committed intrinsic height. No viewport inflation here; the caller applies
 * the container minimum. Pure, framework-free.
 */
internal fun contentHeightForLayout(
    extent: GridVerticalExtent,
    frontier: GridVerticalLayout.EditBoundary?,
    moveActive: Boolean?,
    cellSizePx: Int,
    rowGapPx: Int,
): Int {
    require(cellSizePx > 0) { "cellSizePx must be positive" }
    require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
    if (!shouldUseEditLayout(moveActive, frontier)) {
        return contentMinHeightPxSigned(extent, cellSizePx, rowGapPx)
    }
    return editContentHeightPx(
        layoutRows = editLayoutRows(extent, frontier),
        cellSizePx = cellSizePx,
        rowGapPx = rowGapPx,
    )
}

/**
 * Committed scroll maximum in pixels: `max(0, committedHeight - usableHeight)`
 * using the intrinsic height (`maxExcl - min`), so the final tile bottom lands
 * at/above the usable bottom with no tail. Saturates to [Int]. Pure,
 * framework-free.
 */
internal fun committedScrollMaxPx(
    extent: GridVerticalExtent,
    cellSizePx: Int,
    rowGapPx: Int,
    viewportHeightPx: Int,
): Int {
    require(cellSizePx > 0) { "cellSizePx must be positive" }
    require(rowGapPx >= 0) { "rowGapPx must be non-negative" }
    require(viewportHeightPx >= 0) { "viewportHeightPx must be non-negative" }
    val committedHeight = contentMinHeightPxSigned(extent, cellSizePx, rowGapPx).toLong()
    return Math.subtractExact(committedHeight, viewportHeightPx.toLong())
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Clamps a real scroll offset to the committed range (Int capped): preserves
 * in-range values, clamps on shrink, rests at zero when the range is zero.
 * Feeds the actual offset, never a shadow. Pure, framework-free.
 */
internal fun clampScrollToCommitted(actualPx: Int, committedMaxPx: Int): Int {
    require(actualPx >= 0) { "actualPx must be non-negative" }
    require(committedMaxPx >= 0) { "committedMaxPx must be non-negative" }
    if (committedMaxPx == 0) return 0
    return actualPx.coerceIn(0, committedMaxPx)
}

/**
 * Whether a MOVE end discards the transient frontier: every no-commit end
 * (cancel, steal, hide, stale break without lift, invalid or no-move lift)
 * discards with zero writes; only a valid lifted commit retains the frontier
 * lease. Pure, framework-free.
 */
internal fun shouldDiscardFrontierOnMoveEnd(
    commit: LogicalPosition?,
    cancelled: Boolean,
    stolen: Boolean,
    isVisible: Boolean,
    completedByUp: Boolean,
): Boolean {
    if (cancelled || stolen || !isVisible || !completedByUp) return true
    return commit == null
}

/**
 * Commit-result ack for a reported MOVE, in feature-owned types (no
 * repository/result types cross into `:feature:home`).
 *
 * [accepted] is true when the repository accepted the move (lease retained
 * until the projection reflects it) and false for rejected/not-found/wrong-home
 * (lease + frontier clear).
 *
 * [requestId] names the exact move attempt this ack answers (assigned by the
 * grid per attempt and echoed back by `:app`). Equal item/position acks for
 * different attempts are distinct values so each retriggers ack handling;
 * only the ack naming the pending attempt's id clears its lease.
 */
data class HomeMoveCommitAck(
    val itemId: ItemId,
    val position: LogicalPosition,
    val accepted: Boolean,
    val requestId: Long = 0L,
)

/**
 * Whether a commit-result ack clears the pending MOVE lease: only a
 * rejected/not-found ack naming the pending attempt's [requestId] (with
 * matching item and position) clears; an accepted ack retains the lease
 * until the projection reflects it, and a stale ack for another attempt —
 * even with an equal item/position — clears nothing. Pure, framework-free.
 */
internal fun shouldClearLeaseOnAck(pending: PendingMoveCommit?, ack: HomeMoveCommitAck?): Boolean {
    if (pending == null || ack == null) return false
    if (ack.requestId != pending.requestId) return false
    if (ack.itemId != pending.itemId || ack.position != pending.position) return false
    return !ack.accepted
}

/**
 * Whether the loaded extent is consistent with all loaded tiles (parity with
 * [GridContentBounds]): the height count equals `maxExcl - min` and matches a
 * fresh [GridVerticalLayout.measure] over the tiles. Empty tiles require a
 * zero extent. Pure, framework-free; the remeasure below uses the same grid
 * policy, never a second one.
 */
internal fun isExtentParityOk(extent: GridVerticalExtent, tiles: List<HomeRenderTile>): Boolean {
    if (tiles.isEmpty()) {
        return extent.totalScrollableRows == 0L &&
            extent.occupiedHeightRows == 0L &&
            extent.occupiedMaxRowExclusive == extent.occupiedMinRow
    }
    return try {
        val placements = tiles.map { tile ->
            GridPlacement(
                id = tile.itemId,
                position = GridPosition(tile.position.column, tile.position.row),
                span = GridSpan(tile.span.columns, tile.span.rows),
            )
        }
        // Viewport/margin args do not affect normal intrinsic measurement;
        // any positive viewport with non-negative margin validates the call.
        val remeasured = GridVerticalLayout.measure(
            placements = placements,
            viewportRows = 1L,
            workingMarginRows = 0L,
        )
        remeasured == extent
    } catch (e: RuntimeException) {
        false
    }
}

/**
 * Committed extent for scroll/legality math: the loaded extent when it matches
 * all loaded placements, otherwise a remeasure from those placements with the
 * same `core:grid` policy. Tiles are never moved, dropped, compacted or reset;
 * only the measurement used for range math falls back. Pure, framework-free.
 */
internal fun committedExtentOrRemeasured(
    loadedExtent: GridVerticalExtent,
    tiles: List<HomeRenderTile>,
    usableHeightPx: Int,
    rowStridePx: Long,
): GridVerticalExtent {
    require(usableHeightPx >= 0) { "usableHeightPx must be non-negative" }
    require(rowStridePx > 0L) { "rowStridePx must be positive" }
    if (isExtentParityOk(loadedExtent, tiles)) return loadedExtent
    val placements = tiles.map { tile ->
        GridPlacement(
            id = tile.itemId,
            position = GridPosition(tile.position.column, tile.position.row),
            span = GridSpan(tile.span.columns, tile.span.rows),
        )
    }
    return try {
        GridVerticalLayout.measure(
            placements = placements,
            viewportRows = viewportRowsFor(usableHeightPx, rowStridePx),
            workingMarginRows = 0L,
        )
    } catch (e: RuntimeException) {
        loadedExtent
    }
}
