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
import me.foxtails.lakehead.core.model.ItemId

/**
 * Floating overlay for one open folder, drawn over the current Home page.
 *
 * [members] are the folder's app and widget tiles at their persisted cells and spans; the panel
 * lays them out on [columns] square cells across (an input, five on a normal phone) and scrolls
 * vertically on its own. App members launch through [onLaunchApp]; widget members render through
 * [widgetContent], the same slot Home uses. A long-press on a member opens a small menu:
 * [onMoveToHome] and [onRemoveMember] report the member id. The scrim and Back call [onDismiss];
 * while the member menu is open Back closes only the menu.
 *
 * Presentation only: no catalogue, database or platform access. In-folder move and resize are
 * not part of this overlay; members sit where the first-free search put them.
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
    widgetContent: HomeWidgetContent = NoHomeWidgetContent,
    pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
    onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
) {
    if (!isOpen) return
    var menuTarget by remember { mutableStateOf<ItemId?>(null) }
    BackHandler(enabled = true) {
        if (menuTarget != null) menuTarget = null else onDismiss()
    }
    val scheme = MaterialTheme.colorScheme
    val dimensions = LocalLauncherDimensions.current
    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(FOLDER_OVERLAY_TAG)) {
        val density = LocalDensity.current
        val layout = remember(members, columns, constraints.maxWidth, constraints.maxHeight) {
            with(density) {
                folderOverlayLayout(
                    tiles = members,
                    columns = columns,
                    viewportWidthPx = constraints.maxWidth,
                    viewportHeightPx = constraints.maxHeight,
                    marginPx = dimensions.gridGap.roundToPx() * 2,
                    paddingPx = dimensions.gridGap.roundToPx(),
                    gapPx = dimensions.gridGap.roundToPx(),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.45f))
                .testTag(FOLDER_OVERLAY_SCRIM_TAG)
                .clickable(role = Role.Button, onClick = onDismiss),
        )
        val panel = layout.panel
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
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag(FOLDER_OVERLAY_SCROLL_TAG),
            ) {
                HomeGridLayout(
                    tiles = members,
                    metrics = layout.metrics,
                    renderedSpan = { it.span },
                    minHeightPx = layout.contentHeightPx,
                    tailPx = 0,
                ) { tile, span ->
                    HomeCanvasTile(
                        tile = if (span != tile.span) tile.copy(span = span) else tile,
                        iconBitmap = tile.appIdentity?.let { iconsByIdentity[it] },
                        onLaunchApp = onLaunchApp,
                        pinnedShortcuts = pinnedByTile[tile.itemId].orEmpty(),
                        onShortcutClick = { onPinnedShortcutClick(tile.itemId, it) },
                        onEnterWiggle = { menuTarget = it.itemId },
                        content = if (tile.isWidget) {
                            { editing ->
                                WidgetTileBody(
                                    tile = tile,
                                    isEditing = editing,
                                    widgetContent = widgetContent,
                                    onSelect = {},
                                    onLongPress = { menuTarget = tile.itemId },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        val target = menuTarget
        if (target != null) {
            // The menu lives at overlay level, not inside the panel: a one-row panel is shorter
            // than the menu and would clip its rows out of reach.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(role = Role.Button) { menuTarget = null },
            )
            FolderMemberMenu(
                onMoveToHome = {
                    menuTarget = null
                    onMoveToHome(target)
                },
                onRemove = {
                    menuTarget = null
                    onRemoveMember(target)
                },
                onDismiss = { menuTarget = null },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Actions on one folder member, centred over the open panel. */
@Composable
internal fun FolderMemberMenu(
    onMoveToHome: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh)
            .padding(8.dp)
            .testTag(FOLDER_MEMBER_MENU_TAG),
    ) {
        MenuRow("Move to Home", FOLDER_MENU_MOVE_TAG, onMoveToHome)
        MenuRow("Remove", FOLDER_MENU_REMOVE_TAG, onRemove)
        MenuRow("Cancel", FOLDER_MENU_CANCEL_TAG, onDismiss)
    }
}

@Composable
private fun MenuRow(text: String, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BasicText(
        text = text,
        style = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
    )
}

const val FOLDER_OVERLAY_TAG: String = "folder-overlay"
const val FOLDER_OVERLAY_SCRIM_TAG: String = "folder-overlay-scrim"
const val FOLDER_OVERLAY_PANEL_TAG: String = "folder-overlay-panel"
const val FOLDER_OVERLAY_SCROLL_TAG: String = "folder-overlay-scroll"
const val FOLDER_MEMBER_MENU_TAG: String = "folder-member-menu"
const val FOLDER_MENU_MOVE_TAG: String = "folder-menu-move"
const val FOLDER_MENU_REMOVE_TAG: String = "folder-menu-remove"
const val FOLDER_MENU_CANCEL_TAG: String = "folder-menu-cancel"
