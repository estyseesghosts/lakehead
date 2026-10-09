package me.foxtails.lakehead.platform.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCachePolicyTest {
    @Test
    fun keyIsStableAndFilesystemSafe() {
        val key = photoKeyFor("0|com.chat|1|null|10001", 42L)
        assertEquals(key, photoKeyFor("0|com.chat|1|null|10001", 42L))
        assertNotEquals(key, photoKeyFor("0|com.chat|1|null|10001", 43L))
        assertTrue(key.all { it.isLetterOrDigit() })
    }

    @Test
    fun orphansAreAlwaysDeleted() {
        val cached = listOf(CachedPhoto("a", 1), CachedPhoto("b", 2))
        assertEquals(listOf("b"), photosToDelete(cached, referenced = setOf("a"), maxFiles = 10))
    }

    @Test
    fun oldestReferencedDroppedBeyondBound() {
        val cached = listOf(CachedPhoto("old", 1), CachedPhoto("mid", 2), CachedPhoto("new", 3))
        val dropped = photosToDelete(cached, referenced = setOf("old", "mid", "new"), maxFiles = 2)
        assertEquals(listOf("old"), dropped)
    }

    @Test
    fun nothingToDeleteWhenWithinBound() {
        val cached = listOf(CachedPhoto("a", 1))
        assertEquals(emptyList<String>(), photosToDelete(cached, setOf("a"), 5))
    }
}
