package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/** The drop-target ghost equals the normalised result, and the lifted tile tracks the finger. */
class MoveSettleAndLiftTest {

    private fun cell(id: String, column: Int, row: Int) =
        GridPlacement(ItemId(id), GridPosition(column, row), GridSpan(1, 1))

    private val snapshot = listOf(cell("a", 0, 0), cell("b", 1, 0))

    @Test
    fun settledPosition_closesUpAGapWiderThanTwoRows() {
        assertEquals(
            LogicalPosition(3, 3),
            MoveDragMath.settledPosition(snapshot, ItemId("a"), LogicalPosition(3, 20), columns = 6),
        )
    }

    @Test
    fun settledPosition_keepsAnAllowedGapAndAdjacentRows() {
        assertEquals(
            LogicalPosition(3, 3),
            MoveDragMath.settledPosition(snapshot, ItemId("a"), LogicalPosition(3, 3), columns = 6),
        )
        assertEquals(
            LogicalPosition(2, 1),
            MoveDragMath.settledPosition(snapshot, ItemId("a"), LogicalPosition(2, 1), columns = 6),
        )
    }

    @Test
    fun settledPosition_fallsBackToTheRequestWhenTheMoveIsNotAccepted() {
        assertEquals(
            LogicalPosition(1, 0),
            MoveDragMath.settledPosition(snapshot, ItemId("a"), LogicalPosition(1, 0), columns = 6),
        )
    }

    @Test
    fun placementGlide_runsFromTheOldSpotToZero() {
        val glide = PlacementGlide()
        glide.retarget(androidx.compose.ui.geometry.Offset(100f, -40f))
        glide.progress = 0f
        assertEquals(100f, glide.offset.x, 0.001f)
        glide.progress = 0.5f
        assertEquals(50f, glide.offset.x, 0.001f)
        assertEquals(-20f, glide.offset.y, 0.001f)
        glide.progress = 1f
        assertEquals(0f, glide.offset.x, 0.001f)
    }

    @Test
    fun placementGlide_retargetMidGlideContinuesFromTheCurrentVisualSpot() {
        val glide = PlacementGlide()
        glide.retarget(androidx.compose.ui.geometry.Offset(100f, 0f))
        glide.progress = 0.5f
        glide.retarget(androidx.compose.ui.geometry.Offset(10f, 0f))
        assertEquals(60f, glide.offset.x, 0.001f)
    }
}
