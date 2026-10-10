package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.onNodeWithContentDescription
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
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

@RunWith(AndroidJUnit4::class)
class FolderOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun member(label: String) = composeRule.onAllNodesWithContentDescription(label)[0]

    private fun gridNode() = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)

    /** Pointer-down point (grid-local) on the quarter of [label]'s tile, as Home's own drag tests do. */
    private fun grab(label: String): Offset {
        val tile = member(label).fetchSemanticsNode().boundsInRoot
        val grid = gridNode().fetchSemanticsNode().boundsInRoot
        return Offset(
            tile.left + tile.width * 0.25f - grid.left,
            tile.top + tile.height * 0.25f - grid.top,
        )
    }

    // The long-press pickup waits on the Compose test clock, not on injected event time.
    private fun holdPastLongPress() {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun id(name: String, profile: String = "0") =
        AppIdentity(ProfileId(profile), ComponentIdentity("org.$name", "org.$name.Main"))

    private fun app(name: String, column: Int, row: Int, columns: Int = 1, rows: Int = 1, profile: String = "0") =
        HomeRenderTile(
            itemId = ItemId(name), appIdentity = id(name, profile), position = LogicalPosition(column, row),
            span = LogicalSpan(columns, rows), availability = ItemAvailability.AVAILABLE,
            label = "App $name", icon = null, isLaunchable = true,
        )

    private fun widget(name: String, column: Int, row: Int) = HomeRenderTile(
        itemId = ItemId(name), appIdentity = id(name), position = LogicalPosition(column, row),
        span = LogicalSpan(4, 2), availability = ItemAvailability.AVAILABLE,
        label = WIDGET_LABEL, icon = null, isLaunchable = true, appWidgetId = 11,
    )

    private fun show(
        members: List<HomeRenderTile>,
        columns: Int = 5,
        onLaunch: (AppIdentity) -> Unit = {},
        onMove: (ItemId) -> Unit = {},
        onMoveMember: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
        onRemove: (ItemId) -> Unit = {},
        onDismiss: () -> Unit = {},
        widgetContent: HomeWidgetContent = NoHomeWidgetContent,
        pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
        onPinned: (ItemId, String) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.fillMaxSize()) {
                    FolderOverlay(
                        isOpen = true, members = members, columns = columns, iconsByIdentity = emptyMap(),
                        onLaunchApp = onLaunch, onMoveToHome = onMove, onRemoveMember = onRemove,
                        onDismiss = onDismiss, widgetContent = widgetContent, onMoveMember = onMoveMember,
                        pinnedByTile = pinnedByTile, onPinnedShortcutClick = onPinned,
                    )
                }
            }
        }
    }

    @Test fun tappingAMemberLaunchesItsExactIdentity() {
        var launched: AppIdentity? = null
        show(listOf(app("a", 0, 0), app("work", 1, 0, profile = "10")), onLaunch = { launched = it })
        member("App work").performClick()
        assertEquals(id("work", "10"), launched)
    }

    @Test fun scrimTapDismissesButPanelTapDoesNot() {
        var dismissed = 0
        show(listOf(app("a", 0, 0)), onDismiss = { dismissed++ })
        composeRule.onNodeWithTag(FOLDER_OVERLAY_PANEL_TAG).performTouchInput { click(Offset(width - 4f, height - 4f)) }
        assertEquals(0, dismissed)
        composeRule.onNodeWithTag(FOLDER_OVERLAY_SCRIM_TAG).performTouchInput { click(Offset(8f, 8f)) }
        assertEquals(1, dismissed)
    }

    @Test fun longPressEntersStandardEditModeInsteadOfAMenu() {
        var removed: ItemId? = null
        show(listOf(app("a", 0, 0), app("b", 1, 0)), onRemove = { removed = it })
        member("App b").performTouchInput { longClick() }
        composeRule.onAllNodesWithContentDescription("Remove App b").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Add widget").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Remove App b")[0].performClick()
        assertEquals(ItemId("b"), removed)
    }

    @Test fun draggingAMemberOutOfThePanelMovesItToHome() {
        var moved: ItemId? = null
        var repositioned: ItemId? = null
        show(
            listOf(app("a", 0, 0), app("b", 1, 0)),
            onMove = { moved = it },
            onMoveMember = { id, _ -> repositioned = id },
        )
        val start = grab("App b")
        gridNode().performTouchInput { down(start) }
        holdPastLongPress()
        gridNode().performTouchInput {
            moveTo(start + Offset(0f, 60f))
            moveTo(start + Offset(0f, 2200f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(ItemId("b"), moved)
        assertEquals(null, repositioned)
    }

    @Test fun draggingWithinThePanelMovesTheMemberToTheDroppedCell() {
        var moved: Pair<ItemId, LogicalPosition>? = null
        var toHome: ItemId? = null
        show(
            listOf(app("a", 0, 0), app("b", 1, 0)),
            onMove = { toHome = it },
            onMoveMember = { id, at -> moved = id to at },
        )
        val cellStep = member("App a").fetchSemanticsNode().boundsInRoot.width * 1.2f
        val start = grab("App a")
        gridNode().performTouchInput { down(start) }
        holdPastLongPress()
        gridNode().performTouchInput {
            moveTo(start + Offset(cellStep * 2, 4f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(ItemId("a"), moved?.first)
        assertEquals(2, moved?.second?.column)
        assertEquals(null, toHome)
    }

    @Test fun panelGrowsWhileAMemberIsHeldAndShowsTheReleaseHint() {
        show(listOf(app("a", 0, 0)))
        val panel = composeRule.onNodeWithTag(FOLDER_OVERLAY_PANEL_TAG)
        val resting = panel.fetchSemanticsNode().boundsInRoot
        val start = grab("App a")
        gridNode().performTouchInput { down(start) }
        holdPastLongPress()
        gridNode().performTouchInput { moveTo(start + Offset(0f, 40f)) }
        composeRule.waitForIdle()
        val held = panel.fetchSemanticsNode().boundsInRoot
        assertTrue("panel must grow downward: ${resting.height} -> ${held.height}", held.height > resting.height)
        assertEquals(resting.top, held.top, 1f)
        gridNode().performTouchInput { moveTo(start + Offset(0f, 2200f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FOLDER_DROP_HINT_TAG).assertIsDisplayed()
        gridNode().performTouchInput { up() }
    }

    @Test fun aMemberKeepsItsPinnedShortcutsAndTheyFire() {
        val clicks = mutableListOf<Pair<ItemId, String>>()
        show(
            listOf(app("wide", 0, 0, 4, 2)),
            pinnedByTile = mapOf(ItemId("wide") to listOf(PinnedTileShortcut("cam", "Camera"))),
            onPinned = { owner, id -> clicks += owner to id },
        )
        composeRule.onNodeWithContentDescription("Camera, shortcut").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Camera, shortcut").performTouchInput { click() }
        assertEquals(listOf(ItemId("wide") to "cam"), clicks)
    }

    @Test fun aWidgetMemberRendersThroughTheWidgetSlotAtItsSpan() {
        var slotSize: Pair<Int, Int>? = null
        show(
            listOf(app("a", 0, 0), widget("w", 0, 1)),
            widgetContent = { _, w, h, _, modifier ->
                slotSize = w to h
                Box(modifier.testTag("hosted"))
            },
        )
        composeRule.onNodeWithTag("hosted").assertIsDisplayed()
        val (w, h) = slotSize!!
        assertTrue("4x2 widget is wider than tall: $w x $h", w > h)
    }

    @Test fun aTwoByTwoMemberIsSquareAndTwiceAsWideAsACell() {
        show(listOf(app("big", 0, 0, 2, 2), app("one", 2, 0)))
        val big = member("App big").fetchSemanticsNode().boundsInRoot
        val one = member("App one").fetchSemanticsNode().boundsInRoot
        assertEquals(big.width, big.height, 1f)
        assertTrue(big.width > one.width * 1.9f)
    }

    @Test fun panelScrollsIndependentlyWhenMembersOverflow() {
        val many = (0 until 100).map { app("m$it", it % 5, it / 5) }
        show(many)
        val before = member("App m0").fetchSemanticsNode().boundsInRoot.top
        repeat(3) { composeRule.onNodeWithTag(FOLDER_OVERLAY_SCROLL_TAG).performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
        val after = member("App m0").fetchSemanticsNode().boundsInRoot.top
        assertTrue("panel content must have scrolled: $before -> $after", after < before)
    }
}
