package me.foxtails.lakehead.feature.allapps

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Composition tests for the P4 [AlphabetPickerOverlay] slice plus its [AllAppsScreen]
 * wiring (hoisted list state, dismiss preservation, back containment).
 *
 * Runs on Robolectric; nothing here exercises inventory, persistence, or platform
 * launches. The background list stays composed underneath the open picker (spatially
 * still All Apps) but is hidden from the merged accessibility tree by the overlay
 * focus trap, so background assertions while open use the unmerged tree.
 */
@RunWith(RobolectricTestRunner::class)
class AlphabetPickerOverlayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun manyEntries(count: Int = 30): List<AllAppsEntry> =
        (0 until count).map { index ->
            val letter = ('A'..'Z').toList()[index % 26]
            AllAppsEntry(
                identity = AppIdentity(
                    ProfileId("0"),
                    ComponentIdentity("com.example.app$index", "com.example.app$index.Main"),
                ),
                label = "$letter-app-$index",
                alreadyOnHome = false,
            )
        }

    @Test
    fun closed_rendersBackgroundOnly() {
        composeRule.setContent {
            LauncherTheme {
                AlphabetPickerOverlay(
                    picker = AlphabetPickerState(isOpen = false, enabledLetters = setOf("A")),
                    onDismiss = { },
                    onLetterPicked = { },
                ) {
                    BasicText("Background list")
                }
            }
        }

        composeRule.onNodeWithText("Background list").assertIsDisplayed()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(ALPHABET_PICKER_SCRIM_DESCRIPTION)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Jump to A").assertDoesNotExist()
    }

    @Test
    fun open_rendersScrimGridAndKeepsBackgroundComposed() {
        composeRule.setContent {
            LauncherTheme {
                AlphabetPickerOverlay(
                    picker = AlphabetPickerState(isOpen = true, enabledLetters = setOf("A")),
                    onDismiss = { },
                    onLetterPicked = { },
                ) {
                    BasicText("Background list")
                }
            }
        }

        composeRule.onNodeWithContentDescription(ALPHABET_PICKER_SCRIM_DESCRIPTION)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Jump to A").assertIsDisplayed()
        // Background stays composed underneath (spatially still All Apps) but the focus
        // trap removes it from the merged tree while the picker is open.
        composeRule.onNodeWithText("Background list", useUnmergedTree = true).assertExists()
    }

    @Test
    fun activePick_reportsLetter_andHarnessDismisses() {
        var picked: String? = null
        var picker by mutableStateOf(AlphabetPickerState(isOpen = true, enabledLetters = setOf("A", "B")))
        composeRule.setContent {
            LauncherTheme {
                AlphabetPickerOverlay(
                    picker = picker,
                    onDismiss = { picker = picker.copy(isOpen = false) },
                    onLetterPicked = {
                        picked = it
                        picker = picker.copy(isOpen = false)
                    },
                ) {
                    BasicText("Background list")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Jump to A").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Jump to A").performClick()

        composeRule.runOnIdle { assertEquals("A", picked) }
        composeRule.onNodeWithContentDescription("Jump to A").assertDoesNotExist()
        composeRule.onNodeWithText("Background list").assertIsDisplayed()
    }

    @Test
    fun inactiveLetter_hasNoClickAction_andStaysOpen() {
        var picked: String? = null
        var dismissed = false
        composeRule.setContent {
            LauncherTheme {
                AlphabetPickerOverlay(
                    picker = AlphabetPickerState(isOpen = true, enabledLetters = setOf("A")),
                    onDismiss = { dismissed = true },
                    onLetterPicked = { picked = it },
                ) {
                    BasicText("Background list")
                }
            }
        }

        // Q is not enabled (only A): exposed for announcement as disabled, so a real
        // tap on it must be a no-op that never reports and never dismisses.
        composeRule.onNodeWithContentDescription("Jump to Q").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Jump to A").assertIsEnabled()
        val qCenter = composeRule.onNodeWithContentDescription("Jump to Q")
            .fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput { click(qCenter) }
        composeRule.runOnIdle {
            assertNull(picked)
            assertFalse(dismissed)
        }
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()
    }

    @Test
    fun scrimDismiss_preservesHoistedListIndex() {
        val listState = LazyListState(firstVisibleItemIndex = 5, firstVisibleItemScrollOffset = 0)
        var picker: AlphabetPickerState? by mutableStateOf(
            AlphabetPickerState(isOpen = true, enabledLetters = setOf("A", "B", "C")),
        )
        composeRule.setContent {
            LauncherTheme {
                AllAppsScreen(
                    state = AllAppsViewState.Loaded(manyEntries()),
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    picker = picker,
                    onPickerDismiss = { picker = picker?.copy(isOpen = false) },
                    onPickerLetterPicked = {},
                    listState = listState,
                )
            }
        }

        composeRule.onNodeWithContentDescription(ALPHABET_PICKER_SCRIM_DESCRIPTION)
            .assertIsDisplayed()
            .assertHasClickAction()
        // True outside tap: gesture click near the scrim's top-left corner, well clear
        // of the centered picker (which keeps >= 32.dp margins on every side), so hit
        // testing routes to the scrim boundary exactly as a user tap would.
        val scrimBounds = composeRule.onNodeWithContentDescription(ALPHABET_PICKER_SCRIM_DESCRIPTION)
            .fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { click(scrimBounds.topLeft + Offset(5f, 5f)) }

        composeRule.waitUntil(timeoutMillis = 5_000) { picker?.isOpen == false }
        composeRule.runOnIdle {
            assertEquals(
                "dismiss must preserve the hoisted list position",
                5,
                listState.firstVisibleItemIndex,
            )
        }
        composeRule.onNodeWithTag(ALL_APPS_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun backWhileOpen_dismissesWithoutPropagating_thenPropagatesWhenClosed() {
        var propagated = false
        var picker by mutableStateOf(AlphabetPickerState(isOpen = true, enabledLetters = setOf("A")))
        composeRule.setContent {
            LauncherTheme {
                BackHandler(enabled = true) { propagated = true }
                AlphabetPickerOverlay(
                    picker = picker,
                    onDismiss = { picker = picker.copy(isOpen = false) },
                    onLetterPicked = {},
                ) {
                    BasicText("Background list")
                }
            }
        }

        pressBack()
        composeRule.runOnIdle {
            assertFalse("back-while-open must close the picker only", picker.isOpen)
            assertFalse("back-while-open must not propagate to the pager", propagated)
        }
        composeRule.onNodeWithText("Background list").assertIsDisplayed()

        pressBack()
        composeRule.runOnIdle {
            assertTrue("back-when-closed must propagate to the caller", propagated)
        }
    }
}
