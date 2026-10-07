package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.designsystem.LauncherTheme
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Slice 4.4 integration: a tile drag that owns the pointer is not stolen by
 * page navigation. A real two-page [HorizontalPager] hosts the canvas, and the
 * tile is dragged in the direction that would navigate to the next page.
 */
@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class HomeDragPagerArbitrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val homeId = HomeId("home-pager-test")
    private val identity = AppIdentity(ProfileId("0"), ComponentIdentity("com.example.app", "Main"))

    private fun tile(item: String, label: String, column: Int, row: Int) = HomeRenderTile(
        itemId = ItemId(item),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = label,
        icon = null,
        isLaunchable = true,
    )

    private lateinit var pagerState: PagerState

    /** Mirrors the shell: swipe is enabled unless a canvas hold signal asks for it to be held. */
    private fun setPagedCanvas(
        tiles: List<HomeRenderTile>,
        holdPagerFromCanvas: Boolean,
        onMoveTile: (ItemId, LogicalPosition) -> Unit,
        onPagerHoldChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            LauncherTheme {
                pagerState = rememberPagerState(pageCount = { 2 })
                var homeHold by remember { mutableStateOf(false) }
                var tileHold by remember { mutableStateOf(false) }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !holdPagerFromCanvas || !(homeHold || tileHold),
                ) { page ->
                    if (page == 0) {
                        HomeCanvas(
                            state = HomeRenderState.Loaded(
                                requestedHomeId = homeId,
                                tiles = tiles,
                                columns = GridColumns(6),
                                extent = GridVerticalExtent(4L, 10L),
                            ),
                            iconsByIdentity = emptyMap(),
                            onLaunchApp = {},
                            onRequestDefaultHome = {},
                            modifier = Modifier.fillMaxSize(),
                            onMoveTile = onMoveTile,
                            onTransientHoldChange = { homeHold = it },
                            onPagerHoldChange = {
                                tileHold = it
                                onPagerHoldChange(it)
                            },
                            isVisible = pagerState.currentPage == 0,
                        )
                    }
                }
            }
        }
    }

    private fun dragTileLeftAcrossGrid(label: String) {
        composeRule.onAllNodesWithContentDescription(label)[0].performTouchInput { longClick() }
        composeRule.waitForIdle()
        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val gridBounds = grid.fetchSemanticsNode().boundsInRoot
        val tileBounds = composeRule.onAllNodesWithContentDescription(label)[0]
            .fetchSemanticsNode().boundsInRoot
        // Grab the upper-left quadrant: on a small tile the exact centre already
        // lies inside the (shrunk) bottom-right resize-handle zone, which routes
        // an unarmed drag to NONE by design.
        val start = Offset(
            tileBounds.left + tileBounds.width * 0.25f - gridBounds.left,
            tileBounds.top + tileBounds.height * 0.25f - gridBounds.top,
        )
        // Three columns to the left is the next-page direction for the pager.
        val end = start + Offset(-gridBounds.width / 2f, 0f)
        grid.performTouchInput { swipe(start, end, 600) }
        composeRule.waitForIdle()
    }

    @Test
    fun activeTileDrag_isNotStolenByPageNavigation_whenPagerIsHeldByTheCanvas() {
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        val holds = mutableListOf<Boolean>()
        setPagedCanvas(
            tiles = listOf(tile("a", "Alpha App", column = 5, row = 0)),
            holdPagerFromCanvas = true,
            onMoveTile = { id, pos -> moves.add(id to pos) },
            onPagerHoldChange = { holds.add(it) },
        )
        composeRule.waitForIdle()

        dragTileLeftAcrossGrid("Alpha App")

        composeRule.runOnIdle {
            assertEquals("drag must not navigate the pager", 0, pagerState.currentPage)
            assertEquals("the tile drag commits exactly one move: $moves", 1, moves.size)
            assertTrue("moved left of its origin: ${moves[0].second}", moves[0].second.column < 5)
            assertTrue("tile touch held the pager: $holds", holds.contains(true))
            assertEquals("hold clears on release", false, holds.lastOrNull())
        }
    }

    @Test
    fun activeTileDrag_isNotStolenByPageNavigation_evenWhenPagerIsNotHeld() {
        // The drag owns the pointer by consuming it; page navigation must not
        // win even if the shell's hold signal were absent.
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        setPagedCanvas(
            tiles = listOf(tile("a", "Alpha App", column = 5, row = 0)),
            holdPagerFromCanvas = false,
            onMoveTile = { id, pos -> moves.add(id to pos) },
        )
        composeRule.waitForIdle()

        dragTileLeftAcrossGrid("Alpha App")

        composeRule.runOnIdle {
            assertEquals("drag must not navigate the pager", 0, pagerState.currentPage)
            assertEquals("the tile drag commits exactly one move: $moves", 1, moves.size)
        }
    }

    @Test
    fun control_sameDragWithoutAPager_commitsAMove() {
        val moves = mutableListOf<Pair<ItemId, LogicalPosition>>()
        composeRule.setContent {
            LauncherTheme {
                HomeCanvas(
                    state = HomeRenderState.Loaded(
                        requestedHomeId = homeId,
                        tiles = listOf(tile("a", "Alpha App", column = 5, row = 0)),
                        columns = GridColumns(6),
                        extent = GridVerticalExtent(4L, 10L),
                    ),
                    iconsByIdentity = emptyMap(),
                    onLaunchApp = {},
                    onRequestDefaultHome = {},
                    modifier = Modifier.fillMaxSize(),
                    onMoveTile = { id, pos -> moves.add(id to pos) },
                )
            }
        }
        composeRule.waitForIdle()

        dragTileLeftAcrossGrid("Alpha App")

        composeRule.runOnIdle { assertEquals("control commits one move: $moves", 1, moves.size) }
    }

    @Test
    fun backgroundSwipe_stillNavigatesThePager() {
        // Control: the same harness navigates for a gesture that starts on
        // empty grid space, so the assertions above can actually fail.
        setPagedCanvas(
            tiles = listOf(tile("a", "Alpha App", column = 5, row = 0)),
            holdPagerFromCanvas = true,
            onMoveTile = { _, _ -> },
        )
        composeRule.waitForIdle()

        val grid = composeRule.onNodeWithTag(HOME_CANVAS_GRID_TAG, useUnmergedTree = true)
        val bounds = grid.fetchSemanticsNode().boundsInRoot
        val y = bounds.height * 0.6f
        grid.performTouchInput {
            swipe(Offset(bounds.width * 0.85f, y), Offset(bounds.width * 0.1f, y), 300)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("background swipe navigates", 1, pagerState.currentPage)
        }
    }
}
