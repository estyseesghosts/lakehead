package me.foxtails.lakehead

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.platform.feed.FeedAvailability
import me.foxtails.lakehead.platform.feed.FeedDiagnostic
import me.foxtails.lakehead.platform.feed.FeedDiagnosticCause
import me.foxtails.lakehead.platform.feed.FeedDiagnosticStatus
import me.foxtails.lakehead.platform.feed.FeedProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level evidence for the S1 settings menu slice.
 *
 * - A Home long-press opens the transient menu through the production [LauncherShell] path,
 *   while a plain Home click still pages without opening it.
 * - Back and the scrim dismiss without reporting a provider.
 * - A provider row reports its exact [FeedProviderId] and dismisses.
 * - [FeedAvailability.Unavailable] shows the honest reason with no row selected.
 * - The menu is built from Foundation/text primitives only: rows own the only content
 *   click actions (header/footer expose none), so no Material button/card surface is present.
 */
@RunWith(AndroidJUnit4::class)
class SettingsMenuOverlayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val available = FeedAvailability.Available(FeedProviderId.Lawnfeed)
    private val unavailable = FeedAvailability.Unavailable(
        FeedAvailability.Unavailable.Reason.NO_PROVIDER,
    )

    private fun pressBack() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun shell(
        availability: FeedAvailability,
        initialPage: Int = LAUNCHER_PAGE_TILE,
        onSelectFeedProvider: (FeedProviderId) -> Unit = {},
    ) {
        composeRule.setContent {
            LauncherShell(
                homeState = HomeRenderState.Loaded(
                    requestedHomeId = HomeId("home-test"),
                    tiles = emptyList(),
                    columns = GridColumns(6),
                    extent = GridVerticalExtent(4L, 10L),
                ),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                feedAvailability = availability,
                onSelectFeedProvider = onSelectFeedProvider,
                initialPage = initialPage,
            )
        }
        composeRule.waitForIdle()
    }

    private fun openMenuViaHomeLongPress() {
        composeRule.onNodeWithContentDescription("Home")
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
    }

    @Test
    fun homeLongPress_opensSettingsMenu() {
        shell(available)

        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
        openMenuViaHomeLongPress()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(feedProviderRowTag(FeedProviderId.Lawnfeed))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(feedProviderRowTag(FeedProviderId.NeoFeed))
            .assertIsDisplayed()
    }

    @Test
    fun homeClick_stillPagesWithoutOpeningMenu() {
        shell(available, initialPage = LAUNCHER_PAGE_FEED)

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Home").assertIsSelected()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun back_dismissesMenuWithoutReporting() {
        var picked: FeedProviderId? = null
        shell(available, onSelectFeedProvider = { picked = it })
        openMenuViaHomeLongPress()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
        assertNull(picked)
    }

    @Test
    fun scrim_dismissesMenuWithoutReporting() {
        var picked: FeedProviderId? = null
        shell(available, onSelectFeedProvider = { picked = it })
        openMenuViaHomeLongPress()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(SETTINGS_MENU_SCRIM_DESCRIPTION)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
        assertNull(picked)
    }

    @Test
    fun rowPick_reportsExactIdAndDismisses() {
        var picked: FeedProviderId? = null
        shell(available, onSelectFeedProvider = { picked = it })
        openMenuViaHomeLongPress()

        // Lawnfeed hosts the feed, so its row carries the selected marker.
        composeRule.onNodeWithText(
            "Lawnfeed" + SETTINGS_MENU_SELECTED_SUFFIX,
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(feedProviderRowTag(FeedProviderId.NeoFeed))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(FeedProviderId.NeoFeed, picked)
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun unavailable_showsReasonWithNoSelection() {
        var picked: FeedProviderId? = null
        var isOpen by mutableStateOf(true)
        composeRule.setContent {
            SettingsMenuOverlay(
                isOpen = isOpen,
                availability = unavailable,
                onDismiss = { isOpen = false },
                onSelectProvider = {
                    picked = it
                    isOpen = false
                },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No feed provider installed").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Lawnfeed" + SETTINGS_MENU_SELECTED_SUFFIX,
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            "Neo Feed" + SETTINGS_MENU_SELECTED_SUFFIX,
        ).assertDoesNotExist()

        // Rows stay actionable: the pick still reports the exact id for the caller to bind.
        composeRule.onNodeWithTag(feedProviderRowTag(FeedProviderId.Lawnfeed))
            .performClick()
        composeRule.waitForIdle()
        assertEquals(FeedProviderId.Lawnfeed, picked)
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun noProviderDiagnostic_footerReadsNoProvider() {
        composeRule.setContent {
            SettingsMenuOverlay(
                isOpen = true,
                availability = unavailable,
                diagnostic = FeedDiagnostic(
                    FeedDiagnosticStatus.NO_PROVIDER,
                    FeedDiagnosticCause.ALL_ABSENT,
                ),
                onDismiss = {},
                onSelectProvider = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_MENU_FEED_DIAGNOSTIC_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("NO_PROVIDER · ALL_ABSENT").assertIsDisplayed()
    }

    @Test
    fun noDiagnostic_footerLineAbsent() {
        composeRule.setContent {
            SettingsMenuOverlay(
                isOpen = true,
                availability = available,
                onDismiss = {},
                onSelectProvider = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_MENU_FEED_DIAGNOSTIC_TAG).assertDoesNotExist()
    }

    @Test
    fun menuRows_ownTheOnlyContentClickActions() {
        composeRule.setContent {
            SettingsMenuOverlay(
                isOpen = true,
                availability = available,
                onDismiss = {},
                onSelectProvider = {},
            )
        }
        composeRule.waitForIdle()

        // Sibling provider rows are the actionable content; the header exposes no action,
        // so no Material button/card surface backs the menu (rows are BasicText siblings).
        composeRule.onNodeWithTag(feedProviderRowTag(FeedProviderId.Lawnfeed))
            .assertHasClickAction()
        composeRule.onNodeWithTag(feedProviderRowTag(FeedProviderId.NeoFeed))
            .assertHasClickAction()
        composeRule.onNodeWithText(SETTINGS_MENU_FEED_HEADER).assertHasNoClickAction()
    }

    @Test
    fun menuContainer_hasNoClickAction() {
        composeRule.setContent {
            SettingsMenuOverlay(
                isOpen = true,
                availability = available,
                onDismiss = {},
                onSelectProvider = {},
            )
        }
        composeRule.waitForIdle()

        // The centered menu layer exposes no click action (no nested clickable over
        // rows); only the outside scrim dismisses and rows act as siblings.
        composeRule.onNodeWithContentDescription("Settings menu")
            .assertHasNoClickAction()
        composeRule.onNodeWithContentDescription(SETTINGS_MENU_SCRIM_DESCRIPTION)
            .assertHasClickAction()
    }
}
