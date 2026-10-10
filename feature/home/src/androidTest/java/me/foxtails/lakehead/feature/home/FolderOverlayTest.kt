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
import me.foxtails.lakehead.core.designsystem.LauncherTheme
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
        onRemove: (ItemId) -> Unit = {},
        onDismiss: () -> Unit = {},
        widgetContent: HomeWidgetContent = NoHomeWidgetContent,
    ) {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.fillMaxSize()) {
                    FolderOverlay(
                        isOpen = true, members = members, columns = columns, iconsByIdentity = emptyMap(),
                        onLaunchApp = onLaunch, onMoveToHome = onMove, onRemoveMember = onRemove,
                        onDismiss = onDismiss, widgetContent = widgetContent,
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

    @Test fun longPressOpensTheMemberMenuAndMoveToHomeReportsTheMember() {
        var moved: ItemId? = null
        show(listOf(app("a", 0, 0), app("b", 1, 0)), onMove = { moved = it })
        member("App b").performTouchInput { longClick() }
        composeRule.onNodeWithTag(FOLDER_MEMBER_MENU_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(FOLDER_MENU_MOVE_TAG).performClick()
        assertEquals(ItemId("b"), moved)
        composeRule.onAllNodesWithTag(FOLDER_MEMBER_MENU_TAG).assertCountEquals(0)
    }

    @Test fun menuRemoveReportsTheMember() {
        var removed: ItemId? = null
        show(listOf(app("a", 0, 0)), onRemove = { removed = it })
        member("App a").performTouchInput { longClick() }
        composeRule.onNodeWithTag(FOLDER_MENU_REMOVE_TAG).performClick()
        assertEquals(ItemId("a"), removed)
    }

    @Test fun cancelClosesTheMenuWithoutReportingAndItCanReopen() {
        var removed: ItemId? = null
        show(listOf(app("a", 0, 0)), onRemove = { removed = it })
        member("App a").performTouchInput { longClick() }
        composeRule.onNodeWithTag(FOLDER_MENU_CANCEL_TAG).performClick()
        composeRule.onAllNodesWithTag(FOLDER_MEMBER_MENU_TAG).assertCountEquals(0)
        assertEquals(null, removed)
        member("App a").performTouchInput { longClick() }
        composeRule.onAllNodesWithTag(FOLDER_MEMBER_MENU_TAG).assertCountEquals(1)
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
