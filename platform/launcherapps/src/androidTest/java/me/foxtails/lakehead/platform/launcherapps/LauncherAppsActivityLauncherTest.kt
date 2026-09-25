package me.foxtails.lakehead.platform.launcherapps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises launch through the library-test UID, not through a configured launcher HOME. */
@RunWith(AndroidJUnit4::class)
class LauncherAppsActivityLauncherTest {
    @Test
    fun launchesEnumeratedComponentAndRejectsStaleProfileAndFakeComponent() {
        // This instrumentation test uses the target/library-test UID and proves only the
        // accessible profile(s) exposed to that UID; it does not prove work-profile launching.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val tokenAdapter = ProfileTokenAdapter(launcherApps, userManager)
        val identity = launcherApps.profiles.asSequence()
            .mapNotNull { user -> tokenAdapter.profileIdFor(user)?.let { it to user } }
            .flatMap { (profileId, user) ->
                launcherApps.getActivityList(null, user).asSequence().map { activity ->
                    AppIdentity(
                        profileId = profileId,
                        component = ComponentIdentity(
                            activity.componentName.packageName,
                            activity.componentName.className,
                        ),
                    )
                }
            }
            .firstOrNull()
        assertNotNull("An accessible profile should have a launchable activity", identity)

        val launcher = LauncherAppsActivityLauncher(context)
        assertTrue("An enumerated activity should launch in its own profile", launcher.launch(identity!!))

        val staleProfile = identity.copy(profileId = ProfileId("9223372036854775807"))
        assertFalse("A stale/inaccessible profile token must fail safely", launcher.launch(staleProfile))

        val fakeComponent = identity.copy(
            component = identity.component.copy(className = "${identity.component.className}.Missing"),
        )
        assertFalse("A component not enumerated for the profile must fail safely", launcher.launch(fakeComponent))
    }
}
