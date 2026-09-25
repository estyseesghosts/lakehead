package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconSnapshotCacheTest {
    private val personal = ProfileId("personal")
    private val work = ProfileId("work")

    @Test
    fun sameComponentInDifferentProfilesHasDistinctSnapshots() {
        val personalSnapshot = icon(personal, "same.app", 1)
        val workSnapshot = icon(work, "same.app", 2)
        val cache = AppIconSnapshotCache(maxEntries = 2)

        cache.replaceAll(listOf(personalSnapshot, workSnapshot))

        assertEquals(personalSnapshot, cache[personalSnapshot.identity])
        assertEquals(workSnapshot, cache[workSnapshot.identity])
        assertEquals(2, cache.snapshot().size)
    }

    @Test
    fun replaceAllReplacesValuesAndRemovesIdentitiesNotInCurrentSet() {
        val removed = icon(personal, "removed.app", 1)
        val retainedOld = icon(personal, "retained.app", 2)
        val retainedFresh = icon(personal, "retained.app", 3)
        val added = icon(work, "added.app", 4)
        val cache = AppIconSnapshotCache(maxEntries = 4)
        cache.replaceAll(listOf(removed, retainedOld))

        cache.replaceAll(listOf(retainedFresh, added))

        assertNull(cache[removed.identity])
        assertEquals(retainedFresh, cache[retainedFresh.identity])
        assertEquals(added, cache[added.identity])
        assertEquals(listOf(retainedFresh.identity, added.identity), cache.snapshot().keys.toList())
    }

    @Test
    fun evictionIsFifoAndDeterministicIncludingRepeatedIdentity() {
        val aOld = icon(personal, "a.app", 1)
        val b = icon(personal, "b.app", 2)
        val aNew = icon(personal, "a.app", 3)
        val c = icon(personal, "c.app", 4)
        val cache = AppIconSnapshotCache(maxEntries = 2)

        cache.replaceAll(listOf(aOld, b, aNew, c))

        assertEquals(listOf(aNew.identity, c.identity), cache.snapshot().keys.toList())
        assertNull(cache[b.identity])
        assertEquals(aNew, cache[aNew.identity])
    }

    @Test
    fun rejectsNonPositiveEntryLimits() {
        assertThrows(IllegalArgumentException::class.java) { AppIconSnapshotCache(0) }
        assertThrows(IllegalArgumentException::class.java) { AppIconSnapshotCache(-1) }
    }

    @Test
    fun snapshotsAndByteArraysAreDefensiveAndReadOnly() {
        val inputBytes = byteArrayOf(1, 2, 3)
        val snapshot = AppIconSnapshot(identity(personal, "immutable.app"), inputBytes)
        inputBytes[0] = 99
        val exposedBytes = snapshot.pngBytes
        exposedBytes[1] = 99
        assertEquals(listOf<Byte>(1, 2, 3), snapshot.pngBytes.toList())

        val cache = AppIconSnapshotCache(maxEntries = 1)
        cache.replaceAll(listOf(snapshot))
        val published = cache.snapshot()
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (published as MutableMap<AppIdentity, AppIconSnapshot>).clear()
        }
        cache.invalidate()
        assertTrue("Earlier snapshot remains detached", published.containsKey(snapshot.identity))
        assertEquals(1, published.size)
    }

    @Test
    fun snapshotRejectsEmptyOrOversizedEncodedImages() {
        assertThrows(IllegalArgumentException::class.java) {
            AppIconSnapshot(identity(personal, "empty.app"), byteArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppIconSnapshot(
                identity(personal, "large.app"),
                ByteArray(AppIconSnapshot.MAX_PNG_BYTES + 1),
            )
        }
    }

    @Test
    fun invalidateRemovesAllEntriesAndAllowsSubsequentReplacement() {
        val first = icon(personal, "first.app", 1)
        val second = icon(work, "second.app", 2)
        val cache = AppIconSnapshotCache(maxEntries = 2)
        cache.replaceAll(listOf(first, second))

        cache.invalidate()

        assertTrue(cache.snapshot().isEmpty())
        assertNull(cache[first.identity])
        cache.replaceAll(listOf(second))
        assertEquals(second, cache[second.identity])
    }

    private fun icon(profileId: ProfileId, packageName: String, byte: Int) =
        AppIconSnapshot(identity(profileId, packageName), byteArrayOf(byte.toByte()))

    private fun identity(profileId: ProfileId, packageName: String) =
        AppIdentity(profileId, ComponentIdentity(packageName, "Main"))
}
