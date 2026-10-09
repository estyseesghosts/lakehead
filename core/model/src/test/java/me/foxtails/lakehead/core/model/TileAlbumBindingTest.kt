package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TileAlbumBindingTest {

    @Test
    fun blankBucketIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { AlbumRef("", "Camera") }
    }

    @Test
    fun blankAlbumNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { AlbumRef("123", "  ") }
    }

    @Test
    fun twoTilesShowingTheSameAlbumStayDistinct() {
        val album = AlbumRef("123", "Camera")
        val tileA = TileAlbumBinding(tileId = ItemId("tile-a"), album = album)
        val tileB = TileAlbumBinding(tileId = ItemId("tile-b"), album = album)

        assertNotEquals(tileA, tileB)
        assertEquals(album, tileA.album)
    }

    @Test
    fun fieldsContainNoFrameworkOrPixelTypes() {
        val allowedPrefixes = listOf(
            "java.lang.",
            "me.foxtails.lakehead.core.model.",
        )
        val fields = AlbumRef::class.java.declaredFields + TileAlbumBinding::class.java.declaredFields

        fields.filterNot { it.isSynthetic }.forEach { field ->
            val name = field.type.name
            assertTrue(
                "${field.declaringClass.simpleName}.${field.name} uses ${field.type.name}",
                field.type.isPrimitive ||
                    field.type.isEnum ||
                    allowedPrefixes.any { name.startsWith(it) },
            )
        }
    }
}
