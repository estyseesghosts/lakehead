package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Pure bottom-edge shortcut-slot capacity policy for home tiles.
 *
 * The app icon occupies exactly one slot on the tile's bottom edge, so the
 * remaining edge cells bound the visual-only shortcut placeholder dots:
 * - single-column tiles (`columns == 1`) grow vertically: `max = rows - 1`
 *   (1x1 → 0, 1x2 → 1, 1x3 → 2);
 * - wider tiles (`columns > 1`) grow horizontally: `max = columns - 1`
 *   (2x2 → 1, 3x2 → 2, 5-wide → 4).
 *
 * Presentation only: no inventory, platform wiring, persistence, or grid
 * legality. Callers clamp `shortcutPlaceholders` into `0..max`; over-counts
 * render `max` dots rather than crashing.
 */
object TileShortcutPolicy {
    /** Capacity for explicit dimensions; negative inputs coerce to 0. */
    fun maxShortcutSlots(columns: Int, rows: Int): Int {
        if (columns <= 0 || rows <= 0) return 0
        return if (columns == 1) (rows - 1).coerceAtLeast(0) else (columns - 1).coerceAtLeast(0)
    }

    /** Capacity for a persisted logical span. */
    fun maxShortcutSlots(span: LogicalSpan): Int =
        maxShortcutSlots(span.columns, span.rows)

    /** Capacity for a grid span (e.g. [AppTileState.span]). */
    fun maxShortcutSlots(span: GridSpan): Int =
        maxShortcutSlots(span.columns, span.rows)
}
