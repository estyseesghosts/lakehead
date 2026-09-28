package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.TileBinding

/**
 * Arranged pinned-shortcut strip for one home tile.
 *
 * @param visible shortcuts to render, in pin order with index 0 nearest the
 * app icon (stable: unpins collapse toward the app, resizes reveal/hide from
 * the far end).
 * @param overflowCount retained-but-hidden shortcuts beyond [capacity].
 * @param capacity visible-slot budget from `core:grid` for the arranging span.
 * @param showOverflowIndicator whether the tile renders the visible `+N`
 * node.  False while the tile is edit-selected (remove/resize controls float
 * above the strip, so the indicator yields) and on zero-capacity spans; the
 * count is still announced through the tile body's semantics in those cases.
 */
data class PinnedStripArrangement(
    val visible: List<PinnedTileShortcut>,
    val overflowCount: Int,
    val capacity: Int,
    val showOverflowIndicator: Boolean,
)

/**
 * Pure arrangement of a tile's pinned shortcuts for rendering.
 *
 * Consumes the B2 sources without rewiring them: the persisted per-tile
 * [TileBinding] (full ordered pin list, the source of truth) joined with the
 * live rank-ordered [LauncherShortcut] metadata for the tile's app.  Capacity
 * comes from `core:grid` via [TileShortcutPolicy]; the visible/overflow
 * window delegates to [ShortcutOrdering]; this object only joins labels and
 * applies the edit-inset rule.  No inventory, platform access, persistence or
 * grid legality lives here.
 */
object TilePinnedStrip {

    /**
     * Joins [binding]'s ordered pin refs with live [shortcuts] metadata into
     * the render list, preserving binding (pin) order — never gateway rank
     * order, which only drives the edit menu.
     *
     * Refs with no live metadata are omitted from rendering (stale shortcuts
     * fail safe and never destabilize the tile) while the binding retains
     * them, so they recover automatically if the app republishes the id.
     */
    fun resolve(
        binding: TileBinding,
        shortcuts: List<LauncherShortcut>,
    ): List<PinnedTileShortcut> {
        if (binding.pinnedShortcuts.isEmpty() || shortcuts.isEmpty()) return emptyList()
        val labelsByRef = shortcuts.associate { it.ref to it.label }
        return binding.pinnedShortcuts.mapNotNull { ref ->
            val label = labelsByRef[ref] ?: return@mapNotNull null
            PinnedTileShortcut(id = ref.shortcutId, label = label)
        }
    }

    /**
     * Derives the render window for [resolved] (as produced by [resolve]) on
     * [span].
     *
     * The full ordered list is never trimmed here: shrinking the span (or the
     * edit-selected inset) only moves items into [PinnedStripArrangement.overflowCount],
     * and growing restores them, so resize never silently destroys user
     * configuration.  Callers re-run this on every span change (including the
     * live resize preview) with the same [resolved] list.
     */
    fun arrange(
        span: LogicalSpan,
        resolved: List<PinnedTileShortcut>,
        isEditSelected: Boolean,
    ): PinnedStripArrangement {
        val capacity = TileShortcutPolicy.maxShortcutSlots(span)
        val visible = ShortcutOrdering.visibleShortcuts(resolved, capacity)
        val overflow = ShortcutOrdering.overflowShortcuts(resolved, capacity)
        return PinnedStripArrangement(
            visible = visible,
            overflowCount = overflow.size,
            capacity = capacity,
            showOverflowIndicator = overflow.isNotEmpty() && capacity > 0 && !isEditSelected,
        )
    }
}
