package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.PIN_TOGGLE_TAG
import me.foxtails.lakehead.core.designsystem.ROW_LAUNCH_ZONE_TAG
import me.foxtails.lakehead.core.designsystem.SHORTCUT_SURFACE_ROW_TAG
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose tests for the B4 edit-mode shortcut menu above the selection.
 *
 * Covers the above-frame viewport, caller rank order, launch vs pin routing,
 * zero-activation dismiss on empty-tap/`Back`/removal, and none → no node.
 * Move/resize-start dismissal is covered at the pure level by
 * `EditShortcutMenuTest` (`isEditShortcutMenuVisible` gates on a null edit
 * session); these composition tests drive the selection paths that remain
 * reachable from the wiggle-editing flow. Requires a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class EditShortcutMenuComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val homeId = HomeId("home-edit-menu-test")

    private fun identity(profile: String, pkg: String, cls: String) =
        AppIdentity(ProfileId(profile), ComponentIdentity(pkg, cls))

    private val personal = identity("0", "com.example.app", "com.example.app.Main")
    private val work = identity("10", "com.example.app", "com.example.app.Main")

    private fun tile(
        item: String,
        appIdentity: AppIdentity,
        label: String?,
        column: Int = 0,
        row: Int = 3,
        spanColumns: Int = 1,
        spanRows: Int = 1,
    ): HomeRenderTile {
        val launchable = label != null
        return HomeRenderTile(
            itemId = ItemId(item),
            appIdentity = appIdentity,
            position = LogicalPosition(column, row),
            span = LogicalSpan(spanColumns, spanRows),
            availability = ItemAvailability.AVAILABLE,
            label = label,
            icon = null,
            isLaunchable = launchable,
        )
    }

    private fun entry(id: String) = ShortcutSurfaceEntry(
        id = id,
        label = "Shortcut $id",
        isPinned = false,
    )

    private fun loaded(tiles: List<HomeRenderTile>) = HomeRenderState.Loaded(
        requestedHomeId = homeId,
        tiles = tiles,
        columns = GridColumns(6),
        extent = GridVerticalExtent(4L, 10L),
        window = GridRowWindow(0L, 10L),
    )

    private val executed = mutableListOf<Pair<ItemId, String>>()
    private val pinned = mutableListOf<Pair<ItemId, String>>()

    private fun setMenuCanvas(
        state: HomeRenderState,
        entries: List<ShortcutSurfaceEntry>,
    ) {
        executed.clear()
        pinned.clear()
        composeRule.setContent {
            HomeCanvas(
                state = state,
                iconsByIdentity = emptyMap(),
                onLaunchApp = {},
                onRequestDefaultHome = {},
                surfaceEntries = entries,
                onSurfaceExecute = { itemId, entryId -> executed.add(itemId to entryId) },
                onSurfaceTogglePin = { itemId, entryId -> pinned.add(itemId to entryId) },
            )
        }
    }

    private fun longPressBody(label: String) {
        composeRule.onAllNodesWithContentDescription(label)[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
    }

    @Test
    fun longPress_showsMenuAboveSelectedTile() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()

        longPressBody("Deep App")

        val menu = composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val body = composeRule.onAllNodesWithContentDescription("Deep App")[0]
            .fetchSemanticsNode()
        val menuBottom = menu.positionInRoot.y + menu.size.height
        composeRule.runOnIdle {
            assertTrue(
                "menu bottom $menuBottom must sit above tile top ${body.positionInRoot.y}",
                menuBottom <= body.positionInRoot.y + 1.5f,
            )
        }
    }

    @Test
    fun rows_renderInCallerRankOrder() {
        val entries = listOf(entry("s1"), entry("s2"), entry("s3"))
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), entries)
        composeRule.waitForIdle()
        longPressBody("Deep App")

        val zones = composeRule.onAllNodesWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)
        zones.assertCountEquals(3)
        composeRule.onAllNodesWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)
            .assertCountEquals(3)
        val labels = zones.fetchSemanticsNodes().map { node ->
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .firstOrNull()
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf("Shortcut s1", "Shortcut s2", "Shortcut s3"),
                labels,
            )
        }
    }

    @Test
    fun launchZone_executesWithoutPin_pinToggle_pinsWithoutExecute() {
        val tiles = listOf(tile("a", personal, "Deep App"))
        setMenuCanvas(loaded(tiles), listOf(entry("s1"), entry("s2")))
        composeRule.waitForIdle()
        longPressBody("Deep App")

        composeRule.onAllNodesWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)[0]
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("a") to "s1"), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }

        composeRule.onAllNodesWithTag(PIN_TOGGLE_TAG, useUnmergedTree = true)[1]
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("a") to "s1"), executed)
            assertEquals(listOf(ItemId("a") to "s2"), pinned)
        }
    }

    @Test
    fun tapOther_transfersMenuToNewSelection() {
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0),
            tile("b", work, "Beta App", column = 1),
        )
        setMenuCanvas(loaded(tiles), listOf(entry("s1")))
        composeRule.waitForIdle()
        longPressBody("Alpha App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        // Tap-other transfers with no Normal round-trip; the menu follows.
        composeRule.onAllNodesWithContentDescription("Beta App")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Beta App, row 3 column 1"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onAllNodesWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)[0]
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("b") to "s1"), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }

    @Test
    fun emptyTap_dismissesMenuWithZeroActivation() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
            .performTouchInput { click(Offset(centerX.toFloat(), (height - 20).toFloat())) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(emptyList<Pair<ItemId, String>>(), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }

    @Test
    fun back_dismissesMenuWithZeroActivation() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(emptyList<Pair<ItemId, String>>(), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }

    @Test
    fun noEditing_showsNoMenuNode() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun zeroShortcuts_showsNoMenuNode() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), emptyList())
        composeRule.waitForIdle()
        longPressBody("Deep App")

        // Editing engages but no empty box is rendered.
        composeRule.onNode(hasStateDescription("Editing Deep App, row 3 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun removedTile_dismissesMenuWithZeroActivation() {
        val holder = androidx.compose.runtime.mutableStateOf(
            listOf(tile("a", personal, "Deep App"), tile("b", work, "Beta App")),
        )
        executed.clear()
        pinned.clear()
        composeRule.setContent {
            HomeCanvas(
                state = loaded(holder.value),
                iconsByIdentity = emptyMap(),
                onLaunchApp = {},
                onRequestDefaultHome = {},
                surfaceEntries = listOf(entry("s1")),
                onSurfaceExecute = { itemId, entryId -> executed.add(itemId to entryId) },
                onSurfaceTogglePin = { itemId, entryId -> pinned.add(itemId to entryId) },
            )
        }
        composeRule.waitForIdle()
        longPressBody("Deep App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        // Uninstall/removal: the selected tile leaves the loaded tiles.
        composeRule.runOnIdle { holder.value = listOf(tile("b", work, "Beta App")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(emptyList<Pair<ItemId, String>>(), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }
}
