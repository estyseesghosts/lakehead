package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class ResizeDragMathTest {
    private val cellSize = 48f
    private val gap = 4f
    private val stride = cellSize + gap // 52f

    // ---- rawSpan ----

    @Test
    fun rawSpan_pointerAtOrigin_returns1x1() {
        // pointer at origin cell's top-left: span = 1x1
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_oneCellRight_returns2x1() {
        // pointer at column 1's top-left: span = 2x1
        val span = ResizeDragMath.rawSpan(
            pointerXPx = stride,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(2, 1), span)
    }

    @Test
    fun rawSpan_oneCellDown_returns1x2() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = stride,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 2), span)
    }

    @Test
    fun rawSpan_diagonalOneCell_returns2x2() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = stride,
            pointerYPx = stride,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    @Test
    fun rawSpan_subCellJitter_returns1x1() {
        // pointer still within the origin cell
        val span = ResizeDragMath.rawSpan(
            pointerXPx = cellSize - 1f,
            pointerYPx = cellSize - 1f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_nonZeroOrigin_offsetsCorrectly() {
        // origin at column=2, row=3; pointer at column=3, row=4 → span 2x2
        val origin = LogicalPosition(2, 3)
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 2 * stride + stride,  // column 3
            pointerYPx = 3 * stride + stride,  // row 4
            originPosition = origin,
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    @Test
    fun rawSpan_pointerBeforeOrigin_returns1x1() {
        // pointer before origin (negative delta) → clamped to 1x1
        val span = ResizeDragMath.rawSpan(
            pointerXPx = -10f,
            pointerYPx = -20f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_zeroCellSize_returns1x1() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 100f,
            pointerYPx = 100f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = 0f,
            columnGapPx = 0f,
            rowGapPx = 0f,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_threeCellsRight_returns4x1() {
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 3 * stride,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(4, 1), span)
    }

    @Test
    fun rawSpan_nonZeroRowOriginAndRest_pointerAtOriginTop_returns1x1() {
        // Signed frame: originTop = rest + (originRow - rowOrigin) * stride.
        // origin row 3, rowOrigin 1, rest 26f → top = 26 + 2*52 = 130.
        // Pointer exactly at the origin top → 1x1.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = 130f,
            originPosition = LogicalPosition(0, 3),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
            rowOrigin = 1L,
            originYPxRestPx = 26f,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_nonZeroRowOriginAndRest_oneStrideDown_returns1x2() {
        // Same origin (top = 130): pointer one stride below (y=182) → rows = 2.
        // With the old row*stride top (156) the delta would be 26 → rows = 1.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = 182f,
            originPosition = LogicalPosition(0, 3),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
            rowOrigin = 1L,
            originYPxRestPx = 26f,
        )
        assertEquals(LogicalSpan(1, 2), span)
    }

    @Test
    fun rawSpan_originAtRowOriginWithRest_offsetsByRestOnly() {
        // origin row == rowOrigin: top = rest. origin (2,1), rowOrigin 1,
        // rest 26f → top = 26; pointer at y=26+52=78 → dy=52 → rows = 2,
        // columns: pointer x one stride right of column-2 left → 2.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 2 * stride + stride,
            pointerYPx = 78f,
            originPosition = LogicalPosition(2, 1),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
            rowOrigin = 1L,
            originYPxRestPx = 26f,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    // ---- RenderableSpans.quantize ----

    @Test
    fun quantize_exactMatch_returnsSame() {
        // All 14 allowlist entries must round-trip exactly.
        val expected = listOf(
            LogicalSpan(1, 1), LogicalSpan(2, 1), LogicalSpan(1, 2), LogicalSpan(2, 2),
            LogicalSpan(1, 3), LogicalSpan(2, 3), LogicalSpan(3, 2), LogicalSpan(4, 2),
            LogicalSpan(5, 2), LogicalSpan(3, 3), LogicalSpan(4, 3), LogicalSpan(5, 3),
            LogicalSpan(4, 4), LogicalSpan(5, 4),
        )
        for (entry in expected) {
            assertEquals("exact match for ${entry.columns}x${entry.rows}", entry, RenderableSpans.quantize(entry))
        }
    }

    @Test
    fun quantize_3x1_nearestIs2x1() {
        // distance to 2x1 = 1; distance to 4x2 = 1+1=2; nearest is 2x1
        assertEquals(LogicalSpan(2, 1), RenderableSpans.quantize(LogicalSpan(3, 1)))
    }

    @Test
    fun quantize_2x4_nearestIs2x3_smallerWins() {
        // distance to 2x3 = |0|+|1| = 1; distance to 1x3 = |1|+|1| = 2
        // But 2x3 is exact distance 1, so no tie — just the closest.
        // Verify it does NOT round up to 3x3 (distance 1+1=2).
        assertEquals(LogicalSpan(2, 3), RenderableSpans.quantize(LogicalSpan(2, 4)))
    }

    @Test
    fun quantize_1x1_clampedFromBelow() {
        // Anything below smallest → smallest (1x1 is the minimum)
        assertEquals(LogicalSpan(1, 1), RenderableSpans.quantize(LogicalSpan(1, 1)))
    }

    @Test
    fun quantize_6x5_nearestIs5x4() {
        // distance to 5x4 = |1|+|1| = 2; distance to 4x4 = |2|+|1| = 3; nearest is 5x4
        assertEquals(LogicalSpan(5, 4), RenderableSpans.quantize(LogicalSpan(6, 5)))
    }

    // ---- column-filtered quantize ----

    @Test
    fun quantize_columnsFiltersOutWiderEntries() {
        // With columns=4, entries with columns > 4 (i.e. 5x2, 5x3, 5x4) are excluded.
        // 5x3 → nearest fitting entry: 4x3 distance |1|+|0| = 1.
        assertEquals(LogicalSpan(4, 3), RenderableSpans.quantize(LogicalSpan(5, 3), columns = 4))
    }

    @Test
    fun quantize_columnsNarrowViewport_5x2FilteredTo4x2() {
        // columns=4: 5x2 → nearest fitting: 4x2 distance 1, 3x2 distance 2. Picks 4x2.
        assertEquals(LogicalSpan(4, 2), RenderableSpans.quantize(LogicalSpan(5, 2), columns = 4))
    }

    @Test
    fun quantize_columns3_excludes4wideAnd5wide() {
        // columns=3: max allowed width = 3. 4x3 → nearest fitting: 3x3 distance 1.
        assertEquals(LogicalSpan(3, 3), RenderableSpans.quantize(LogicalSpan(4, 3), columns = 3))
    }

    // ---- W3 gate: 2x2 → 3x2 drag path in a 5-column grid ----

    @Test
    fun rawSpan_twoStridesRightOneDown_returns3x2() {
        // W3 gate drag: from a 2x2 origin, pointer two strides right and one
        // stride down of the origin top-left → raw 3x2.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 2 * stride,
            pointerYPx = stride,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(3, 2), span)
    }

    @Test
    fun quantize_3x2In5Columns_stays3x2() {
        // The gate target survives quantization in the 5-column reference grid.
        assertEquals(LogicalSpan(3, 2), RenderableSpans.quantize(LogicalSpan(3, 2), columns = 5))
    }

    @Test
    fun rawThenQuantize_gatePath_commits3x2() {
        // End-to-end W3 gate math: raw drag span quantizes to the 3x2 target.
        val raw = ResizeDragMath.rawSpan(
            pointerXPx = 2 * stride,
            pointerYPx = stride,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(3, 2), RenderableSpans.quantize(raw, columns = 5))
    }

    // ---- B3 hardening: far-OOB raw, originX offset, rest-offset regression ----

    @Test
    fun rawSpan_farOutsideBottomRight_growsUnbounded() {
        // B3: the raw span is unclamped math — a pointer ten strides out yields
        // an 11x11 raw span. Clamping to the allowlist/columns and legality
        // stays with RenderableSpans.quantize + core:grid; rawSpan never clips.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 10 * stride,
            pointerYPx = 10 * stride,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(11, 11), span)
    }

    @Test
    fun rawSpan_farOutsideTopLeft_clampsTo1x1() {
        // B3: far out-of-bounds above/left of the origin still floors at 1x1 —
        // the minimum guarantee holds no matter how negative the delta is.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = -500f,
            pointerYPx = -500f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_centeredOriginX_offsetsPointerCorrectly() {
        // B3: the caller-centered originX shifts the origin cell's left edge.
        // originX=13 → origin left = 13. Pointer at x=60 is still inside the
        // origin cell (dx=47 < stride 52) → 1 column. Without the offset the
        // same pointer (dx=60) would read as 2 columns — the offset matters.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 60f,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
            originXPx = 13f,
        )
        assertEquals(LogicalSpan(1, 1), span)
        // One stride right of the shifted origin left → 2 columns.
        val twoWide = ResizeDragMath.rawSpan(
            pointerXPx = 13f + stride,
            pointerYPx = 0f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
            originXPx = 13f,
        )
        assertEquals(LogicalSpan(2, 1), twoWide)
    }

    @Test
    fun rawSpan_negativeRestOffset_regression() {
        // B3 rest-offset regression: tall content anchors above with a negative
        // rest. Origin (0,0), rowOrigin 0, rest -20 → top = -20. Pointer y=32
        // gives dy=52 → rows = 2. Under the old rest-agnostic top (0) the same
        // pointer (dy=32) would read rows = 1 — the rest offset is load-bearing.
        val span = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = 32f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
            rowOrigin = 0L,
            originYPxRestPx = -20f,
        )
        assertEquals(LogicalSpan(1, 2), span)
        // Same pointer with zero rest → still inside the origin cell.
        val withoutRest = ResizeDragMath.rawSpan(
            pointerXPx = 0f,
            pointerYPx = 32f,
            originPosition = LogicalPosition(0, 0),
            cellSizePx = cellSize,
            columnGapPx = gap,
            rowGapPx = gap,
        )
        assertEquals(LogicalSpan(1, 1), withoutRest)
    }
}