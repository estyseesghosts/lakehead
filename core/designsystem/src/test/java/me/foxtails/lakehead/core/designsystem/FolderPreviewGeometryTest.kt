package me.foxtails.lakehead.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPreviewGeometryTest {
    @Test
    fun fourSquareCellsReadRowMajor() {
        val cells = FolderPreviewGeometry.cells(extent = 80f, padding = 4f, gap = 4f)
        assertEquals(4, cells.size)
        assertEquals(34f, cells[0].size, 0f)
        assertEquals(listOf(4f, 42f, 4f, 42f), cells.map { it.left })
        assertEquals(listOf(4f, 4f, 42f, 42f), cells.map { it.top })
        assertTrue(cells.all { it.size == cells[0].size })
    }

    @Test
    fun cellsStayInsideThePaddedTile() {
        val extent = 90f
        val cells = FolderPreviewGeometry.cells(extent, padding = 6f, gap = 3f)
        cells.forEach {
            assertTrue(it.left >= 6f && it.top >= 6f)
            assertTrue(it.left + it.size <= extent - 6f + 0.001f)
            assertTrue(it.top + it.size <= extent - 6f + 0.001f)
        }
        val gapX = cells[1].left - (cells[0].left + cells[0].size)
        assertEquals(3f, gapX, 0.001f)
    }

    @Test
    fun aTileSmallerThanItsInsetsCollapsesToZeroSizedCells() {
        assertEquals(0f, FolderPreviewGeometry.cellSize(extent = 6f, padding = 4f, gap = 4f), 0f)
    }
}
