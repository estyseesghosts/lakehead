package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShortcutTest {
    private val ref = ShortcutRef("com.example", ProfileId("personal"), "compose")

    @Test
    fun acceptsLabelRankAndRef() {
        val shortcut = LauncherShortcut(ref, "Compose", 2)
        assertEquals(ref, shortcut.ref)
        assertEquals("Compose", shortcut.label)
        assertEquals(2, shortcut.rank)
    }

    @Test
    fun rankOrdersByLowerFirst() {
        val ranked = listOf(
            LauncherShortcut(ref.copy(shortcutId = "c"), "C", 3),
            LauncherShortcut(ref.copy(shortcutId = "a"), "A", 0),
            LauncherShortcut(ref.copy(shortcutId = "b"), "B", 1),
        ).sortedBy { it.rank }
        assertEquals(listOf("a", "b", "c"), ranked.map { it.ref.shortcutId })
    }

    @Test
    fun blankLabelIsRejected() {
        try {
            LauncherShortcut(ref, "  ", 0)
            throw AssertionError("blank label must be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun negativeRankIsRejected() {
        try {
            LauncherShortcut(ref, "Compose", -1)
            throw AssertionError("negative rank must be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun distinctShortcutIdsAreDistinctRefs() {
        val first = LauncherShortcut(ref, "Compose", 0)
        val second = LauncherShortcut(ref.copy(shortcutId = "search"), "Search", 1)
        assertTrue(first != second)
        assertTrue(first.ref != second.ref)
    }
}
