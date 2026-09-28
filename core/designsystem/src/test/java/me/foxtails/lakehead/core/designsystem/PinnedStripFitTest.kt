package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the C1 measured strip fit: grid-unit pitch derivation
 * from the measured edge plus the shared gap, bounds containment, overflow
 * reduction before overlap, and edit-control reserves.
 *
 * Realistic geometry assumes the normal-phone grid (six cells across a
 * `392.dp` viewport with `8.dp` insets/gaps): cell `56.dp`, so `1×2` is
 * `56×120.dp`, `2×2` is `120×120.dp`, `4×2` is `248×120.dp` and `1×3` is
 * `56×184.dp`. Glyphs stay `40.dp` at `8.dp` tile padding; edit controls are
 * `48.dp` touch squares (in-tile half `24.dp`).
 */
class PinnedStripFitTest {

    // ── pitch ─────────────────────────────────────────────────────

    @Test
    fun pitch_derivesGridUnitFromMeasuredEdge() {
        // (120 + 8) / 2 = 64: cell 56 + gap 8.
        assertEquals(64.dp, stripPitch(120.dp, 8.dp, 2))
        assertEquals(64.dp, stripPitch(248.dp, 8.dp, 4))
        assertEquals(64.dp, stripPitch(184.dp, 8.dp, 3))
    }

    @Test
    fun pitch_matchesSlotPitchPxContract() {
        // slotPitchPx(cellPx, gapPx) = cellPx + gapPx: 56.dp at density 3 is
        // 168px, 8.dp is 24px, pitch 64.dp is 192px.
        assertEquals(192L, ShortcutStripGeometry.slotPitchPx(168L, 24L))
        assertEquals(64.dp, stripPitch(120.dp, 8.dp, 2))
    }

    @Test
    fun pitch_adaptsToWidth() {
        // Same span, wider tile: pitch grows instead of a hard-coded 44.dp.
        assertEquals(64.dp, stripPitch(120.dp, 8.dp, 2))
        assertEquals(104.dp, stripPitch(200.dp, 8.dp, 2))
        assertNotEquals(stripPitch(120.dp, 8.dp, 2), stripPitch(200.dp, 8.dp, 2))
    }

    // ── non-edit fit ──────────────────────────────────────────────

    private fun fit(
        tileWidth: Int,
        tileHeight: Int,
        span: GridSpan,
        pinnedCount: Int,
        overflowCount: Int = 0,
        showOverflowIndicator: Boolean = overflowCount > 0,
        isEditSelected: Boolean = false,
    ): PinnedStripFit = fitPinnedStrip(
        tileWidth = tileWidth.dp,
        tileHeight = tileHeight.dp,
        span = span,
        pinnedCount = pinnedCount,
        overflowCount = overflowCount,
        showOverflowIndicator = showOverflowIndicator,
        isEditSelected = isEditSelected,
    )

    @Test
    fun twoByTwo_rendersAppPlusOne_exactFill() {
        // 40 + 64 = 104 = 120 - 2 * 8: full capacity exactly fills the edge.
        val fit = fit(120, 120, GridSpan(2, 2), pinnedCount = 1)
        assertEquals(1, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
        assertEquals(0, fit.hiddenCount)
        assertEquals(64.dp, fit.pitch)
    }

    @Test
    fun twoByTwo_overflow_keepsPin_announcesRemainder() {
        // app + pin + node (40 + 2 * 64 = 168) exceeds 104, so the node
        // yields: the pin stays visible and tappable, the remainder is
        // announced through the tile body.
        val fit = fit(120, 120, GridSpan(2, 2), pinnedCount = 1, overflowCount = 1)
        assertEquals(1, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
        assertEquals(1, fit.hiddenCount)
    }

    @Test
    fun oneByTwo_rendersAppPlusOne_exactFill() {
        val fit = fit(56, 120, GridSpan(1, 2), pinnedCount = 1)
        assertEquals(1, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
        assertEquals(0, fit.hiddenCount)
    }

    @Test
    fun oneByTwo_overflow_keepsPin_announcesRemainder() {
        val fit = fit(56, 120, GridSpan(1, 2), pinnedCount = 1, overflowCount = 1)
        assertEquals(1, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
        assertEquals(1, fit.hiddenCount)
    }

    @Test
    fun fourWide_overflow_showsNodeAlongside() {
        // Two pins plus one overflow: app + 2 + node (232) exactly fills the
        // edge, so nothing is withheld and +N counts only the caller hidden.
        val fit = fit(248, 120, GridSpan(4, 2), pinnedCount = 2, overflowCount = 1)
        assertEquals(2, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertTrue(fit.showOverflowNode)
        assertEquals(1, fit.hiddenCount)
    }

    @Test
    fun fourWide_rendersAppPlusThree_exactFill() {
        // 40 + 3 * 64 = 232 = 248 - 2 * 8.
        val fit = fit(248, 120, GridSpan(4, 2), pinnedCount = 3)
        assertEquals(3, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
    }

    @Test
    fun oneByThree_rendersAppPlusTwo_exactFill() {
        // 40 + 2 * 64 = 168 = 184 - 2 * 8.
        val fit = fit(56, 184, GridSpan(1, 3), pinnedCount = 2)
        assertEquals(2, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
    }

    // ── edit reserves ─────────────────────────────────────────────

    @Test
    fun oneByTwo_editSelected_appOnly_aboveResize() {
        // The app glyph stays; the shortcut spills to announced overflow so
        // nothing reaches the resize corner reserve.
        val fit = fit(56, 120, GridSpan(1, 2), pinnedCount = 1, isEditSelected = true)
        assertEquals(0, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
        assertEquals(1, fit.hiddenCount)
    }

    @Test
    fun twoByTwo_editSelected_withholdsStrip_beforeCenteredControl() {
        // A centered 48.dp control on a 120.dp tile leaves no 40.dp slot:
        // the strip yields entirely and everything is announced.
        val fit = fit(120, 120, GridSpan(2, 2), pinnedCount = 1, isEditSelected = true)
        assertEquals(0, fit.visibleShortcutCount)
        assertFalse(fit.showAppGlyph)
        assertFalse(fit.showOverflowNode)
        assertEquals(1, fit.hiddenCount)
    }

    @Test
    fun fourWide_editSelected_withholdsStrip() {
        val fit = fit(248, 120, GridSpan(4, 2), pinnedCount = 3, isEditSelected = true)
        assertFalse(fit.showAppGlyph)
        assertEquals(3, fit.hiddenCount)
    }

    @Test
    fun twoWideFourTall_editSelected_appOnly() {
        // Tall tile: the bottom band clears the centered control, but the
        // resize corner reserve leaves room for the app alone.
        val fit = fit(120, 248, GridSpan(2, 4), pinnedCount = 1, isEditSelected = true)
        assertEquals(0, fit.visibleShortcutCount)
        assertTrue(fit.showAppGlyph)
        assertEquals(1, fit.hiddenCount)
    }

    @Test
    fun editSelected_neverShowsOverflowNode() {
        val fit = fitPinnedStrip(
            tileWidth = 248.dp,
            tileHeight = 120.dp,
            span = GridSpan(4, 2),
            pinnedCount = 3,
            overflowCount = 2,
            showOverflowIndicator = true,
            isEditSelected = true,
        )
        assertFalse(fit.showOverflowNode)
        assertEquals(5, fit.hiddenCount)
    }
}
