package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.TileBounds
import me.foxtails.lakehead.core.grid.GridRest
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Absolute-origin renderer coverage for the home canvas helpers.
 *
 * Covers [rendererOriginYPx] (G2 rest passthrough, null → 0),
 * [contentMinHeightPxSigned] (row-count height, saturating), [signedRestFor]
 * (tall anchors zero, short applies the 0.45 bias), [contentBoundsForTiles],
 * and [HomeGridMetrics.signedFrame] parity with the legacy row-origin-0 frame.
 */
class HomeCanvasSignedRendererTest {

    private val geometry = SquareCellGeometry(
        columnCount = 5,
        cellSizePx = 100,
        columnGapPx = 8,
        rowGapPx = 8,
    )

    @Test
    fun rendererOriginYPx_passesThroughRestGridOriginY() {
        assertEquals(0L, rendererOriginYPx(null))
        assertEquals(
            540L,
            rendererOriginYPx(GridRest(gridOriginY = 540L, scrollRangePx = 0L, scrollable = false)),
        )
        assertEquals(
            -216L,
            rendererOriginYPx(GridRest(gridOriginY = -216L, scrollRangePx = 100L, scrollable = true)),
        )
    }

    @Test
    fun contentMinHeightPxSigned_countsCellsAndGaps() {
        // 4 rows × 100px cells + 3 × 8px gaps = 424.
        assertEquals(
            424,
            contentMinHeightPxSigned(
                extent = GridVerticalExtent(occupiedBottomRowExclusive = 4L, totalScrollableRows = 4L),
                cellSizePx = 100,
                rowGapPx = 8,
            ),
        )
    }

    @Test
    fun contentMinHeightPxSigned_singleRowHasNoGap() {
        assertEquals(
            100,
            contentMinHeightPxSigned(
                extent = GridVerticalExtent(occupiedBottomRowExclusive = 1L, totalScrollableRows = 1L),
                cellSizePx = 100,
                rowGapPx = 8,
            ),
        )
    }

    @Test
    fun contentMinHeightPxSigned_saturatesOnOverflow() {
        assertEquals(
            Int.MAX_VALUE,
            contentMinHeightPxSigned(
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
        assertEquals(0L, rendererOriginYPx(rest))
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
        assertEquals(540L, rendererOriginYPx(rest))
    }

    @Test
    fun signedRestFor_emptyViewportReturnsNull() {
        assertNull(
            signedRestFor(viewportHeightPx = 0, contentHeightPx = 100, rowStridePx = 108L),
        )
        assertEquals(0L, rendererOriginYPx(null))
    }

    @Test
    fun contentBoundsForTiles_preservesAbsoluteLeadingRows() {
        assertNull(contentBoundsForTiles(emptyList(), 5))
        val oneRow = contentBoundsForTiles(
            listOf(renderTile("a", column = 0, row = 5)),
            5,
        )
        assertEquals(0L, oneRow?.minRow)
        assertEquals(6L, oneRow?.maxRowExclusive)

        val twoRows = contentBoundsForTiles(
            listOf(renderTile("b", column = 0, row = 5, spanRows = 2)),
            5,
        )
        assertEquals(0L, twoRows?.minRow)
        assertEquals(7L, twoRows?.maxRowExclusive)
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

    @Test
    fun signedFrame_matchesLegacyRowOriginZeroFormula() {
        // Dropping the always-zero row origin must not move any frame: the
        // metrics frame equals the designsystem signed frame at originRow 0.
        for (rest in listOf(-216L, 0L, 540L)) {
            for (originX in listOf(0, 13)) {
                val metrics = homeGridMetricsFor(geometry, 5, originX, rest.toFloat())
                for (row in listOf(0, 1, 7)) {
                    for (column in listOf(0, 2)) {
                        val legacy = TileBounds.signedBounds(
                            column = column,
                            row = row.toLong(),
                            span = GridSpan(2, 2),
                            geometry = geometry,
                            originX = originX,
                            originRow = 0L,
                            originYPx = rest,
                        )
                        assertEquals(
                            legacy,
                            metrics.signedFrame(LogicalPosition(column, row), LogicalSpan(2, 2)),
                        )
                    }
                }
            }
        }
    }

    private fun renderTile(id: String, column: Int, row: Int, spanRows: Int = 1): HomeRenderTile {
        val identity = AppIdentity(
            profileId = ProfileId("profile"),
            component = ComponentIdentity("pkg.$id", "cls.$id"),
        )
        return HomeRenderTile(
            itemId = ItemId(id),
            appIdentity = identity,
            position = LogicalPosition(column, row),
            span = LogicalSpan(1, spanRows),
            availability = ItemAvailability.AVAILABLE,
            label = "App $id",
            icon = null,
            isLaunchable = true,
        )
    }
}
