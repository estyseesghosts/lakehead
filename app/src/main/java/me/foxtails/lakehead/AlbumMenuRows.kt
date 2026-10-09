package me.foxtails.lakehead

import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.model.TileAlbumBinding

/** Edit-menu row that reopens the album picker for a bound tile. */
internal const val ALBUM_MENU_CHANGE_ID: String = "protolaunch:album:change"

/** Edit-menu row that clears the tile's album binding. */
internal const val ALBUM_MENU_REMOVE_ID: String = "protolaunch:album:remove"

internal const val ALBUM_MENU_CHANGE_LABEL: String = "Change album"
internal const val ALBUM_MENU_REMOVE_LABEL: String = "Remove album"

/**
 * Plain action rows for a bound album tile, appended after the tile's
 * shortcut rows in the edit menu (slice 7).
 *
 * A null [binding] yields no rows, so unbound tiles keep the shortcut-only
 * menu (or no menu when there are no shortcuts either). The ids are
 * namespaced so they never collide with platform shortcut ids; the caller
 * routes taps by id (change reopens the picker, remove clears the binding)
 * and never launches them as shortcuts. Rows are execute-only
 * ([ShortcutSurfaceEntry.isAction]): the menu renders no pin toggle for
 * them, and a pin toggle tap would resolve to no shortcut and do nothing.
 */
internal fun albumMenuRows(binding: TileAlbumBinding?): List<ShortcutSurfaceEntry> =
    if (binding == null) {
        emptyList()
    } else {
        listOf(
            ShortcutSurfaceEntry(
                id = ALBUM_MENU_CHANGE_ID,
                label = ALBUM_MENU_CHANGE_LABEL,
                isPinned = false,
                isPinEnabled = false,
                isAction = true,
            ),
            ShortcutSurfaceEntry(
                id = ALBUM_MENU_REMOVE_ID,
                label = ALBUM_MENU_REMOVE_LABEL,
                isPinned = false,
                isPinEnabled = false,
                isAction = true,
            ),
        )
    }
