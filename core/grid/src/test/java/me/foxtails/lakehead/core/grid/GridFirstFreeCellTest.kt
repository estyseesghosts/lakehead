package me.foxtails.lakehead.core.grid

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridFirstFreeCellTest {

    @Test
    fun emptyGridReturnsOrigin() {
        val result = firstFreeCell(occupied = emptyList(), columns = 6, maxRow = 3)
        assertEquals(LogicalPosition(0, 0), result)
    }

    @Test
    fun singleOccupiedAtOriginReturnsNextColumn() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
        )
        val result = firstFreeCell(occupied, columns = 6, maxRow = 3)
        assertEquals(LogicalPosition(1, 0), result)
    }

    @Test
    fun singleColumnGridOccupiedAtOriginReturnsNextRow() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
        )
        val result = firstFreeCell(occupied, columns = 1, maxRow = 3)
        assertEquals(LogicalPosition(0, 1), result)
    }

    @Test
    fun holeInTheMiddleIsFilled() {
        // Columns 0 and 1 of row 0 occupied; column 2 is free.
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
            PlacementRect(LogicalPosition(1, 0), LogicalSpan(1, 1)),
            PlacementRect(LogicalPosition(3, 0), LogicalSpan(1, 1)),
        )
        val result = firstFreeCell(occupied, columns = 6, maxRow = 3)
        assertEquals(LogicalPosition(2, 0), result)
    }

    @Test
    fun fullFirstRowMovesToNextRow() {
        // All 4 columns of row 0 occupied.
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(4, 1)),
        )
        val result = firstFreeCell(occupied, columns = 4, maxRow = 3)
        assertEquals(LogicalPosition(0, 1), result)
    }

    @Test
    fun twoByTwoSpanSkipsEntireArea() {
        // 2×2 at (0,0) covers (0,0),(1,0),(0,1),(1,1).
        // Next free cell should be (2,0).
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(2, 2)),
        )
        val result = firstFreeCell(occupied, columns = 6, maxRow = 3)
        assertEquals(LogicalPosition(2, 0), result)
    }

    @Test
    fun fourByTwoSpanSkipsCorrectly() {
        // 4×2 at (0,0) covers columns 0-3, rows 0-1.
        // Next free cell should be (4,0).
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(4, 2)),
        )
        val result = firstFreeCell(occupied, columns = 6, maxRow = 3)
        assertEquals(LogicalPosition(4, 0), result)
    }

    @Test
    fun fullGridReturnsNull() {
        // 6 columns × 4 rows fully covered by six 1×4 vertical spans.
        val occupied = (0 until 6).map { col ->
            PlacementRect(LogicalPosition(col, 0), LogicalSpan(1, 4))
        }
        val result = firstFreeCell(occupied, columns = 6, maxRow = 3)
        assertNull(result)
    }

    @Test
    fun multipleOverlappingPlacementsCorrectFirstFree() {
        // Row 0: (0,0) 1×1, (2,0) 2×1. Gap at col 1.
        // Row 1: (0,1) 2×2 covers (0,1),(1,1),(0,2),(1,2).
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
            PlacementRect(LogicalPosition(2, 0), LogicalSpan(2, 1)),
            PlacementRect(LogicalPosition(0, 1), LogicalSpan(2, 2)),
        )
        val result = firstFreeCell(occupied, columns = 6, maxRow = 3)
        // (1,0) is free — not covered by (0,0) 1×1 or (2,0) 2×1 or (0,1) 2×2.
        assertEquals(LogicalPosition(1, 0), result)
    }

    @Test
    fun singleCellMaxRowZero() {
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(1, 1)),
        )
        // 1 column, maxRow=0, only cell (0,0) exists and is occupied.
        val result = firstFreeCell(occupied, columns = 1, maxRow = 0)
        assertNull(result)
    }

    @Test
    fun edgeTouchDoesNotBlock() {
        // 2×2 at (0,0). Another rect touching at (2,0) does not overlap.
        val occupied = listOf(
            PlacementRect(LogicalPosition(0, 0), LogicalSpan(2, 2)),
            PlacementRect(LogicalPosition(2, 0), LogicalSpan(2, 1)),
        )
        // (0,0)-(1,1) and (2,0)-(3,0) occupied. First free is (4,0).
        val result = firstFreeCell(occupied, columns = 6, maxRow = 3)
        assertEquals(LogicalPosition(4, 0), result)
    }

    @Test
    fun invalidColumnsThrows() {
        try {
            firstFreeCell(emptyList(), columns = 0, maxRow = 3)
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun invalidMaxRowThrows() {
        try {
            firstFreeCell(emptyList(), columns = 6, maxRow = -1)
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }
}