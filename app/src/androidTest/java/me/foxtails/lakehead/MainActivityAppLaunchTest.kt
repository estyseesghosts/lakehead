package me.foxtails.lakehead

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.lakehead.core.model.LauncherAppEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies catalogue metadata is not implicitly converted to persisted Home placements. */
@RunWith(AndroidJUnit4::class)
class MainActivityAppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun catalogueEntryDoesNotBecomeAHomeTileWithoutPersistedPlacement() {
        val entries: List<LauncherAppEntry> = runBlocking {
            withTimeout(30_000) {
                composeRule.activity.appCatalogue.apps.first { apps ->
                    apps.any { candidate -> apps.count { it.label == candidate.label } == 1 }
                }
            }
        }
        val entry = entries
            .sortedWith(
                compareBy<LauncherAppEntry>(
                    { it.identity.profileId.value },
                    { it.identity.component.packageName },
                    { it.identity.component.className },
                ),
            )
            .first { candidate -> entries.count { it.label == candidate.label } == 1 }

        composeRule.waitForIdle()
        assertTrue(
            "Catalogue-only app must not appear on the persisted Home canvas",
            composeRule.onAllNodesWithText(entry.label).fetchSemanticsNodes().isEmpty(),
        )
    }
}
