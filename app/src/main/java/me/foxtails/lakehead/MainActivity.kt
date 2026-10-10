package me.foxtails.lakehead

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.app.role.RoleManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.repository.FolderRepository
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.RemoveResult
import me.foxtails.lakehead.data.repository.RoomFolderRepository
import me.foxtails.lakehead.data.repository.RoomHomePlacementRepository
import me.foxtails.lakehead.feature.home.HomeCanvasViewportStyle
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.feature.home.HomeViewportInputs
import me.foxtails.lakehead.platform.feed.AndroidFeedServiceQuery
import me.foxtails.lakehead.platform.feed.FeedOverlayGateway
import me.foxtails.lakehead.platform.feed.FeedOverlayGatewayImpl
import me.foxtails.lakehead.platform.feed.FeedProviderResolver
import me.foxtails.lakehead.platform.feed.FeedServiceQuery
import me.foxtails.lakehead.platform.feed.startFeedDiagnosticsLogging
import me.foxtails.lakehead.platform.launcherapps.AppLauncher
import me.foxtails.lakehead.platform.media.MediaAccess
import me.foxtails.lakehead.platform.media.MediaAccessQuery
import me.foxtails.lakehead.platform.media.MediaStoreGateway
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshotCache
import me.foxtails.lakehead.platform.launcherapps.BoundedLauncherAppIconPngEncoder
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconActivityLookup
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconSnapshotRefresher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsActivityLauncher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue
import me.foxtails.lakehead.platform.notifications.NotificationAccess
import me.foxtails.lakehead.platform.shortcuts.LauncherAppsShortcutGateway
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway
import me.foxtails.lakehead.platform.widgets.AppWidgetHosting
import me.foxtails.lakehead.platform.widgets.AppWidgetProviderCatalogue
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.WidgetProviderEntry
import android.content.Intent

internal val PRODUCTION_HOME_ID = HomeId("home-main")

private const val NO_PENDING_WIDGET = -1
private const val CONFIGURE_REQUEST_CODE = 0x57
private const val STATE_WIDGET_ID = "pendingWidgetId"
private const val STATE_WIDGET_PROVIDER = "pendingWidgetProvider"

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
            .addMigrations(PlacementDatabase.MIGRATION_1_2, PlacementDatabase.MIGRATION_2_3,
                PlacementDatabase.MIGRATION_3_4, PlacementDatabase.MIGRATION_4_5)
            .build()
    }

    private val placementRepository: HomePlacementRepository by lazy(LazyThreadSafetyMode.NONE) {
        RoomHomePlacementRepository(placementDatabase)
    }

    private val folderRepository: FolderRepository by lazy(LazyThreadSafetyMode.NONE) {
        RoomFolderRepository(placementDatabase)
    }

    private val folderCoordinator: FolderCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        FolderCoordinator(
            homeId = PRODUCTION_HOME_ID,
            folders = folderRepository,
            columns = 5,
            metadata = { homeRouteLoader.metadataSnapshot() },
            onHomeChanged = {
                homeRouteLoader.reload()
                allAppsRouteLoader.refreshHomeMembership()
            },
            releaseWidget = { widgetHosting.deleteId(it) },
            ioDispatcher = Dispatchers.IO,
        )
    }

    private val iconSnapshots by lazy(LazyThreadSafetyMode.NONE) { AppIconSnapshotCache() }
    private val iconLookup by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsIconActivityLookup(applicationContext)
    }
    private val iconEncoder by lazy(LazyThreadSafetyMode.NONE) {
        BoundedLauncherAppIconPngEncoder()
    }
    private val iconRefresher by lazy(LazyThreadSafetyMode.NONE) {
        LauncherAppsIconSnapshotRefresher(
            lookup = iconLookup,
            encoder = iconEncoder,
            cache = iconSnapshots,
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val decodedIcons by lazy(LazyThreadSafetyMode.NONE) {
        DecodedIconCache(::decodeAlpha8Icon)
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
            folders = folderRepository,
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
                ),
                columnsBound = 5,
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
            decodedIcons = decodedIcons,
            columnsBound = 5,
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val addToHomeCoordinator: AddToHomeCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        AddToHomeCoordinator(
            homeId = PRODUCTION_HOME_ID,
            repository = placementRepository,
            columns = 5,
            ioDispatcher = Dispatchers.IO,
        )
    }

    private val homeLayoutExportImport: HomeLayoutExportImport by lazy(LazyThreadSafetyMode.NONE) {
        HomeLayoutExportImport(
            homeId = PRODUCTION_HOME_ID,
            columns = 5,
            repository = placementRepository,
            ioDispatcher = Dispatchers.IO,
            // Decision 7: import restores an album binding only when its
            // bucket is on this device (denied permission reads as none).
            loadDeviceAlbumBuckets = { mediaGateway.albums().map { it.album.bucketId }.toSet() },
        )
    }

    // SAF launchers: registered at activity construction time (before onCreate)
    // so the system can restore them across configuration changes. Each launcher
    // holds a mutable reference to the pending JSON/filename set during the
    // export callback; the result handler reads it and writes/reads via ContentResolver.

    /** Pending export state: set by the export callback, consumed by the SAF result. */
    @Volatile
    private var pendingExportJson: org.json.JSONObject? = null

    private val exportLayoutLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExportJson ?: return@registerForActivityResult
        pendingExportJson = null
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(json.toString(2))
            }
        }
    }

    private val importLayoutLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            org.json.JSONObject(reader.readText())
        } ?: return@registerForActivityResult

        val routeLoader = homeRouteLoader
        val allAppsLoader = allAppsRouteLoader
        lifecycleScope.launch(Dispatchers.IO) {
            val result = homeLayoutExportImport.importLayout(json)
            if (result is ImportResult.Applied) {
                routeLoader.reload()
                allAppsLoader.refreshHomeMembership()
            }
        }
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

    private val widgetHosting by lazy(LazyThreadSafetyMode.NONE) { AppWidgetHosting(applicationContext) }
    // Album slideshow (slice 6): the MediaStore gateway owns album/photo/
    // thumbnail queries and change observation over the personal profile; the
    // coordinator owns the offer prompt, permission gating, binding
    // persistence and the rotation clock. Permission is requested only after
    // the user accepts the prompt on a gallery tile.
    private val mediaGateway: MediaStoreGateway by lazy(LazyThreadSafetyMode.NONE) {
        MediaStoreGateway(applicationContext)
    }
    private val albumCoordinator: AlbumTileCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        AlbumTileCoordinator(
            repository = placementRepository,
            library = MediaStoreAlbumLibrary(mediaGateway),
            accessNow = { MediaAccessQuery.accessNow(applicationContext) },
            onRequestPermission = {
                albumPermissionLauncher.launch(
                    MediaAccessQuery.permissionsFor(Build.VERSION.SDK_INT).toTypedArray(),
                )
            },
        )
    }
    private val albumPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val access = MediaAccessQuery.accessFrom(
            granted.filterValues { it }.keys,
            Build.VERSION.SDK_INT,
        )
        albumCoordinator.onPermissionResult(access)
    }
    private val widgetCatalogue by lazy(LazyThreadSafetyMode.NONE) {
        AppWidgetProviderCatalogue(applicationContext)
    }
    private val addWidgetCoordinator: AddWidgetCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        AddWidgetCoordinator(
            homeId = PRODUCTION_HOME_ID,
            repository = placementRepository,
            host = object : WidgetHostPort {
                override fun allocateId() = widgetHosting.allocateId()
                override fun deleteId(appWidgetId: Int) = widgetHosting.deleteId(appWidgetId)
                override fun bindIfAllowed(appWidgetId: Int, provider: WidgetProviderEntry) =
                    widgetHosting.bindIfAllowed(appWidgetId, provider.provider)
                override fun needsConfigure(appWidgetId: Int) = widgetHosting.needsConfigure(appWidgetId)
            },
            columns = 5,
            ioDispatcher = Dispatchers.IO,
        )
    }

    // The add flow spans activity results and may span process death, so the pending
    // id and provider are saved in instance state; nothing else about it persists.
    private var pendingWidgetId: Int = NO_PENDING_WIDGET
    private var pendingWidgetProvider: AppIdentity? = null

    private val bindConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> finishPendingWidget { id, provider -> addWidgetCoordinator.onBindResult(id, provider, result.resultCode == RESULT_OK) } }


    internal var appLauncherForTest: AppLauncher? = null

    private val requestHomeRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val provider = pendingWidgetProvider
        if (pendingWidgetId != NO_PENDING_WIDGET && provider != null) {
            outState.putInt(STATE_WIDGET_ID, pendingWidgetId)
            outState.putStringArray(
                STATE_WIDGET_PROVIDER,
                arrayOf(provider.profileId.value, provider.component.packageName, provider.component.className),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArray(STATE_WIDGET_PROVIDER)?.let { (profile, pkg, cls) ->
            pendingWidgetId = savedInstanceState.getInt(STATE_WIDGET_ID, NO_PENDING_WIDGET)
            pendingWidgetProvider = AppIdentity(ProfileId(profile), ComponentIdentity(pkg, cls))
        }
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
        val folders = folderCoordinator
        // Setprop-gated (debug.protolaunch.feed_diag); a no-op unless enabled.
        startFeedDiagnosticsLogging(feed, lifecycleScope)
        setContent {
            LauncherTheme {
            val homeState by routeLoader.state.collectAsState()
            val homeIcons by routeLoader.icons.collectAsState()
            val allAppsState by allAppsLoader.state.collectAsState()
            val allAppsIconsMap by allAppsLoader.icons.collectAsState()
            val feedAvailability by feed.state.collectAsState()
            val feedDiagnostic by feed.diagnostic.collectAsState()
            val widgetProviders by widgetCatalogue.providers.collectAsState()
            val widgetContent = androidx.compose.runtime.remember { hostedWidgetContent(widgetHosting) }
            val shortcutMenu by shortcutMenuCoordinator.menu.collectAsState()
            val pinnedByTile by shortcutMenuCoordinator.pinnedByTile.collectAsState()
            // B1: forward loaded tiles so the coordinator can project the
            // selection to its AppIdentity and clear menus of removed tiles.
            androidx.compose.runtime.LaunchedEffect(homeState) {
                shortcutMenuCoordinator.onHomeTilesChanged(
                    (homeState as? HomeRenderState.Loaded)?.tiles.orEmpty(),
                )
                albumCoordinator.onHomeTilesChanged(
                    (homeState as? HomeRenderState.Loaded)?.tiles.orEmpty(),
                )
            }
            val openFolder by folders.openFolder.collectAsState()
            val openFolderMembers by folders.members.collectAsState()
            val folderAddSource by folders.addSource.collectAsState()
            val folderRemovePrompt by folders.removePrompt.collectAsState()
            // A folder that disappears from Home (removed, dissolved, imported over) closes its overlay.
            androidx.compose.runtime.LaunchedEffect(homeState, openFolder) {
                val loaded = homeState as? HomeRenderState.Loaded
                val open = openFolder
                if (loaded != null && open != null && loaded.tiles.none { it.itemId == open }) folders.close()
            }
            val notificationsByTile = rememberNotificationsByTile(homeState)
            val albumPrompt by albumCoordinator.prompt.collectAsState()
            val albumPickerAlbums by albumCoordinator.pickerAlbums.collectAsState()
            val albumPhotos by albumCoordinator.albumPhotoByTile.collectAsState()
            LauncherShell(
                homeState = homeState,
                notificationsByTile = notificationsByTile,
                onOpenNotificationAccess = {
                    startActivity(NotificationAccess.settingsIntent())
                },
                homeIcons = homeIcons,
                allAppsState = allAppsState,
                allAppsIcons = allAppsIconsMap,
                onLaunchApp = { identity ->
                    (appLauncherForTest ?: productionAppLauncher).launch(identity)
                },
                // Home-tile taps advance a bound slideshow one photo (the
                // caller still launches); All Apps taps keep [onLaunchApp].
                onHomeLaunchApp = { identity ->
                    val tileId = (homeState as? HomeRenderState.Loaded)?.tiles
                        ?.firstOrNull { it.appIdentity == identity }?.itemId
                    if (tileId != null) albumCoordinator.onHomeTileTapped(tileId)
                    (appLauncherForTest ?: productionAppLauncher).launch(identity)
                },
                onHomeVisibleChange = albumCoordinator::setHomeVisible,
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
                feedDiagnostic = feedDiagnostic,
                // S1: menu picks attach directly (non-suspend tryLock path); the
                // shell dismisses the menu around this call.
                onSelectFeedProvider = { feedGateway.attach(it) },
                // S1: export/import layout via SAF. The menu stays open;
                // the launcher opens a system picker on top.
                onExportHomeLayout = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val (json, filename) = homeLayoutExportImport.exportLayout()
                        pendingExportJson = json
                        // ActivityResultLauncher#launch must be called on the main thread.
                        withContext(Dispatchers.Main) {
                            exportLayoutLauncher.launch(filename)
                        }
                    }
                },
                onImportHomeLayout = {
                    importLayoutLauncher.launch(arrayOf("application/json"))
                },
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
                    lifecycleScope.launch { routeLoader.commitMove(itemId, position) }
                },
                onResizeTile = { itemId, span ->
                    val tile = (homeState as? HomeRenderState.Loaded)?.tiles
                        ?.firstOrNull { it.itemId == itemId }
                    val tileIdentity = tile?.appIdentity
                    if (tile != null && tileIdentity != null) {
                        albumCoordinator.onResized(
                            tile,
                            tile.span,
                            span,
                            isPersonalProfile(tileIdentity.profileId),
                        )
                    }
                    lifecycleScope.launch {
                        routeLoader.commitResize(itemId, span)
                    }
                },
                onRemoveTile = { itemId ->
                    val removed = (homeState as? HomeRenderState.Loaded)?.tiles
                        ?.firstOrNull { it.itemId == itemId }
                    val widgetId = removed?.appWidgetId
                    if (removed?.isFolder == true) {
                        // A folder asks first and removes through the folder repository,
                        // which also reports the member widgets to release.
                        lifecycleScope.launch { folders.requestRemoveFolder(itemId) }
                        return@LauncherShell
                    }
                    lifecycleScope.launch {
                        val result = routeLoader.commitRemove(itemId)
                        if (result is RemoveResult.Removed) {
                            widgetId?.let(widgetHosting::deleteId)
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
                // Album rows (slice 7) route by id: Change reopens the
                // picker through the coordinator's pending-tile flow
                // (re-requesting permission when denied), Remove clears the
                // binding and refreshes the open menu so its rows drop.
                // Anything else is a shortcut launch under its profile.
                onSurfaceExecute = { itemId, entryId ->
                    lifecycleScope.launch {
                        when (entryId) {
                            ALBUM_MENU_CHANGE_ID -> albumCoordinator.openAlbumPicker(itemId)
                            ALBUM_MENU_REMOVE_ID -> {
                                albumCoordinator.removeAlbumBinding(itemId)
                                shortcutMenuCoordinator.refreshSelection()
                            }
                            else -> shortcutMenuCoordinator.execute(itemId, entryId)
                        }
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
                // Album slideshow (slice 6): prompt/picker state and photo
                // projection come from the coordinator; verbs report back.
                albumPhotoByTile = albumPhotos,
                albumPromptLabel = albumPrompt?.appLabel,
                onAlbumPromptAccept = albumCoordinator::onPromptAccept,
                onAlbumPromptDecline = albumCoordinator::onPromptDecline,
                onAlbumPromptDismiss = albumCoordinator::onPromptDismiss,
                albumPickerAlbums = albumPickerAlbums,
                onAlbumPick = albumCoordinator::onAlbumPick,
                onAlbumPickerDismiss = albumCoordinator::onPickerDismiss,
                folders = FolderShell(
                    openMembers = openFolder?.let { openFolderMembers },
                    chooserFolders = if (folderAddSource != null) {
                        (homeState as? HomeRenderState.Loaded)?.tiles.orEmpty().filter { it.isFolder }
                    } else {
                        null
                    },
                    removePromptOpen = folderRemovePrompt != null,
                    onOpen = { id -> lifecycleScope.launch { folders.open(id) } },
                    onClose = folders::close,
                    onLaunchMember = { identity ->
                        folders.close()
                        (appLauncherForTest ?: productionAppLauncher).launch(identity)
                    },
                    onMemberToHome = { id -> lifecycleScope.launch { folders.moveToHome(id) } },
                    onMemberRemove = { id -> lifecycleScope.launch { folders.removeMember(id) } },
                    onNewFolder = { id -> lifecycleScope.launch { folders.createFolder(id) } },
                    onRequestAddToFolder = folders::requestAddToFolder,
                    onPickFolder = { id -> lifecycleScope.launch { folders.pickFolder(id) } },
                    onChooserDismiss = folders::dismissChooser,
                    onRemoveConfirm = { lifecycleScope.launch { folders.confirmRemoveFolder() } },
                    onRemoveDismiss = folders::dismissRemovePrompt,
                ),
                viewportStyle = routeViewportStyle,
                widgetContent = widgetContent,
                widgetProviders = widgetProviders,
                onAddWidget = { entry ->
                    lifecycleScope.launch {
                        handleAddWidgetStep(addWidgetCoordinator.begin(entry), entry)
                    }
                },
            )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appCatalogue.start()
        shortcutGateway.start(lifecycleScope)
        shortcutMenuCoordinator.start(lifecycleScope)
        mediaGateway.start()
        albumCoordinator.start(lifecycleScope)
        homeRouteLoader.start(lifecycleScope)
        allAppsRouteLoader.start(lifecycleScope)
        feedGateway.start(lifecycleScope)
        widgetHosting.startListening()
        widgetCatalogue.start(lifecycleScope)
    }

    override fun onStop() {
        widgetCatalogue.stop()
        widgetHosting.stopListening()
        albumCoordinator.stop()
        mediaGateway.stop()
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

    /** Launches whatever UI the step needs, or reloads Home when the widget was placed. */
    private suspend fun handleAddWidgetStep(step: AddWidgetStep, provider: WidgetProviderEntry) {
        when (step) {
            is AddWidgetStep.NeedsBindConsent -> {
                pendingWidgetId = step.appWidgetId
                pendingWidgetProvider = provider.provider
                bindConsentLauncher.launch(widgetHosting.bindConsentIntent(step.appWidgetId, provider.provider))
            }
            is AddWidgetStep.NeedsConfigure -> {
                pendingWidgetId = step.appWidgetId
                pendingWidgetProvider = provider.provider
                widgetHosting.startConfigure(this, step.appWidgetId, CONFIGURE_REQUEST_CODE)
            }
            AddWidgetStep.Placed -> {
                clearPendingWidget()
                homeRouteLoader.reload()
            }
            is AddWidgetStep.Failed -> clearPendingWidget()
        }
    }

    private fun clearPendingWidget() {
        pendingWidgetId = NO_PENDING_WIDGET
        pendingWidgetProvider = null
    }

    /** Resumes the pending add flow with an activity result, re-resolving the provider by identity. */
    private fun finishPendingWidget(step: suspend (Int, WidgetProviderEntry) -> AddWidgetStep) {
        val id = pendingWidgetId
        val identity = pendingWidgetProvider
        if (id == NO_PENDING_WIDGET || identity == null) return
        lifecycleScope.launch {
            widgetCatalogue.refresh()
            val provider = widgetCatalogue.providers.value.firstOrNull { it.provider == identity }
            if (provider == null) {
                addWidgetCoordinator.cancel(id)
                clearPendingWidget()
            } else {
                handleAddWidgetStep(step(id, provider), provider)
            }
        }
    }

    @Deprecated("Configure activities return through the host request code")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONFIGURE_REQUEST_CODE) {
            finishPendingWidget { id, provider ->
                addWidgetCoordinator.onConfigureResult(id, provider, resultCode == RESULT_OK)
            }
        }
    }

    private fun requestDefaultHome() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            requestHomeRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    /**
     * Whether [profileId] names the personal (primary-user) profile, read
     * from the serial of this process's user handle. The album offer and the
     * `MediaStore` reads cover the personal profile only.
     */
    private fun isPersonalProfile(profileId: ProfileId): Boolean {
        val userManager = getSystemService(Context.USER_SERVICE) as? android.os.UserManager
            ?: return false
        val serial = runCatching {
            userManager.getSerialNumberForUser(android.os.Process.myUserHandle())
        }.getOrNull() ?: return false
        return profileId.value == serial.toString()
    }
}

@Suppress("DEPRECATION")
private fun Activity.enterImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // Genuine edge-to-edge: fully transparent bar backgrounds so no opaque
    // system-bar color ever simulates the edge (the shell background supplies
    // continuity). Bars stay hidden via the controller below; transparency only
    // governs transient reveals. The setters are deprecated on API 35+ (there is
    // no insets-controller replacement for the background itself), hence scoped.
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT

    WindowCompat.getInsetsController(
        window,
        window.decorView,
    ).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        hide(WindowInsetsCompat.Type.systemBars())
    }
}
