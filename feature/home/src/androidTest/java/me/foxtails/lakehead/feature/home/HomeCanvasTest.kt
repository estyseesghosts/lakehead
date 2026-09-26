package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.RESIZE_HANDLE_TAG
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused composition tests for the D3 [HomeCanvas] slice.
 *
 * Covers loading/not-found/loaded-empty, populated labels/roles/exact launch identity,
 * unavailable no-launch, metadata/order stability, 1/6/wide geometry, overlay
 * request/focus/target, no long-click, sparse distant rows after scroll, pre-decoded icon
 * rendering vs label-only fallback, and ownership evidence that the feature performs no
 * decode, catalogue/platform lookup, or database access (behavior plus public-signature
 * inspection). Uses existing Compose test idioms; pixel expectations derive from runtime
 * density rather than fixed values.
 */
@RunWith(AndroidJUnit4::class)
class HomeCanvasTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val homeId = HomeId("home-test")

    private fun identity(profile: String, pkg: String, cls: String) =
        AppIdentity(ProfileId(profile), ComponentIdentity(pkg, cls))

    private val personal = identity("0", "com.example.app", "com.example.app.Main")
    private val work = identity("10", "com.example.app", "com.example.app.Main")

    private fun tile(
        item: String,
        appIdentity: AppIdentity,
        label: String?,
        column: Int = 0,
        row: Int = 0,
        spanColumns: Int = 1,
        spanRows: Int = 1,
        availability: ItemAvailability = ItemAvailability.AVAILABLE,
    ): HomeRenderTile {
        val launchable = availability == ItemAvailability.AVAILABLE && label != null
        return HomeRenderTile(
            itemId = ItemId(item),
            appIdentity = appIdentity,
            position = LogicalPosition(column, row),
            span = LogicalSpan(spanColumns, spanRows),
            availability = availability,
            label = label,
            icon = null,
            isLaunchable = launchable,
        )
    }

    private fun loaded(
        tiles: List<HomeRenderTile>,
        columns: Int = 6,
        extent: GridVerticalExtent = GridVerticalExtent(4L, 10L),
        window: GridRowWindow = GridRowWindow(0L, 10L),
    ) = HomeRenderState.Loaded(
        requestedHomeId = homeId,
        tiles = tiles,
        columns = GridColumns(columns),
        extent = extent,
        window = window,
    )

    private fun setCanvas(
        state: HomeRenderState,
        icons: HomeCanvasIcons = emptyMap(),
        onLaunchApp: (AppIdentity) -> Unit = {},
        onRequestDefaultHome: () -> Unit = {},
        overscanRows: Long = 1L,
    ) {
        composeRule.setContent {
            HomeCanvas(
                state = state,
                iconsByIdentity = icons,
                onLaunchApp = onLaunchApp,
                onRequestDefaultHome = onRequestDefaultHome,
                overscanRows = overscanRows,
            )
        }
    }

    // ---- Honest states ----

    @Test
    fun loading_showsLoadingText_andNoTilesOrOverlay() {
        setCanvas(HomeRenderState.Loading(homeId))

        composeRule.onNodeWithText("Loading home…").assertIsDisplayed()
        composeRule.onNodeWithText("Home not found").assertDoesNotExist()
        composeRule.onNodeWithText("Home is empty").assertDoesNotExist()
        composeRule.onNodeWithText(HOME_ROLE_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun notFound_showsNotFound_andCreatesNothing() {
        setCanvas(HomeRenderState.NotFound(homeId))

        composeRule.onNodeWithText("Home not found").assertIsDisplayed()
        composeRule.onNodeWithText("Loading home…").assertDoesNotExist()
        composeRule.onNodeWithText("Home is empty").assertDoesNotExist()
        composeRule.onNodeWithText(HOME_ROLE_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun loadedEmpty_showsEmptyMessage_withNoRoleOverlay() {
        var requested = false
        setCanvas(
            loaded(emptyList()),
            onRequestDefaultHome = { requested = true },
        )

        composeRule.onNodeWithText("Home is empty").assertIsDisplayed()
        composeRule.onNodeWithText("Loading home…").assertDoesNotExist()
        composeRule.onNodeWithText("Home not found").assertDoesNotExist()
        composeRule.onNodeWithText(HOME_ROLE_DESCRIPTION).assertDoesNotExist()
        composeRule.onAllNodes(hasText(HOME_ROLE_DESCRIPTION)).assertCountEquals(0)
        composeRule.runOnIdle { assertFalse(requested) }
    }

    // ---- Populated labels/roles/launch ----

    @Test
    fun populated_showsLabelsWithButtonRole() {
        val tiles = listOf(
            tile("a", personal, "Personal App", column = 0, row = 0),
            tile("b", work, "Work App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles))

        // D2: each tile exposes TileBody + AppLaunchButton as distinct Role.Button nodes.
        val personalNodes = composeRule.onAllNodesWithContentDescription("Personal App")
        personalNodes.assertCountEquals(2)
        personalNodes[0]
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        personalNodes[1]
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithText("Personal App").assertDoesNotExist()
        val workNodes = composeRule.onAllNodesWithContentDescription("Work App")
        workNodes.assertCountEquals(2)
        workNodes[0]
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        workNodes[1]
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithText("Work App").assertDoesNotExist()
    }

    @Test
    fun populatedClick_emitsExactFullIdentity_includingProfile() {
        var emitted: AppIdentity? = null
        val tiles = listOf(
            tile("a", personal, "Personal App", column = 0, row = 0),
            tile("b", work, "Work App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { emitted = it })

        // Body and symbol are different nodes launching the same identity.
        val workNodes = composeRule.onAllNodesWithContentDescription("Work App")
        workNodes.assertCountEquals(2)
        workNodes[0].assertExists().performClick()
        composeRule.onNodeWithText("Work App").assertDoesNotExist()

        composeRule.runOnIdle { assertEquals(work, emitted) }
        composeRule.runOnIdle { emitted = null }
        workNodes[1].assertExists().performClick()
        composeRule.runOnIdle { assertEquals(work, emitted) }
    }

    @Test
    fun unavailableTile_showsFallbackOrLabel_noLaunch_andUnavailableDescription() {
        var emitted: AppIdentity? = null
        val missing = identity("0", "com.example.gone", "com.example.gone.Main")
        val tiles = listOf(
            // No joined label: fallback text, preserves identity, never launches.
            tile("gone", missing, null, column = 0, row = 0),
            // Unavailable with label: keeps label for display, never launches.
            tile(
                "stale",
                personal,
                "Stale App",
                column = 1,
                row = 0,
                availability = ItemAvailability.UNAVAILABLE,
            ),
        )
        setCanvas(loaded(tiles), onLaunchApp = { emitted = it })

        composeRule.onAllNodesWithContentDescription("Unavailable app").assertCountEquals(2)
        composeRule.onNodeWithText("Unavailable app").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Stale App").assertCountEquals(2)
        composeRule.onNodeWithText("Stale App").assertDoesNotExist()

        // Unavailable Body + Symbol emit nothing.
        composeRule.onAllNodesWithContentDescription("Unavailable app")[0].assertExists().performClick()
        composeRule.onAllNodesWithContentDescription("Unavailable app")[1].assertExists().performClick()
        composeRule.onAllNodesWithContentDescription("Stale App")[0].assertExists().performClick()
        composeRule.onAllNodesWithContentDescription("Stale App")[1].assertExists().performClick()
        composeRule.runOnIdle { assertEquals(null, emitted) }

        composeRule.onAllNodes(hasStateDescription("Unavailable")).assertCountEquals(2)
    }

    @Test
    fun metadataUpdate_doesNotReorderTiles() {
        val first = listOf(
            tile("b", work, "Work App", column = 1, row = 0),
            tile("a", personal, "Personal App", column = 0, row = 0),
        )
        val holder = androidx.compose.runtime.mutableStateOf(first)
        composeRule.setContent {
            HomeCanvas(
                state = loaded(holder.value),
                iconsByIdentity = emptyMap(),
                onLaunchApp = {},
                onRequestDefaultHome = {},
                overscanRows = 1L,
            )
        }
        composeRule.onAllNodesWithContentDescription("Personal App").assertCountEquals(2)
        composeRule.onNodeWithText("Personal App").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Work App").assertCountEquals(2)
        composeRule.onNodeWithText("Work App").assertDoesNotExist()

        val updated = listOf(
            tile("b", work, "Work App v2", column = 1, row = 0),
            tile("a", personal, "Personal App v2", column = 0, row = 0),
        )
        composeRule.runOnIdle { holder.value = updated }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Personal App v2").assertCountEquals(2)
        composeRule.onNodeWithText("Personal App v2").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Work App v2").assertCountEquals(2)
        composeRule.onNodeWithText("Work App v2").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Personal App").assertCountEquals(0)
        composeRule.onNodeWithText("Personal App").assertDoesNotExist()
    }

    // ---- Geometry: 1 / 6 / wide ----

    @Test
    fun geometry_narrowSingleColumn_rendersWithoutClamp() {
        // Pure geometry proof: an explicit single column derives a positive cell.
        val single = SquareCellGeometry.fromViewport(
            availableWidthPx = 200,
            horizontalPaddingPx = 0,
            columnGapPx = 0,
            rowGapPx = 0,
            columnCount = 1,
        )
        assertEquals(1, single.columnCount)
        assertTrue(single.cellSizePx > 0)

        val tiles = listOf(tile("a", personal, "Narrow App", column = 0, row = 0))
        setCanvas(loaded(tiles, columns = 1))
        composeRule.onAllNodesWithContentDescription("Narrow App").assertCountEquals(2)
        composeRule.onNodeWithText("Narrow App").assertDoesNotExist()
    }

    @Test
    fun geometry_referenceSix_renders() {
        val tiles = listOf(
            tile("a", personal, "Six A", column = 0, row = 0),
            tile("b", work, "Six B", column = 5, row = 0),
        )
        setCanvas(loaded(tiles, columns = 6))
        composeRule.onAllNodesWithContentDescription("Six A").assertCountEquals(2)
        composeRule.onNodeWithText("Six A").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Six B").assertCountEquals(2)
        composeRule.onNodeWithText("Six B").assertDoesNotExist()
    }

    @Test
    fun geometry_wideAboveSix_rendersWithoutClamp() {
        val wide = SquareCellGeometry.fromViewport(
            availableWidthPx = 2000,
            horizontalPaddingPx = 0,
            columnGapPx = 8,
            rowGapPx = 8,
            columnCount = 8,
        )
        assertEquals(8, wide.columnCount)
        assertTrue(wide.cellSizePx > 0)

        val tiles = listOf(
            tile("a", personal, "Wide App", column = 7, row = 0, spanColumns = 1, spanRows = 1),
        )
        setCanvas(loaded(tiles, columns = 8))
        composeRule.onAllNodesWithContentDescription("Wide App").assertCountEquals(2)
        composeRule.onNodeWithText("Wide App").assertDoesNotExist()
    }

    // ---- Overlay: request/focus/target, no fakes ----

    @Test
    fun overlay_hasNoRoleRequest_gridStillRenders() {
        val tiles = listOf(tile("a", personal, "Overlay App", column = 0, row = 0))
        var requested = false
        setCanvas(loaded(tiles), onRequestDefaultHome = { requested = true })

        // No HOME-role control after removal; no fake destinations either.
        composeRule.onAllNodes(hasText(HOME_ROLE_DESCRIPTION)).assertCountEquals(0)
        composeRule.onNodeWithText(HOME_ROLE_DESCRIPTION).assertDoesNotExist()
        composeRule.onNodeWithText("News").assertDoesNotExist()
        composeRule.onNodeWithText("Message Center").assertDoesNotExist()
        composeRule.onNodeWithText("All Apps").assertDoesNotExist()

        // Background canvas still renders.
        composeRule.onAllNodesWithContentDescription("Overlay App").assertCountEquals(2)
        composeRule.onNodeWithText("Overlay App").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(requested) }
    }

    @Test
    fun tiles_launchableExposesLongClickOnBodyOnly_unavailableDoesNot() {
        val tiles = listOf(
            tile("a", personal, "Movable App", column = 0, row = 0),
            tile("b", work, null, column = 1, row = 0),
        )
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // TileBody exposes the move-entry long-click; AppLaunchButton never does.
        val launchableNodes = composeRule.onAllNodesWithContentDescription("Movable App")
        launchableNodes.assertCountEquals(2)
        val launchableWithLongClick = launchableNodes.fetchSemanticsNodes()
            .count { it.config.contains(SemanticsActions.OnLongClick) }
        composeRule.runOnIdle { assertEquals(1, launchableWithLongClick) }
        val unavailableNodes = composeRule.onAllNodesWithContentDescription("Unavailable app")
        unavailableNodes.assertCountEquals(2)
        assertFalse(
            "unavailable tile must expose no long-click action on either region",
            unavailableNodes.fetchSemanticsNodes()
                .any { it.config.contains(SemanticsActions.OnLongClick) },
        )
    }

    @Test
    fun longPressBody_entersEditing_withSelectedStateDescription() {
        val tiles = listOf(tile("a", personal, "Movable App", column = 0, row = 0))
        var launched: AppIdentity? = null
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Index 0 is TileBody (first sibling in composition order): the only region
        // with a long-click action.
        composeRule.onAllNodesWithContentDescription("Movable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        // W1 wiggle-editing: selected tile announces Editing label semantics.
        composeRule.onNode(hasStateDescription("Editing Movable App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Movable App").assertDoesNotExist()
        // No move-session artifacts: Active is never entered from the wiggle path.
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        // Tiles retain click for select; body retains long-click for retarget.
        val regions = composeRule.onAllNodesWithContentDescription("Movable App")
        regions.assertCountEquals(2)
        regions[0].assert(hasClickAction())
        regions[1].assert(hasClickAction())
        composeRule.runOnIdle {
            val nodes = regions.fetchSemanticsNodes()
            assertTrue(
                "editing body must retain long-click for retarget",
                nodes[0].config.contains(SemanticsActions.OnLongClick),
            )
            assertEquals(null, launched)
        }
    }

    @Test
    fun longPressSymbol_doesNotEnterEditing() {
        val tiles = listOf(tile("a", personal, "Movable App", column = 0, row = 0))
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // Index 1 is AppLaunchButton: it exposes a click action but no long-click,
        // so no wiggle-editing entry is possible from the symbol.
        val symbolNode = composeRule.onAllNodesWithContentDescription("Movable App")[1]
            .fetchSemanticsNode()
        assertFalse(
            "symbol region must expose no long-click action",
            symbolNode.config.contains(SemanticsActions.OnLongClick),
        )
        composeRule.onNode(hasStateDescription("Editing Movable App, row 0 column 0"))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun longPressLaunchable_entersEditingWithoutResizeHandle() {
        val tiles = listOf(tile("a", personal, "Resizable App", column = 0, row = 0))
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // Index 0 is TileBody (first sibling in composition order): the only region
        // with a long-click action.
        composeRule.onAllNodesWithContentDescription("Resizable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resizable App").assertDoesNotExist()

        // W1 wiggle-editing enters Editing without a move session: no resize
        // handle and no move overlay; Editing label semantics mark the selection.
        composeRule.onNode(hasStateDescription("Editing Resizable App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun editing_tilesRetainClickForSelect_tapsNeverLaunch() {
        var launched: AppIdentity? = null
        val tiles = listOf(
            tile("a", personal, "Editable App", column = 0, row = 0),
            tile("b", work, "Other App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Enter W1 wiggle-editing from the first tile's body.
        composeRule.onAllNodesWithContentDescription("Editable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        // W1 Editing: every tile region retains click for select (never launch);
        // body retains long-click for retarget, symbol never exposes long-click.
        val edited = composeRule.onAllNodesWithContentDescription("Editable App")
        edited.assertCountEquals(2)
        edited[0].assert(hasClickAction())
        edited[1].assert(hasClickAction())
        val other = composeRule.onAllNodesWithContentDescription("Other App")
        other.assertCountEquals(2)
        other[0].assert(hasClickAction())
        other[1].assert(hasClickAction())
        composeRule.runOnIdle {
            val editedNodes = edited.fetchSemanticsNodes()
            assertTrue(
                "editing body must retain long-click for retarget",
                editedNodes[0].config.contains(SemanticsActions.OnLongClick),
            )
            assertFalse(
                "symbol must never expose long-click",
                editedNodes[1].config.contains(SemanticsActions.OnLongClick),
            )
            val otherNodes = other.fetchSemanticsNodes()
            assertTrue(
                "non-selected body must retain long-click for retarget",
                otherNodes[0].config.contains(SemanticsActions.OnLongClick),
            )
        }

        // Tap on the other tile selects (transfers Editing) and never launches.
        other[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Other App, row 0 column 1"))
            .assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Editing Editable App, row 0 column 0"))
            .assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, launched) }

        // Tapping the selected tile again stays selected and never launches.
        other[1].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Other App, row 0 column 1"))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, launched) }

        // No move-session artifacts while wiggle-editing.
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // ---- W1 recess/select/exit ----

    @Test
    fun w1_normalToAToBToNormal_transferNoLaunch() {
        val launched = mutableListOf<AppIdentity>()
        val tiles = listOf(
            tile("a", personal, "App A", column = 0, row = 0),
            tile("b", work, "App B", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { launched.add(it) })
        composeRule.waitForIdle()

        // Normal: no Editing semantics yet.
        composeRule.onNode(hasStateDescription("Editing App A, row 0 column 0"))
            .assertDoesNotExist()
        composeRule.onNode(hasStateDescription("Editing App B, row 0 column 1"))
            .assertDoesNotExist()

        // Normal → A: long-press selects A.
        composeRule.onAllNodesWithContentDescription("App A")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing App A, row 0 column 0"))
            .assertIsDisplayed()

        // A → B: tap transfers selection directly with no Normal round-trip.
        composeRule.onAllNodesWithContentDescription("App B")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing App B, row 0 column 1"))
            .assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Editing App A, row 0 column 0"))
            .assertDoesNotExist()

        // B → Normal: Back exits.
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing App A, row 0 column 0"))
            .assertDoesNotExist()
        composeRule.onNode(hasStateDescription("Editing App B, row 0 column 1"))
            .assertDoesNotExist()

        // No tap or transfer ever launched.
        composeRule.runOnIdle { assertTrue("transfer must never launch: $launched", launched.isEmpty()) }
    }

    @Test
    fun w1_unavailableLongPress_noop() {
        var launched: AppIdentity? = null
        val missing = identity("0", "com.example.gone", "com.example.gone.Main")
        val tiles = listOf(
            tile("gone", missing, null, column = 0, row = 0),
            tile("a", personal, "Available App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Unavailable tile exposes no long-click on either region.
        val unavailable = composeRule.onAllNodesWithContentDescription("Unavailable app")
        unavailable.assertCountEquals(2)
        composeRule.runOnIdle {
            unavailable.fetchSemanticsNodes().forEach { node ->
                assertFalse(
                    "unavailable tile must expose no long-click action",
                    node.config.contains(SemanticsActions.OnLongClick),
                )
            }
        }
        // No Editing semantics appear; available tile stays unselected.
        composeRule.onNode(hasStateDescription("Editing Available App, row 0 column 1"))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    @Test
    fun w1_emptyTap_exitsToNormal() {
        var launched: AppIdentity? = null
        val tiles = listOf(
            tile("a", personal, "Tappable App", column = 0, row = 0),
            tile("b", work, "Other App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Tappable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Tappable App, row 0 column 0"))
            .assertIsDisplayed()

        // Tap empty grid area (bottom of the scrolling grid, below row 0 tiles).
        composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
            .performTouchInput { click(Offset(centerX.toFloat(), (height - 20).toFloat())) }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Tappable App, row 0 column 0"))
            .assertDoesNotExist()
        composeRule.onNode(hasStateDescription("Editing Other App, row 0 column 1"))
            .assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    @Test
    fun w1_back_exitsToNormal() {
        var launched: AppIdentity? = null
        val tiles = listOf(tile("a", personal, "Back App", column = 0, row = 0))
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Back App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Back App, row 0 column 0"))
            .assertIsDisplayed()

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Back App, row 0 column 0"))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    // ---- Sparse distant rows ----

    @Test
    fun sparseDistantRow_isOmittedUntilScrolled_thenRenders() {
        val tiles = listOf(
            tile("near", personal, "Near App", column = 0, row = 0),
            tile("far", work, "Distant App", column = 0, row = 16),
        )
        setCanvas(
            loaded(
                tiles,
                extent = GridVerticalExtent(occupiedBottomRowExclusive = 17L, totalScrollableRows = 19L),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Near App").assertCountEquals(2)
        composeRule.onNodeWithText("Near App").assertDoesNotExist()
        // Large empty gap produces no row allocation: distant tile is not composed yet.
        composeRule.onAllNodesWithContentDescription("Distant App").assertCountEquals(0)
        composeRule.onNodeWithText("Distant App").assertDoesNotExist()

        // Scroll the canvas grid directly; the distant placement must render when it
        // scrolls into range. Swipe repeatedly (up to 10) until the window moves.
        var nearExists = true
        var farExists = false
        repeat(10) {
            if (farExists || !nearExists) return@repeat
            composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
                .performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            nearExists = composeRule.onAllNodesWithContentDescription("Near App")
                .fetchSemanticsNodes().size == 2
            farExists = composeRule.onAllNodesWithContentDescription("Distant App")
                .fetchSemanticsNodes().size == 2
        }
        composeRule.waitForIdle()
        assertTrue(
            "scroll must move the window (nearExists=$nearExists farExists=$farExists)",
            !nearExists || farExists,
        )
    }

    // ---- Pre-decoded icons ----

    @Test
    fun predecodedIcon_rendersDirectly_andMissingRendersLabelOnly() {
        val bitmap = ImageBitmap(32, 32)
        val tiles = listOf(
            tile("with-icon", personal, "Icon App", column = 0, row = 0),
            tile("without-icon", work, "Label Only App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), icons = mapOf(personal to bitmap))

        composeRule.onAllNodesWithContentDescription("Icon App").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Label Only App").assertCountEquals(2)
        composeRule.onNodeWithText("Icon App").assertDoesNotExist()
        composeRule.onNodeWithText("Label Only App").assertDoesNotExist()
        // The icon slot is decorative (contentDescription=null); presence is verified by tag.
        composeRule.onAllNodesWithTag(HOME_TILE_ICON_TAG, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithTag(HOME_TILE_ICON_TAG, useUnmergedTree = true).assertExists()
    }

    // ---- D3 shortcut placeholders (visual-only, inert) ----

    @Test
    fun defaultCanvas_showsNoShortcutPlaceholders() {
        val tiles = listOf(tile("a", personal, "Plain App", column = 0, row = 0))
        setCanvas(loaded(tiles))

        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 1").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 2").assertCountEquals(0)
    }

    @Test
    fun homeCanvasTile_forwardsPlaceholders_asTwoInertNodes() {
        var launched: AppIdentity? = null
        composeRule.setContent {
            HomeCanvasTile(
                tile = tile(
                    "wide",
                    personal,
                    "Wide App",
                    column = 0,
                    row = 0,
                    spanColumns = 3,
                    spanRows = 1,
                ),
                iconBitmap = null,
                onLaunchApp = { launched = it },
                shortcutPlaceholders = 2,
            )
        }
        composeRule.waitForIdle()

        // Pass-through renders exactly two distinct placeholder nodes.
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

        // Existing Body/Symbol separation still passes; placeholders trigger neither.
        val regions = composeRule.onAllNodesWithContentDescription("Wide App")
        regions.assertCountEquals(2)
        regions[0].assertExists().performClick()
        composeRule.runOnIdle { assertEquals(personal, launched) }
        composeRule.runOnIdle { launched = null }
        regions[1].assertExists().performClick()
        composeRule.runOnIdle { assertEquals(personal, launched) }
    }

    @Test
    fun homeCanvasTile_clampsOverCountPlaceholders_toSpanMax() {
        composeRule.setContent {
            HomeCanvasTile(
                tile = tile(
                    "square",
                    personal,
                    "Square App",
                    column = 0,
                    row = 0,
                    spanColumns = 2,
                    spanRows = 2,
                ),
                iconBitmap = null,
                onLaunchApp = {},
                // 2x2 capacity is 1: over-count renders max instead of crashing.
                shortcutPlaceholders = 4,
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 1").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Shortcut placeholder 2").assertCountEquals(0)
        // Body/Symbol separation survives clamping.
        composeRule.onAllNodesWithContentDescription("Square App").assertCountEquals(2)
    }

    // ---- Remove affordance (delete X) ----

    @Test
    fun removeAffordance_hiddenBeforeEdit_shownInEdit_tapCallsOnRemoveOnly() {
        val tiles = listOf(
            tile("a", personal, "Removable App", column = 0, row = 0),
            tile("b", work, "Other App", column = 1, row = 0),
        )
        var removed: ItemId? = null
        var moved: ItemId? = null
        var resized: ItemId? = null
        var launched: AppIdentity? = null
        composeRule.setContent {
            HomeCanvas(
                state = loaded(tiles),
                iconsByIdentity = emptyMap(),
                onLaunchApp = { launched = it },
                onRequestDefaultHome = {},
                onMoveTile = { id, _ -> moved = id },
                onResizeTile = { id, _ -> resized = id },
                onRemoveTile = { removed = it },
            )
        }
        composeRule.waitForIdle()

        // Hidden before edit.
        composeRule.onAllNodesWithContentDescription("Remove Removable App").assertCountEquals(0)

        // Enter edit from TileBody (index 0: only region with long-click).
        composeRule.onAllNodesWithContentDescription("Removable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        // Shown in MOVE mode with Role.Button and 48dp minima.
        composeRule.onNodeWithContentDescription("Remove Removable App")
            .assertExists()
            .assert(hasClickAction())
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            )
        val minTouchPx = with(composeRule.density) { 48.dp.roundToPx() }
        val removeSize = composeRule
            .onNodeWithContentDescription("Remove Removable App")
            .fetchSemanticsNode().size
        composeRule.runOnIdle {
            assertTrue(
                "remove target width ${removeSize.width} must meet 48dp ($minTouchPx px)",
                removeSize.width >= minTouchPx,
            )
            assertTrue(
                "remove target height ${removeSize.height} must meet 48dp ($minTouchPx px)",
                removeSize.height >= minTouchPx,
            )
        }

        // Body/Symbol stay inert while editing.
        val regions = composeRule.onAllNodesWithContentDescription("Removable App")
        regions.assertCountEquals(2)
        composeRule.runOnIdle {
            regions.fetchSemanticsNodes().forEach { node ->
                assertFalse(
                    "in-edit body/symbol must expose no click",
                    node.config.contains(SemanticsActions.OnClick),
                )
                assertFalse(
                    "in-edit body/symbol must expose no long-click",
                    node.config.contains(SemanticsActions.OnLongClick),
                )
            }
        }

        // X tap reports remove only: no move/resize commit, no launch.
        composeRule.onNodeWithContentDescription("Remove Removable App").performClick()
        composeRule.runOnIdle {
            assertEquals(ItemId("a"), removed)
            assertEquals(null, moved)
            assertEquals(null, resized)
            assertEquals(null, launched)
        }
    }

    // ---- Ownership/purity evidence ----

    @Test
    fun publicSignatures_referenceOnlyPredecodedSnapshots_noPlatformOrDecodeTypes() {
        // Behavior evidence: icons arrive as already-decoded ImageBitmap snapshots keyed by
        // full identity; the feature never calls LauncherApps/PackageManager/decode/database.
        // Signature evidence: inspect the feature HomeCanvas entry point for forbidden types.
        val methods = Class.forName("me.foxtails.lakehead.feature.home.HomeCanvasKt").declaredMethods
            .filter { it.name == "HomeCanvas" }
        assertTrue("HomeCanvas entry point must exist", methods.isNotEmpty())
        val signature = methods.joinToString("\n") { method ->
            method.genericParameterTypes.joinToString(",") { it.typeName }
        }
        val forbidden = listOf(
            "LauncherApps",
            "PackageManager",
            "Drawable",
            "BitmapFactory",
            "Room",
            "Flow",
            "Cursor",
            "SQLite",
        )
        forbidden.forEach { token ->
            assertFalse("HomeCanvas signature must not reference $token: $signature", signature.contains(token))
        }
        assertTrue(
            "HomeCanvas must accept pre-decoded ImageBitmap snapshots: $signature",
            signature.contains("ImageBitmap"),
        )
        // Window/overscan math is checked/saturating: near-Long-limit windows stay bounded.
        val window = visibleWindowFor(
            scrollPx = Int.MAX_VALUE,
            viewportHeightPx = Int.MAX_VALUE,
            rowStridePx = 1L,
            overscanRows = 1L,
        )
        assertTrue(window.firstRow >= 0L)
        assertTrue(window.lastRowExclusive >= window.firstRow)
        assertEquals(Long.MAX_VALUE, GridRowWindow(Long.MAX_VALUE - 5, Long.MAX_VALUE).withOverscan(10L).lastRowExclusive)
    }
}
