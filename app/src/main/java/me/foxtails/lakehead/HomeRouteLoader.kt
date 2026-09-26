package me.foxtails.lakehead

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.RemoveResult
import me.foxtails.lakehead.feature.home.HomeAppMetadata
import me.foxtails.lakehead.feature.home.HomeRenderProjection
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.feature.home.HomeViewportInputs
import me.foxtails.lakehead.platform.launcherapps.AppCatalogue
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshotCache
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconSnapshotRefresher
import java.util.Collections

/** Route-scoped, read-only loader for the explicitly selected Home. */
internal class HomeRouteLoader(
    private val homeId: HomeId,
    private val repository: HomePlacementRepository,
    private val catalogue: AppCatalogue,
    private val iconRefresher: LauncherAppsIconSnapshotRefresher,
    private val encodedIcons: AppIconSnapshotCache,
    private val decodedIcons: DecodedHomeIconCache<ImageBitmap>,
    private val viewport: HomeRouteViewport,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow<HomeRenderState>(HomeRenderProjection.loading(homeId))
    val state: StateFlow<HomeRenderState> = mutableState.asStateFlow()
    private val mutableIcons = MutableStateFlow<Map<AppIdentity, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<AppIdentity, ImageBitmap>> = mutableIcons.asStateFlow()

    private var scope: CoroutineScope? = null
    private var catalogueJob: Job? = null
    private var loadJob: Job? = null
    private var generation = 0L
    private var catalogueGeneration = 0L
    private val iconRefreshMutex = Mutex()
    private var placements: List<AppPlacement>? = null
    private var notFound = false
    private var currentEntries: List<LauncherAppEntry> = emptyList()

    @Synchronized
    fun start(parentScope: CoroutineScope) {
        if (scope != null) return
        val routeScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
        scope = routeScope
        catalogueJob = routeScope.launch {
            catalogue.apps.collect { entries ->
                val revision = synchronized(this@HomeRouteLoader) {
                    currentEntries = entries.toList()
                    catalogueGeneration++
                    projectCurrent()
                    catalogueGeneration
                }
                refreshCatalogueProjection(entries, revision)
            }
        }
        reload()
    }

    @Synchronized
    fun stop() {
        generation++
        loadJob?.cancel()
        catalogueJob?.cancel()
        scope?.cancel()
        scope = null
        loadJob = null
        catalogueJob = null
    }

    @Synchronized
    fun reload() {
        val routeScope = scope ?: return
        val requestGeneration = ++generation
        loadJob?.cancel()
        loadJob = routeScope.launch {
            val result = withContext(ioDispatcher) {
                repository.load(homeId, viewport.columnsBound, viewport.rowsBound)
            }
            synchronized(this@HomeRouteLoader) {
                if (requestGeneration != generation || scope !== routeScope) return@launch
                when (result) {
                    is LoadResult.Loaded -> {
                        placements = result.placements.toList()
                        notFound = false
                        projectCurrent()
                    }
                    LoadResult.NotFound -> {
                        placements = null
                        notFound = true
                        mutableState.value = HomeRenderProjection.notFound(homeId)
                        mutableIcons.value = emptyMap()
                    }
                }
            }
            val catalogueSnapshot = synchronized(this@HomeRouteLoader) {
                currentEntries to catalogueGeneration
            }
            refreshCatalogueProjection(catalogueSnapshot.first, catalogueSnapshot.second)
        }
    }

    /**
     * Commits a drag-finished tile move on the IO dispatcher and reloads on success.
     *
     * Callers must be in a coroutine scope (typically `lifecycleScope`).
     * The repository write is synchronous (Room transaction) and is wrapped in
     * `withContext(Dispatchers.IO)` so the caller never blocks the main thread.
     * Only [MutationResult.Accepted] triggers a reload — rejected or cancelled
     * writes persist nothing.
     */
    suspend fun commitMove(itemId: ItemId, position: LogicalPosition): MutationResult {
        return withContext(ioDispatcher) {
            val result = repository.move(
                homeId = homeId,
                itemId = itemId,
                position = position,
                columns = viewport.columnsBound,
                rows = viewport.rowsBound,
            )
            if (result is MutationResult.Accepted) {
                reload()
            }
            result
        }
    }

    /**
     * Commits a drag-finished tile resize on the IO dispatcher and reloads on success.
     *
     * Callers must be in a coroutine scope (typically `lifecycleScope`).
     * The repository write is synchronous (Room transaction) and is wrapped in
     * `withContext(Dispatchers.IO)` so the caller never blocks the main thread.
     * Only [MutationResult.Accepted] triggers a reload — rejected or cancelled
     * writes persist nothing.
     */
    suspend fun commitResize(itemId: ItemId, span: LogicalSpan): MutationResult {
        return withContext(ioDispatcher) {
            val result = repository.resize(
                homeId = homeId,
                itemId = itemId,
                span = span,
                columns = viewport.columnsBound,
                rows = viewport.rowsBound,
            )
            if (result is MutationResult.Accepted) {
                reload()
            }
            result
        }
    }

    /**
     * Commits a tile removal on the IO dispatcher and reloads on success.
     *
     * Callers must be in a coroutine scope (typically `lifecycleScope`).
     * The repository write is synchronous (Room transaction) and is wrapped in
     * `withContext(Dispatchers.IO)` so the caller never blocks the main thread.
     * Only [RemoveResult.Removed] triggers a reload — [RemoveResult.NotFound] or
     * [RemoveResult.WrongHome] persist nothing.
     */
    suspend fun commitRemove(itemId: ItemId): RemoveResult {
        return withContext(ioDispatcher) {
            val result = repository.remove(
                homeId = homeId,
                itemId = itemId,
            )
            if (result is RemoveResult.Removed) {
                reload()
            }
            result
        }
    }

    private suspend fun refreshCatalogueProjection(entries: List<LauncherAppEntry>, revision: Long) {
        iconRefreshMutex.withLock {
            val persisted = synchronized(this) {
                if (revision != catalogueGeneration) return
                placements
            } ?: return
            val placedIdentities = persisted.mapTo(HashSet()) { it.appIdentity }
            val placedEntries = entries.filter { it.identity in placedIdentities }
            try {
                iconRefresher.refresh(placedEntries)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Preserve last-good decoded icons on transient refresh failure;
                // do not invalidate the (now-isolated) encoded cache.
                return@withLock
            }
            val decoded = withContext(ioDispatcher) { decodedIcons.replace(encodedIcons.snapshot()) }
            synchronized(this) {
                // Revisions serialize cache replacement and keep stale metadata/icons from winning.
                if (revision != catalogueGeneration || placements !== persisted || notFound) return
                mutableIcons.value = decoded
            }
        }
    }

    private fun projectCurrent() {
        val persisted = placements ?: return
        val metadata = currentEntries.associate { it.identity to HomeAppMetadata(it.label) }
        val projected = HomeRenderProjection.project(homeId, persisted, metadata, viewport.inputs)
        mutableState.value = projected.copy(tiles = Collections.unmodifiableList(ArrayList(projected.tiles)))
    }
}

/** Finite repository bounds and explicit normal-phone visual inputs (not a responsive policy). */
internal data class HomeRouteViewport(
    val inputs: HomeViewportInputs,
    val columnsBound: Int = 5,
    val rowsBound: Int = 4096,
) {
    init { require(columnsBound > 0 && rowsBound > 0) }
}
