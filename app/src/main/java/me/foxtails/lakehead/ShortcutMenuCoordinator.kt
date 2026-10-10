package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.feature.home.HomeRenderTile
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway

/**
 * App-side owner of the selected-tile shortcut menu (B1).
 *
 * Joins the [ShortcutGateway]'s rank-ordered shortcuts for the currently
 * selected tile with its persisted binding (via [ShortcutSurfaceLoader])
 * into selection-scoped [menu] rows. The grid reports selection transitions
 * through [onSelectionChanged] and renders [menu]'s entries only while its
 * owner still matches the selection, so an A→B tap-switch hides honestly
 * until B's rows arrive instead of flashing A's rows on B.
 *
 * - Query-on-select: a newly selected tile triggers [ShortcutGateway.refresh]
 *   for its [AppIdentity][me.foxtails.lakehead.core.model.AppIdentity]; the
 *   gateway threads the platform query itself.
 * - Live invalidation: [start] observes [ShortcutGateway.shortcuts], so a
 *   publish while the menu is open recomputes rows without leaving edit and
 *   without re-firing [refresh] (reloads never refresh, so no query loop).
 * - Missing tile clears: tiles that leave [onHomeTilesChanged] drop a menu
 *   owned by the gone tile; the grid's lookup-miss hides it independently.
 * - Zero shortcuts yields no menu (null), never an empty box.
 * - Album rows: a tile with a persisted album binding additionally shows
 *   the Change/Remove action rows ([albumMenuRows][me.foxtails.lakehead.albumMenuRows],
 *   even with zero shortcuts). [refreshSelection] re-reads the open menu
 *   after such a binding changes with no tile or selection transition.
 * - [execute] resolves `(tile, entry)` to the gateway's [ShortcutRef][me.foxtails.lakehead.core.model.ShortcutRef]
 *   from the live snapshot — carrying the exact profile — and launches it
 *   off the caller's thread. Unknown tiles/entries report false, never throw.
 * - Pinning is selection-scoped and persisted off-main; a failed capacity
  *   toggle performs no write.
 *
 * Binding persistence is delegated to [ShortcutSurfaceLoader] and kept off the
 * caller thread. Call [start] before composition reports selections (production:
 * `onStart`) and [stop] when leaving (production: `onStop`).
 */
internal data class SelectedTileMenu(
    val tileId: ItemId,
    val entries: List<ShortcutSurfaceEntry>,
)

internal class ShortcutMenuCoordinator(
    private val gateway: ShortcutGateway,
    private val surfaceLoader: ShortcutSurfaceLoader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val selections = MutableStateFlow<ItemId?>(null)
    private val tiles = MutableStateFlow<List<HomeRenderTile>>(emptyList())
    private val mutableMenu = MutableStateFlow<SelectedTileMenu?>(null)
    private val mutablePinnedByTile = MutableStateFlow<Map<ItemId, List<PinnedTileShortcut>>>(emptyMap())

    /** Selection-scoped menu rows; null means no menu. */
    val menu: StateFlow<SelectedTileMenu?> = mutableMenu.asStateFlow()

    /** Live joined pin projections, keyed by persisted home tile id. */
    val pinnedByTile: StateFlow<Map<ItemId, List<PinnedTileShortcut>>> = mutablePinnedByTile.asStateFlow()

    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null
    private var reloadJob: Job? = null
    private var generation = 0L
    private var pinnedJob: Job? = null
    private var pinnedMetadataJob: Job? = null
    private var restartHoldJob: Job? = null
    private var pinnedIdentityByTile = emptyMap<ItemId, me.foxtails.lakehead.core.model.AppIdentity>()
    private var pinnedSnapshot = emptyMap<me.foxtails.lakehead.core.model.AppIdentity, List<me.foxtails.lakehead.core.model.LauncherShortcut>>()
    private var pinnedSnapshotInitialized = false
    private var pinnedSnapshotVersion = 0L
    private var pinnedProjectionBlocked = false
    private var pendingInitialPinnedRefreshes =
        emptyMap<me.foxtails.lakehead.core.model.AppIdentity, PendingPinnedRefresh>()
    private var pinnedRefreshGeneration = 0L

    @Synchronized
    fun start(parentScope: CoroutineScope) {
        if (scope != null) return
        val routeScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
        scope = routeScope
        pinnedProjectionBlocked = true
        collectJob = routeScope.launch {
            combine(selections, tiles, gateway.shortcuts) { selection, currentTiles, _ ->
                selection to currentTiles
            }.collect { (selection, currentTiles) ->
                reload(currentTiles, selection)
            }
        }
        routeScope.launch {
            gateway.shortcuts.collect { snapshot ->
                val snapshotVersion = ++pinnedSnapshotVersion
                val firstSnapshot = !pinnedSnapshotInitialized
                val changed = if (firstSnapshot) {
                    pinnedSnapshotInitialized = true
                    emptySet()
                } else {
                    (pinnedSnapshot.keys + snapshot.keys).filterTo(mutableSetOf()) { identity ->
                        pinnedSnapshot[identity] != snapshot[identity]
                    }
                }
                pinnedSnapshot = snapshot
                if (firstSnapshot) {
                    refreshPinnedMetadataForRestart(tiles.value)
                } else if (pendingInitialPinnedRefreshes.isNotEmpty()) {
                    // Publications alone have no request origin. Do not let an
                    // old, uncancellable query release a newer hold; the
                    // awaitable refresh below is the completion authority.
                } else if (changed.isNotEmpty()) {
                    projectPinned(tiles.value.filter { pinnedIdentityByTile[it.itemId] in changed }, false)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        generation++
        reloadJob?.cancel()
        pinnedJob?.cancel()
        pinnedMetadataJob?.cancel()
        restartHoldJob?.cancel()
        collectJob?.cancel()
        scope?.cancel()
        mutablePinnedByTile.value = emptyMap()
        pinnedIdentityByTile = emptyMap()
        pinnedSnapshot = emptyMap()
        pinnedSnapshotInitialized = false
        pinnedSnapshotVersion = 0L
        pinnedProjectionBlocked = false
        pendingInitialPinnedRefreshes = emptyMap()
        pinnedRefreshGeneration++
        scope = null
        collectJob = null
        reloadJob = null
        pinnedJob = null
        pinnedMetadataJob = null
        restartHoldJob = null
    }

    /**
     * Reports a grid selection transition.
     *
     * A changed selection clears stale rows immediately (honest empty during
     * the load), fires query-on-select for the newly selected tile's identity,
     * and reloads. An unchanged selection is a no-op (no refresh storm on
     * retarget-to-same). Missing tiles clear: a non-null id with no tile
     * publishes no menu.
     */
    fun onSelectionChanged(tileId: ItemId?) {
        if (selections.value == tileId) return
        selections.value = tileId
        mutableMenu.value = null
        val identity = tileId?.let { id -> tiles.value.firstOrNull { it.itemId == id }?.appIdentity }
        if (identity != null) {
            gateway.refresh(identity)
        }
    }

    /**
     * Reports the latest loaded home tiles.
     *
     * Drops a menu owned by a tile that is no longer present (removal/
     * uninstall clears without leaving edit); the entries reload observes the
     * new tiles through [start]'s combine.
     */
    fun onHomeTilesChanged(currentTiles: List<HomeRenderTile>) {
        // Folder tiles have no app identity, so no shortcut menu or pins.
        tiles.value = currentTiles.filterNot { it.isFolder }
        projectPinned(currentTiles)
        if (pinnedProjectionBlocked) {
            refreshPinnedMetadataForRestart(currentTiles)
        } else {
            refreshPinnedMetadata(currentTiles)
        }
        val current = mutableMenu.value
        if (current != null && currentTiles.none { it.itemId == current.tileId }) {
            mutableMenu.value = null
        }
    }

    /**
     * Executes the menu row [entryId] of [tileId] under its exact profile.
     *
     * Returns false (never throws) when the tile is unknown, the entry is not
     * in the gateway's live snapshot for the tile's identity, or the platform
     * rejects the start.
     */
    suspend fun execute(tileId: ItemId, entryId: String): Boolean = withContext(ioDispatcher) {
        val tile = tiles.value.firstOrNull { it.itemId == tileId } ?: return@withContext false
        val ref = gateway.shortcutsFor(tile.requireAppIdentity)
            .firstOrNull { it.ref.shortcutId == entryId }?.ref
            ?: return@withContext false
        surfaceLoader.execute(ref)
    }

    private fun reload(currentTiles: List<HomeRenderTile>, selection: ItemId?) {
        val routeScope = synchronized(this) { scope } ?: return
        val requestGeneration = synchronized(this) { ++generation }
        reloadJob?.cancel()
        reloadJob = routeScope.launch {
            val selected = synchronized(this@ShortcutMenuCoordinator) {
                if (requestGeneration != generation) return@launch
                selection
            } ?: run {
                mutableMenu.value = null
                return@launch
            }
            val tile = currentTiles.firstOrNull { it.itemId == selected }
            if (tile == null) {
                if (requestGeneration == generation) mutableMenu.value = null
                return@launch
            }
            val entries = withContext(ioDispatcher) {
                surfaceLoader.entriesForTile(selected, tile.requireAppIdentity, tile.span)
            }
            synchronized(this@ShortcutMenuCoordinator) {
                if (requestGeneration != generation || selections.value != selected) return@launch
                mutableMenu.value = if (entries.isEmpty()) null else SelectedTileMenu(selected, entries)
            }
        }
    }

    fun togglePin(tileId: ItemId, entryId: String) {
        val routeScope = synchronized(this) { scope } ?: return
        routeScope.launch {
            val tile = tiles.value.firstOrNull { it.itemId == tileId } ?: return@launch
            if (surfaceLoader.togglePin(tileId, tile.requireAppIdentity, tile.span, entryId)) {
                projectPinned(tiles.value.filter { it.itemId == tileId }, false)
                refreshPinnedMetadata(tiles.value)
                if (selections.value == tileId) reload(tiles.value, tileId)
            }
        }
    }

    /**
     * Reloads the current selection's rows without a selection or tile
     * change (slice 7).
     *
     * The album binding can change while the menu stays open (edit-menu
     * "Remove album" clears it with no tile or selection transition), so
     * the caller asks for a re-read after such a change and the open menu
     * drops or gains its Change/Remove rows. A null selection or a missing
     * tile publishes no menu, exactly like [reload].
     */
    fun refreshSelection() {
        synchronized(this) { scope } ?: return
        reload(tiles.value, selections.value)
    }

    private fun projectPinned(
        currentTiles: List<HomeRenderTile>,
        replaceAll: Boolean = true,
        allowWhileBlocked: Boolean = false,
    ) {
        val routeScope = synchronized(this) { scope } ?: return
        if (pinnedProjectionBlocked && !allowWhileBlocked) return
        pinnedJob?.cancel()
        pinnedJob = routeScope.launch {
            val projected = currentTiles.associate { tile ->
                tile.itemId to surfaceLoader.pinnedForTile(tile.itemId, tile.requireAppIdentity)
            }
            val identities = currentTiles.associate { tile ->
                tile.itemId to tile.requireAppIdentity
            }
            pinnedIdentityByTile = if (replaceAll) {
                val pinned = surfaceLoader.pinnedIdentitiesForTiles(currentTiles).toSet()
                identities.filterValues { it in pinned }
            } else {
                pinnedIdentityByTile - currentTiles.map { it.itemId }.toSet() +
                    identities.filterValues { identity ->
                        identity in surfaceLoader.pinnedIdentitiesForTiles(currentTiles).toSet()
                    }
            }
            mutablePinnedByTile.value = if (replaceAll) {
                projected.filterValues { it.isNotEmpty() }
            } else {
                (mutablePinnedByTile.value - projected.keys + projected.filterValues { it.isNotEmpty() })
                    .filterKeys { id -> tiles.value.any { it.itemId == id } }
            }
        }
    }

    private fun refreshPinnedMetadata(currentTiles: List<HomeRenderTile>) {
        val routeScope = synchronized(this) { scope } ?: return
        pinnedMetadataJob?.cancel()
        pinnedMetadataJob = routeScope.launch {
            val identities = surfaceLoader.pinnedIdentitiesForTiles(currentTiles).distinct()
            pinnedIdentityByTile = currentTiles
                .filter { it.requireAppIdentity in identities }
                .associate { it.itemId to it.requireAppIdentity }
            identities.forEach(gateway::refresh)
        }
    }

    /**
     * A retained home can still have a stale shortcut snapshot after process
     * restart. Hold the first projection until each persisted pinned identity
     * has had its restart refresh published, with a bounded fallback for a
     * profile/query that cannot publish.
     */
    private fun refreshPinnedMetadataForRestart(currentTiles: List<HomeRenderTile>) {
        val routeScope = synchronized(this) { scope } ?: return
        val requestGeneration = ++pinnedRefreshGeneration
        restartHoldJob?.cancel()
        restartHoldJob = routeScope.launch {
            try {
                val identities = surfaceLoader.pinnedIdentitiesForTiles(currentTiles).distinct()
                val requestSnapshotVersion = pinnedSnapshotVersion
                pendingInitialPinnedRefreshes = identities.associateWith {
                    PendingPinnedRefresh(requestGeneration, requestSnapshotVersion)
                }
                if (identities.isEmpty()) {
                    delay(INITIAL_PINNED_REFRESH_TIMEOUT_MS)
                    if (requestGeneration != pinnedRefreshGeneration) return@launch
                    pinnedProjectionBlocked = false
                    projectPinned(currentTiles)
                    return@launch
                }
                val refreshJobs = identities.map { identity ->
                    launch {
                        if (gateway.refreshAndAwait(identity)) {
                            synchronized(this@ShortcutMenuCoordinator) {
                                if (requestGeneration != pinnedRefreshGeneration) return@launch
                                pendingInitialPinnedRefreshes = pendingInitialPinnedRefreshes - identity
                                pinnedProjectionBlocked = pendingInitialPinnedRefreshes.isNotEmpty()
                            }
                            if (!pinnedProjectionBlocked) {
                                projectPinned(tiles.value, allowWhileBlocked = true)
                            }
                        }
                    }
                }
                // Keep the bounded fallback independent of query completion.
                delay(INITIAL_PINNED_REFRESH_TIMEOUT_MS)
                refreshJobs.forEach(Job::cancel)
                if (requestGeneration == pinnedRefreshGeneration) {
                    pendingInitialPinnedRefreshes = emptyMap()
                    pinnedProjectionBlocked = false
                    projectPinned(tiles.value)
                }
            } finally {
                // A superseded hold must not strand its state. The generation
                // guard preserves a newer hold's pending set while ensuring an
                // independently cancelled current hold releases its block.
                if (requestGeneration == pinnedRefreshGeneration) {
                    pendingInitialPinnedRefreshes = emptyMap()
                    pinnedProjectionBlocked = false
                }
            }
        }
    }

    private companion object {
        const val INITIAL_PINNED_REFRESH_TIMEOUT_MS = 1_000L
    }

    private data class PendingPinnedRefresh(
        val generation: Long,
        val snapshotVersion: Long,
    )
}
