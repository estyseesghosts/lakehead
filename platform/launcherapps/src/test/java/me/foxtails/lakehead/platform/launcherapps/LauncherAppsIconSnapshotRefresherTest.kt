package me.foxtails.lakehead.platform.launcherapps

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class LauncherAppsIconSnapshotRefresherTest {
    @Test
    fun sameComponentInDifferentProfilesGetsSeparateSnapshots() = runTest {
        val personal = entry("0")
        val work = entry("10")
        val cache = AppIconSnapshotCache()
        val refresher = refresher(cache) { identity -> TestIcon(identity.profileId.value.toByte()) }

        refresher.refresh(listOf(personal, work))

        assertEquals(setOf(personal.identity, work.identity), cache.snapshot().keys)
        assertEquals(0, cache[personal.identity]?.pngBytes?.single()?.toInt())
        assertEquals(10, cache[work.identity]?.pngBytes?.single()?.toInt())
    }

    @Test
    fun missingAndFailedIconsAreOmittedWhileSuccessfulIconsRemainUsable() = runTest {
        val present = entry("0", "present")
        val missing = entry("0", "missing")
        val failed = entry("0", "failed")
        val cache = AppIconSnapshotCache()
        val refresher = LauncherAppsIconSnapshotRefresher(
            lookup = LauncherAppIconLookup { identity ->
                when (identity.component.className) {
                    "present" -> TestIcon(1)
                    "missing" -> null
                    else -> TestIcon(2)
                }
            },
            encoder = LauncherAppIconPngEncoder { icon ->
                if ((icon as TestIcon).value == 2.toByte()) error("broken drawable")
                byteArrayOf(1)
            },
            cache = cache,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        refresher.refresh(listOf(present, missing, failed))

        assertNotNull(cache[present.identity])
        assertNull(cache[missing.identity])
        assertNull(cache[failed.identity])
    }

    @Test
    fun refreshRunsLookupAndEncodingOnInjectedDispatcher() = runTest {
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "icon-io-test")
        }.asCoroutineDispatcher()
        val cache = AppIconSnapshotCache()
        val checks = mutableListOf<Boolean>()
        fun checkDispatcher() {
            checks += Thread.currentThread().name == "icon-io-test"
        }
        val refresher = LauncherAppsIconSnapshotRefresher(
            lookup = LauncherAppIconLookup {
                checkDispatcher()
                TestIcon(1)
            },
            encoder = LauncherAppIconPngEncoder {
                checkDispatcher()
                byteArrayOf(1)
            },
            cache = cache,
            ioDispatcher = dispatcher,
        )

        refresher.refresh(listOf(entry("0")))
        dispatcher.close()

        assertEquals(listOf(true, true), checks)
    }

    @Test
    fun replacementRemovesNoLongerPresentAndFailedOldSnapshots() = runTest {
        val retained = entry("0", "retained")
        val removed = entry("0", "removed")
        val cache = AppIconSnapshotCache()
        val available = mutableSetOf(retained.identity, removed.identity)
        val refresher = refresher(cache) { identity ->
            if (identity in available) TestIcon(1) else null
        }
        refresher.refresh(listOf(retained, removed))
        assertEquals(2, cache.snapshot().size)

        available.clear()
        available.add(retained.identity)
        refresher.refresh(listOf(retained, entry("0", "new-missing")))

        assertEquals(setOf(retained.identity), cache.snapshot().keys)
    }

    @Test
    fun oversizedEncoderOutputIsRejectedAndSnapshotBytesStayBounded() = runTest {
        val accepted = entry("0", "accepted")
        val oversized = entry("0", "oversized")
        val cache = AppIconSnapshotCache()
        val identityLookup = LauncherAppsIconSnapshotRefresher(
            lookup = LauncherAppIconLookup { identity ->
                TestIcon(if (identity == oversized.identity) 2 else 1)
            },
            encoder = LauncherAppIconPngEncoder { icon ->
                if ((icon as TestIcon).value == 2.toByte()) {
                    ByteArray(AppIconSnapshot.MAX_PNG_BYTES + 1)
                } else byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
            },
            cache = cache,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        identityLookup.refresh(listOf(accepted, oversized))

        assertEquals(setOf(accepted.identity), cache.snapshot().keys)
        assertTrue(cache[accepted.identity]!!.pngBytes.size <= AppIconSnapshot.MAX_PNG_BYTES)
        assertFalse(cache[accepted.identity]!!.pngBytes.isEmpty())
    }

    @Test
    fun refreshOfALargeCatalogueCachesEveryEntry() = runTest {
        val entries = (0 until 500).map { entry("0", "app$it") }
        val cache = AppIconSnapshotCache()
        val refresher = refresher(cache) { TestIcon(1) }

        refresher.refresh(entries)

        assertEquals(entries.map { it.identity }.toSet(), cache.snapshot().keys)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun refresher(
        cache: AppIconSnapshotCache,
        iconFor: (AppIdentity) -> LauncherAppIcon?,
    ) = LauncherAppsIconSnapshotRefresher(
        lookup = LauncherAppIconLookup(iconFor),
        encoder = LauncherAppIconPngEncoder { byteArrayOf((it as TestIcon).value) },
        cache = cache,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun entry(profile: String, component: String = "shared") = LauncherAppEntry(
        identity = AppIdentity(
            profileId = ProfileId(profile),
            component = ComponentIdentity("example.package", component),
        ),
        label = "Unchanged label",
    )

    private data class TestIcon(val value: Byte) : LauncherAppIcon {
        constructor(value: Int) : this(value.toByte())
    }
}
