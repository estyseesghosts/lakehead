package me.foxtails.lakehead

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsActivityLauncher
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real LauncherApps inventory and launch from ProtoLaunch's target UID. */
@RunWith(AndroidJUnit4::class)
class LauncherAppsAppLifecycleTest {
    @Test
    fun catalogueStartsStopsAndLaunchesAnEnumeratedActivity() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(
            "Instrumentation must execute ProtoLaunch's target UID",
            context.applicationInfo.uid,
            Process.myUid(),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val catalogue = LauncherAppsAppCatalogue(context.applicationContext, scope)
        try {
            instrumentation.runOnMainSync {
                catalogue.start()
                catalogue.start()
            }

            val identity: AppIdentity = withTimeout(30_000) {
                catalogue.apps.first { it.isNotEmpty() }.first().identity
            }

            instrumentation.runOnMainSync {
                catalogue.stop()
                catalogue.stop()
            }

            val launcher = LauncherAppsActivityLauncher(context.applicationContext)
            assertTrue("An enumerated activity should launch", launcher.launch(identity))

            val fakeComponent = identity.copy(
                component = identity.component.copy(
                    className = "${identity.component.className}.Missing",
                ),
            )
            assertFalse("A missing activity should not launch", launcher.launch(fakeComponent))
        } finally {
            instrumentation.runOnMainSync { catalogue.stop() }
            scope.cancel()
        }
    }
}
