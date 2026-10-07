package me.foxtails.lakehead

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.feature.allapps.ALPHABET_PICKER_OVERLAY_TAG
import me.foxtails.lakehead.feature.allapps.AllAppsEntry
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.platform.feed.FeedAvailability
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level evidence for the P4 shell wiring: a header tap opens the alphabet
 * picker through the production [LauncherShell] path, and pager swipe is held while
 * the picker is open.
 *
 * - Header tap on "B" shows [ALPHABET_PICKER_OVERLAY_TAG] and reports "B".
 * - A swipe toward Home while open leaves the picker up (the shell sets
 *   `userScrollEnabled = false` while `picker?.isOpen`); without the block the swipe
 *   would page to Home and the overlay would vanish.
 */
@RunWith(AndroidJUnit4::class)
class LauncherShellPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(label: String, pkg: String) = AllAppsEntry(
        identity = AppIdentity(ProfileId("0"), ComponentIdentity(pkg, "$pkg.Main")),
        label = label,
        alreadyOnHome = false,
    )

    @Test
    fun headerTap_opensPicker_andSwipeStaysOnAllApps() {
        val entries = listOf(
            entry("Apple", "com.example.apple"),
            entry("Banana", "com.example.banana"),
        )
        var headerTapped: String? = null

        composeRule.setContent {
            LauncherShell(
                homeState = HomeRenderState.Loaded(
                    requestedHomeId = HomeId("home-test"),
                    tiles = emptyList(),
                    columns = GridColumns(6),
                    extent = GridVerticalExtent(4L, 10L),
                ),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(entries),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = FeedAvailability.Unavailable(
                    FeedAvailability.Unavailable.Reason.NO_PROVIDER,
                ),
                onHeaderTap = { headerTapped = it },
                initialPage = LAUNCHER_PAGE_ALL_APPS,
            )
        }
        composeRule.waitForIdle()

        // Closed: bare All Apps list, no overlay.
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()

        // Header tap opens the picker via the shell's production path.
        composeRule.onNodeWithContentDescription("B, application section").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()
        assertEquals("B", headerTapped)

        // Swipe toward Home while open must not page away: picker stays up.
        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()
    }
}
