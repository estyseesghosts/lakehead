package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.designsystem.RESIZE_HANDLE_TAG
import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridVerticalLayout
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
 * Phase 5.1/5.2 contract on a real device: Home composes every tile at its
 * absolute logical row, and the scroll range is the content that was measured.
 */
@RunWith(AndroidJUnit4::class)
class HomeAbsoluteLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val homeId = HomeId("home-absolute")
    private var state by mutableStateOf<HomeRenderState>(HomeRenderState.Loading(homeId))

    private fun tile(name: String, row: Int, column: Int = 0, rows: Int = 1) = HomeRenderTile(
        itemId = ItemId(name),
        appIdentity = AppIdentity(ProfileId("0"), ComponentIdentity("com.example.$name", "com.example.$name.Main")),
        position = LogicalPosition(column, row),
        span = LogicalSpan(1, rows),
        availability = ItemAvailability.AVAILABLE,
        label = name,
        icon = null,
        isLaunchable = true,
    )

    private fun loaded(vararg tiles: HomeRenderTile): HomeRenderState.Loaded {
        val extent = GridVerticalLayout.measure(
            placements = tiles.map {
                me.foxtails.lakehead.core.grid.GridPlacement(
                    it.itemId,
                    me.foxtails.lakehead.core.grid.GridPosition(it.position.column, it.position.row),
                    me.foxtails.lakehead.core.grid.GridSpan(it.span.columns, it.span.rows),
                )
            },
            viewportRows = 10L,
            workingMarginRows = 0L,
        )
        return HomeRenderState.Loaded(homeId, tiles.toList(), GridColumns(6), extent)
    }

    private val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()

    private fun show(initial: HomeRenderState.Loaded) {
        state = initial
        composeRule.setContent {
            LauncherTheme {
                HomeCanvas(
                    state = state,
                    iconsByIdentity = emptyMap(),
                    onLaunchApp = {},
                    onRequestDefaultHome = {},
                    onMoveTile = { id, position -> moves.add(id to position) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun tileNode(name: String): SemanticsNode =
        composeRule.onAllNodesWithContentDescription(name)[0].fetchSemanticsNode()

    private fun gridNode(): SemanticsNode =
        composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true).fetchSemanticsNode()

    /** Tile top relative to the top of the scroll content (scroll is at zero in these tests). */
    private fun contentTop(name: String): Float = tileNode(name).positionInRoot.y - gridNode().positionInRoot.y

    @Test
    fun tileAtRowN_isPlacedAtRowN_whicheverOtherRowsAreOccupied() {
        show(loaded(tile("a", 0), tile("b", 1), tile("z", 16)))
        val stride = contentTop("b") - contentTop("a")
        assertEquals("row 16 sits sixteen strides below row 0", 16 * stride, contentTop("z") - contentTop("a"), 1f)
        val withNeighbours = contentTop("z")

        composeRule.runOnIdle { state = loaded(tile("z", 16)) }
        composeRule.waitForIdle()
        assertEquals("a lone tile keeps its absolute row", withNeighbours, contentTop("z"), 1f)
        assertEquals(16 * stride, contentTop("z"), 1f)
    }

    @Test
    fun loneTileAtRow1000_isNotRebased() {
        show(loaded(tile("a", 0), tile("b", 1), tile("far", 1000)))
        val stride = contentTop("b") - contentTop("a")
        assertEquals(1000 * stride, contentTop("far") - contentTop("a"), 2f)
    }

    @Test
    fun scrollRange_isTheMeasuredContentHeight() {
        show(loaded(tile("a", 0), tile("z", 16, rows = 2)))
        val grid = gridNode()
        val viewportHeight = grid.size.height
        val lowestBottom = contentTop("z") + tileNode("z").size.height
        val maxValue = grid.config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        assertEquals(
            "scroll range must equal content bottom minus viewport",
            lowestBottom - viewportHeight,
            maxValue,
            1f,
        )
    }

    @Test
    fun centredShortGrid_frameDoesNotMoveWhenEditingBegins_orUnderAStationaryDrag() {
        // Two tiles in a tall viewport: content is centred by a nonzero rest offset.
        show(loaded(tile("a", 0), tile("b", 1)))
        val restTop = contentTop("a")
        assertTrue("short content is centred (rest offset > 0): $restTop", restTop > 10f)
        val stride = contentTop("b") - restTop

        composeRule.onAllNodesWithContentDescription("a")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
        assertEquals("entering edit mode does not move the frame", restTop, contentTop("a"), 0.5f)
        // Recessed tiles scale in edit mode, so the baseline is taken once edit mode is on.
        val restTopB = contentTop("b")

        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val tile = tileNode("a")
        val gridPos = gridNode().positionInRoot
        // Upper-left quadrant: the exact centre of a small tile is inside the resize-handle zone.
        val start = Offset(
            tile.positionInRoot.x - gridPos.x + tile.size.width * 0.25f,
            tile.positionInRoot.y - gridPos.y + tile.size.height * 0.25f,
        )
        grid.performTouchInput { down(start) }
        repeat(10) { grid.performTouchInput { moveBy(Offset(0f, 0.2f * stride)) } }
        // Hold the pointer still; the old animated origin drifted here as the
        // edit layout made the content "tall".
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        // The lifted tile follows the finger; the frame is read from a tile that stays put.
        assertEquals("a held drag keeps the frame", restTopB, contentTop("b"), 0.5f)
        grid.performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(
                "two strides down from a stationary frame is exactly row 2 (rest offset included in the map)",
                listOf(ItemId("a") to LogicalPosition(0, 2)),
                moves.toList(),
            )
        }
    }

    private fun scrollMax(): Float {
        val config = gridNode().config
        return if (config.contains(SemanticsProperties.VerticalScrollAxisRange)) {
            config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        } else {
            0f
        }
    }

    private fun grabTileForMove(name: String): Offset {
        composeRule.onAllNodesWithContentDescription(name)[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()
        val tile = tileNode(name)
        val gridPos = gridNode().positionInRoot
        return Offset(
            tile.positionInRoot.x - gridPos.x + tile.size.width * 0.25f,
            tile.positionInRoot.y - gridPos.y + tile.size.height * 0.25f,
        )
    }

    @Test
    fun moveDrag_onShortContent_addsNoScrollRange_andReleasingWithoutMovingCommitsNothing() {
        show(loaded(tile("a", 0), tile("b", 1)))
        assertEquals("content that fits does not scroll when idle", 0f, scrollMax(), 0f)
        val start = grabTileForMove("a")
        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        grid.performTouchInput { down(start) }
        repeat(3) { grid.performTouchInput { moveBy(Offset(0f, 10f)) } }
        composeRule.waitForIdle()
        assertEquals("the workspace is the committed bottom plus 2 rows plus the span", 0f, scrollMax(), 0f)
        grid.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("nothing was moved far enough to commit", 0f, scrollMax(), 0f)
    }

    @Test
    fun heldBottomEdgeDrag_scrollsWithinTheCappedWorkspace_withNoCommitUntilRelease() {
        show(loaded(tile("a", 0), tile("z", 16, rows = 2)))
        val start = grabTileForMove("a")
        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        grid.performTouchInput { down(start) }
        repeat(3) { grid.performTouchInput { moveBy(Offset(0f, 10f)) } }
        composeRule.waitForIdle()
        val range = scrollMax()
        assertTrue("tall content scrolls: $range", range > 0f)
        // A held edge requests a frame per tick, so drive the clock by hand there.
        composeRule.mainClock.autoAdvance = false
        grid.performTouchInput { moveTo(Offset(start.x, gridNode().size.height - 4f)) }
        composeRule.mainClock.advanceTimeBy(6_000)
        val reached = scrollValue()
        grid.performTouchInput { moveTo(Offset(start.x, gridNode().size.height / 2f)) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue("holding the bottom edge scrolled: $reached", reached > 0f)
        assertTrue("never past the workspace cap: $reached of ${scrollMax()}", reached <= scrollMax() + 1f)
        composeRule.runOnIdle { assertTrue("no commit while held", moves.isEmpty()) }
        grid.performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun scrollValue(): Float {
        val config = gridNode().config
        return if (config.contains(SemanticsProperties.VerticalScrollAxisRange)) {
            config[SemanticsProperties.VerticalScrollAxisRange].value()
        } else {
            0f
        }
    }

    @Test
    fun autoScroll_isContinuousPerFrame_notRowTeleports() {
        show(loaded(tile("a", 0), tile("z", 16, rows = 2)))
        val start = grabTileForMove("a")
        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        grid.performTouchInput { down(start) }
        try {
            repeat(3) { grid.performTouchInput { moveBy(Offset(0f, 10f)) } }
            composeRule.mainClock.autoAdvance = false
            grid.performTouchInput { moveTo(Offset(start.x, gridNode().size.height - 4f)) }
            composeRule.mainClock.advanceTimeBy(48)
            val first = scrollValue()
            composeRule.mainClock.advanceTimeBy(48)
            val second = scrollValue()
            assertTrue("scrolls from the first frames: $first", first > 0f)
            assertTrue("keeps scrolling every frame: $first -> $second", second > first)
            composeRule.runOnIdle { assertTrue("scrolling never commits", moves.isEmpty()) }
        } finally {
            grid.performTouchInput { moveTo(Offset(start.x, gridNode().size.height / 2f)) }
            composeRule.mainClock.autoAdvance = true
            grid.performTouchInput { up() }
        }
    }

    @Test
    fun overlays_sitOnTheTileRect_atNonzeroRowColumnAndRestOffset() {
        show(loaded(tile("u", 0), tile("t", row = 1, column = 2)))
        assertTrue("rest offset is nonzero", contentTop("u") > 10f)
        composeRule.onAllNodesWithContentDescription("t")[0]
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.waitForIdle()

        val tile = tileNode("t")
        val left = tile.positionInRoot.x
        val top = tile.positionInRoot.y
        val right = left + tile.size.width
        val bottom = top + tile.size.height

        // Corner controls are centre-pinned on the tile's corners: each contains its corner.
        val remove = composeRule.onNodeWithContentDescription("Remove t").fetchSemanticsNode().boundsInRoot
        assertTrue("remove control holds the top-right corner", remove.contains(Offset(right, top)))
        val handle = composeRule.onNodeWithTag(RESIZE_HANDLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("resize handle holds the bottom-right corner", handle.contains(Offset(right, bottom)))

        // The candidate outline of a just-begun MOVE is exactly the tile rect.
        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val gridPos = gridNode().positionInRoot
        val start = Offset(
            left - gridPos.x + tile.size.width * 0.25f,
            top - gridPos.y + tile.size.height * 0.25f,
        )
        grid.performTouchInput { down(start) }
        repeat(3) { grid.performTouchInput { moveBy(Offset(0f, 10f)) } }
        composeRule.waitForIdle()
        val outline = composeRule.onNodeWithTag(MOVE_CANDIDATE_OVERLAY_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(left, outline.positionInRoot.x, 0.5f)
        assertEquals(top, outline.positionInRoot.y, 0.5f)
        assertEquals(tile.size.width, outline.size.width)
        assertEquals(tile.size.height, outline.size.height)
        grid.performTouchInput { up() }
    }

    @Test
    fun contentThatFits_doesNotScroll() {
        show(loaded(tile("a", 0), tile("b", 1)))
        // Either no scroll axis is exposed (scrolling disabled) or its range is zero.
        val config = gridNode().config
        val maxValue = if (config.contains(SemanticsProperties.VerticalScrollAxisRange)) {
            config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        } else {
            0f
        }
        assertEquals(0f, maxValue, 0f)
    }

    @Test
    fun scrolledDrag_byOneStride_commitsExactlyOneRow() {
        // Regression for the scrolled-drag double-count: gesture Y arrives
        // node-local (scroll included) and must convert once to viewport-local.
        show(loaded(tile("a", 0), tile("b", 1), tile("mid", 5), tile("z", 16, rows = 2)))
        val stride = contentTop("b") - contentTop("a")
        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val w = gridNode().size.width.toFloat()
        val h = gridNode().size.height.toFloat()
        grid.performTouchInput {
            swipe(Offset(w / 2f, h * 0.7f), Offset(w / 2f, h * 0.7f - 3 * stride), 400)
        }
        composeRule.waitForIdle()
        val scrolled = scrollValue()
        assertTrue("precondition: grid scrolled, got $scrolled", scrolled > stride)
        // Mid row stays on screen after a 3-row scroll of a ~13-row viewport.
        val start = grabTileForMove("mid")
        grid.performTouchInput { down(start) }
        repeat(3) { grid.performTouchInput { moveBy(Offset(0f, 10f)) } }
        composeRule.waitForIdle()
        grid.performTouchInput { moveTo(start + Offset(0f, stride)) }
        grid.performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(
                "one stride down from row 5 while scrolled is exactly row 6, got $moves",
                listOf(ItemId("mid") to LogicalPosition(0, 6)),
                moves.toList(),
            )
        }
    }

    @Test
    fun scrolledHoldAtTopEdge_scrollsUp() {
        // Edge zones are viewport-local: a finger 4px below the viewport top
        // while scrolled must scroll up. Content-space Y (4 + scroll) would miss
        // the band and never scroll.
        show(loaded(tile("a", 0), tile("mid", 5), tile("z", 16, rows = 2)))
        val stride = contentTop("mid") - contentTop("a")
        val perRow = stride / 5f
        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val w = gridNode().size.width.toFloat()
        val h = gridNode().size.height.toFloat()
        grid.performTouchInput {
            swipe(Offset(w / 2f, h * 0.7f), Offset(w / 2f, h * 0.7f - 3 * perRow), 400)
        }
        composeRule.waitForIdle()
        val scrolled = scrollValue()
        assertTrue("precondition: grid scrolled, got $scrolled", scrolled > perRow)
        val start = grabTileForMove("mid")
        grid.performTouchInput { down(start) }
        try {
            repeat(3) { grid.performTouchInput { moveBy(Offset(0f, 10f)) } }
            composeRule.mainClock.autoAdvance = false
            grid.performTouchInput { moveTo(Offset(start.x, 4f)) }
            composeRule.mainClock.advanceTimeBy(2_000)
            val after = scrollValue()
            grid.performTouchInput { moveTo(Offset(start.x, gridNode().size.height / 2f)) }
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            assertTrue("top edge while scrolled must scroll up: $scrolled -> $after", after < scrolled)
        } finally {
            composeRule.mainClock.autoAdvance = true
            grid.performTouchInput { up() }
        }
        composeRule.waitForIdle()
    }
}
