package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
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
 * B4 pure tests for the edit-mode shortcut menu: selection follow/dismiss
 * ([editShortcutMenuEntries]/[isEditShortcutMenuVisible]), the above-anchor
 * size policy ([editShortcutMenuSizePx]) and frame ([editShortcutMenuFrame]).
 *
 * Composition behavior (above-frame viewport, caller order, launch vs pin,
 * zero-activation on empty/Back, none → no node) is covered by the Compose
 * suite; `ShortcutOrderingTest`, `ShortcutSurfaceRowTest` and
 * `SwipeSurfacePlacementTest` stay untouched.
 */
class EditShortcutMenuTest {

    private val identity = AppIdentity(ProfileId("personal"), ComponentIdentity("com.app", "Main"))

    private fun tile(id: String) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(0, 0),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )

    private fun entry(id: String) = ShortcutSurfaceEntry(
        id = id,
        label = "Shortcut $id",
        isPinned = false,
    )

    private val tiles = listOf(tile("a"), tile("b"))
    private val entries = listOf(entry("s1"), entry("s2"))

    private val gridMetrics = HomeGridMetrics(
        columns = 6,
        cellWidthPx = 100f,
        cellHeightPx = 100f,
        columnGapPx = 0f,
        rowGapPx = 8f,
        originXPx = 0f,
        restOffsetYPx = 0f,
    )

    private fun movingState() = HomeEditState.Normal
        .enterOnLongPress(ItemId("a"))
        .beginMove(tiles.first(), tiles, gridMetrics, 0f, 0f)

    private fun resizingState() = HomeEditState.Normal
        .enterOnLongPress(ItemId("a"))
        .toggleResizeArmed()
        .beginResize(tiles.first(), gridMetrics)

    // ---- follow/dismiss selector ----

    @Test
    fun selectedTileWithShortcuts_showsEntriesInCallerOrder() {
        assertEquals(entries, editShortcutMenuEntries(ItemId("a"), tiles, entries))
    }

    @Test
    fun tapOtherTransfers_showsEntriesForNewSelection() {
        // No Normal round-trip: the selector is a pure function of the new id.
        assertEquals(entries, editShortcutMenuEntries(ItemId("b"), tiles, entries))
    }

    @Test
    fun nullSelection_showsNone() {
        assertTrue(editShortcutMenuEntries(null, tiles, entries).isEmpty())
    }

    @Test
    fun missingTile_showsNone() {
        // Removal/uninstall: the selected tile is gone from the loaded tiles.
        assertTrue(
            editShortcutMenuEntries(ItemId("gone"), tiles, entries).isEmpty(),
        )
        assertTrue(
            editShortcutMenuEntries(ItemId("a"), emptyList(), entries).isEmpty(),
        )
    }

    @Test
    fun zeroShortcuts_showsNoneNoEmptyBox() {
        assertTrue(editShortcutMenuEntries(ItemId("a"), tiles, emptyList()).isEmpty())
    }

    // ---- visibility (move/resize gate) ----

    @Test
    fun idleSessionWithSelection_isVisible() {
        val selected = HomeEditState.Normal.enterOnLongPress(ItemId("a"))
        assertTrue(isEditShortcutMenuVisible(selected.isDragging, selected.selectedTileId, tiles, entries))
    }

    @Test
    fun moveStart_dismissesWithZeroActivation() {
        val moving = movingState()
        assertTrue(moving.isDragging)
        assertFalse(isEditShortcutMenuVisible(moving.isDragging, moving.selectedTileId, tiles, entries))
    }

    @Test
    fun resizeStart_dismissesWithZeroActivation() {
        val resizing = resizingState()
        assertTrue(resizing.isDragging)
        assertFalse(isEditShortcutMenuVisible(resizing.isDragging, resizing.selectedTileId, tiles, entries))
    }

    @Test
    fun emptyTapBackNavigate_dismissesViaNullSelection() {
        // exit() yields Normal (no selectedTileId).
        val exited = HomeEditState.Normal.enterOnLongPress(ItemId("a")).exit()
        assertFalse(isEditShortcutMenuVisible(exited.isDragging, exited.selectedTileId, tiles, entries))
    }

    @Test
    fun removal_dismissesViaMissingTile() {
        assertFalse(isEditShortcutMenuVisible(false, ItemId("gone"), tiles, entries))
    }

    // ---- B1 selection scoping (owner gate) ----

    @Test
    fun scopedOwnerMatchingSelection_matches() {
        assertTrue(isSurfaceEntriesForSelection(ItemId("a"), ItemId("a")))
    }

    @Test
    fun staleOwnerAfterSwitch_hidesUntilReload() {
        // A→B tap-switch: A's rows must not render on B while B loads.
        assertFalse(isSurfaceEntriesForSelection(ItemId("a"), ItemId("b")))
    }

    @Test
    fun unscopedRows_keepLegacyBehavior() {
        // Null owner (previews/tests) never gates on the selection.
        assertTrue(isSurfaceEntriesForSelection(null, ItemId("a")))
        assertTrue(isSurfaceEntriesForSelection(null, null))
    }

    @Test
    fun scopedOwnerWithNullSelection_hides() {
        assertFalse(isSurfaceEntriesForSelection(ItemId("a"), null))
    }

    // ---- size policy ----

    @Test
    fun size_oneRowNaturalIsSingleRowPlusPadding() {
        assertEquals(56 + 8, editShortcutMenuSizePx(800, 16, 320, 56, 8, 1).heightPx)
    }

    @Test
    fun size_multiRowGrowsByWholeRows() {
        val one = editShortcutMenuSizePx(800, 16, 320, 56, 8, 1).heightPx
        val three = editShortcutMenuSizePx(800, 16, 320, 56, 8, 3).heightPx
        assertEquals(one + 2 * 56, three)
    }

    @Test
    fun size_widthIsMinOfAvailMinusMarginsAndMax() {
        assertEquals(
            FrameSize(320, 120),
            editShortcutMenuSizePx(800, 16, 320, 56, 8, 2),
        )
        assertEquals(
            FrameSize(200 - 32, 64),
            editShortcutMenuSizePx(200, 16, 320, 56, 8, 1),
        )
    }

    @Test
    fun size_heightIsRowsPlusPadding() {
        assertEquals(56 * 3 + 8, editShortcutMenuSizePx(800, 16, 320, 56, 8, 3).heightPx)
        // Empty floors at one row (callers still render no menu when empty).
        assertEquals(56 + 8, editShortcutMenuSizePx(800, 16, 320, 56, 8, 0).heightPx)
    }

    // ---- visible safe viewport + capped height ----

    @Test
    fun capped_fittingNaturalWrapsUnchanged() {
        assertEquals(120, editShortcutMenuCappedHeightPx(120, 800))
    }

    @Test
    fun capped_manyRowClampsToViewportMax() {
        assertEquals(800, editShortcutMenuCappedHeightPx(56 * 20 + 8, 800))
    }

    @Test
    fun viewport_scrolledTopIsNonzeroWindow() {
        assertEquals(
            PixelRect(left = 0, top = 500, right = 400, bottom = 1300),
            editShortcutMenuVisibleViewport(400, 500, 1300),
        )
    }

    @Test
    fun viewport_scrolledPlacementStaysInside() {
        val scrolled = editShortcutMenuVisibleViewport(400, 500, 1300)
        val tiles = listOf(
            SignedPixelFrame(left = 0, top = 500, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 320, top = 500, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 0, top = 1220, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 320, top = 1220, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 160, top = 900, widthPx = 80, heightPx = 80),
        )
        for (tile in tiles) {
            val frame = editShortcutMenuFrame(scrolled, tile, overlay, gapPx = 8)
            assertTrue(
                "menu $frame for tile $tile must stay inside $scrolled",
                frame.left >= scrolled.left && frame.top >= scrolled.top &&
                    frame.right <= scrolled.right && frame.bottom <= scrolled.bottom,
            )
        }
    }

    @Test
    fun viewport_edgeTilesStayInside() {
        val tiles = listOf(
            SignedPixelFrame(left = 0, top = 400, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 320, top = 400, widthPx = 80, heightPx = 80),
        )
        for (tile in tiles) {
            val frame = editShortcutMenuFrame(viewport, tile, overlay, gapPx = 8)
            assertTrue(
                "menu $frame for edge tile $tile must stay inside $viewport",
                frame.left >= viewport.left && frame.top >= viewport.top &&
                    frame.right <= viewport.right && frame.bottom <= viewport.bottom,
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun viewport_collapsedWindowIsRejected() {
        editShortcutMenuVisibleViewport(400, 500, 500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun capped_nonPositiveViewportIsRejected() {
        editShortcutMenuCappedHeightPx(120, 0)
    }

    // ---- above-anchor frame ----

    private val viewport = PixelRect(left = 0, top = 0, right = 400, bottom = 800)
    private val overlay = FrameSize(widthPx = 120, heightPx = 200)

    @Test
    fun midTile_prefersAboveLeftAligned() {
        val tile = SignedPixelFrame(left = 40, top = 400, widthPx = 80, heightPx = 80)
        assertEquals(
            PixelRect(left = 40, top = 200, right = 160, bottom = 400),
            editShortcutMenuFrame(viewport, tile, overlay),
        )
    }

    @Test
    fun midTile_gapSeparatesMenu() {
        val tile = SignedPixelFrame(left = 40, top = 400, widthPx = 80, heightPx = 80)
        assertEquals(
            PixelRect(left = 40, top = 188, right = 160, bottom = 388),
            editShortcutMenuFrame(viewport, tile, overlay, gapPx = 12),
        )
    }

    @Test
    fun firstRowTile_flipsBelow() {
        // No room above; the menu flips below the tile, left-aligned.
        val tile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        assertEquals(
            PixelRect(left = 40, top = 180, right = 160, bottom = 380),
            editShortcutMenuFrame(viewport, tile, overlay),
        )
    }

    @Test
    fun bottomRightCorner_clampsIntoViewport() {
        // Above fits vertically but not horizontally; below fits neither.
        // The frame keeps the valid above-top and clamps left into the corner.
        val tile = SignedPixelFrame(left = 300, top = 700, widthPx = 80, heightPx = 80)
        val frame = editShortcutMenuFrame(viewport, tile, overlay)
        assertEquals(PixelRect(left = 280, top = 500, right = 400, bottom = 700), frame)
    }

    @Test
    fun rightEdgeTile_clampsAboveIntoViewport() {
        // Above fits vertically but not horizontally; above wins, clamped left.
        val tile = SignedPixelFrame(left = 320, top = 400, widthPx = 80, heightPx = 80)
        val frame = editShortcutMenuFrame(viewport, tile, overlay)
        assertEquals(PixelRect(left = 280, top = 200, right = 400, bottom = 400), frame)
    }

    @Test
    fun placement_alwaysStaysInsideViewport() {
        val tiles = listOf(
            SignedPixelFrame(left = 0, top = 0, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 320, top = 0, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 0, top = 720, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 320, top = 720, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 160, top = 360, widthPx = 80, heightPx = 80),
        )
        for (tile in tiles) {
            val frame = editShortcutMenuFrame(viewport, tile, overlay, gapPx = 8)
            assertTrue(
                "menu $frame for tile $tile must stay inside $viewport",
                frame.left >= viewport.left && frame.top >= viewport.top &&
                    frame.right <= viewport.right && frame.bottom <= viewport.bottom,
            )
        }
    }

    @Test
    fun placement_neverMovesTilesNoReflow() {
        val tile = SignedPixelFrame(left = 40, top = 400, widthPx = 80, heightPx = 80)
        val neighbour = SignedPixelFrame(left = 200, top = 400, widthPx = 80, heightPx = 80)
        val tileBefore = tile.copy()
        val neighbourBefore = neighbour.copy()
        editShortcutMenuFrame(viewport, tile, overlay)
        assertEquals(tileBefore, tile)
        assertEquals(neighbourBefore, neighbour)
    }

    @Test
    fun pixelRectTile_overloadMatchesSignedFrame() {
        val rectTile = PixelRect(left = 40, top = 400, right = 120, bottom = 480)
        val signedTile = SignedPixelFrame(left = 40, top = 400, widthPx = 80, heightPx = 80)
        assertEquals(
            editShortcutMenuFrame(viewport, signedTile, overlay),
            editShortcutMenuFrame(viewport, rectTile, overlay),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedOverlay_isRejected() {
        editShortcutMenuFrame(viewport, SignedPixelFrame(40, 400, 80, 80), FrameSize(500, 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeGap_isRejected() {
        editShortcutMenuFrame(viewport, SignedPixelFrame(40, 400, 80, 80), overlay, gapPx = -1)
    }
}
