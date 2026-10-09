package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileNotificationPreviewTest {
    private val padding = 8.dp
    private val gap = 8.dp
    private val extent = 40.dp

    private fun region(width: Dp, height: Dp) =
        notificationPreviewRegion(width, height, padding, gap, extent)

    @Test
    fun regionStopsAboveTheBottomIconRow() {
        // 2×2 on a 5-column phone: ~152dp square.
        val r = region(152.dp, 152.dp)!!
        assertEquals(136f, r.width.value, 0.01f)
        // top padding + region + gap + icon row + bottom padding == tile height
        assertEquals(152f, (padding + r.height + gap + extent + padding).value, 0.01f)
    }

    @Test
    fun regionGrowsWithRowsAndWidth() {
        val twoByTwo = region(152.dp, 152.dp)!!
        val fourByTwo = region(312.dp, 152.dp)!!
        val twoByThree = region(152.dp, 232.dp)!!
        val threeByFive = region(232.dp, 392.dp)!!
        assertEquals(twoByTwo.height, fourByTwo.height)
        assertTrue(fourByTwo.width > twoByTwo.width)
        assertTrue(twoByThree.height > twoByTwo.height)
        assertTrue(threeByFive.height > twoByThree.height)
    }

    @Test
    fun tooShortTileHasNoRegion() {
        assertNull(region(152.dp, 90.dp))
        assertNotNull(region(152.dp, 152.dp))
    }

    @Test
    fun twoByTwoFitsTwoRowsAndLastRowTakesTheRest() {
        val r = region(152.dp, 152.dp)!!
        val layout = notificationPreviewLayout(r.height, previewCount = 5)
        assertEquals(2, layout.rows)
        assertEquals(2, layout.lastBodyLines)
    }

    @Test
    fun rowsNeverExceedAvailablePreviews() {
        val r = region(232.dp, 392.dp)!!
        val one = notificationPreviewLayout(r.height, previewCount = 1)
        assertEquals(1, one.rows)
        // A single notification may use the whole region for its body.
        assertTrue(one.lastBodyLines > 4)
        assertEquals(0, notificationPreviewLayout(r.height, previewCount = 0).rows)
    }

    @Test
    fun layoutHeightNeverExceedsRegion() {
        listOf(88f, 120f, 168f, 300f).forEach { h ->
            (1..8).forEach { count ->
                val layout = notificationPreviewLayout(h.dp, count)
                val used = 38f * (layout.rows - 1) + 16f + 16f * layout.lastBodyLines
                assertTrue("h=$h count=$count used=$used", used <= h + 0.01f)
            }
        }
    }
}
