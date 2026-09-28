package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the C1 tile arrangement: joining the persisted per-tile
 * binding with live shortcut metadata ([TilePinnedStrip.resolve]) and
 * deriving the visible/overflow window per span ([TilePinnedStrip.arrange]).
 *
 * Capacity comes from `core:grid` (never re-derived here): `2×2` → app + 1,
 * `4`-wide × `2`-high → app + 3, and a 2-wide × 4-tall span holds 1 (axes
 * never transposed).
 */
class TilePinnedStripTest {

    private val profile = ProfileId("personal")
    private fun ref(id: String) = ShortcutRef("com.example", profile, id)
    private fun shortcut(id: String, label: String, rank: Int = 0) =
        LauncherShortcut(ref(id), label, rank)
    private fun binding(vararg ids: String) =
        TileBinding(ItemId("tile"), ids.map { ref(it) })

    // ── resolve ─────────────────────────────────────────────────────

    @Test
    fun resolve_emptyBindingOrShortcuts_resolvesEmpty() {
        assertTrue(TilePinnedStrip.resolve(binding(), listOf(shortcut("a", "A"))).isEmpty())
        assertTrue(TilePinnedStrip.resolve(binding("a"), emptyList()).isEmpty())
    }

    @Test
    fun resolve_preservesBindingOrder_notGatewayRank() {
        // Gateway rank order is c,a,b; binding pin order is a,b,c and wins.
        val shortcuts = listOf(
            shortcut("c", "Camera", 0),
            shortcut("a", "New post", 1),
            shortcut("b", "Search", 2),
        )
        val resolved = TilePinnedStrip.resolve(binding("a", "b", "c"), shortcuts)
        assertEquals(
            listOf(
                PinnedTileShortcut("a", "New post"),
                PinnedTileShortcut("b", "Search"),
                PinnedTileShortcut("c", "Camera"),
            ),
            resolved,
        )
    }

    @Test
    fun resolve_omitsStaleRefs_keepsOrder() {
        // "gone" has no live metadata: omitted from rendering (fails safe),
        // survivors keep binding order.
        val resolved = TilePinnedStrip.resolve(
            binding("a", "gone", "b"),
            listOf(shortcut("a", "A"), shortcut("b", "B")),
        )
        assertEquals(
            listOf(PinnedTileShortcut("a", "A"), PinnedTileShortcut("b", "B")),
            resolved,
        )
    }

    // ── arrange: capacity ───────────────────────────────────────────

    private fun resolved(vararg ids: String) =
        ids.map { PinnedTileShortcut(it, "Label $it") }

    @Test
    fun arrange_twoByTwo_showsAppPlusOne() {
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(2, 2),
            resolved("a", "b", "c"),
            isEditSelected = false,
        )
        assertEquals(1, arrangement.capacity)
        assertEquals(listOf(PinnedTileShortcut("a", "Label a")), arrangement.visible)
        assertEquals(2, arrangement.overflowCount)
        assertTrue(arrangement.showOverflowIndicator)
    }

    @Test
    fun arrange_fourWide_showsAppPlusThree() {
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(4, 2),
            resolved("a", "b", "c"),
            isEditSelected = false,
        )
        assertEquals(3, arrangement.capacity)
        assertEquals(3, arrangement.visible.size)
        assertEquals(0, arrangement.overflowCount)
        assertFalse(arrangement.showOverflowIndicator)
    }

    @Test
    fun arrange_twoWideFourTall_holdsOne_axesNeverTransposed() {
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(2, 4),
            resolved("a", "b", "c"),
            isEditSelected = false,
        )
        assertEquals(1, arrangement.capacity)
        assertEquals(listOf(PinnedTileShortcut("a", "Label a")), arrangement.visible)
        assertEquals(2, arrangement.overflowCount)
    }

    @Test
    fun arrange_narrowOneByTwo_holdsOne() {
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(1, 2),
            resolved("a", "b"),
            isEditSelected = false,
        )
        assertEquals(1, arrangement.capacity)
        assertEquals(listOf(PinnedTileShortcut("a", "Label a")), arrangement.visible)
        assertEquals(1, arrangement.overflowCount)
    }

    @Test
    fun arrange_oneByOne_hidesOverflowIndicator_announcesViaSemantics() {
        // Zero capacity with retained pins: nothing visible, no +N node (the
        // tile body announces the count instead).
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(1, 1),
            resolved("a"),
            isEditSelected = false,
        )
        assertEquals(0, arrangement.capacity)
        assertTrue(arrangement.visible.isEmpty())
        assertEquals(1, arrangement.overflowCount)
        assertFalse(arrangement.showOverflowIndicator)
    }

    // ── arrange: edit inset + resize ────────────────────────────────

    @Test
    fun arrange_editSelected_hidesIndicator_keepsVisible() {
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(2, 2),
            resolved("a", "b"),
            isEditSelected = true,
        )
        // The strip stays rendered under the floating edit controls; only the
        // +N node yields (count stays in body semantics).
        assertEquals(listOf(PinnedTileShortcut("a", "Label a")), arrangement.visible)
        assertEquals(1, arrangement.overflowCount)
        assertFalse(arrangement.showOverflowIndicator)
    }

    @Test
    fun arrange_resizeRevealsOverflow_withoutDeleting() {
        val full = resolved("a", "b", "c")
        val shrunk = TilePinnedStrip.arrange(LogicalSpan(2, 2), full, isEditSelected = false)
        assertEquals(1, shrunk.visible.size)
        assertEquals(2, shrunk.overflowCount)
        // Growing restores from the same retained list: nothing was deleted.
        val grown = TilePinnedStrip.arrange(LogicalSpan(4, 2), full, isEditSelected = false)
        assertEquals(
            listOf(
                PinnedTileShortcut("a", "Label a"),
                PinnedTileShortcut("b", "Label b"),
                PinnedTileShortcut("c", "Label c"),
            ),
            grown.visible,
        )
        assertEquals(0, grown.overflowCount)
    }

    @Test
    fun arrange_emptyList_rendersNothing() {
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(4, 2),
            emptyList(),
            isEditSelected = false,
        )
        assertTrue(arrangement.visible.isEmpty())
        assertEquals(0, arrangement.overflowCount)
        assertFalse(arrangement.showOverflowIndicator)
    }
}
