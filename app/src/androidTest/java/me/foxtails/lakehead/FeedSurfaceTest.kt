package me.foxtails.lakehead

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.platform.feed.FeedAvailability
import me.foxtails.lakehead.platform.feed.FeedProviderId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C3 shell-swap evidence for the [FeedSurface] host-boundary seam.
 *
 * - The [FEED_SURFACE_TAG] slot exists in both availability states.
 * - `Unavailable` keeps the honest "Feed unavailable" fallback text.
 * - `Available` hosts the provider slot with no fallback text.
 * - The shell keeps its 3-page pill (Feed/Home/All Apps) with a provider hosted.
 */
@RunWith(AndroidJUnit4::class)
class FeedSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val unavailable = FeedAvailability.Unavailable(
        FeedAvailability.Unavailable.Reason.NO_PROVIDER,
    )
    private val available = FeedAvailability.Available(FeedProviderId.Lawnfeed)

    @Test
    fun unavailable_showsFallbackTextInTaggedSlot() {
        composeRule.setContent {
            FeedSurface(availability = unavailable)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertExists()
        composeRule.onNodeWithText("Feed unavailable").assertIsDisplayed()
    }

    @Test
    fun available_hostsProviderSlotWithoutFallbackText() {
        composeRule.setContent {
            FeedSurface(availability = available)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertExists()
        composeRule.onNodeWithText("Feed unavailable").assertDoesNotExist()
    }

    @Test
    fun shell_availableFeed_keepsThreePagePill() {
        composeRule.setContent {
            LauncherShell(
                homeState = HomeRenderState.Loaded(
                    requestedHomeId = HomeId("home-test"),
                    tiles = emptyList(),
                    columns = GridColumns(6),
                    extent = GridVerticalExtent(4L, 10L),
                    window = GridRowWindow(0L, 10L),
                ),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = available,
                initialPage = LAUNCHER_PAGE_FEED,
            )
        }
        composeRule.waitForIdle()

        // Provider page hosts the slot, not the fallback ...
        composeRule.onNodeWithTag(FEED_SURFACE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Feed unavailable").assertDoesNotExist()
        // ... and the 3-page pill is unchanged.
        composeRule.onNodeWithContentDescription("Feed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("All Apps").assertIsDisplayed()
    }
}
