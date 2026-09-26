package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.ShortcutStripGeometry
import me.foxtails.lakehead.core.model.ShortcutRef

/**
 * Pure shortcut ordering logic for a single tile's pinned shortcut list.
 *
 * All functions are side-effect-free and framework-free. The full pinned list
 * is the source of truth; capacity ([ShortcutStripGeometry.maxSlots]) determines
 * which items are visible. Overflow items are retained and become visible when
 * capacity increases (e.g. on resize).
 */
object ShortcutOrdering {

    /**
     * Presentation layout style for visible shortcuts on a tile.
     *
     * [CENTERED]: visible shortcuts are centered along the tile's shortcut strip edge.
     * [APP_WITH_PINNED]: the app icon occupies one slot in the strip, remaining
     *   slots show pinned shortcuts.
     */
    enum class Layout { CENTERED, APP_WITH_PINNED }

    /**
     * Derives the visible subset of [fullList] for the given [capacity].
     *
     * Returns at most [capacity] items from the front of [fullList]; remaining
     * items are overflow and not rendered. A [capacity] of 0 always returns
     * an empty list regardless of [fullList] size.
     */
    fun visibleShortcuts(fullList: List<ShortcutRef>, capacity: Int): List<ShortcutRef> {
        require(capacity >= 0) { "capacity must be non-negative" }
        return fullList.take(capacity)
    }

    /**
     * Derives the overflow (hidden) subset of [fullList] for the given [capacity].
     *
     * Returns items beyond the first [capacity]. An empty list when all items fit.
     */
    fun overflowShortcuts(fullList: List<ShortcutRef>, capacity: Int): List<ShortcutRef> {
        require(capacity >= 0) { "capacity must be non-negative" }
        return fullList.drop(capacity)
    }

    /**
     * Appends [shortcut] to the full list, returning the new list.
     *
     * Returns `null` when [fullList] already has [capacity] or more items
     * (at-capacity: cannot pin more). The caller should not persist when null
     * is returned. Duplicate entries are permitted (the caller may deduplicate
     * before calling).
     */
    fun pin(
        fullList: List<ShortcutRef>,
        shortcut: ShortcutRef,
        capacity: Int,
    ): List<ShortcutRef>? {
        require(capacity >= 0) { "capacity must be non-negative" }
        if (fullList.size >= capacity) return null
        return fullList + shortcut
    }

    /**
     * Removes [shortcut] from [fullList] and collapses overflow into visible.
     *
     * Returns the updated list (shorter by one, or unchanged if [shortcut] is
     * not found). Because [visibleShortcuts] derives visible from the front,
     * removing any item naturally lets the next overflow item fill the gap.
     */
    fun unpin(fullList: List<ShortcutRef>, shortcut: ShortcutRef): List<ShortcutRef> {
        val index = fullList.indexOf(shortcut)
        if (index < 0) return fullList
        return fullList.toMutableList().apply { removeAt(index) }
    }

    /**
     * `true` when removing [shortcut] from [fullList] with the given [capacity]
     * would leave the visible set empty.
     *
     * This is the case when [fullList] has exactly one item (which must be
     * [shortcut]) or when [fullList] has [capacity] items and [shortcut] is
     * in the visible prefix — meaning after removal the list shrinks below
     * capacity but the new visible count becomes `capacity - 1` or less.
     *
     * Simplified: the visible set is empty after unpin iff the full list has
     * at most 1 item, or the remaining list has 0 items.
     */
    fun isLastUnpin(fullList: List<ShortcutRef>, shortcut: ShortcutRef, capacity: Int): Boolean {
        if (capacity <= 0) return true
        if (fullList.size <= 1) return true
        val remaining = unpin(fullList, shortcut)
        return remaining.isEmpty()
    }

    /**
     * Derives the visible list after a resize, preserving the full list.
     *
     * The full list is unchanged; only the visible window changes based on
     * the new [capacity]. Returns the new visible subset.
     */
    fun resize(
        fullList: List<ShortcutRef>,
        oldCapacity: Int,
        newCapacity: Int,
    ): List<ShortcutRef> {
        require(oldCapacity >= 0) { "oldCapacity must be non-negative" }
        require(newCapacity >= 0) { "newCapacity must be non-negative" }
        return visibleShortcuts(fullList, newCapacity)
    }

    /**
     * Restores the full pinned list from a persisted source.
     *
     * Returns the visible subset for the given [capacity]. This is the
     * cold-start equivalent of [visibleShortcuts] — the full list is loaded
     * from persistence and only the visible prefix is rendered.
     */
    fun restore(
        fullList: List<ShortcutRef>,
        capacity: Int,
    ): List<ShortcutRef> = visibleShortcuts(fullList, capacity)

    /**
     * Lays out [visible] shortcuts according to the given [layout] style.
     *
     * Returns the ordered list of shortcuts to render. For [Layout.CENTERED],
     * the visible list is returned as-is (centering is a visual offset handled
     * by the renderer). For [Layout.APP_WITH_PINNED], the visible list is
     * returned as-is (the app icon is prepended by the renderer, occupying
     * the first slot).
     *
     * Both layouts return the same list; the difference is how the renderer
     * positions them relative to the tile's edge and the app icon.
     */
    fun layoutShortcuts(visible: List<ShortcutRef>, layout: Layout): List<ShortcutRef> {
        // Pure pass-through: both layouts render the same ordered list.
        // Positioning is a presentation concern outside this pure domain.
        return visible
    }
}