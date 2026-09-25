package me.foxtails.lakehead.platform.launcherapps

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import me.foxtails.lakehead.core.model.LauncherAppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs under the instrumentation target/library-test UID, not a real launcher HOME UID. */
@RunWith(AndroidJUnit4::class)
class LauncherAppsAppCatalogueTest {
    @Test
    fun startAndStopAreIdempotentAndPublishDeviceInventoryWithLabels() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalogue = LauncherAppsAppCatalogue(context, scope)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                catalogue.start()
                catalogue.start()
            }
            val inventory = withTimeout(30_000) { catalogue.apps.first { it.isNotEmpty() } }
            assertTrue(inventory.isNotEmpty())
            // Confirm observers can consume the published process flow, not just a cached helper.
            val observed = withTimeout(5_000) { catalogue.apps.first { it.isNotEmpty() } }
            assertFalse(observed.isEmpty())
            assertTrue("Every entry must carry a non-blank label", inventory.all { it.label.isNotBlank() })
            assertTrue("Every entry must retain a non-blank component", inventory.all { it.identity.component.packageName.isNotBlank() })
            assertTrue("Every entry must retain a non-blank profile token", inventory.all { it.identity.profileId.value.isNotBlank() })
            assertEquals(
                "One component cannot appear twice with competing labels",
                inventory.size,
                inventory.map { it.identity }.distinct().size,
            )
            assertEquals(
                "Snapshots are deterministically ordered by profile, package and class",
                inventory.sortedWith(
                    compareBy(
                        { it.identity.profileId.value },
                        { it.identity.component.packageName },
                        { it.identity.component.className },
                    ),
                ),
                inventory,
            )
            assertTrue("Point-in-time reads expose the same snapshot contract", catalogue.cachedApps().isNotEmpty())
            assertPublishedEntriesAreFrameworkFree(inventory)
            val scopeJob = scope.coroutineContext[Job]!!
            scope.cancel()
            scopeJob.join()
            assertThrows(IllegalStateException::class.java) { catalogue.start() }
        } finally {
            instrumentation.runOnMainSync {
                catalogue.stop()
                catalogue.stop()
            }
            scope.cancel()
        }
        // Merely resolving the system service here documents the environment used by this test.
        assertTrue(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) != null)
    }

    /** Published entries must declare only framework-free fields: identity plus a plain String label. */
    private fun assertPublishedEntriesAreFrameworkFree(entries: List<LauncherAppEntry>) {
        assertEquals(
            me.foxtails.lakehead.core.model.AppIdentity::class.java,
            LauncherAppEntry::class.java.getDeclaredField("identity").type,
        )
        assertEquals(String::class.java, LauncherAppEntry::class.java.getDeclaredField("label").type)
        entries.forEach { entry ->
            assertTrue("Labels must be plain non-blank strings", entry.label.isNotBlank())
        }
    }
}
