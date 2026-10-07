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
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.feature.allapps.AllAppsEntry
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.platform.launcherapps.AppCatalogue
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshotCache
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconSnapshotRefresher

/**
 * Route-scoped, read-only loader for the All Apps list.
 *
 * Mirrors [HomeRouteLoader]'s concurrency pattern: [start] creates a child scope with a
 * [SupervisorJob], collects [AppCatalogue.apps], joins with home membership from
 * [HomePlacementRepository], and projects [AllAppsViewState]. [stop] cancels all jobs.
 *
 * Icons are refreshed for ALL catalogue entries and read from the same encoded and decoded
 * caches that [HomeRouteLoader] uses.
 */
internal class AllAppsRouteLoader(
    private val homeId: HomeId,
    private val repository: HomePlacementRepository,
    private val catalogue: AppCatalogue,
    private val iconRefresher: LauncherAppsIconSnapshotRefresher,
    private val encodedIcons: AppIconSnapshotCache,
    private val decodedIcons: DecodedIconCache<ImageBitmap>,
    private val columnsBound: Int = 5,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow<AllAppsViewState>(AllAppsViewState.Loading)
    val state: StateFlow<AllAppsViewState> = mutableState.asStateFlow()
    private val mutableIcons = MutableStateFlow<Map<AppIdentity, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<AppIdentity, ImageBitmap>> = mutableIcons.asStateFlow()

    private var scope: CoroutineScope? = null
    private var catalogueJob: Job? = null
    private var loadJob: Job? = null
    private var generation = 0L
    private var catalogueGeneration = 0L
    private val iconRefreshMutex = Mutex()
    private var homeIdentitySet: Set<AppIdentity>? = null
    private var currentEntries: List<LauncherAppEntry> = emptyList()

    @Synchronized
    fun start(parentScope: CoroutineScope) {
        if (scope != null) return
        val routeScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
        scope = routeScope
        catalogueJob = routeScope.launch {
            catalogue.apps.collect { entries ->
                val revision = synchronized(this@AllAppsRouteLoader) {
                    currentEntries = entries.toList()
                    catalogueGeneration++
                    projectCurrent()
                    catalogueGeneration
                }
                refreshIconProjection(entries, revision)
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
        mutableState.value = AllAppsViewState.Loading
        homeIdentitySet = null
        loadJob = routeScope.launch {
            val result = withContext(ioDispatcher) {
                repository.load(homeId, columnsBound)
            }
            synchronized(this@AllAppsRouteLoader) {
                if (requestGeneration != generation || scope !== routeScope) return@launch
                when (result) {
                    is LoadResult.Loaded -> {
                        homeIdentitySet = result.placements.mapTo(HashSet()) { it.appIdentity }
                        projectCurrent()
                    }
                    LoadResult.NotFound -> {
                        homeIdentitySet = emptySet()
                        projectCurrent()
                    }
                }
            }
            val catalogueSnapshot = synchronized(this@AllAppsRouteLoader) {
                currentEntries to catalogueGeneration
            }
            refreshIconProjection(catalogueSnapshot.first, catalogueSnapshot.second)
        }
    }

    /** Re-reads home membership and re-projects (for post-add refresh). */
    @Synchronized
    fun refreshHomeMembership() {
        val routeScope = scope ?: return
        val requestGeneration = ++generation
        loadJob?.cancel()
        loadJob = routeScope.launch {
            val result = withContext(ioDispatcher) {
                repository.load(homeId, columnsBound)
            }
            synchronized(this@AllAppsRouteLoader) {
                if (requestGeneration != generation || scope !== routeScope) return@launch
                when (result) {
                    is LoadResult.Loaded -> {
                        homeIdentitySet = result.placements.mapTo(HashSet()) { it.appIdentity }
                        projectCurrent()
                    }
                    LoadResult.NotFound -> {
                        homeIdentitySet = emptySet()
                        projectCurrent()
                    }
                }
            }
        }
    }

    private suspend fun refreshIconProjection(entries: List<LauncherAppEntry>, revision: Long) {
        iconRefreshMutex.withLock {
            try {
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
                if (revision != catalogueGeneration) return
                mutableIcons.value = decoded
            }
        }
    }

    private fun projectCurrent() {
        val membership = homeIdentitySet ?: return
        val entries = currentEntries.map { entry ->
            AllAppsEntry(
                identity = entry.identity,
                label = entry.label,
                sortKey = entry.sortKey,
                displayLabel = entry.displayLabel,
                alreadyOnHome = entry.identity in membership,
            )
        }
        mutableState.value = AllAppsViewState.Loaded(entries)
    }
}
