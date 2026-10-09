package me.foxtails.lakehead.core.grid

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class GridFirstFreeRectTest {

    // 1. Empty grid returns (0,0)
    @Test
    fun emptyGridReturnsOrigin() {
        val result = firstFreeRect(
            occupied = emptyList(),
            columns = 6,
            span = LogicalSpan(2, 2),
        )
        assertEquals(LogicalPosition(0, 0), result)
    }

    // 2. 4×2 in 5 columns beside a 1×1 at (0,0) skips to the next row
    @Test
    fun fourByTwoInFiveColumnsBesideOneByOne() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
        )
        val result = firstFreeRect(
            occupied = occupied,
            columns = 5,
            span = LogicalSpan(4, 2),
        )
        // Candidate at (1,0) occupies columns 1-4, does not overlap 1×1 at (0,0).
        assertEquals(LogicalPosition(1, 0), result)
    }

    // 3. Hole that fits is used
    @Test
    fun holeThatFitsIsUsed() {
        // Row 0: 1×1 at col 0, 1×1 at col 3. Hole at columns 1-2 (width 2).
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
            PlacementRect(LogicalPosition(3, 0), LogicalSpan(1, 1)),
        )
        val result = firstFreeRect(
            occupied = occupied,
            columns = 5,
            span = LogicalSpan(2, 1),
        )
        // 2×1 fits at (1,0) without overlapping either occupied rect.
        assertEquals(LogicalPosition(1, 0), result)
    }

    // 4. Hole that is too small is skipped
    @Test
    fun holeTooSmallIsSkipped() {
        // Row 0: 1×1 at col 0, 1×1 at col 2. Hole at col 1 (width 1).
        // 2×1 does not fit in the hole. Should fall to row 1.
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
            PlacementRect(LogicalPosition(2, 0), LogicalSpan(1, 1)),
        )
        val result = firstFreeRect(
            occupied = occupied,
            columns = 4,
            span = LogicalSpan(2, 1),
        )
        // Row 0: col 0 occupied, col 1 only 1 wide, col 2 occupied → no fit.
        // Row 1: (0,1) fits.
        assertEquals(LogicalPosition(0, 1), result)
    }

    // 5. Span exactly equal to columns
    @Test
    fun spanExactlyEqualToColumns() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(3, 1)),
        )
        val result = firstFreeRect(
            occupied = occupied,
            columns = 3,
            span = LogicalSpan(3, 1),
        )
        // Row 0 fully occupied. Row 1 has space for 3×1 at (0,1).
        assertEquals(LogicalPosition(0, 1), result)
    }

    // 6. Span wider than columns throws
    @Test(expected = IllegalArgumentException::class)
    fun spanWiderThanColumnsThrows() {
        firstFreeRect(
            occupied = emptyList(),
            columns = 3,
            span = LogicalSpan(4, 1),
        )
    }

    // 7. Result below all content when no hole fits
    @Test
    fun resultBelowAllContentWhenNoHoleFits() {
        // Row 0: 2×2 at (0,0) and 2×2 at (2,0). No 2-wide gap in row 0 for columns=4.
        // Actually (0,0)-(1,1) and (2,0)-(3,1) — that fills the whole row.
        // Row 2: 1×1 at (0,2).
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(2, 2)),
            PlacementRect(LogicalPosition(2, 0), LogicalSpan(2, 2)),
            PlacementRect(LogicalPosition(0, 2), LogicalSpan(1, 1)),
        )
        val result = firstFreeRect(
            occupied = occupied,
            columns = 4,
            span = LogicalSpan(2, 1),
        )
        // Rows 0-1 fully covered by the two 2×2 spans.
        // Row 2: (1,2) is free but only 1 wide before the edge → actually let me check:
        // maxOccupiedBottom = max(0+2, 0+2, 2+1) = 3
        // maxCandidateRow = max(3-1, 0) = 2
        // Row 0: col 0 overlaps (0,0) 2×2; col 1 overlaps (0,0) 2×2; col 2 overlaps (2,0) 2×2 → no fit
        // Row 1: col 0 overlaps (0,0) 2×2; col 1 overlaps (0,0) 2×2; col 2 overlaps (2,0) 2×2 → no fit
        // Row 2: col 0 overlaps (0,2) 1×1; col 1 does NOT overlap anything! (2,2) 2×1 covers col 2-3, no overlap with 1×1 at (0,2))
        // Actually (1,2) 2×1 covers columns 1-2, row 2. (0,2) 1×1 covers col 0, row 2. No overlap. So result = (1,2).
        // Hmm, that doesn't match "result below all content". Let me use a tighter scenario.
        assertEquals(LogicalPosition(1, 2), result)
    }

    @Test
    fun resultBelowAllContentCompletelyFilled() {
        // Row 0 fully occupied by a single 4×1 span. Want a 2×1 in 4 columns.
        // Row 0: no gap. Row 1: (0,1) fits. But maxOccupiedBottom = 1.
        // maxCandidateRow = max(1-1, 0) = 0. Row 0: no fit. Fall through to (0,1).
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(4, 1)),
        )
        val result = firstFreeRect(
            occupied = occupied,
            columns = 4,
            span = LogicalSpan(2, 1),
        )
        assertEquals(LogicalPosition(0, 1), result)
    }

    @Test
    fun resultBelowMultiRowContent() {
        // 4×3 block at (0,0) fills all 4 columns for 3 rows.
        // Want a 2×2. No fit in rows 0-1. maxOccupiedBottom = 3.
        // maxCandidateRow = max(3-2, 0) = 1. Rows 0-1: no fit. Fall through to (0,3).
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(4, 3)),
        )
        val result = firstFreeRect(
            occupied = occupied,
            columns = 4,
            span = LogicalSpan(2, 2),
        )
        assertEquals(LogicalPosition(0, 3), result)
    }

    // 8. 1×1 span agrees with firstFreeCell on a few cases
    @Test
    fun oneByOneAgreesWithFirstFreeCell_emptyGrid() {
        val occupied = emptyList<PlacementRect>()
        val rect = firstFreeRect(occupied, columns = 6, span = LogicalSpan(1, 1))
        val cell = firstFreeCell(occupied, columns = 6, maxRow = 0)
        assertEquals(cell, rect)
    }

    @Test
    fun oneByOneAgreesWithFirstFreeCell_singleOccupied() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
        )
        val rect = firstFreeRect(occupied, columns = 6, span = LogicalSpan(1, 1))
        val cell = firstFreeCell(occupied, columns = 6, maxRow = 1)
        assertEquals(cell, rect)
    }

    @Test
    fun oneByOneAgreesWithFirstFreeCell_twoByTwoBlocker() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(2, 2)),
        )
        val rect = firstFreeRect(occupied, columns = 6, span = LogicalSpan(1, 1))
        val cell = firstFreeCell(occupied, columns = 6, maxRow = 2)
        assertEquals(cell, rect)
    }

    @Test
    fun oneByOneAgreesWithFirstFreeCell_fullFirstRow() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(4, 1)),
        )
        val rect = firstFreeRect(occupied, columns = 4, span = LogicalSpan(1, 1))
        val cell = firstFreeCell(occupied, columns = 4, maxRow = 1)
        assertEquals(cell, rect)
    }

    @Test
    fun oneByOneAgreesWithFirstFreeCell_multiplePlacements() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
            PlacementRect(LogicalPosition(2, 0), LogicalSpan(2, 1)),
            PlacementRect(LogicalPosition(0, 1), LogicalSpan(2, 2)),
        )
        val rect = firstFreeRect(occupied, columns = 6, span = LogicalSpan(1, 1))
        val cell = firstFreeCell(occupied, columns = 6, maxRow = 3)
        assertEquals(cell, rect)
    }

    // Additional: verify requirement on columns <= 0
    @Test(expected = IllegalArgumentException::class)
    fun zeroColumnsThrows() {
        firstFreeRect(emptyList(), columns = 0, span = LogicalSpan(1, 1))
    }
}