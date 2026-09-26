package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1 long-press contract for [NavigationControlButton].
 *
 * A null [onLongPress] keeps a plain click with no long-click semantics node; a non-null
 * [onLongPress] exposes the long-click action alongside the press without changing the
 * press signal itself.
 */
@RunWith(AndroidJUnit4::class)
class NavigationControlButtonLongPressTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nullLongPress_exposesNoLongClickNode() {
        composeRule.setContent {
            NavigationControlButton(
                selected = false,
                enabled = true,
                contentDescription = "Plain control",
                onPress = {},
            ) {
                BasicText("P")
            }
        }

        val node = composeRule.onNodeWithContentDescription("Plain control")
            .fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsActions.OnClick))
        assertFalse(
            "null onLongPress must expose no long-click action",
            node.config.contains(SemanticsActions.OnLongClick),
        )
    }

    @Test
    fun nonNullLongPress_exposesLongClickAndPreservesPress() {
        var presses = 0
        var longPresses = 0
        composeRule.setContent {
            NavigationControlButton(
                selected = false,
                enabled = true,
                contentDescription = "Long control",
                onPress = { presses++ },
                onLongPress = { longPresses++ },
            ) {
                BasicText("L")
            }
        }

        val node = composeRule.onNodeWithContentDescription("Long control")
            .fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsActions.OnClick))
        assertTrue(node.config.contains(SemanticsActions.OnLongClick))

        composeRule.onNodeWithContentDescription("Long control")
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.onNodeWithContentDescription("Long control").performClick()
        composeRule.runOnIdle {
            assertEquals(1, longPresses)
            assertEquals(1, presses)
        }
    }

    @Test
    fun disabledButton_suppressesBothPressAndLongPress() {
        var presses = 0
        var longPresses = 0
        composeRule.setContent {
            NavigationControlButton(
                selected = false,
                enabled = false,
                contentDescription = "Disabled long control",
                onPress = { presses++ },
                onLongPress = { longPresses++ },
            ) {
                BasicText("D")
            }
        }

        composeRule.onNodeWithContentDescription("Disabled long control").performClick()
        composeRule.runOnIdle {
            assertEquals(0, presses)
            assertEquals(0, longPresses)
        }
    }
}
