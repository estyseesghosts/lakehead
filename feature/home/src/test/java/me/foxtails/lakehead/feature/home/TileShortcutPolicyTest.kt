package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileBinding
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for [TileShortcutPolicy]: bottom-edge capacity where the app
 * icon occupies exactly one slot (single-column tiles allow `rows - 1`, wider
 * tiles allow `columns - 1`).
 */
class TileShortcutPolicyTest {

    @Test
    fun entriesDerivePinStateAndDisableOnlyUnpinnedAtCapacity() {
        val first = ShortcutRef("com.example", ProfileId("p"), "first")
        val second = ShortcutRef("com.example", ProfileId("p"), "second")
        val entries = TileShortcutPolicy.entries(
            shortcuts = listOf(
                LauncherShortcut(first, "First", 0),
                LauncherShortcut(second, "Second", 1),
            ),
            binding = TileBinding(ItemId("tile"), listOf(first)),
            span = LogicalSpan(2, 1),
        )

        assertEquals(true, entries[0].isPinned)
        assertEquals(true, entries[0].isPinEnabled)
        assertEquals(false, entries[1].isPinned)
        assertEquals(false, entries[1].isPinEnabled)
    }

    @Test
    fun entriesAreIndependentPerTileBinding() {
        val ref = ShortcutRef("com.example", ProfileId("p"), "same")
        val shortcut = LauncherShortcut(ref, "Same", 0)
        val pinned = TileShortcutPolicy.entries(
            listOf(shortcut), TileBinding(ItemId("a"), listOf(ref)), LogicalSpan(2, 1),
        )
        val unpinned = TileShortcutPolicy.entries(
            listOf(shortcut), TileBinding(ItemId("b")), LogicalSpan(2, 1),
        )

        assertEquals(true, pinned.single().isPinned)
        assertEquals(false, unpinned.single().isPinned)
    }

    @Test
    fun singleColumn_allowsRowsMinusOne() {
        assertEquals(0, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(1, 1)))
        assertEquals(1, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(1, 2)))
        assertEquals(2, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(1, 3)))
    }

    @Test
    fun wideSpans_allowColumnsMinusOne() {
        assertEquals(1, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(2, 1)))
        assertEquals(1, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(2, 2)))
        assertEquals(2, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(3, 2)))
        assertEquals(4, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(5, 2)))
    }

    @Test
    fun gridSpanOverload_matchesLogicalSpan() {
        assertEquals(0, TileShortcutPolicy.maxShortcutSlots(GridSpan(1, 1)))
        assertEquals(1, TileShortcutPolicy.maxShortcutSlots(GridSpan(1, 2)))
        assertEquals(1, TileShortcutPolicy.maxShortcutSlots(GridSpan(2, 2)))
        assertEquals(2, TileShortcutPolicy.maxShortcutSlots(GridSpan(3, 2)))
    }

    @Test
    fun nonPositiveDimensions_coerceToZero() {
        assertEquals(0, TileShortcutPolicy.maxShortcutSlots(0, 2))
        assertEquals(0, TileShortcutPolicy.maxShortcutSlots(2, 0))
        assertEquals(0, TileShortcutPolicy.maxShortcutSlots(-1, 3))
    }
}
