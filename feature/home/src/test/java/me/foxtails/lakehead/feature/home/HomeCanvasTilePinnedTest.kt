package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.PinnedTileShortcut
import me.foxtails.lakehead.core.designsystem.pinnedOverflowDescription
import me.foxtails.lakehead.core.designsystem.pinnedShortcutDescription
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry
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
 * D1 regression coverage for the tile pinned-shortcut strip in one place.
 *
 * Exercises the production join + window ([TilePinnedStrip.resolve]/[arrange])
 * against the `core:grid` capacity/layout contract ([TileShortcutPolicy] and
 * [ShortcutStripGeometry]) without touching production, app wiring, or
 * B1/B2/B3 menu/persistence logic:
 *
 * - per-tile isolation (two bindings over shared gateway metadata stay distinct)
 * - capacity/overflow (`2×2` → app + 1, `4×2` → app + 3)
 * - centered horizontal/square group + upward vertical strip
 * - app/shortcut accessible distinction
 * - no-overlap with edit controls (edit-selected yields the `+N` node)
 * - resize recompute (shrink preserves overflow, grow reveals)
 */
class HomeCanvasTilePinnedTest {

    private val profile = ProfileId("personal")

    private fun ref(id: String) = ShortcutRef("com.example", profile, id)

    private fun shortcut(id: String, label: String, rank: Int = 0) =
        LauncherShortcut(ref(id), label, rank)

    private fun gateway() = listOf(
        shortcut("new-post", "New post", 0),
        shortcut("search", "Search", 1),
        shortcut("camera", "Camera", 2),
    )

    // ── per-tile isolation ────────────────────────────────────────────

    @Test
    fun resolve_perTileBindings_stayIsolatedOverSharedMetadata() {
        val live = gateway()
        val tileA = TileBinding(ItemId("tile-a"), listOf(ref("new-post"), ref("search")))
        val tileB = TileBinding(ItemId("tile-b"), listOf(ref("search")))

        val resolvedA = TilePinnedStrip.resolve(tileA, live)
        val resolvedB = TilePinnedStrip.resolve(tileB, live)

        assertEquals(
            listOf(
                PinnedTileShortcut("new-post", "New post"),
                PinnedTileShortcut("search", "Search"),
            ),
            resolvedA,
        )
        assertEquals(listOf(PinnedTileShortcut("search", "Search")), resolvedB)

        // Policy projection agrees per binding: tile A pins new-post, tile B does not.
        val entriesA = TileShortcutPolicy.entries(live, tileA, LogicalSpan(4, 2))
        val entriesB = TileShortcutPolicy.entries(live, tileB, LogicalSpan(4, 2))
        assertTrue(entriesA.first { it.id == "new-post" }.isPinned)
        assertFalse(entriesB.first { it.id == "new-post" }.isPinned)
        assertTrue(entriesA.first { it.id == "search" }.isPinned)
        assertTrue(entriesB.first { it.id == "search" }.isPinned)
    }

    // ── capacity / overflow ───────────────────────────────────────────

    @Test
    fun arrange_twoByTwo_showsAppPlusOne_withOverflowIndicator() {
        val resolved = TilePinnedStrip.resolve(
            TileBinding(ItemId("tile"), listOf(ref("new-post"), ref("search"), ref("camera"))),
            gateway(),
        )
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(2, 2),
            resolved,
            isEditSelected = false,
        )

        assertEquals(1, ShortcutStripGeometry.maxSlots(2, 2))
        assertEquals(1, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(2, 2)))
        assertEquals(1, arrangement.capacity)
        assertEquals(listOf(PinnedTileShortcut("new-post", "New post")), arrangement.visible)
        assertEquals(2, arrangement.overflowCount)
        assertTrue(arrangement.showOverflowIndicator)
        assertEquals("2 more shortcuts", pinnedOverflowDescription(arrangement.overflowCount))
    }

    @Test
    fun arrange_fourWide_showsAppPlusThree_withoutOverflowIndicator() {
        val resolved = TilePinnedStrip.resolve(
            TileBinding(ItemId("tile"), listOf(ref("new-post"), ref("search"), ref("camera"))),
            gateway(),
        )
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(4, 2),
            resolved,
            isEditSelected = false,
        )

        assertEquals(3, ShortcutStripGeometry.maxSlots(4, 2))
        assertEquals(3, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(4, 2)))
        assertEquals(3, arrangement.capacity)
        assertEquals(resolved, arrangement.visible)
        assertEquals(0, arrangement.overflowCount)
        assertFalse(arrangement.showOverflowIndicator)
    }

    // ── centered horizontal/square + upward vertical ──────────────────

    @Test
    fun geometry_horizontalAndSquare_midAlignsGroup_neverRightAligned() {
        for (span in listOf(GridSpan(4, 2), GridSpan(2, 2))) {
            assertEquals(
                ShortcutStripGeometry.PinnedStripOrientation.HORIZONTAL,
                ShortcutStripGeometry.orientationFor(span),
            )
            assertEquals(
                ShortcutStripGeometry.StripDirection.LEFTWARD,
                ShortcutStripGeometry.directionFor(span),
            )
            assertEquals(
                ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT,
                ShortcutStripGeometry.anchorFor(span),
            )
        }

        // App + 1 visible shortcut = 2 slots: group = extent + 1 pitch, mid-aligned.
        val group = ShortcutStripGeometry.stripGroupExtentPx(
            extentPx = 40L,
            pitchPx = 64L,
            slotCount = 2,
        )
        assertEquals(104L, group)
        assertEquals(448L, ShortcutStripGeometry.centeredGroupStartPx(1000L, group))
        // Recompute from the measured edge on every resize: a wider tile
        // re-centers the same group instead of caching the offset.
        assertEquals(548L, ShortcutStripGeometry.centeredGroupStartPx(1200L, group))
    }

    @Test
    fun geometry_verticalStrip_runsUpwardFromBottomRight() {
        for (span in listOf(GridSpan(1, 2), GridSpan(1, 3))) {
            assertEquals(
                ShortcutStripGeometry.PinnedStripOrientation.VERTICAL,
                ShortcutStripGeometry.orientationFor(span),
            )
            assertEquals(
                ShortcutStripGeometry.StripDirection.UPWARD,
                ShortcutStripGeometry.directionFor(span),
            )
            assertEquals(
                ShortcutStripGeometry.ShortcutAnchor.BOTTOM_CENTER,
                ShortcutStripGeometry.anchorFor(span),
            )
        }

        // Successive slots move away from the bottom anchor by one pitch.
        val pitch = ShortcutStripGeometry.slotPitchPx(cellPx = 100L, gapPx = 8L)
        assertEquals(1000L, ShortcutStripGeometry.slotCenterPx(1000L, 0, pitch))
        assertEquals(1000L - pitch, ShortcutStripGeometry.slotCenterPx(1000L, 1, pitch))
        assertEquals(1000L - 2 * pitch, ShortcutStripGeometry.slotCenterPx(1000L, 2, pitch))

        // Vertical capacity follows rows - 1: a 1×3 tile holds the app + 2.
        val resolved = TilePinnedStrip.resolve(
            TileBinding(ItemId("tile"), listOf(ref("new-post"), ref("search"), ref("camera"))),
            gateway(),
        )
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(1, 3),
            resolved,
            isEditSelected = false,
        )
        assertEquals(2, TileShortcutPolicy.maxShortcutSlots(LogicalSpan(1, 3)))
        assertEquals(2, arrangement.capacity)
        assertEquals(2, arrangement.visible.size)
        assertEquals(1, arrangement.overflowCount)
    }

    // ── app / shortcut distinction ────────────────────────────────────

    @Test
    fun shortcutGlyph_accessibleName_staysDistinctFromAppLabel() {
        // App tiles announce the raw label; shortcut glyphs carry the
        // ", shortcut" suffix so TalkBack never confuses the two.
        assertEquals("Search, shortcut", pinnedShortcutDescription("Search"))
        assertEquals("New post, shortcut", pinnedShortcutDescription("New post"))

        val resolved = TilePinnedStrip.resolve(
            TileBinding(ItemId("tile"), listOf(ref("search"))),
            gateway(),
        )
        assertEquals("Search", resolved.single().label)
        assertEquals("Search, shortcut", pinnedShortcutDescription(resolved.single().label))
    }

    // ── no overlap with edit controls ─────────────────────────────────

    @Test
    fun arrange_editSelected_yieldsOverflowNode_keepsStrip() {
        val resolved = TilePinnedStrip.resolve(
            TileBinding(ItemId("tile"), listOf(ref("new-post"), ref("search"))),
            gateway(),
        )
        val idle = TilePinnedStrip.arrange(LogicalSpan(2, 2), resolved, isEditSelected = false)
        val editing = TilePinnedStrip.arrange(LogicalSpan(2, 2), resolved, isEditSelected = true)

        // The floating remove/resize controls sit above the strip: the strip
        // stays rendered under them while the +N node yields (the count stays
        // announced through the tile body instead).
        assertEquals(idle.visible, editing.visible)
        assertEquals(idle.overflowCount, editing.overflowCount)
        assertTrue(idle.showOverflowIndicator)
        assertFalse(editing.showOverflowIndicator)
    }

    @Test
    fun arrange_zeroCapacity_neverShowsOverflowNode() {
        val resolved = TilePinnedStrip.resolve(
            TileBinding(ItemId("tile"), listOf(ref("new-post"))),
            gateway(),
        )
        val arrangement = TilePinnedStrip.arrange(
            LogicalSpan(1, 1),
            resolved,
            isEditSelected = false,
        )

        assertEquals(0, arrangement.capacity)
        assertTrue(arrangement.visible.isEmpty())
        assertEquals(1, arrangement.overflowCount)
        assertFalse(arrangement.showOverflowIndicator)
    }

    // ── resize recompute ──────────────────────────────────────────────

    @Test
    fun arrange_resize_shrinkPreservesOverflow_growReveals() {
        val binding = TileBinding(
            ItemId("tile"),
            listOf(ref("new-post"), ref("search"), ref("camera")),
        )
        val resolved = TilePinnedStrip.resolve(binding, gateway())

        val grown = TilePinnedStrip.arrange(LogicalSpan(4, 2), resolved, isEditSelected = false)
        assertEquals(3, grown.visible.size)
        assertEquals(0, grown.overflowCount)

        // Shrinking only moves items into overflow: the binding retains all pins.
        val shrunk = TilePinnedStrip.arrange(LogicalSpan(2, 2), resolved, isEditSelected = false)
        assertEquals(1, shrunk.visible.size)
        assertEquals(2, shrunk.overflowCount)
        assertEquals(3, binding.pinnedShortcuts.size)
        assertEquals(3, resolved.size)

        // Growing restores from the same retained list: nothing was deleted.
        val regrown = TilePinnedStrip.arrange(LogicalSpan(4, 2), resolved, isEditSelected = false)
        assertEquals(resolved, regrown.visible)
        assertEquals(0, regrown.overflowCount)
    }
}
