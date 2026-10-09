package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Album photo layer: shown at 2x2 and larger, icon below, edit border intact. */
@RunWith(AndroidJUnit4::class)
class AppTileAlbumPhotoTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        span: GridSpan,
        width: Dp,
        height: Dp,
        photo: ImageBitmap? = ImageBitmap(4, 4),
        pinned: Int = 0,
        previews: List<TileNotificationPreview> = emptyList(),
        isSelected: Boolean = false,
        isEditSelected: Boolean = false,
    ) {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(width, height)) {
                    AppTile(
                        state = AppTileState(label = "Gallery", span = span, isSelected = isSelected),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { extent -> Box(Modifier.testTag("icon").size(extent)) },
                        symbolContentDescription = "Gallery icon",
                        pinnedShortcuts = List(pinned) { PinnedTileShortcut("s$it", "Shortcut $it") },
                        notificationPreviews = previews,
                        isEditSelected = isEditSelected,
                        albumPhoto = photo,
                    )
                }
            }
        }
    }

    @Test
    fun twoByTwoShowsPhotoNotIcon() {
        show(GridSpan(2, 2), 160.dp, 160.dp)

        composeRule.onNodeWithTag(ALBUM_PHOTO_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag("icon").assertCountEquals(0)
    }

    @Test
    fun fourByTwoShowsPhoto() {
        show(GridSpan(4, 2), 320.dp, 160.dp)

        composeRule.onNodeWithTag(ALBUM_PHOTO_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag("icon").assertCountEquals(0)
    }

    @Test
    fun oneByOneShowsIconNotPhoto() {
        show(GridSpan(1, 1), 72.dp, 72.dp)

        composeRule.onAllNodesWithTag(ALBUM_PHOTO_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag("icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun twoByOneShowsIconNotPhoto() {
        show(GridSpan(2, 1), 152.dp, 72.dp)

        composeRule.onAllNodesWithTag(ALBUM_PHOTO_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag("icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun oneByTwoShowsIconNotPhoto() {
        show(GridSpan(1, 2), 72.dp, 152.dp)

        composeRule.onAllNodesWithTag(ALBUM_PHOTO_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag("icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun editSelectedKeepsPhotoAndSelection() {
        show(GridSpan(2, 2), 160.dp, 160.dp, isSelected = true, isEditSelected = true)

        // The selection border draws from isSelected on the container; the
        // photo layer must not clear it.
        composeRule.onNodeWithTag(ALBUM_PHOTO_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Gallery")[0].assertIsSelected()
    }

    @Test
    fun pinnedStripDrawsOverPhotoWithScrim() {
        show(GridSpan(2, 2), 200.dp, 200.dp, pinned = 2)

        composeRule.onNodeWithTag(ALBUM_PHOTO_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ALBUM_SCRIM_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("$PINNED_SHORTCUT_TAG_PREFIX${"s0"}", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun photoSuppressesNotificationPreviews() {
        show(
            GridSpan(2, 2), 160.dp, 160.dp,
            previews = listOf(TileNotificationPreview(name = "Ada", body = "preview body")),
        )

        composeRule.onNodeWithTag(ALBUM_PHOTO_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("preview body").assertDoesNotExist()
    }

    @Test
    fun photoChangeKeepsPhotoDisplayed() {
        val photo = androidx.compose.runtime.mutableStateOf(ImageBitmap(4, 4))
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(160.dp, 160.dp)) {
                    AppTile(
                        state = AppTileState(label = "Gallery", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { extent -> Box(Modifier.testTag("icon").size(extent)) },
                        symbolContentDescription = "Gallery icon",
                        albumPhoto = photo.value,
                    )
                }
            }
        }
        composeRule.onNodeWithTag(ALBUM_PHOTO_TAG).assertIsDisplayed()

        photo.value = ImageBitmap(6, 6)

        composeRule.onNodeWithTag(ALBUM_PHOTO_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag("icon").assertCountEquals(0)
    }
}
