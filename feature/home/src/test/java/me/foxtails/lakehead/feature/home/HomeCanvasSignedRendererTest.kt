package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rest-offset and content-height coverage for the home canvas helpers.
 *
 * Covers [contentMinHeightPx] (row-count height, saturating), [signedRestFor]
 * (tall anchors zero, short applies the 0.45 bias) and the absolute
 * [HomeGridMetrics.signedFrame] top formula.
 */
class HomeCanvasSignedRendererTest {

    private val geometry = SquareCellGeometry(
        columnCount = 5,
        cellSizePx = 100,
        columnGapPx = 8,
        rowGapPx = 8,
    )

    @Test
    fun contentMinHeightPx_countsCellsAndGaps() {
        // 4 rows × 100px cells + 3 × 8px gaps = 424.
        assertEquals(
            424,
            contentMinHeightPx(
                extent = GridVerticalExtent(occupiedBottomRowExclusive = 4L, totalScrollableRows = 4L),
                cellSizePx = 100,
                rowGapPx = 8,
            ),
        )
    }

    @Test
    fun contentMinHeightPx_singleRowHasNoGap() {
        assertEquals(
            100,
            contentMinHeightPx(
                extent = GridVerticalExtent(occupiedBottomRowExclusive = 1L, totalScrollableRows = 1L),
                cellSizePx = 100,
                rowGapPx = 8,
            ),
        )
    }

    @Test
    fun contentMinHeightPx_saturatesOnOverflow() {
        assertEquals(
            Int.MAX_VALUE,
            contentMinHeightPx(
                extent = GridVerticalExtent(
                    occupiedBottomRowExclusive = Long.MAX_VALUE,
                    totalScrollableRows = Long.MAX_VALUE,
                ),
                cellSizePx = Int.MAX_VALUE,
                rowGapPx = Int.MAX_VALUE,
            ),
        )
    }

    @Test
    fun signedRestFor_tallContentAnchorsAtZero() {
        // Content (3290px for 30 rows of stride 110 minus final gap) exceeds the
        // 1000px viewport: tall branch anchors minRow 0 at the viewport top.
        val rest = signedRestFor(
            viewportHeightPx = 1000,
            contentHeightPx = 3290,
            rowStridePx = 110L,
        )
        assertEquals(0L, rest?.gridOriginY)
        assertEquals(2290L, rest?.scrollRangePx)
        assertEquals(0L, rest?.gridOriginY ?: 0L)
    }

    @Test
    fun signedRestFor_shortContentAppliesBias() {
        // Viewport 2000, content 800, stride 200: free 1200 × 0.45 → origin 540.
        val rest = signedRestFor(
            viewportHeightPx = 2000,
            contentHeightPx = 800,
            rowStridePx = 200L,
        )
        assertEquals(540L, rest?.gridOriginY)
        assertEquals(540L, rest?.gridOriginY ?: 0L)
    }

    @Test
    fun signedRestFor_emptyViewportReturnsNull() {
        assertNull(
            signedRestFor(viewportHeightPx = 0, contentHeightPx = 100, rowStridePx = 108L),
        )
    }

    @Test
    fun signedFrames_applyRestOffsetFromPermanentRowZero() {
        val metrics = homeGridMetricsFor(geometry, columns = 5, originXPx = 0, restOffsetYPx = 540f)
        val spans = listOf(
            LogicalSpan(1, 1),
            LogicalSpan(2, 1),
            LogicalSpan(1, 2),
            LogicalSpan(2, 2),
            LogicalSpan(4, 2),
        )
        for (row in 0..2) {
            val expectedTop = 540 + row * 108
            for (span in spans) {
                assertEquals(expectedTop, metrics.signedFrame(LogicalPosition(0, row), span).top)
            }
        }
        val first = metrics.signedFrame(LogicalPosition(0, 0), LogicalSpan(1, 1))
        assertEquals(540, first.top)
        assertEquals(640, first.bottom)
    }
}
