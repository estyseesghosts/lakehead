package me.foxtails.lakehead

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityCatalogueLifecycleTest {
    @Test
    fun resumedActivityOwnsCatalogueThatPublishesAppsAndCanBeStopped() {
        runBlocking {
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                lateinit var catalogue: LauncherAppsAppCatalogue
                scenario.onActivity { activity -> catalogue = activity.appCatalogue }

                val apps = withTimeout(30_000) {
                    catalogue.apps.first { it.isNotEmpty() }
                }
                assertFalse("A resumed activity should expose a non-empty app inventory", apps.isEmpty())

                // Moving to CREATED invokes Activity.onStop; this checks the lifecycle path,
                // not independent observation of LauncherApps callback unregistration.
                scenario.moveToState(Lifecycle.State.CREATED)
            } finally {
                scenario.close()
            }
        }
    }
}
