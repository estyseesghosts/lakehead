package me.foxtails.lakehead.platform.shortcuts

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the refresh version fence used by the gateway commit. */
class RefreshVersionFenceTest {
    private val identity = AppIdentity(ProfileId("personal"), ComponentIdentity("com.example", "Main"))

    @Test
    fun currentVersionCommits() {
        val fence = RefreshVersionFence()
        val version = fence.next(identity)
        var commits = 0
        assertTrue(fence.tryCommit(identity, version) { commits++ })
        assertEquals(1, commits)
    }

    @Test
    fun olderCompletingAfterNewerStampFailsAndKeepsNewer() {
        val fence = RefreshVersionFence()
        val older = fence.next(identity)
        val newer = fence.next(identity)
        var commits = 0
        // The older query finishes after the newer stamp: it must not commit.
        assertFalse(fence.tryCommit(identity, older) { commits++ })
        assertEquals(0, commits)
        assertTrue(fence.tryCommit(identity, newer) { commits++ })
        assertEquals(1, commits)
    }

    @Test
    fun cancelledCommitIsSuppressed() {
        val fence = RefreshVersionFence()
        val version = fence.next(identity)
        var commits = 0
        assertFalse(fence.tryCommit(identity, version, isCancelled = { true }) { commits++ })
        assertEquals(0, commits)
        // The version is still current, so a non-cancelled retry commits.
        assertTrue(fence.tryCommit(identity, version) { commits++ })
        assertEquals(1, commits)
    }

    @Test
    fun invalidateFailsOutstandingStamp() {
        val fence = RefreshVersionFence()
        val stale = fence.next(identity)
        fence.invalidate(identity)
        var commits = 0
        assertFalse(fence.tryCommit(identity, stale) { commits++ })
        assertEquals(0, commits)
    }

    @Test
    fun invalidatePackageFailsOnlyThatPackageAndProfile() {
        val fence = RefreshVersionFence()
        val personal = AppIdentity(ProfileId("personal"), ComponentIdentity("com.gone", "Main"))
        val work = AppIdentity(ProfileId("work"), ComponentIdentity("com.gone", "Main"))
        val kept = AppIdentity(ProfileId("personal"), ComponentIdentity("com.kept", "Main"))
        val stalePersonal = fence.next(personal)
        val staleWork = fence.next(work)
        val currentKept = fence.next(kept)
        fence.invalidatePackage("com.gone", ProfileId("personal"))
        var commits = 0
        assertFalse(fence.tryCommit(personal, stalePersonal) { commits++ })
        assertTrue(fence.tryCommit(work, staleWork) { commits++ })
        assertTrue(fence.tryCommit(kept, currentKept) { commits++ })
        assertEquals(2, commits)
    }

    @Test
    fun invalidateAllFailsEveryOutstandingStamp() {
        val fence = RefreshVersionFence()
        val other = AppIdentity(ProfileId("work"), ComponentIdentity("com.other", "Main"))
        val stale = fence.next(identity)
        val staleOther = fence.next(other)
        fence.invalidateAll()
        var commits = 0
        assertFalse(fence.tryCommit(identity, stale) { commits++ })
        assertFalse(fence.tryCommit(other, staleOther) { commits++ })
        assertEquals(0, commits)
    }

    @Test
    fun abandonReleasesAndAllowsRetire() {
        val fence = RefreshVersionFence()
        val version = fence.next(identity)
        assertEquals(1, fence.outstandingCount(identity))
        fence.abandon(identity, version)
        assertEquals(0, fence.outstandingCount(identity))
        // Idle and not cached retires.
        fence.retire(identity, isCached = false)
        assertEquals(0, fence.size())
        // Retired stale still fails fail-closed.
        var commits = 0
        assertFalse(fence.tryCommit(identity, version) { commits++ })
        assertEquals(0, commits)
        // Fresh stamp after retire commits.
        val fresh = fence.next(identity)
        assertTrue(fence.tryCommit(identity, fresh) { commits++ })
        assertEquals(1, commits)
    }

    @Test
    fun retireKeepsCachedAndInFlight() {
        val fence = RefreshVersionFence()
        val version = fence.next(identity)
        // In-flight is retained even when not cached.
        fence.retire(identity, isCached = false)
        assertEquals(1, fence.size())
        // Cached idle is retained.
        fence.abandon(identity, version)
        fence.retire(identity, isCached = true)
        assertEquals(1, fence.size())
        // Idle non-cached retires.
        fence.retire(identity, isCached = false)
        assertEquals(0, fence.size())
    }

    @Test
    fun nextBoundsIdleAndPreservesInFlight() {
        val fence = RefreshVersionFence(maxEntries = 4)
        fun idFor(pkg: String) =
            AppIdentity(ProfileId("personal"), ComponentIdentity(pkg, "Main"))
        // Fill to bound with idle entries (commit immediately, no outstanding).
        (0 until 4).forEach { i ->
            val id = idFor("com.idle$i")
            val v = fence.next(id)
            var commits = 0
            assertTrue(fence.tryCommit(id, v) { commits++ })
        }
        assertEquals(4, fence.size())
        // Cold in-flight that must survive eviction pressure.
        val cold = idFor("com.cold")
        val coldVersion = fence.next(cold)
        // Bounded: inserting cold evicts the eldest idle, size stays at max.
        assertEquals(4, fence.size())
        // Pressure from new idle identities evicts eldest idle, never cold.
        (4 until 8).forEach { i ->
            val id = idFor("com.idle$i")
            val v = fence.next(id)
            var commits = 0
            assertTrue(fence.tryCommit(id, v) { commits++ })
        }
        // Cold still tracked: invalidation fails it, and without
        // invalidation it would still commit.
        assertEquals(1, fence.outstandingCount(cold))
        fence.invalidate(cold)
        var commits = 0
        assertFalse(fence.tryCommit(cold, coldVersion) { commits++ })
        assertEquals(0, commits)
    }

    @Test
    fun trimRetiresIdleNonCachedAndKeepsInFlight() {
        val fence = RefreshVersionFence()
        fun idFor(pkg: String) =
            AppIdentity(ProfileId("personal"), ComponentIdentity(pkg, "Main"))
        val idle = idFor("com.idle")
        val idleVersion = fence.next(idle)
        var commits = 0
        assertTrue(fence.tryCommit(idle, idleVersion) { commits++ })
        val cold = idFor("com.cold")
        fence.next(cold)
        fence.trim(setOf(idle))
        // Idle non-cached would retire, but idle is cached so kept; cold is
        // in-flight so kept even though not cached.
        assertEquals(2, fence.size())
        fence.trim(emptySet())
        // Idle non-cached retires, in-flight cold retained.
        assertEquals(1, fence.size())
        assertEquals(1, fence.outstandingCount(cold))
    }
}
