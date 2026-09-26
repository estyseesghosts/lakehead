package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridContentBoundsTest {

    // ── 1. negative rows min/max ────────────────────────────────────────────

    @Test
    fun negativeRowsMinMaxAreCorrect() {
        val placements = listOf(
            signedPlacement("above", column = 0, row = -10, cols = 1, rows = 2),
            signedPlacement("below", column = 1, row = 5, cols = 1, rows = 3),
        )

        val bounds = GridContentBounds.contentBoundsOf(placements, columns = 5)!!

        assertEquals(-10L, bounds.minRow)
        // above ends at -10 + 2 = -8; below ends at 5 + 3 = 8
        assertEquals(8L, bounds.maxRowExclusive)
        assertEquals(0L, bounds.minCol)
        // below occupies column 1..2 (exclusive)
        assertEquals(2L, bounds.maxColExclusive)
    }

    // ── 2. min/max from coordinates not count ───────────────────────────────

    @Test
    fun minMaxDeriveFromCoordinatesNotPlacementCount() {
        // Three placements: one at row 0, one at row 100, one at row 50.
        // Bounds should be [0, 101), not [0, 3).
        val placements = listOf(
            signedPlacement("a", column = 0, row = 0),
            signedPlacement("b", column = 0, row = 100),
            signedPlacement("c", column = 0, row = 50),
        )

        val bounds = GridContentBounds.contentBoundsOf(placements, columns = 5)!!

        assertEquals(0L, bounds.minRow)
        assertEquals(101L, bounds.maxRowExclusive)
        assertEquals(101L, GridContentBounds.contentHeightRows(bounds))
    }

    // ── 3. sparse gap included in bounding box ──────────────────────────────

    @Test
    fun sparseGapIsIncludedInBounds() {
        // Row 2 and row 10 — gap of 7 rows is swallowed into the bounding box.
        val placements = listOf(
            signedPlacement("early", column = 0, row = 2, cols = 1, rows = 1),
            signedPlacement("late", column = 0, row = 10, cols = 1, rows = 1),
        )

        val bounds = GridContentBounds.contentBoundsOf(placements, columns = 5)!!

        assertEquals(2L, bounds.minRow)
        assertEquals(11L, bounds.maxRowExclusive)
        // Height spans the entire gap: 11 - 2 = 9 rows
        assertEquals(9L, GridContentBounds.contentHeightRows(bounds))
    }

    // ── 4. empty → null + 0; single 1×1 ─────────────────────────────────────

    @Test
    fun emptyPlacementsReturnsNullAndHeightZero() {
        val empty = emptyList<SignedGridPlacement<String>>()

        assertNull(GridContentBounds.contentBoundsOf(empty, columns = 5))
        assertEquals(0L, GridContentBounds.contentHeightRows(empty, columns = 5))
    }

    @Test
    fun singleOneByOnePlacementBoundsAndHeight() {
        val placements = listOf(
            signedPlacement("only", column = 3, row = 7),
        )

        val bounds = GridContentBounds.contentBoundsOf(placements, columns = 5)!!

        assertEquals(7L, bounds.minRow)
        assertEquals(8L, bounds.maxRowExclusive)
        assertEquals(3L, bounds.minCol)
        assertEquals(4L, bounds.maxColExclusive)
        assertEquals(1L, GridContentBounds.contentHeightRows(bounds))
    }

    // ── 5. overlap vs edge-touch via hasOverlap ─────────────────────────────

    @Test
    fun overlapVsEdgeTouch() {
        // Strict overlap — share interior cells
        assertTrue(
            GridContentBounds.hasOverlap(
                aMinRow = 0, aMaxRowExclusive = 3, aMinCol = 0, aMaxColExclusive = 3,
                bMinRow = 1, bMaxRowExclusive = 4, bMinCol = 1, bMaxColExclusive = 4,
            )
        )

        // Edge-touch only (right edge of A meets left edge of B) — no overlap
        assertFalse(
            GridContentBounds.hasOverlap(
                aMinRow = 0, aMaxRowExclusive = 3, aMinCol = 0, aMaxColExclusive = 2,
                bMinRow = 0, bMaxRowExclusive = 3, bMinCol = 2, bMaxColExclusive = 4,
            )
        )

        // Edge-touch only (bottom edge of A meets top edge of B) — no overlap
        assertFalse(
            GridContentBounds.hasOverlap(
                aMinRow = 0, aMaxRowExclusive = 2, aMinCol = 0, aMaxColExclusive = 3,
                bMinRow = 2, bMaxRowExclusive = 5, bMinCol = 0, bMaxColExclusive = 3,
            )
        )

        // Corner-touch only — no overlap
        assertFalse(
            GridContentBounds.hasOverlap(
                aMinRow = 0, aMaxRowExclusive = 2, aMinCol = 0, aMaxColExclusive = 2,
                bMinRow = 2, bMaxRowExclusive = 4, bMinCol = 2, bMaxColExclusive = 4,
            )
        )

        // Fully contained — overlap
        assertTrue(
            GridContentBounds.hasOverlap(
                aMinRow = 0, aMaxRowExclusive = 10, aMinCol = 0, aMaxColExclusive = 10,
                bMinRow = 2, bMaxRowExclusive = 4, bMinCol = 2, bMaxColExclusive = 4,
            )
        )
    }

    // ── 6. 4-wide-in-5 legal + 5-wide-at-col1 throws ───────────────────────

    @Test
    fun fourWideInFiveColumnsIsLegal() {
        val placements = listOf(
            signedPlacement("wide", column = 0, row = 0, cols = 4, rows = 1),
        )

        // Should not throw; 4 cells starting at column 0 fit in 5 columns
        val bounds = GridContentBounds.contentBoundsOf(placements, columns = 5)!!
        assertEquals(0L, bounds.minCol)
        assertEquals(4L, bounds.maxColExclusive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fiveWideAtColumnOneInFiveColumnsThrows() {
        val placements = listOf(
            signedPlacement("toowide", column = 1, row = 0, cols = 5, rows = 1),
        )

        // Column 1 + span 5 = column 6, which exceeds columns=5
        GridContentBounds.contentBoundsOf(placements, columns = 5)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun <T : Any> signedPlacement(
        id: T,
        column: Int,
        row: Int,
        cols: Int = 1,
        rows: Int = 1,
    ) = SignedGridPlacement(
        id = id,
        column = column,
        row = row,
        span = GridSpan(columns = cols, rows = rows),
    )
}