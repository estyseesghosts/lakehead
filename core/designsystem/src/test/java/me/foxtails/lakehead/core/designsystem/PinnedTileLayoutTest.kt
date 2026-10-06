package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the C1 tile-layout rules: the large-span shared rule, the
 * pinned-shortcut presentation model and its accessible names.
 */
class PinnedTileLayoutTest {

    // ── isLargeTileSpan ─────────────────────────────────────────────

    @Test
    fun compactSpansAreNotLarge() {
        assertFalse(isLargeTileSpan(GridSpan(1, 1)))
        assertFalse(isLargeTileSpan(GridSpan(1, 2)))
        assertFalse(isLargeTileSpan(GridSpan(2, 1)))
    }

    @Test
    fun squareAndWideSpansAreLarge() {
        assertTrue(isLargeTileSpan(GridSpan(2, 2)))
        assertTrue(isLargeTileSpan(GridSpan(3, 1)))
        assertTrue(isLargeTileSpan(GridSpan(4, 2)))
        assertTrue(isLargeTileSpan(GridSpan(3, 3)))
        assertTrue(isLargeTileSpan(GridSpan(2, 4)))
    }

    @Test
    fun tallNarrowSpansAreLarge() {
        assertTrue(isLargeTileSpan(GridSpan(1, 3)))
        assertTrue(isLargeTileSpan(GridSpan(1, 4)))
    }

    // ── PinnedTileShortcut ──────────────────────────────────────────

    @Test
    fun pinnedShortcutKeepsIdAndLabel() {
        val pinned = PinnedTileShortcut(id = "new-post", label = "New post")
        assertEquals("new-post", pinned.id)
        assertEquals("New post", pinned.label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pinnedShortcutRejectsBlankId() {
        PinnedTileShortcut(id = "  ", label = "New post")
    }

    @Test(expected = IllegalArgumentException::class)
    fun pinnedShortcutRejectsBlankLabel() {
        PinnedTileShortcut(id = "new-post", label = "  ")
    }

    // ── accessible names ────────────────────────────────────────────

    @Test
    fun pinnedShortcutDescriptionSuffixesShortcut() {
        assertEquals("New post, shortcut", pinnedShortcutDescription("New post"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pinnedShortcutDescriptionRejectsBlankLabel() {
        pinnedShortcutDescription("  ")
    }

    @Test
    fun overflowDescriptionCountsHidden() {
        assertEquals("1 more shortcuts", pinnedOverflowDescription(1))
        assertEquals("3 more shortcuts", pinnedOverflowDescription(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun overflowDescriptionRejectsZero() {
        pinnedOverflowDescription(0)
    }
}
