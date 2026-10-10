package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderOverlayLayoutTest {
    private fun tile(name: String, column: Int, row: Int, columns: Int = 1, rows: Int = 1) = HomeRenderTile(
        itemId = ItemId(name),
        appIdentity = AppIdentity(ProfileId("0"), ComponentIdentity("org.$name", "org.$name.Main")),
        position = LogicalPosition(column, row),
        span = LogicalSpan(columns, rows),
        availability = ItemAvailability.AVAILABLE,
        label = name,
        icon = null,
        isLaunchable = true,
    )

    private fun layout(
        tiles: List<HomeRenderTile>,
        columns: Int = 5,
        width: Int = 1080,
        height: Int = 2200,
    ) = folderOverlayLayout(tiles, columns, width, height, marginPx = 48, paddingPx = 24, gapPx = 24)

    @Test fun panelIsCentredAndInsideTheMargins() {
        val result = layout(listOf(tile("a", 0, 0)))
        assertEquals(48, result.panel.left)
        assertEquals(1080 - 48, result.panel.right)
        assertEquals(result.panel.top, 2200 - result.panel.bottom)
    }

    @Test fun columnCountComesFromInput() {
        val five = layout(listOf(tile("a", 0, 0)), columns = 5).metrics
        val three = layout(listOf(tile("a", 0, 0)), columns = 3).metrics
        assertEquals(5, five.columns)
        assertEquals(3, three.columns)
        assertTrue(three.cellWidthPx > five.cellWidthPx)
    }

    @Test fun fiveSquareCellsFillTheInteriorWidth() {
        val m = layout(listOf(tile("a", 0, 0))).metrics
        val interior = 1080 - 96
        val used = 5 * m.cellWidthPx + 4 * m.columnGapPx
        assertTrue(used <= interior - 2 * 24 + 0.001f)
        assertEquals(m.cellWidthPx, m.cellHeightPx, 0f)
    }

    @Test fun aTwoByTwoAndAWideMemberLayOutAtTheirSpans() {
        val tiles = listOf(tile("big", 0, 0, 2, 2), tile("wide", 0, 2, 4, 2))
        val m = layout(tiles).metrics
        val big = m.pixelFrame(tiles[0].position, tiles[0].span)
        val wide = m.pixelFrame(tiles[1].position, tiles[1].span)
        assertEquals(big.widthPx, big.heightPx)
        assertEquals(2 * m.cellWidthPx.toInt() + m.columnGapPx.toInt(), big.widthPx)
        assertEquals(4 * m.cellWidthPx.toInt() + 3 * m.columnGapPx.toInt(), wide.widthPx)
        assertEquals(2 * m.cellHeightPx.toInt() + m.rowGapPx.toInt(), wide.heightPx)
    }

    @Test fun shortContentShrinksThePanelAndTallContentCapsAtSeventyPercent() {
        val short = layout(listOf(tile("a", 0, 0)))
        val tall = layout((0 until 40).map { tile("t$it", it % 5, it / 5) })
        assertTrue(short.panel.bottom - short.panel.top < tall.panel.bottom - tall.panel.top)
        assertEquals((2200 * FOLDER_PANEL_MAX_HEIGHT_FRACTION).toInt(), tall.panel.bottom - tall.panel.top)
        assertTrue(tall.contentHeightPx > tall.panel.bottom - tall.panel.top)
    }

    @Test fun emptyFolderStillGetsOneCellOfPanel() {
        val result = layout(emptyList())
        assertTrue(result.panel.bottom - result.panel.top > 0)
    }
}
