package me.foxtails.lakehead.platform.launcherapps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.text.Collator
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LauncherActivityIdentityEnumeratorTest {
    @Test
    fun enumeratesStableEntriesWithFrameworkLabelsAndRetainedIdentityForAccessibleProfiles() {
        // This library test executes under the instrumentation target UID, not ProtoLaunch's UID.
        // It makes no assumption that a managed work profile exists on the connected device.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val tokens = ProfileTokenAdapter(launcherApps, userManager)
        val enumerator = LauncherActivityIdentityEnumerator(launcherApps, tokens)
        val profiles = launcherApps.profiles
        assertTrue("LauncherApps should expose the calling profile", profiles.isNotEmpty())

        profiles.forEach { handle ->
            val profileId = tokens.profileIdFor(handle)
            assertNotNull("Accessible profile must resolve to a token", profileId)
            val id = profileId!!
            val expectedLabelsByComponent = expectedLabels(launcherApps, handle)
            val expectedHasActivities = expectedLabelsByComponent.isNotEmpty()
            val first = enumerator.enumerate(id)
            val second = enumerator.enumerate(id)

            if (expectedHasActivities) assertTrue("Activities should enumerate for $id", first.isNotEmpty())
            assertEquals("Repeated calls should return identical entries and labels", first, second)
            assertEquals("Identities must be duplicate-free", first.map { it.identity }.distinct(), first.map { it.identity })
            val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
            assertEquals(
                "Entries should be ordered by label then package and class",
                first.sortedWith(
                    compareBy<LauncherAppEntry, String>(collator) { it.label }
                        .thenBy { it.identity.component.packageName }
                        .thenBy { it.identity.component.className },
                ),
                first,
            )
            assertTrue("Every entry retains its source profile token", first.all { it.identity.profileId == id })
            first.forEach { entry ->
                assertTrue("Labels must be non-blank", entry.label.isNotBlank())
                assertEquals(id, entry.identity.profileId)
                assertTrue(entry.identity.component.packageName.isNotBlank())
                assertTrue(entry.identity.component.className.isNotBlank())
                val expectedLabels = expectedLabelsByComponent[entry.identity.component]
                assertNotNull("Enumerated component must exist in the framework activity list", expectedLabels)
                assertTrue(
                    "Label must be the trimmed framework label or the package-name fallback: ${entry.label}",
                    expectedLabels!!.contains(entry.label),
                )
                assertExpectedDomainEntryShape(entry)
            }
        }
    }

    /**
     * Expected label per component: the trimmed framework label, or the package name when that
     * label is blank. Duplicate activities for one component may contribute several candidates.
     */
    private fun expectedLabels(launcherApps: LauncherApps, handle: android.os.UserHandle) =
        launcherApps.getActivityList(null, handle)
            .map { activity ->
                val component = activity.componentName
                val trimmedLabel = activity.label?.toString()?.trim().orEmpty()
                ComponentIdentity(component.packageName, component.className) to
                    (trimmedLabel.ifEmpty { component.packageName })
            }
            .groupBy({ it.first }, { it.second })

    private fun assertExpectedDomainEntryShape(entry: LauncherAppEntry) {
        assertEquals(LauncherAppEntry::class.java, entry.javaClass)
        assertEquals(AppIdentity::class.java, entry.identity.javaClass)
        assertEquals(ProfileId::class.java, entry.identity.profileId.javaClass)
        assertEquals(ComponentIdentity::class.java, entry.identity.component.javaClass)
        // The entry must retain only framework-free values: identity plus a plain String label.
        assertEquals(AppIdentity::class.java, LauncherAppEntry::class.java.getDeclaredField("identity").type)
        assertEquals(String::class.java, LauncherAppEntry::class.java.getDeclaredField("label").type)
        assertEquals(String::class.java, AppIdentity::class.java.getDeclaredField("profileId").type)
        assertEquals(
            ComponentIdentity::class.java,
            AppIdentity::class.java.getDeclaredField("component").type,
        )
        assertEquals(String::class.java, entry.identity.component.javaClass.getDeclaredField("packageName").type)
        assertEquals(String::class.java, entry.identity.component.javaClass.getDeclaredField("className").type)
    }
}
