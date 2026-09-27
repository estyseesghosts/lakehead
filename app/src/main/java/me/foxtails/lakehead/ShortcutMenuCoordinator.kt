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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
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
 * - [execute] resolves `(tile, entry)` to the gateway's [ShortcutRef][me.foxtails.lakehead.core.model.ShortcutRef]
 *   from the live snapshot — carrying the exact profile — and launches it
 *   off the caller's thread. Unknown tiles/entries report false, never throw.
 * - Pinning stays inert: this coordinator never toggles bindings (B2 owns
 *   pin persistence); callers leave `onSurfaceTogglePin` unwired.
 *
 * No persistence writes occur here; [ShortcutSurfaceLoader] performs reads
 * only. Call [start] before composition reports selections (production:
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

    /** Selection-scoped menu rows; null means no menu. */
    val menu: StateFlow<SelectedTileMenu?> = mutableMenu.asStateFlow()

    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null
    private var reloadJob: Job? = null
    private var generation = 0L

    @Synchronized
    fun start(parentScope: CoroutineScope) {
        if (scope != null) return
        val routeScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
        scope = routeScope
        collectJob = routeScope.launch {
            combine(selections, tiles, gateway.shortcuts) { selection, currentTiles, _ ->
                selection to currentTiles
            }.collect { (selection, currentTiles) ->
                reload(currentTiles, selection)
            }
        }
    }

    @Synchronized
    fun stop() {
        generation++
        reloadJob?.cancel()
        collectJob?.cancel()
        scope?.cancel()
        scope = null
        collectJob = null
        reloadJob = null
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
        tiles.value = currentTiles.toList()
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
        val ref = gateway.shortcutsFor(tile.appIdentity)
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
                surfaceLoader.entriesForTile(selected, tile.appIdentity)
            }
            synchronized(this@ShortcutMenuCoordinator) {
                if (requestGeneration != generation || selections.value != selected) return@launch
                mutableMenu.value = if (entries.isEmpty()) null else SelectedTileMenu(selected, entries)
            }
        }
    }
}
