package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused composition tests for the C2 [AppTile] primitive: label, optional icon slot,
 * span-driven arrangement, selected/pressed visual state exposed through semantics,
 * click/long-click behavior and the accessible name contract.
 *
 * The tile is presentation-only: these tests supply every label, icon and callback, so
 * nothing here exercises app inventory, persistence or navigation.
 *
 * Note: this module pins ui-test 1.7.0, which has no `performLongClick`,
 * `hasLongClickAction` or `hasRole` test helpers and exposes `assertExists`,
 * `assertDoesNotExist` and `onAllNodes` as members rather than top-level imports.
 * Long-click and role behavior below is therefore verified through the stable
 * semantics [SemanticsNodeInteraction.fetchSemanticsNode] configuration instead.
 */
@RunWith(AndroidJUnit4::class)
class AppTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labelTextIsVisible() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
            )
        }

        composeRule.onNodeWithText("Calendar").assertExists()
    }

    @Test
    fun blankLabelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AppTileState(label = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppTileState(label = "   ", span = GridSpan(1, 1))
        }
    }

    @Test
    fun blankContentDescriptionOverrideIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onClick = {},
                    contentDescription = "  ",
                )
            }
        }
    }

    @Test
    fun suppliedIconIsComposed() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
                icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
            )
        }

        composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true).assertExists()
    }

    @Test
    fun absentIconComposesLabelOnly() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
                icon = null,
            )
        }

        composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Calendar").assertExists()
    }

    @Test
    fun wideSpanArrangesIconBesideLabel() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(2, 1)),
                onClick = {},
                icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
            )
        }
        composeRule.waitForIdle()

        val iconCenter = centerInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        val labelCenter = centerInRoot(composeRule.onNodeWithText("Calendar", useUnmergedTree = true))
        assertTrue("wide span must place the icon left of the label", iconCenter.x < labelCenter.x)
    }

    @Test
    fun tallSpanArrangesIconAboveLabel() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 2)),
                onClick = {},
                icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
            )
        }
        composeRule.waitForIdle()

        val iconCenter = centerInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        val labelCenter = centerInRoot(composeRule.onNodeWithText("Calendar", useUnmergedTree = true))
        assertTrue("tall span must place the icon above the label", iconCenter.y < labelCenter.y)
    }

    @Test
    fun squareSpanArrangesIconAboveLabel() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
                icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
            )
        }
        composeRule.waitForIdle()

        val iconCenter = centerInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        val labelCenter = centerInRoot(composeRule.onNodeWithText("Calendar", useUnmergedTree = true))
        assertTrue("square span must place the icon above the label", iconCenter.y < labelCenter.y)
    }

    @Test
    fun selectedTileReportsSelected() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1), isSelected = true),
                onClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("Calendar").assertIsSelected()
    }

    @Test
    fun defaultTileReportsNotSelectedAndNoPressedDescription() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("Calendar").assertIsNotSelected()
        composeRule.onAllNodes(hasStateDescription(AppTileDefaults.PressedStateDescription))
            .assertCountEquals(0)
    }

    @Test
    fun pressedTileExposesPressedStateDescription() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1), isPressed = true),
                onClick = {},
            )
        }

        composeRule.onNode(hasStateDescription(AppTileDefaults.PressedStateDescription)).assertExists()
    }

    @Test
    fun clickActionExistsAndInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = { clicks++ },
            )
        }

        composeRule.onNodeWithContentDescription("Calendar")
            .assert(hasClickAction())
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun tileExposesButtonRoleAndDefaultsToLabelDescription() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
            )
        }

        val node = composeRule.onNodeWithContentDescription("Calendar").fetchSemanticsNode()
        assertEquals(Role.Button, node.config[SemanticsProperties.Role])
    }

    @Test
    fun customContentDescriptionOverridesLabel() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
                contentDescription = "Open Calendar",
            )
        }

        composeRule.onNodeWithContentDescription("Open Calendar").assertExists()
    }

    @Test
    fun longClickInvokesCallbackWhenSupplied() {
        var longClicks = 0
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
                onLongClick = { longClicks++ },
            )
        }
        composeRule.waitForIdle()

        val node = composeRule.onNodeWithContentDescription("Calendar").fetchSemanticsNode()
        assertTrue(
            "supplied onLongClick must expose a long-click action",
            node.config.contains(SemanticsActions.OnLongClick),
        )
        val action = node.config[SemanticsActions.OnLongClick]
        composeRule.runOnIdle { action.action?.invoke() }
        composeRule.runOnIdle { assertEquals(1, longClicks) }
    }

    @Test
    fun noLongClickActionWhenNotSupplied() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onClick = {},
                onLongClick = null,
            )
        }
        composeRule.waitForIdle()

        val node = composeRule.onNodeWithContentDescription("Calendar").fetchSemanticsNode()
        assertFalse(
            "absent onLongClick must expose no long-click action",
            node.config.contains(SemanticsActions.OnLongClick),
        )
    }

    private fun centerInRoot(interaction: SemanticsNodeInteraction): Offset {
        val node = interaction.fetchSemanticsNode()
        val position = node.positionInRoot
        val size = node.size
        return Offset(
            x = position.x + size.width / 2f,
            y = position.y + size.height / 2f,
        )
    }
}
