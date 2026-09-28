package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.PIN_TOGGLE_TAG
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.designsystem.ROW_LAUNCH_ZONE_TAG
import me.foxtails.lakehead.core.designsystem.SHORTCUT_SURFACE_ROW_TAG
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose tests for the B4 edit-mode shortcut menu above the selection.
 *
 * Covers the above-frame viewport, caller rank order, launch vs pin routing,
 * zero-activation dismiss on empty-tap/`Back`/removal, and none → no node.
 * Move/resize-start dismissal is covered at the pure level by
 * `EditShortcutMenuTest` (`isEditShortcutMenuVisible` gates on a null edit
 * session); these composition tests drive the selection paths that remain
 * reachable from the wiggle-editing flow. Requires a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class EditShortcutMenuComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val homeId = HomeId("home-edit-menu-test")

    private fun identity(profile: String, pkg: String, cls: String) =
        AppIdentity(ProfileId(profile), ComponentIdentity(pkg, cls))

    private val personal = identity("0", "com.example.app", "com.example.app.Main")
    private val work = identity("10", "com.example.app", "com.example.app.Main")

    private fun tile(
        item: String,
        appIdentity: AppIdentity,
        label: String?,
        column: Int = 0,
        row: Int = 3,
        spanColumns: Int = 1,
        spanRows: Int = 1,
    ): HomeRenderTile {
        val launchable = label != null
        return HomeRenderTile(
            itemId = ItemId(item),
            appIdentity = appIdentity,
            position = LogicalPosition(column, row),
            span = LogicalSpan(spanColumns, spanRows),
            availability = ItemAvailability.AVAILABLE,
            label = label,
            icon = null,
            isLaunchable = launchable,
        )
    }

    private fun entry(id: String) = ShortcutSurfaceEntry(
        id = id,
        label = "Shortcut $id",
        isPinned = false,
    )

    private fun loaded(tiles: List<HomeRenderTile>) = HomeRenderState.Loaded(
        requestedHomeId = homeId,
        tiles = tiles,
        columns = GridColumns(6),
        extent = GridVerticalExtent(4L, 10L),
        window = GridRowWindow(0L, 10L),
    )

    private val executed = mutableListOf<Pair<ItemId, String>>()
    private val pinned = mutableListOf<Pair<ItemId, String>>()

    private fun setMenuCanvas(
        state: HomeRenderState,
        entries: List<ShortcutSurfaceEntry>,
    ) {
        executed.clear()
        pinned.clear()
        composeRule.setContent {
            // LauncherTheme is colour-only (no typography/shapes) and supplies
            // the LocalLauncherDimensions production now requires; it does not
            // affect the menu's content-hugging measurements below.
            LauncherTheme {
                HomeCanvas(
                    state = state,
                    iconsByIdentity = emptyMap(),
                    onLaunchApp = {},
                    onRequestDefaultHome = {},
                    surfaceEntries = entries,
                    onSurfaceExecute = { itemId, entryId -> executed.add(itemId to entryId) },
                    onSurfaceTogglePin = { itemId, entryId -> pinned.add(itemId to entryId) },
                )
            }
        }
    }

    private fun longPressBody(label: String) {
        composeRule.onAllNodesWithContentDescription(label)[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
    }

    private fun menuSizePx() =
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().size

    @Test
    fun singleShortShortcut_isNarrowShortBox() {
        val narrow = ShortcutSurfaceEntry(id = "s1", label = "Go", isPinned = false)
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(narrow))
        composeRule.waitForIdle()
        longPressBody("Deep App")

        val size = menuSizePx()
        val capPx = with(composeRule.density) { 320.dp.roundToPx() }
        val minPx = with(composeRule.density) { 96.dp.roundToPx() }
        val wellBelowPx = with(composeRule.density) { 32.dp.roundToPx() }
        val shortHeightPx = with(composeRule.density) { 120.dp.roundToPx() }
        composeRule.runOnIdle {
            assertTrue(
                "single short width ${size.width} must hug content well below 320dp cap $capPx",
                size.width in minPx - 2 until capPx - wellBelowPx,
            )
            assertTrue(
                "single short height ${size.height} must stay short (< 120dp = $shortHeightPx)",
                size.height < shortHeightPx,
            )
        }
    }

    @Test
    fun threeShortShortcuts_keepNarrowWidthButGrowTaller() {
        fun shortEntries(count: Int) = List(count) { index ->
            ShortcutSurfaceEntry(id = "s${index + 1}", label = "Go", isPinned = false)
        }
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), shortEntries(1))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        val one = menuSizePx()

        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), shortEntries(3))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        val three = menuSizePx()

        val oneRowPx = with(composeRule.density) { 48.dp.roundToPx() }
        composeRule.runOnIdle {
            assertTrue(
                "3-row width ${three.width} must match 1-row width ${one.width} (same short text)",
                kotlin.math.abs(three.width - one.width) <= 2,
            )
            assertTrue(
                "3-row height ${three.height} must grow with count vs 1-row ${one.height}",
                three.height > one.height + oneRowPx,
            )
        }
    }

    @Test
    fun longLabel_capsAtFrameNotWider() {
        val narrow = ShortcutSurfaceEntry(id = "s1", label = "Go", isPinned = false)
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(narrow))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        val narrowWidth = menuSizePx().width

        val long = ShortcutSurfaceEntry(id = "s1", label = "L".repeat(160), isPinned = false)
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(long))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        val capped = menuSizePx()
        val gridWidth = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().size.width
        val edgePx = with(composeRule.density) { 16.dp.roundToPx() }
        val frameCapPx = minOf(
            with(composeRule.density) { 320.dp.roundToPx() },
            gridWidth - 2 * edgePx,
        )
        val tolerancePx = with(composeRule.density) { 8.dp.roundToPx() }
        composeRule.runOnIdle {
            assertTrue(
                "long label width ${capped.width} must exceed narrow $narrowWidth (follows text)",
                capped.width > narrowWidth,
            )
            assertTrue(
                "long label width ${capped.width} must not exceed frame cap $frameCapPx",
                capped.width <= frameCapPx + 2,
            )
            assertTrue(
                "long label width ${capped.width} must reach frame cap $frameCapPx " +
                    "(ellipsis in ShortcutSurfaceRow maxLines=1 handles overflow)",
                capped.width >= frameCapPx - tolerancePx,
            )
        }
        // The capped row still renders (single-line ellipsis path) instead of
        // widening the box or dropping the node.
        composeRule.onAllNodesWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun longPress_showsMenuAboveSelectedTile() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()

        longPressBody("Deep App")

        val menu = composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val body = composeRule.onAllNodesWithContentDescription("Deep App")[0]
            .fetchSemanticsNode()
        val menuBottom = menu.positionInRoot.y + menu.size.height
        composeRule.runOnIdle {
            assertTrue(
                "menu bottom $menuBottom must sit above tile top ${body.positionInRoot.y}",
                menuBottom <= body.positionInRoot.y + 1.5f,
            )
        }
    }

    @Test
    fun rows_renderInCallerRankOrder() {
        val entries = listOf(entry("s1"), entry("s2"), entry("s3"))
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), entries)
        composeRule.waitForIdle()
        longPressBody("Deep App")

        val zones = composeRule.onAllNodesWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)
        zones.assertCountEquals(3)
        composeRule.onAllNodesWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)
            .assertCountEquals(3)
        val labels = zones.fetchSemanticsNodes().map { node ->
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .firstOrNull()
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf("Shortcut s1", "Shortcut s2", "Shortcut s3"),
                labels,
            )
        }
    }

    @Test
    fun launchZone_executesWithoutPin_pinToggle_pinsWithoutExecute() {
        val tiles = listOf(tile("a", personal, "Deep App"))
        setMenuCanvas(loaded(tiles), listOf(entry("s1"), entry("s2")))
        composeRule.waitForIdle()
        longPressBody("Deep App")

        composeRule.onAllNodesWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)[0]
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("a") to "s1"), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }

        composeRule.onAllNodesWithTag(PIN_TOGGLE_TAG, useUnmergedTree = true)[1]
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("a") to "s1"), executed)
            assertEquals(listOf(ItemId("a") to "s2"), pinned)
        }
    }

    @Test
    fun tapOther_transfersMenuToNewSelection() {
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0),
            tile("b", work, "Beta App", column = 1),
        )
        setMenuCanvas(loaded(tiles), listOf(entry("s1")))
        composeRule.waitForIdle()
        longPressBody("Alpha App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        // Tap-other transfers with no Normal round-trip; the menu follows.
        composeRule.onAllNodesWithContentDescription("Beta App")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Beta App, row 3 column 1"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onAllNodesWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)[0]
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("b") to "s1"), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }

    @Test
    fun emptyTap_dismissesMenuWithZeroActivation() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
            .performTouchInput { click(Offset(centerX.toFloat(), (height - 20).toFloat())) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(emptyList<Pair<ItemId, String>>(), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }

    @Test
    fun back_dismissesMenuWithZeroActivation() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()
        longPressBody("Deep App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(emptyList<Pair<ItemId, String>>(), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }

    @Test
    fun noEditing_showsNoMenuNode() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun zeroShortcuts_showsNoMenuNode() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), emptyList())
        composeRule.waitForIdle()
        longPressBody("Deep App")

        // Editing engages but no empty box is rendered.
        composeRule.onNode(hasStateDescription("Editing Deep App, row 3 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun rows_keepFixedHeightNeverStretch() {
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), listOf(entry("s1")))
        composeRule.waitForIdle()
        longPressBody("Deep App")

        val rowPx = with(composeRule.density) { 56.dp.roundToPx() }
        val rows = composeRule.onAllNodesWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)
        rows.assertCountEquals(1)
        val height = rows[0].fetchSemanticsNode().size.height
        composeRule.runOnIdle {
            assertTrue(
                "single row height $height must equal the fixed 56dp row $rowPx (no stretch)",
                kotlin.math.abs(height - rowPx) <= 2,
            )
        }
    }

    @Test
    fun manyRows_capAtViewportAndLastRowReachableByScroll() {
        val many = List(20) { index -> entry("s$index") }
        setMenuCanvas(loaded(listOf(tile("a", personal, "Deep App"))), many)
        composeRule.waitForIdle()
        longPressBody("Deep App")

        val menu = composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val gridHeight = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().size.height
        composeRule.runOnIdle {
            assertTrue(
                "20-row menu height ${menu.size.height} must respect the viewport cap $gridHeight",
                menu.size.height <= gridHeight + 2,
            )
        }
        composeRule.onAllNodesWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)
            .assertCountEquals(20)

        // The last row scrolls into view inside the capped menu.
        val zones = composeRule.onAllNodesWithTag(ROW_LAUNCH_ZONE_TAG, useUnmergedTree = true)
        zones[19].performScrollTo()
        composeRule.waitForIdle()
        val labels = zones.fetchSemanticsNodes().map { node ->
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .firstOrNull()
        }
        composeRule.runOnIdle {
            assertEquals("Shortcut s19", labels[19])
        }
        zones[19].assertIsDisplayed()
    }

    @Test
    fun updates_remeasureGrowAndShrink() {
        val holder = androidx.compose.runtime.mutableStateOf(listOf(entry("s1")))
        executed.clear()
        pinned.clear()
        composeRule.setContent {
            LauncherTheme {
                HomeCanvas(
                    state = loaded(listOf(tile("a", personal, "Deep App"))),
                    iconsByIdentity = emptyMap(),
                    onLaunchApp = {},
                    onRequestDefaultHome = {},
                    surfaceEntries = holder.value,
                    onSurfaceExecute = { itemId, entryId -> executed.add(itemId to entryId) },
                    onSurfaceTogglePin = { itemId, entryId -> pinned.add(itemId to entryId) },
                )
            }
        }
        composeRule.waitForIdle()
        longPressBody("Deep App")
        val one = menuSizePx()

        composeRule.runOnIdle {
            holder.value = listOf(entry("s1"), entry("s2"), entry("s3"))
        }
        composeRule.waitForIdle()
        val three = menuSizePx()
        val oneRowPx = with(composeRule.density) { 48.dp.roundToPx() }
        composeRule.runOnIdle {
            assertTrue(
                "3-row height ${three.height} must grow vs 1-row ${one.height}",
                three.height > one.height + oneRowPx,
            )
        }

        composeRule.runOnIdle { holder.value = listOf(entry("s1")) }
        composeRule.waitForIdle()
        val backToOne = menuSizePx()
        composeRule.runOnIdle {
            assertTrue(
                "shrunk height ${backToOne.height} must return to 1-row ${one.height}",
                kotlin.math.abs(backToOne.height - one.height) <= 2,
            )
        }
    }

    @Test
    fun pinnedAndUnpinned_sameLabelSameHeight() {
        val label = "Shared label"
        setMenuCanvas(
            loaded(listOf(tile("a", personal, "Deep App"))),
            listOf(ShortcutSurfaceEntry(id = "s1", label = label, isPinned = false)),
        )
        composeRule.waitForIdle()
        longPressBody("Deep App")
        val unpinned = composeRule.onAllNodesWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)[0]
            .fetchSemanticsNode().size.height

        setMenuCanvas(
            loaded(listOf(tile("a", personal, "Deep App"))),
            listOf(ShortcutSurfaceEntry(id = "s1", label = label, isPinned = true)),
        )
        composeRule.waitForIdle()
        longPressBody("Deep App")
        val pinnedHeight = composeRule.onAllNodesWithTag(SHORTCUT_SURFACE_ROW_TAG, useUnmergedTree = true)[0]
            .fetchSemanticsNode().size.height

        composeRule.runOnIdle {
            assertEquals(unpinned, pinnedHeight)
        }
    }

    @Test
    fun removedTile_dismissesMenuWithZeroActivation() {
        val holder = androidx.compose.runtime.mutableStateOf(
            listOf(tile("a", personal, "Deep App"), tile("b", work, "Beta App")),
        )
        executed.clear()
        pinned.clear()
        composeRule.setContent {
            LauncherTheme {
                HomeCanvas(
                    state = loaded(holder.value),
                    iconsByIdentity = emptyMap(),
                    onLaunchApp = {},
                    onRequestDefaultHome = {},
                    surfaceEntries = listOf(entry("s1")),
                    onSurfaceExecute = { itemId, entryId -> executed.add(itemId to entryId) },
                    onSurfaceTogglePin = { itemId, entryId -> pinned.add(itemId to entryId) },
                )
            }
        }
        composeRule.waitForIdle()
        longPressBody("Deep App")
        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()

        // Uninstall/removal: the selected tile leaves the loaded tiles.
        composeRule.runOnIdle { holder.value = listOf(tile("b", work, "Beta App")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(emptyList<Pair<ItemId, String>>(), executed)
            assertEquals(emptyList<Pair<ItemId, String>>(), pinned)
        }
    }

    @Test
    fun narrowViewport_menuNeverWiderThanFrame() {
        // P2: avail < 128dp leaves a capped frame under the 96dp floor
        // (100 - 2*16 = 68dp). The effective minimum follows the frame, so the
        // box measures inside it instead of crashing or overflowing.
        val availDp = 100.dp
        val availPx = with(composeRule.density) { availDp.roundToPx() }
        val cellPx = (availPx / 6).coerceAtLeast(1)
        val geometry = SquareCellGeometry(
            columnCount = 6,
            cellSizePx = cellPx,
            columnGapPx = 0,
            rowGapPx = 0,
        )
        val menuTile = tile("a", personal, "Deep App")
        val narrow = ShortcutSurfaceEntry(id = "s1", label = "Go", isPinned = false)
        val visibleBottomPx = with(composeRule.density) { 800.dp.roundToPx() }
        composeRule.setContent {
            LauncherTheme {
                Box(modifier = Modifier.width(availDp)) {
                    EditShortcutMenu(
                        tile = menuTile,
                        entries = listOf(narrow),
                        geometry = geometry,
                        originX = 0,
                        rowOrigin = 0L,
                        originYPx = 0L,
                        availableWidthPx = availPx,
                        visibleTopPx = 0,
                        visibleBottomPx = visibleBottomPx,
                        onSurfaceExecute = { _, _ -> },
                        onSurfaceTogglePin = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // No crash reaching here; the menu renders inside the narrow frame.
        val menu = composeRule.onNodeWithTag(EDIT_SHORTCUT_MENU_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val edgePx = with(composeRule.density) { 16.dp.roundToPx() }
        val maxPx = with(composeRule.density) { 320.dp.roundToPx() }
        val rowPx = with(composeRule.density) { 56.dp.roundToPx() }
        val paddingPx = with(composeRule.density) { 8.dp.roundToPx() }
        val frameWidthPx = editShortcutMenuSizePx(
            availableWidthPx = availPx,
            edgeMarginPx = edgePx,
            maxWidthPx = maxPx,
            rowPx = rowPx,
            paddingPx = paddingPx,
            entryCount = 1,
        ).widthPx
        composeRule.runOnIdle {
            assertTrue(
                "narrow avail $availPx leaves frame $frameWidthPx under 96dp; " +
                    "menu width ${menu.size.width} must stay inside the frame",
                menu.size.width <= frameWidthPx + 2,
            )
            assertTrue(
                "menu width ${menu.size.width} must stay inside narrow avail $availPx",
                menu.size.width <= availPx + 2,
            )
        }
    }
}
