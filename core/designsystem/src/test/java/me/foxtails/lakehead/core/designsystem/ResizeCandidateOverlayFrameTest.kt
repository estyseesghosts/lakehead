package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Signed-origin coverage for [resizeCandidateFrame].
 *
 * Geometry uses `cellSizePx = 100, columnGapPx = 8, rowGapPx = 8` (stride 108).
 * Short centered content carries a nonzero G2 rest offset (`originYPx = 540`);
 * the overlay must land on the same Box content coordinates as the windowed
 * tiles instead of ghosting off-origin.
 */
class ResizeCandidateOverlayFrameTest {

    private val geometry = SquareCellGeometry(
        columnCount = 5,
        cellSizePx = 100,
        columnGapPx = 8,
        rowGapPx = 8,
    )

    @Test
    fun nonzeroRest_shiftsTopByOriginYPx() {
        val withoutRest = resizeCandidateFrame(
            originPosition = GridPosition(0, 0),
            candidateSpan = GridSpan(2, 2),
            geometry = geometry,
            rowOrigin = 0L,
            originYPx = 0L,
        )
        val withRest = resizeCandidateFrame(
            originPosition = GridPosition(0, 0),
            candidateSpan = GridSpan(2, 2),
            geometry = geometry,
            rowOrigin = 0L,
            originYPx = 540L,
        )
        assertEquals(0, withoutRest.top)
        assertEquals(540, withRest.top)
        assertEquals(withoutRest.left, withRest.left)
        assertEquals(withoutRest.widthPx, withRest.widthPx)
        assertEquals(withoutRest.heightPx, withRest.heightPx)
        assertEquals(208, withRest.widthPx)
        assertEquals(208, withRest.heightPx)
        assertEquals(540 + 208, withRest.bottom)
    }

    @Test
    fun nonzeroRest_withRowOriginAndOriginX() {
        // Row 1 with origin 0 → local 1 → top 108, plus rest 540 → 648.
        // Column 1 → left 108 with centered originX 12 → 120.
        val frame = resizeCandidateFrame(
            originPosition = GridPosition(1, 1),
            candidateSpan = GridSpan(1, 1),
            geometry = geometry,
            originX = 12,
            rowOrigin = 0L,
            originYPx = 540L,
        )
        assertEquals(120, frame.left)
        assertEquals(648, frame.top)
        assertEquals(220, frame.right)
        assertEquals(748, frame.bottom)
    }
}
