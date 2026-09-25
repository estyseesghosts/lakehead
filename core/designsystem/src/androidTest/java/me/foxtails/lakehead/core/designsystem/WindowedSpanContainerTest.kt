package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused composition tests for the D3 [WindowedSpanContainer] primitive: windowed
 * composition count, overscan and boundary-span inclusion, empty canvas, sparse distant
 * row rebasing, generalized 2/6/8-column geometry, stable caller order, minimum height,
 * and invalid/unrepresentable input.
 *
 * Positions use `cellSizePx = 10, columnGapPx = 2, rowGapPx = 3` (x stride 12, y stride
 * 13) unless a test states otherwise. Six columns is only the reference arrangement:
 * geometry here is [SquareCellGeometry] with any positive count and no minimum/clamp.
 */
@RunWith(AndroidJUnit4::class)
class WindowedSpanContainerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val referenceGeometry = SquareCellGeometry(
        columnCount = 6,
        cellSizePx = 10,
        columnGapPx = 2,
        rowGapPx = 3,
    )

    @Test
    fun onlyIntersectingChildrenAreComposed() {
        val composed = mutableListOf<String>()
        composeRule.setContent {
            WindowedSpanContainer(
                children = (0..4).map { row ->
                    SpanChild(GridPlacement("r$row", GridPosition(0, row), GridSpan(1, 1))) {
                        composed.add("r$row")
                        Box(Modifier.semantics { contentDescription = "tile-r$row" })
                    }
                },
                window = GridRowWindow(1L, 3L),
                geometry = referenceGeometry,
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf("r1", "r2"), composed) }
        composeRule.onNodeWithContentDescription("tile-r1").assertExists()
        composeRule.onNodeWithContentDescription("tile-r2").assertExists()
        composeRule.onNodeWithContentDescription("tile-r0").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("tile-r3").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("tile-r4").assertDoesNotExist()
    }

    @Test
    fun overscanIncludesNearVisibleRows() {
        val composed = mutableListOf<String>()
        composeRule.setContent {
            WindowedSpanContainer(
                children = (0..4).map { row ->
                    SpanChild(GridPlacement("r$row", GridPosition(0, row), GridSpan(1, 1))) {
                        composed.add("r$row")
                        Box(Modifier.semantics { contentDescription = "tile-r$row" })
                    }
                },
                window = GridRowWindow(2L, 3L),
                overscanRows = 1L,
                geometry = referenceGeometry,
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf("r1", "r2", "r3"), composed) }
        composeRule.onNodeWithContentDescription("tile-r0").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("tile-r4").assertDoesNotExist()
    }

    @Test
    fun spansCrossingEitherBoundaryAreIncluded() {
        composeRule.setContent {
            WindowedSpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("before", GridPosition(0, 1), GridSpan(1, 2))) {
                        Box(Modifier.semantics { contentDescription = "tile-before" })
                    },
                    SpanChild(GridPlacement("inside", GridPosition(1, 3), GridSpan(1, 1))) {
                        Box(Modifier.semantics { contentDescription = "tile-inside" })
                    },
                    SpanChild(GridPlacement("after", GridPosition(0, 4), GridSpan(1, 2))) {
                        Box(Modifier.semantics { contentDescription = "tile-after" })
                    },
                    SpanChild(GridPlacement("outside", GridPosition(0, 6), GridSpan(1, 1))) {
                        Box(Modifier.semantics { contentDescription = "tile-outside" })
                    },
                ),
                window = GridRowWindow(3L, 5L),
                geometry = referenceGeometry,
            )
        }

        // "before" rows [1,3) end exactly at the window start: half-open exclusion.
        composeRule.onNodeWithContentDescription("tile-before").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("tile-inside").assertExists()
        // "after" rows [4,6) cross the exclusive end boundary and stay included.
        composeRule.onNodeWithContentDescription("tile-after").assertExists()
        composeRule.onNodeWithContentDescription("tile-outside").assertDoesNotExist()
    }

    @Test
    fun spanStartingAboveWindowIsComposedWithNegativeTop() {
        val bounds = WindowedSpanLayout.boundsFor(
            children = listOf(
                SpanChild(GridPlacement("cross", GridPosition(0, 8), GridSpan(1, 4))) { Box(Modifier) },
            ),
            window = GridRowWindow(10L, 12L),
            overscanRows = 0L,
            geometry = referenceGeometry,
        )

        // Top = (8 - 10) * 13 = -26; height = 4 * 10 + 3 * 3 = 49; bottom = 23.
        assertEquals(1, bounds.placed.size)
        assertEquals(10L, bounds.origin)
        assertEquals(-26, bounds.placed[0].top)
        assertEquals(23, bounds.placed[0].bottom)

        composeRule.setContent {
            WindowedSpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("cross", GridPosition(0, 8), GridSpan(1, 4))) {
                        Box(Modifier.semantics { contentDescription = "tile-cross" })
                    },
                ),
                window = GridRowWindow(10L, 12L),
                geometry = referenceGeometry,
            )
        }
        composeRule.onNodeWithContentDescription("tile-cross").assertExists()
    }

    @Test
    fun emptyChildrenOccupyZeroSizeAndComposeNothing() {
        val composed = mutableListOf<String>()
        var containerSize: IntSize? = null
        composeRule.setContent {
            WindowedSpanContainer<String>(
                modifier = Modifier.onGloballyPositioned { containerSize = it.size },
                children = emptyList(),
                window = GridRowWindow(0L, 4L),
                geometry = referenceGeometry,
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue(composed.isEmpty())
            assertEquals(IntSize(0, 0), containerSize)
        }
    }

    @Test
    fun disjointWindowComposesNothingAndKeepsMinimumHeight() {
        val composed = mutableListOf<String>()
        var containerSize: IntSize? = null
        composeRule.setContent {
            WindowedSpanContainer(
                modifier = Modifier.onGloballyPositioned { containerSize = it.size },
                children = listOf(
                    SpanChild(GridPlacement("far", GridPosition(0, 50), GridSpan(1, 1))) {
                        composed.add("far")
                        Box(Modifier.semantics { contentDescription = "tile-far" })
                    },
                ),
                window = GridRowWindow(0L, 4L),
                geometry = referenceGeometry,
                minHeightPx = 40,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("tile-far").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(composed.isEmpty())
            assertEquals(IntSize(0, 40), containerSize)
        }
    }

    @Test
    fun distantRowIsRebasedToSafeLocalCoordinates() {
        val nearMax = Int.MAX_VALUE - 1
        val frames = mutableMapOf<String, Frame>()
        composeRule.setContent {
            WindowedSpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("near", GridPosition(0, 0), GridSpan(1, 1))) {
                        Box(Modifier.semantics { contentDescription = "tile-near" })
                    },
                    SpanChild(GridPlacement("distant", GridPosition(1, nearMax), GridSpan(1, 1))) {
                        Box(
                            Modifier
                                .onGloballyPositioned { frames["distant"] = Frame(it) }
                                .semantics { contentDescription = "tile-distant" },
                        )
                    },
                ),
                window = GridRowWindow(nearMax.toLong() - 1L, nearMax.toLong() + 1L),
                geometry = referenceGeometry,
            )
        }
        composeRule.waitForIdle()

        // Only the distant tile is composed; its local row is 1, so top = 13.
        composeRule.onNodeWithContentDescription("tile-near").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("tile-distant").assertExists()
        composeRule.runOnIdle {
            assertEquals(IntSize(10, 10), frames.getValue("distant").size)
            // The container is at the test root, so positionInRoot equals the rebased
            // local offset: local row 1 * 13 = 13, column 1 * 12 = 12.
            assertEquals(13f, frames.getValue("distant").position.y)
            assertEquals(12f, frames.getValue("distant").position.x)
        }
    }

    @Test
    fun geometryWorksForTwoColumns() {
        geometryRendersSpannedTileAtCount(2)
    }

    @Test
    fun geometryWorksForSixColumns() {
        geometryRendersSpannedTileAtCount(6)
    }

    @Test
    fun geometryWorksForEightColumns() {
        geometryRendersSpannedTileAtCount(8)
    }

    private fun geometryRendersSpannedTileAtCount(count: Int) {
        val geometry = SquareCellGeometry(
            columnCount = count,
            cellSizePx = 10,
            columnGapPx = 2,
            rowGapPx = 3,
        )
        val frames = mutableMapOf<String, Frame>()
        composeRule.setContent {
            WindowedSpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("tile", GridPosition(1, 1), GridSpan(2, 1))) {
                        Box(Modifier.onGloballyPositioned { frames["tile"] = Frame(it) })
                    },
                ),
                window = GridRowWindow(0L, 4L),
                geometry = geometry,
            )
        }
        composeRule.waitForIdle()

        // Left = 1 * 12 = 12, top = 1 * 13 = 13, extent = 2 * 10 + 2 = 22 by 10.
        composeRule.runOnIdle {
            val frame = frames.getValue("tile")
            assertEquals(IntSize(22, 10), frame.size)
            assertEquals(12f, frame.position.x)
            assertEquals(13f, frame.position.y)
        }
    }

    @Test
    fun stableInputOrderIsPreserved() {
        val composed = mutableListOf<String>()
        composeRule.setContent {
            WindowedSpanContainer(
                children = listOf("c", "a", "b").map { id ->
                    // Deliberately unsorted by row: composition must follow caller order.
                    val row = when (id) {
                        "c" -> 3
                        "a" -> 1
                        else -> 2
                    }
                    SpanChild(GridPlacement(id, GridPosition(0, row), GridSpan(1, 1))) {
                        composed.add(id)
                        Box(Modifier.semantics { contentDescription = "tile-$id" })
                    }
                },
                window = GridRowWindow(0L, 5L),
                geometry = referenceGeometry,
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf("c", "a", "b"), composed) }
    }

    @Test
    fun callerSemanticsPassThrough() {
        composeRule.setContent {
            WindowedSpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("a", GridPosition(0, 1), GridSpan(1, 1))) {
                        Box(Modifier.semantics { contentDescription = "Tile A" })
                    },
                ),
                window = GridRowWindow(0L, 4L),
                geometry = referenceGeometry,
            )
        }

        composeRule.onNodeWithContentDescription("Tile A").assertExists()
    }

    @Test
    fun negativeOverscanOriginAndMinimumAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WindowedSpanLayout.boundsFor(
                children = emptyList<SpanChild<String>>(),
                window = GridRowWindow(0L, 1L),
                overscanRows = -1L,
                geometry = referenceGeometry,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WindowedSpanLayout.boundsFor(
                children = emptyList<SpanChild<String>>(),
                window = GridRowWindow(0L, 1L),
                overscanRows = 0L,
                geometry = referenceGeometry,
                originX = -1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WindowedSpanLayout.boundsFor(
                children = emptyList<SpanChild<String>>(),
                window = GridRowWindow(0L, 1L),
                overscanRows = 0L,
                geometry = referenceGeometry,
                rowOrigin = -1L,
            )
        }
    }

    @Test
    fun negativeOverscanInCompositionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                WindowedSpanContainer<String>(
                    children = emptyList(),
                    window = GridRowWindow(0L, 1L),
                    overscanRows = -1L,
                    geometry = referenceGeometry,
                )
            }
        }
    }

    @Test
    fun negativeMinHeightInCompositionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                WindowedSpanContainer<String>(
                    children = emptyList(),
                    window = GridRowWindow(0L, 1L),
                    geometry = referenceGeometry,
                    minHeightPx = -1,
                )
            }
        }
    }

    @Test
    fun invalidWindowsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GridRowWindow(-1L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { GridRowWindow(2L, 1L) }
    }

    @Test
    fun unrepresentableSparseExtentFailsExplicitly() {
        val farRow = Int.MAX_VALUE
        // Both children are selected by the wide window, but the rebased bottom of the
        // distant child (Int.MAX_VALUE * 13) cannot fit Int pixels.
        assertThrows(IllegalArgumentException::class.java) {
            WindowedSpanLayout.boundsFor(
                children = listOf(
                    SpanChild(GridPlacement("near", GridPosition(0, 0), GridSpan(1, 1))) {
                        Box(Modifier)
                    },
                    SpanChild(GridPlacement("far", GridPosition(0, farRow), GridSpan(1, 1))) {
                        Box(Modifier)
                    },
                ),
                window = GridRowWindow(0L, farRow.toLong() + 1L),
                overscanRows = 0L,
                geometry = referenceGeometry,
            )
        }
    }

    @Test
    fun emptyWindowSelectsNothing() {
        val bounds = WindowedSpanLayout.boundsFor(
            children = listOf(
                SpanChild(GridPlacement("at", GridPosition(0, 4), GridSpan(1, 1))) { Box(Modifier) },
            ),
            window = GridRowWindow(4L, 4L),
            overscanRows = 0L,
            geometry = referenceGeometry,
        )
        assertTrue(bounds.placed.isEmpty())
    }

    private data class Frame(
        val position: Offset,
        val size: IntSize,
    ) {
        constructor(coordinates: LayoutCoordinates) : this(
            position = coordinates.positionInRoot(),
            size = coordinates.size,
        )
    }
}
