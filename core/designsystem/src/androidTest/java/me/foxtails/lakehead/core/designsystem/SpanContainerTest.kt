package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused composition tests for the C1.3 [SpanContainer] primitive: child measurement,
 * logical-grid placement, container extent, invalid-input behavior, and the narrow
 * semantics contract (caller-supplied labels pass through; the geometry primitive
 * invents no click action).
 *
 * Positions and sizes are captured in pixels via `onGloballyPositioned`, so assertions
 * hold at any test density. Expected values below are hand-computed for
 * `cellSizePx = 10, columnGapPx = 2, rowGapPx = 3` (x stride 12, y stride 13).
 */
@RunWith(AndroidJUnit4::class)
class SpanContainerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val geometry = SixCellGeometry(cellSizePx = 10, columnGapPx = 2, rowGapPx = 3)

    @Test
    fun singleOneByOneAtOrigin_isMeasuredCellSizeAndPlacedAtOrigin() {
        val frames = recordFrames(geometry, listOf(slot("a", 0, 0, 1, 1)))

        assertEquals(Offset(0f, 0f), frames.getValue("a").position)
        assertEquals(IntSize(10, 10), frames.getValue("a").size)
    }

    @Test
    fun childrenArePlacedAtLogicalGridPositions() {
        val frames = recordFrames(
            geometry,
            listOf(
                slot("a", 2, 1, 1, 1),
                slot("b", 0, 2, 2, 1),
            ),
        )

        // a: left = 2 * (10 + 2) = 24, top = 1 * (10 + 3) = 13.
        assertEquals(Offset(24f, 13f), frames.getValue("a").position)
        assertEquals(IntSize(10, 10), frames.getValue("a").size)
        // b: left = 0, top = 2 * (10 + 3) = 26; width = 2 * 10 + 2 = 22.
        assertEquals(Offset(0f, 26f), frames.getValue("b").position)
        assertEquals(IntSize(22, 10), frames.getValue("b").size)
    }

    @Test
    fun spannedChildExtentIncludesOnlyInternalGaps() {
        val frames = recordFrames(geometry, listOf(slot("wide", 0, 0, 2, 2)))

        // Width = 2 * 10 + 1 * 2 = 22; height = 2 * 10 + 1 * 3 = 23.
        assertEquals(IntSize(22, 23), frames.getValue("wide").size)
        assertEquals(Offset(0f, 0f), frames.getValue("wide").position)
    }

    @Test
    fun containerWrapsMaximumChildEdges() {
        var containerSize: IntSize? = null
        composeRule.setContent {
            SpanContainer(
                modifier = Modifier.onGloballyPositioned { containerSize = it.size },
                children = listOf(
                    childOf(slot("a", 2, 1, 1, 1)),
                    childOf(slot("b", 0, 2, 2, 1)),
                ),
                geometry = geometry,
            )
        }
        composeRule.waitForIdle()

        // Right: max(24 + 10, 0 + 22) = 34. Bottom: max(13 + 10, 26 + 10) = 36.
        composeRule.runOnIdle { assertEquals(IntSize(34, 36), containerSize) }
    }

    @Test
    fun originOffsetsEveryChild() {
        val frames = mutableMapOf<String, Frame>()
        composeRule.setContent {
            SpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("a", GridPosition(1, 1), GridSpan(1, 1))) {
                        Box(Modifier.onGloballyPositioned { frames["a"] = Frame(it) })
                    },
                ),
                geometry = geometry,
                originX = 5,
                originY = 4,
            )
        }
        composeRule.waitForIdle()

        // Left = 1 * 12 + 5 = 17, top = 1 * 13 + 4 = 17.
        composeRule.runOnIdle {
            assertEquals(Offset(17f, 17f), frames.getValue("a").position)
            assertEquals(IntSize(10, 10), frames.getValue("a").size)
        }
    }

    @Test
    fun overlappingPlacementsRenderWithoutLegalityCheck() {
        val frames = recordFrames(
            geometry,
            listOf(
                slot("a", 0, 0, 1, 1),
                slot("b", 0, 0, 1, 1),
            ),
        )

        // No collision check: both children are placed at the same origin slot.
        assertEquals(Offset(0f, 0f), frames.getValue("a").position)
        assertEquals(Offset(0f, 0f), frames.getValue("b").position)
        assertTrue(frames.getValue("a").size == IntSize(10, 10))
    }

    @Test
    fun emptyChildrenOccupyZeroSizeWhenUnconstrained() {
        var containerSize: IntSize? = null
        composeRule.setContent {
            // fillMaxSize is intentionally absent: the container must wrap, not fill.
            SpanContainer<String>(
                modifier = Modifier.onGloballyPositioned { containerSize = it.size },
                children = emptyList(),
                geometry = geometry,
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(IntSize(0, 0), containerSize) }
    }

    @Test
    fun negativeOriginXIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                SpanContainer(
                    children = listOf(childOf(slot("a", 0, 0, 1, 1))),
                    geometry = geometry,
                    originX = -1,
                )
            }
        }
    }

    @Test
    fun negativeOriginYIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                SpanContainer(
                    children = listOf(childOf(slot("a", 0, 0, 1, 1))),
                    geometry = geometry,
                    originY = -1,
                )
            }
        }
    }

    @Test
    fun overflowingPlacementIsRejected() {
        val largeCell = SixCellGeometry(cellSizePx = Int.MAX_VALUE, columnGapPx = 0, rowGapPx = 0)

        // SpanContainer calls TileBounds during Compose measurement, where the exception is
        // delivered asynchronously on the UI thread and cannot be caught by assertThrows.
        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.bounds(
                position = GridPosition(1, 0),
                span = GridSpan(1, 1),
                geometry = largeCell,
            )
        }
    }

    @Test
    fun callerSuppliedContentDescriptionPassesThrough() {
        composeRule.setContent {
            SpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("a", GridPosition(0, 0), GridSpan(1, 1))) {
                        Box(Modifier.semantics { contentDescription = "Tile A" })
                    },
                    SpanChild(GridPlacement("b", GridPosition(1, 0), GridSpan(1, 1))) {
                        Box(Modifier.semantics { contentDescription = "Tile B" })
                    },
                ),
                geometry = geometry,
            )
        }

        // The geometry container must not strip or rename caller-supplied labels.
        composeRule.onNodeWithContentDescription("Tile A").assertExists()
        composeRule.onNodeWithContentDescription("Tile B").assertExists()
    }

    @Test
    fun containerInventsNoClickAction() {
        composeRule.setContent {
            SpanContainer(
                children = listOf(
                    SpanChild(GridPlacement("a", GridPosition(0, 0), GridSpan(1, 1))) {
                        Box(Modifier.semantics { contentDescription = "Tile A" })
                    },
                ),
                geometry = geometry,
            )
        }

        // SpanContainer is a geometry primitive: plain caller content must not gain
        // an invented click action from the container itself.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private data class Frame(
        val position: Offset,
        val size: IntSize,
    ) {
        constructor(coordinates: androidx.compose.ui.layout.LayoutCoordinates) : this(
            position = coordinates.positionInRoot(),
            size = coordinates.size,
        )
    }

    private data class SlotSpec(
        val id: String,
        val column: Int,
        val row: Int,
        val columns: Int,
        val rows: Int,
    )

    private fun slot(id: String, column: Int, row: Int, columns: Int, rows: Int): SlotSpec =
        SlotSpec(id, column, row, columns, rows)

    private fun childOf(spec: SlotSpec): SpanChild<String> = SpanChild(
        GridPlacement(
            spec.id,
            GridPosition(spec.column, spec.row),
            GridSpan(spec.columns, spec.rows),
        ),
    ) {
        Box(Modifier)
    }

    private fun recordFrames(
        geometry: SixCellGeometry,
        slots: List<SlotSpec>,
    ): Map<String, Frame> {
        val frames = mutableMapOf<String, Frame>()
        composeRule.setContent {
            SpanContainer(
                children = slots.map { spec ->
                    SpanChild(
                        GridPlacement(
                            spec.id,
                            GridPosition(spec.column, spec.row),
                            GridSpan(spec.columns, spec.rows),
                        ),
                    ) {
                        Box(
                            Modifier.onGloballyPositioned { frames[spec.id] = Frame(it) },
                        )
                    }
                },
                geometry = geometry,
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(slots.size, frames.size) }
        return frames
    }
}
