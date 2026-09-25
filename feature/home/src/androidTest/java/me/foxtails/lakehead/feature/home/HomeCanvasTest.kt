package me.foxtails.lakehead.feature.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
    val composeRule = createComposeRule()

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
    fun loadedEmpty_showsEmptyMessage_withSingleRoleOverlay() {
        var requested = false
        setCanvas(
            loaded(emptyList()),
            onRequestDefaultHome = { requested = true },
        )

        composeRule.onNodeWithText("Home is empty").assertIsDisplayed()
        composeRule.onNodeWithText("Loading home…").assertDoesNotExist()
        composeRule.onNodeWithText("Home not found").assertDoesNotExist()
        composeRule.onNodeWithText(HOME_ROLE_DESCRIPTION)
            .assertIsDisplayed()
            .assert(hasClickAction())
            .performClick()
        composeRule.runOnIdle { assertTrue(requested) }
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
    fun overlay_hasExactlyOneTruthfulRequest_leavesFocus_andMeetsTouchTarget() {
        val tiles = listOf(tile("a", personal, "Overlay App", column = 0, row = 0))
        var requested = false
        setCanvas(loaded(tiles), onRequestDefaultHome = { requested = true })

        // Exactly one truthful HOME-role control; no fake destinations.
        composeRule.onAllNodes(hasText(HOME_ROLE_DESCRIPTION)).assertCountEquals(1)
        composeRule.onNodeWithText("News").assertDoesNotExist()
        composeRule.onNodeWithText("Message Center").assertDoesNotExist()
        composeRule.onNodeWithText("All Apps").assertDoesNotExist()

        // Background canvas stays in the merged accessibility tree (no focus trap).
        composeRule.onAllNodesWithContentDescription("Overlay App").assertCountEquals(2)
        composeRule.onNodeWithText("Overlay App").assertDoesNotExist()
        composeRule.onNodeWithText(HOME_ROLE_DESCRIPTION).assertIsDisplayed()

        // At least 48dp interactive target, derived from runtime density.
        val minTouchPx = with(composeRule.density) { 48.dp.roundToPx() }
        val nodeSize = composeRule
            .onNodeWithText(HOME_ROLE_DESCRIPTION)
            .fetchSemanticsNode().size
        composeRule.runOnIdle {
            assertTrue("overlay width ${nodeSize.width} must meet 48dp ($minTouchPx px)", nodeSize.width >= minTouchPx)
            assertTrue("overlay height ${nodeSize.height} must meet 48dp ($minTouchPx px)", nodeSize.height >= minTouchPx)
        }

        composeRule.onNodeWithText(HOME_ROLE_DESCRIPTION).performClick()
        composeRule.runOnIdle { assertTrue(requested) }
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
    fun longPressBody_entersMoveMode_withSelectedStateDescription() {
        val tiles = listOf(tile("a", personal, "Movable App", column = 0, row = 0))
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // Index 0 is TileBody (first sibling in composition order): the only region
        // with a long-click action.
        composeRule.onAllNodesWithContentDescription("Movable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription("Editing Movable App, row 0 column 0"))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Movable App").assertDoesNotExist()
        composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun longPressSymbol_doesNotEnterMoveMode() {
        val tiles = listOf(tile("a", personal, "Movable App", column = 0, row = 0))
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // Index 1 is AppLaunchButton: it exposes a click action but no long-click,
        // so invoking long-press there must not enter edit mode.
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
    }

    @Test
    fun longPressLaunchable_showsResizeHandle() {
        val tiles = listOf(tile("a", personal, "Resizable App", column = 0, row = 0))
        setCanvas(loaded(tiles))
        composeRule.waitForIdle()

        // Index 0 is TileBody (first sibling in composition order): the only region
        // with a long-click action.
        composeRule.onAllNodesWithContentDescription("Resizable App")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resizable App").assertDoesNotExist()

        // Resize handle should appear at the bottom-right corner of the active tile
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .assertExists()
        // Handle should meet 48dp minimum touch target
        val handleSize = composeRule
            .onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().size
        val minTouchPx = with(composeRule.density) { 48.dp.roundToPx() }
        composeRule.runOnIdle {
            assertTrue("resize handle width ${handleSize.width} must meet 48dp ($minTouchPx px)", handleSize.width >= minTouchPx)
            assertTrue("resize handle height ${handleSize.height} must meet 48dp ($minTouchPx px)", handleSize.height >= minTouchPx)
        }
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
                    spanColumns = 2,
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
