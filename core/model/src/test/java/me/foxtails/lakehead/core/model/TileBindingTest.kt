package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileBindingTest {

    @Test
    fun twoTilesWithSameAppDivergeByTileId() {
        val shortcut = ShortcutRef("com.example", ProfileId("profile-a"), "shortcut-1")
        val tileA = TileBinding(tileId = ItemId("tile-a"), pinnedShortcuts = listOf(shortcut))
        val tileB = TileBinding(tileId = ItemId("tile-b"), pinnedShortcuts = listOf(shortcut))

        assertNotEquals(tileA, tileB)
        assertEquals(2, setOf(tileA, tileB).size)
    }

    @Test
    fun shortcutOrderIsSignificant() {
        val s1 = ShortcutRef("com.example", ProfileId("profile-a"), "shortcut-1")
        val s2 = ShortcutRef("com.example", ProfileId("profile-a"), "shortcut-2")
        val orderA = TileBinding(tileId = ItemId("tile-a"), pinnedShortcuts = listOf(s1, s2))
        val orderB = TileBinding(tileId = ItemId("tile-a"), pinnedShortcuts = listOf(s2, s1))

        assertNotEquals(orderA, orderB)
    }

    @Test
    fun defaultPinnedShortcutsIsEmpty() {
        val binding = TileBinding(tileId = ItemId("tile-a"))

        assertTrue(binding.pinnedShortcuts.isEmpty())
    }

    @Test
    fun tileIdDrivesIdentity() {
        val binding = TileBinding(tileId = ItemId("tile-a"))
        val copy = binding.copy()

        assertEquals(binding, copy)
        assertEquals(binding.hashCode(), copy.hashCode())
    }

    @Test
    fun fieldsContainNoFrameworkOrPixelTypes() {
        val allowedPrefixes = listOf(
            "java.lang.",
            "me.foxtails.lakehead.core.model.",
        )

        TileBinding::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .forEach { field ->
                val name = field.type.name
                assertTrue(
                    "TileBinding.${field.name} uses ${field.type.name}",
                    field.type.isPrimitive ||
                        field.type.isEnum ||
                        java.util.List::class.java.isAssignableFrom(field.type) ||
                        allowedPrefixes.any { name.startsWith(it) }
                )
            }
    }
}