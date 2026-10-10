package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderTileTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        previews: List<ImageBitmap?> = emptyList(),
        isSelected: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(72.dp)) {
                    FolderTile(
                        label = "Folder",
                        previews = previews,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        isSelected = isSelected,
                        isInteractionEnabled = enabled,
                    )
                }
            }
        }
    }

    @Test fun zeroToFourSymbolsRender() {
        val count = mutableIntStateOf(0)
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(72.dp)) {
                    FolderTile("Folder", List(count.intValue) { ImageBitmap(4, 4) }, onClick = {})
                }
            }
        }
        for (n in 0..4) {
            count.intValue = n
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(FOLDER_TILE_TAG).assertExists()
        }
    }

    @Test fun wholeTileIsOneHitTarget() {
        var clicks = 0
        show(previews = List(4) { ImageBitmap(4, 4) }, onClick = { clicks++ })
        composeRule.onAllNodesWithTag(FOLDER_TILE_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(FOLDER_TILE_TAG).performClick()
        assertEquals(1, clicks)
    }

    @Test fun longPressReachesTheTileCallback() {
        var longs = 0
        show(onLongClick = { longs++ })
        composeRule.onNodeWithTag(FOLDER_TILE_TAG).performTouchInput { longClick() }
        assertEquals(1, longs)
    }

    @Test fun disabledInteractionExposesNoClick() {
        var clicks = 0
        show(enabled = false, onClick = { clicks++ })
        composeRule.onNodeWithTag(FOLDER_TILE_TAG).performClick()
        assertEquals(0, clicks)
    }

    @Test fun selectionIsAnnounced() {
        show(isSelected = true)
        composeRule.onNodeWithTag(FOLDER_TILE_TAG).assertIsSelected()
    }
}
