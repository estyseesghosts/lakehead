package me.foxtails.lakehead

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.feature.allapps.AllAppsScreen
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.home.HomeCanvas
import me.foxtails.lakehead.feature.home.HomeCanvasViewportStyle
import me.foxtails.lakehead.feature.home.HomeRenderState

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
    onMoveTile: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    onRemoveTile: (ItemId) -> Unit = { },
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                LAUNCHER_PAGE_FEED -> FeedSurface(
                    modifier = Modifier.fillMaxSize(),
                )

                LAUNCHER_PAGE_ALL_APPS -> AllAppsScreen(
                    state = allAppsState,
                    icons = allAppsIcons,
                    onLaunchApp = onLaunchApp,
                    onAddToHome = onAddToHome,
                    modifier = Modifier.fillMaxSize(),
                    bottomClearance = bottomClearance,
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
                    bottomClearance = bottomClearance,
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
