package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway

/**
 * App-side seam between the shortcut gateway, persisted tile bindings, and the
 * existing edit-menu surface (`surfaceEntries` / `onSurfaceExecute` in
 * [LauncherShell], which this slice does not change).
 *
 * [entriesForTile] joins the gateway's rank-ordered shortcuts for [identity]
 * with the persisted binding for [tileId]: rows keep gateway rank order,
 * `isPinned` reflects binding membership, and `isPinEnabled` stays true
 * because pinning persistence is a later slice — the pin toggle is inert
 * until then (the caller leaves `onSurfaceTogglePin` unwired). A zero
 * shortcut list yields an empty list so the existing
 * `editShortcutMenuEntries` empty-hides with no menu.
 */
internal class ShortcutSurfaceLoader(
    private val gateway: ShortcutGateway,
    private val repository: HomePlacementRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun entriesForTile(tileId: ItemId, identity: AppIdentity): List<ShortcutSurfaceEntry> =
        withContext(ioDispatcher) {
            val shortcuts = gateway.shortcutsFor(identity)
            if (shortcuts.isEmpty()) return@withContext emptyList()
            val pinned = repository.loadBinding(tileId).pinnedShortcuts.toSet()
            shortcuts.map { shortcut ->
                ShortcutSurfaceEntry(
                    id = shortcut.ref.shortcutId,
                    label = shortcut.label,
                    isPinned = shortcut.ref in pinned,
                    isPinEnabled = true,
                )
            }
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
