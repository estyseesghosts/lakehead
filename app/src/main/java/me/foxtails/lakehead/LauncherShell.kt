package me.foxtails.lakehead

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.designsystem.GestureArbitration.ArbWinner
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.TileNotifications
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.MediaAlbum
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.WidgetProviderEntry
import me.foxtails.lakehead.feature.home.HomeWidgetContent
import me.foxtails.lakehead.feature.home.NoHomeWidgetContent
import me.foxtails.lakehead.feature.home.WidgetPickerOverlay
import me.foxtails.lakehead.feature.allapps.AllAppsScreen
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.allapps.AlphabetPickerState
import me.foxtails.lakehead.feature.allapps.blocksPager
import me.foxtails.lakehead.feature.allapps.headerItemIndex
import me.foxtails.lakehead.feature.allapps.sectionIndexForKey
import me.foxtails.lakehead.feature.allapps.toAlphabetPickerState
import me.foxtails.lakehead.feature.allapps.toAppSections
import me.foxtails.lakehead.feature.home.FolderChooserOverlay
import me.foxtails.lakehead.feature.home.FolderOverlay
import me.foxtails.lakehead.feature.home.FolderRemovePrompt
import me.foxtails.lakehead.feature.home.HomeCanvas
import me.foxtails.lakehead.feature.home.HomeCanvasViewportStyle
import me.foxtails.lakehead.feature.home.AlbumPickerOverlay
import me.foxtails.lakehead.feature.home.AlbumSlideshowPromptOverlay
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.platform.feed.FeedAvailability
import me.foxtails.lakehead.platform.feed.FeedDiagnostic
import me.foxtails.lakehead.platform.feed.FeedProviderId

/** Feed page index in the launcher pager. */
const val LAUNCHER_PAGE_FEED = 0

/** Home tile page index in the launcher pager. */
const val LAUNCHER_PAGE_TILE = 1

/** All Apps page index in the launcher pager. */
const val LAUNCHER_PAGE_ALL_APPS = 2

/** Total page count of the launcher pager. */
const val LAUNCHER_PAGE_COUNT = 3

/**
 * Top-level 3-page pager shell: Feed, Home tile canvas, All Apps.
 *
 * Swipe or the floating [LauncherNavPill] navigates; there is no 4th tab and no route
 * table. The root is opaque in the theme background so all pages share the
 * colour-milestone background; only empty-cell gaps inside HomeCanvasGrid remain
 * transparent. Page 0 is the [FeedSurface] host-boundary seam, page 1 is [HomeCanvas]
 * with the caller's state/icons/callbacks, and page 2 is [AllAppsScreen] with the
 * caller's state/icons/callbacks. The current page is [rememberSaveable] so it survives
 * configuration changes. Pager motion uses foundation defaults only (no custom
 * fade/scale). The pill floats above content with [Alignment.BottomCenter]; content
 * reserves max([LauncherNavPillBottomClearance], gesture inset + pill height +
 * rounding headroom) so the pill never covers the last row.
 *
 * P4 picker ownership: the shell hoists transient [AlphabetPickerState] (plain
 * `remember`, never persisted) so header taps have a production open path and the
 * pager can block. Tapping a section header opens the picker with enablement derived
 * from the current All Apps sections and forwards the key to [onHeaderTap]; picking
 * a letter reports it via [onHeaderTap] then dismisses. While `picker?.isOpen`
 * ([blocksPager]) the pager sets `userScrollEnabled = false`, so swipe navigation is
 * held; programmatic pill navigation still works and back dismisses inside the
 * overlay without reaching the pager.
 *
 * P2 gesture arbitration: the picker block stays the top guard and a
 * [GestureArbitration.arbPriority] guard sits alongside it. The shell owns no
 * drag deltas (page children own their slop/settle thresholds), so the shell
 * arbitrates with [LockedAxis.UNDECIDED]: picker-open still wins and disables
 * swipe, anything else leaves the pager enabled. The floating pill always uses
 * programmatic `animateScrollToPage`, which is unaffected by
 * `userScrollEnabled`, so it keeps working while swipe is held.
 *
 * A3 bottom-33% start region: the outer Box measures its height via
 * `onSizeChanged` and passively observes each gesture's first down in the
 * Initial pointer pass (never consuming, so tiles/scroll/pager are
 * unaffected). The latch [pagerRegionAllowed] records whether that down fell
 * in the bottom strip per [GestureArbitration.isBottomRegionStart] and is
 * ANDed into `userScrollEnabled` alongside the picker/winner/homeHold/
 * pagerHold guards. Top-67% starts never page; bottom-33% starts page unless
 * another guard holds. The pill bypasses `userScrollEnabled` and always works.
 *
 * S1 settings menu: the shell hoists transient `isSettingsOpen` (plain
 * `remember`, never persisted). A Home long-press opens the menu; Back, the
 * scrim, or a provider pick dismisses it, and the pick reports through
 * [onSelectFeedProvider] (binding stays with the caller). The menu also
 * exposes "Export layout…" and "Import layout…" rows that invoke
 * [onExportHomeLayout] and [onImportHomeLayout] without dismissing. While
 * open the pager sets `userScrollEnabled = false`, so swipe navigation is
 * held; programmatic pill navigation still works.
 *
 * F3 feed driving: the shell derives the left-most page progress and settled
 * visibility from the pager state and reports them through [onFeedProgress] /
 * [onFeedVisible] (production forwards to the gateway's never-throw notify
 * verbs, which parallax and open/close the bound overlay). The per-gesture
 * [feedStartAllowed] latch bypasses only the A3 region rule for gestures that
 * start settled on the feed page; every other guard stays. The provider view's
 * window handshake flows through [onFeedWindowAttached] / [onFeedWindowDetached]
 * (production registers/unregisters the host window with the bound overlay, which
 * is what lets the provider render pixels).
 */
@Composable
internal fun LauncherShell(
    homeState: HomeRenderState,
    homeIcons: Map<AppIdentity, ImageBitmap>,
    allAppsState: AllAppsViewState,
    allAppsIcons: Map<AppIdentity, ImageBitmap>,
    onLaunchApp: (AppIdentity) -> Unit,
    onAddToHome: (AppIdentity) -> Unit,
    onRequestDefaultHome: () -> Unit,
    // Feed host verdict from the platform gateway (owned by the caller): page 0
    // hosts the provider surface when Available, else the honest fallback.
    // Required (no default) so callers cannot silently hide Unavailable.
    feedAvailability: FeedAvailability,
    onMoveTile: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    onRemoveTile: (ItemId) -> Unit = { },
    // B4 edit-menu rows (thin read-only domain interface via :app): rank-ordered
    // shortcut entries for the currently selected home tile. Discovery, menu
    // execution, and pin persistence are implemented; tile rendering remains
    // deferred to C1. Row taps report the selected ItemId and entry id, and
    // execution/persistence stay with the caller. B1 selection scoping: [surfaceEntriesTileId] names the
    // tile the rows were loaded for (null = unscoped) and
    // [onSelectedTileChange] reports grid selection transitions for
    // query-on-select; the shell never queries.
    surfaceEntries: List<ShortcutSurfaceEntry> = emptyList(),
    surfaceEntriesTileId: ItemId? = null,
    onSelectedTileChange: (ItemId?) -> Unit = {},
    onSurfaceExecute: (ItemId, String) -> Unit = { _, _ -> },
    onSurfaceTogglePin: (ItemId, String) -> Unit = { _, _ -> },
    pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
    notificationsByTile: Map<ItemId, TileNotifications> = emptyMap(),
    // Album slideshow photo per tile (slice 6 wires the coordinator supplying
    // this). Missing entries render the normal icon.
    albumPhotoByTile: Map<ItemId, ImageBitmap> = emptyMap(),
    // Album offer prompt: non-null [albumPromptLabel] opens the Yes/No prompt
    // for that gallery label; the accept/decline/dismiss verbs belong to the
    // caller (the coordinator). Defaults keep the shell prompt-free.
    albumPromptLabel: String? = null,
    onAlbumPromptAccept: () -> Unit = {},
    onAlbumPromptDecline: () -> Unit = {},
    onAlbumPromptDismiss: () -> Unit = {},
    // Album picker: non-null [albumPickerAlbums] opens the list (empty means
    // the honest empty row); the pick/dismiss verbs belong to the caller.
    albumPickerAlbums: List<MediaAlbum>? = null,
    onAlbumPick: (AlbumRef) -> Unit = {},
    onAlbumPickerDismiss: () -> Unit = {},
    // Home-tile taps (body/symbol) report here; All Apps keeps [onLaunchApp].
    // Defaults preserve the single-callback behavior for callers that do not
    // need the distinction (tap-advance stays with the coordinator).
    onHomeLaunchApp: (AppIdentity) -> Unit = onLaunchApp,
    // Settled home visibility for the album rotation ticker: true only while
    // settled on the home tile page. Defaults to a no-op.
    onHomeVisibleChange: (Boolean) -> Unit = {},
    onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
    onHeaderTap: (String) -> Unit = {},
    // S1 settings menu: reports the feed provider picked in the transient
    // settings menu. The shell dismisses the menu; binding stays with the caller.
    onSelectFeedProvider: (FeedProviderId) -> Unit = {},
    // Gateway diagnosis (status + cause) shown in the settings footer; null = none.
    feedDiagnostic: FeedDiagnostic? = null,
    // S1 settings menu: export/import home layout as JSON. These fire without
    // dismissing the menu; SAF launchers and persistence stay with the caller.
    onExportHomeLayout: () -> Unit = {},
    onImportHomeLayout: () -> Unit = {},
    // Opens the system notification-listener access screen; the grant happens there.
    onOpenNotificationAccess: () -> Unit = {},
    // F3 feed driving: [onFeedProgress] receives the left-most page scroll
    // progress (1 when the feed page is fully shown, 0 on home and beyond) on
    // every pager offset change; [onFeedVisible] receives true only while
    // settled on the feed page. Both default to no-ops so previews and tests
    // that do not wire a gateway keep the A3 behavior. The production caller
    // forwards to the gateway's never-throw notify verbs.
    onFeedProgress: (Float) -> Unit = {},
    onFeedVisible: (Boolean) -> Unit = {},
    // F3 window handshake: [onFeedWindowAttached] fires once per hosted provider
    // identity (the production caller registers the host window with the bound
    // overlay through the gateway, which is what lets the provider render
    // pixels); [onFeedWindowDetached] fires when the hosted view releases. Both
    // default to no-ops so previews and tests that do not wire a gateway keep
    // the prior behavior.
    onFeedWindowAttached: () -> Unit = {},
    onFeedWindowDetached: () -> Unit = {},
    // Widgets: the hosted-view slot, the installed providers for the picker, and
    // the provider the user picked (binding and placement stay with the caller).
    widgetContent: HomeWidgetContent = NoHomeWidgetContent,
    widgetProviders: List<WidgetProviderEntry> = emptyList(),
    onAddWidget: (WidgetProviderEntry) -> Unit = {},
    // Folders: overlay, chooser and confirmation state plus their verbs, owned by the caller.
    folders: FolderShell = FolderShell.None,
    modifier: Modifier = Modifier,
    initialPage: Int = LAUNCHER_PAGE_TILE,
    viewportStyle: HomeCanvasViewportStyle = HomeCanvasViewportStyle(),
) {
    var restoredPage by rememberSaveable {
        mutableIntStateOf(initialPage.coerceIn(0, LAUNCHER_PAGE_COUNT - 1))
    }
    val pagerState = rememberPagerState(
        initialPage = restoredPage,
        pageCount = { LAUNCHER_PAGE_COUNT },
    )
    LaunchedEffect(pagerState.currentPage) {
        restoredPage = pagerState.currentPage
    }
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val gestureBottom = with(density) {
        WindowInsets.systemGestures.getBottom(density).toDp()
    }
    // P2 pill overlap: the pill is lifted by gestureBottom via
    // bottomSafetyInset, so its top sits at gestureBottom + pillHeight above
    // the container bottom. Pill height minimum is 48dp touch target + 2x12dp
    // outer padding = 72dp (NavigationControlPill defaults). Each Dp rounds
    // to px separately in layout (48dp + 12dp + 12dp vs a single 72dp can
    // differ by ~1px, e.g. density 2.625: 126+32+32=190px vs 189px), so the
    // px reserve rounds gestureBottom and pillHeight separately, sums in px,
    // and adds 2px rounding headroom; floored by the legacy 80dp minimum.
    val pillHeight: Dp = 72.dp
    val homeBottomClearancePx = maxOf(
        with(density) { LauncherNavPillBottomClearance.roundToPx() },
        with(density) { gestureBottom.roundToPx() } +
            with(density) { pillHeight.roundToPx() } + 2,
    )
    // A3 normal viewport: the shell owns the bottom clearance (max of legacy
    // vs gesture inset + pill height + rounding, resolved in px above). Home
    // takes it as a plain pixel count inside its viewport style so no Android
    // object enters the grid; All Apps keeps the Dp form of the same px
    // reserve for its own padding.
    val bottomClearance: Dp = with(density) { homeBottomClearancePx.toDp() }

    // P4 transient picker, hoisted so the pager can block while it is open. Plain
    // remember: open/close is gesture state, never persisted. Null means no picker
    // has been opened yet; AllAppsScreen renders its bare list until then.
    var picker by remember { mutableStateOf<AlphabetPickerState?>(null) }
    // Enablement mirrors the sections AllAppsScreen derives from the same entries,
    // so the opened picker jumps exactly to the rendered sections. Grouping itself
    // stays owned by the feature; this is a read-only derivation for enablement.
    val pickerSections = remember(allAppsState) {
        (allAppsState as? AllAppsViewState.Loaded)?.entries?.toAppSections().orEmpty()
    }

    // Hoisted LazyListState so the shell can scroll the All Apps list from
    // the picker-letter callback without depending on AllAppsScreen internals.
    val listState = rememberLazyListState()

    // P2 arbitration alongside the P4 top guard: picker-open wins (swipe held);
    // the axis lock is UNDECIDED at shell level because drag deltas live in the
    // page children, so this guard preserves `!blocksPager()` swipe behavior
    // exactly while routing the decision through the shared predicate.
    val pickerBlocksPager = picker.blocksPager()
    val pagerWinner = remember(pickerBlocksPager) {
        GestureArbitration.arbPriority(
            isPickerOpen = pickerBlocksPager,
            isTap = false,
            locked = LockedAxis.UNDECIDED,
        )
    }
    // A1 transient home hold: true while home reports an active edit or wiggle
    // editing. Plain remember: gesture state,
    // never persisted. ANDed into userScrollEnabled below; the pill's
    // programmatic animateScrollToPage is unaffected by userScrollEnabled.
    var homeHold by remember { mutableStateOf(false) }
    // A2 transient tile-touch hold: true from a tile-origin down until release
    // (tap, select-drag, active move/resize) so the gesture never becomes a
    // page swipe. Plain remember alongside homeHold; ANDed into
    // userScrollEnabled below; the pill is unaffected.
    var pagerHold by remember { mutableStateOf(false) }
    // A3 bottom-33% start region: measured shell height (px) for the
    // isBottomRegionStart gate, plus a per-gesture latch recording whether
    // the current gesture's first down fell in the bottom strip. Both are
    // plain remember transient gesture state, never persisted. pagerRegionAllowed
    // defaults to true so the pager behaves as before until the first
    // measured down latches a region; ANDed into userScrollEnabled below
    // alongside picker/winner/homeHold/pagerHold. The pill is unaffected.
    var shellHeightPx by remember { mutableFloatStateOf(0f) }
    var pagerRegionAllowed by remember { mutableStateOf(true) }
    // S1 transient settings menu: true from a Home long-press until dismiss or
    // provider pick. Plain remember: gesture state, never persisted. ANDed into
    // userScrollEnabled below; the pill's programmatic animateScrollToPage is
    // unaffected by userScrollEnabled.
    var isSettingsOpen by remember { mutableStateOf(false) }
    // Transient widget picker, opened by the (+) button; holds the pager like the settings menu.
    var isWidgetPickerOpen by remember { mutableStateOf(false) }
    // F3 feed-start latch: true when the current gesture's first down found the
    // pager settled on the feed page. Plain remember transient gesture state,
    // never persisted. ORed with the A3 region latch below: the feed page hosts
    // provider-owned full-bleed content with no tiles to protect from accidental
    // paging, so swipes starting there always navigate (the pill is unaffected
    // regardless). Latched per gesture like pagerRegionAllowed so the gate never
    // flips mid-swipe when currentPage settles across the feed/home boundary.
    var feedStartAllowed by remember { mutableStateOf(false) }

    // F3 feed driving: left-most page progress (1 on feed, 0 on home and beyond)
    // and settled visibility flow to the gateway through onFeedProgress/Visible.
    // Reads of currentPage/currentPageOffsetFraction/isScrollInProgress here only
    // schedule recomposition; the callbacks fire from LaunchedEffect, never during
    // composition. Visibility additionally gates on !isScrollInProgress so a
    // feed→home drag mid-swipe (currentPage still the feed snap, offset != 0)
    // never emits visible=true flaps; progress stays continuous for parallax.
    val feedProgress = feedOverlayProgress(
        pagerState.currentPage,
        pagerState.currentPageOffsetFraction,
    )
    val feedVisible = isFeedOverlayVisible(
        pagerState.currentPage,
        pagerState.isScrollInProgress,
        pagerState.currentPageOffsetFraction,
    )
    LaunchedEffect(feedProgress) { onFeedProgress(feedProgress) }
    LaunchedEffect(feedVisible) { onFeedVisible(feedVisible) }
    // B3 P1: settled home visibility for the canvas hide-abort. Mirrors the
    // feed settled predicate so a swipe away discards the transient edit with
    // zero writes; settled on home keeps the exact prior behavior.
    val homeVisible = isHomeCanvasVisible(
        pagerState.currentPage,
        pagerState.isScrollInProgress,
        pagerState.currentPageOffsetFraction,
    )
    // Album rotation ticker follows the same settled signal.
    LaunchedEffect(homeVisible) { onHomeVisibleChange(homeVisible) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { shellHeightPx = it.height.toFloat() }
            .pointerInput(shellHeightPx) {
                // Passive Initial-pass observer: latch the first down's Y only,
                // never consume, so tiles, scroll and the pager receive every
                // event untouched. The remainder of the gesture is drained
                // passively until release.
                awaitEachGesture {
                    val first = awaitPointerEvent(PointerEventPass.Initial)
                    val down = first.changes.firstOrNull { it.pressed }
                        ?: return@awaitEachGesture
                    val height = shellHeightPx
                    if (height > 0f) {
                        pagerRegionAllowed =
                            GestureArbitration.isBottomRegionStart(
                                yPx = down.position.y,
                                heightPx = height,
                            )
                    }
                    // F3: latch whether this gesture started settled on the feed
                    // page (per-gesture, like the region latch above, so a swipe
                    // that settles across the feed/home boundary never flips the
                    // gate mid-gesture). Reading currentPage here takes the live
                    // value at down time; the handler is not restarted by page
                    // changes on purpose. The settled overload (scroll-aware)
                    // keeps a down during an in-flight settle animation from
                    // latching the feed exception.
                    feedStartAllowed = isFeedOverlayVisible(
                        pagerState.currentPage,
                        pagerState.isScrollInProgress,
                        pagerState.currentPageOffsetFraction,
                    )
                    while (true) {
                        val next = awaitPointerEvent(PointerEventPass.Initial)
                        if (next.changes.none { it.pressed }) break
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = isPagerSwipeEnabled(pickerBlocksPager, pagerWinner, homeHold, pagerHold, pagerRegionAllowed, isSettingsOpen || isWidgetPickerOpen || folders.holdsPager, feedStartAllowed),
        ) { page ->
            when (page) {
                LAUNCHER_PAGE_FEED -> FeedSurface(
                    availability = feedAvailability,
                    modifier = Modifier.fillMaxSize(),
                    onWindowAttached = onFeedWindowAttached,
                    onWindowDetached = onFeedWindowDetached,
                )

                LAUNCHER_PAGE_ALL_APPS -> AllAppsScreen(
                    state = allAppsState,
                    icons = allAppsIcons,
                    onLaunchApp = onLaunchApp,
                    onAddToHome = onAddToHome,
                    modifier = Modifier.fillMaxSize(),
                    bottomClearance = bottomClearance,
                    onHeaderTap = { key ->
                        picker = pickerSections.toAlphabetPickerState(isOpen = true)
                        onHeaderTap(key)
                    },
                    picker = picker,
                    onPickerDismiss = { picker = picker?.copy(isOpen = false) },
                    onPickerLetterPicked = { letter ->
                        // Dismiss first, then look up the target section header
                        // and scroll to it. scrollToItem is instant (not animated);
                        // missing key is a no-op.
                        picker = picker?.copy(isOpen = false)
                        val sectionIdx = pickerSections.sectionIndexForKey(letter)
                        if (sectionIdx != null) {
                            val itemIdx = pickerSections.headerItemIndex(sectionIdx)
                            scope.launch { listState.scrollToItem(itemIdx) }
                        }
                    },
                    listState = listState,
                )

                else -> HomeCanvas(
                    state = homeState,
                    iconsByIdentity = homeIcons,
                    onLaunchApp = onHomeLaunchApp,
                    onRequestDefaultHome = onRequestDefaultHome,
                    modifier = Modifier.fillMaxSize(),
                    viewportStyle = viewportStyle.copy(
                        bottomClearancePx = homeBottomClearancePx,
                    ),
                    onMoveTile = onMoveTile,
                    onResizeTile = onResizeTile,
                    onRemoveTile = onRemoveTile,
                    surfaceEntries = surfaceEntries,
                    surfaceEntriesTileId = surfaceEntriesTileId,
                    onSelectedTileChange = onSelectedTileChange,
                    onSurfaceExecute = onSurfaceExecute,
                    onSurfaceTogglePin = onSurfaceTogglePin,
                    pinnedByTile = pinnedByTile,
                    notificationsByTile = notificationsByTile,
                    albumPhotoByTile = albumPhotoByTile,
                    onPinnedShortcutClick = onPinnedShortcutClick,
                    onTransientHoldChange = { homeHold = it },
                    onPagerHoldChange = { pagerHold = it },
                    isVisible = homeVisible,
                    widgetContent = widgetContent,
                    onRequestAddWidget = { isWidgetPickerOpen = true },
                    onOpenFolder = folders.onOpen,
                    onNewFolder = folders.onNewFolder,
                    onRequestAddToFolder = folders.onRequestAddToFolder,
                    onDropOnFolder = folders.onDropOnFolder,
                )
            }
        }
        LauncherNavPill(
            selectedPage = pagerState.currentPage,
            onSelectPage = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomSafetyInset = gestureBottom,
            onHomeLongPress = { isSettingsOpen = true },
        )
        WidgetPickerOverlay(
            isOpen = isWidgetPickerOpen,
            providers = widgetProviders,
            columns = homeColumns(homeState),
            onPick = { entry ->
                isWidgetPickerOpen = false
                onAddWidget(entry)
            },
            onDismiss = { isWidgetPickerOpen = false },
        )
        // Folder overlay, chooser and confirmation float above the pager and the pill.
        FolderOverlay(
            isOpen = folders.openMembers != null,
            members = folders.openMembers.orEmpty(),
            columns = homeColumns(homeState),
            iconsByIdentity = homeIcons,
            onLaunchApp = folders.onLaunchMember,
            onMoveToHome = folders.onMemberToHome,
            onRemoveMember = folders.onMemberRemove,
            onMoveMember = folders.onMemberMove,
            onResizeMember = folders.onMemberResize,
            pinnedByTile = folders.pinnedByTile,
            notificationsByTile = folders.notificationsByTile,
            albumPhotoByTile = folders.albumPhotoByTile,
            onPinnedShortcutClick = folders.onPinnedShortcutClick,
            surfaceEntries = folders.surfaceEntries,
            surfaceEntriesTileId = folders.surfaceEntriesTileId,
            onSelectedTileChange = folders.onSelectedTileChange,
            onSurfaceExecute = folders.onSurfaceExecute,
            onSurfaceTogglePin = folders.onSurfaceTogglePin,
            onDismiss = folders.onClose,
            widgetContent = widgetContent,
        )
        FolderChooserOverlay(
            isOpen = folders.chooserFolders != null,
            folders = folders.chooserFolders.orEmpty(),
            iconsByIdentity = homeIcons,
            onPick = folders.onPickFolder,
            onDismiss = folders.onChooserDismiss,
        )
        FolderRemovePrompt(
            isOpen = folders.removePromptOpen,
            onConfirm = folders.onRemoveConfirm,
            onDismiss = folders.onRemoveDismiss,
        )
        // S1 settings menu floats above the pager and the pill. A provider pick
        // reports through onSelectFeedProvider then dismisses; Back and the scrim
        // dismiss without reporting. Export/import fire without dismissing.
        SettingsMenuOverlay(
            isOpen = isSettingsOpen,
            availability = feedAvailability,
            diagnostic = feedDiagnostic,
            onDismiss = { isSettingsOpen = false },
            onSelectProvider = { provider ->
                onSelectFeedProvider(provider)
                isSettingsOpen = false
            },
            onExportHomeLayout = onExportHomeLayout,
            onImportHomeLayout = onImportHomeLayout,
            onOpenNotificationAccess = onOpenNotificationAccess,
        )
        // Album offer prompt and picker float above the pager and the pill.
        // Both are caller-owned (the coordinator); the shell only renders.
        AlbumSlideshowPromptOverlay(
            isOpen = albumPromptLabel != null,
            appLabel = albumPromptLabel ?: "",
            onAccept = onAlbumPromptAccept,
            onDecline = onAlbumPromptDecline,
            onDismiss = onAlbumPromptDismiss,
        )
        AlbumPickerOverlay(
            isOpen = albumPickerAlbums != null,
            albums = albumPickerAlbums.orEmpty(),
            onPick = onAlbumPick,
            onDismiss = onAlbumPickerDismiss,
        )
    }
}

private fun homeColumns(state: HomeRenderState): Int =
    (state as? HomeRenderState.Loaded)?.columns?.count ?: 5

/**
 * A1 pager swipe gate: swipe is enabled only when the picker is not blocking,
 * arbitration did not award the gesture to the picker, and home is not
 * holding (active edit or wiggle editing).
 *
 * A2 adds the tile-touch hold: swipe is additionally disabled while
 * [pagerHold] is true (tile-origin down until release), so a tile touch never
 * becomes a page swipe. Background-origin gestures leave [pagerHold] false.
 *
 * A3 adds the bottom-33% start-region latch: swipe is additionally enabled
 * only when [pagerRegionAllowed] is true (the current gesture's first down
 * fell in the bottom strip per `isBottomRegionStart`). Top-67% starts never
 * page on any page. Defaults to true so pre-measurement behavior matches A2.
 *
 * S1 adds the transient settings-menu hold: swipe is additionally disabled
 * while [isSettingsOpen] is true (the settings menu is up). Defaults to false
 * so callers that do not hoist the menu keep the A3 behavior exactly.
 *
 * F3 adds the feed-start exception: swipe is enabled when [feedStartAllowed] is
 * true (the gesture's first down found the pager settled on the feed page) even
 * if the A3 region latch is false. Defaults to false so callers that do not
 * track the feed page keep the S1 behavior exactly. The exception bypasses only
 * the region latch — picker, arbitration, holds and the settings menu still
 * disable swipe — because the feed page hosts provider-owned content with no
 * tiles to protect, and trapping the user there with only the pill as escape is
 * worse than a top-start page. Programmatic pill navigation via
 * `animateScrollToPage` bypasses `userScrollEnabled` and is unaffected by this
 * gate.
 */
internal fun isPagerSwipeEnabled(
    pickerBlocksPager: Boolean,
    pagerWinner: ArbWinner,
    homeHold: Boolean,
    pagerHold: Boolean = false,
    pagerRegionAllowed: Boolean = true,
    isSettingsOpen: Boolean = false,
    feedStartAllowed: Boolean = false,
): Boolean = !pickerBlocksPager && pagerWinner != ArbWinner.PICKER && !homeHold && !pagerHold && (pagerRegionAllowed || feedStartAllowed) && !isSettingsOpen

/**
 * F3 feed overlay scroll progress from the pager position.
 *
 * Returns 1 when the feed page ([LAUNCHER_PAGE_FEED]) is fully shown and 0 on
 * home and beyond, linear in between: `1 - (currentPage - feedPage + offset)`.
 * [pageOffsetFraction] is the pager's current-page offset as a fraction of the
 * page size (0 when settled). Out-of-range positions (overscroll past the feed,
 * pages past home) clamp to `0..1`, and `NaN` maps to `0` so a corrupt offset
 * parks the overlay instead of poisoning the gateway cache (which applies the
 * same coercion downstream). Pure, framework-free and unit-testable.
 */
internal fun feedOverlayProgress(currentPage: Int, pageOffsetFraction: Float): Float {
    val raw = 1f - (currentPage - LAUNCHER_PAGE_FEED + pageOffsetFraction)
    return if (raw.isNaN()) 0f else raw.coerceIn(0f, 1f)
}

/**
 * F3 feed overlay visibility from the settled pager page.
 *
 * True only while settled on the feed page: [currentPage] is the feed page, no
 * scroll is in progress ([isScrollInProgress] false — during a drag or settle
 * animation `currentPage` only names the nearest snap, so it flaps mid-swipe),
 * and [pageOffsetFraction] is exactly `0` (no peek offset). The gateway's
 * open/close verbs therefore fire on arrival/departure rather than on every
 * mid-swipe snap change. Pure, framework-free and unit-testable. The default
 * arguments preserve the settled-page check for callers that do not track scroll
 * state (down-time latch, previews).
 */
internal fun isFeedOverlayVisible(
    currentPage: Int,
    isScrollInProgress: Boolean = false,
    pageOffsetFraction: Float = 0f,
): Boolean =
    currentPage == LAUNCHER_PAGE_FEED && !isScrollInProgress && pageOffsetFraction == 0f

/**
 * B3 P1 settled home visibility for the canvas hide-abort.
 *
 * True only while settled on the home tile page ([LAUNCHER_PAGE_TILE]): no
 * scroll in progress and zero peek offset, mirroring [isFeedOverlayVisible].
 * The shell passes this as `HomeCanvas(isVisible = ...)` so a pager
 * navigation away discards the transient edit with zero writes. Defaults
 * preserve the settled-page check for callers that do not track scroll state
 * (previews). Pure, framework-free and unit-testable.
 */
internal fun isHomeCanvasVisible(
    currentPage: Int,
    isScrollInProgress: Boolean = false,
    pageOffsetFraction: Float = 0f,
): Boolean =
    currentPage == LAUNCHER_PAGE_TILE && !isScrollInProgress && pageOffsetFraction == 0f
