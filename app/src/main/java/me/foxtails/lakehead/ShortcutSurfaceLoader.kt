package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.feature.home.ShortcutOrdering
import me.foxtails.lakehead.feature.home.HomeRenderTile
import me.foxtails.lakehead.feature.home.TilePinnedStrip
import me.foxtails.lakehead.feature.home.TileShortcutPolicy
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway

/**
 * App-side seam between the shortcut gateway, persisted tile bindings, and the
 * existing edit-menu surface (`surfaceEntries` / `onSurfaceExecute` in
 * [LauncherShell], which this slice does not change).
 *
 * [entriesForTile] joins the gateway's rank-ordered shortcuts for [identity]
 * with the persisted binding for [tileId]: rows keep gateway rank order,
 * `isPinned` reflects binding membership, and `isPinEnabled` reflects the
 * tile's capacity. Pin mutations are serialized per tile. Zero shortcuts
 * with no album binding yield an empty list so the existing
 * `editShortcutMenuEntries` empty-hides with no menu.
 *
 * Album rows (slice 7): a tile with a persisted album binding additionally
 * gets the [albumMenuRows] plain actions appended after its shortcut rows —
 * even when it has zero shortcuts, so a bound tile always offers Change /
 * Remove. The shortcut-binding read stays skipped when there are no
 * shortcuts; the album binding is always read.
 */
internal class ShortcutSurfaceLoader(
    private val gateway: ShortcutGateway,
    private val repository: HomePlacementRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * A binding is a complete ordered list, so a toggle must hold the tile's
     * lock across both the read and the write.  The coordinator deliberately
     * launches mutations independently; serializing here also protects other
     * callers of this loader from lost updates.
     */
    private val bindingLocks = mutableMapOf<ItemId, Mutex>()

    private fun lockFor(tileId: ItemId): Mutex = synchronized(bindingLocks) {
        bindingLocks.getOrPut(tileId) { Mutex() }
    }

    suspend fun entriesForTile(tileId: ItemId, identity: AppIdentity, span: LogicalSpan): List<ShortcutSurfaceEntry> =
        withContext(ioDispatcher) {
            val shortcuts = gateway.shortcutsFor(identity)
            val albumBinding = repository.loadAlbumBinding(tileId)
            if (shortcuts.isEmpty() && albumBinding == null) return@withContext emptyList()
            val shortcutEntries = if (shortcuts.isEmpty()) {
                emptyList()
            } else {
                TileShortcutPolicy.entries(shortcuts, repository.loadBinding(tileId), span)
            }
            shortcutEntries + albumMenuRows(albumBinding)
        }

    /** Compatibility projection for callers that have not yet resolved a tile span. */
    suspend fun entriesForTile(tileId: ItemId, identity: AppIdentity): List<ShortcutSurfaceEntry> =
        entriesForTile(tileId, identity, LogicalSpan(5, 1))

    suspend fun togglePin(tileId: ItemId, identity: AppIdentity, span: LogicalSpan, entryId: String): Boolean =
        withContext(ioDispatcher) {
            lockFor(tileId).withLock {
                val shortcut = gateway.shortcutsFor(identity).firstOrNull { it.ref.shortcutId == entryId }
                    ?: return@withLock false
                val binding = repository.loadBinding(tileId)
                val updated = if (shortcut.ref in binding.pinnedShortcuts) {
                    TileBinding(tileId, binding.pinnedShortcuts.filterNot { it == shortcut.ref })
                } else {
                    val pinned = ShortcutOrdering.pin(
                        binding.pinnedShortcuts, shortcut.ref, TileShortcutPolicy.maxShortcutSlots(span),
                    ) ?: return@withLock false
                    TileBinding(tileId, pinned)
                }
                repository.saveBinding(updated)
                true
            }
        }

    /** Resolves the persisted pin order to the live, label-bearing tile strip model. */
    suspend fun pinnedForTile(tileId: ItemId, identity: AppIdentity): List<PinnedTileShortcut> =
        withContext(ioDispatcher) {
            TilePinnedStrip.resolve(repository.loadBinding(tileId), gateway.shortcutsFor(identity))
        }

    /** Returns identities whose persisted bindings contain at least one pin. */
    suspend fun pinnedIdentitiesForTiles(tiles: List<HomeRenderTile>): List<AppIdentity> =
        withContext(ioDispatcher) {
            tiles.filter { repository.loadBinding(it.itemId).pinnedShortcuts.isNotEmpty() }
                .map { it.requireAppIdentity }
                .distinct()
        }

    /**
     * Executes the shortcut named by [ref] off the caller's thread.
     * Returns false when the profile is inaccessible or the platform rejects
     * the start; never throws for launch failures.
     */
    suspend fun execute(ref: ShortcutRef): Boolean = withContext(ioDispatcher) {
        gateway.launch(ref)
    }
}
