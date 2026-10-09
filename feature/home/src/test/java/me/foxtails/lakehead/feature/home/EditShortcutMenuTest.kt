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
        .beginResize(tiles.first(), tiles, gridMetrics)

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

    // ---- row cap + side space + capped height (Slice 1: never viewport-tall) ----

    @Test
    fun rowCap_isFiveAndAHalfRowsPlusPadding() {
        assertEquals(5.5, EDIT_SHORTCUT_MENU_MAX_VISIBLE_ROWS, 0.0)
        assertEquals((56 * 5.5 + 8).toInt(), editShortcutMenuRowCapPx(56, 8))
        assertEquals(56 + 8, editShortcutMenuOneRowPx(56, 8))
    }

    @Test
    fun capped_threeShortcutsPassThroughUnchanged() {
        val rowPx = 56
        val paddingPx = 8
        val natural = editShortcutMenuSizePx(800, 16, 320, rowPx, paddingPx, 3).heightPx
        assertEquals(
            natural,
            editShortcutMenuCappedHeightPx(
                naturalHeightPx = natural,
                rowCapPx = editShortcutMenuRowCapPx(rowPx, paddingPx),
                sideSpacePx = 800,
                oneRowPx = editShortcutMenuOneRowPx(rowPx, paddingPx),
            ),
        )
    }

    @Test
    fun capped_fortyShortcutsStopAtRowCap() {
        val rowPx = 56
        val paddingPx = 8
        val natural = editShortcutMenuSizePx(800, 16, 320, rowPx, paddingPx, 40).heightPx
        val rowCap = editShortcutMenuRowCapPx(rowPx, paddingPx)
        assertTrue("40-row natural $natural must exceed the row cap $rowCap", natural > rowCap)
        assertEquals(
            rowCap,
            editShortcutMenuCappedHeightPx(
                naturalHeightPx = natural,
                rowCapPx = rowCap,
                sideSpacePx = 10_000,
                oneRowPx = editShortcutMenuOneRowPx(rowPx, paddingPx),
            ),
        )
    }

    @Test
    fun overhangs_smallSpanReservesBothHandles() {
        // 1x1: remove top-right + resize bottom-right both spill half the 48dp control.
        assertEquals(EditShortcutMenuOverhangs(topPx = 24, bottomPx = 24), editShortcutMenuOverhangs(1, 1, 48))
        assertEquals(EditShortcutMenuOverhangs(topPx = 24, bottomPx = 24), editShortcutMenuOverhangs(1, 2, 48))
    }

    @Test
    fun overhangs_largeSpanReservesOnlyResizeBelow() {
        // 2x2+: remove centers inside the tile (no top spill); resize still spills below.
        assertEquals(EditShortcutMenuOverhangs(topPx = 0, bottomPx = 24), editShortcutMenuOverhangs(2, 2, 48))
        assertEquals(EditShortcutMenuOverhangs(topPx = 0, bottomPx = 24), editShortcutMenuOverhangs(4, 2, 48))
    }

    @Test
    fun sideSpace_tileNearTopGivesBelowSide() {
        val viewport = PixelRect(left = 0, top = 0, right = 400, bottom = 800)
        val tile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        // Above: 100 - 8 - 0 = 92; below: 800 - (180 + 8) = 612.
        assertEquals(612, editShortcutMenuSideSpacePx(viewport, tile, gapPx = 8))
    }

    @Test
    fun sideSpace_tileNearBottomGivesAboveSide() {
        val viewport = PixelRect(left = 0, top = 0, right = 400, bottom = 800)
        val tile = SignedPixelFrame(left = 40, top = 700, widthPx = 80, heightPx = 80)
        // Above: 700 - 8 - 0 = 692; below: 800 - (780 + 8) = 12.
        assertEquals(692, editShortcutMenuSideSpacePx(viewport, tile, gapPx = 8))
    }

    @Test
    fun sideSpace_overhangsReserveHandleRoom() {
        val viewport = PixelRect(left = 0, top = 0, right = 400, bottom = 800)
        val tile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        // Top overhang (remove button spill) eats the above side: 100 - 24 - 8 = 68.
        assertEquals(
            612,
            editShortcutMenuSideSpacePx(viewport, tile, gapPx = 8, topOverhangPx = 24),
        )
        val bottomTile = SignedPixelFrame(left = 40, top = 700, widthPx = 80, heightPx = 80)
        // Bottom overhang (resize handle spill) eats the below side: 800 - (780 + 24 + 8) < 0,
        // so the above side (700 - 8 = 692) still wins.
        assertEquals(
            692,
            editShortcutMenuSideSpacePx(viewport, bottomTile, gapPx = 8, bottomOverhangPx = 24),
        )
    }

    @Test
    fun sideSpace_tileCoveringViewportClampsToZero() {
        val viewport = PixelRect(left = 0, top = 0, right = 400, bottom = 800)
        val tile = SignedPixelFrame(left = 0, top = -100, widthPx = 400, heightPx = 1000)
        assertEquals(0, editShortcutMenuSideSpacePx(viewport, tile, gapPx = 8))
    }

    @Test
    fun sideSpace_pixelRectOverloadMatchesSignedFrame() {
        val viewport = PixelRect(left = 0, top = 0, right = 400, bottom = 800)
        val rectTile = PixelRect(left = 40, top = 100, right = 120, bottom = 180)
        val signedTile = SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80)
        assertEquals(
            editShortcutMenuSideSpacePx(viewport, signedTile, gapPx = 8),
            editShortcutMenuSideSpacePx(viewport, rectTile, gapPx = 8),
        )
    }

    @Test
    fun capped_tallTileWithUnderOneRowSpaceGivesOneRowFloor() {
        // A tall tile leaving 2px free: min() would collapse to 2, the floor keeps one row.
        assertEquals(
            64,
            editShortcutMenuCappedHeightPx(
                naturalHeightPx = 56 * 40 + 8,
                rowCapPx = 316,
                sideSpacePx = 2,
                oneRowPx = 64,
            ),
        )
    }

    @Test
    fun capped_noOverflowAtExtremes() {
        assertEquals(
            Int.MAX_VALUE,
            editShortcutMenuCappedHeightPx(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
        )
        assertEquals(
            316,
            editShortcutMenuCappedHeightPx(Int.MAX_VALUE, 316, 800, 64),
        )
        assertEquals(Int.MAX_VALUE, editShortcutMenuRowCapPx(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, editShortcutMenuOneRowPx(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun capped_negativeSideSpaceIsRejected() {
        editShortcutMenuCappedHeightPx(120, 316, -1, 64)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rowCap_nonPositiveRowIsRejected() {
        editShortcutMenuRowCapPx(0, 8)
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
    fun capped_nonPositiveNaturalIsRejected() {
        editShortcutMenuCappedHeightPx(0, 316, 800, 64)
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

    // ---- Slice 2: the frame never overlaps the tile ----

    private fun frameOverlapsTile(frame: PixelRect, tile: SignedPixelFrame): Boolean =
        frame.left < tile.right && tile.left < frame.right &&
            frame.top < tile.bottom && tile.top < frame.bottom

    private fun frameStaysInsideViewport(frame: PixelRect, viewport: PixelRect): Boolean =
        frame.left >= viewport.left && frame.top >= viewport.top &&
            frame.right <= viewport.right && frame.bottom <= viewport.bottom

    @Test
    fun frame_cappedMenuNeverOverlapsTile_firstRowLastRowAndMiddle() {
        val rowPx = 56
        val paddingPx = 8
        val gapPx = 8
        val rowCapPx = editShortcutMenuRowCapPx(rowPx, paddingPx)
        val oneRowPx = editShortcutMenuOneRowPx(rowPx, paddingPx)
        val natural = rowPx * 40 + paddingPx
        val tiles = listOf(
            SignedPixelFrame(left = 0, top = 0, widthPx = 100, heightPx = 100),
            SignedPixelFrame(left = 300, top = 0, widthPx = 100, heightPx = 100),
            SignedPixelFrame(left = 0, top = 700, widthPx = 100, heightPx = 100),
            SignedPixelFrame(left = 300, top = 700, widthPx = 100, heightPx = 100),
            SignedPixelFrame(left = 150, top = 350, widthPx = 100, heightPx = 100),
        )
        for (tile in tiles) {
            val sideSpace = editShortcutMenuSideSpacePx(viewport, tile, gapPx)
            assertTrue("tile $tile must leave a row of room, was $sideSpace", sideSpace >= oneRowPx)
            val capped = editShortcutMenuCappedHeightPx(natural, rowCapPx, sideSpace, oneRowPx)
            val frame = editShortcutMenuFrame(viewport, tile, FrameSize(120, capped), gapPx)
            assertFalse("menu $frame must not overlap tile $tile", frameOverlapsTile(frame, tile))
            assertTrue(
                "menu $frame for tile $tile must stay inside $viewport",
                frameStaysInsideViewport(frame, viewport),
            )
        }
    }

    @Test
    fun frame_cappedMenuNeverOverlapsTile_wideSpansAndScrolledViewport() {
        // 2×2 = 208px and 4×2 = 424px footprints on 100px cells with 8px gaps.
        val scrolled = PixelRect(left = 0, top = 500, right = 600, bottom = 1500)
        val rowPx = 56
        val paddingPx = 8
        val gapPx = 8
        val rowCapPx = editShortcutMenuRowCapPx(rowPx, paddingPx)
        val oneRowPx = editShortcutMenuOneRowPx(rowPx, paddingPx)
        val natural = rowPx * 40 + paddingPx
        val tiles = listOf(
            SignedPixelFrame(left = 0, top = 500, widthPx = 208, heightPx = 208),
            SignedPixelFrame(left = 88, top = 1292, widthPx = 424, heightPx = 208),
            SignedPixelFrame(left = 250, top = 950, widthPx = 100, heightPx = 100),
        )
        for (tile in tiles) {
            val sideSpace = editShortcutMenuSideSpacePx(scrolled, tile, gapPx)
            assertTrue("tile $tile must leave a row of room, was $sideSpace", sideSpace >= oneRowPx)
            val capped = editShortcutMenuCappedHeightPx(natural, rowCapPx, sideSpace, oneRowPx)
            val frame = editShortcutMenuFrame(scrolled, tile, FrameSize(120, capped), gapPx)
            assertFalse("menu $frame must not overlap tile $tile", frameOverlapsTile(frame, tile))
            assertTrue(
                "menu $frame for tile $tile must stay inside $scrolled",
                frameStaysInsideViewport(frame, scrolled),
            )
        }
    }

    @Test
    fun frame_withHandleOverhangs_clearsAvoidRect() {
        val rowPx = 56
        val paddingPx = 8
        val gapPx = 8
        val rowCapPx = editShortcutMenuRowCapPx(rowPx, paddingPx)
        val oneRowPx = editShortcutMenuOneRowPx(rowPx, paddingPx)
        val natural = rowPx * 40 + paddingPx
        val tiles = listOf(
            SignedPixelFrame(left = 40, top = 100, widthPx = 80, heightPx = 80),
            SignedPixelFrame(left = 40, top = 700, widthPx = 80, heightPx = 80),
        )
        for (tile in tiles) {
            val topOverhangPx = 24
            val bottomOverhangPx = 24
            val sideSpace = editShortcutMenuSideSpacePx(
                viewport, tile, gapPx, topOverhangPx, bottomOverhangPx,
            )
            assertTrue("tile $tile must leave a row of room, was $sideSpace", sideSpace >= oneRowPx)
            val capped = editShortcutMenuCappedHeightPx(natural, rowCapPx, sideSpace, oneRowPx)
            val frame = editShortcutMenuFrame(
                viewport, tile, FrameSize(120, capped), gapPx, topOverhangPx, bottomOverhangPx,
            )
            val avoid = SignedPixelFrame(
                left = tile.left,
                top = tile.top - topOverhangPx,
                widthPx = tile.widthPx,
                heightPx = tile.heightPx + topOverhangPx + bottomOverhangPx,
            )
            assertFalse("menu $frame must not overlap avoid-rect $avoid", frameOverlapsTile(frame, avoid))
            assertTrue(
                "menu $frame for tile $tile must stay inside $viewport",
                frameStaysInsideViewport(frame, viewport),
            )
        }
    }

    @Test
    fun frame_uncappedTallRequestShrinksToRoomierSide() {
        // Defensive path only: capped callers never send this (Slice 1 guarantees fit).
        // The 700px request fits the viewport but exceeds both sides (above 392, below 312),
        // so the fallback keeps the roomier above side at exactly that height.
        val tile = SignedPixelFrame(left = 40, top = 400, widthPx = 80, heightPx = 80)
        val frame = editShortcutMenuFrame(viewport, tile, FrameSize(120, 700), gapPx = 8)
        assertEquals(PixelRect(left = 40, top = 0, right = 160, bottom = 392), frame)
        assertFalse(frameOverlapsTile(frame, tile))
    }
}
