package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused composition tests for the C4 [NavigationControlGeometry] shape/geometry
 * contract, [NavigationControlButton] state/semantics, and [NavigationControlPill]
 * framing.
 *
 * Scope is shape/geometry only: slots are generic positions with no destinations,
 * no navigation state, and no app inventory. The event contract is a raw press
 * signal verified to change nothing by itself.
 */
@RunWith(AndroidJUnit4::class)
class NavigationControlGeometryTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Geometry / shape.

    @Test
    fun pillExtentDerivesFromCommonDimensions() {
        // Height = 56 + 2 * 12 = 80.
        // Width = 4 * 56 + 3 * 8 + 2 * 12 = 224 + 24 + 24 = 272.
        val geometry = NavigationControlGeometry(
            buttonDiameterPx = 56,
            buttonGapPx = 8,
            outerPaddingPx = 12,
            buttonCount = 4,
        )

        assertEquals(80, geometry.pillHeightPx)
        assertEquals(272, geometry.pillWidthPx)
    }

    @Test
    fun radiiAreConcentricHalves() {
        val geometry = NavigationControlGeometry(
            buttonDiameterPx = 56,
            buttonGapPx = 8,
            outerPaddingPx = 12,
            buttonCount = 4,
        )

        assertEquals(80 / 2f, geometry.outerRadiusPx, 0f)
        assertEquals(56 / 2f, geometry.buttonRadiusPx, 0f)
    }

    @Test
    fun buttonCentersAreEvenlySpaced() {
        val geometry = NavigationControlGeometry(
            buttonDiameterPx = 56,
            buttonGapPx = 8,
            outerPaddingPx = 12,
            buttonCount = 4,
        )

        // Vertical center = 12 + 56 / 2 = 40 for every slot.
        assertEquals(40, geometry.buttonCenterYPx())
        // Horizontal centers: 12 + 28 + i * (56 + 8) = 40, 104, 168, 232.
        assertEquals(40, geometry.buttonCenterXPx(0))
        assertEquals(104, geometry.buttonCenterXPx(1))
        assertEquals(168, geometry.buttonCenterXPx(2))
        assertEquals(232, geometry.buttonCenterXPx(3))
    }

    @Test
    fun geometryRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationControlGeometry(0, 8, 12, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationControlGeometry(56, -1, 12, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationControlGeometry(56, 8, -1, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationControlGeometry(56, 8, 12, 0)
        }
        val geometry = NavigationControlGeometry(56, 8, 12, 4)
        assertThrows(IllegalArgumentException::class.java) { geometry.buttonCenterXPx(-1) }
        assertThrows(IllegalArgumentException::class.java) { geometry.buttonCenterXPx(4) }
    }

    @Test
    fun geometryRejectsOverflowingExtents() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationControlGeometry(Int.MAX_VALUE, 0, 0, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationControlGeometry(Int.MAX_VALUE, 0, Int.MAX_VALUE, 1)
        }
    }

    // Selected / enabled state and accessibility label.

    @Test
    fun selectedStateIsExposedInSemantics() {
        composeRule.setContent {
            NavigationControlButton(
                selected = true,
                enabled = true,
                contentDescription = "Selected control",
                onPress = {},
            ) {
                BasicText("S")
            }
        }

        composeRule.onNodeWithContentDescription("Selected control").assertIsSelected()
    }

    @Test
    fun unselectedStateIsExposedInSemantics() {
        composeRule.setContent {
            NavigationControlButton(
                selected = false,
                enabled = true,
                contentDescription = "Unselected control",
                onPress = {},
            ) {
                BasicText("U")
            }
        }

        composeRule.onNodeWithContentDescription("Unselected control").assertIsNotSelected()
    }

    @Test
    fun enabledButtonEmitsPress() {
        var presses = 0
        composeRule.setContent {
            NavigationControlButton(
                selected = false,
                enabled = true,
                contentDescription = "Enabled control",
                onPress = { presses++ },
            ) {
                BasicText("E")
            }
        }

        composeRule.onNodeWithContentDescription("Enabled control").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Enabled control").performClick()
        composeRule.runOnIdle { assertEquals(1, presses) }
    }

    @Test
    fun disabledButtonSuppressesPress() {
        var presses = 0
        composeRule.setContent {
            NavigationControlButton(
                selected = false,
                enabled = false,
                contentDescription = "Disabled control",
                onPress = { presses++ },
            ) {
                BasicText("D")
            }
        }

        composeRule.onNodeWithContentDescription("Disabled control").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Disabled control").performClick()
        composeRule.runOnIdle { assertEquals(0, presses) }
    }

    @Test
    fun minimumTouchTargetIsEnforcedAgainstUndersizedCallerSize() {
        var size: IntSize? = null
        composeRule.setContent {
            NavigationControlButton(
                selected = false,
                enabled = true,
                contentDescription = "Small control",
                onPress = {},
                modifier = Modifier
                    .size(24.dp)
                    .onGloballyPositioned { size = it.size },
            ) {
                Box(Modifier.size(12.dp))
            }
        }
        composeRule.waitForIdle()

        val minimumPx = with(composeRule.density) { 48.dp.roundToPx() }
        composeRule.runOnIdle {
            val measured = size ?: throw AssertionError("button was never placed")
            assertTrue(
                "button must meet 48dp minimum, was $measured (minimumPx=$minimumPx)",
                measured.width >= minimumPx && measured.height >= minimumPx,
            )
        }
    }

    @Test
    fun callerSuppliedLabelsAreDistinctNodes() {
        composeRule.setContent {
            NavigationControlPill {
                NavigationControlButton(
                    selected = true,
                    enabled = true,
                    contentDescription = "First control",
                    onPress = {},
                ) {
                    BasicText("1")
                }
                NavigationControlButton(
                    selected = false,
                    enabled = true,
                    contentDescription = "Second control",
                    onPress = {},
                ) {
                    BasicText("2")
                }
            }
        }

        composeRule.onNodeWithContentDescription("First control").assertExists()
        composeRule.onNodeWithContentDescription("First control").assertIsSelected()
        composeRule.onNodeWithContentDescription("Second control").assertExists()
        composeRule.onNodeWithContentDescription("Second control").assertIsNotSelected()
    }

    @Test
    fun blankLabelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                NavigationControlButton(
                    selected = false,
                    enabled = true,
                    contentDescription = "  ",
                    onPress = {},
                ) {
                    BasicText("X")
                }
            }
        }
    }

    // No fake destination behavior.

    @Test
    fun pressChangesNoSelectionByItself() {
        var presses = 0
        composeRule.setContent {
            NavigationControlPill {
                NavigationControlButton(
                    selected = false,
                    enabled = true,
                    contentDescription = "Peer first",
                    onPress = { presses++ },
                ) {
                    BasicText("1")
                }
                NavigationControlButton(
                    selected = false,
                    enabled = true,
                    contentDescription = "Peer second",
                    onPress = {},
                ) {
                    BasicText("2")
                }
            }
        }

        // A raw press is only a signal: the primitive holds no destination state,
        // so neither peer gains selection from the press itself.
        composeRule.onNodeWithContentDescription("Peer first").performClick()
        composeRule.onNodeWithContentDescription("Peer first").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Peer second").assertIsNotSelected()
        composeRule.runOnIdle { assertEquals(1, presses) }
    }

    @Test
    fun pillFrameInventsNoClickAction() {
        composeRule.setContent {
            NavigationControlPill {
                Box(Modifier.size(48.dp))
                Box(Modifier.size(48.dp))
            }
        }

        // The pill is a shape/geometry frame: label-free caller content must not
        // gain an invented click action or destination from the container itself.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
