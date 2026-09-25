package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for [TileShortcutPolicy]: bottom-edge capacity where the app
 * icon occupies exactly one slot (single-column tiles allow `rows - 1`, wider
 * tiles allow `columns - 1`).
 */
class TileShortcutPolicyTest {

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
