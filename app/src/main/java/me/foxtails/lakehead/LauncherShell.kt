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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.designsystem.GestureArbitration
import me.foxtails.lakehead.core.designsystem.GestureArbitration.ArbWinner
import me.foxtails.lakehead.core.designsystem.GestureArbitration.LockedAxis
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.feature.allapps.AllAppsScreen
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.allapps.AlphabetPickerState
import me.foxtails.lakehead.feature.allapps.blocksPager
import me.foxtails.lakehead.feature.allapps.headerItemIndex
import me.foxtails.lakehead.feature.allapps.sectionIndexForKey
import me.foxtails.lakehead.feature.allapps.toAlphabetPickerState
import me.foxtails.lakehead.feature.allapps.toAppSections
import me.foxtails.lakehead.feature.home.HomeCanvas
import me.foxtails.lakehead.feature.home.HomeCanvasViewportStyle
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.platform.feed.FeedAvailability

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
 * reserves [LauncherNavPillBottomClearance] (or the system-gesture bottom, whichever is
 * larger) so the pill never covers the last row.
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
    // shortcut entries for the currently selected home tile (empty until
    // shortcut discovery lands, so production shows no menu). Row taps report
    // the selected ItemId and entry id; execution and pin persistence stay
    // with the caller.
    surfaceEntries: List<ShortcutSurfaceEntry> = emptyList(),
    onSurfaceExecute: (ItemId, String) -> Unit = { _, _ -> },
    onSurfaceTogglePin: (ItemId, String) -> Unit = { _, _ -> },
    onHeaderTap: (String) -> Unit = {},
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
    val bottomClearance = maxOf(LauncherNavPillBottomClearance, gestureBottom)

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
            userScrollEnabled = isPagerSwipeEnabled(pickerBlocksPager, pagerWinner, homeHold, pagerHold, pagerRegionAllowed),
        ) { page ->
            when (page) {
                LAUNCHER_PAGE_FEED -> FeedSurface(
                    availability = feedAvailability,
                    modifier = Modifier.fillMaxSize(),
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
                    onLaunchApp = onLaunchApp,
                    onRequestDefaultHome = onRequestDefaultHome,
                    modifier = Modifier.fillMaxSize(),
                    viewportStyle = viewportStyle,
                    onMoveTile = onMoveTile,
                    onResizeTile = onResizeTile,
                    onRemoveTile = onRemoveTile,
                    surfaceEntries = surfaceEntries,
                    onSurfaceExecute = onSurfaceExecute,
                    onSurfaceTogglePin = onSurfaceTogglePin,
                    bottomClearance = bottomClearance,
                    onTransientHoldChange = { homeHold = it },
                    onPagerHoldChange = { pagerHold = it },
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
        )
    }
}

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
 * Pure boolean AND, framework-free and unit-testable. Programmatic pill
 * navigation via `animateScrollToPage` bypasses `userScrollEnabled` and is
 * unaffected by this gate.
 */
internal fun isPagerSwipeEnabled(
    pickerBlocksPager: Boolean,
    pagerWinner: ArbWinner,
    homeHold: Boolean,
    pagerHold: Boolean = false,
    pagerRegionAllowed: Boolean = true,
): Boolean = !pickerBlocksPager && pagerWinner != ArbWinner.PICKER && !homeHold && !pagerHold && pagerRegionAllowed
