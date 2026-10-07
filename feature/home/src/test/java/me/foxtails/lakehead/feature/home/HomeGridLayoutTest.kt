package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pure placement/height rules of [HomeGridLayout]; composition is covered on device. */
class HomeGridLayoutTest {
    private fun metrics(rest: Float = 0f) = HomeGridMetrics(
        columns = 6,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 8f,
        rowGapPx = 8f,
        originXPx = 4f,
        restOffsetYPx = rest,
    )

    private fun frame(m: HomeGridMetrics, column: Int, row: Int, cols: Int = 1, rows: Int = 1) =
        m.signedFrame(LogicalPosition(column, row), LogicalSpan(cols, rows))

    @Test
    fun tileFrame_dependsOnlyOnItsOwnRowAndColumn() {
        val m = metrics()
        val alone = frame(m, column = 2, row = 5)
        // The same tile among occupied rows above and below it.
        val among = listOf(frame(m, 0, 0), frame(m, 2, 5), frame(m, 1, 9, 2, 2)).single { it.left == alone.left && it.top == alone.top }
        assertEquals(alone, among)
        assertEquals(alone.top, (5 * m.rowStridePx).toInt())
        assertEquals(alone.left, (4 + 2 * m.columnStridePx).toInt())
    }

    @Test
    fun loneTile_atDistantRow_isNotRebased() {
        val m = metrics()
        listOf(16, 40, 1000).forEach { row ->
            val f = frame(m, 0, row)
            assertEquals("row $row", (row * m.rowStridePx).toInt(), f.top)
            assertEquals(100, f.heightPx)
        }
    }

    @Test
    fun contentHeight_isLowestBottom_notOccupiedRowCount() {
        val m = metrics()
        // A lone tile at row 16 still reserves its sixteen rows above it.
        val frames = listOf(frame(m, 0, 16))
        assertEquals(16 * 108 + 100, homeGridContentHeightPx(frames, minHeightPx = 0, tailPx = 0))
    }

    @Test
    fun contentHeight_usesTallestSpanBottom() {
        val m = metrics()
        val frames = listOf(frame(m, 0, 0), frame(m, 2, 3, 2, 2), frame(m, 4, 1))
        assertEquals(3 * 108 + 208, homeGridContentHeightPx(frames, 0, 0))
    }

    @Test
    fun contentHeight_includesTailAndRespectsMinimum() {
        val m = metrics()
        val frames = listOf(frame(m, 0, 1))
        val bottom = 108 + 100
        assertEquals(bottom + 50, homeGridContentHeightPx(frames, minHeightPx = 0, tailPx = 50))
        assertEquals(1000, homeGridContentHeightPx(frames, minHeightPx = 1000, tailPx = 50))
    }

    @Test
    fun contentHeight_countsRestOffsetBecauseItIsPartOfTheFrame() {
        val frames = listOf(frame(metrics(rest = 150f), 0, 1))
        assertEquals(150 + 108 + 100, homeGridContentHeightPx(frames, 0, 0))
        assertNotEquals(
            homeGridContentHeightPx(listOf(frame(metrics(rest = 0f), 0, 1)), 0, 0),
            homeGridContentHeightPx(frames, 0, 0),
        )
    }

    @Test
    fun contentHeight_emptyIsMinimumAndSaturatesInsteadOfWrapping() {
        assertEquals(300, homeGridContentHeightPx(emptyList(), minHeightPx = 300, tailPx = 0))
        val frames = listOf(frame(metrics(), 0, 5))
        assertEquals(Int.MAX_VALUE, homeGridContentHeightPx(frames, 0, Int.MAX_VALUE))
    }
}
