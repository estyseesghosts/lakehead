package me.foxtails.lakehead

import android.app.Activity
import android.os.Bundle
import android.app.role.RoleManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.RemoveResult
import me.foxtails.lakehead.data.repository.RoomHomePlacementRepository
import me.foxtails.lakehead.feature.home.HomeCanvasViewportStyle
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.feature.home.HomeViewportInputs
import me.foxtails.lakehead.platform.feed.AndroidFeedServiceQuery
import me.foxtails.lakehead.platform.feed.FeedOverlayGateway
import me.foxtails.lakehead.platform.feed.FeedOverlayGatewayImpl
import me.foxtails.lakehead.platform.feed.FeedProviderResolver
import me.foxtails.lakehead.platform.feed.FeedServiceQuery
import me.foxtails.lakehead.platform.launcherapps.AppLauncher
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshotCache
import me.foxtails.lakehead.platform.launcherapps.BoundedLauncherAppIconPngEncoder
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconActivityLookup
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconSnapshotRefresher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsActivityLauncher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue
import me.foxtails.lakehead.platform.shortcuts.LauncherAppsShortcutGateway
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway

internal val PRODUCTION_HOME_ID = HomeId("home-main")

class MainActivity : ComponentActivity() {
    internal val appCatalogue: LauncherAppsAppCatalogue by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsAppCatalogue(applicationContext, lifecycleScope)
    }

    private val appLauncher: AppLauncher by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsActivityLauncher(applicationContext)
    }

    // Shortcut gateway: application context only, never the activity. The gateway
    // owns per-app shortcut queries, the bounded snapshot cache and shortcut /
    // package invalidation; the surface loader joins its rank-ordered rows with
    // persisted tile bindings for the live edit-menu seam (discovery and menu
    // execution are wired; tile shortcut rendering remains deferred to C1).
    internal val shortcutGateway: ShortcutGateway by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsShortcutGateway(applicationContext)
    }

    internal val shortcutSurfaceLoader: ShortcutSurfaceLoader by lazy(LazyThreadSafetyMode.NONE) {
        ShortcutSurfaceLoader(shortcutGateway, placementRepository)
    }

    // B1 selected-tile menu: joins the gateway's rank-ordered shortcuts for
    // the grid-reported selection with the tile's persisted binding into
    // selection-scoped surface rows, resolves row taps to profile-exact gateway
    // launches, and persists pin toggles. Feed + shortcut gateway lifecycle
    // remains unchanged; tile shortcut rendering is deferred to C1.
    internal val shortcutMenuCoordinator: ShortcutMenuCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        ShortcutMenuCoordinator(shortcutGateway, shortcutSurfaceLoader)
    }

    private val placementDatabase: PlacementDatabase by lazy(LazyThreadSafetyMode.NONE) {
        Room.databaseBuilder(applicationContext, PlacementDatabase::class.java, "placements.db")
            .addMigrations(PlacementDatabase.MIGRATION_1_2)
            .build()
    }

    private val placementRepository: HomePlacementRepository by lazy(LazyThreadSafetyMode.NONE) {
        RoomHomePlacementRepository(placementDatabase)
    }

    private val homeIconSnapshots by lazy(LazyThreadSafetyMode.NONE) { AppIconSnapshotCache(maxEntries = 64) }
    private val allAppsIconSnapshots by lazy(LazyThreadSafetyMode.NONE) { AppIconSnapshotCache(maxEntries = 64) }
    private val iconLookup by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsIconActivityLookup(applicationContext)
    }
    private val iconEncoder by lazy(LazyThreadSafetyMode.NONE) {
        BoundedLauncherAppIconPngEncoder()
    }
    private val homeIconRefresher by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsIconSnapshotRefresher(
            lookup = iconLookup,
            encoder = iconEncoder,
            cache = homeIconSnapshots,
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val allAppsIconRefresher by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsIconSnapshotRefresher(
            lookup = iconLookup,
            encoder = iconEncoder,
            cache = allAppsIconSnapshots,
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val decodedIcons by lazy(LazyThreadSafetyMode.NONE) {
        DecodedHomeIconCache(maxEntries = 64) { png ->
            BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
        }
    }
    private val decodedAllAppsIcons by lazy(LazyThreadSafetyMode.NONE) {
        DecodedHomeIconCache(maxEntries = 64) { png ->
            BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
        }
    }
    private val homeViewportStyle by lazy(LazyThreadSafetyMode.NONE) {
        HomeCanvasViewportStyle(
            horizontalPaddingPx = (8f * resources.displayMetrics.density).toInt(),
            columnGapPx = (8f * resources.displayMetrics.density).toInt(),
            rowGapPx = (8f * resources.displayMetrics.density).toInt(),
        )
    }
    private val homeRouteLoader: HomeRouteLoader by lazy(LazyThreadSafetyMode.NONE) {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val viewportWidth = metrics.widthPixels.coerceAtLeast(1)
        val viewportHeight = metrics.heightPixels.coerceAtLeast(1)
        val horizontalPaddingPx = (8f * density).toInt()
        val gapPx = (8f * density).toInt()
        val minimumCellPx = (60f * density).toInt().coerceAtLeast(1)
        val viewportRows = (viewportHeight / minimumCellPx).toLong().coerceAtLeast(1)
        HomeRouteLoader(
            homeId = PRODUCTION_HOME_ID,
            repository = placementRepository,
            catalogue = appCatalogue,
            iconRefresher = homeIconRefresher,
            encodedIcons = homeIconSnapshots,
            decodedIcons = decodedIcons,
            viewport = HomeRouteViewport(
                inputs = HomeViewportInputs(
                    viewportWidthPx = viewportWidth,
                    horizontalPaddingPx = horizontalPaddingPx,
                    columnGapPx = gapPx,
                    minimumCellWidthPx = minimumCellPx,
                    viewportRows = viewportRows,
                    visibleRows = GridRowWindow(0L, viewportRows),
                ),
                columnsBound = 5,
                rowsBound = 4096,
            ),
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val allAppsRouteLoader: AllAppsRouteLoader by lazy(LazyThreadSafetyMode.NONE) {
        AllAppsRouteLoader(
            homeId = PRODUCTION_HOME_ID,
            repository = placementRepository,
            catalogue = appCatalogue,
            iconRefresher = allAppsIconRefresher,
            encodedIcons = allAppsIconSnapshots,
            decodedIcons = decodedAllAppsIcons,
            columnsBound = 5,
            rowsBound = 4096,
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val addToHomeCoordinator: AddToHomeCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        AddToHomeCoordinator(
            homeId = PRODUCTION_HOME_ID,
            repository = placementRepository,
            columns = 5,
            rows = 4096,
            ioDispatcher = Dispatchers.IO,
        )
    }

    // Feed overlay gateway: application context only, never the activity. The
    // PackageManager probe lives in the platform boundary adapter behind the pure
    // FeedProviderResolver decision; the gateway owns binding, lifecycle and
    // availability state, and composables only collect the framework-free state.
    private val feedQuery: FeedServiceQuery by lazy(LazyThreadSafetyMode.NONE) {
        AndroidFeedServiceQuery(applicationContext)
    }

    private val feedGateway: FeedOverlayGateway by lazy(LazyThreadSafetyMode.NONE) {
        FeedOverlayGatewayImpl(
            applicationContext = applicationContext,
            resolver = FeedProviderResolver(feedQuery),
            ioDispatcher = Dispatchers.IO,
        )
    }

    internal var appLauncherForTest: AppLauncher? = null

    private val requestHomeRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        // Resolve all lazy app-side dependencies before composition starts. In particular, the
        // loader constructs the Room repository/database and platform icon wiring here, while its
        // actual reads and refresh work remain lifecycle-scoped and off the composition path.
        val routeLoader = homeRouteLoader
        val allAppsLoader = allAppsRouteLoader
        val coordinator = addToHomeCoordinator
        val routeViewportStyle = homeViewportStyle
        val productionAppLauncher = appLauncher
        val feed = feedGateway
        setContent {
            LauncherTheme {
            val homeState by routeLoader.state.collectAsState()
            val homeIcons by routeLoader.icons.collectAsState()
            val allAppsState by allAppsLoader.state.collectAsState()
            val allAppsIconsMap by allAppsLoader.icons.collectAsState()
            val feedAvailability by feed.state.collectAsState()
            val shortcutMenu by shortcutMenuCoordinator.menu.collectAsState()
            val pinnedByTile by shortcutMenuCoordinator.pinnedByTile.collectAsState()
            // B1: forward loaded tiles so the coordinator can project the
            // selection to its AppIdentity and clear menus of removed tiles.
            androidx.compose.runtime.LaunchedEffect(homeState) {
                shortcutMenuCoordinator.onHomeTilesChanged(
                    (homeState as? HomeRenderState.Loaded)?.tiles.orEmpty(),
                )
            }
            LauncherShell(
                homeState = homeState,
                homeIcons = homeIcons,
                allAppsState = allAppsState,
                allAppsIcons = allAppsIconsMap,
                onLaunchApp = { identity ->
                    (appLauncherForTest ?: productionAppLauncher).launch(identity)
                },
                onAddToHome = { identity ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = coordinator.add(identity)
                        if (result is AddToHomeResult.Added) {
                            routeLoader.reload()
                            allAppsLoader.refreshHomeMembership()
                        }
                    }
                },
                onRequestDefaultHome = ::requestDefaultHome,
                feedAvailability = feedAvailability,
                // S1: menu picks attach directly (non-suspend tryLock path); the
                // shell dismisses the menu around this call.
                onSelectFeedProvider = { feedGateway.attach(it) },
                // F3 feed driving (kept separate from the shortcut gateway
                // wiring): pager progress/visibility flow into the gateway's
                // never-throw best-effort notify verbs, which parallax and
                // open/close the bound overlay.
                onFeedProgress = { feedGateway.notifyOverlayProgress(it) },
                onFeedVisible = { feedGateway.notifyOverlayVisible(it) },
                // F3 window handshake: the hosted provider view registers this
                // activity's window with the bound overlay (provider renders
                // pixels only after windowAttached) and unregisters on release.
                // The gateway copies the window params and never retains the
                // activity; no new permission (manifest queries already cover
                // the provider packages, binding uses BIND_AUTO_CREATE).
                onFeedWindowAttached = { feedGateway.notifyWindowAttached(this@MainActivity) },
                onFeedWindowDetached = { feedGateway.notifyWindowDetached(false) },
                onMoveTile = { itemId, position ->
                    lifecycleScope.launch {
                        routeLoader.commitMove(itemId, position)
                    }
                },
                onResizeTile = { itemId, span ->
                    lifecycleScope.launch {
                        routeLoader.commitResize(itemId, span)
                    }
                },
                onRemoveTile = { itemId ->
                    lifecycleScope.launch {
                        val result = routeLoader.commitRemove(itemId)
                        if (result is RemoveResult.Removed) {
                            allAppsLoader.refreshHomeMembership()
                        }
                    }
                },
                // B1 shortcut menu wiring: selection-scoped rows from the
                // coordinator; row taps execute under the exact profile
                // off-main.
                surfaceEntries = shortcutMenu?.entries.orEmpty(),
                surfaceEntriesTileId = shortcutMenu?.tileId,
                onSelectedTileChange = shortcutMenuCoordinator::onSelectionChanged,
                onSurfaceExecute = { itemId, entryId ->
                    lifecycleScope.launch {
                        shortcutMenuCoordinator.execute(itemId, entryId)
                    }
                },
                // B2: pin toggles mutate only the selected tile binding and
                // never invoke shortcut execution.
                onSurfaceTogglePin = shortcutMenuCoordinator::togglePin,
                pinnedByTile = pinnedByTile,
                onPinnedShortcutClick = { itemId, entryId ->
                    lifecycleScope.launch {
                        shortcutMenuCoordinator.execute(itemId, entryId)
                    }
                },
                viewportStyle = routeViewportStyle,
            )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appCatalogue.start()
        shortcutGateway.start(lifecycleScope)
        shortcutMenuCoordinator.start(lifecycleScope)
        homeRouteLoader.start(lifecycleScope)
        allAppsRouteLoader.start(lifecycleScope)
        feedGateway.start(lifecycleScope)
    }

    override fun onStop() {
        feedGateway.stop()
        allAppsRouteLoader.stop()
        homeRouteLoader.stop()
        shortcutMenuCoordinator.stop()
        shortcutGateway.stop()
        appCatalogue.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    private fun requestDefaultHome() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            requestHomeRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }
}

private fun Activity.enterImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    WindowCompat.getInsetsController(
        window,
        window.decorView,
    ).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        hide(WindowInsetsCompat.Type.systemBars())
    }
}
