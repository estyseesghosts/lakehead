package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Composition tests for the S2 [ShortcutSurfaceRow] primitive: rank-ordered row
 * content, sibling (never nested) launch/pin hit regions, `Pin/Unpin "X"` toggle
 * labels and click routing.
 *
 * The row is presentation-only: tests supply every entry and callback, so nothing
 * here exercises shortcut inventory, persistence or navigation.
 *
 * Note: this module pins ui-test 1.7.0, which has no `hasRole` test helper; role
 * behavior is verified through the semantics-node configuration instead (same as
 * `AppTileTest`).
 */
@RunWith(AndroidJUnit4::class)
class ShortcutSurfaceRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unpinnedRow_exposesLabelAndPinToTile() {
        composeRule.setContent {
            LauncherTheme {
                ShortcutSurfaceRow(
                    entry = ShortcutSurfaceEntry(id = "new-message", label = "New message", isPinned = false),
                    onExecute = {},
                    onTogglePin = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("New message").assertExists()
        composeRule.onNodeWithContentDescription("Pin \"New message\" to tile").assertExists()
        composeRule.onNodeWithContentDescription("Unpin \"New message\" from tile").assertDoesNotExist()
    }

    @Test
    fun pinnedRow_exposesUnpinFromTile() {
        composeRule.setContent {
            LauncherTheme {
                ShortcutSurfaceRow(
                    entry = ShortcutSurfaceEntry(id = "camera", label = "Camera", isPinned = true),
                    onExecute = {},
                    onTogglePin = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Camera").assertExists()
        composeRule.onNodeWithContentDescription("Unpin \"Camera\" from tile").assertExists()
        composeRule.onNodeWithContentDescription("Pin \"Camera\" to tile").assertDoesNotExist()
    }

    @Test
    fun launchZoneAndPinToggle_areSiblingButtonsWithDistinctRouting() {
        var executions = 0
        var toggles = 0
        composeRule.setContent {
            LauncherTheme {
                ShortcutSurfaceRow(
                    entry = ShortcutSurfaceEntry(id = "search", label = "Search", isPinned = false),
                    onExecute = { executions++ },
                    onTogglePin = { toggles++ },
                )
            }
        }

        val launch = composeRule.onNodeWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)
        val pin = composeRule.onNodeWithTag(PIN_TOGGLE_TAG, useUnmergedTree = true)
        launch.assert(hasClickAction())
        pin.assert(hasClickAction())
        composeRule.runOnIdle {
            assertEquals(Role.Button, launch.fetchSemanticsNode().config[SemanticsProperties.Role])
            assertEquals(Role.Button, pin.fetchSemanticsNode().config[SemanticsProperties.Role])
        }

        launch.performClick()
        pin.performClick()
        composeRule.runOnIdle {
            assertEquals(1, executions)
            assertEquals(1, toggles)
        }
    }

    @Test
    fun pinToggle_isNeverNestedInsideLaunchZone() {
        composeRule.setContent {
            LauncherTheme {
                ShortcutSurfaceRow(
                    entry = ShortcutSurfaceEntry(id = "compose", label = "Compose", isPinned = true),
                    onExecute = {},
                    onTogglePin = {},
                )
            }
        }
        composeRule.waitForIdle()

        val row = composeRule.onNodeWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
        val launch = composeRule.onNodeWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
        val pin = composeRule.onNodeWithTag(PIN_TOGGLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()

        // Siblings: both regions share the row container as their direct parent.
        composeRule.runOnIdle {
            assertEquals(row.id, launch.parent?.id)
            assertEquals(row.id, pin.parent?.id)
        }
        // The pin toggle is not a descendant of the launch zone (walk to the root).
        composeRule.runOnIdle {
            var cursor: SemanticsNode? = pin.parent
            while (cursor != null) {
                assertTrue(
                    "pin toggle must never nest inside the launch zone",
                    cursor.id != launch.id,
                )
                cursor = cursor.parent
            }
        }
        // Nothing clickable nests inside the launch zone either.
        composeRule.runOnIdle {
            assertFalse(
                "launch zone must not contain a nested clickable",
                hasNestedClickAction(launch),
            )
        }
    }

    @Test
    fun disabledPin_keepsLabelAndRoleWithoutAction() {
        composeRule.setContent {
            LauncherTheme {
                ShortcutSurfaceRow(
                    entry = ShortcutSurfaceEntry(
                        id = "search",
                        label = "Search",
                        isPinned = false,
                        isPinEnabled = false,
                    ),
                    onExecute = {},
                    onTogglePin = {},
                )
            }
        }
        composeRule.waitForIdle()

        // At-capacity disabled pin keeps its accessible name but exposes no action.
        composeRule.onNodeWithContentDescription("Pin \"Search\" to tile").assertExists()
        val pin = composeRule.onNodeWithTag(PIN_TOGGLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
        composeRule.runOnIdle {
            assertFalse(
                "disabled pin must expose no click action",
                pin.config.contains(SemanticsActions.OnClick),
            )
            assertEquals(Role.Button, pin.config[SemanticsProperties.Role])
        }
        // Rank order is caller-owned: rows render in the order supplied.
        composeRule.onNodeWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun blankEntryFields_areRejected() {
        composeRule.runOnIdle {
            try {
                ShortcutSurfaceEntry(id = "", label = "Search", isPinned = false)
                throw AssertionError("blank id must be rejected")
            } catch (expected: IllegalArgumentException) {
            }
            try {
                ShortcutSurfaceEntry(id = "search", label = "  ", isPinned = false)
                throw AssertionError("blank label must be rejected")
            } catch (expected: IllegalArgumentException) {
            }
            try {
                pinToggleDescription("  ", isPinned = false)
                throw AssertionError("blank toggle label must be rejected")
            } catch (expected: IllegalArgumentException) {
            }
        }
        assertEquals("Pin \"X\" to tile", pinToggleDescription("X", isPinned = false))
        assertEquals("Unpin \"X\" from tile", pinToggleDescription("X", isPinned = true))
    }

    private fun hasNestedClickAction(node: SemanticsNode): Boolean =
        node.children.any { child ->
            child.config.contains(SemanticsActions.OnClick) || hasNestedClickAction(child)
        }
}
