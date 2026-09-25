package me.foxtails.lakehead

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises package mutation through Android's real install/remove broadcasts. */
@RunWith(AndroidJUnit4::class)
class MainActivityPackageMutationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun installedAndRemovedFixtureUpdatesCatalogueWithoutReplacingActivity() {
        val activity = composeRule.activity
        val catalogue = activity.appCatalogue

        try {
            assertFixtureApkPresent()
            uninstallFixture()
            awaitFixtureAbsent(catalogue)
            assertFixtureRowAbsent("Fixture row must be absent before installation")
            assertActivityStarted(activity)

            runShell("pm install --user 0 -r $FIXTURE_APK_PATH").also { output ->
                assertTrue("Fixture install failed: $output", output.contains("Success"))
            }

            val fixtureEntry = awaitFixturePresent(catalogue)
            assertEquals(FIXTURE_PACKAGE, fixtureEntry.identity.component.packageName)
            assertEquals(FIXTURE_ACTIVITY_CLASS, fixtureEntry.identity.component.className)
            assertActivityStarted(activity)

            assertFixtureRowAbsent("Catalogue updates remain metadata-only on Home")
            assertSame(activity, composeRule.activity)
            assertActivityNotDestroyed(activity)

            uninstallFixture()
            awaitFixtureAbsent(catalogue)
            assertFixtureRowAbsent("Fixture row must disappear after package uninstall")
            assertSame(activity, composeRule.activity)
            assertActivityNotDestroyed(activity)
        } finally {
            uninstallFixture()
        }
    }

    private fun awaitFixtureAbsent(catalogue: LauncherAppsAppCatalogue) {
        try {
            runBlocking {
                withTimeout(MUTATION_TIMEOUT_MS) {
                    catalogue.apps.first { entries ->
                        entries.isNotEmpty() && entries.none { it.label == FIXTURE_LABEL }
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for fixture row to disappear", timeout)
        }
    }

    private fun awaitFixturePresent(
        catalogue: LauncherAppsAppCatalogue,
    ) = try {
        runBlocking {
            withTimeout(MUTATION_TIMEOUT_MS) {
                catalogue.apps.first { entries ->
                    entries.filter { it.label == FIXTURE_LABEL }.size == 1
                }.single { it.label == FIXTURE_LABEL }
            }
        }
    } catch (timeout: TimeoutCancellationException) {
        val packageState = runShell("pm list packages --user 0 $FIXTURE_PACKAGE")
        throw AssertionError(
            "Timed out waiting for fixture row to appear; package manager state: $packageState",
            timeout,
        )
    }

    private fun assertFixtureRowAbsent(message: String) {
        composeRule.waitUntil(MUTATION_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(FIXTURE_LABEL).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(
            message,
            composeRule.onAllNodesWithText(FIXTURE_LABEL).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertFixtureApkPresent() {
        val output = runShell("ls $FIXTURE_APK_PATH")
        assertTrue(
            "Required fixture APK is missing from device at $FIXTURE_APK_PATH; build and push " +
                "the launchable fixture before running this test. Device output: $output",
            output.contains(FIXTURE_APK_PATH),
        )
    }

    private fun assertActivityStarted(activity: MainActivity) {
        assertTrue(
            "MainActivity should remain STARTED",
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        assertActivityNotDestroyed(activity)
    }

    private fun assertActivityNotDestroyed(activity: MainActivity) {
        assertFalse("MainActivity must not be destroyed", activity.isDestroyed)
        assertFalse("MainActivity must not be finishing", activity.isFinishing)
    }

    private fun uninstallFixture() {
        val output = runShell("pm uninstall --user 0 $FIXTURE_PACKAGE")
        assertTrue(
            "Fixture uninstall failed: $output",
            output.contains("Success") || output.contains("not installed", ignoreCase = true),
        )
    }

    private fun runShell(command: String): String {
        val automation: UiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor: ParcelFileDescriptor = automation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    private companion object {
        const val FIXTURE_PACKAGE = "me.foxtails.lakehead.fixture"
        const val FIXTURE_ACTIVITY_CLASS = "android.app.Activity"
        const val FIXTURE_LABEL = "ProtoLaunch A3 Fixture"
        const val FIXTURE_APK_PATH = "/data/local/tmp/protolauch-a3-fixture.apk"
        const val MUTATION_TIMEOUT_MS = 60_000L
    }
}
