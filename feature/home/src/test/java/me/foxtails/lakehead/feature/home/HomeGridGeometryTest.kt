package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeGridGeometryTest {
    private val spans = listOf(
        LogicalSpan(1, 1), LogicalSpan(2, 1), LogicalSpan(1, 2), LogicalSpan(2, 2), LogicalSpan(4, 2),
    )
    private val rows = listOf(0, 1, 5, 50)
    private val scrolls = listOf(0f, 13f, 250.5f, 4000f)

    private fun metrics(
        viewportWidth: Float,
        columns: Int = 6,
        gap: Float = 4f,
        rest: Float = 0f,
    ): HomeGridMetrics {
        val cell = (viewportWidth - gap * (columns - 1)) / columns
        return HomeGridMetrics(columns, cell, cell, gap, gap, originXPx = 3f, restOffsetYPx = rest)
    }

    private val metricsTable = listOf(
        metrics(360f), metrics(411f), metrics(1080f / 2.625f, rest = 0f),
        metrics(411f, rest = 37.25f), metrics(900f, columns = 8, gap = 6f, rest = 120f),
    )

    @Test
    fun rectUsesStrideOriginRestAndGapsOnlyInsideTheSpan() {
        val m = HomeGridMetrics(6, 48f, 48f, 4f, 4f, originXPx = 10f, restOffsetYPx = 20f)
        val rect = m.rect(LogicalPosition(2, 3), LogicalSpan(2, 2))
        assertEquals(10f + 2 * 52f, rect.left, 0f)
        assertEquals(20f + 3 * 52f, rect.top, 0f)
        assertEquals(100f, rect.width, 0f)
        assertEquals(100f, rect.height, 0f)
    }

    @Test
    fun topLeftRoundTripsForEveryColumnRowSpanMetricsAndScroll() {
        for (m in metricsTable) for (span in spans) for (row in rows) for (scroll in scrolls) {
            for (column in 0..m.columns - span.columns) {
                val rect = m.rect(LogicalPosition(column, row), span)
                // tile top-left in content space, seen from the viewport and back
                val viewportY = rect.top - scroll
                val cell = m.requestedTopLeftCell(rect.left, m.contentY(viewportY, scroll))
                assertEquals("m=$m span=$span", RequestedCell(column.toLong(), row.toLong()), cell)
            }
        }
    }

    @Test
    fun grabPointAnywhereInsideTileRecoversTheTileCell() {
        for (m in metricsTable) for (span in spans) for (row in rows) {
            val position = LogicalPosition(0, row)
            val rect = m.rect(position, span)
            val grabs = listOf(
                0f to 0f,
                rect.width / 2 to rect.height / 2,
                rect.width - 0.5f to rect.height - 0.5f,
                rect.width - 0.5f to 0f,
            )
            for ((gx, gy) in grabs) for (scroll in scrolls) {
                val pointerViewportY = rect.top + gy - scroll
                val tileTop = m.contentY(pointerViewportY, scroll) - gy
                val cell = m.requestedTopLeftCell(rect.left + gx - gx, tileTop)
                assertEquals(RequestedCell(0L, row.toLong()), cell)
            }
        }
    }

    @Test
    fun oneRowStrideOfScrollMovesTheRequestedRowByExactlyOne() {
        for (m in metricsTable) for (row in rows) for (scroll in scrolls) {
            val grabY = m.cellHeightPx / 2
            val pointerViewportY = m.top(row) + grabY - scroll
            val before = m.requestedTopLeftCell(
                0f, m.contentY(pointerViewportY, scroll) - grabY,
            )
            val after = m.requestedTopLeftCell(
                0f, m.contentY(pointerViewportY, scroll + m.rowStridePx) - grabY,
            )
            assertEquals(before.row + 1, after.row)
        }
    }

    @Test
    fun requestsAboveAndBesideTheGridStayRawAndUnclamped() {
        val m = HomeGridMetrics(6, 48f, 48f, 4f, 4f, originXPx = 10f, restOffsetYPx = 20f)
        assertEquals(-1L, m.requestedTopLeftCell(10f, 20f - 1f).row)
        assertEquals(-3L, m.requestedTopLeftCell(10f, 20f - 3 * 52f).row)
        assertEquals(-1L, m.requestedTopLeftCell(10f - 1f, 20f).column)
        assertEquals(9L, m.requestedTopLeftCell(10f + 9 * 52f, 20f).column)
    }

    @Test
    fun snapRuleFloorsWithinTolerance() {
        val m = HomeGridMetrics(6, 48f, 48f, 4f, 4f, originXPx = 0f, restOffsetYPx = 0f)
        assertEquals(1L, m.requestedTopLeftCell(52f - 0.01f, 0f).column) // inside tolerance
        assertEquals(0L, m.requestedTopLeftCell(52f - 0.5f, 0f).column) // clearly previous cell
        assertEquals(1L, m.requestedTopLeftCell(52f + 51f, 0f).column) // gap/late band floors
    }

    @Test
    fun requestedSpanKeepsOriginFixedAndFollowsPointer() {
        val m = HomeGridMetrics(6, 48f, 48f, 4f, 4f, originXPx = 0f, restOffsetYPx = 10f)
        val origin = LogicalPosition(1, 5)
        assertEquals(LogicalSpan(1, 1), m.requestedSpan(origin, 0f, 0f)) // up/left clamps to 1x1
        assertEquals(LogicalSpan(2, 2), m.requestedSpan(origin, 52f + 52f, 10f + 5 * 52f + 52f))
        assertEquals(LogicalSpan(4, 2), m.requestedSpan(origin, 52f + 3 * 52f, 10f + 6 * 52f))
    }

    @Test
    fun tileAtHitsOnlyTilesAndNothingAboveGrid() {
        val m = HomeGridMetrics(6, 48f, 48f, 4f, 4f, originXPx = 0f, restOffsetYPx = 0f)
        assertNull(m.tileAt(emptyList(), 10f, 10f))
        assertNull(m.tileAt(emptyList(), -10f, -10f))
    }

    @Test
    fun nodeLocalToViewportY_subtractsScrollOnce() {
        assertEquals(100f, nodeLocalToViewportY(100f, 0f), 0f)
        assertEquals(100f, nodeLocalToViewportY(300f, 200f), 0f)
        assertEquals(0f, nodeLocalToViewportY(52f, 52f), 0f)
    }

    @Test
    fun scrolledNodeLocalDownRecoversTileCellWithoutDoubleCount() {
        // Regression for the scrolled-drag double-count: gesture positions arrive
        // node-local (content space, scroll included). Converting once to viewport
        // and re-applying scroll recovers the tile; adding scroll to node-local
        // directly lands scrollY px below the finger.
        val m = HomeGridMetrics(6, 48f, 48f, 4f, 4f, originXPx = 0f, restOffsetYPx = 0f)
        val tiles = listOf(
            fakeTile("a", 2, 4),
            fakeTile("b", 0, 0),
        )
        val target = tiles.first()
        val rect = m.rect(target.position, target.span)
        val grabY = m.cellHeightPx / 2
        val nodeY = rect.top + grabY
        for (scroll in listOf(0f, 52f, 250.5f, 3 * 52f)) {
            val viewportY = nodeLocalToViewportY(nodeY, scroll)
            val contentY = m.contentY(viewportY, scroll)
            assertEquals(nodeY, contentY, 0.001f)
            val hit = m.tileAt(tiles, rect.left + 10f, contentY)
            assertEquals(target.itemId, hit?.itemId)
            // The old double-count (nodeY + scroll) drifts below the tile once scrolled.
            if (scroll > 0f) {
                val drifted = m.tileAt(tiles, rect.left + 10f, nodeY + scroll)
                assert(hit?.itemId != drifted?.itemId || drifted?.position != target.position) {
                    "double-count should miss at scroll=$scroll"
                }
            }
        }
    }

    private fun fakeTile(id: String, column: Int, row: Int) = HomeRenderTile(
        itemId = me.foxtails.lakehead.core.model.ItemId(id),
        appIdentity = me.foxtails.lakehead.core.model.AppIdentity(
            me.foxtails.lakehead.core.model.ProfileId("p"),
            me.foxtails.lakehead.core.model.ComponentIdentity("pkg", "cls"),
        ),
        position = LogicalPosition(column, row),
        span = LogicalSpan(1, 1),
        availability = me.foxtails.lakehead.core.model.ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )
}
