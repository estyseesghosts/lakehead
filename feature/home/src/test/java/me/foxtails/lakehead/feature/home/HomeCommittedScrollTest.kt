package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.GridVerticalLayout
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Committed geometry owns the idle scroll range; the transient MOVE workspace
 * ([HomeEditWorkspace]) is covered separately.
 */
class HomeCommittedScrollTest {

    private val profile = ProfileId("personal")
    private val identity = AppIdentity(profile, ComponentIdentity("com.app", "Main"))

    private fun tile(
        id: String,
        column: Int = 0,
        row: Int = 0,
        spanColumns: Int = 1,
        spanRows: Int = 1,
    ) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(spanColumns, spanRows),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )

    private fun extent(bottomExclusive: Long, totalRows: Long, minRow: Long = 0L) =
        GridVerticalExtent(
            occupiedBottomRowExclusive = bottomExclusive,
            totalScrollableRows = totalRows,
            occupiedMinRow = minRow,
            occupiedMaxRowExclusive = bottomExclusive,
            occupiedHeightRows = totalRows,
        )

    // ── committedScrollMaxPx ─────────────────────────────────────────

    @Test
    fun committedMax_exactOverflow_noTail() {
        val committed = committedScrollMaxPx(extent(17L, 17L), 100, 8, 1720)
        assertEquals(108, committed)
    }

    @Test
    fun committedMax_contentThatFits_isZero() {
        val committed = committedScrollMaxPx(extent(2L, 2L), 100, 8, 1800)
        assertEquals(0, committed)
        assertEquals(0, clampScrollToCommitted(900, committed))
    }

    // ── clampScrollToCommitted ───────────────────────────────────────

    @Test
    fun clampCommitted_preservesShrinkBottomPinsZeroRests() {
        assertEquals(500, clampScrollToCommitted(500, 1000))
        assertEquals(2000, clampScrollToCommitted(4500, 2000))
        assertEquals(0, clampScrollToCommitted(900, 0))
    }

    // ── extent parity + fallback ─────────────────────────────────────

    @Test
    fun parity_emptyRequiresZero() {
        assertTrue(isExtentParityOk(extent(0L, 0L), emptyList()))
        assertFalse(isExtentParityOk(extent(4L, 4L), emptyList()))
    }

    @Test
    fun parity_sparseMultiSpanNonZeroMin_matchesMeasure() {
        val tiles = listOf(
            tile("a", column = 0, row = 5),
            tile("b", column = 1, row = 18, spanColumns = 2, spanRows = 2),
        )
        val placements = tiles.map {
            GridPlacement(
                id = it.itemId,
                position = GridPosition(it.position.column, it.position.row),
                span = GridSpan(it.span.columns, it.span.rows),
            )
        }
        val measured = GridVerticalLayout.measure(placements, 10L, 2L)
        assertTrue(isExtentParityOk(measured, tiles))
        // A stale short extent (ignoring the distant tile) fails parity.
        assertFalse(isExtentParityOk(extent(6L, 1L, 5L), tiles))
    }

    @Test
    fun fallback_invalidRemeasuresFromAllPlacements_neverMovesTiles() {
        val tiles = listOf(
            tile("near", column = 0, row = 0),
            tile("far", column = 0, row = 16),
        )
        val stale = extent(1L, 1L)
        val fixed = committedExtentOrRemeasured(stale, tiles, 1800, 108L)
        assertEquals(17L, fixed.occupiedMaxRowExclusive)
        assertEquals(0L, fixed.occupiedMinRow)
        assertEquals(17L, fixed.totalScrollableRows)
        // Tiles untouched: positions preserved exactly.
        assertEquals(LogicalPosition(0, 0), tiles[0].position)
        assertEquals(LogicalPosition(0, 16), tiles[1].position)
    }

    @Test
    fun fallback_validKeepsLoaded() {
        val tiles = listOf(tile("a", column = 0, row = 2))
        val placements = listOf(
            GridPlacement(ItemId("a"), GridPosition(0, 2), GridSpan(1, 1)),
        )
        val measured = GridVerticalLayout.measure(placements, 10L, 0L)
        assertEquals(measured, committedExtentOrRemeasured(measured, tiles, 1800, 108L))
    }
}
