package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolAlignmentTest {

    @Test
    fun oneByOneIsBottomEnd() {
        assertEquals(Alignment.BottomEnd, symbolAlignment(GridSpan(1, 1)))
        assertEquals(Alignment.BottomEnd, symbolAlignment(GridSpan(1, 1), hasPinned = true))
    }

    @Test
    fun tallOneByTwoIsBottomCenter() {
        assertEquals(Alignment.BottomCenter, symbolAlignment(GridSpan(1, 2)))
    }

    @Test
    fun oneByThreeIsBottomCenter() {
        assertEquals(Alignment.BottomCenter, symbolAlignment(GridSpan(1, 3)))
    }

    @Test
    fun allOtherSpansAreBottomEnd() {
        val spans = listOf(
            GridSpan(1, 1),
            GridSpan(2, 1),
            GridSpan(2, 2),
            GridSpan(4, 2),
            GridSpan(3, 2),
            GridSpan(4, 3),
            GridSpan(3, 3),
        )
        for (span in spans) {
            assertEquals("span=$span", Alignment.BottomEnd, symbolAlignment(span))
        }
    }

    @Test
    fun singleArgDelegatesToPinnedAnchor() {
        val spans = listOf(
            GridSpan(1, 1),
            GridSpan(1, 2),
            GridSpan(1, 3),
            GridSpan(2, 1),
            GridSpan(2, 2),
            GridSpan(4, 2),
        )
        for (span in spans) {
            assertEquals(
                "span=$span",
                symbolAlignment(span, hasPinned = true),
                symbolAlignment(span),
            )
        }
    }

    @Test
    fun noPinnedShortcutsIsAlwaysCentered() {
        val spans = listOf(
            GridSpan(1, 1),
            GridSpan(1, 2),
            GridSpan(1, 3),
            GridSpan(2, 1),
            GridSpan(2, 2),
            GridSpan(4, 2),
        )
        for (span in spans) {
            assertEquals("span=$span", Alignment.Center, symbolAlignment(span, hasPinned = false))
        }
    }

    @Test
    fun pinnedNarrowSpansAreBottomCenterOthersBottomEnd() {
        assertEquals(Alignment.BottomCenter, symbolAlignment(GridSpan(1, 2), hasPinned = true))
        assertEquals(Alignment.BottomCenter, symbolAlignment(GridSpan(1, 3), hasPinned = true))
        assertEquals(Alignment.BottomEnd, symbolAlignment(GridSpan(1, 1), hasPinned = true))
        assertEquals(Alignment.BottomEnd, symbolAlignment(GridSpan(2, 2), hasPinned = true))
    }

    @Test
    fun centeredIconExtentIsMinOfAvailableSides() {
        assertEquals(100.dp, centeredIconExtent(100.dp, 200.dp))
        assertEquals(100.dp, centeredIconExtent(200.dp, 100.dp))
        assertEquals(120.dp, centeredIconExtent(120.dp, 120.dp))
    }
}
