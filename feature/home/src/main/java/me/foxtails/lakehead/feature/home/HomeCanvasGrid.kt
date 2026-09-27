package me.foxtails.lakehead.feature.home

import androidx.compose.animation.core.Animatable
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
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
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
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.SignedGridPlacement
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import kotlin.math.abs

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
 *   [HomeEditSession.Active] session in MOVE mode, vertical scroll is disabled while active
 *   (and while idle a confident horizontal axis lock yields to the pager, so a
 *   horizontal swipe pages instead of scrolling), pointer drags update the
 *   candidate via [MoveDragMath] with validity from `core:grid`, a
 *   non-interactive outline marks the candidate, back cancels, and drag end commits via
 *   [onMoveTile] when the candidate differs from the origin. A single bottom-right,
 *   center-pinned resize handle resizes only when armed: tapping the arrow toggles
 *   [HomeInteractionController.Editing.resizeArmed] with no write, and only an armed
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
 *   with the caller: this grid only reports the committed [ItemId] and [LogicalPosition] or
 *   [LogicalSpan].
 * - Corner controls appear for a selected [HomeInteractionController.Editing] tile as
 *   well as [HomeEditSession.Active]. Each uses a 32dp visual circle in a 48dp touch
 *   target: [RemoveButton] is top-right center pinned and [ResizeHandle] is bottom-right
 *   center pinned via [cornerControlOffset]. The remove affordance floats as a sibling
 *   overlay in both MOVE and RESIZE modes and is announced as `"Remove {label}"`.
 *   Tapping it reports [onRemoveTile] with the selected/active [ItemId] only; the
 *   grid performs no persistence delete and issues no move/resize.
 * - B1: swipe-to-open shortcuts are removed — no idle/editing flick opens a
 *   shortcut surface and this grid exposes no swipe rows. Shortcuts return in
 *   B4 as an edit-mode menu above the selected tile: [EditShortcutMenu] follows
 *   `(interaction as? Editing)?.selectedId` (long-press shows, tap-other
 *   transfers, empty-tap/Back/navigate/move-start/resize-start/removal
 *   dismisses with zero activation) and renders [surfaceEntries] in caller
 *   rank order via the reused [swipeSurfaceFrame]-pattern geometry
 *   ([editShortcutMenuFrame]); no reflow, no packing, no grid legality.
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
    // B4 edit-menu rows: rank-ordered shortcut entries for the currently
    // selected tile (empty until shortcut discovery lands). Row taps report
    // onSurfaceExecute/onSurfaceTogglePin with the selected ItemId and entry
    // id; execution and pin persistence stay with the caller.
    surfaceEntries: List<ShortcutSurfaceEntry> = emptyList(),
    onSurfaceExecute: (ItemId, String) -> Unit = { _, _ -> },
    onSurfaceTogglePin: (ItemId, String) -> Unit = { _, _ -> },
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
) {
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
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
            // W1 recess/select/exit: transient Normal|Editing(selectedId?) owned by
            // HomeInteractionController (pure, never modified in place). Hoisted
            // alongside editSession inside key(requestedHomeId) so a home switch
            // resets it. TileId-keyed, never position/index; render-only recess
            // (no occupancy change). Move/resize entry (Active) is NOT entered
            // from the wiggle path; W2 rewires manipulation entry separately.
            var interaction by remember {
                mutableStateOf<HomeInteractionController>(HomeInteractionController.Normal)
            }
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
            val columns = loaded.columns.count
            val rows = loaded.extent.totalScrollableRows.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val active = editSession as? HomeEditSession.Active
            val interactionEditing = interaction as? HomeInteractionController.Editing
            // A1 hold signal: active edit or wiggle editing holds
            // pager swipe. Emitted via LaunchedEffect with the hoisted latest
            // callback so recompositions of the lambda never restart the
            // emission. Tiles are non-empty here (empty returned above), so
            // this delegates to the same predicate the empty guard forces false.
            val isHold = gridHoldSignal(
                tilesEmpty = false,
                active = active,
                interaction = interaction,
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
            BackHandler(
                enabled = active != null || interactionEditing != null,
            ) {
                if (editSession as? HomeEditSession.Active != null) {
                    editSession = editSession.cancel()
                } else {
                    interaction = interaction.exitOnBack()
                }
            }
            // W1 navigate/Home reset: leaving the tile-grid surface exits
            // wiggle-editing via the pure transition (idempotent from Normal).
            // key(requestedHomeId) already resets on home switch; this covers
            // navigate-away reloads of the same home.
            LaunchedEffect(loaded.requestedHomeId) {
                interaction = interaction.exitOnNavigate()
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
            // G5: single animated grid origin. One Animatable moves the whole
            // field as one object: tiles keep their internal grid positions
            // while the shared origin glides to the new rest target. The target
            // is the signed rest origin; LaunchedEffect auto-cancels an
            // in-flight animateTo on retarget and restarts from the current
            // value, so a rapid add+remove converges on the latest target.
            // First composition, null rest, and zero-height viewports snap.
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
            val minHeightPx = baseContentHeightPx.coerceAtLeast(availableHeightPx)
            val children: List<SpanChild<ItemId>> = loaded.tiles.map { tile ->
                val iconBitmap = iconsByIdentity[tile.appIdentity]
                // W3 renderedSpan = preview ?: persisted: while RESIZE-dragging the
                // active tile, its placement and content use the live last-valid
                // preview span so content adapts during the drag. Reduced-motion
                // snaps to the same span (no rapid animation); geometry identical.
                val renderedSpan = editSession.renderedSpan(tile.itemId, tile.span)
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
                            isEditActive = active != null,
                            activeItemId = active?.itemId,
                            // W1 recess/select: tap transfers selection directly
                            // (selectOnTap, never launch); long-press enters
                            // wiggle-editing (enterOnLongPress).
                            // Never enters Active here.
                            interactionState = interaction,
                            onSelect = { selectedId ->
                                interaction = interaction.selectOnTap(selectedId)
                            },
                            onEnterWiggle = { entered ->
                                interaction = interaction.enterOnLongPress(
                                    entered.itemId,
                                    entered.isLaunchable,
                                )
                            },
                        )
                    },
                )
            }
            // Latest session for drag callbacks: drag pointerInput is keyed by
            // loaded.requestedHomeId (not item id), so it stays stable through candidate
            // recomposition and resets on home switch.
            val sessionState = rememberUpdatedState(editSession)
            val interactionSnapshot = rememberUpdatedState(interaction)
            // Snapshot holder for the Unit-keyed idle gesture observer below: tiles,
            // geometry and origins stay fresh without restarting in-flight gestures.
            // B1: the flick-to-show detector is removed, but the down hit-test is
            // retained for A2 tile-vs-background pager-hold arbitration.
            val idleHitInputs = rememberUpdatedState(
                IdleHitSnapshot(
                    tiles = loaded.tiles,
                    geometry = geometry,
                    originX = originX,
                    rowOrigin = rowOrigin,
                    originYPx = originYPx,
                    scrollYPx = scrollState.value.toFloat(),
                ),
            )
            // Tap on the grid background (outside any tile) cancels an active edit
            // session and exits W1 wiggle-editing via the pure exitOnEmptyTap
            // transition. Tiles use combinedClickable which consumes their own
            // taps; this handler only fires for taps on empty grid area.
            val tapCancelModifier = Modifier.pointerInput(active?.itemId, interaction) {
                if (active?.itemId == null &&
                    interactionSnapshot.value !is HomeInteractionController.Editing
                ) {
                    return@pointerInput
                }
                detectTapGestures(
                    onTap = {
                        editSession = sessionState.value.cancel()
                        interaction = interactionSnapshot.value.exitOnEmptyTap()
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
                    val downCell = MoveDragMath.cellForPixel(
                        xPx = startPos.x,
                        yPx = startPos.y,
                        cellSizePx = snapshot.geometry.cellSizePx.toFloat(),
                        columnGapPx = snapshot.geometry.columnGapPx.toFloat(),
                        rowGapPx = snapshot.geometry.rowGapPx.toFloat(),
                        windowFirstRow = snapshot.rowOrigin,
                        originXPx = snapshot.originX.toFloat(),
                        originYPxPx = snapshot.originYPx.toFloat(),
                        scrollYPx = snapshot.scrollYPx,
                    )
                    val downTile = snapshot.tiles.firstOrNull { tile ->
                        downCell.column in tile.position.column until
                            tile.position.column + tile.span.columns &&
                        downCell.row in tile.position.row until
                            tile.position.row + tile.span.rows
                    }
                    // Selected-drag is the sole owner of this gesture. Do not
                    // set/reset pager state here or flip it before slop.
                    val idleInteraction = interactionSnapshot.value
                    if (idleInteraction is HomeInteractionController.Editing &&
                        downTile?.itemId == idleInteraction.selectedId
                    ) {
                        return@awaitEachGesture
                    }
                    // A2 single arbitration for the idle gesture: route the down-hit
                    // through resolveHomeGesture so a tile-origin touch holds the
                    // pager for the whole gesture (snapshot-once, never flipped
                    // mid-gesture even with horizontal motion). Wiggle-editing
                    // tile downs arbitrate as SELECT_DRAG, plain tile downs as
                    // TAP, empty-grid downs as BACKGROUND (pager stays free).
                    val idleWinner = resolveHomeGesture(
                        isSelectDrag = interactionSnapshot.value is HomeInteractionController.Editing &&
                            downTile != null,
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
            val dragModifier = Modifier.pointerInput(loaded.requestedHomeId) {
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
                    val interactionAtDown = interactionSnapshot.value
                    val hitSnapshot = idleHitInputs.value
                    val downCell = MoveDragMath.cellForPixel(
                        xPx = startPos.x,
                        yPx = startPos.y,
                        cellSizePx = hitSnapshot.geometry.cellSizePx.toFloat(),
                        columnGapPx = hitSnapshot.geometry.columnGapPx.toFloat(),
                        rowGapPx = hitSnapshot.geometry.rowGapPx.toFloat(),
                        windowFirstRow = hitSnapshot.rowOrigin,
                        originXPx = hitSnapshot.originX.toFloat(),
                        originYPxPx = hitSnapshot.originYPx.toFloat(),
                        scrollYPx = gestureScrollYPx,
                    )
                    val downTile = hitSnapshot.tiles.firstOrNull { tile ->
                        downCell.column in tile.position.column until
                            tile.position.column + tile.span.columns &&
                            downCell.row in tile.position.row until
                            tile.position.row + tile.span.rows
                    }
                    val selectedTile = (interactionAtDown as? HomeInteractionController.Editing)
                        ?.selectedId
                        ?.let { selectedId -> hitSnapshot.tiles.firstOrNull { it.itemId == selectedId } }
                    val selectedBounds = selectedTile?.let { tile ->
                        TileBounds.signedBounds(
                            column = tile.position.column,
                            row = tile.position.row.toLong(),
                            span = GridSpan(tile.span.columns, tile.span.rows),
                            geometry = geometry,
                            originX = originX,
                            originRow = rowOrigin,
                            originYPx = originYPx,
                        )
                    }
                    val selectedResize = selectedBounds?.let {
                        isInHandle(
                            startPos.x,
                            startPos.y + gestureScrollYPx,
                            it,
                            with(density) { RESIZE_TOUCH_DP.dp.roundToPx() },
                        )
                    } == true
                    // Preserve the point grabbed inside the selected tile. This is
                    // content-space state, while startPos remains viewport-space.
                    val grabOffsetXPx = selectedBounds?.let {
                        startPos.x - it.left.toFloat()
                    } ?: 0f
                    val grabOffsetYPx = selectedBounds?.let {
                        startPos.y + gestureScrollYPx - it.top.toFloat()
                    } ?: 0f
                    val selectedDrag = interactionAtDown is HomeInteractionController.Editing &&
                        interactionAtDown.selectedId == (downTile?.itemId ?: selectedTile?.itemId) &&
                        (downTile != null || selectedResize)
                    val dragTile = downTile ?: selectedTile?.takeIf { selectedResize }
                    // A2-S1 armed snapshot: resize is armed only for the down-selected
                    // tile (arm owned by the selection). Captured once at down alongside
                    // selected+arrow-hit; the slop wait below never refreshes it, and
                    // the past-slop route is decided once from this snapshot with no
                    // mid-gesture switch.
                    val armedAtDown = snapshotResizeArmed(interactionAtDown, dragTile?.itemId)
                    var currentSession: HomeEditSession = sessionState.value
                    var sessionAtDown = currentSession as? HomeEditSession.Active
                    if (sessionAtDown == null && !selectedDrag) return@awaitEachGesture
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
                    val slopChange = slop ?: return@awaitEachGesture
                    if (sessionAtDown == null) {
                        val entered = currentSession.enterSelectedDrag(
                            interaction = interactionAtDown,
                            tile = dragTile,
                            placements = loaded.tiles,
                            columns = columns,
                            rows = rows,
                        )
                        currentSession = entered
                        editSession = entered
                        sessionAtDown = entered as? HomeEditSession.Active
                            ?: run {
                                pagerHold = false
                                return@awaitEachGesture
                            }
                    }
                    var activeAtDown = sessionAtDown ?: return@awaitEachGesture
                    // W3 single-BR-only: the origin corner is snapshotted ONCE at
                    // drag start from the ORIGIN span (top-left fixed anchor).
                    val originBounds = TileBounds.signedBounds(
                        column = activeAtDown.origin.column,
                        row = activeAtDown.origin.row.toLong(),
                        span = GridSpan(activeAtDown.originSpan.columns, activeAtDown.originSpan.rows),
                        geometry = geometry,
                        originX = originX,
                        originRow = rowOrigin,
                        originYPx = originYPx,
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
                    val route = routeDownDrag(
                        arrowHit = startInHandle,
                        armed = armedAtDown,
                        hasTile = true,
                    )
                    if (route == DownDragRoute.NONE) {
                        // Unarmed arrow-drag: neither move nor resize. Zero-write
                        // cancel of the just-entered session (a pre-existing Active
                        // is always at origin state here, so cancel loses no
                        // progress); the selection stays wiggle-editing and the
                        // tile-touch hold ends with the gesture.
                        currentSession = currentSession.cancel()
                        editSession = currentSession
                        pagerHold = false
                        return@awaitEachGesture
                    }
                    if (route == DownDragRoute.MOVE) {
                        // Body-drag while armed stays move and clears the arm, so a
                        // later arrow-drag needs a fresh arrow-tap.
                        interaction = interaction.disarmResize()
                    }
                    val isOnHandle = route == DownDragRoute.RESIZE
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
                        // RESIZE mode: corner-anchored span updates. beginResize is
                        // gated by the route above — this branch is reachable only for
                        // an armed arrow-origin, so an unarmed arrow-drag can never
                        // resize and a plain drag never changes span.
                        currentSession = activeAtDown.beginResize()
                        editSession = currentSession
                        activeAtDown = currentSession as? HomeEditSession.Active
                            ?: return@awaitEachGesture
                        // W4: track initial scroll and pager lock for steal detection.
                        val resizeStartScroll = scrollState.value
                        val resizeStartPagerLock = pagerAxisLock
                        var resizeStolen = false
                        var currentChange: PointerInputChange = slopChange
                        while (true) {
                            // W4: break on pager flip or scroll delta (steal attempt).
                            // Steal fails: keeps last-valid, zero-write on cancel.
                            if (pagerAxisLock != resizeStartPagerLock ||
                                scrollState.value != resizeStartScroll
                            ) {
                                resizeStolen = true
                                break
                            }
                            val next = awaitDragOrCancellation(currentChange.id) ?: break
                            next.consume()
                            currentChange = next
                            if (currentSession !is HomeEditSession.Active) break
                            val pointerXPx = next.position.x
                            val pointerYPx = next.position.y
                            val rawSpan = ResizeDragMath.rawSpan(
                                pointerXPx = pointerXPx,
                                pointerYPx = pointerYPx,
                                originPosition = activeAtDown.origin,
                                cellSizePx = geometry.cellSizePx.toFloat(),
                                columnGapPx = geometry.columnGapPx.toFloat(),
                                rowGapPx = geometry.rowGapPx.toFloat(),
                                originXPx = originX.toFloat(),
                                rowOrigin = rowOrigin,
                                originYPxRestPx = originYPx.toFloat(),
                                scrollYPx = gestureScrollYPx,
                            )
                            val quantized = RenderableSpans.quantize(rawSpan, columns)
                            currentSession = currentSession.updateResizeCandidate(
                                quantized,
                                loaded.tiles,
                                columns,
                                rows,
                            )
                            editSession = currentSession
                        }
                        val final = currentSession as? HomeEditSession.Active
                        // W3 release: commit the held last-valid span iff it differs
                        // from origin; otherwise snap back. Either way the session
                        // stays in edit mode (stays-editing, never exits on resize).
                        // W4: skip commit when stolen (zero-write on cancel).
                        val commitSpan = if (resizeStolen) null else currentSession.resizeCommitSpan()
                        if (final != null && commitSpan != null) {
                            onResizeTile(final.itemId, commitSpan)
                            // Keep session active in MOVE with the committed span as origin.
                            currentSession = HomeEditSession.Active(
                                itemId = final.itemId,
                                origin = final.origin,
                                originSpan = commitSpan,
                                candidatePosition = final.origin,
                                candidateSpan = commitSpan,
                                lastValidSpan = commitSpan,
                                // Position resets to origin alongside the span commit
                                // (W2 owns lastValidPosition semantics).
                                lastValidPosition = final.origin,
                                candidateValidity = CandidateValidity.VALID,
                                mode = EditMode.MOVE,
                            )
                            editSession = currentSession
                        } else if (final != null) {
                            // No change or blocked-only — snap back, stay in MOVE with original origin.
                            currentSession = HomeEditSession.Active(
                                itemId = final.itemId,
                                origin = final.origin,
                                originSpan = final.originSpan,
                                candidatePosition = final.origin,
                                candidateSpan = final.originSpan,
                                lastValidSpan = final.originSpan,
                                lastValidPosition = final.origin,
                                candidateValidity = CandidateValidity.VALID,
                                mode = EditMode.MOVE,
                            )
                            editSession = currentSession
                        } else {
                            currentSession = HomeEditSession.Idle
                            editSession = currentSession
                        }
                        // W4: reset pager axis lock once after resize release/cancel.
                        // A2: tile-touch hold ends with the drag; the session stays
                        // in edit so the A1 isHold signal keeps holding the pager.
                        pagerAxisLock = LockedAxis.UNDECIDED
                        pagerHold = false
                    } else {
                        // MOVE mode: pointer position maps to a candidate cell.
                        // W3 single-gesture rule (W4 owns arbitration): routing was
                        // decided at drag start above, so this branch stays MOVE
                        // until release — it never promotes into the handle corner.
                        val slopCell = MoveDragMath.cellForPixel(
                            xPx = slopChange.position.x,
                            yPx = slopChange.position.y,
                            cellSizePx = geometry.cellSizePx.toFloat(),
                            columnGapPx = geometry.columnGapPx.toFloat(),
                            rowGapPx = geometry.rowGapPx.toFloat(),
                            windowFirstRow = rowOrigin,
                            originXPx = originX.toFloat(),
                            originYPxPx = originYPx.toFloat(),
                            scrollYPx = gestureScrollYPx,
                            grabOffsetXPx = grabOffsetXPx,
                            grabOffsetYPx = grabOffsetYPx,
                        )
                        currentSession = currentSession.updateMoveCandidate(
                            slopCell,
                            loaded.tiles,
                            columns,
                            rows,
                        )
                        editSession = currentSession
                        // W4: track initial scroll and pager lock for steal detection.
                        val moveStartScroll = scrollState.value
                        val moveStartPagerLock = pagerAxisLock
                        var moveStolen = false
                        var currentChange: PointerInputChange = slopChange
                        while (true) {
                            // W4: break on pager flip or scroll delta (steal attempt).
                            // Steal fails: keeps last-valid, zero-write on cancel.
                            if (pagerAxisLock != moveStartPagerLock ||
                                scrollState.value != moveStartScroll
                            ) {
                                moveStolen = true
                                break
                            }
                            val next = awaitDragOrCancellation(currentChange.id) ?: break
                            next.consume()
                            currentChange = next
                            if (currentSession !is HomeEditSession.Active) break
                            val cell = MoveDragMath.cellForPixel(
                                xPx = next.position.x,
                                yPx = next.position.y,
                                cellSizePx = geometry.cellSizePx.toFloat(),
                                columnGapPx = geometry.columnGapPx.toFloat(),
                                rowGapPx = geometry.rowGapPx.toFloat(),
                                windowFirstRow = rowOrigin,
                                originXPx = originX.toFloat(),
                                originYPxPx = originYPx.toFloat(),
                                scrollYPx = gestureScrollYPx,
                                grabOffsetXPx = grabOffsetXPx,
                                grabOffsetYPx = grabOffsetYPx,
                            )
                            currentSession = currentSession.updateMoveCandidate(
                                cell,
                                loaded.tiles,
                                columns,
                                rows,
                            )
                            editSession = currentSession
                        }
                        val final = currentSession as? HomeEditSession.Active
                        // W4: skip commit when stolen (zero-write on cancel).
                        val commit = if (moveStolen) null else currentSession.moveCommitPosition()
                        if (final != null && commit != null) {
                            onMoveTile(final.itemId, commit)
                        }
                        currentSession = HomeEditSession.Idle
                        editSession = currentSession
                        // W4: reset pager axis lock once after move release/cancel.
                        // A2: tile-touch hold ends with the drag; the session is
                        // Idle so neither hold keeps the pager.
                        pagerAxisLock = LockedAxis.UNDECIDED
                        pagerHold = false
                    }
                }
            }
            Box(
                modifier = Modifier
                    .verticalScroll(
                        scrollState,
                        enabled = active == null && pagerAxisLock != LockedAxis.HORIZONTAL,
                    )
                    .then(tapCancelModifier)
                    .then(dragModifier)
                    .then(if (active != null) Modifier else idleGestureModifier)
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
                    // W3 preview overlay: frame at the held last-valid span (blocked
                    // proposals keep the frame; validity flips the valid/error
                    // color). Animated and reduced-motion branches share
                    // [resizePreviewSpan], so geometry is identical.
                    val previewSpan = resizePreviewSpan(
                        active = active,
                        reducedMotion = reducedMotion,
                    )
                    ResizeCandidateOverlay(
                        originPosition = GridPosition(active.origin.column, active.origin.row),
                        candidateSpan = previewSpan,
                        geometry = geometry,
                        rowOrigin = rowOrigin,
                        isValid = active.candidateValidity == CandidateValidity.VALID,
                        originX = originX,
                        originYPx = originYPx,
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
                val controlItemId = active?.itemId ?: interactionEditing?.selectedId
                if (controlItemId != null) {
                    val activeTile = loaded.tiles.find { it.itemId == controlItemId }
                    if (activeTile != null) {
                        // Handle tracks the live last-valid preview span (not the
                        // persisted span) so it stays on the growing/shrinking
                        // corner during a resize drag.
                        // Signed frame: already rebased around `rowOrigin` with the G2
                        // rest offset, so its top is final Box content coordinates.
                        val previewSpan = if (active == null) {
                            GridSpan(activeTile.span.columns, activeTile.span.rows)
                        } else if (active.mode == EditMode.RESIZE) {
                            GridSpan(active.lastValidSpan.columns, active.lastValidSpan.rows)
                        } else GridSpan(active.candidateSpan.columns, active.candidateSpan.rows)
                        val tileBounds = TileBounds.signedBounds(
                            column = active?.origin?.column ?: activeTile.position.column,
                            row = active?.origin?.row?.toLong() ?: activeTile.position.row.toLong(),
                            span = GridSpan(previewSpan.columns, previewSpan.rows),
                            geometry = geometry,
                            originX = originX,
                            originRow = rowOrigin,
                            originYPx = originYPx,
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
                                armed = interactionEditing?.selectedId == controlItemId &&
                                    interactionEditing.resizeArmed,
                                onToggleArmed = {
                                    if ((interaction as? HomeInteractionController.Editing)?.selectedId == controlItemId) {
                                        interaction = interaction.toggleResizeArmed()
                                    }
                                },
                            )
                        }
                        // Remove affordance: sibling overlay over the active tile in
                        // both MOVE and RESIZE modes. Tracks the live candidate span
                        // (same footprint as the handle) so it stays attached during
                        // a resize drag. Tap reports onRemoveTile only; the grid
                        // performs no persistence delete and issues no move/resize.
                        val removeLabel = handleLabel
                        val buttonPx = with(density) { CORNER_CONTROL_TOUCH_SIZE.dp.roundToPx() }
                        val removeAbsolute = cornerControlOffset(
                            tileBounds = tileBounds,
                            controlSizePx = buttonPx,
                            corner = ControlCorner.TOP_RIGHT,
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
                                    interaction = interaction.unpinIfSelected(controlItemId)
                                    editSession = editSession.cancel()
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
                // the menu never moves, resizes or pages tiles.
                val menuSelectedId = interactionEditing?.selectedId
                if (isEditShortcutMenuVisible(active, menuSelectedId, loaded.tiles, surfaceEntries)) {
                    val menuTile = loaded.tiles.find { it.itemId == menuSelectedId }
                    if (menuTile != null) {
                        EditShortcutMenu(
                            tile = menuTile,
                            entries = surfaceEntries,
                            geometry = geometry,
                            originX = originX,
                            rowOrigin = rowOrigin,
                            originYPx = originYPx,
                            availableWidthPx = availableWidthPx,
                            minHeightPx = minHeightPx,
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
 * Snapshots whether resize is armed for the down-hit tile.
 *
 * The arm is owned by the selection: true only when [interaction] is
 * [HomeInteractionController.Editing] with [HomeInteractionController.Editing.resizeArmed]
 * and its [HomeInteractionController.Editing.selectedId] matches [tileId]. Any
 * transfer, retarget, unpin or exit clears the arm, so a stale arm can never
 * authorize a resize for another tile. Pure, framework-free and unit-testable.
 */
internal fun snapshotResizeArmed(
    interaction: HomeInteractionController,
    tileId: ItemId?,
): Boolean {
    if (interaction !is HomeInteractionController.Editing) return false
    if (tileId == null) return false
    return interaction.resizeArmed && interaction.selectedId == tileId
}

/**
 * A1 hold predicate: transient home state that must hold pager swipe.
 *
 * B1: returns true when an edit session is active ([active] != null) or wiggle
 * editing is engaged ([interaction] is [HomeInteractionController.Editing]).
 * The retired swipe-surface arm is gone: no idle/editing flick opens a surface,
 * so there is no shown state left to hold. Pure math-free boolean OR,
 * framework-free and unit-testable. The caller (shell) ANDs the negation into
 * `userScrollEnabled`; the pill's programmatic `animateScrollToPage` is unaffected.
 */
internal fun isHomeHold(
    active: HomeEditSession.Active?,
    interaction: HomeInteractionController,
): Boolean = active != null ||
    interaction is HomeInteractionController.Editing

/**
 * P0 empty-guard hold signal for [HomeCanvasGrid].
 *
 * The grid early-returns on empty tiles before transient edit state
 * exists, so an empty grid must never hold regardless of stale inputs: empty
 * forces false, non-empty delegates to [isHomeHold]. Pure, framework-free and
 * unit-testable. The composable emits false via `LaunchedEffect` on the empty
 * path and on dispose, so shell `homeHold` resets on populated→empty
 * transitions and on grid removal.
 */
internal fun gridHoldSignal(
    tilesEmpty: Boolean,
    active: HomeEditSession.Active?,
    interaction: HomeInteractionController,
): Boolean = if (tilesEmpty) false else isHomeHold(active, interaction)

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
    val geometry: SquareCellGeometry,
    val originX: Int,
    val rowOrigin: Long,
    val originYPx: Long,
    val scrollYPx: Float,
)

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
 * Non-interactive outline marking the move ghost's committed position.
 *
 * Position uses [TileBounds.signedBounds] with the signed [rowOrigin] plus the G2
 * rest [originYPx] offset, so the outline lands on the same Box content coordinates
 * the windowed tiles are placed at (rebased container plus rest offset). The frame
 * is placed at [HomeEditSession.Active.lastValidPosition] (equivalent to
 * [HomeEditSession.renderedPosition]) so the ghost shows the tile's snap-back
 * target — colliding/out-of-bounds drags keep the outline at the last confirmed
 * valid cell rather than following the pointer into illegal territory. A valid
 * candidate draws a 2dp outline in `scheme.primary`; any other validity draws
 * `scheme.error` (the color reports the current drag's legality, not the frame
 * position).
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
        column = active.lastValidPosition.column,
        row = active.lastValidPosition.row.toLong(),
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
 * Resize preview span for the RESIZE overlay, handle, and tile content.
 *
 * W3 `renderedSpan = preview ?: persisted`: returns the held last-valid span
 * ([HomeEditSession.Active.lastValidSpan]) for a RESIZE session, which is the
 * frame both the animated path and the reduced-motion snap path render — the
 * [reducedMotion] flag only selects snap-vs-animate presentation, never a
 * different span, so geometry is identical either way. Pure math,
 * framework-free. Legality stays owned by `core:grid`.
 */
internal fun resizePreviewSpan(
    active: HomeEditSession.Active,
    reducedMotion: Boolean = false,
): GridSpan {
    // Snap branch (reduced-motion) and animated branch share one source: the
    // held last-valid preview. No separate span computation per branch.
    val preview = active.lastValidSpan
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
