package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Count-beside-icon layout on single-column and single-row tiles. */
@RunWith(AndroidJUnit4::class)
class AppTileNotificationCountTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(span: GridSpan, width: Dp, height: Dp, count: Int, pinned: Int = 0) {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(width, height)) {
                    AppTile(
                        state = AppTileState(label = "Chat", span = span),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { extent -> Box(Modifier.testTag("icon").size(extent)) },
                        pinnedShortcuts = List(pinned) { PinnedTileShortcut("s$it", "Shortcut $it") },
                        notificationCount = count,
                    )
                }
            }
        }
    }

    private fun assertCountLeftOfIcon(countText: String, tileWidth: Dp) {
        val icon = composeRule.onNodeWithTag("icon", useUnmergedTree = true).getBoundsInRoot()
        val text = composeRule.onNodeWithTag(NOTIFICATION_COUNT_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("count must sit left of the icon", text.right <= icon.left + 0.5.dp)
        assertTrue("pair must stay inside the tile", text.left >= 0.dp && icon.right <= tileWidth)
        assertEquals("icon is square", icon.width.value, icon.height.value, 0.5f)
    }

    @Test
    fun oneByOneShowsCountBesideShrunkIcon() {
        show(GridSpan(1, 1), 72.dp, 72.dp, count = 3)
        assertCountLeftOfIcon("3", 72.dp)
        val icon = composeRule.onNodeWithTag("icon", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(icon.width < 40.dp)
    }

    @Test
    fun rowTileKeepsFullIcon() {
        show(GridSpan(2, 1), 152.dp, 72.dp, count = 12)
        assertCountLeftOfIcon("12", 152.dp)
        val icon = composeRule.onNodeWithTag("icon", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(40f, icon.width.value, 0.5f)
    }

    @Test
    fun columnTileWithPinsShrinksOnlyAppSlot() {
        show(GridSpan(1, 3), 72.dp, 232.dp, count = 5, pinned = 2)
        assertCountLeftOfIcon("5", 72.dp)
    }
}
