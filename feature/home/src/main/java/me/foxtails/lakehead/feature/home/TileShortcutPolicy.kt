package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Pure bottom-edge shortcut-slot capacity policy for home tiles.
 *
 * Thin delegate to [ShortcutStripGeometry.maxSlots]: the app icon occupies
 * exactly one slot on the tile's bottom edge, so the remaining edge cells bound
 * the visual-only shortcut placeholder dots.
 *
 * Presentation only: no inventory, platform wiring, persistence, or grid
 * legality. Callers clamp `shortcutPlaceholders` into `0..max`; over-counts
 * render `max` dots rather than crashing.
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
}
