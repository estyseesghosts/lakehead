package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

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
    fun labelIsAccessibilityNameOnly() {
        composeRule.setContent {
            LauncherTheme {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                )
            }
        }

        // TileBody and AppLaunchButton both default to the label as accessible name.
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(2)
        composeRule.onNodeWithText("Calendar").assertDoesNotExist()
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
                    onBodyClick = {}, onSymbolClick = {},
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
                onBodyClick = {}, onSymbolClick = {},
                icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
            )
        }

        composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true).assertExists()
    }

    @Test
    fun absentIconComposesLabelOnly() {
        composeRule.setContent {
            LauncherTheme {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                    icon = null,
                )
            }
        }

        composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(2)
        composeRule.onNodeWithText("Calendar").assertDoesNotExist()
    }

    @Test
    fun wideSpanAnchorsIconBottomEnd() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(320.dp, 160.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 1)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Tile bounds come from TileBody (index 0: first sibling, full-size region).
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        assertTrue(
            "2x1 span must anchor the icon to the tile end " +
                "(tileCenterX=${tileBounds.center.x} iconCenterX=${iconBounds.center.x})",
            iconBounds.center.x > tileBounds.center.x,
        )
        assertTrue(
            "2x1 span must anchor the icon to the tile bottom " +
                "(tileCenterY=${tileBounds.center.y} iconCenterY=${iconBounds.center.y})",
            iconBounds.center.y > tileBounds.center.y,
        )
        assertIconInsideTile(tileBounds, iconBounds)
    }

    @Test
    fun tallSpanCentersIconHorizontally() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(160.dp, 320.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Tile bounds come from TileBody (index 0: first sibling, full-size region).
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        assertTrue(
            "1x2 span must x-center the icon " +
                "(tileCenterX=${tileBounds.center.x} iconCenterX=${iconBounds.center.x})",
            abs(iconBounds.center.x - tileBounds.center.x) <= 1f,
        )
        assertIconInsideTile(tileBounds, iconBounds)
    }

    @Test
    fun largeSquareSpanAnchorsIconBottomEnd() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(320.dp, 320.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Tile bounds come from TileBody (index 0: first sibling, full-size region).
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        assertTrue(
            "2x2 span must anchor the icon to the tile end " +
                "(tileCenterX=${tileBounds.center.x} iconCenterX=${iconBounds.center.x})",
            iconBounds.center.x > tileBounds.center.x,
        )
        assertTrue(
            "2x2 span must anchor the icon to the tile bottom " +
                "(tileCenterY=${tileBounds.center.y} iconCenterY=${iconBounds.center.y})",
            iconBounds.center.y > tileBounds.center.y,
        )
        assertIconInsideTile(tileBounds, iconBounds)
    }

    @Test
    fun selectedTileReportsSelectedOnBodyOnly() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1), isSelected = true),
                onBodyClick = {}, onSymbolClick = {},
            )
        }

        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        val selectedCount = nodes.fetchSemanticsNodes()
            .count { it.config.getOrElse(SemanticsProperties.Selected) { false } }
        composeRule.runOnIdle { assertEquals("selected flag lives on TileBody only", 1, selectedCount) }
    }

    @Test
    fun defaultTileReportsNotSelectedAndNoPressedDescription() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onBodyClick = {}, onSymbolClick = {},
            )
        }

        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        assertTrue(
            "no region selected by default",
            nodes.fetchSemanticsNodes()
                .none { it.config.getOrElse(SemanticsProperties.Selected) { false } },
        )
        composeRule.onAllNodes(hasStateDescription(AppTileDefaults.PressedStateDescription))
            .assertCountEquals(0)
    }

    @Test
    fun pressedTileExposesPressedStateDescription() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1), isPressed = true),
                onBodyClick = {}, onSymbolClick = {},
            )
        }

        composeRule.onNode(hasStateDescription(AppTileDefaults.PressedStateDescription)).assertExists()
    }

    @Test
    fun bodyAndSymbolClickActionsInvokeDistinctCallbacks() {
        var bodyClicks = 0
        var symbolClicks = 0
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onBodyClick = { bodyClicks++ },
                onSymbolClick = { symbolClicks++ },
            )
        }

        // Two distinct Role.Button nodes share the label; each click routes to exactly
        // one callback, so (1, 1) totals prove distinct routing regardless of order.
        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        nodes[0].assert(hasClickAction()).performClick()
        nodes[1].assert(hasClickAction()).performClick()
        composeRule.runOnIdle {
            assertEquals(1, bodyClicks)
            assertEquals(1, symbolClicks)
        }
    }

    @Test
    fun tileExposesButtonRoleOnBothRegionsAndDefaultsToLabelDescription() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onBodyClick = {}, onSymbolClick = {},
            )
        }

        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        nodes.fetchSemanticsNodes().forEach { node ->
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
        }
    }

    @Test
    fun customContentDescriptionOverridesLabelOnBothRegions() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onBodyClick = {}, onSymbolClick = {},
                contentDescription = "Open Calendar",
            )
        }

        composeRule.onAllNodesWithContentDescription("Open Calendar").assertCountEquals(2)
    }

    @Test
    fun customSymbolDescriptionOverridesSymbolNodeOnly() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onBodyClick = {}, onSymbolClick = {},
                symbolContentDescription = "Launch Calendar",
            )
        }

        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Launch Calendar").assertCountEquals(1)
    }

    @Test
    fun blankSymbolContentDescriptionOverrideIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                    symbolContentDescription = "  ",
                )
            }
        }
    }

    @Test
    fun longClickInvokesCallbackWhenSupplied() {
        var longClicks = 0
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onBodyClick = {}, onSymbolClick = {},
                onLongClick = { longClicks++ },
            )
        }
        composeRule.waitForIdle()

        // Only TileBody exposes the long-click action; AppLaunchButton never does.
        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        val withLongClick = nodes.fetchSemanticsNodes()
            .filter { it.config.contains(SemanticsActions.OnLongClick) }
        composeRule.runOnIdle { assertEquals("long-click lives on TileBody only", 1, withLongClick.size) }
        val action = withLongClick.single().config[SemanticsActions.OnLongClick]
        composeRule.runOnIdle { action.action?.invoke() }
        composeRule.runOnIdle { assertEquals(1, longClicks) }
    }

    @Test
    fun noLongClickActionWhenNotSupplied() {
        composeRule.setContent {
            AppTile(
                state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                onBodyClick = {}, onSymbolClick = {},
                onLongClick = null,
            )
        }
        composeRule.waitForIdle()

        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        assertTrue(
            "absent onLongClick must expose no long-click action on either region",
            nodes.fetchSemanticsNodes().none { it.config.contains(SemanticsActions.OnLongClick) },
        )
    }

    @Test
    fun normalAndAccentResolveToDifferentColorsLight() {
        var normal: TileColors? = null
        var accent: TileColors? = null
        composeRule.setContent {
            LauncherTheme(darkTheme = false) {
                normal = resolveTileColors(TileColorRole.Normal, isPressed = false)
                accent = resolveTileColors(TileColorRole.Accent, isPressed = false)
            }
        }

        composeRule.runOnIdle {
            assertNotEquals(normal!!.container, accent!!.container)
            assertNotEquals(normal!!.content, accent!!.content)
        }
    }

    @Test
    fun normalAndAccentResolveToDifferentColorsDark() {
        var normal: TileColors? = null
        var accent: TileColors? = null
        composeRule.setContent {
            LauncherTheme(darkTheme = true) {
                normal = resolveTileColors(TileColorRole.Normal, isPressed = false)
                accent = resolveTileColors(TileColorRole.Accent, isPressed = false)
            }
        }

        composeRule.runOnIdle {
            assertNotEquals(normal!!.container, accent!!.container)
            assertNotEquals(normal!!.content, accent!!.content)
        }
    }

    @Test
    fun oneByOneSymbolHitBoxSmallerThanTileBody() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(160.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                        onBodyClick = {}, onSymbolClick = {},
                        symbolContentDescription = "Launch Calendar",
                        onLongClick = {},
                        icon = { Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // TileBody keeps the full tile bounds; the 1x1 symbol hit region is an
        // icon-sized (symbolSize coerced to >=48dp) box centered on the tile, so
        // ring/edge touches still reach TileBody.
        val tileBounds = boundsInRoot(composeRule.onNodeWithContentDescription("Calendar"))
        val symbolBounds = boundsInRoot(composeRule.onNodeWithContentDescription("Launch Calendar"))
        composeRule.runOnIdle {
            assertTrue(
                "1x1 symbol hit box must be smaller than the tile " +
                    "(tile=$tileBounds symbol=$symbolBounds)",
                symbolBounds.width < tileBounds.width &&
                    symbolBounds.height < tileBounds.height,
            )
            assertTrue(
                "1x1 symbol hit box must stay inside the tile " +
                    "(tile=$tileBounds symbol=$symbolBounds)",
                symbolBounds.left >= tileBounds.left - 1f &&
                    symbolBounds.top >= tileBounds.top - 1f &&
                    symbolBounds.right <= tileBounds.right + 1f &&
                    symbolBounds.bottom <= tileBounds.bottom + 1f,
            )
            assertTrue(
                "1x1 symbol hit box must be centered on the tile " +
                    "(tile=$tileBounds symbol=$symbolBounds)",
                abs(symbolBounds.center.x - tileBounds.center.x) <= 1f &&
                    abs(symbolBounds.center.y - tileBounds.center.y) <= 1f,
            )
        }
        val bodyNode = composeRule.onNodeWithContentDescription("Calendar").fetchSemanticsNode()
        val symbolNode =
            composeRule.onNodeWithContentDescription("Launch Calendar").fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(
                "TileBody must still expose OnLongClick",
                bodyNode.config.contains(SemanticsActions.OnLongClick),
            )
            assertFalse(
                "AppLaunchButton must never expose OnLongClick",
                symbolNode.config.contains(SemanticsActions.OnLongClick),
            )
        }
    }

    @Test
    fun defaultShowsNoShortcutPlaceholders() {
        composeRule.setContent {
            LauncherTheme {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 1").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 2").assertCountEquals(0)
    }

    @Test
    fun negativeShortcutPlaceholdersRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                    shortcutPlaceholders = -1,
                )
            }
        }
    }

    @Test
    fun wideSpanPlaceholders_areTwoDistinctInertNodes_bodySymbolUnaffected() {
        var bodyClicks = 0
        var symbolClicks = 0
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(320.dp, 160.dp)) {
                    AppTile(
                        // 3x1 capacity is columns - 1 = 2, so both dots render.
                        state = AppTileState(label = "Calendar", span = GridSpan(3, 1)),
                        onBodyClick = { bodyClicks++ },
                        onSymbolClick = { symbolClicks++ },
                        shortcutPlaceholders = 2,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Exactly two distinct placeholder nodes, one per slot.
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 1").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 2").assertCountEquals(1)
        val placeholders = listOf("Shortcut placeholder 1", "Shortcut placeholder 2")
            .map { composeRule.onNodeWithContentDescription(it).fetchSemanticsNode() }
        composeRule.runOnIdle {
            placeholders.forEach { node ->
                assertFalse(
                    "shortcut placeholder must expose no click action",
                    node.config.contains(SemanticsActions.OnClick),
                )
                assertFalse(
                    "shortcut placeholder must expose no long-click action",
                    node.config.contains(SemanticsActions.OnLongClick),
                )
                assertFalse(
                    "shortcut placeholder must not be a button",
                    node.config.contains(SemanticsProperties.Role),
                )
            }
        }

        // Existing Body/Symbol separation still passes: two Role.Button regions route
        // to exactly one callback each, so placeholders trigger neither.
        val regions = composeRule.onAllNodesWithContentDescription("Calendar")
        regions.assertCountEquals(2)
        regions[0].assert(hasClickAction()).performClick()
        regions[1].assert(hasClickAction()).performClick()
        composeRule.runOnIdle {
            assertEquals(1, bodyClicks)
            assertEquals(1, symbolClicks)
        }
    }

    @Test
    fun overCountPlaceholders_clampedToSpanMax_neverCrashes() {
        // 1x1 capacity is 0: over-count renders none instead of crashing.
        composeRule.setContent {
            LauncherTheme {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                    shortcutPlaceholders = 3,
                )
            }
        }
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 1").assertCountEquals(0)

        // 2x2 capacity is columns - 1 = 1: over-count renders exactly one dot.
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(320.dp, 320.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        shortcutPlaceholders = 5,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 1").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 2").assertCountEquals(0)
    }

    @Test
    fun singleColumnCapacity_followsRowsMinusOne() {
        // 1x2 capacity is rows - 1 = 1: requesting 3 renders exactly one dot.
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(160.dp, 320.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        shortcutPlaceholders = 3,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 1").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 2").assertCountEquals(0)
    }

    private fun boundsInRoot(interaction: SemanticsNodeInteraction): Rect {
        val node = interaction.fetchSemanticsNode()
        val position = node.positionInRoot
        val size = node.size
        return Rect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height,
        )
    }

    private fun assertIconInsideTile(tileBounds: Rect, iconBounds: Rect) {
        assertTrue(
            "anchored icon must stay inside the tile " +
                "(tile=$tileBounds icon=$iconBounds)",
            iconBounds.left >= tileBounds.left - 1f &&
                iconBounds.top >= tileBounds.top - 1f &&
                iconBounds.right <= tileBounds.right + 1f &&
                iconBounds.bottom <= tileBounds.bottom + 1f,
        )
    }
}
