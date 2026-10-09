package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scrolled-gesture boundary regression: pointer Y arrives node-local (content
 * space) and must convert once to viewport-local before `contentY`/move math.
 */
class HomeGestureCoordinatesTest {
    private val size = 48f
    private val gap = 4f
    private val stride = size + gap

    private fun metrics() = HomeGridMetrics(6, size, size, gap, gap, originXPx = 0f, restOffsetYPx = 0f)

    private fun tile(column: Int, row: Int) = HomeRenderTile(
        itemId = ItemId("t"),
        appIdentity = AppIdentity(ProfileId("p"), ComponentIdentity("pkg", "cls")),
        position = LogicalPosition(column, row),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = "t",
        icon = null,
        isLaunchable = true,
    )

    @Test
    fun fingerOnTileYieldsThatCellForSeveralScrolls() {
        val m = metrics()
        val column = 2
        val row = 4
        val rect = m.rect(LogicalPosition(column, row), LogicalSpan(1, 1))
        val grabX = 10f
        val grabY = 20f
        val nodeX = rect.left + grabX
        val nodeY = rect.top + grabY
        val frame = MoveCoordinateFrame(m, GridSpan(1, 1), grabX, grabY)
        for (scroll in listOf(0f, stride, 250.5f, 3 * stride)) {
            val viewportX = nodeX
            val viewportY = nodeLocalToViewportY(nodeY, scroll)
            val cell = MoveDragMath.requestedCell(frame, viewportX, viewportY, scroll)
            assertEquals("scroll=$scroll", RequestedCell(column.toLong(), row.toLong()), cell)
        }
    }

    @Test
    fun liftTranslationPutsTileUnderFingerWhenScrolled() {
        val m = metrics()
        val position = LogicalPosition(2, 4)
        val rect = m.rect(position, LogicalSpan(1, 1))
        val grabX = 10f
        val grabY = 20f
        val move = DragState.Move(
            itemId = ItemId("t"),
            originalPosition = position,
            span = LogicalSpan(1, 1),
            metrics = m,
            grabOffsetXPx = grabX,
            grabOffsetYPx = grabY,
            requestedCell = RequestedCell(2, 4),
            previewPosition = position,
            validity = CandidateValidity.VALID,
            snapshot = emptyList(),
        )
        val nodeX = rect.left + grabX
        val nodeY = rect.top + grabY
        for (scroll in listOf(0f, stride, 250.5f, 3 * stride)) {
            val viewportY = nodeLocalToViewportY(nodeY, scroll)
            val lift = MoveDragMath.liftTranslation(move, nodeX, viewportY, scroll)
            assertEquals("scroll=$scroll", 0f, lift.x, 0.001f)
            assertEquals("scroll=$scroll", 0f, lift.y, 0.001f)
        }
    }

    @Test
    fun edgeZoneSeesViewportNotContentY() {
        // Finger 10px below the viewport top with 500px scrolled: viewport says TOP,
        // the unconverted content Y (510) would miss the band entirely.
        val viewportH = 1000
        val edge = 72
        val scroll = 500f
        val nodeY = 10f + scroll
        val viewportY = nodeLocalToViewportY(nodeY, scroll)
        assertEquals(AutoScrollZone.TOP, autoScrollZone(viewportY, viewportH, edge))
        assertEquals(AutoScrollZone.NONE, autoScrollZone(nodeY, viewportH, edge))
    }
}
