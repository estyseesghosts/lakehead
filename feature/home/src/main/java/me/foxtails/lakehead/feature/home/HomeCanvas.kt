package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.FloatingControlOverlay
import me.foxtails.lakehead.core.designsystem.FrameAnchor
import me.foxtails.lakehead.core.designsystem.FrameInsets
import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.HorizontalFrameAnchor
import me.foxtails.lakehead.core.designsystem.OverlayFocusPolicy
import me.foxtails.lakehead.core.designsystem.OverlayLayer
import me.foxtails.lakehead.core.designsystem.VerticalFrameAnchor
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
 *   background; populated states render the scrolling tile grid. Both loaded cases float
 *   exactly one truthful `home-role-request` layer above the canvas (the existing
 *   HOME-role callback) with [OverlayFocusPolicy.LEAVE_FOCUS_UNCHANGED], at least 48dp
 *   touch target, explicit description/anchor/insets, and no fake destinations.
 * - Edit mode is transient presentation state owned by [HomeCanvasGrid]: [onMoveTile]
 *   reports a drag-committed [ItemId] and [LogicalPosition] to the caller, and [onResizeTile]
 *   reports a resize-committed [ItemId] and [LogicalSpan]. The caller owns persistence.
 *   This composable performs no writes itself.
 */
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
            val roleLayer = OverlayLayer(
                id = HOME_ROLE_LAYER_ID,
                priority = 10,
                anchor = FrameAnchor(
                    horizontal = HorizontalFrameAnchor.CENTER,
                    vertical = VerticalFrameAnchor.BOTTOM,
                ),
                size = FrameSize(widthPx = 240, heightPx = 72),
                insets = FrameInsets(bottomPx = 16),
                contentDescription = HOME_ROLE_DESCRIPTION,
                content = {
                    BasicText(text = HOME_ROLE_DESCRIPTION)
                },
            )
            FloatingControlOverlay(
                modifier = modifier.fillMaxSize(),
                backgroundContent = {
                    HomeCanvasGrid(
                        loaded = state,
                        iconsByIdentity = iconsByIdentity,
                        onLaunchApp = onLaunchApp,
                        overscanRows = overscanRows,
                        viewportStyle = viewportStyle,
                        modifier = Modifier.fillMaxSize(),
                        onMoveTile = onMoveTile,
                        onResizeTile = onResizeTile,
                    )
                },
                overlays = listOf(roleLayer),
                focusPolicy = OverlayFocusPolicy.LEAVE_FOCUS_UNCHANGED,
                minTouchTargetDp = 48.dp,
                onOverlayActivated = { id ->
                    if (id == HOME_ROLE_LAYER_ID) {
                        onRequestDefaultHome()
                    }
                },
            )
        }
    }
}

/** Stable id for the single truthful HOME-role request overlay layer. */
const val HOME_ROLE_LAYER_ID: String = "home-role-request"

/** Accessible description and visible label for the HOME-role request control. */
const val HOME_ROLE_DESCRIPTION: String = "Set ProtoLaunch as default home"
