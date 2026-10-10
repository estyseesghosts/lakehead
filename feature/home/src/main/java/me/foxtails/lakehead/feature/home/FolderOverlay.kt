package me.foxtails.lakehead.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.lakehead.core.designsystem.LocalLauncherDimensions
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Floating overlay for one open folder, drawn over the current Home page.
 *
 * [members] are the folder's app and widget tiles at their persisted cells and spans. The panel
 * hosts the same [HomeCanvasGrid] as Home in its interior mode, so editing is the standard edit
 * mode: long-press a member to select it, drag it to move ([onMoveMember]), use the handle to
 * resize widgets ([onResizeMember]) and the remove button for [onRemoveMember]. Dragging a member
 * out of the panel and releasing moves it back to Home ([onMoveToHome]); while a member is held
 * the panel grows one row downward so it can be placed lower. Outside edit mode a tap launches an
 * app through [onLaunchApp]. The scrim and Back call [onDismiss]; while editing Back leaves edit
 * mode first.
 *
 * Presentation only: no catalogue, database or platform access. Persistence and reconciliation
 * stay with the caller, which feeds the changed [members] back in.
 */
@Composable
fun FolderOverlay(
    isOpen: Boolean,
    members: List<HomeRenderTile>,
    columns: Int,
    iconsByIdentity: HomeCanvasIcons,
    onLaunchApp: (AppIdentity) -> Unit,
    onMoveToHome: (ItemId) -> Unit,
    onRemoveMember: (ItemId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveMember: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    onResizeMember: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    widgetContent: HomeWidgetContent = NoHomeWidgetContent,
    pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
    onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
) {
    if (!isOpen) return
    BackHandler(enabled = true, onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    val dimensions = LocalLauncherDimensions.current
    var moveActive by remember { mutableStateOf(false) }
    var dropOutside by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(FOLDER_OVERLAY_TAG)) {
        val density = LocalDensity.current
        val paddingPx = with(density) { dimensions.gridGap.roundToPx() }
        val layout = remember(members, columns, constraints.maxWidth, constraints.maxHeight, moveActive) {
            folderOverlayLayout(
                tiles = members,
                columns = columns,
                viewportWidthPx = constraints.maxWidth,
                viewportHeightPx = constraints.maxHeight,
                marginPx = paddingPx * 2,
                paddingPx = paddingPx,
                gapPx = paddingPx,
                expandRows = if (moveActive) 1 else 0,
            )
        }
        val loaded = remember(members, columns) { folderInteriorState(members, columns) }
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.45f))
                .testTag(FOLDER_OVERLAY_SCRIM_TAG)
                .clickable(role = Role.Button, onClick = onDismiss),
        )
        val panel = layout.panel
        val innerHeightPx = (panel.bottom - panel.top - 2 * paddingPx).coerceAtLeast(0)
        Box(
            modifier = Modifier
                .offset { IntOffset(panel.left, panel.top) }
                .size(
                    with(density) { (panel.right - panel.left).toDp() },
                    with(density) { (panel.bottom - panel.top).toDp() },
                )
                .background(scheme.surfaceContainer)
                .testTag(FOLDER_OVERLAY_PANEL_TAG)
                // Taps on empty panel area must not reach the dismissing scrim underneath.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            HomeCanvasGrid(
                loaded = loaded,
                iconsByIdentity = iconsByIdentity,
                onLaunchApp = onLaunchApp,
                viewportStyle = HomeCanvasViewportStyle(
                    horizontalPaddingPx = paddingPx,
                    columnGapPx = paddingPx,
                    rowGapPx = paddingPx,
                ),
                usableHeightPx = innerHeightPx,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = with(density) { paddingPx.toDp() })
                    .testTag(FOLDER_OVERLAY_SCROLL_TAG),
                onMoveTile = onMoveMember,
                onResizeTile = onResizeMember,
                onRemoveTile = onRemoveMember,
                pinnedByTile = pinnedByTile,
                onPinnedShortcutClick = onPinnedShortcutClick,
                widgetContent = widgetContent,
                interior = true,
                onDropOutside = onMoveToHome,
                onMoveActiveChange = { moveActive = it },
                onDragOutsideChange = { dropOutside = it },
            )
        }
        if (dropOutside) {
            BasicText(
                text = "Release to move to Home",
                style = TextStyle(color = scheme.onSurface, fontSize = 14.sp),
                modifier = Modifier
                    .offset { IntOffset(panel.left, panel.bottom + paddingPx) }
                    .testTag(FOLDER_DROP_HINT_TAG),
            )
        }
    }
}

/** The folder interior as a loaded Home grid: same tiles, [columns] across, measured extent. */
internal fun folderInteriorState(members: List<HomeRenderTile>, columns: Int): HomeRenderState.Loaded {
    val bottom = members.maxOfOrNull { it.position.row + it.span.rows }?.toLong() ?: 0L
    return HomeRenderState.Loaded(
        requestedHomeId = HomeId(FOLDER_INTERIOR_ID),
        tiles = members,
        columns = GridColumns(columns),
        extent = GridVerticalExtent(occupiedBottomRowExclusive = bottom, totalScrollableRows = bottom),
    )
}

private const val FOLDER_INTERIOR_ID: String = "folder-interior"

const val FOLDER_OVERLAY_TAG: String = "folder-overlay"
const val FOLDER_OVERLAY_SCRIM_TAG: String = "folder-overlay-scrim"
const val FOLDER_OVERLAY_PANEL_TAG: String = "folder-overlay-panel"
const val FOLDER_OVERLAY_SCROLL_TAG: String = "folder-overlay-scroll"
const val FOLDER_DROP_HINT_TAG: String = "folder-drop-hint"
