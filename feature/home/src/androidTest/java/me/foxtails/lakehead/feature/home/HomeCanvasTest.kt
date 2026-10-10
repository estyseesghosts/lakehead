package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.designsystem.APP_STRIP_GLYPH_TAG
import me.foxtails.lakehead.core.designsystem.PINNED_OVERFLOW_TAG
import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.RESIZE_HANDLE_TAG
import me.foxtails.lakehead.core.grid.GridColumns
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
 * Covers populated labels/roles/exact launch identity, pinned shortcut
 * reporting, pre-decoded icon rendering vs label-only fallback, ownership
 * evidence that the feature performs no decode, catalogue/platform lookup,
 * or database access (behavior plus public-signature inspection),
 * sparse distant rows after scroll, preview span change with pin clear,
 * and edit-drag + page-swipe arbitration. Uses existing Compose test idioms;
 * pixel expectations derive from runtime density rather than fixed values.
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
    ) = HomeRenderState.Loaded(
        requestedHomeId = homeId,
        tiles = tiles,
        columns = GridColumns(columns),
        extent = extent,
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
        pinnedByTile: Map<ItemId, List<PinnedTileShortcut>> = emptyMap(),
        onPinnedShortcutClick: (ItemId, String) -> Unit = { _, _ -> },
    ) {
        setThemedContent {
            HomeCanvas(
                state = state,
                iconsByIdentity = icons,
                onLaunchApp = onLaunchApp,
                onRequestDefaultHome = onRequestDefaultHome,
                pinnedByTile = pinnedByTile,
                onPinnedShortcutClick = onPinnedShortcutClick,
            )
        }
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

    // ---- Sparse distant rows ----

    @Test
    fun sparseDistantRow_isComposedAtItsAbsoluteRow_andScrollsIntoView() {
        // Phase 5 contract: Home composes every tile, so a distant row exists
        // before it is scrolled to (below the viewport) instead of being windowed out.
        // Row 16: the SM-G986W viewport shows ~13 rows, so row 16 starts below it
        // (a T4 re-anchor to row 5 was device-refuted — row 5 is displayed).
        val tiles = listOf(
            tile("near", personal, "Near App", column = 0, row = 0),
            tile("far", work, "Distant App", column = 0, row = 16),
        )
        setCanvas(
            loaded(
                tiles,
                extent = GridVerticalExtent(occupiedBottomRowExclusive = 17L, totalScrollableRows = 17L),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Near App").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Near App")[0].assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Distant App").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Distant App")[0].assertIsNotDisplayed()

        // Scroll the canvas grid directly until the distant tile is on screen.
        var farShown = false
        repeat(10) {
            if (farShown) return@repeat
            composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
                .performTouchInput {
                    swipe(
                        Offset(centerX.toFloat(), centerY.toFloat()),
                        Offset(centerX.toFloat(), 0f),
                        500,
                    )
                }
            composeRule.waitForIdle()
            farShown = runCatching {
                composeRule.onAllNodesWithContentDescription("Distant App")[0].assertIsDisplayed()
            }.isSuccess
        }
        assertTrue("scrolling must bring the distant row into view", farShown)
    }

    // ---- C1 pinned shortcut strip (real list, distinct actions) ----

    @Test
    fun homeCanvasTile_lastPinClear_duringPreviewSpanChange_restoresCenteredBounds() {
        // C-B P2: last-pin removal combined with a resize preview span change.
        // The preview span arrives with a changed modifier while pins remain;
        // the modifier also carries the preview size (square 80dp cells, as the
        // grid's resize preview supplies both together), so the tile bounds
        // genuinely shrink 4x2->2x2 instead of only retagging. Clearing the
        // final pin at the preview span must restore the pin-free centered
        // treatment with measured centered bounds/extent vs the NEW bounds.
        val span = androidx.compose.runtime.mutableStateOf(LogicalSpan(4, 2))
        val pins = androidx.compose.runtime.mutableStateOf(
            listOf(PinnedTileShortcut("new-post", "New post")),
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
                iconBitmap = ImageBitmap(32, 32),
                onLaunchApp = {},
                // The preview span arrives with a changed modifier, as the
                // grid's resize preview supplies both together: tag plus the
                // preview size at the square-cell pitch (80dp per cell).
                modifier = Modifier
                    .testTag("preview-tile-${span.value.columns}x${span.value.rows}")
                    .size(80.dp * span.value.columns, 80.dp * span.value.rows),
                pinnedShortcuts = pins.value,
                onShortcutClick = {},
            )
        }
        composeRule.waitForIdle()

        // 4x2 with one pin: compact strip (app glyph + shortcut) with the icon slot.
        composeRule.onNodeWithTag("preview-tile-4x2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()
        composeRule.onNodeWithTag(HOME_TILE_ICON_TAG, useUnmergedTree = true).assertExists()
        // Baseline size evidence: 4x2 at the 80dp pitch is 320x160dp.
        val baselineTileBounds = composeRule.onAllNodesWithContentDescription("Preview App")[0]
            .fetchSemanticsNode().boundsInRoot
        val baselineW = with(composeRule.density) { 320.dp.toPx() }
        val baselineH = with(composeRule.density) { 160.dp.toPx() }
        composeRule.runOnIdle {
            assertEquals(baselineW, baselineTileBounds.width, 2f)
            assertEquals(baselineH, baselineTileBounds.height, 2f)
        }

        // Preview shrink to 2x2 while the pin remains: the strip survives
        // (2x2 capacity is 1), the binding retains the pin, nothing deleted.
        composeRule.runOnIdle { span.value = LogicalSpan(2, 2) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview-tile-2x2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("preview-tile-4x2", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertExists()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        // Preview size evidence: the tile itself shrank to 160x160dp, not
        // just retagged, so later centered assertions bind to NEW bounds.
        val previewTileBounds = composeRule.onAllNodesWithContentDescription("Preview App")[0]
            .fetchSemanticsNode().boundsInRoot
        val previewSize = with(composeRule.density) { 160.dp.toPx() }
        composeRule.runOnIdle {
            assertEquals(previewSize, previewTileBounds.width, 2f)
            assertEquals(previewSize, previewTileBounds.height, 2f)
        }

        // Clear the last pin at the preview span: strip gone, centered icon
        // slot stays, the two app regions share the label.
        composeRule.runOnIdle { pins.value = emptyList() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(APP_STRIP_GLYPH_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New post, shortcut").assertDoesNotExist()
        composeRule.onNodeWithTag(PINNED_OVERFLOW_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(HOME_TILE_ICON_TAG, useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithContentDescription("Preview App").assertCountEquals(2)

        // Measured centered bounds/extent vs the NEW (2x2 preview) bounds:
        // the tile kept its 160x160dp preview size through the pin clear, and
        // the pin-free large tile centers the icon at min(availW, availH)
        // after tile padding (centeredIconExtent), so the icon rect is square
        // at that extent sharing the NEW tile center.
        val tileBounds = composeRule.onAllNodesWithContentDescription("Preview App")[0]
            .fetchSemanticsNode().boundsInRoot
        val iconBounds = composeRule.onNodeWithTag(HOME_TILE_ICON_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val paddingPx = with(composeRule.density) { 8.dp.toPx() }
        val expectedExtent = minOf(tileBounds.width, tileBounds.height) - 2 * paddingPx
        composeRule.runOnIdle {
            assertEquals(previewSize, tileBounds.width, 2f)
            assertEquals(previewSize, tileBounds.height, 2f)
            assertEquals(iconBounds.width, iconBounds.height, 1.5f)
            assertEquals(expectedExtent, iconBounds.width, 2f)
            assertEquals(expectedExtent, iconBounds.height, 2f)
            assertEquals(tileBounds.center.x, iconBounds.center.x, 1.5f)
            assertEquals(tileBounds.center.y, iconBounds.center.y, 1.5f)
        }
    }

    // ---- A2 physical touch: shared long-press + tap independence ----

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

    // ---- Ownership/purity evidence ----

    @Test
    fun publicSignatures_referenceOnlyPredecodedSnapshots_noPlatformOrDecodeTypes() {
        // Behavior evidence: icons arrive as already-decoded ImageBitmap snapshots keyed by
        // full identity; the feature never calls LauncherApps/PackageManager/decode/database.
        // Signature evidence: inspect the feature HomeCanvas entry point for forbidden types.
        val methods = Class.forName("me.foxtails.lakehead.feature.home.HomeCanvasKt").declaredMethods
            .filter { it.name.startsWith("HomeCanvas") && !it.isSynthetic }
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
        onDropOnFolder: (ItemId, ItemId) -> Unit = { _, _ -> },
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
                onDropOnFolder = onDropOnFolder,
            )
        }
    }

    @Test
    fun aFolderTileCanBePickedUpAndMoved() {
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val folder = HomeRenderTile(
            itemId = ItemId("f"), appIdentity = null, position = LogicalPosition(0, 0),
            span = LogicalSpan(1, 1), availability = ItemAvailability.AVAILABLE, label = "Folder",
            icon = null, isLaunchable = false, folderPreview = emptyList(),
        )
        setMoveCanvas(listOf(folder), onMoveTile = { id, pos -> moves.add(id to pos) })
        composeRule.waitForIdle()
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val grid = gridNode.fetchSemanticsNode().boundsInRoot
        val f = composeRule.onAllNodesWithContentDescription("Folder")[0].fetchSemanticsNode().boundsInRoot
        val start = Offset(f.left + f.width * 0.25f - grid.left, f.top + f.height * 0.25f - grid.top)
        val stride = grid.width / 6f
        // One continuous touch from outside edit mode: hold, then drag two cells right.
        gridNode.performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(1_000)
        gridNode.performTouchInput { moveTo(start + Offset(stride * 2, 0f)) }
        composeRule.waitForIdle()
        gridNode.performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, moves.size)
            assertEquals(ItemId("f"), moves[0].first)
            assertEquals(2, moves[0].second.column)
        }
    }

    @Test
    fun dropOnAFolderTile_addsToTheFolderInsteadOfMoving() {
        val drops = mutableListOf<Pair<ItemId, ItemId>>()
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val folder = HomeRenderTile(
            itemId = ItemId("f"), appIdentity = null, position = LogicalPosition(2, 0),
            span = LogicalSpan(1, 1), availability = ItemAvailability.AVAILABLE, label = "Folder",
            icon = null, isLaunchable = false, folderPreview = emptyList(),
        )
        setMoveCanvas(
            listOf(tile("a", personal, "Alpha App", column = 0, row = 0), folder),
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onDropOnFolder = { id, f -> drops.add(id to f) },
        )
        composeRule.waitForIdle()
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val grid = gridNode.fetchSemanticsNode().boundsInRoot
        val a = composeRule.onAllNodesWithContentDescription("Alpha App")[0].fetchSemanticsNode().boundsInRoot
        val f = composeRule.onAllNodesWithContentDescription("Folder")[0].fetchSemanticsNode().boundsInRoot
        val start = Offset(a.left + a.width * 0.25f - grid.left, a.top + a.height * 0.25f - grid.top)
        val end = Offset(f.center.x - grid.left, f.center.y - grid.top)
        gridNode.performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(1_000)
        gridNode.performTouchInput { moveTo(end) }
        composeRule.waitForIdle()
        gridNode.performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(ItemId("a") to ItemId("f")), drops)
            assertTrue("a drop on a folder must not also move: $moves", moves.isEmpty())
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

    // ---- G1 end-to-end move-drag invariants ----

    @Test
    fun move_horizontalOneCell_requestsExactCellAndKeepsSpan() {
        // MoveDragMath invariant: a 1×1 tile dragged one column right requests
        // exactly the adjacent cell; a move never changes the span.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val resizes = mutableListOf<Pair<ItemId, LogicalSpan>>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 3, row = 0),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onResizeTile = { id, span -> resizes.add(id to span) },
        )
        composeRule.waitForIdle()

        // Enter wiggle-editing.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Grab upper-left quadrant (outside resize-handle zone) and swipe one
        // column stride right: candidate lands on cell (4, 0).
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        // One column stride = grid width / 6 (six-column grid, 4 gaps worth of
        // stride ≈ gridWidth / columns).
        val columnStride = gridBounds.width / 6f
        val dragEnd = dragStart + Offset(columnStride, 0f)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 500) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("one move for the dragged tile", 1, moves.size)
            assertEquals(ItemId("a"), moves[0].first)
            assertEquals(
                "horizontal one-cell move lands on exactly column 4, row 0",
                LogicalPosition(4, 0),
                moves[0].second,
            )
            assertTrue("move drag must never resize: $resizes", resizes.isEmpty())
        }
    }

    @Test
    fun move_trueFortyFiveDegreeDiagonal_requestsExactCellAndKeepsSpan() {
        // A 1×1 tile dragged exactly 45° (2 strides right, 2 down) lands on the
        // cell under (pointer − grab). Edit-mode drags start on plain radial
        // touch slop, so no axis needs to dominate.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val resizes = mutableListOf<Pair<ItemId, LogicalSpan>>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onResizeTile = { id, span -> resizes.add(id to span) },
        )
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        val stride = gridBounds.width / 6f
        val dragEnd = dragStart + Offset(2f * stride, 2f * stride)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 500) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("one move for the dragged tile", 1, moves.size)
            assertEquals(ItemId("a"), moves[0].first)
            assertEquals(
                "a true 45° move lands on exactly column 2, row 2",
                LogicalPosition(2, 2),
                moves[0].second,
            )
            assertTrue("move drag must never resize: $resizes", resizes.isEmpty())
        }
    }

    @Test
    fun move_longPressThenDragInOneGesture_startsAtOnceAndCommits() {
        // One continuous touch from outside edit mode: hold past the long-press
        // timeout, then drag. The pickup needs no slop, so the first movement
        // already carries the tile.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
        )
        composeRule.waitForIdle()

        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        val stride = gridBounds.width / 6f
        gridNode.performTouchInput { down(dragStart) }
        composeRule.mainClock.advanceTimeBy(1_000)
        gridNode.performTouchInput { moveTo(dragStart + Offset(stride, stride)) }
        composeRule.waitForIdle()
        gridNode.performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("one move for the picked-up tile", 1, moves.size)
            assertEquals(LogicalPosition(1, 1), moves[0].second)
        }
    }

    @Test
    fun move_intoOccupiedCell_holdsLastValidAndKeepsSpan() {
        // MoveDragMath / GridRules collision invariant: dragging a tile into a
        // cell occupied by another tile rejects the commit. The tile stays at
        // its origin cell and the span is unchanged.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val resizes = mutableListOf<Pair<ItemId, LogicalSpan>>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 2, row = 0),
            tile("b", work, "Beta App", column = 3, row = 0),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onResizeTile = { id, span -> resizes.add(id to span) },
        )
        composeRule.waitForIdle()

        // Enter wiggle-editing on Alpha.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Drag Alpha one cell right into Beta's cell (3, 0): collision.
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        val columnStride = gridBounds.width / 6f
        val dragEnd = dragStart + Offset(columnStride, 0f)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 500) }
        composeRule.waitForIdle()

        // Collision rejection: the editor's finish() returns null commit when
        // validity is COLLIDING, so onMoveTile is never called.
        composeRule.runOnIdle {
            assertTrue(
                "collision: move must not be committed, got $moves",
                moves.isEmpty(),
            )
            assertTrue("collision drag must never resize: $resizes", resizes.isEmpty())
        }
    }

    @Test
    fun move_topAndRightOverflow_rejectedNeverClamped() {
        // MoveDragMath out-of-bounds invariant: dragging above row 0 and past
        // the right edge produces negative column/row requests that are
        // REJECTED (never clamped to 0 or last column). No commit fires.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val resizes = mutableListOf<Pair<ItemId, LogicalSpan>>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 3, row = 1),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onResizeTile = { id, span -> resizes.add(id to span) },
        )
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Drag upward enough to push above row 0 and rightward enough to push
        // past column 5 (rightmost valid for 1×1 on 6 columns).
        // Up: 3 strides → row = 1 - 3 = -2 (OOB).
        // Right: 5 strides → column = 3 + 5 = 8 (OOB, rightmost is 5).
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        val stride = gridBounds.width / 6f
        val dragEnd = dragStart + Offset(5f * stride, -3f * stride)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 500) }
        composeRule.waitForIdle()

        // OOB rejection: MoveDragMath.cellForPixel returns Rejected, the editor
        // sets validity = OUT_OF_BOUNDS, and finish() produces a null commit.
        // The tile is never clamped into a valid cell.
        composeRule.runOnIdle {
            assertTrue(
                "OOB: move must not be committed, got $moves",
                moves.isEmpty(),
            )
            assertTrue("OOB drag must never resize: $resizes", resizes.isEmpty())
        }
    }

    // ---- G2 end-to-end resize + large-tile + auto-scroll invariants ----

    @Test
    fun resize_handleDrag_requestsNewSpanAndKeepsPosition() {
        // Grab the resize handle of a 1×1 tile, drag to grow it;
        // assert onResizeTile fires with a larger span, position unchanged,
        // and onMoveTile is empty. The resize handle must be armed first
        // via tap, then dragged from the handle corner.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val resizes = mutableListOf<Pair<ItemId, LogicalSpan>>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onResizeTile = { id, span -> resizes.add(id to span) },
        )
        composeRule.waitForIdle()

        // Enter wiggle-editing via long-press on the tile body.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Arm resize by tapping the resize handle overlay.
        // The handle's own clickable fires onToggleArmed, which toggles
        // resizeArmed. The parent drag handler sees the tap but returns
        // from awaitSlop with no movement, so the click reaches the handle.
        composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        // Drag from the resize handle corner to grow the tile.
        // The parent's unified drag handler hit-tests the pointer against
        // the handle zone at gesture start: inHandle=true + armedAtDown=true
        // → chooseRouteOnce returns RESIZE.
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        // Resize handle is center-pinned at the tile's bottom-right corner.
        val handleCenter = Offset(
            tileBounds.right - gridBounds.left,
            tileBounds.bottom - gridBounds.top,
        )
        val stride = gridBounds.width / 6f
        // Drag right + down past the corner to grow the raw span from 1×1
        // toward 2×2 (the raw span is used as-is, clamped to the grid width). The swipe keeps a dominant axis (1.5 : 1): a perfect 45°
        // diagonal never leaves slop by design (`classifyHomeAxis`
        // diagonal-never-steals contract), so the resize drag must too.
        val dragEnd = handleCenter + Offset(1.5f * stride, stride)
        gridNode.performTouchInput { swipe(handleCenter, dragEnd, 500) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("one resize for the dragged tile", 1, resizes.size)
            assertEquals(ItemId("a"), resizes[0].first)
            assertTrue(
                "resize must produce a larger span, got ${resizes[0].second}",
                resizes[0].second.columns * resizes[0].second.rows > 1,
            )
            // Resize preserves position: onMoveTile must be empty.
            assertTrue("resize drag must never move: $moves", moves.isEmpty())
        }
    }

    @Test
    fun move_cornerGrabOnLargeTile_noRecenterTransfer() {
        // 2×2 tile: grab its upper-left corner quadrant (outside the
        // bottom-right handle zone) and drag downward. The pointer is on
        // the tile body, not the handle, so chooseRouteOnce returns MOVE
        // (not RESIZE). Assert exactly one onMoveTile commit for the
        // dragged tile and no resize. The grab offset is preserved from
        // the initial grab point (no recentering to tile center).
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val resizes = mutableListOf<Pair<ItemId, LogicalSpan>>()
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 1, row = 1, spanColumns = 2, spanRows = 2),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onResizeTile = { id, span -> resizes.add(id to span) },
        )
        composeRule.waitForIdle()

        // Enter wiggle-editing via long-press.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Grab the upper-left quadrant (25% from left, 25% from top).
        // For a 2×2 tile, the handle zone is the bottom-right corner;
        // 25%/25% is well outside that zone.
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        val stride = gridBounds.width / 6f
        // Drag 3 strides down to move the tile.
        val dragEnd = dragStart + Offset(0f, 3f * stride)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 500) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("one move for the dragged tile", 1, moves.size)
            assertEquals(ItemId("a"), moves[0].first)
            assertTrue(
                "drag must commit a new cell, got ${moves[0].second}",
                moves[0].second != LogicalPosition(1, 1),
            )
            assertTrue("corner grab must never resize: $resizes", resizes.isEmpty())
        }
    }

    @Test
    fun drag_downwardEdgeHold_extendsScrollWhileDragging() {
        // Long content grid: drag a tile downward toward the bottom of
        // the viewport. The edge ticker reads the viewport pointer from
        // moveEdgePointer; a slow swipe keeps the pointer near the bottom
        // auto-scroll band long enough for the scroll offset to grow,
        // pushing the move candidate into lower rows. Assert the drag
        // committed a valid cell and the tile moved from its origin.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        // Grid with enough tiles that content overflows the viewport,
        // giving the auto-scroll band room to operate.
        val tiles = listOf(
            tile("a", personal, "Alpha App", column = 0, row = 0),
            tile("b", work, "Beta App", column = 2, row = 0),
            tile("c", personal, "Gamma App", column = 4, row = 0),
            tile("d", personal, "Delta App", column = 0, row = 3),
        )
        setMoveCanvas(
            tiles,
            onMoveTile = { id, pos -> moves.add(id to pos) },
        )
        composeRule.waitForIdle()

        // Enter wiggle-editing.
        composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Slow swipe toward the bottom of the viewport.
        val gridNode = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tileBounds = composeRule.onAllNodesWithContentDescription("Alpha App")[0]
            .fetchSemanticsNode().boundsInRoot
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val dragStart = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        val stride = gridBounds.width / 6f
        // Target near the bottom of the visible grid; 2000ms gives the
        // edge ticker dwell time to advance the scroll offset.
        val dragEnd = Offset(dragStart.x, gridBounds.height * 0.9f)
        gridNode.performTouchInput { swipe(dragStart, dragEnd, 2000) }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue("scroll + commit: must commit a move", moves.isNotEmpty())
            val lastPos = moves.last().second
            assertTrue(
                "eventual commit must be a valid cell (row >= 0), got $lastPos",
                lastPos.row >= 0 && lastPos.column >= 0,
            )
            assertTrue(
                "scroll drag: tile must have moved from origin (0,0), got $lastPos",
                lastPos != LogicalPosition(0, 0),
            )
        }
    }
}
