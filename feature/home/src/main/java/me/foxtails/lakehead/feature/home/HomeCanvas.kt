package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Read-only viewport-aware canvas for one persisted home.
 *
 * Presentation only: [state] and [iconsByIdentity] are caller-acquired. Icons are
 * already-decoded [HomeCanvasIcons] snapshots; this composable performs no lookup, decode,
 * cache, repository, catalogue, or platform access and uses Foundation text only (no
 * Material product components).
 *
 * - [HomeRenderState.Loading] renders honest loading text.
 * - [HomeRenderState.NotFound] renders an honest not-found message and creates nothing.
 * - [HomeRenderState.Loaded] with no tiles renders an honest empty message as the scrolling
 *   background; populated states render the scrolling tile grid. No HOME-role overlay is
 *   rendered here; [onRequestDefaultHome] is retained (currently unused) for a future
 *   settings entry so callers in `:app` keep a stable signature.
 * - Edit mode is transient presentation state owned by [HomeCanvasGrid]: [onMoveTile]
 *   reports a drag-committed [ItemId], [LogicalPosition] and per-attempt request id
 *   to the caller, [onResizeTile]
 *   reports a resize-committed [ItemId] and [LogicalSpan], and [onRemoveTile] reports a
 *   remove-tap [ItemId]. The caller owns persistence. This composable performs no
 *   writes itself.
 * - B1: swipe-to-open shortcuts are removed — no surface rows or execute/pin
 *   callbacks pass through this canvas for swipes. Shortcuts return in B4 as
 *   an edit-mode menu above the selected tile: [surfaceEntries] carries the
 *   rank-ordered shortcut rows for the current selection, and row taps report
 *   [onSurfaceExecute]/[onSurfaceTogglePin] with the selected [ItemId] and entry id.
 *   Menu execution and pin persistence are implemented and stay with the caller;
 *   rendering the pinned shortcuts on tiles remains deferred to C1. B1 selection scoping:
 *   [surfaceEntriesTileId] names the tile the rows were loaded for (null =
 *   unscoped) and [onSelectedTileChange] reports selection transitions so the
 *   caller can query-on-select; the grid itself never queries.
 *   Tile strips render [pinnedByTile] (joined per-tile pins; empty renders the
 *   centered icon): strip taps report [onPinnedShortcutClick] with the owning
 *   tile id and shortcut id, and execution stays with the caller.
 * - A1 hold signal: [onTransientHoldChange] reports transient hold state owned by
 *   [HomeCanvasGrid] (active edit or wiggle editing) so the shell
 *   can gate pager swipe. Programmatic pill navigation is unaffected.
+ * - A3 normal viewport: this canvas measures the raw height via BoxWithConstraints
+ *   and hands HomeCanvasGrid the usable height (`max(0, measured - clearance)`) plus
+ *   a matching layout height, so the pill/system-gesture reserve never covers the
+ *   last row and the scroll range is the exact overflow. The clearance arrives as
+ *   the plain pixel count HomeCanvasViewportStyle.bottomClearancePx (resolved by
+ *   `:app`); no Android object enters the grid.
+ * - A2 tile-touch hold: [onPagerHoldChange] reports the per-press tile-origin
+ *   hold owned by [HomeCanvasGrid] (tile down until release) so a tile touch
+ *   never becomes a page swipe. Separate from the A1 hold; the shell ANDs both.
 *
 * @param onRequestDefaultHome retained for a future settings entry; currently unused by
 * this canvas (no HOME-role overlay is rendered).
 * @param onTransientHoldChange pass-through hold callback forwarded to
 * [HomeCanvasGrid]; invoked with true while held, false when released.
 * @param onPagerHoldChange pass-through tile-touch hold callback forwarded to
 * [HomeCanvasGrid]; invoked with true from a tile-origin down until release.
 * @param isVisible pager visibility signal forwarded to [HomeCanvasGrid];
 * when false the transient edit is discarded with zero writes.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeCanvas(
    state: HomeRenderState,
    iconsByIdentity: HomeCanvasIcons,
    onLaunchApp: (AppIdentity) -> Unit,
    onRequestDefaultHome: () -> Unit,
    modifier: Modifier = Modifier,
    overscanRows: Long = 1L,
    viewportStyle: HomeCanvasViewportStyle = HomeCanvasViewportStyle(),
    onMoveTile: (ItemId, LogicalPosition, Long) -> Unit = { _, _, _ -> },
    onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    onRemoveTile: (ItemId) -> Unit = {},
    surfaceEntries: List<ShortcutSurfaceEntry> = emptyList(),
    surfaceEntriesTileId: ItemId? = null,
    onSelectedTileChange: (ItemId?) -> Unit = {},
    onSurfaceExecute: (ItemId, String) -> Unit = { _, _ -> },
    onSurfaceTogglePin: (ItemId, String) -> Unit = { _, _ -> },
    // C1 tile strip: joined pinned shortcuts per tile, forwarded to
    // [HomeCanvasGrid] (see its docs).  Defaults keep the canvas pin-free.
    pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
    onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
    onTransientHoldChange: (Boolean) -> Unit = {},
    onPagerHoldChange: (Boolean) -> Unit = {},
    // P2 navigation visibility: forwarded to [HomeCanvasGrid]. False while the
    // pager shows another page (the grid stays composed for adjacent pages),
    // discarding the transient edit with zero writes. Defaults to true.
    isVisible: Boolean = true,
    // Softlock ack: commit-result for a reported MOVE (feature-owned
    // [HomeMoveCommitAck]; no repository types cross the boundary). The ack
    // names the attempt's request id so equal retries retrigger handling.
    // Accepted retains the pending lease until the projection reflects it;
    // rejected/not-found clears the lease + frontier. Idle range stays
    // committed-bounds either way.
    moveCommitAck: HomeMoveCommitAck? = null,
) {
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
    // P0 stale-hold guard: Loading/NotFound never compose HomeCanvasGrid, so
    // the grid's hold emission cannot run to reset shell `homeHold`. Hoist a
    // false emission + dispose reset here; Loaded (empty or populated) stays
    // owned by the grid.
    val latestHoldCallback = rememberUpdatedState(onTransientHoldChange)
    // A2 stale-hold guard: same Loading/NotFound + dispose reset for the
    // separate tile-touch `pagerHold` so it cannot stick true when the grid
    // never composes.
    val latestPagerHoldCallback = rememberUpdatedState(onPagerHoldChange)
    LaunchedEffect(state) {
        if (shouldResetCanvasHold(state)) {
            latestHoldCallback.value(false)
            latestPagerHoldCallback.value(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            latestHoldCallback.value(false)
            latestPagerHoldCallback.value(false)
        }
    }
    when (state) {
        is HomeRenderState.Loading -> BasicText(
            text = "Loading home…",
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        )

        is HomeRenderState.NotFound -> BasicText(
            text = "Home not found",
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        )

        is HomeRenderState.Loaded -> {
            // A3 normal viewport: measure the raw height here and lay the grid out at
            // exactly the usable height (raw minus the style's bottom clearance), so the
            // pill/system-gesture reserve never covers the last row and the scroll range
            // is the exact overflow. The grid consumes the usable height for all of its
            // viewport math; the Dp conversion below only sizes the layout.
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val rawHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(0)
                val usableHeightPx = usableCanvasHeightPx(
                    rawHeightPx = rawHeightPx,
                    bottomClearancePx = viewportStyle.bottomClearancePx,
                )
                HomeCanvasGrid(
                    loaded = state,
                    iconsByIdentity = iconsByIdentity,
                    onLaunchApp = onLaunchApp,
                    overscanRows = overscanRows,
                    viewportStyle = viewportStyle,
                    usableHeightPx = usableHeightPx,
                    modifier = Modifier.fillMaxWidth()
                        .height(with(density) { usableHeightPx.toDp() }),
                    onMoveTile = onMoveTile,
                    onResizeTile = onResizeTile,
                    onRemoveTile = onRemoveTile,
                    surfaceEntries = surfaceEntries,
                    surfaceEntriesTileId = surfaceEntriesTileId,
                    onSelectedTileChange = onSelectedTileChange,
                    onSurfaceExecute = onSurfaceExecute,
                    onSurfaceTogglePin = onSurfaceTogglePin,
                    pinnedByTile = pinnedByTile,
                    onPinnedShortcutClick = onPinnedShortcutClick,
                    onTransientHoldChange = onTransientHoldChange,
                    onPagerHoldChange = onPagerHoldChange,
                    isVisible = isVisible,
                    moveCommitAck = moveCommitAck,
                )
            }
        }
    }
}

/** Legacy stable id for the removed HOME-role request overlay layer; kept for test compat. */
const val HOME_ROLE_LAYER_ID: String = "home-role-request"

/**
 * Legacy accessible description and visible label for the removed HOME-role request
 * control. No longer rendered by [HomeCanvas]; kept for test compat.
 */
const val HOME_ROLE_DESCRIPTION: String = "Set ProtoLaunch as default home"

/**
 * A3 usable canvas height: raw viewport height minus the caller-resolved bottom
 * clearance (nav pill / system-gesture reserve), floored at zero.
 *
 * Pure, framework-free and unit-testable.
 */
internal fun usableCanvasHeightPx(rawHeightPx: Int, bottomClearancePx: Int): Int {
    require(rawHeightPx >= 0) { "rawHeightPx must be non-negative" }
    require(bottomClearancePx >= 0) { "bottomClearancePx must be non-negative" }
    return (rawHeightPx - bottomClearancePx).coerceAtLeast(0)
}

/**
 * P0 canvas hold-reset predicate for [HomeCanvas].
 *
 * Loading/NotFound never compose [HomeCanvasGrid], so the grid's hold emission
 * cannot reset shell `homeHold` on those transitions: they must force false
 * here. Loaded (empty or populated) stays owned by the grid, which emits its
 * own false on the empty path and on dispose. Pure, framework-free and
 * unit-testable.
 */
internal fun shouldResetCanvasHold(state: HomeRenderState): Boolean =
    state !is HomeRenderState.Loaded
