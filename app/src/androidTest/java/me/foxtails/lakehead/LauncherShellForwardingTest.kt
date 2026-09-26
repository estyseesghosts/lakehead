package me.foxtails.lakehead

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.navigation.LauncherDestination
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.feature.home.HomeRenderTile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [LauncherShell] correctly forwards the [onRemoveTile] callback
 * from the underlying [me.foxtails.lakehead.feature.home.HomeCanvas].
 */
@RunWith(AndroidJUnit4::class)
class LauncherShellForwardingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val homeId = HomeId("home-test")
    private val personal = AppIdentity(
        ProfileId("0"),
        ComponentIdentity("com.example.app", "com.example.app.Main"),
    )

    private fun tile(
        item: String,
        identity: AppIdentity,
        label: String?,
        column: Int = 0,
        row: Int = 0,
        availability: ItemAvailability = ItemAvailability.AVAILABLE,
    ): HomeRenderTile {
        val launchable = availability == ItemAvailability.AVAILABLE && label != null
        return HomeRenderTile(
            itemId = ItemId(item),
            appIdentity = identity,
            position = LogicalPosition(column, row),
            span = LogicalSpan(1, 1),
            availability = availability,
            label = label,
            icon = null,
            isLaunchable = launchable,
        )
    }

    private fun loaded(tiles: List<HomeRenderTile>) = HomeRenderState.Loaded(
        requestedHomeId = homeId,
        tiles = tiles,
        columns = GridColumns(6),
        extent = GridVerticalExtent(4L, 10L),
        window = GridRowWindow(0L, 10L),
    )

    @Test
    fun shellForwardsOnRemoveTileToCanvas() {
        val tiles = listOf(tile("tile-a", personal, "Test App"))
        var removed: ItemId? = null

        composeRule.setContent {
            LauncherShell(
                homeState = loaded(tiles),
                homeIcons = emptyMap(),
                allAppsState = AllAppsViewState.Loaded(emptyList()),
                allAppsIcons = emptyMap(),
                onLaunchApp = {},
                onAddToHome = {},
                onRequestDefaultHome = {},
                onRemoveTile = { removed = it },
                initialDestination = LauncherDestination.HOME,
            )
        }
        composeRule.waitForIdle()

        // Hidden before edit.
        composeRule.onAllNodesWithContentDescription("Remove Test App").assertCountEquals(0)

        // Enter edit mode via long-click.
        composeRule.onAllNodesWithContentDescription("Test App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        // Tap the remove button.
        composeRule.onNodeWithContentDescription("Remove Test App")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()

        assertEquals(
            "LauncherShell must forward onRemoveTile from HomeCanvas",
            ItemId("tile-a"),
            removed,
        )
    }
}