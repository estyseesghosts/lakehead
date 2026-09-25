package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.Alignment
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolAlignmentTest {

    @Test
    fun tallOneByTwoIsBottomCenter() {
        assertEquals(Alignment.BottomCenter, symbolAlignment(GridSpan(1, 2)))
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
}
