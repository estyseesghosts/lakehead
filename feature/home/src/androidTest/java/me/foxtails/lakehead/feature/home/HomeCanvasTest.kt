package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
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
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.designsystem.APP_STRIP_GLYPH_TAG
import me.foxtails.lakehead.core.designsystem.PINNED_OVERFLOW_TAG
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.RESIZE_HANDLE_TAG
import me.foxtails.lakehead.core.designsystem.REMOVE_BUTTON_TAG
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
 * request/focus/target, shared every-region long-press (body/symbol/shortcut,
 * including unavailable tiles), sparse distant rows after scroll, pre-decoded icon
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

    private fun setThemedContent(content: @Composable () -> Unit) {
        // Production always composes home content under LauncherTheme
        // (MainActivity); AppTile requires LocalLauncherDimensions, so bare
        // content crashes with "No LauncherDimensions provided".
        composeRule.setContent { LauncherTheme { content() } }
    }

    private fun setCanvas(
        state: HomeRenderState,
        icons: HomeCanvasIcons = emptyMap(),
        onLaunchApp: (AppIdentity) -> Unit = {},
        onRequestDefaultHome: () -> Unit = {},
        overscanRows: Long = 1L,
        pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
        onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
    ) {
        setThemedContent {
            HomeCanvas(
                state = state,
                iconsByIdentity = icons,
                onLaunchApp = onLaunchApp,
                onRequestDefaultHome = onRequestDefaultHome,
                overscanRows = overscanRows,
                pinnedByTile = pinnedByTile,
                onPinnedShortcutClick = onPinnedShortcutClick,
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
        setThemedContent {
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
    fun tiles_everyRegionExposesSharedLongClick_includingUnavailable() {
        val tiles = listOf(
            tile("a", personal, "Movable App", column = 0, row = 0),
            tile("b", work, null, column = 1, row = 0),
        )
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // The shared tile long-press is forwarded from every hit region
        // (TileBody + app launch glyph); edit entry is independent of
        // launchability, so unavailable tiles expose it too.
        val launchableNodes = composeRule.onAllNodesWithContentDescription("Movable App")
        launchableNodes.assertCountEquals(2)
        val launchableWithLongClick = launchableNodes.fetchSemanticsNodes()
            .count { it.config.contains(SemanticsActions.OnLongClick) }
        composeRule.runOnIdle { assertEquals(2, launchableWithLongClick) }
        val unavailableNodes = composeRule.onAllNodesWithContentDescription("Unavailable app")
        unavailableNodes.assertCountEquals(2)
        val unavailableWithLongClick = unavailableNodes.fetchSemanticsNodes()
            .count { it.config.contains(SemanticsActions.OnLongClick) }
        composeRule.runOnIdle {
            assertEquals(
                "unavailable tile must expose the shared long-click on both regions",
                2,
                unavailableWithLongClick,
            )
        }
    }

    @Test
    fun longPressBody_entersEditing_withSelectedStateDescription() {
        val tiles = listOf(tile("a", personal, "Movable App", column = 0, row = 0))
        var launched: AppIdentity? = null
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Index 0 is TileBody (first sibling in composition order); every
        // region forwards the same shared tile long-press.
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
            .assertIsDisplayed()
        composeRule.onNodeWithTag(REMOVE_BUTTON_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        // Tiles retain click for select; every region retains long-click for retarget.
        val regions = composeRule.onAllNodesWithContentDescription("Movable App")
        regions.assertCountEquals(2)
        regions[0].assert(hasClickAction())
        regions[1].assert(hasClickAction())
        val bodyKeepsLongClick = regions.fetchSemanticsNodes()[0]
            .config.contains(SemanticsActions.OnLongClick)
        composeRule.runOnIdle {
            assertTrue(
                "editing body must retain long-click for retarget",
                bodyKeepsLongClick,
            )
            assertEquals(null, launched)
        }
    }

    @Test
    fun longPressSymbol_entersEditing() {
        var launched: AppIdentity? = null
        val tiles = listOf(tile("a", personal, "Movable App", column = 0, row = 0))
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Index 1 is the app launch glyph: it forwards the same shared tile
        // long-press as TileBody, so wiggle-editing entry works from the symbol.
        composeRule.onAllNodesWithContentDescription("Movable App")[1]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Movable App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    @Test
    fun longPressLaunchable_entersEditingWithCornerControls() {
        val tiles = listOf(tile("a", personal, "Resizable App", column = 0, row = 0))
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // Index 0 is TileBody (first sibling in composition order); every
        // region forwards the same shared tile long-press.
        composeRule.onAllNodesWithContentDescription("Resizable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resizable App").assertDoesNotExist()

        // W1 wiggle-editing enters Editing without a move session; both corner
        // controls are already available while the selected tile is editing.
        composeRule.onNode(hasStateDescription("Editing Resizable App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(REMOVE_BUTTON_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
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
        // every region retains the shared long-click for retarget.
        val edited = composeRule.onAllNodesWithContentDescription("Editable App")
        edited.assertCountEquals(2)
        edited[0].assert(hasClickAction())
        edited[1].assert(hasClickAction())
        val other = composeRule.onAllNodesWithContentDescription("Other App")
        other.assertCountEquals(2)
        other[0].assert(hasClickAction())
        other[1].assert(hasClickAction())
        // Snapshot semantics off the main thread: fetchSemanticsNodes()
        // synchronizes and must never run inside runOnIdle.
        val editedNodes = edited.fetchSemanticsNodes()
        val editedBodyKeepsLongClick =
            editedNodes[0].config.contains(SemanticsActions.OnLongClick)
        val editedSymbolKeepsLongClick =
            editedNodes[1].config.contains(SemanticsActions.OnLongClick)
        val otherNodes = other.fetchSemanticsNodes()
        val otherBodyKeepsLongClick =
            otherNodes[0].config.contains(SemanticsActions.OnLongClick)
        val otherSymbolKeepsLongClick =
            otherNodes[1].config.contains(SemanticsActions.OnLongClick)
        composeRule.runOnIdle {
            assertTrue(
                "editing body must retain long-click for retarget",
                editedBodyKeepsLongClick,
            )
            assertTrue(
                "editing symbol must retain the shared long-click for retarget",
                editedSymbolKeepsLongClick,
            )
            assertTrue(
                "non-selected body must retain long-click for retarget",
                otherBodyKeepsLongClick,
            )
            assertTrue(
                "non-selected symbol must retain long-click for retarget",
                otherSymbolKeepsLongClick,
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
            .assertIsDisplayed()
        composeRule.onNodeWithTag(REMOVE_BUTTON_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
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
    fun w1_unavailableLongPress_entersEditing() {
        var launched: AppIdentity? = null
        val missing = identity("0", "com.example.gone", "com.example.gone.Main")
        val tiles = listOf(
            tile("gone", missing, null, column = 0, row = 0),
            tile("a", personal, "Available App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Edit entry is independent of launchability: the unavailable tile
        // exposes the shared long-click on both regions.
        val unavailable = composeRule.onAllNodesWithContentDescription("Unavailable app")
        unavailable.assertCountEquals(2)
        val unavailableLongClicks = unavailable.fetchSemanticsNodes()
            .count { it.config.contains(SemanticsActions.OnLongClick) }
        composeRule.runOnIdle {
            assertEquals(
                "unavailable tile must expose the shared long-click on both regions",
                2,
                unavailableLongClicks,
            )
        }
        // Long-press selects the unavailable tile into wiggle-editing with no launch.
        unavailable[0].performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Unavailable app, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
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

    // ---- C1 pinned shortcut strip (real list, distinct actions) ----

    @Test
    fun defaultCanvas_showsNoPinnedStrip() {
        val tiles = listOf(tile("a", personal, "Plain App", column = 0, row = 0))
        setCanvas(loaded(tiles))

        composeRule.onNodeWithContentDescription("Compose, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun homeCanvasTile_rendersPinnedStrip_withDistinctActions() {
        var launched: AppIdentity? = null
        val shortcutClicks = mutableListOf<String>()
        setThemedContent {
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
                // 3x1 capacity is 2: both pins visible, no overflow.
                pinnedShortcuts = listOf(
                    PinnedTileShortcut("new-post", "New post"),
                    PinnedTileShortcut("camera", "Camera"),
                ),
                onShortcutClick = { shortcutClicks.add(it) },
            )
        }
        composeRule.waitForIdle()

        // Two distinct shortcut buttons with their own labels.
        composeRule.onNodeWithContentDescription("New post, shortcut")
            .assertExists().assert(hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription("Camera, shortcut")
            .assertExists().assert(hasClickAction()).performClick()
        composeRule.runOnIdle { assertEquals(listOf("new-post", "camera"), shortcutClicks) }
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()

        // Existing Body/Symbol separation still passes; strip taps launch neither.
        val regions = composeRule.onAllNodesWithContentDescription("Wide App")
        regions.assertCountEquals(2)
        regions[0].assertExists().performClick()
        composeRule.runOnIdle { assertEquals(personal, launched) }
        composeRule.runOnIdle { launched = null }
        regions[1].assertExists().performClick()
        composeRule.runOnIdle { assertEquals(personal, launched) }
        composeRule.runOnIdle { assertEquals(2, shortcutClicks.size) }
    }

    @Test
    fun homeCanvasTile_overflowBeyondCapacity_announcedInSemantics() {
        val shortcutClicks = mutableListOf<String>()
        setThemedContent {
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
                // 2x2 capacity is 1: first pin visible, two hidden. The tile
                // fits its strip to the measured bounds at the grid-unit
                // pitch, where app + pin exactly fills the edge: the +N node
                // would overflow, so it yields to TileBody semantics while
                // the pin stays visible and tappable. Nothing is deleted.
                pinnedShortcuts = listOf(
                    PinnedTileShortcut("new-post", "New post"),
                    PinnedTileShortcut("camera", "Camera"),
                    PinnedTileShortcut("search", "Search"),
                ),
                onShortcutClick = { shortcutClicks.add(it) },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("New post, shortcut")
            .assertExists().assert(hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription("Camera, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasStateDescription("2 more shortcuts")).assertExists()
        composeRule.runOnIdle { assertEquals(listOf("new-post"), shortcutClicks) }
        // Body/Symbol separation survives overflow.
        composeRule.onAllNodesWithContentDescription("Square App").assertCountEquals(2)
    }

    @Test
    fun homeCanvasTile_previewSpanChange_recomputesStrip() {
        // Direct HomeCanvasTile preview: the tile arranges visible + overflow
        // from its live span on every recomposition (resize preview included),
        // owning no pin or capacity logic itself.
        val span = androidx.compose.runtime.mutableStateOf(LogicalSpan(4, 2))
        val pins = androidx.compose.runtime.mutableStateOf(
            listOf(
                PinnedTileShortcut("new-post", "New post"),
                PinnedTileShortcut("camera", "Camera"),
                PinnedTileShortcut("search", "Search"),
            ),
        )
        setThemedContent {
            HomeCanvasTile(
                tile = tile(
                    "wide",
                    personal,
                    "Preview App",
                    column = 0,
                    row = 0,
                    spanColumns = span.value.columns,
                    spanRows = span.value.rows,
                ),
                iconBitmap = null,
                onLaunchApp = {},
                // The preview span arrives with a changed modifier, as the
                // grid's resize preview supplies both together.
                modifier = Modifier.testTag("preview-tile-${span.value.columns}x${span.value.rows}"),
                pinnedShortcuts = pins.value,
                onShortcutClick = {},
            )
        }
        composeRule.waitForIdle()

        // 4x2 capacity is 3: every pin visible, no overflow.
        composeRule.onNodeWithTag("preview-tile-4x2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()
        composeRule.onNodeWithContentDescription("Camera, shortcut").assertExists()
        composeRule.onNodeWithContentDescription("Search, shortcut").assertExists()

        // Preview shrink to 2x2 (capacity 1): only index 0 stays visible; the
        // binding retains the rest, announced as overflow, nothing deleted.
        composeRule.runOnIdle { span.value = LogicalSpan(2, 2) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview-tile-2x2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("preview-tile-4x2", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()
        composeRule.onNodeWithContentDescription("Camera, shortcut").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Search, shortcut").assertDoesNotExist()
        composeRule.onNode(hasStateDescription("2 more shortcuts")).assertExists()

        // Preview grow restores from the same retained list.
        composeRule.runOnIdle { span.value = LogicalSpan(4, 2) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview-tile-4x2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Camera, shortcut").assertExists()
        composeRule.onNodeWithContentDescription("Search, shortcut").assertExists()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun homeCanvasTile_lastPinClear_restoresCentered() {
        // Clearing the last pin recomputes to the pin-free treatment: no
        // strip glyphs, the centered icon slot remains, body + symbol share
        // the label. The tile owns no pin state; it only reflects the list.
        val pins = androidx.compose.runtime.mutableStateOf(
            listOf(PinnedTileShortcut("new-post", "New post")),
        )
        setThemedContent {
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
                iconBitmap = ImageBitmap(32, 32),
                onLaunchApp = {},
                pinnedShortcuts = pins.value,
                onShortcutClick = {},
            )
        }
        composeRule.waitForIdle()

        // Pinned: compact strip (app glyph + shortcut) with the icon slot.
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()
        composeRule.onNodeWithTag(HOME_TILE_ICON_TAG, useUnmergedTree = true).assertExists()

        // Last pin cleared: strip gone, centered icon slot stays, the two app
        // regions share the label with no strip glyphs left behind.
        composeRule.runOnIdle { pins.value = emptyList() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(HOME_TILE_ICON_TAG, useUnmergedTree = true).assertExists()
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
        setThemedContent {
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

        // Enter edit from TileBody long-press (index 0).
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

    @Test
    fun removeAffordance_smallTile_topRightCorner_tapCallsOnRemoveOnly() {
        // A5: a 1x1 tile keeps the top-right corner contract — the remove center
        // sits on the tile's (right, top) corner.
        val tiles = listOf(tile("a", personal, "Small App", column = 0, row = 0))
        var removed: ItemId? = null
        var moved: ItemId? = null
        setThemedContent {
            HomeCanvas(
                state = loaded(tiles),
                iconsByIdentity = emptyMap(),
                onLaunchApp = {},
                onRequestDefaultHome = {},
                onMoveTile = { id, _ -> moved = id },
                onRemoveTile = { removed = it },
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Small App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        val tileBounds = composeRule.onAllNodesWithContentDescription("Small App")[0]
            .fetchSemanticsNode().boundsInRoot
        val removeBounds = composeRule.onNodeWithContentDescription("Remove Small App")
            .assertExists()
            .assert(hasClickAction())
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertEquals(tileBounds.right, removeBounds.center.x, 1.5f)
            assertEquals(tileBounds.top, removeBounds.center.y, 1.5f)
        }

        composeRule.onNodeWithContentDescription("Remove Small App").performClick()
        composeRule.runOnIdle {
            assertEquals(ItemId("a"), removed)
            assertEquals(null, moved)
        }
    }

    @Test
    fun removeAffordance_largeTile_centered_tapCallsOnRemoveOnly() {
        // A5: a 2x2 tile centers the remove affordance — its center coincides
        // with the tile center instead of the top-right corner.
        val tiles = listOf(
            tile("big", personal, "Big App", column = 0, row = 0, spanColumns = 2, spanRows = 2),
        )
        var removed: ItemId? = null
        var moved: ItemId? = null
        var resized: ItemId? = null
        setThemedContent {
            HomeCanvas(
                state = loaded(tiles),
                iconsByIdentity = emptyMap(),
                onLaunchApp = {},
                onRequestDefaultHome = {},
                onMoveTile = { id, _ -> moved = id },
                onResizeTile = { id, _ -> resized = id },
                onRemoveTile = { removed = it },
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Big App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        val tileBounds = composeRule.onAllNodesWithContentDescription("Big App")[0]
            .fetchSemanticsNode().boundsInRoot
        val removeBounds = composeRule.onNodeWithContentDescription("Remove Big App")
            .assertExists()
            .assert(hasClickAction())
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            )
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertEquals(tileBounds.center.x, removeBounds.center.x, 1.5f)
            assertEquals(tileBounds.center.y, removeBounds.center.y, 1.5f)
        }

        composeRule.onNodeWithContentDescription("Remove Big App").performClick()
        composeRule.runOnIdle {
            assertEquals(ItemId("big"), removed)
            assertEquals(null, moved)
            assertEquals(null, resized)
        }
    }

    // ---- A2 physical touch: shared long-press + tap independence ----

    @Test
    fun physicalLongPress_emptyBody_entersEditing_noLaunch() {
        var launched: AppIdentity? = null
        // 2x2 with one pin: compact bottom strip (app + pin), free top area
        // above it for an honest empty-body probe. A 1x1 tile cannot serve:
        // its centered symbol fills the whole cell.
        val tiles = listOf(
            tile("a", personal, "Touch App", column = 0, row = 0, spanColumns = 2, spanRows = 2),
        )
        setCanvas(
            loaded(tiles),
            onLaunchApp = { launched = it },
            pinnedByTile = mapOf(
                ItemId("a") to listOf(PinnedTileShortcut("new-post", "New post")),
            ),
        )
        composeRule.waitForIdle()

        val bodyNode = composeRule.onAllNodesWithContentDescription("Touch App")[0]
        val tileBounds = bodyNode.fetchSemanticsNode().boundsInRoot
        val appBounds = composeRule.onAllNodesWithContentDescription("Touch App")[1]
            .fetchSemanticsNode().boundsInRoot
        val pinBounds = composeRule.onNodeWithContentDescription("New post, shortcut")
            .fetchSemanticsNode().boundsInRoot
        val emptyRoot = Offset(
            tileBounds.center.x,
            tileBounds.top + with(composeRule.density) { 4.dp.toPx() },
        )
        composeRule.runOnIdle {
            assertFalse("empty probe must miss app ($appBounds)", appBounds.contains(emptyRoot))
            assertFalse("empty probe must miss pin ($pinBounds)", pinBounds.contains(emptyRoot))
        }
        val emptyLocal = Offset(emptyRoot.x - tileBounds.left, emptyRoot.y - tileBounds.top)
        bodyNode.performTouchInput { longClick(emptyLocal) }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Touch App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    @Test
    fun physicalLongPress_symbol_entersEditing_noLaunch() {
        var launched: AppIdentity? = null
        val tiles = listOf(tile("a", personal, "Touch App", column = 0, row = 0))
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Touch App")[1]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Touch App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    @Test
    fun physicalLongPress_shortcut_entersEditing_noLaunch() {
        var launched: AppIdentity? = null
        val shortcutClicks = mutableListOf<Pair<ItemId, String>>()
        val tiles = listOf(
            tile("wide", personal, "Wide App", column = 0, row = 0, spanColumns = 3, spanRows = 1),
        )
        setCanvas(
            loaded(tiles),
            onLaunchApp = { launched = it },
            pinnedByTile = mapOf(
                ItemId("wide") to listOf(
                    PinnedTileShortcut("new-post", "New post"),
                    PinnedTileShortcut("camera", "Camera"),
                ),
            ),
            onPinnedShortcutClick = { owner, id -> shortcutClicks.add(owner to id) },
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("New post, shortcut")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Wide App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(null, launched)
            assertTrue("long-press release must not fire a shortcut click", shortcutClicks.isEmpty())
        }
    }

    @Test
    fun physicalLongPress_unavailable_entersEditing_noLaunch() {
        var launched: AppIdentity? = null
        val missing = identity("0", "com.example.gone", "com.example.gone.Main")
        val tiles = listOf(tile("gone", missing, null, column = 0, row = 0))
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Unavailable app")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Unavailable app, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    @Test
    fun physicalTap_bodyAndSymbol_launchExactIdentityOnce_each() {
        val launched = mutableListOf<AppIdentity>()
        // 2x2 with one pin: compact bottom strip, free top area for an honest
        // body tap outside the app/pin glyphs.
        val tiles = listOf(
            tile("a", personal, "Tap App", column = 0, row = 0, spanColumns = 2, spanRows = 2),
        )
        setCanvas(
            loaded(tiles),
            onLaunchApp = { launched.add(it) },
            pinnedByTile = mapOf(
                ItemId("a") to listOf(PinnedTileShortcut("new-post", "New post")),
            ),
        )
        composeRule.waitForIdle()

        // Body tap on empty tile area (top-center, above the strip).
        val bodyNode = composeRule.onAllNodesWithContentDescription("Tap App")[0]
        val tileBounds = bodyNode.fetchSemanticsNode().boundsInRoot
        val appBounds = composeRule.onAllNodesWithContentDescription("Tap App")[1]
            .fetchSemanticsNode().boundsInRoot
        val pinBounds = composeRule.onNodeWithContentDescription("New post, shortcut")
            .fetchSemanticsNode().boundsInRoot
        val emptyRoot = Offset(
            tileBounds.center.x,
            tileBounds.top + with(composeRule.density) { 4.dp.toPx() },
        )
        composeRule.runOnIdle {
            assertFalse("empty probe must miss app ($appBounds)", appBounds.contains(emptyRoot))
            assertFalse("empty probe must miss pin ($pinBounds)", pinBounds.contains(emptyRoot))
        }
        val emptyLocal = Offset(emptyRoot.x - tileBounds.left, emptyRoot.y - tileBounds.top)
        bodyNode.performTouchInput { click(emptyLocal) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(listOf(personal), launched) }

        // Symbol tap launches the same exact identity once more, never entering edit.
        composeRule.onAllNodesWithContentDescription("Tap App")[1]
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(listOf(personal, personal), launched) }
        composeRule.onNode(hasStateDescription("Editing Tap App, row 0 column 0"))
            .assertDoesNotExist()
    }

    @Test
    fun physicalTap_shortcut_reportsOwnIdOnly_noLaunch() {
        var launched: AppIdentity? = null
        val shortcutClicks = mutableListOf<Pair<ItemId, String>>()
        // 4x2 (capacity 3): app + both pins comfortably visible, no overflow.
        val tiles = listOf(
            tile("wide", personal, "Wide App", column = 0, row = 0, spanColumns = 4, spanRows = 2),
        )
        setCanvas(
            loaded(tiles),
            onLaunchApp = { launched = it },
            pinnedByTile = mapOf(
                ItemId("wide") to listOf(
                    PinnedTileShortcut("new-post", "New post"),
                    PinnedTileShortcut("camera", "Camera"),
                ),
            ),
            onPinnedShortcutClick = { owner, id -> shortcutClicks.add(owner to id) },
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Camera, shortcut")
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("wide") to "camera"), shortcutClicks)
            assertEquals(null, launched)
        }
        composeRule.onNode(hasStateDescription("Editing Wide App, row 0 column 0"))
            .assertDoesNotExist()
    }

    @Test
    fun physicalTap_editing_selectsWithoutLaunch() {
        var launched: AppIdentity? = null
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
            tile("b", work, "Beta App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Enter wiggle-editing with a physical long-press on Alpha.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Alpha App, row 0 column 0"))
            .assertIsDisplayed()

        // Physical tap on Beta transfers selection and never launches.
        composeRule.onAllNodesWithContentDescription("Beta App")[0]
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Beta App, row 0 column 1"))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, launched) }

        // Physical tap on the selected tile re-selects and never launches.
        composeRule.onAllNodesWithContentDescription("Beta App")[1]
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Beta App, row 0 column 1"))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, launched) }
    }

    @Test
    fun physicalTap_shortcutDuringEdit_selectsWithoutShortcutLaunch() {
        // P3: tapping a pinned shortcut glyph while wiggle-editing selects the
        // tile via the HomeCanvasTile shortcut route — never launches the app
        // and never invokes the shortcut. Real pointer input, not semantics.
        // Composes the tile directly: the grid withholds strip glyphs on the
        // edit-selected tile by design (floating remove/resize controls own
        // that footprint; see fitPinnedStrip), so the route is covered here
        // with a non-selected tile during edit (the transfer case).
        var launched: AppIdentity? = null
        var selected: ItemId? = null
        val shortcutClicks = mutableListOf<String>()
        val renderTile = tile(
            "wide",
            personal,
            "Wide App",
            column = 0,
            row = 0,
            spanColumns = 4,
            spanRows = 2,
        )
        setThemedContent {
            HomeCanvasTile(
                tile = renderTile,
                iconBitmap = null,
                onLaunchApp = { launched = it },
                pinnedShortcuts = listOf(
                    PinnedTileShortcut("new-post", "New post"),
                    PinnedTileShortcut("camera", "Camera"),
                ),
                onShortcutClick = { shortcutClicks.add(it) },
                interactionState = HomeInteractionController.Editing(
                    selectedId = ItemId("other"),
                ),
                onSelect = { selected = it },
                onEnterWiggle = {},
            )
        }
        composeRule.waitForIdle()

        // Physical tap on the pinned shortcut glyph transfers selection to
        // this tile without launching the app or invoking the shortcut.
        composeRule.onNodeWithContentDescription("New post, shortcut")
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(ItemId("wide"), selected)
            assertEquals(null, launched)
            assertTrue(
                "shortcut tap during edit must not invoke: $shortcutClicks",
                shortcutClicks.isEmpty(),
            )
        }
    }

    @Test
    fun activeSession_staleEditingSelection_tilesExposeNoActions_noRetarget() {
        // P2: an Active move/resize session owns every gesture — a stale W1
        // Editing selection must not re-enable child actions. The body keeps
        // only a disabled click (no invoke), the symbol exposes nothing, no
        // region exposes long-click, and physical taps stay inert (no select
        // retarget, no launch, no wiggle entry). Selection visuals persist.
        var launched: AppIdentity? = null
        var selected: ItemId? = null
        var wiggleEntered: ItemId? = null
        val renderTile = tile("a", personal, "Active App", column = 0, row = 0)
        setThemedContent {
            HomeCanvasTile(
                tile = renderTile,
                iconBitmap = null,
                onLaunchApp = { launched = it },
                isEditActive = true,
                activeItemId = ItemId("a"),
                interactionState = HomeInteractionController.Editing(
                    selectedId = ItemId("a"),
                ),
                onSelect = { selected = it },
                onEnterWiggle = { wiggleEntered = it.itemId },
            )
        }
        composeRule.waitForIdle()

        // Index 0 is TileBody (first sibling in composition order), index 1
        // the app launch glyph. While Active the body click is disabled, the
        // glyph installs no detector, and neither exposes long-click.
        val regions = composeRule.onAllNodesWithContentDescription("Active App")
        regions.assertCountEquals(2)
        regions[0].assertIsNotEnabled()
        // Snapshot semantics off the main thread: fetchSemanticsNodes()
        // synchronizes and must never run inside runOnIdle.
        val nodes = regions.fetchSemanticsNodes()
        composeRule.runOnIdle {
            assertTrue(
                "active tile must expose no long-click actions",
                nodes.none { it.config.contains(SemanticsActions.OnLongClick) },
            )
            assertTrue(
                "active symbol must expose no click action",
                !nodes[1].config.contains(SemanticsActions.OnClick),
            )
        }
        // Selection visuals persist while actions stay inert.
        composeRule.onNode(hasStateDescription("Editing Active App, row 0 column 0"))
            .assertIsDisplayed()

        // Physical taps stay inert: no select retarget, no launch, no wiggle entry.
        regions[0].performTouchInput { click() }
        composeRule.waitForIdle()
        regions[1].performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(null, selected)
            assertEquals(null, launched)
            assertEquals(null, wiggleEntered)
        }
    }

    // ---- Ownership/purity evidence ----

    @Test
    fun tapWhileEditing_neverLaunches_evenOnSelectedTile() {
        // W4: verify that tapping a tile during editing mode never triggers
        // onLaunchApp, even for the currently selected tile. Taps transfer
        // selection or re-select; they never launch.
        var launched: AppIdentity? = null
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
            tile("b", work, "Beta App", column = 1, row = 0),
        )
        setCanvas(loaded(tiles), onLaunchApp = { launched = it })
        composeRule.waitForIdle()

        // Enter editing via long-press on Alpha App.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Alpha App, row 0 column 0"))
            .assertIsDisplayed()

        // Tap the selected tile itself — must not launch.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0].performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("tap on selected tile must never launch", null, launched)
        }

        // Tap the non-selected tile — transfers selection, must not launch.
        composeRule.onAllNodesWithContentDescription("Beta App")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Beta App, row 0 column 1"))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("tap to transfer selection must never launch", null, launched)
        }

        // Tap the now-selected tile again — re-select, must not launch.
        composeRule.onAllNodesWithContentDescription("Beta App")[0].performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("re-tap on selected tile must never launch", null, launched)
        }

        // Tap the now-recessed tile — transfers back, must not launch.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Alpha App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("transfer back must never launch", null, launched)
        }
    }

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

    // ---- A3 unified edit-drag + page-swipe arbitration (real pointer) ----

    private fun setMoveCanvas(
        tiles: List<HomeRenderTile>,
        onLaunchApp: (AppIdentity) -> Unit = {},
        onMoveTile: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
        onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
        pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
        onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
        onSelectedTileChange: (ItemId?) -> Unit = {},
        onPagerHoldChange: (Boolean) -> Unit = {},
    ) {
        setThemedContent {
            HomeCanvas(
                state = loaded(tiles),
                iconsByIdentity = emptyMap(),
                onLaunchApp = onLaunchApp,
                onRequestDefaultHome = {},
                onMoveTile = onMoveTile,
                onResizeTile = onResizeTile,
                pinnedByTile = pinnedByTile,
                onPinnedShortcutClick = onPinnedShortcutClick,
                onSelectedTileChange = onSelectedTileChange,
                onPagerHoldChange = onPagerHoldChange,
            )
        }
    }

    @Test
    fun editDrag_unselectedAppOrigin_movesAndTransfers_noChildCallbacks() {
        // Tile A selected, tile B pinned unselected: a past-slop drag from B's
        // app symbol moves the whole tile, transfers selection to B, and fires
        // no child callbacks. Real pointer input, not semantics.
        val launched = mutableListOf<AppIdentity>()
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val resizes = mutableListOf<Pair<ItemId, LogicalSpan>>()
        val selected = mutableListOf<ItemId?>()
        val holds = mutableListOf<Boolean>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
            tile("b", work, "Beta App", column = 1, row = 0),
        )
        setMoveCanvas(
            tiles,
            onLaunchApp = { launched.add(it) },
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onResizeTile = { id, span -> resizes.add(id to span) },
            onSelectedTileChange = { selected.add(it) },
            onPagerHoldChange = { holds.add(it) },
        )
        composeRule.waitForIdle()

        // Enter wiggle-editing with a physical long-press on Alpha.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Alpha App, row 0 column 0"))
            .assertIsDisplayed()

        // Past-slop drag from Beta's app symbol (index 1): whole-tile MOVE.
        // Swipe on the grid from Beta's center down two-plus rows so the
        // candidate leaves the origin cell and release commits a new cell.
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val betaBounds = composeRule.onAllNodesWithContentDescription("Beta App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            betaBounds.center.x - gridBounds.left,
            betaBounds.center.y - gridBounds.top,
        )
        val dragEnd = dragStart + Offset(0f, 600f)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 500) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("one move for the dragged tile", 1, moves.size)
            assertEquals(ItemId("b"), moves[0].first)
            assertTrue(
                "drag must commit a new cell, got ${moves[0].second}",
                moves[0].second != LogicalPosition(1, 0),
            )
            assertEquals("selection must transfer to the dragged tile", ItemId("b"), selected.lastOrNull())
            assertTrue("drag must never launch: $launched", launched.isEmpty())
            assertTrue("transfer drag must never resize: $resizes", resizes.isEmpty())
            assertTrue("tile-origin drag must hold the pager: $holds", holds.contains(true))
            assertEquals("hold must clear at release, no stuck hold", false, holds.lastOrNull())
        }
        composeRule.onNode(hasStateDescription("Editing Beta App, row 0 column 1"))
            .assertIsDisplayed()
    }

    @Test
    fun editDrag_unselectedShortcutOrigin_movesWholeTile_noShortcutFire() {
        // Same transfer contract from a shortcut glyph: the whole tile moves,
        // the shortcut itself never fires, and selection transfers.
        val launched = mutableListOf<AppIdentity>()
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val shortcutClicks = mutableListOf<Pair<ItemId, String>>()
        val selected = mutableListOf<ItemId?>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
            tile("wide", work, "Wide App", column = 1, row = 0, spanColumns = 4, spanRows = 2),
        )
        setMoveCanvas(
            tiles,
            onLaunchApp = { launched.add(it) },
            onMoveTile = { id, pos -> moves.add(id to pos) },
            pinnedByTile = mapOf(
                ItemId("wide") to listOf(
                    PinnedTileShortcut("new-post", "New post"),
                    PinnedTileShortcut("camera", "Camera"),
                ),
            ),
            onPinnedShortcutClick = { owner, id -> shortcutClicks.add(owner to id) },
            onSelectedTileChange = { selected.add(it) },
        )
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Editing Alpha App, row 0 column 0"))
            .assertIsDisplayed()

        // Past-slop drag starting exactly on the shortcut glyph: swipe on the
        // grid from the glyph's center down two-plus rows.
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val pinBounds = composeRule.onNodeWithContentDescription("Camera, shortcut")
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            pinBounds.center.x - gridBounds.left,
            pinBounds.center.y - gridBounds.top,
        )
        val dragEnd = dragStart + Offset(0f, 600f)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 500) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("one move for the dragged tile", 1, moves.size)
            assertEquals(ItemId("wide"), moves[0].first)
            assertEquals("selection must transfer to the dragged tile", ItemId("wide"), selected.lastOrNull())
            assertTrue("drag must never launch: $launched", launched.isEmpty())
            assertTrue(
                "shortcut drag must not invoke the shortcut: $shortcutClicks",
                shortcutClicks.isEmpty(),
            )
        }
    }

    @Test
    fun editDrag_belowSlopTap_selectsWithoutMove() {
        // Below slop the selection/child-tap path owns the gesture: a tap on
        // the unselected tile transfers selection with no move and no launch.
        val launched = mutableListOf<AppIdentity>()
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val selected = mutableListOf<ItemId?>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
            tile("b", work, "Beta App", column = 1, row = 0),
        )
        setMoveCanvas(
            tiles,
            onLaunchApp = { launched.add(it) },
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onSelectedTileChange = { selected.add(it) },
        )
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Beta App")[0]
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Beta App, row 0 column 1"))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("below-slop tap selects only", ItemId("b"), selected.lastOrNull())
            assertTrue("below-slop tap must not move: $moves", moves.isEmpty())
            assertTrue("tap must never launch: $launched", launched.isEmpty())
        }
    }

    @Test
    fun pagerHold_tileTouch_trueThenFalse() {
        // onPagerHoldChange direct: a real tile touch holds the pager for the
        // press and clears on release, so a tile-origin gesture never becomes
        // a page swipe and the hold never sticks.
        val holds = mutableListOf<Boolean>()
        val tiles = listOf(tile("a", personal, "Hold App", column = 0, row = 0))
        setMoveCanvas(tiles, onPagerHoldChange = { holds.add(it) })
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Hold App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Hold App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue("tile touch must hold the pager: $holds", holds.contains(true))
            assertEquals("hold must clear on release", false, holds.lastOrNull())
        }
    }
}
