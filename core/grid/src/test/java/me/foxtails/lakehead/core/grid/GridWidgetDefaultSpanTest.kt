package me.foxtails.lakehead.core.grid

import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class GridWidgetDefaultSpanTest {

    // --- Target cells used as-is ---

    @Test
    fun targetCellsUsedDirectly() {
        assertEquals(
            LogicalSpan(4, 2),
            widgetDefaultSpan(targetCellWidth = 4, targetCellHeight = 2, minWidthDp = 0, minHeightDp = 0, columns = 5),
        )
    }

    @Test
    fun targetCellsUsedDirectlySmallSpan() {
        assertEquals(
            LogicalSpan(2, 1),
            widgetDefaultSpan(targetCellWidth = 2, targetCellHeight = 1, minWidthDp = 100, minHeightDp = 100, columns = 6),
        )
    }

    // --- Column clamping ---

    @Test
    fun targetWidthExceedingColumnsClampsToMax() {
        // 6-wide target on a 5-column grid → clamped to 5
        assertEquals(
            LogicalSpan(5, 2),
            widgetDefaultSpan(targetCellWidth = 6, targetCellHeight = 2, minWidthDp = 0, minHeightDp = 0, columns = 5),
        )
    }

    @Test
    fun fallbackWidthClampsToColumns() {
        // minWidthDp = 1000 → ceil((1000+30)/70.0) = ceil(14.71) = 15, clamped to 6
        assertEquals(
            LogicalSpan(6, 1),
            widgetDefaultSpan(targetCellWidth = 0, targetCellHeight = 0, minWidthDp = 1000, minHeightDp = 0, columns = 6),
        )
    }

    // --- Fallback boundaries per axis ---

    @Test
    fun fallbackMinDp40GivesOne() {
        // ceil((40+30)/70.0) = ceil(1.0) = 1
        assertEquals(
            LogicalSpan(1, 1),
            widgetDefaultSpan(targetCellWidth = 0, targetCellHeight = 0, minWidthDp = 40, minHeightDp = 40, columns = 6),
        )
    }

    @Test
    fun fallbackMinDp41GivesTwo() {
        // ceil((41+30)/70.0) = ceil(1.014) = 2
        assertEquals(
            LogicalSpan(2, 2),
            widgetDefaultSpan(targetCellWidth = 0, targetCellHeight = 0, minWidthDp = 41, minHeightDp = 41, columns = 6),
        )
    }

    @Test
    fun fallbackMinDp110GivesTwo() {
        // ceil((110+30)/70.0) = ceil(2.0) = 2
        assertEquals(
            LogicalSpan(2, 2),
            widgetDefaultSpan(targetCellWidth = 0, targetCellHeight = 0, minWidthDp = 110, minHeightDp = 110, columns = 6),
        )
    }

    @Test
    fun fallbackMinDp111GivesThree() {
        // ceil((111+30)/70.0) = ceil(2.014) = 3
        assertEquals(
            LogicalSpan(3, 3),
            widgetDefaultSpan(targetCellWidth = 0, targetCellHeight = 0, minWidthDp = 111, minHeightDp = 111, columns = 6),
        )
    }

    // --- Zero dp gives minimum 1 ---

    @Test
    fun zeroDpGivesOne() {
        // ceil((0+30)/70.0) = ceil(0.428) = 1
        assertEquals(
            LogicalSpan(1, 1),
            widgetDefaultSpan(targetCellWidth = 0, targetCellHeight = 0, minWidthDp = 0, minHeightDp = 0, columns = 6),
        )
    }

    // --- Only one target field set falls back to formula ---

    @Test
    fun onlyWidthSetFallsBackToFormula() {
        // targetCellWidth > 0 but targetCellHeight = 0 → formula for both axes
        assertEquals(
            LogicalSpan(2, 3),
            widgetDefaultSpan(targetCellWidth = 5, targetCellHeight = 0, minWidthDp = 41, minHeightDp = 111, columns = 6),
        )
    }

    @Test
    fun onlyHeightSetFallsBackToFormula() {
        // targetCellHeight > 0 but targetCellWidth = 0 → formula for both axes
        assertEquals(
            LogicalSpan(2, 3),
            widgetDefaultSpan(targetCellWidth = 0, targetCellHeight = 5, minWidthDp = 41, minHeightDp = 111, columns = 6),
        )
    }

    // --- require(columns > 0) ---

    @Test(expected = IllegalArgumentException::class)
    fun zeroColumnsThrows() {
        widgetDefaultSpan(targetCellWidth = 2, targetCellHeight = 2, minWidthDp = 0, minHeightDp = 0, columns = 0)
    }
}