package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry

/**
 * Pure bottom-edge shortcut-slot capacity policy for home tiles.
 *
 * Builds entries from the real [TileBinding.pinnedShortcuts] binding, using the
 * tile span to derive [ShortcutStripGeometry.maxSlots] capacity for ordered
 * shortcut windowing. There are no placeholder-dot entries: pinned shortcuts
 * remain in the binding when the visible window is smaller than the binding.
 *
 * Presentation only: feature policy delegates capacity to `core:grid`; it is
 * pure and has no Android, Compose, inventory, platform wiring, persistence, or
 * grid-legality responsibility.
 */
object TileShortcutPolicy {
    /** Capacity for explicit dimensions; delegates to [ShortcutStripGeometry.maxSlots]. */
    fun maxShortcutSlots(columns: Int, rows: Int): Int =
        ShortcutStripGeometry.maxSlots(columns, rows)

    /** Capacity for a persisted logical span. */
    fun maxShortcutSlots(span: LogicalSpan): Int =
        ShortcutStripGeometry.maxSlots(span.columns, span.rows)

    /** Capacity for a grid span (e.g. [AppTileState.span]). */
    fun maxShortcutSlots(span: GridSpan): Int =
        ShortcutStripGeometry.maxSlots(span)

    fun entries(
        shortcuts: List<LauncherShortcut>,
        binding: TileBinding,
        span: LogicalSpan,
    ): List<ShortcutSurfaceEntry> {
        val pinned = binding.pinnedShortcuts.toSet()
        val capacity = maxShortcutSlots(span)
        return shortcuts.map { shortcut ->
            ShortcutSurfaceEntry(
                id = shortcut.ref.shortcutId,
                label = shortcut.label,
                isPinned = shortcut.ref in pinned,
                isPinEnabled = shortcut.ref in pinned || binding.pinnedShortcuts.size < capacity,
            )
        }
    }
}
