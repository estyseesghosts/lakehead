package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.MediaAlbum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose tests for the slice 5 album overlays (prompt + picker).
 *
 * Covers the prompt's yes/no/cancel paths, the picker's pick and empty-list
 * rows, and the picker's cancel path. Requires a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class AlbumTileOverlaysTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun albums() = listOf(
        MediaAlbum(AlbumRef("1", "Camera"), 42, "content://media/1"),
        MediaAlbum(AlbumRef("2", "Screenshots"), 7, "content://media/2"),
    )

    private fun setPrompt(
        appLabel: String = "Gallery",
        onAccept: () -> Unit = {},
        onDecline: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            LauncherTheme {
                AlbumSlideshowPromptOverlay(
                    isOpen = true,
                    appLabel = appLabel,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    private fun setPicker(
        entries: List<MediaAlbum>,
        onPick: (AlbumRef) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            LauncherTheme {
                AlbumPickerOverlay(
                    isOpen = true,
                    albums = entries,
                    onPick = onPick,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun promptClosed_rendersNothing() {
        composeRule.setContent {
            LauncherTheme {
                AlbumSlideshowPromptOverlay(
                    isOpen = false,
                    appLabel = "Gallery",
                    onAccept = {}, onDecline = {}, onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ALBUM_PROMPT_TAG).assertDoesNotExist()
    }

    @Test
    fun promptYes_reportsAcceptOnly() {
        var accepted = 0
        var declined = 0
        var dismissed = 0
        setPrompt(
            onAccept = { accepted++ },
            onDecline = { declined++ },
            onDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(ALBUM_PROMPT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ALBUM_PROMPT_ACCEPT_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, accepted)
            assertEquals(0, declined)
            assertEquals(0, dismissed)
        }
    }

    @Test
    fun promptNo_reportsDeclineOnly() {
        var accepted = 0
        var declined = 0
        var dismissed = 0
        setPrompt(
            onAccept = { accepted++ },
            onDecline = { declined++ },
            onDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(ALBUM_PROMPT_DECLINE_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(0, accepted)
            assertEquals(1, declined)
            assertEquals(0, dismissed)
        }
    }

    @Test
    fun promptCancel_reportsDismissOnly() {
        var accepted = 0
        var declined = 0
        var dismissed = 0
        setPrompt(
            onAccept = { accepted++ },
            onDecline = { declined++ },
            onDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(ALBUM_PROMPT_SCRIM_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(0, accepted)
            assertEquals(0, declined)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun pickerClosed_rendersNothing() {
        composeRule.setContent {
            LauncherTheme {
                AlbumPickerOverlay(
                    isOpen = false,
                    albums = albums(),
                    onPick = {}, onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ALBUM_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun pickerPick_reportsChosenAlbum() {
        var picked: AlbumRef? = null
        setPicker(albums(), onPick = { picked = it })

        composeRule.onNodeWithTag(ALBUM_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ALBUM_PICKER_ROW_TAG_PREFIX + "2", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(AlbumRef("2", "Screenshots"), picked)
        }
    }

    @Test
    fun pickerEmpty_showsHonestEmptyRow() {
        setPicker(emptyList())

        composeRule.onNodeWithTag(ALBUM_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ALBUM_PICKER_EMPTY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("No albums found").assertIsDisplayed()
    }

    @Test
    fun pickerCancel_reportsDismiss() {
        var dismissed = 0
        var picked: AlbumRef? = null
        setPicker(albums(), onPick = { picked = it }, onDismiss = { dismissed++ })

        // Real touch on a scrim-only point (top-center, above the centered
        // dialog): the same path as a user's tap-out.
        composeRule.onNodeWithTag(ALBUM_PICKER_SCRIM_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ALBUM_PICKER_TAG).performTouchInput {
            click(Offset(centerX.toFloat(), height * 0.05f))
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, dismissed)
            assertTrue("cancel must not pick an album, got $picked", picked == null)
        }
    }
}
