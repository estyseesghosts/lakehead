package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * G3 signed renderer mapping for [TileBounds.signedBounds].
 *
 * Geometry is a 5-column [SquareCellGeometry] with `cellSizePx = 100`,
 * `columnGapPx = 8`, `rowGapPx = 8` (strides 108). The signed origin is
 * `originRow = -2` (content `minRow`) with `originYPx = 0`, covering rows
 * -2/-1/0 across the home span set 1×1, 2×1, 1×2, 2×2, 4×2.
 */
class TileBoundsSignedTest {

    private val geometry = SquareCellGeometry(
        columnCount = 5,
        cellSizePx = 100,
        columnGapPx = 8,
        rowGapPx = 8,
    )

    @Test
    fun rowsAboveOrigin_rebaseAroundSignedMinRow() {
        // Stride 108: row -2 → local 0 → top 0; row -1 → 108; row 0 → 216.
        assertEquals(
            0,
            TileBounds.signedBounds(0, -2L, GridSpan(1, 1), geometry, originRow = -2L).top,
        )
        assertEquals(
            108,
            TileBounds.signedBounds(0, -1L, GridSpan(1, 1), geometry, originRow = -2L).top,
        )
        assertEquals(
            216,
            TileBounds.signedBounds(0, 0L, GridSpan(1, 1), geometry, originRow = -2L).top,
        )
    }

    @Test
    fun allHomeSpans_measureFromSignedOrigin() {
        val originRow = -2L
        val cases = listOf(
            GridSpan(1, 1) to (100 to 100),
            GridSpan(2, 1) to (208 to 100),
            GridSpan(1, 2) to (100 to 208),
            GridSpan(2, 2) to (208 to 208),
            GridSpan(4, 2) to (424 to 208),
        )
        for (row in listOf(-2L, -1L, 0L)) {
            val expectedTop = ((row - originRow) * 108L).toInt()
            for ((span, extent) in cases) {
                val frame = TileBounds.signedBounds(
                    column = 0,
                    row = row,
                    span = span,
                    geometry = geometry,
                    originRow = originRow,
                )
                assertEquals("left row=$row span=$span", 0, frame.left)
                assertEquals("top row=$row span=$span", expectedTop, frame.top)
                assertEquals("width row=$row span=$span", extent.first, frame.widthPx)
                assertEquals("height row=$row span=$span", extent.second, frame.heightPx)
                assertEquals("right row=$row span=$span", extent.first, frame.right)
                assertEquals("bottom row=$row span=$span", expectedTop + extent.second, frame.bottom)
            }
        }
    }

    @Test
    fun columnOffset_usesColumnStrideWithOriginX() {
        // Column 1: left = 108; 4×2 width 424 → right 532.
        val frame = TileBounds.signedBounds(
            column = 1,
            row = -2L,
            span = GridSpan(4, 2),
            geometry = geometry,
            originRow = -2L,
        )
        assertEquals(108, frame.left)
        assertEquals(532, frame.right)
        assertEquals(0, frame.top)
        assertEquals(208, frame.bottom)
    }

    @Test
    fun originYPx_shiftsTopByRestOffset() {
        // G2 rest offset (e.g. short-content bias 540) applies on top of rebasing.
        val frame = TileBounds.signedBounds(
            column = 0,
            row = -2L,
            span = GridSpan(1, 1),
            geometry = geometry,
            originRow = -2L,
            originYPx = 540L,
        )
        assertEquals(540, frame.top)
        assertEquals(640, frame.bottom)
    }

    @Test
    fun rowAboveSignedOrigin_keepsNegativeTop() {
        // Row -3 with origin -2 → local -1 → top -108; bottom -8 stays negative.
        val frame = TileBounds.signedBounds(
            column = 0,
            row = -3L,
            span = GridSpan(1, 1),
            geometry = geometry,
            originRow = -2L,
        )
        assertEquals(-108, frame.top)
        assertEquals(-8, frame.bottom)
        assertEquals(0, frame.left)
    }

    @Test
    fun negativeColumnAndOriginX_areRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.signedBounds(-1, 0L, GridSpan(1, 1), geometry)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.signedBounds(0, 0L, GridSpan(1, 1), geometry, originX = -1)
        }
    }

    @Test
    fun unrepresentableTop_failsExplicitly() {
        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.signedBounds(
                column = 0,
                row = Long.MAX_VALUE,
                span = GridSpan(1, 1),
                geometry = geometry,
                originRow = 0L,
            )
        }
    }
}
