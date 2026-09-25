package me.foxtails.lakehead.platform.launcherapps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherAppsInvalidationCallbackTest {
    @Test
    fun eachPackageAvailabilityAndSuspensionEventInvalidatesExactlyOnce() {
        // Instrumentation runs as its target UID; accessible profiles can differ from the app's
        // eventual launcher UID, and a managed work profile is not guaranteed to exist.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val profile: UserHandle = launcherApps.profiles.firstOrNull()
            ?: throw AssertionError("LauncherApps should expose the calling profile")
        var invalidations = 0
        val callback = LauncherAppsInvalidationCallback { invalidations++ }

        val events = listOf<() -> Unit>(
            { callback.onPackageAdded("example.added", profile) },
            { callback.onPackageRemoved("example.removed", profile) },
            { callback.onPackageChanged("example.changed", profile) },
            { callback.onPackagesAvailable(arrayOf("example.one", "example.two"), profile, false) },
            { callback.onPackagesUnavailable(arrayOf("example.one", "example.two"), profile, false) },
            { callback.onPackagesSuspended(arrayOf("example.one", "example.two"), profile) },
            { callback.onPackagesSuspended(arrayOf("example.one", "example.two"), profile, android.os.Bundle.EMPTY) },
            { callback.onPackagesUnsuspended(arrayOf("example.one", "example.two"), profile) },
        )

        events.forEachIndexed { index, invoke ->
            val before = invalidations
            invoke()
            assertEquals("Framework event $index must invalidate once", before + 1, invalidations)
        }
        assertEquals("Plural callbacks invalidate once, not once per package", events.size, invalidations)
        assertTrue(launcherApps.profiles.contains(profile))
    }
}
