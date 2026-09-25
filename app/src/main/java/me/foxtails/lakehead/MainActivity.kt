package me.foxtails.lakehead

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
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.RoomHomePlacementRepository
import me.foxtails.lakehead.feature.home.HomeCanvasViewportStyle
import me.foxtails.lakehead.feature.home.HomeViewportInputs
import me.foxtails.lakehead.platform.launcherapps.AppLauncher
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshotCache
import me.foxtails.lakehead.platform.launcherapps.BoundedLauncherAppIconPngEncoder
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconActivityLookup
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconSnapshotRefresher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsActivityLauncher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue

internal val PRODUCTION_HOME_ID = HomeId("home-main")

class MainActivity : ComponentActivity() {
    internal val appCatalogue: LauncherAppsAppCatalogue by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsAppCatalogue(applicationContext, lifecycleScope)
    }

    private val appLauncher: AppLauncher by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsActivityLauncher(applicationContext)
    }

    private val placementDatabase: PlacementDatabase by lazy(LazyThreadSafetyMode.NONE) {
        Room.databaseBuilder(applicationContext, PlacementDatabase::class.java, "placements.db").build()
    }

    private val placementRepository: HomePlacementRepository by lazy(LazyThreadSafetyMode.NONE) {
        RoomHomePlacementRepository(placementDatabase)
    }

    private val iconSnapshots by lazy(LazyThreadSafetyMode.NONE) { AppIconSnapshotCache(maxEntries = 64) }
    private val iconRefresher by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsIconSnapshotRefresher(
            lookup = LauncherAppsIconActivityLookup(applicationContext),
            encoder = BoundedLauncherAppIconPngEncoder(),
            cache = iconSnapshots,
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
            horizontalPaddingPx = (16f * resources.displayMetrics.density).toInt(),
            columnGapPx = (8f * resources.displayMetrics.density).toInt(),
            rowGapPx = (8f * resources.displayMetrics.density).toInt(),
        )
    }
    private val homeRouteLoader: HomeRouteLoader by lazy(LazyThreadSafetyMode.NONE) {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val viewportWidth = metrics.widthPixels.coerceAtLeast(1)
        val viewportHeight = metrics.heightPixels.coerceAtLeast(1)
        val horizontalPaddingPx = (16f * density).toInt()
        val gapPx = (8f * density).toInt()
        val minimumCellPx = (48f * density).toInt().coerceAtLeast(1)
        val viewportRows = (viewportHeight / minimumCellPx).toLong().coerceAtLeast(1)
        HomeRouteLoader(
            homeId = PRODUCTION_HOME_ID,
            repository = placementRepository,
            catalogue = appCatalogue,
            iconRefresher = iconRefresher,
            encodedIcons = iconSnapshots,
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
                columnsBound = 6,
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
            iconRefresher = iconRefresher,
            encodedIcons = iconSnapshots,
            decodedIcons = decodedAllAppsIcons,
            columnsBound = 6,
            rowsBound = 4096,
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val addToHomeCoordinator: AddToHomeCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        AddToHomeCoordinator(
            homeId = PRODUCTION_HOME_ID,
            repository = placementRepository,
            columns = 6,
            rows = 4096,
            ioDispatcher = Dispatchers.IO,
        )
    }

    internal var appLauncherForTest: AppLauncher? = null

    private val requestHomeRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Resolve all lazy app-side dependencies before composition starts. In particular, the
        // loader constructs the Room repository/database and platform icon wiring here, while its
        // actual reads and refresh work remain lifecycle-scoped and off the composition path.
        val routeLoader = homeRouteLoader
        val allAppsLoader = allAppsRouteLoader
        val coordinator = addToHomeCoordinator
        val routeViewportStyle = homeViewportStyle
        val productionAppLauncher = appLauncher
        setContent {
            val homeState by routeLoader.state.collectAsState()
            val homeIcons by routeLoader.icons.collectAsState()
            val allAppsState by allAppsLoader.state.collectAsState()
            val allAppsIconsMap by allAppsLoader.icons.collectAsState()
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
                viewportStyle = routeViewportStyle,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        appCatalogue.start()
        homeRouteLoader.start(lifecycleScope)
        allAppsRouteLoader.start(lifecycleScope)
    }

    override fun onStop() {
        allAppsRouteLoader.stop()
        homeRouteLoader.stop()
        appCatalogue.stop()
        super.onStop()
    }

    private fun requestDefaultHome() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            requestHomeRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }
}