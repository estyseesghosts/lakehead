package me.foxtails.lakehead

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.model.AlbumPhoto
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.MediaAlbum
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.shouldOfferAlbum
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.feature.home.HomeRenderTile
import me.foxtails.lakehead.platform.media.MediaAccess
import me.foxtails.lakehead.platform.media.MediaStoreGateway

/**
 * Length of one slideshow clock slot. The shown photo advances one position
 * per slot plus one per tap: `(slot + tapCount) mod albumSize`.
 */
internal const val ALBUM_SLIDE_SLOT_MILLIS: Long = 15 * 60 * 1000L

/** Downsampled thumbnail edge for album tiles: one bitmap per bound tile. */
internal const val ALBUM_THUMBNAIL_PX: Int = 512

/**
 * Slideshow photo index for [clockMillis] and an in-memory [tapCount].
 *
 * The slot is `floor(clockMillis / 15 min)` so the timer recomputes from the
 * clock on every resume instead of drifting. Pure and unit-testable: callers
 * pass the fake clock value directly.
 */
internal fun albumPhotoIndex(clockMillis: Long, tapCount: Long, albumSize: Int): Int {
    require(albumSize > 0) { "albumSize must be positive" }
    val slot = Math.floorDiv(clockMillis, ALBUM_SLIDE_SLOT_MILLIS)
    return Math.floorMod(slot + tapCount, albumSize.toLong()).toInt()
}

/**
 * Media port behind the album coordinator: album list, newest-first photos
 * per bucket, one downsampled image per shown photo, and a change counter.
 * Production reads the personal profile through [MediaStoreGateway]; tests
 * substitute an in-memory fake. Thumbnails resolve off the caller's thread.
 */
internal interface AlbumMediaLibrary {
    val changes: StateFlow<Long>
    suspend fun albums(): List<MediaAlbum>
    suspend fun photos(bucketId: String): List<AlbumPhoto>
    suspend fun thumbnail(contentUri: String, sizePx: Int): ImageBitmap?
}

/** Production [AlbumMediaLibrary] over [MediaStoreGateway]. */
internal class MediaStoreAlbumLibrary(
    private val gateway: MediaStoreGateway,
) : AlbumMediaLibrary {
    override val changes: StateFlow<Long> = gateway.changes
    override suspend fun albums(): List<MediaAlbum> = gateway.albums()
    override suspend fun photos(bucketId: String): List<AlbumPhoto> = gateway.photos(bucketId)
    override suspend fun thumbnail(contentUri: String, sizePx: Int): ImageBitmap? =
        gateway.thumbnail(contentUri, sizePx)?.asImageBitmap()
}

/** A pending album offer: the resized tile and the label the prompt shows. */
internal data class AlbumOfferPrompt(
    val tileId: ItemId,
    val appLabel: String,
)

/**
 * App-side owner of gallery-tile album slideshows (slice 6).
 *
 * Flow: [onResized] applies [shouldOfferAlbum] to a committed resize and
 * raises [prompt]; [onPromptAccept] requests the photo permission through
 * [onRequestPermission] when [accessNow] is denied, otherwise opens the album
 * [pickerAlbums]; [onPermissionResult] continues a pending accept;
 * [onAlbumPick] persists the binding and projects the newest photo.
 * Declining remembers nothing, so the next threshold crossing offers again.
 *
 * Rotation: [selectedPhotoByTile] names the selected content URI per bound
 * tile from [albumPhotoIndex] (clock slot plus in-memory tap count;
 * [onHomeTileTapped] advances one photo per tap). [albumPhotoByTile] carries
 * the decoded image for the shell; a missing/unreadable thumbnail renders
 * the normal icon while the binding is kept. The ticker runs only while
 * [setHomeVisible] is true and recomputes from the clock on resume; no
 * alarms, no background work. Tap counts live only in memory, so process
 * death resets them.
 *
 * Binding persistence stays with [HomePlacementRepository] off the caller
 * thread. Call [start] before tiles arrive (production: `onStart`) and
 * [stop] when leaving (production: `onStop`).
 */
internal class AlbumTileCoordinator(
    private val repository: HomePlacementRepository,
    private val library: AlbumMediaLibrary,
    private val accessNow: () -> MediaAccess = { MediaAccess.DENIED },
    private val onRequestPermission: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tickIntervalMs: Long = 60_000L,
) {
    private val mutablePrompt = MutableStateFlow<AlbumOfferPrompt?>(null)

    /** Pending offer; null means no prompt. */
    val prompt: StateFlow<AlbumOfferPrompt?> = mutablePrompt.asStateFlow()

    private val mutablePickerAlbums = MutableStateFlow<List<MediaAlbum>?>(null)

    /** Album list for the picker; null means the picker is closed. */
    val pickerAlbums: StateFlow<List<MediaAlbum>?> = mutablePickerAlbums.asStateFlow()

    private val mutablePhotos = MutableStateFlow<Map<ItemId, ImageBitmap>>(emptyMap())

    /** Decoded slideshow image per bound tile; missing entries render the icon. */
    val albumPhotoByTile: StateFlow<Map<ItemId, ImageBitmap>> = mutablePhotos.asStateFlow()

    private val mutableSelected = MutableStateFlow<Map<ItemId, String>>(emptyMap())

    /**
     * Selected photo content URI per bound tile: the rotation decision
     * (clock slot plus tap count), independent of whether its thumbnail
     * decoded. Missing entries render the normal icon.
     */
    val selectedPhotoByTile: StateFlow<Map<ItemId, String>> = mutableSelected.asStateFlow()

    private var scope: CoroutineScope? = null
    private var changesJob: Job? = null
    private var tickerJob: Job? = null
    private var refreshJob: Job? = null
    private var generation = 0L
    private val refreshMutex = Mutex()

    private var tiles: List<HomeRenderTile> = emptyList()
    private var bindings = emptyMap<ItemId, TileAlbumBinding>()
    private var photosByBucket = emptyMap<String, List<AlbumPhoto>>()
    private var tapCounts = mutableMapOf<ItemId, Long>()
    private var pendingTile: ItemId? = null
    private var homeVisible = false

    @Synchronized
    fun start(parentScope: CoroutineScope) {
        if (scope != null) return
        val routeScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
        scope = routeScope
        changesJob = routeScope.launch {
            library.changes.drop(1).collect {
                refreshMutex.withLock {
                    refreshPhotoLists()
                    project()
                }
            }
        }
        ensureTicker()
        launchRefresh()
    }

    @Synchronized
    fun stop() {
        generation++
        refreshJob?.cancel()
        tickerJob?.cancel()
        changesJob?.cancel()
        scope?.cancel()
        scope = null
        changesJob = null
        tickerJob = null
        refreshJob = null
    }

    /**
     * Reports the latest loaded home tiles.
     *
     * Reloads album bindings for the current tiles off the caller thread,
     * then refreshes photo lists and projects. Tiles that leave drop their
     * photo, tap count, pending prompt and pending pick (a removed tile never
     * keeps a dialog). Safe before [start]: the tile set is remembered and
     * the load runs on [start] and on the next update.
     */
    fun onHomeTilesChanged(currentTiles: List<HomeRenderTile>) {
        val knownIds = currentTiles.map { it.itemId }.toSet()
        synchronized(this) {
            tiles = currentTiles.toList()
            tapCounts.keys.retainAll(knownIds)
            if (mutablePrompt.value?.tileId !in knownIds) mutablePrompt.value = null
            if (pendingTile !in knownIds) {
                pendingTile = null
                mutablePickerAlbums.value = null
            }
        }
        launchRefresh()
    }

    /**
     * Reports a committed resize of [tile] from [oldSpan] to [newSpan].
     *
     * Loads the tile's binding off the caller thread and raises [prompt]
     * when [shouldOfferAlbum] holds for its package, widget state,
     * [isPersonalProfile] and span crossing. The resize itself is already
     * committed by the caller either way.
     */
    fun onResized(
        tile: HomeRenderTile,
        oldSpan: LogicalSpan,
        newSpan: LogicalSpan,
        isPersonalProfile: Boolean,
    ) {
        val routeScope = synchronized(this) { scope } ?: return
        routeScope.launch {
            val binding = withContext(ioDispatcher) { repository.loadAlbumBinding(tile.itemId) }
            if (!shouldOfferAlbum(
                    tile.appIdentity.component.packageName,
                    tile.isWidget,
                    isPersonalProfile,
                    binding != null,
                    oldSpan,
                    newSpan,
                )
            ) {
                return@launch
            }
            val label = tile.label?.takeIf { it.isNotBlank() }
                ?: tile.appIdentity.component.packageName
            mutablePrompt.value = AlbumOfferPrompt(tile.itemId, label)
        }
    }

    /**
     * Accepts the pending offer: with photo access already granted the album
     * list opens directly, otherwise the permission request fires first and
     * [onPermissionResult] continues. No prompt means no-op.
     */
    fun onPromptAccept() {
        val offer = mutablePrompt.value ?: return
        mutablePrompt.value = null
        synchronized(this) { pendingTile = offer.tileId }
        if (accessNow() != MediaAccess.DENIED) {
            openPicker()
        } else {
            onRequestPermission()
        }
    }

    /** Continues a pending accept after the permission result. */
    fun onPermissionResult(access: MediaAccess) {
        if (pendingTile == null) return
        if (access == MediaAccess.DENIED) {
            synchronized(this) { pendingTile = null }
            return
        }
        openPicker()
    }

    /** Declines the pending offer; the next crossing offers again. */
    fun onPromptDecline() {
        mutablePrompt.value = null
    }

    /** Cancels the pending offer (scrim/Back); the next crossing offers again. */
    fun onPromptDismiss() {
        mutablePrompt.value = null
    }

    /**
     * Binds the picked album to the pending tile, persists it, and projects
     * its newest photo. A tile removed while the picker was open saves
     * nothing and just closes.
     */
    fun onAlbumPick(album: AlbumRef) {
        val tileId = synchronized(this) {
            val pending = pendingTile ?: return
            pendingTile = null
            mutablePickerAlbums.value = null
            pending
        }
        val routeScope = synchronized(this) { scope } ?: return
        routeScope.launch {
            refreshMutex.withLock {
                val live = synchronized(this@AlbumTileCoordinator) { tiles }
                if (live.none { it.itemId == tileId }) return@launch
                withContext(ioDispatcher) { repository.saveAlbumBinding(TileAlbumBinding(tileId, album)) }
                synchronized(this@AlbumTileCoordinator) {
                    bindings = bindings + (tileId to TileAlbumBinding(tileId, album))
                    tapCounts.remove(tileId)
                }
                val fresh = withContext(ioDispatcher) { library.photos(album.bucketId) }
                synchronized(this@AlbumTileCoordinator) {
                    photosByBucket = photosByBucket + (album.bucketId to fresh)
                }
                project()
            }
        }
    }

    /** Cancels the open picker without binding. */
    fun onPickerDismiss() {
        synchronized(this) { pendingTile = null }
        mutablePickerAlbums.value = null
    }

    /**
     * Advances a bound tile one photo per tap. Taps on tiles without a
     * binding are a no-op for the slideshow (the caller still launches).
     */
    fun onHomeTileTapped(tileId: ItemId) {
        val routeScope = synchronized(this) { scope } ?: return
        synchronized(this) {
            if (!bindings.containsKey(tileId)) return
            tapCounts[tileId] = (tapCounts[tileId] ?: 0L) + 1L
        }
        routeScope.launch {
            refreshMutex.withLock { project() }
        }
    }

    /**
     * Reports whether Home is the settled pager page. The ticker runs only
     * while visible; regaining visibility refreshes photo lists (covering
     * changes the observer may have missed) and recomputes from the clock.
     */
    fun setHomeVisible(visible: Boolean) {
        synchronized(this) { homeVisible = visible }
        ensureTicker()
        if (!visible) return
        val routeScope = synchronized(this) { scope } ?: return
        routeScope.launch {
            refreshMutex.withLock {
                refreshPhotoLists()
                project()
            }
        }
    }

    private fun openPicker() {
        val routeScope = synchronized(this) { scope } ?: return
        routeScope.launch {
            val albums = withContext(ioDispatcher) { library.albums() }
            synchronized(this@AlbumTileCoordinator) {
                if (pendingTile == null) return@launch
                mutablePickerAlbums.value = albums
            }
        }
    }

    private fun launchRefresh() {
        val routeScope = synchronized(this) { scope } ?: return
        val requestGeneration = synchronized(this) { ++generation }
        refreshJob?.cancel()
        refreshJob = routeScope.launch {
            refreshMutex.withLock {
                if (requestGeneration != synchronized(this@AlbumTileCoordinator) { generation }) return@launch
                val current = synchronized(this@AlbumTileCoordinator) { tiles }
                val loaded = withContext(ioDispatcher) {
                    current.map { it.itemId to repository.loadAlbumBinding(it.itemId) }
                }
                if (requestGeneration != synchronized(this@AlbumTileCoordinator) { generation }) return@launch
                val freshBindings = loaded.mapNotNull { (id, binding) -> binding?.let { id to it } }.toMap()
                val freshPhotos = withContext(ioDispatcher) {
                    freshBindings.values.map { it.album.bucketId }.distinct()
                        .associateWith { library.photos(it) }
                }
                if (requestGeneration != synchronized(this@AlbumTileCoordinator) { generation }) return@launch
                synchronized(this@AlbumTileCoordinator) {
                    bindings = freshBindings
                    photosByBucket = freshPhotos
                }
                project()
            }
        }
    }

    /** Reloads newest-first photo lists for every bound album. */
    private suspend fun refreshPhotoLists() {
        val buckets = synchronized(this) { bindings.values.map { it.album.bucketId }.distinct() }
        val fresh = withContext(ioDispatcher) { buckets.associateWith { library.photos(it) } }
        synchronized(this) { photosByBucket = fresh }
    }

    /**
     * Projects the selected photo and its image for every bound tile still
     * present. Empty or unreadable albums select nothing, so the tile renders
     * its normal icon while the binding is kept. A thumbnail that fails to
     * decode retries on the next projection.
     */
    private suspend fun project() {
        val now = clock()
        val snapshotTiles: List<HomeRenderTile>
        val snapshotBindings: Map<ItemId, TileAlbumBinding>
        val snapshotPhotos: Map<String, List<AlbumPhoto>>
        val snapshotTaps: Map<ItemId, Long>
        synchronized(this) {
            snapshotTiles = tiles
            snapshotBindings = bindings
            snapshotPhotos = photosByBucket
            snapshotTaps = tapCounts.toMap()
        }
        val selected = mutableMapOf<ItemId, String>()
        val images = mutableMapOf<ItemId, ImageBitmap>()
        for (tile in snapshotTiles) {
            val binding = snapshotBindings[tile.itemId] ?: continue
            val photos = snapshotPhotos[binding.album.bucketId].orEmpty()
            if (photos.isEmpty()) continue
            val uri = photos[albumPhotoIndex(now, snapshotTaps[tile.itemId] ?: 0L, photos.size)].contentUri
            selected[tile.itemId] = uri
            val current = mutablePhotos.value[tile.itemId]
            if (mutableSelected.value[tile.itemId] == uri && current != null) {
                images[tile.itemId] = current
                continue
            }
            val bitmap = withContext(ioDispatcher) { library.thumbnail(uri, ALBUM_THUMBNAIL_PX) }
            if (bitmap != null) images[tile.itemId] = bitmap
        }
        mutableSelected.value = selected
        mutablePhotos.value = images
    }

    private fun ensureTicker() {
        val routeScope = synchronized(this) { scope } ?: return
        val visible = synchronized(this) { homeVisible }
        tickerJob?.cancel()
        tickerJob = if (!visible) {
            null
        } else {
            routeScope.launch {
                while (true) {
                    delay(tickIntervalMs)
                    refreshMutex.withLock { project() }
                }
            }
        }
    }
}
