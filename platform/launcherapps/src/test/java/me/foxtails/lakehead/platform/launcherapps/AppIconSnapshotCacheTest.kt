package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconSnapshotCacheTest {
    private val personal = ProfileId("personal")
    private val work = ProfileId("work")
    private val provenance = AppIconProvenance(sourceStamp = 1L, encoderVersion = 1)

    @Test
    fun sameComponentInDifferentProfilesHasDistinctSnapshots() {
        val personalSnapshot = icon(personal, "same.app", 1)
        val workSnapshot = icon(work, "same.app", 2)
        val cache = AppIconSnapshotCache()

        cache.put(personalSnapshot, provenance)
        cache.put(workSnapshot, provenance)

        assertEquals(personalSnapshot, cache[personalSnapshot.identity])
        assertEquals(workSnapshot, cache[workSnapshot.identity])
        assertEquals(2, cache.snapshot().size)
    }

    @Test
    fun retainOnlyDropsLeftIdentitiesAndKeepsTheOthersUntouched() {
        val removed = icon(personal, "removed.app", 1)
        val retained = icon(personal, "retained.app", 2)
        val cache = AppIconSnapshotCache()
        cache.put(removed, provenance)
        cache.put(retained, provenance)

        cache.retainOnly(setOf(retained.identity, identity(work, "never-cached.app")))

        assertNull(cache[removed.identity])
        assertSame(retained, cache[retained.identity])
        assertEquals(listOf(retained.identity), cache.snapshot().keys.toList())
    }

    @Test
    fun putReplacesSnapshotAndProvenanceOfOneIdentity() {
        val old = icon(personal, "a.app", 1)
        val fresh = icon(personal, "a.app", 3)
        val cache = AppIconSnapshotCache()
        cache.put(old, provenance)

        val newer = AppIconProvenance(sourceStamp = 2L, encoderVersion = 1)
        cache.put(fresh, newer)

        assertSame(fresh, cache[fresh.identity])
        assertEquals(newer, cache.provenanceOf(fresh.identity))
        assertNull(cache.provenanceOf(identity(work, "absent.app")))
    }

    @Test
    fun retainsEveryEntryOfALargeCatalogue() {
        val all = (0 until 500).map { icon(personal, "app$it", it) }
        val cache = AppIconSnapshotCache()

        all.forEach { cache.put(it, provenance) }

        assertEquals(500, cache.snapshot().size)
        assertEquals(all.map { it.identity }, cache.snapshot().keys.toList())
    }

    @Test
    fun snapshotsAndByteArraysAreDefensiveAndReadOnly() {
        val inputBytes = byteArrayOf(1, 2, 3)
        val snapshot = AppIconSnapshot(identity(personal, "immutable.app"), inputBytes)
        inputBytes[0] = 99
        val exposedBytes = snapshot.pngBytes
        exposedBytes[1] = 99
        assertEquals(listOf<Byte>(1, 2, 3), snapshot.pngBytes.toList())

        val cache = AppIconSnapshotCache()
        cache.put(snapshot, provenance)
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
    fun invalidateRemovesAllEntriesAndAllowsSubsequentPuts() {
        val first = icon(personal, "first.app", 1)
        val second = icon(work, "second.app", 2)
        val cache = AppIconSnapshotCache()
        cache.put(first, provenance)
        cache.put(second, provenance)

        cache.invalidate()

        assertTrue(cache.snapshot().isEmpty())
        assertNull(cache[first.identity])
        cache.put(second, provenance)
        assertEquals(second, cache[second.identity])
    }

    private fun icon(profileId: ProfileId, packageName: String, byte: Int) =
        AppIconSnapshot(identity(profileId, packageName), byteArrayOf(byte.toByte()))

    private fun identity(profileId: ProfileId, packageName: String) =
        AppIdentity(profileId, ComponentIdentity(packageName, "Main"))
}
