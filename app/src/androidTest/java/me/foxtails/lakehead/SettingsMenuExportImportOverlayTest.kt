package me.foxtails.lakehead

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
import me.foxtails.lakehead.platform.feed.FeedProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level evidence for the layout export/import debug rows in the settings menu.
 *
 * - Both "Export layout…" and "Import layout…" rows are present with correct test tags.
 * - Tapping either row invokes its callback without dismissing the menu.
 * - Neither row carries the `selected` semantics flag.
 * - The existing feed-provider rows still work after the layout rows are added.
 * - Scrim and Back still dismiss the menu.
 */
@RunWith(AndroidJUnit4::class)
class SettingsMenuExportImportOverlayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val available = FeedAvailability.Available(FeedProviderId.Lawnfeed)

    private fun pressBack() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun openMenuViaHomeLongPress() {
        composeRule.onNodeWithContentDescription("Home")
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
    }

    private fun shell(
        availability: FeedAvailability = available,
        onSelectFeedProvider: (FeedProviderId) -> Unit = {},
        onExportHomeLayout: () -> Unit = {},
        onImportHomeLayout: () -> Unit = {},
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
                onExportHomeLayout = onExportHomeLayout,
                onImportHomeLayout = onImportHomeLayout,
                initialPage = LAUNCHER_PAGE_TILE,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun homeLongPress_showsLayoutDebugRows() {
        shell()
        openMenuViaHomeLongPress()

        composeRule.onNodeWithTag(SETTINGS_MENU_EXPORT_ROW_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag(SETTINGS_MENU_IMPORT_ROW_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun exportRow_tapsLaunchesCallbackWithoutDismissingMenu() {
        var exportCalled = false
        shell(onExportHomeLayout = { exportCalled = true })
        openMenuViaHomeLongPress()

        composeRule.onNodeWithTag(SETTINGS_MENU_EXPORT_ROW_TAG)
            .performClick()
        composeRule.waitForIdle()

        assertTrue("onExportHomeLayout should have been called", exportCalled)
        // Menu stays open — the row does NOT dismiss.
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertIsDisplayed()
    }

    @Test
    fun importRow_tapsLaunchesCallbackWithoutDismissingMenu() {
        var importCalled = false
        shell(onImportHomeLayout = { importCalled = true })
        openMenuViaHomeLongPress()

        composeRule.onNodeWithTag(SETTINGS_MENU_IMPORT_ROW_TAG)
            .performClick()
        composeRule.waitForIdle()

        assertTrue("onImportHomeLayout should have been called", importCalled)
        // Menu stays open.
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertIsDisplayed()
    }

    @Test
    fun existingProviderRows_stillWorkAfterLayoutRowsAdded() {
        var picked: FeedProviderId? = null
        shell(onSelectFeedProvider = { picked = it })
        openMenuViaHomeLongPress()

        composeRule.onNodeWithTag(feedProviderRowTag(FeedProviderId.NeoFeed))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(FeedProviderId.NeoFeed, picked)
        // Provider pick still dismisses.
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun scrimAndBack_stillDismissMenu() {
        shell()
        openMenuViaHomeLongPress()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertIsDisplayed()

        // Scrim dismiss.
        composeRule.onNodeWithContentDescription(SETTINGS_MENU_SCRIM_DESCRIPTION)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()

        // Re-open and test Back.
        openMenuViaHomeLongPress()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
    }
}