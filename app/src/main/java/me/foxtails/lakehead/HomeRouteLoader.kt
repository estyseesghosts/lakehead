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
import me.foxtails.lakehead.core.grid.GridRules
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

/**
 * Route-scoped owner of the projected state of the explicitly selected Home.
 *
 * It loads persisted placements and is also the only place that acknowledges
 * and reconciles move/resize/remove commits, so gesture code only emits intents.
 */
internal class HomeRouteLoader(
    private val homeId: HomeId,
    private val repository: HomePlacementRepository,
    private val catalogue: AppCatalogue,
    private val iconRefresher: LauncherAppsIconSnapshotRefresher,
    private val encodedIcons: AppIconSnapshotCache,
    private val decodedIcons: DecodedIconCache<ImageBitmap>,
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

    // Serializes repository writes in call order; mutationsInFlight counts
    // move/resize commits between their optimistic apply and reconciliation.
    private val writeMutex = Mutex()
    private var mutationsInFlight = 0
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
                repository.load(homeId, viewport.columnsBound)
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
            refreshIconsForCurrentCatalogue()
        }
    }

    /**
     * Commits a drag-finished tile move: the requested cell is applied to the
     * projected state immediately, the repository write runs on the IO
     * dispatcher, and the returned normalized grid becomes the final state.
     * See [commitMutation] for the reconciliation rules.
     *
     * Callers must be in a coroutine scope (typically `lifecycleScope`).
     */
    suspend fun commitMove(itemId: ItemId, position: LogicalPosition): MutationResult =
        commitMutation(
            optimistic = { withRequestedMove(it, itemId, position, viewport.columnsBound) },
            write = {
                repository.move(
                    homeId = homeId,
                    itemId = itemId,
                    position = position,
                    columns = viewport.columnsBound,
                )
            },
        )

    /**
     * Commits a drag-finished tile resize with the same optimistic apply and
     * reconciliation as [commitMove].
     */
    suspend fun commitResize(itemId: ItemId, span: LogicalSpan): MutationResult =
        commitMutation(
            optimistic = { withRequestedSpan(it, itemId, span) },
            write = {
                repository.resize(
                    homeId = homeId,
                    itemId = itemId,
                    span = span,
                    columns = viewport.columnsBound,
                )
            },
        )

    /**
     * Commits a tile removal on the IO dispatcher and reloads on success.
     *
     * Callers must be in a coroutine scope (typically `lifecycleScope`).
     * The write is serialized with move/resize writes. Only [RemoveResult.Removed]
     * reloads — [RemoveResult.NotFound] or [RemoveResult.WrongHome] persist
     * nothing — and a move/resize still in flight reconciles afterwards instead,
     * from a grid that already excludes the removed tile.
     */
    suspend fun commitRemove(itemId: ItemId): RemoveResult {
        val result = writeMutex.withLock {
            withContext(ioDispatcher) { repository.remove(homeId = homeId, itemId = itemId) }
        }
        if (result is RemoveResult.Removed) reloadWhenNoMutationInFlight()
        return result
    }

    /**
     * Shared reconciliation for placement mutations.
     *
     * 1. [optimistic] is applied to the projected state before the first
     *    suspension, so the dropped tile does not wait for the database.
     * 2. Writes are serialized by [writeMutex] in call order.
     * 3. [MutationResult.Accepted] carries the full normalized grid; the last
     *    in-flight mutation replaces the state with it directly (no reload).
     *    Normalization can move several tiles, so the request is never assumed
     *    to be the final layout.
     * 4. Rejection, not-found, failure or cancellation of the last in-flight
     *    mutation reloads the persisted state, undoing the optimistic apply. An
     *    earlier mutation never reconciles while a later one is in flight: the
     *    later result already reflects every earlier write.
     */
    private suspend fun commitMutation(
        optimistic: (List<AppPlacement>) -> List<AppPlacement>?,
        write: () -> MutationResult,
    ): MutationResult {
        beginMutation(optimistic)
        val result = try {
            writeMutex.withLock { withContext(ioDispatcher) { write() } }
        } catch (failure: Throwable) {
            finishMutation(accepted = null)
            throw failure
        }
        val appliedDirectly = finishMutation((result as? MutationResult.Accepted)?.gridResult)
        if (appliedDirectly) refreshIconsForCurrentCatalogue()
        return result
    }

    @Synchronized
    private fun beginMutation(optimistic: (List<AppPlacement>) -> List<AppPlacement>?) {
        mutationsInFlight++
        val current = placements ?: return
        val requested = optimistic(current) ?: return
        // A load that started before this write would land the old cells over it.
        generation++
        placements = requested
        projectCurrent()
    }

    /** Returns true when the state was replaced from [accepted] without a reload. */
    @Synchronized
    private fun finishMutation(accepted: GridRules.Result.Accepted<ItemId>?): Boolean {
        mutationsInFlight--
        if (mutationsInFlight > 0) return false
        val current = placements
        val final = if (accepted != null && current != null && scope != null) {
            withAcceptedGrid(current, accepted)
        } else {
            null
        }
        if (final == null) {
            reload()
            return false
        }
        generation++
        placements = final
        projectCurrent()
        return true
    }

    @Synchronized
    private fun reloadWhenNoMutationInFlight() {
        if (mutationsInFlight == 0) reload()
    }

    private suspend fun refreshIconsForCurrentCatalogue() {
        val snapshot = synchronized(this) { currentEntries to catalogueGeneration }
        refreshCatalogueProjection(snapshot.first, snapshot.second)
    }

    private suspend fun refreshCatalogueProjection(entries: List<LauncherAppEntry>, revision: Long) {
        iconRefreshMutex.withLock {
            val persisted = synchronized(this) {
                if (revision != catalogueGeneration) return
                placements
            } ?: return
            try {
                // The icon cache is shared with All Apps and is driven by the whole catalogue.
                iconRefresher.refresh(entries)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Preserve last-good decoded icons on transient refresh failure;
                // do not invalidate the shared encoded cache.
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

/** Explicit normal-phone visual inputs (not a responsive policy). */
internal data class HomeRouteViewport(
    val inputs: HomeViewportInputs,
    val columnsBound: Int = 5,
) {
    init { require(columnsBound > 0) }
}
