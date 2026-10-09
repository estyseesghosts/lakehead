package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileNotificationCountTest {
    private val symbol = 40.dp
    private val gap = 4.dp

    @Test
    fun countTilesAreSingleColumnOrSingleRow() {
        listOf(GridSpan(1, 1), GridSpan(1, 3), GridSpan(2, 1), GridSpan(5, 1))
            .forEach { assertTrue("$it", isCountTileSpan(it)) }
        listOf(GridSpan(2, 2), GridSpan(4, 2), GridSpan(2, 3), GridSpan(3, 5))
            .forEach { assertFalse("$it", isCountTileSpan(it)) }
    }

    @Test
    fun countTextIsCappedPerShape() {
        assertEquals("", notificationCountText(0, GridSpan(1, 1)))
        assertEquals("7", notificationCountText(7, GridSpan(1, 1)))
        assertEquals("9", notificationCountText(9, GridSpan(1, 3)))
        assertEquals("9+", notificationCountText(10, GridSpan(1, 1)))
        assertEquals("42", notificationCountText(42, GridSpan(2, 1)))
        assertEquals("99", notificationCountText(99, GridSpan(5, 1)))
        assertEquals("99+", notificationCountText(100, GridSpan(5, 1)))
    }

    @Test
    fun rowTileKeepsFullIconAndMatchesDigitToVisibleGlyph() {
        val layout = countedSymbolLayout(availableWidth = 130.dp, symbolSize = symbol, gap = gap, characterCount = 2)
        assertEquals(40f, layout.iconExtent.value, 0.01f)
        assertEquals(30f, layout.digitHeight.value, 0.01f)
    }

    @Test
    fun oneColumnTileShrinksPairToFit() {
        val available = 56.dp
        val one = countedSymbolLayout(available, symbol, gap, characterCount = 1)
        val two = countedSymbolLayout(available, symbol, gap, characterCount = 2)
        assertTrue(one.iconExtent < symbol)
        assertTrue(two.iconExtent <= one.iconExtent)
        listOf(one, two).forEach {
            assertTrue(it.iconExtent + gap + it.textWidth <= available + 0.01.dp)
            assertTrue(it.iconExtent >= 24.dp)
        }
        // Digit height equals the visible glyph while the floor is not binding.
        assertEquals(one.iconExtent.value * 0.75f, one.digitHeight.value, 0.01f)
    }

    @Test
    fun floorHoldsIconAndShrinksOnlyDigitsWhenNarrow() {
        val layout = countedSymbolLayout(availableWidth = 40.dp, symbolSize = symbol, gap = gap, characterCount = 2)
        assertEquals(24f, layout.iconExtent.value, 0.01f)
        assertTrue(layout.iconExtent + gap + layout.textWidth <= 40.dp + 0.01.dp)
        assertTrue(layout.digitHeight < layout.iconExtent * 0.75f)
    }

    @Test
    fun descriptionIsPluralised() {
        assertEquals("1 notification", notificationCountDescription(1))
        assertEquals("3 notifications", notificationCountDescription(3))
    }

    @Test
    fun extraSlotsDisplaceShortcutsIntoOverflow() {
        val base = fitPinnedStrip(
            tileWidth = 300.dp, tileHeight = 70.dp, span = GridSpan(4, 1),
            pinnedCount = 3, overflowCount = 0, showOverflowIndicator = true, isEditSelected = false,
        )
        val counted = fitPinnedStrip(
            tileWidth = 300.dp, tileHeight = 70.dp, span = GridSpan(4, 1),
            pinnedCount = 3, overflowCount = 0, showOverflowIndicator = true, isEditSelected = false,
            extraSlots = 1,
        )
        assertTrue(counted.visibleShortcutCount <= base.visibleShortcutCount)
        assertEquals(3 - counted.visibleShortcutCount, counted.hiddenCount)
    }
}
