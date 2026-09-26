package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
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
 *   reports a drag-committed [ItemId] and [LogicalPosition] to the caller, [onResizeTile]
 *   reports a resize-committed [ItemId] and [LogicalSpan], and [onRemoveTile] reports a
 *   remove-tap [ItemId]. The caller owns persistence. This composable performs no
 *   writes itself.
 * - The swipe surface is transient presentation state owned by [HomeCanvasGrid]:
 *   [surfaceEntries] carries the rank-ordered shortcut rows for the currently shown
 *   tile (empty until shortcut discovery lands), and row taps report
 *   [onSurfaceExecute]/[onSurfaceTogglePin] with the shown [ItemId] and entry id.
 *   Execution and pin persistence stay with the caller.
 *
 * @param onRequestDefaultHome retained for a future settings entry; currently unused by
 * this canvas (no HOME-role overlay is rendered).
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
    onMoveTile: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    onRemoveTile: (ItemId) -> Unit = {},
    surfaceEntries: List<ShortcutSurfaceEntry> = emptyList(),
    onSurfaceExecute: (ItemId, String) -> Unit = { _, _ -> },
    onSurfaceTogglePin: (ItemId, String) -> Unit = { _, _ -> },
    bottomClearance: Dp = 0.dp,
) {
    require(overscanRows >= 0L) { "overscanRows must be non-negative" }
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
            Column(modifier = modifier.fillMaxSize()) {
                HomeCanvasGrid(
                    loaded = state,
                    iconsByIdentity = iconsByIdentity,
                    onLaunchApp = onLaunchApp,
                    overscanRows = overscanRows,
                    viewportStyle = viewportStyle,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onMoveTile = onMoveTile,
                    onResizeTile = onResizeTile,
                    onRemoveTile = onRemoveTile,
                    surfaceEntries = surfaceEntries,
                    onSurfaceExecute = onSurfaceExecute,
                    onSurfaceTogglePin = onSurfaceTogglePin,
                )
                Spacer(
                    modifier = Modifier.fillMaxWidth().height(bottomClearance),
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
