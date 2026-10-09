package me.foxtails.lakehead

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePlacementReconciliationTest {
    private val home = HomeId("home")
    private val identity = AppIdentity(ProfileId("0"), ComponentIdentity("com.a", "A"))

    private fun placed(id: String, column: Int, row: Int, availability: ItemAvailability = ItemAvailability.AVAILABLE) =
        AppPlacement(home, ItemId(id), identity, LogicalPosition(column, row), LogicalSpan(1, 1), availability)

    private fun grid(vararg cells: GridPlacement<ItemId>) = GridRules.Result.Accepted(cells.toList())

    private fun cell(id: String, column: Int, row: Int, columns: Int = 1, rows: Int = 1) =
        GridPlacement(ItemId(id), GridPosition(column, row), GridSpan(columns, rows))

    @Test
    fun requestedMoveChangesOnlyTheNamedTile() {
        val result = withRequestedMove(
            listOf(placed("a", 0, 0), placed("b", 1, 0)),
            ItemId("a"),
            LogicalPosition(3, 1),
            columns = 6,
        )
        assertEquals(listOf(LogicalPosition(3, 1), LogicalPosition(1, 0)), result!!.map { it.position })
    }

    @Test
    fun requestedMoveFarBelowIsClosedUpExactlyAsTheRepositoryWould() {
        val result = withRequestedMove(
            listOf(placed("a", 0, 0), placed("b", 1, 0)),
            ItemId("a"),
            LogicalPosition(3, 20),
            columns = 6,
        )
        // Two empty rows are tolerated between sections: row 20 settles at row 3.
        assertEquals(listOf(LogicalPosition(3, 3), LogicalPosition(1, 0)), result!!.map { it.position })
    }

    @Test
    fun requestedMoveOntoAnotherTileIsNull() {
        assertNull(
            withRequestedMove(
                listOf(placed("a", 0, 0), placed("b", 1, 0)),
                ItemId("a"),
                LogicalPosition(1, 0),
                columns = 6,
            ),
        )
    }

    @Test
    fun requestedMoveOfAnUnknownTileIsNull() {
        assertNull(withRequestedMove(listOf(placed("a", 0, 0)), ItemId("x"), LogicalPosition(1, 1), columns = 6))
    }

    @Test
    fun requestedSpanChangesOnlyTheNamedTile() {
        val result = withRequestedSpan(listOf(placed("a", 0, 0), placed("b", 1, 0)), ItemId("b"), LogicalSpan(2, 2))
        assertEquals(listOf(LogicalSpan(1, 1), LogicalSpan(2, 2)), result!!.map { it.span })
    }

    @Test
    fun requestedSpanOfAnUnknownTileIsNull() {
        assertNull(withRequestedSpan(listOf(placed("a", 0, 0)), ItemId("x"), LogicalSpan(2, 2)))
    }

    @Test
    fun acceptedGridReplacesPositionsAndSpansButKeepsOrderAvailabilityAndIdentity() {
        val before = listOf(
            placed("a", 0, 0),
            placed("b", 0, 10, ItemAvailability.UNAVAILABLE),
        )
        val result = withAcceptedGrid(before, grid(cell("a", 1, 1, 2, 2), cell("b", 0, 4)))!!
        assertEquals(listOf(ItemId("a"), ItemId("b")), result.map { it.itemId })
        assertEquals(listOf(LogicalPosition(1, 1), LogicalPosition(0, 4)), result.map { it.position })
        assertEquals(LogicalSpan(2, 2), result[0].span)
        assertEquals(ItemAvailability.UNAVAILABLE, result[1].availability)
        assertEquals(identity, (result[1] as AppPlacement).appIdentity)
    }

    @Test
    fun acceptedGridDropsPlacementsItNoLongerContains() {
        val result = withAcceptedGrid(listOf(placed("a", 0, 0), placed("b", 1, 0)), grid(cell("a", 0, 0)))!!
        assertEquals(listOf(ItemId("a")), result.map { it.itemId })
    }

    @Test
    fun acceptedGridNamingAnUnknownTileIsNull() {
        assertNull(withAcceptedGrid(listOf(placed("a", 0, 0)), grid(cell("a", 0, 0), cell("new", 2, 2))))
    }
}
