package me.foxtails.lakehead.platform.shortcuts

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutSnapshotCacheTest {
    private val personal = ProfileId("personal")
    private val work = ProfileId("work")
    private fun identity(profile: ProfileId, pkg: String = "com.example") =
        AppIdentity(profile, ComponentIdentity(pkg, "Main"))

    private fun shortcut(pkg: String, profile: ProfileId, id: String, rank: Int = 0) =
        LauncherShortcut(ShortcutRef(pkg, profile, id), id, rank)

    @Test
    fun putGetAndSnapshotAreIsolatedPerIdentity() {
        val cache = ShortcutSnapshotCache()
        val personalIdentity = identity(personal)
        val workIdentity = identity(work)
        cache.put(personalIdentity, listOf(shortcut("com.example", personal, "a")))
        assertEquals(listOf("a"), cache.get(personalIdentity).map { it.ref.shortcutId })
        assertTrue(cache.get(workIdentity).isEmpty())
        assertEquals(setOf(personalIdentity), cache.snapshot().keys)
    }

    @Test
    fun samePackageInTwoProfilesKeepsSeparateLists() {
        val cache = ShortcutSnapshotCache()
        cache.put(identity(personal), listOf(shortcut("com.example", personal, "p")))
        cache.put(identity(work), listOf(shortcut("com.example", work, "w")))
        assertEquals(listOf("p"), cache.get(identity(personal)).map { it.ref.shortcutId })
        assertEquals(listOf("w"), cache.get(identity(work)).map { it.ref.shortcutId })
    }

    @Test
    fun publishedSnapshotsAreImmutable() {
        val cache = ShortcutSnapshotCache()
        val id = identity(personal)
        cache.put(id, listOf(shortcut("com.example", personal, "a")))
        try {
            (cache.snapshot() as MutableMap<*, *>).clear()
            throw AssertionError("snapshot must be immutable")
        } catch (_: UnsupportedOperationException) {
        }
    }

    @Test
    fun evictsLeastRecentlyUsedBeyondBound() {
        val cache = ShortcutSnapshotCache(maxEntries = 2)
        val first = identity(personal, "com.first")
        val second = identity(personal, "com.second")
        val third = identity(personal, "com.third")
        cache.put(first, listOf(shortcut("com.first", personal, "a")))
        cache.put(second, listOf(shortcut("com.second", personal, "b")))
        cache.put(third, listOf(shortcut("com.third", personal, "c")))
        assertEquals(2, cache.size())
        assertTrue(cache.get(first).isEmpty())
        assertEquals(listOf("b"), cache.get(second).map { it.ref.shortcutId })
        assertEquals(listOf("c"), cache.get(third).map { it.ref.shortcutId })
    }

    @Test
    fun identitiesForPackageAndRemovePackageArePackageScoped() {
        val cache = ShortcutSnapshotCache()
        cache.put(identity(personal, "com.gone"), listOf(shortcut("com.gone", personal, "a")))
        cache.put(identity(work, "com.gone"), listOf(shortcut("com.gone", work, "b")))
        cache.put(identity(personal, "com.kept"), listOf(shortcut("com.kept", personal, "c")))
        assertEquals(2, cache.identitiesForPackage("com.gone").size)
        cache.removePackage("com.gone")
        assertTrue(cache.identitiesForPackage("com.gone").isEmpty())
        assertEquals(listOf("c"), cache.get(identity(personal, "com.kept")).map { it.ref.shortcutId })
    }

    @Test
    fun removePackageForProfileKeepsSamePackageInOtherProfile() {
        val cache = ShortcutSnapshotCache()
        cache.put(identity(personal, "com.gone"), listOf(shortcut("com.gone", personal, "a")))
        cache.put(identity(work, "com.gone"), listOf(shortcut("com.gone", work, "b")))
        cache.removePackageForProfile("com.gone", personal)
        assertTrue(cache.get(identity(personal, "com.gone")).isEmpty())
        assertEquals(listOf("b"), cache.get(identity(work, "com.gone")).map { it.ref.shortcutId })
    }
}
