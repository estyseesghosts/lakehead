package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry
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
                icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
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
                // Realistic 2x1 tile on a six-cell normal-phone grid
                // (56.dp cells, 8.dp gaps): 120x56.dp.
                Box(Modifier.size(120.dp, 56.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 1)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Tile bounds come from TileBody (index 0: first sibling, full-size region).
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val appBounds = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        val insetPx = with(composeRule.density) { 8.dp.toPx() }
        assertTrue(
            "2x1 app glyph must sit on the tile right edge " +
                "(tileRight=${tileBounds.right} appRight=${appBounds.right})",
            abs(tileBounds.right - appBounds.right - insetPx) <= 2f,
        )
        assertTrue(
            "2x1 app glyph must sit on the tile bottom edge " +
                "(tileBottom=${tileBounds.bottom} appBottom=${appBounds.bottom})",
            abs(tileBounds.bottom - appBounds.bottom - insetPx) <= 2f,
        )
        assertIconInsideTile(tileBounds, iconBounds)
    }

    @Test
    fun tallSpanWithPins_anchorsAppBottomRight() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 1x2 tile (56x120.dp): app + 1 exactly fills the
                // right edge at the grid-unit pitch.
                Box(Modifier.size(56.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Tile bounds come from TileBody (index 0: first sibling, full-size region).
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val appBounds = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        val insetPx = with(composeRule.density) { 8.dp.toPx() }
        assertTrue(
            "1x2 span with pinned shortcuts must anchor the app to the tile right edge " +
                "(tileRight=${tileBounds.right} appRight=${appBounds.right})",
            abs(tileBounds.right - appBounds.right - insetPx) <= 2f,
        )
        assertTrue(
            "1x2 span with pinned shortcuts must anchor the app to the tile bottom " +
                "(tileBottom=${tileBounds.bottom} appBottom=${appBounds.bottom})",
            abs(tileBounds.bottom - appBounds.bottom - insetPx) <= 2f,
        )
        assertIconInsideTile(tileBounds, iconBounds)
    }

    @Test
    fun largeSquareSpanAnchorsIconBottomEnd() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 2x2 tile (120x120.dp).
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
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
            LauncherTheme {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                    onLongClick = { longClicks++ },
                )
            }
        }
        composeRule.waitForIdle()

        // TileBody and AppLaunchButton share the one tile long-press path.
        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        val withLongClick = nodes.fetchSemanticsNodes()
            .filter { it.config.contains(SemanticsActions.OnLongClick) }
        composeRule.runOnIdle { assertEquals("long-click lives on every tile hit region", 2, withLongClick.size) }
        val action = withLongClick.first().config[SemanticsActions.OnLongClick]
        composeRule.runOnIdle { action.action?.invoke() }
        composeRule.runOnIdle { assertEquals(1, longClicks) }
    }

    @Test
    fun noLongClickActionWhenNotSupplied() {
        composeRule.setContent {
            LauncherTheme {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                    onLongClick = null,
                )
            }
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
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
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
            assertTrue(
                "AppLaunchButton shares the tile OnLongClick",
                symbolNode.config.contains(SemanticsActions.OnLongClick),
            )
        }
    }

    @Test
    fun defaultShowsNoStrip() {
        composeRule.setContent {
            LauncherTheme {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                )
            }
        }

        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
        // Body + symbol only; no strip glyphs.
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(2)
    }

    @Test
    fun negativeOverflowCountRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                AppTile(
                    state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                    onBodyClick = {}, onSymbolClick = {},
                    overflowCount = -1,
                )
            }
        }
    }

    @Test
    fun wideSpanPinnedShortcuts_areDistinctButtons_bodySymbolUnaffected() {
        var bodyClicks = 0
        var symbolClicks = 0
        val shortcutClicks = mutableListOf<String>()
        composeRule.setContent {
            LauncherTheme {
                // Realistic 3x1 tile (184x56.dp): app + 2 exactly fills the
                // bottom edge at the grid-unit pitch.
                Box(Modifier.size(184.dp, 56.dp)) {
                    AppTile(
                        // 3x1 capacity is columns - 1 = 2: the caller passes the
                        // pre-clamped visible list in pin order.
                        state = AppTileState(label = "Calendar", span = GridSpan(3, 1)),
                        onBodyClick = { bodyClicks++ },
                        onSymbolClick = { symbolClicks++ },
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                        ),
                        onShortcutClick = { shortcutClicks.add(it) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Each shortcut is its own Role.Button node with its own label,
        // distinct from the tile's app regions.
        val first = composeRule.onNodeWithContentDescription("New post, shortcut")
        val second = composeRule.onNodeWithContentDescription("Camera, shortcut")
        first.assertExists().assert(hasClickAction())
        second.assertExists().assert(hasClickAction())
        val firstNode = first.fetchSemanticsNode()
        val secondNode = second.fetchSemanticsNode()
        composeRule.runOnIdle {
            assertEquals(
                Role.Button,
                firstNode.config[SemanticsProperties.Role],
            )
            assertEquals(
                Role.Button,
                secondNode.config[SemanticsProperties.Role],
            )
        }
        first.performClick()
        second.performClick()
        composeRule.runOnIdle { assertEquals(listOf("new-post", "camera"), shortcutClicks) }

        // Existing Body/Symbol separation still passes: two Role.Button regions route
        // to exactly one callback each, so strip taps trigger neither. TileBody
        // center sits under the middle shortcut glyph on a realistic tile (a
        // center tap would correctly hit the glyph), so the body tap targets
        // a strip-clear point in the top padding band instead.
        val regions = composeRule.onAllNodesWithContentDescription("Calendar")
        regions.assertCountEquals(2)
        val bodyNode = regions[0].assert(hasClickAction()).fetchSemanticsNode()
        val clearTap = with(composeRule.density) {
            Offset(x = bodyNode.size.width / 2f, y = 4.dp.toPx())
        }
        regions[0].performTouchInput { click(clearTap) }
        regions[1].assert(hasClickAction()).performClick()
        composeRule.runOnIdle {
            assertEquals(1, bodyClicks)
            assertEquals(1, symbolClicks)
            assertEquals(2, shortcutClicks.size)
        }
    }

    @Test
    fun pinnedList_rendersFittedWindow_overflowAnnounced() {
        // 2x2 capacity is columns - 1 = 1: the caller passes one visible glyph
        // plus the hidden count (clamping lives in :feature:home arrangement,
        // never in this tile). On a realistic 120.dp tile app + pin exactly
        // fills the edge, so the +N node (which would overflow) yields to
        // TileBody semantics while the pin stays visible and tappable.
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                        overflowCount = 4,
                        showOverflowIndicator = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()
        composeRule.onNodeWithContentDescription("Camera, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasStateDescription("4 more shortcuts")).assertExists()
    }

    @Test
    fun overflowIndicator_isVisibleNonLaunchableNode() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 4x2 tile (248x120.dp): app + 2 + node exactly
                // fills the bottom edge, so the +N node renders in bounds.
                Box(Modifier.size(248.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(4, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                        ),
                        overflowCount = 1,
                        showOverflowIndicator = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val overflow = composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true)
        overflow.assertExists()
        composeRule.onNodeWithContentDescription("1 more shortcuts").assertExists()
        // The node stays inside the tile bounds.
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val nodeBounds = boundsInRoot(overflow)
        assertTrue(
            "overflow node must stay inside the tile (tile=$tileBounds node=$nodeBounds)",
            nodeBounds.left >= tileBounds.left - 1f &&
                nodeBounds.top >= tileBounds.top - 1f &&
                nodeBounds.right <= tileBounds.right + 1f &&
                nodeBounds.bottom <= tileBounds.bottom + 1f,
        )
        val overflowNode = overflow.fetchSemanticsNode()
        composeRule.runOnIdle {
            assertFalse(
                "overflow indicator must expose no click action",
                overflowNode.config.contains(SemanticsActions.OnClick),
            )
            assertFalse(
                "overflow indicator must not be a button",
                overflowNode.config.contains(SemanticsProperties.Role),
            )
        }
    }

    @Test
    fun hiddenOverflow_mergesIntoBodySemantics() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                        // Edit-selected / zero-capacity: no +N node.
                        overflowCount = 2,
                        showOverflowIndicator = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        // The hidden count stays announced on the tile body.
        composeRule.onNode(hasStateDescription("2 more shortcuts")).assertExists()
    }

    @Test
    fun disabledInteraction_disablesStripGlyphs_keepsLabelsAndRoles() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        onShortcutClick = { error("must not execute while disabled") },
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                        isInteractionEnabled = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Shortcut glyphs keep their accessible names and roles but expose no
        // click action, so the tile drags as one object in edit mode.
        val glyph = composeRule.onNodeWithContentDescription("New post, shortcut")
        glyph.assertExists()
        val glyphNode = glyph.fetchSemanticsNode()
        composeRule.runOnIdle {
            assertFalse(
                "disabled shortcut glyph must expose no click action",
                glyphNode.config.contains(SemanticsActions.OnClick),
            )
            assertEquals(Role.Button, glyphNode.config[SemanticsProperties.Role])
        }
    }

    @Test
    fun zeroPinned_wideTileCentersSymbolAndRendersNoStrip() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(320.dp, 320.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        assertTrue(
            "2x2 with zero pinned must x-center the symbol " +
                "(tileCenterX=${tileBounds.center.x} iconCenterX=${iconBounds.center.x})",
            abs(iconBounds.center.x - tileBounds.center.x) <= 2f,
        )
        assertTrue(
            "2x2 with zero pinned must y-center the symbol " +
                "(tileCenterY=${tileBounds.center.y} iconCenterY=${iconBounds.center.y})",
            abs(iconBounds.center.y - tileBounds.center.y) <= 2f,
        )
        // No strip: only Body + symbol share the label, no strip glyphs.
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(2)
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun largeTile_passesCenteredExtentToIconSlot() {
        var capturedExtent: Dp? = null
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(320.dp, 320.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { extent ->
                            capturedExtent = extent
                            Box(Modifier.testTag("tile-icon").size(extent))
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // min(availW, availH) after tile padding: 320 - 2 * 8 = 304.
        composeRule.runOnIdle { assertEquals(304.dp, capturedExtent) }
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        assertTrue(
            "large centered icon must x-center " +
                "(tileCenterX=${tileBounds.center.x} iconCenterX=${iconBounds.center.x})",
            abs(iconBounds.center.x - tileBounds.center.x) <= 2f,
        )
        assertTrue(
            "large centered icon must y-center " +
                "(tileCenterY=${tileBounds.center.y} iconCenterY=${iconBounds.center.y})",
            abs(iconBounds.center.y - tileBounds.center.y) <= 2f,
        )
    }

    @Test
    fun zeroPinned_narrowTile_anchorsBottomCenterAndRendersNoStrip() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 1x2 tile (56x120.dp) with zero pins: the small-span
                // unpinned anchor is bottom-center per docs/grid.md section 4.
                Box(Modifier.size(56.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        assertTrue(
            "1x2 with zero pinned must x-center the symbol " +
                "(tileCenterX=${tileBounds.center.x} iconCenterX=${iconBounds.center.x})",
            abs(iconBounds.center.x - tileBounds.center.x) <= 2f,
        )
        val bottomInsetPx = with(composeRule.density) { 8.dp.toPx() }
        assertTrue(
            "1x2 with zero pinned must sit on the tile bottom edge above padding " +
                "(tileBottom=${tileBounds.bottom} iconBottom=${iconBounds.bottom})",
            iconBounds.bottom <= tileBounds.bottom - bottomInsetPx + 2f &&
                iconBounds.bottom > tileBounds.center.y,
        )
        assertIconInsideTile(tileBounds, iconBounds)
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(2)
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun zeroPinned_smallWideTile_anchorsBottomEndAndRendersNoStrip() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 2x1 tile (120x56.dp) with zero pins: the
                // small-span unpinned anchor is bottom-right.
                Box(Modifier.size(120.dp, 56.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 1)),
                        onBodyClick = {}, onSymbolClick = {},
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val iconBounds = boundsInRoot(composeRule.onNodeWithTag("tile-icon", useUnmergedTree = true))
        assertTrue(
            "2x1 with zero pinned must anchor the symbol to the tile end " +
                "(tileCenterX=${tileBounds.center.x} iconCenterX=${iconBounds.center.x})",
            iconBounds.center.x > tileBounds.center.x,
        )
        assertTrue(
            "2x1 with zero pinned must anchor the symbol to the tile bottom " +
                "(tileCenterY=${tileBounds.center.y} iconBottom=${iconBounds.bottom})",
            iconBounds.bottom > tileBounds.center.y,
        )
        assertIconInsideTile(tileBounds, iconBounds)
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(2)
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun disabledInteraction_stripsActions_keepsRoleDescriptionAndMinima() {
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(160.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                        onBodyClick = {}, onSymbolClick = {},
                        onLongClick = {},
                        isInteractionEnabled = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Both regions keep their accessible names but expose no actions.
        val nodes = composeRule.onAllNodesWithContentDescription("Calendar")
        nodes.assertCountEquals(2)
        composeRule.runOnIdle {
            nodes.fetchSemanticsNodes().forEach { node ->
                assertFalse(
                    "disabled tile must expose no click action",
                    node.config.contains(SemanticsActions.OnClick),
                )
                assertFalse(
                    "disabled tile must expose no long-click action",
                    node.config.contains(SemanticsActions.OnLongClick),
                )
                assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            }
        }
        // Minima survive: both hit regions still meet 48dp.
        val minTouchPx = with(composeRule.density) { 48.dp.roundToPx() }
        nodes.fetchSemanticsNodes().forEach { node ->
            composeRule.runOnIdle {
                assertTrue(
                    "disabled region must keep 48dp minima (size=${node.size})",
                    node.size.width >= minTouchPx && node.size.height >= minTouchPx,
                )
            }
        }
    }

    @Test
    fun selectedDimsContainerToHalfAlpha_keepsBorder() {
        var base: TileColors? = null
        var selected: TileColors? = null
        composeRule.setContent {
            LauncherTheme {
                base = resolveTileColors(TileColorRole.Normal, isPressed = false, isSelected = false)
                selected = resolveTileColors(TileColorRole.Normal, isPressed = false, isSelected = true)
            }
        }
        composeRule.runOnIdle {
            val b = base!!
            val s = selected!!
            assertEquals(1f, b.container.alpha, 0.01f)
            assertEquals(0.5f, s.container.alpha, 0.01f)
            assertEquals(b.container.red, s.container.red, 0.01f)
            assertEquals(b.container.green, s.container.green, 0.01f)
            assertEquals(b.container.blue, s.container.blue, 0.01f)
            assertEquals("selected must keep the selection border", b.selectionBorder, s.selectionBorder)
            assertEquals(b.content, s.content)
        }
    }

    @Test
    fun selectedContainer_neverHardcodesBlackOrWhite() {
        var lightSelected: TileColors? = null
        var darkSelected: TileColors? = null
        composeRule.setContent {
            LauncherTheme(darkTheme = false) {
                lightSelected = resolveTileColors(TileColorRole.Normal, isPressed = false, isSelected = true)
            }
        }
        composeRule.runOnIdle {
            assertNotEquals(androidx.compose.ui.graphics.Color.Black, lightSelected!!.container)
            assertNotEquals(androidx.compose.ui.graphics.Color.White, lightSelected!!.container)
        }
        composeRule.setContent {
            LauncherTheme(darkTheme = true) {
                darkSelected = resolveTileColors(TileColorRole.Normal, isPressed = false, isSelected = true)
            }
        }
        composeRule.runOnIdle {
            assertNotEquals(androidx.compose.ui.graphics.Color.Black, darkSelected!!.container)
            assertNotEquals(androidx.compose.ui.graphics.Color.White, darkSelected!!.container)
        }
    }

    @Test
    fun wideStrip_bottomCenteredGroup_indexZeroNearestApp() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 4x2 tile (248x120.dp): app + 3 exactly fills the
                // bottom edge at the grid-unit pitch.
                Box(Modifier.size(248.dp, 120.dp)) {
                    AppTile(
                        // 4x2 capacity is columns - 1 = 3: app + 3 centered group.
                        state = AppTileState(label = "Calendar", span = GridSpan(4, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                            PinnedTileShortcut("search", "Search"),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        // Row order is farthest-first: search leftmost, new-post nearest app.
        val farthest = boundsInRoot(composeRule.onNodeWithContentDescription("Search, shortcut"))
        val middle = boundsInRoot(composeRule.onNodeWithContentDescription("Camera, shortcut"))
        val nearest = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val app = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        // Strip sits on the bottom edge, inset by exactly the tile padding.
        val bottomInsetPx = with(composeRule.density) { 8.dp.roundToPx() }
        assertTrue(
            "wide strip must sit on the tile bottom edge " +
                "(tileBottom=${tileBounds.bottom} bottom=${farthest.bottom} insetPx=$bottomInsetPx)",
            abs(tileBounds.bottom - farthest.bottom - bottomInsetPx) <= 2f,
        )
        // Pin order is spatially stable: index 0 nearest the app (rightmost).
        assertTrue(
            "index 0 must be nearest the app (nearestX=${nearest.center.x} farthestX=${farthest.center.x})",
            nearest.center.x > farthest.center.x,
        )
        assertTrue(
            "app glyph must be rightmost (appX=${app.center.x} nearestX=${nearest.center.x})",
            app.center.x > nearest.center.x,
        )
        // Fixed pitch: centers share one row and are evenly spaced.
        assertTrue(
            "wide strip slots must share one row",
            abs(farthest.center.y - nearest.center.y) <= 1f &&
                abs(nearest.center.y - app.center.y) <= 1f,
        )
        assertTrue(
            "wide strip pitch must be fixed",
            abs((nearest.center.x - middle.center.x) - (middle.center.x - farthest.center.x)) <= 1f,
        )
        // Centered group (not BottomEnd): group mid aligns with tile mid.
        val groupCenterX = (farthest.left + app.right) / 2f
        assertTrue(
            "group must mid-align with the tile (groupCenterX=$groupCenterX tileCenterX=${tileBounds.center.x})",
            abs(groupCenterX - tileBounds.center.x) <= 2f,
        )
    }

    @Test
    fun narrowStrip_rightEdgeUpward_indexZeroNearestApp() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 1x3 tile (56x184.dp): app + 2 exactly fills the
                // right edge at the grid-unit pitch.
                Box(Modifier.size(56.dp, 184.dp)) {
                    AppTile(
                        // 1x3 capacity is rows - 1 = 2: app bottom-right + upward.
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 3)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        // Column order is farthest-first (top): camera above, new-post nearest app.
        val farthest = boundsInRoot(composeRule.onNodeWithContentDescription("Camera, shortcut"))
        val nearest = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val app = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        // Right edge: glyphs sit one tile padding from the tile right.
        // (On a realistic single-cell-wide tile the 40.dp column is centered,
        // so edge-ness is the right inset, not the center offset.)
        val insetPx = with(composeRule.density) { 8.dp.toPx() }
        assertTrue(
            "narrow strip must hug the right edge " +
                "(tileRight=${tileBounds.right} firstRight=${farthest.right})",
            abs(tileBounds.right - farthest.right - insetPx) <= 2f,
        )
        assertTrue(
            "app glyph must sit bottom-right (tileRight=${tileBounds.right} appRight=${app.right})",
            abs(tileBounds.right - app.right - insetPx) <= 2f,
        )
        // Upward growth: index 0 sits directly above the app, index 1 above it.
        assertTrue(
            "narrow strip index 0 must be nearest the app " +
                "(nearestY=${nearest.center.y} farthestY=${farthest.center.y} appY=${app.center.y})",
            nearest.center.y > farthest.center.y && app.center.y > nearest.center.y,
        )
        assertTrue(
            "narrow strip slots must share one column",
            abs(farthest.center.x - nearest.center.x) <= 1f &&
                abs(nearest.center.x - app.center.x) <= 1f,
        )
        // Fixed pitch upward.
        assertTrue(
            "narrow strip pitch must be fixed",
            abs((nearest.center.y - farthest.center.y) - (app.center.y - nearest.center.y)) <= 1f,
        )
    }

    @Test
    fun stripPitch_adaptsToMeasuredWidth() {
        // Same 2x2 span at two tile widths side by side: slot pitch derives
        // from the measured edge (64.dp at 120.dp wide, 104.dp at 200.dp
        // wide), never a hard-coded adjacency pitch.
        composeRule.setContent {
            LauncherTheme {
                Row {
                    Box(Modifier.size(120.dp, 120.dp)) {
                        AppTile(
                            state = AppTileState(label = "Narrow", span = GridSpan(2, 2)),
                            onBodyClick = {}, onSymbolClick = {},
                            pinnedShortcuts = listOf(
                                PinnedTileShortcut("narrow-post", "Narrow post"),
                            ),
                        )
                    }
                    Box(Modifier.size(200.dp, 200.dp)) {
                        AppTile(
                            state = AppTileState(label = "Wide", span = GridSpan(2, 2)),
                            onBodyClick = {}, onSymbolClick = {},
                            pinnedShortcuts = listOf(
                                PinnedTileShortcut("wide-post", "Wide post"),
                            ),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val narrowApp = boundsInRoot(
            composeRule.onAllNodesWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true)[0],
        )
        val narrowPin = boundsInRoot(composeRule.onNodeWithContentDescription("Narrow post, shortcut"))
        val narrowPitchPx = narrowApp.center.x - narrowPin.center.x

        val wideApp = boundsInRoot(
            composeRule.onAllNodesWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true)[1],
        )
        val widePin = boundsInRoot(composeRule.onNodeWithContentDescription("Wide post, shortcut"))
        val widePitchPx = wideApp.center.x - widePin.center.x

        val expectedNarrowPx = with(composeRule.density) { 64.dp.toPx() }
        val expectedWidePx = with(composeRule.density) { 104.dp.toPx() }
        assertTrue(
            "pitch must derive from the measured edge " +
                "(narrow=$narrowPitchPx expected=$expectedNarrowPx)",
            abs(narrowPitchPx - expectedNarrowPx) <= 2f,
        )
        assertTrue(
            "pitch must adapt to width (wide=$widePitchPx expected=$expectedWidePx)",
            abs(widePitchPx - expectedWidePx) <= 2f,
        )
    }

    @Test
    fun pinnedStrip_realisticTwoByTwo_overflowStaysInBounds() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 2x2 tile (120x120.dp) with caller overflow: app +
                // pin + node would exceed the edge, so the node yields to body
                // semantics while the pin stays visible; everything rendered
                // stays inside the tile.
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                        overflowCount = 1,
                        showOverflowIndicator = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasStateDescription("1 more shortcuts")).assertExists()
        val app = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val pin = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        for ((name, rect) in listOf("app" to app, "pin" to pin)) {
            assertTrue(
                "$name glyph must stay inside the tile (tile=$tileBounds rect=$rect)",
                rect.left >= tileBounds.left - 1f &&
                    rect.top >= tileBounds.top - 1f &&
                    rect.right <= tileBounds.right + 1f &&
                    rect.bottom <= tileBounds.bottom + 1f,
            )
        }
    }

    @Test
    fun pinnedStrip_realisticOneByTwo_editSelected_appClearsResizeHandle() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic edit-selected 1x2 tile (56x120.dp): the shortcut
                // yields so the app glyph sits above the 48.dp resize corner
                // reserve with no overlap; the hidden pin is announced.
                Box(Modifier.size(56.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                        isEditSelected = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasStateDescription("1 more shortcuts")).assertExists()
        val app = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        // Resize handle in-tile footprint: the 48.dp touch square
        // center-pinned on the bottom-right corner extends half inside.
        val halfPx = with(composeRule.density) { 24.dp.toPx() }
        val resizeLeft = tileBounds.right - halfPx
        val resizeTop = tileBounds.bottom - halfPx
        assertTrue(
            "edit app glyph must clear the resize corner " +
                "(app=$app resizeLeft=$resizeLeft resizeTop=$resizeTop)",
            app.right <= resizeLeft + 1f ||
                app.left >= tileBounds.right - 1f ||
                app.bottom <= resizeTop + 1f ||
                app.top >= tileBounds.bottom - 1f,
        )
        assertTrue(
            "edit app glyph must stay inside the tile (tile=$tileBounds app=$app)",
            app.left >= tileBounds.left - 1f &&
                app.top >= tileBounds.top - 1f &&
                app.right <= tileBounds.right + 1f &&
                app.bottom <= tileBounds.bottom + 1f,
        )
    }

    @Test
    fun pinnedStrip_realisticTwoByTwo_editSelected_yieldsToCenteredControl() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic edit-selected 2x2 tile (120x120.dp): a centered
                // 48.dp remove control leaves no 40.dp slot clear of both
                // controls, so the strip yields entirely and the body
                // announces app + retained pins (nothing deleted).
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                        overflowCount = 1,
                        showOverflowIndicator = false,
                        isEditSelected = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        // Tile body still carries the app name and the retained total.
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(1)
        composeRule.onNode(hasStateDescription("2 more shortcuts")).assertExists()
    }

    @Test
    fun zeroPins_longPressAppAndEmpty_shareTileCallback_noClickOnRelease() {
        var bodyClicks = 0
        var symbolClicks = 0
        var longClicks = 0
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(160.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 1)),
                        onBodyClick = { bodyClicks++ },
                        onSymbolClick = { symbolClicks++ },
                        symbolContentDescription = "Launch Calendar",
                        onLongClick = { longClicks++ },
                        icon = { _ -> Box(Modifier.testTag("tile-icon").size(24.dp)) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Physical long-press on the app symbol hits the launch region but
        // fires the shared tile callback once with no short click on release.
        composeRule.onNodeWithContentDescription("Launch Calendar")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, longClicks)
            assertEquals(0, bodyClicks)
            assertEquals(0, symbolClicks)
        }

        // Physical long-press on empty tile area (top-left corner, outside the
        // centered symbol) hits TileBody and fires the same callback once.
        val tileBounds = boundsInRoot(composeRule.onNodeWithContentDescription("Calendar"))
        val symbolBounds = boundsInRoot(composeRule.onNodeWithContentDescription("Launch Calendar"))
        val insetPx = with(composeRule.density) { 8.dp.toPx() }
        val emptyRoot = Offset(tileBounds.left + insetPx, tileBounds.top + insetPx)
        composeRule.runOnIdle {
            assertFalse(
                "empty probe must sit outside the symbol (empty=$emptyRoot symbol=$symbolBounds)",
                symbolBounds.contains(emptyRoot),
            )
        }
        val emptyLocal = Offset(emptyRoot.x - tileBounds.left, emptyRoot.y - tileBounds.top)
        composeRule.onNodeWithContentDescription("Calendar")
            .performTouchInput { longClick(emptyLocal) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(2, longClicks)
            assertEquals(0, bodyClicks)
            assertEquals(0, symbolClicks)
        }
    }

    @Test
    fun oneShortcut_longPressEveryRegion_sharesTileCallback() {
        var bodyClicks = 0
        var symbolClicks = 0
        val shortcutClicks = mutableListOf<String>()
        var longClicks = 0
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = { bodyClicks++ },
                        onSymbolClick = { symbolClicks++ },
                        onLongClick = { longClicks++ },
                        pinnedShortcuts = listOf(PinnedTileShortcut("new-post", "New post")),
                        onShortcutClick = { shortcutClicks.add(it) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // App strip glyph.
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true)
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, longClicks) }

        // Single shortcut glyph.
        composeRule.onNodeWithContentDescription("New post, shortcut")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(2, longClicks) }

        // Empty area: top-center above the bottom strip.
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val appBounds = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val pinBounds = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val emptyRoot = Offset(
            tileBounds.center.x,
            tileBounds.top + with(composeRule.density) { 4.dp.toPx() },
        )
        composeRule.runOnIdle {
            assertFalse("empty probe must miss app ($appBounds)", appBounds.contains(emptyRoot))
            assertFalse("empty probe must miss pin ($pinBounds)", pinBounds.contains(emptyRoot))
        }
        val emptyLocal = Offset(emptyRoot.x - tileBounds.left, emptyRoot.y - tileBounds.top)
        composeRule.onAllNodesWithContentDescription("Calendar")[0]
            .performTouchInput { longClick(emptyLocal) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(3, longClicks)
            assertEquals(0, bodyClicks)
            assertEquals(0, symbolClicks)
            assertTrue("no short click on release", shortcutClicks.isEmpty())
        }
    }

    @Test
    fun multiShortcuts_longPressEveryRegion_sharesTileCallback() {
        var bodyClicks = 0
        var symbolClicks = 0
        val shortcutClicks = mutableListOf<String>()
        var longClicks = 0
        composeRule.setContent {
            LauncherTheme {
                // Realistic 4x2 tile (248x120.dp): app + 2 exactly fills the
                // bottom edge at the grid-unit pitch.
                Box(Modifier.size(248.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(4, 2)),
                        onBodyClick = { bodyClicks++ },
                        onSymbolClick = { symbolClicks++ },
                        onLongClick = { longClicks++ },
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                        ),
                        onShortcutClick = { shortcutClicks.add(it) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true)
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, longClicks) }

        composeRule.onNodeWithContentDescription("New post, shortcut")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(2, longClicks) }

        composeRule.onNodeWithContentDescription("Camera, shortcut")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(3, longClicks) }

        // Empty area: top-center above the bottom strip.
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val appBounds = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val firstBounds = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val secondBounds = boundsInRoot(composeRule.onNodeWithContentDescription("Camera, shortcut"))
        val emptyRoot = Offset(
            tileBounds.center.x,
            tileBounds.top + with(composeRule.density) { 4.dp.toPx() },
        )
        composeRule.runOnIdle {
            assertFalse("empty probe must miss app ($appBounds)", appBounds.contains(emptyRoot))
            assertFalse("empty probe must miss first ($firstBounds)", firstBounds.contains(emptyRoot))
            assertFalse("empty probe must miss second ($secondBounds)", secondBounds.contains(emptyRoot))
        }
        val emptyLocal = Offset(emptyRoot.x - tileBounds.left, emptyRoot.y - tileBounds.top)
        composeRule.onAllNodesWithContentDescription("Calendar")[0]
            .performTouchInput { longClick(emptyLocal) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(4, longClicks)
            assertEquals(0, bodyClicks)
            assertEquals(0, symbolClicks)
            assertTrue("no short click on release", shortcutClicks.isEmpty())
        }
    }

    @Test
    fun shortTaps_stayTargetSpecific_withSharedLongPress() {
        var bodyClicks = 0
        var symbolClicks = 0
        val shortcutClicks = mutableListOf<String>()
        var longClicks = 0
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(248.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(4, 2)),
                        onBodyClick = { bodyClicks++ },
                        onSymbolClick = { symbolClicks++ },
                        onLongClick = { longClicks++ },
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                        ),
                        onShortcutClick = { shortcutClicks.add(it) },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Short tap on the app glyph routes to the symbol callback only.
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true)
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, symbolClicks)
            assertEquals(0, bodyClicks)
            assertEquals(0, longClicks)
            assertTrue(shortcutClicks.isEmpty())
        }

        // Short tap on one shortcut routes to its own id only.
        composeRule.onNodeWithContentDescription("New post, shortcut")
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf("new-post"), shortcutClicks)
            assertEquals(1, symbolClicks)
            assertEquals(0, bodyClicks)
            assertEquals(0, longClicks)
        }

        // Short tap on empty tile area routes to the body callback only.
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val appBounds = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val firstBounds = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val secondBounds = boundsInRoot(composeRule.onNodeWithContentDescription("Camera, shortcut"))
        val emptyRoot = Offset(
            tileBounds.center.x,
            tileBounds.top + with(composeRule.density) { 4.dp.toPx() },
        )
        composeRule.runOnIdle {
            assertFalse("empty probe must miss app ($appBounds)", appBounds.contains(emptyRoot))
            assertFalse("empty probe must miss first ($firstBounds)", firstBounds.contains(emptyRoot))
            assertFalse("empty probe must miss second ($secondBounds)", secondBounds.contains(emptyRoot))
        }
        val emptyLocal = Offset(emptyRoot.x - tileBounds.left, emptyRoot.y - tileBounds.top)
        composeRule.onAllNodesWithContentDescription("Calendar")[0]
            .performTouchInput { click(emptyLocal) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, bodyClicks)
            assertEquals(1, symbolClicks)
            assertEquals(listOf("new-post"), shortcutClicks)
            assertEquals(0, longClicks)
        }
    }

    @Test
    fun pinLifecycle_noPin_firstPin_lastRemoval_restoresCentered() {
        // State-driven C-B transition on a realistic 2x2 tile (120x120.dp):
        // zero pins keeps the large centered icon with no strip reserve,
        // the first pin switches to the compact strip, and removing the last
        // pin restores the centered treatment (extent + position + tags).
        val pins = mutableStateOf(emptyList<PinnedTileShortcut>())
        var capturedExtent: Dp? = null
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(120.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(2, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        // Test-only probe tag: the slot receives the large
                        // centered extent while pin-free and the compact unit
                        // extent inside the strip, so the extent proves which
                        // path rendered without a prod tag.
                        icon = { extent ->
                            capturedExtent = extent
                            Box(Modifier.testTag("lifecycle-probe-icon").size(24.dp))
                        },
                        pinnedShortcuts = pins.value,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 0 pins: min(120 - 2 * 8) = 104.dp centered icon, no strip.
        composeRule.runOnIdle { assertEquals(104.dp, capturedExtent) }
        composeRule.onNodeWithTag("lifecycle-probe-icon", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertDoesNotExist()
        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        var probeBounds =
            boundsInRoot(composeRule.onNodeWithTag("lifecycle-probe-icon", useUnmergedTree = true))
        assertTrue(
            "zero-pin probe must center (tile=$tileBounds probe=$probeBounds)",
            abs(probeBounds.center.x - tileBounds.center.x) <= 2f &&
                abs(probeBounds.center.y - tileBounds.center.y) <= 2f,
        )

        // First pin: compact strip at the unit extent with app + shortcut tags.
        composeRule.runOnIdle {
            pins.value = listOf(PinnedTileShortcut("new-post", "New post"))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(40.dp, capturedExtent) }
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()

        // Last removal: centered treatment returns, strip tags gone.
        composeRule.runOnIdle { pins.value = emptyList() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(104.dp, capturedExtent) }
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        probeBounds =
            boundsInRoot(composeRule.onNodeWithTag("lifecycle-probe-icon", useUnmergedTree = true))
        assertTrue(
            "probe must re-center after last removal (tile=$tileBounds probe=$probeBounds)",
            abs(probeBounds.center.x - tileBounds.center.x) <= 2f &&
                abs(probeBounds.center.y - tileBounds.center.y) <= 2f,
        )
        // Body + symbol share the label again; no strip glyphs remain.
        composeRule.onAllNodesWithContentDescription("Calendar").assertCountEquals(2)
    }

    @Test
    fun stripOrder_horizontal_pinZeroAdjacent_appRightmost() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 4x2 tile (248x120.dp): app + 3 exactly fills the
                // padded edge at the 64.dp grid-unit pitch.
                Box(Modifier.size(248.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(4, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                            PinnedTileShortcut("search", "Search"),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val app = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val pin0 = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val pin1 = boundsInRoot(composeRule.onNodeWithContentDescription("Camera, shortcut"))
        val pin2 = boundsInRoot(composeRule.onNodeWithContentDescription("Search, shortcut"))
        // App rightmost: every pin sits left of the app.
        assertTrue(
            "app must be rightmost (appX=${app.center.x} pin0X=${pin0.center.x})",
            pin0.center.x < app.center.x &&
                pin1.center.x < app.center.x &&
                pin2.center.x < app.center.x,
        )
        // Pin0 adjacent: one grid-unit pitch left of the app, sharing its row.
        val pitchPx = with(composeRule.density) { 64.dp.toPx() }
        assertTrue(
            "pin0 must sit one pitch left of the app " +
                "(dx=${app.center.x - pin0.center.x} pitch=$pitchPx)",
            abs(app.center.x - pin0.center.x - pitchPx) <= 2f,
        )
        assertTrue(
            "pin0 must share the app row",
            abs(pin0.center.y - app.center.y) <= 1f,
        )
        // Far end extends leftward in pin order.
        assertTrue(
            "pins must extend leftward pin0 nearest app",
            pin2.center.x < pin1.center.x && pin1.center.x < pin0.center.x,
        )
        for ((name, rect) in listOf("app" to app, "pin0" to pin0, "pin1" to pin1, "pin2" to pin2)) {
            assertIconInsideTile(tileBounds, rect, name)
        }
    }

    @Test
    fun stripOrder_vertical_pinZeroAbove_appBottommost() {
        composeRule.setContent {
            LauncherTheme {
                // Realistic 1x3 tile (56x184.dp): app + 2 exactly fills the
                // right edge at the 64.dp grid-unit pitch.
                Box(Modifier.size(56.dp, 184.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(1, 3)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val app = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val pin0 = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val pin1 = boundsInRoot(composeRule.onNodeWithContentDescription("Camera, shortcut"))
        // App bottommost: every pin sits above the app.
        assertTrue(
            "app must be bottommost (appY=${app.center.y} pin0Y=${pin0.center.y})",
            pin0.center.y < app.center.y && pin1.center.y < app.center.y,
        )
        // Pin0 adjacent: one grid-unit pitch above the app, sharing its column.
        val pitchPx = with(composeRule.density) { 64.dp.toPx() }
        assertTrue(
            "pin0 must sit one pitch above the app " +
                "(dy=${app.center.y - pin0.center.y} pitch=$pitchPx)",
            abs(app.center.y - pin0.center.y - pitchPx) <= 2f,
        )
        assertTrue(
            "pin0 must share the app column",
            abs(pin0.center.x - app.center.x) <= 1f,
        )
        // Far end grows upward in pin order.
        assertTrue(
            "pins must extend upward pin0 nearest app",
            pin1.center.y < pin0.center.y,
        )
        for ((name, rect) in listOf("app" to app, "pin0" to pin0, "pin1" to pin1)) {
            assertIconInsideTile(tileBounds, rect, name)
        }
    }

    @Test
    fun overflowNode_groupMath_noOverlapInsideTile() {
        // 248x120 4x2 tile, 2 visibles + 1 caller overflow: app + 2 + node is
        // 4 slots, 40 + 3 * 64 = 232 = 248 - 2 * 8, exactly filling the
        // padded edge — the cross-contract group math the fit must honor.
        assertEquals(232L, ShortcutStripGeometry.stripGroupExtentPx(40L, 64L, 4))
        composeRule.setContent {
            LauncherTheme {
                Box(Modifier.size(248.dp, 120.dp)) {
                    AppTile(
                        state = AppTileState(label = "Calendar", span = GridSpan(4, 2)),
                        onBodyClick = {}, onSymbolClick = {},
                        pinnedShortcuts = listOf(
                            PinnedTileShortcut("new-post", "New post"),
                            PinnedTileShortcut("camera", "Camera"),
                        ),
                        overflowCount = 1,
                        showOverflowIndicator = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val tileBounds = boundsInRoot(composeRule.onAllNodesWithContentDescription("Calendar")[0])
        val node = boundsInRoot(
            composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true),
        )
        val app = boundsInRoot(
            composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true),
        )
        val pin0 = boundsInRoot(composeRule.onNodeWithContentDescription("New post, shortcut"))
        val pin1 = boundsInRoot(composeRule.onNodeWithContentDescription("Camera, shortcut"))
        val glyphs = listOf("node" to node, "pin1" to pin1, "pin0" to pin0, "app" to app)
        // No overlap: every pair of glyphs is disjoint.
        for (i in glyphs.indices) {
            for (j in i + 1 until glyphs.size) {
                val (nameA, rectA) = glyphs[i]
                val (nameB, rectB) = glyphs[j]
                assertTrue(
                    "$nameA ($rectA) must not overlap $nameB ($rectB)",
                    rectA.right <= rectB.left + 1f || rectB.right <= rectA.left + 1f ||
                        rectA.bottom <= rectB.top + 1f || rectB.bottom <= rectA.top + 1f,
                )
            }
        }
        // Nothing leaves the tile.
        for ((name, rect) in glyphs) {
            assertIconInsideTile(tileBounds, rect, name)
        }
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

    private fun assertIconInsideTile(tileBounds: Rect, iconBounds: Rect, name: String = "icon") {
        assertTrue(
            "$name must stay inside the tile " +
                "(tile=$tileBounds $name=$iconBounds)",
            iconBounds.left >= tileBounds.left - 1f &&
                iconBounds.top >= tileBounds.top - 1f &&
                iconBounds.right <= tileBounds.right + 1f &&
                iconBounds.bottom <= tileBounds.bottom + 1f,
        )
    }
}
