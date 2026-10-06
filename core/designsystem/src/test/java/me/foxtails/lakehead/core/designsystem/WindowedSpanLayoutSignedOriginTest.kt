package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * G3 signed-origin coverage for [WindowedSpanLayout].
 *
 * [GridPlacement] rows stay `Int`-bounded and non-negative; what is signed here is
 * the rebasing [rowOrigin] (a signed content `minRow`, may be negative). Geometry
 * uses `cellSizePx = 10, columnGapPx = 2, rowGapPx = 3` (x stride 12, y stride 13).
 */
class WindowedSpanLayoutSignedOriginTest {

    private val geometry = SquareCellGeometry(
        columnCount = 5,
        cellSizePx = 10,
        columnGapPx = 2,
        rowGapPx = 3,
    )

    @Test
    fun negativeRowOrigin_isAcceptedAndRebasesPositiveRows() {
        val bounds = WindowedSpanLayout.boundsFor(
            children = listOf(
                SpanChild(GridPlacement("a", GridPosition(0, 0), GridSpan(1, 1))) {},
                SpanChild(GridPlacement("b", GridPosition(1, 1), GridSpan(1, 1))) {},
            ),
            window = GridRowWindow(-2L, 2L),
            overscanRows = 0L,
            geometry = geometry,
            rowOrigin = -2L,
        )
        assertEquals(-2L, bounds.origin)
        assertEquals(2, bounds.placed.size)
        // Row 0 → local 2 → top 26; row 1 → local 3 → top 39.
        assertEquals(26, bounds.placed[0].top)
        assertEquals(0, bounds.placed[0].left)
        assertEquals(39, bounds.placed[1].top)
        assertEquals(12, bounds.placed[1].left)
    }

    @Test
    fun resolveRowOrigin_returnsNegativeOverride() {
        assertEquals(
            -2L,
            WindowedSpanLayout.resolveRowOrigin(
                window = GridRowWindow(0L, 4L),
                overscanRows = 0L,
                rowOrigin = -2L,
            ),
        )
    }

    @Test
    fun spanCrossingSignedWindow_keepsNegativeTop() {
        // Child rows [0,2) against window [1,3) with origin 1: top = (0-1)*13 = -13.
        val bounds = WindowedSpanLayout.boundsFor(
            children = listOf(
                SpanChild(GridPlacement("cross", GridPosition(0, 0), GridSpan(1, 2))) {},
            ),
            window = GridRowWindow(1L, 3L),
            overscanRows = 0L,
            geometry = geometry,
            rowOrigin = 1L,
        )
        assertEquals(1, bounds.placed.size)
        assertEquals(-13, bounds.placed[0].top)
        assertEquals(10, bounds.placed[0].bottom)
    }

    @Test
    fun leftStaysNonNegative_underSignedOrigin() {
        val bounds = WindowedSpanLayout.boundsFor(
            children = listOf(
                SpanChild(GridPlacement("tile", GridPosition(2, 0), GridSpan(2, 1))) {},
            ),
            window = GridRowWindow(-2L, 2L),
            overscanRows = 0L,
            geometry = geometry,
            rowOrigin = -2L,
        )
        assertEquals(1, bounds.placed.size)
        // Column 2 → left 24; row 0 → local 2 → top 26.
        assertEquals(24, bounds.placed[0].left)
        assertEquals(26, bounds.placed[0].top)
        assertEquals(22, bounds.placed[0].widthPx)
    }

    @Test
    fun callerOrderPreserved_withSignedOrigin() {
        val bounds = WindowedSpanLayout.boundsFor(
            children = listOf("c", "a", "b").map { id ->
                val row = when (id) {
                    "c" -> 2
                    "a" -> 0
                    else -> 1
                }
                SpanChild(GridPlacement(id, GridPosition(0, row), GridSpan(1, 1))) {}
            },
            window = GridRowWindow(-2L, 4L),
            overscanRows = 0L,
            geometry = geometry,
            rowOrigin = -2L,
        )
        assertEquals(listOf("c", "a", "b"), bounds.placed.map { it.child.placement.id })
    }
}
