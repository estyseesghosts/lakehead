package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ResizeDragMathTest {
    private val cellSize = 48f
    private val gap = 4f
    private val stride = cellSize + gap // 52f

    // Reference grid: six square 1x1 cells across, 4px gaps, caller-supplied
    // originX and rest offset. Scroll is applied once inside rawSpan.
    private fun metrics(originXPx: Float = 0f, restOffsetYPx: Float = 0f): HomeGridMetrics =
        HomeGridMetrics(
            columns = 6,
            cellWidthPx = 48f,
            cellHeightPx = 48f,
            columnGapPx = 4f,
            rowGapPx = 4f,
            originXPx = originXPx,
            restOffsetYPx = restOffsetYPx,
        )

    // ---- rawSpan ----

    @Test
    fun rawSpan_pointerAtOrigin_returns1x1() {
        // pointer at origin cell's top-left: span = 1x1
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 0f,
            pointerYPxViewport = 0f,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_oneCellRight_returns2x1() {
        // pointer at column 1's top-left: span = 2x1
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = stride,
            pointerYPxViewport = 0f,
        )
        assertEquals(LogicalSpan(2, 1), span)
    }

    @Test
    fun rawSpan_oneCellDown_returns1x2() {
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 0f,
            pointerYPxViewport = stride,
        )
        assertEquals(LogicalSpan(1, 2), span)
    }

    @Test
    fun rawSpan_diagonalOneCell_returns2x2() {
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = stride,
            pointerYPxViewport = stride,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    @Test
    fun rawSpan_subCellJitter_returns1x1() {
        // pointer still within the origin cell
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = cellSize - 1f,
            pointerYPxViewport = cellSize - 1f,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_nonZeroOrigin_offsetsCorrectly() {
        // origin at column=2, row=3; pointer at column=3, row=4 → span 2x2
        val origin = LogicalPosition(2, 3)
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = origin,
            pointerXPx = 2 * stride + stride,  // column 3
            pointerYPxViewport = 3 * stride + stride,  // row 4
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    @Test
    fun rawSpan_pointerBeforeOrigin_returns1x1() {
        // pointer before origin (negative delta) → clamped to 1x1
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = -10f,
            pointerYPxViewport = -20f,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun homeGridMetrics_zeroCellSize_throws() {
        // HomeGridMetrics rejects non-positive cell sizes, so rawSpan never sees
        // a zero cell (the retired rawSpan_zeroCellSize_returns1x1 contract).
        assertThrows(IllegalArgumentException::class.java) {
            HomeGridMetrics(
                columns = 6,
                cellWidthPx = 0f,
                cellHeightPx = 48f,
                columnGapPx = 4f,
                rowGapPx = 4f,
                originXPx = 0f,
                restOffsetYPx = 0f,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            HomeGridMetrics(
                columns = 6,
                cellWidthPx = 48f,
                cellHeightPx = 0f,
                columnGapPx = 4f,
                rowGapPx = 4f,
                originXPx = 0f,
                restOffsetYPx = 0f,
            )
        }
    }

    @Test
    fun rawSpan_threeCellsRight_returns4x1() {
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 3 * stride,
            pointerYPxViewport = 0f,
        )
        assertEquals(LogicalSpan(4, 1), span)
    }

    @Test
    fun rawSpan_absoluteRow5WithRest_pointerAtOriginTop_returns1x1() {
        // Absolute row: origin row 5, rest 26 → top = 26 + 5*52 = 286. Pointer exactly at
        // the origin top → 1x1.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(restOffsetYPx = 26f),
            originPosition = LogicalPosition(0, 5),
            pointerXPx = 0f,
            pointerYPxViewport = 26f + 5 * stride,
        )
        assertEquals(LogicalSpan(1, 1), span)
    }

    @Test
    fun rawSpan_absoluteRow5WithRest_oneStrideDown_returns1x2() {
        // Same origin (top = 286): pointer one stride below (y=338) → rows = 2.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(restOffsetYPx = 26f),
            originPosition = LogicalPosition(0, 5),
            pointerXPx = 0f,
            pointerYPxViewport = 26f + 5 * stride + stride,
        )
        assertEquals(LogicalSpan(1, 2), span)
    }

    @Test
    fun rawSpan_absoluteRow50WithRest_offsetsByRestOnly() {
        // Absolute row: origin (2,50), rest 26 → top = 26 + 50*52 = 2626; pointer one
        // stride down and one stride right of the origin top-left → 2x2.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(restOffsetYPx = 26f),
            originPosition = LogicalPosition(2, 50),
            pointerXPx = 2 * stride + stride,
            pointerYPxViewport = 26f + 50 * stride + stride,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    // ---- clampSpan (free resize) ----

    @Test
    fun clampSpan_previouslyUnreachableShapesPassThrough() {
        for (span in listOf(LogicalSpan(3, 1), LogicalSpan(4, 1), LogicalSpan(5, 1), LogicalSpan(1, 4), LogicalSpan(5, 5), LogicalSpan(2, 7))) {
            assertEquals(span, ResizeDragMath.clampSpan(span, columns = 5))
        }
    }

    @Test
    fun clampSpan_widthClampsToGridColumns() {
        assertEquals(LogicalSpan(5, 4), ResizeDragMath.clampSpan(LogicalSpan(6, 4), columns = 5))
        assertEquals(LogicalSpan(3, 2), ResizeDragMath.clampSpan(LogicalSpan(5, 2), columns = 3))
    }

    // ---- W3 gate: 2x2 → 3x2 drag path in a 5-column grid ----

    @Test
    fun rawSpan_twoStridesRightOneDown_returns3x2() {
        // W3 gate drag: from a 2x2 origin, pointer two strides right and one
        // stride down of the origin top-left → raw 3x2.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 2 * stride,
            pointerYPxViewport = stride,
        )
        assertEquals(LogicalSpan(3, 2), span)
    }

    @Test
    fun clamp_3x2In5Columns_stays3x2() {
        assertEquals(LogicalSpan(3, 2), ResizeDragMath.clampSpan(LogicalSpan(3, 2), columns = 5))
    }

    @Test
    fun rawThenClamp_gatePath_commits3x2() {
        // End-to-end W3 gate math: raw drag span clamps to the 3x2 target.
        val raw = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 2 * stride,
            pointerYPxViewport = stride,
        )
        assertEquals(LogicalSpan(3, 2), ResizeDragMath.clampSpan(raw, columns = 5))
    }

    // ---- B3 hardening: far-OOB raw, originX offset, rest-offset regression ----

    @Test
    fun rawSpan_farOutsideBottomRight_growsUnbounded() {
        // B3: the raw span is unclamped math — a pointer ten strides out yields
        // an 11x11 raw span. Clamping to the allowlist/columns and legality
        // stays with clampSpan + core:grid; rawSpan never clips.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 10 * stride,
            pointerYPxViewport = 10 * stride,
        )
        assertEquals(LogicalSpan(11, 11), span)
    }

    @Test
    fun rawSpan_farOutsideTopLeft_clampsTo1x1() {
        // B3: far out-of-bounds above/left of the origin still floors at 1x1 —
        // the minimum guarantee holds no matter how negative the delta is.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = -500f,
            pointerYPxViewport = -500f,
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
            metrics = metrics(originXPx = 13f),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 60f,
            pointerYPxViewport = 0f,
        )
        assertEquals(LogicalSpan(1, 1), span)
        // One stride right of the shifted origin left → 2 columns.
        val twoWide = ResizeDragMath.rawSpan(
            metrics = metrics(originXPx = 13f),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 13f + stride,
            pointerYPxViewport = 0f,
        )
        assertEquals(LogicalSpan(2, 1), twoWide)
    }

    @Test
    fun rawSpan_negativeRestOffset_regression() {
        // B3 rest-offset regression: tall content anchors above with a negative
        // rest. Origin (0,0), rest -20 → top = -20. Pointer y=32 gives
        // dy=52 → rows = 2. Under a rest-agnostic top (0) the same pointer
        // (dy=32) would read rows = 1 — the rest offset is load-bearing.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(restOffsetYPx = -20f),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 0f,
            pointerYPxViewport = 32f,
        )
        assertEquals(LogicalSpan(1, 2), span)
        // Same pointer with zero rest → still inside the origin cell.
        val withoutRest = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 0f,
            pointerYPxViewport = 32f,
        )
        assertEquals(LogicalSpan(1, 1), withoutRest)
    }

    @Test
    fun rawSpan_viewportScroll_restoresVerticalAndHorizontalAxes() {
        // Origin cell (2, 3) is at content (104, 156), while scrolling by
        // (20, 52) renders it at viewport (84, 104). One cell down/right
        // therefore still produces a 2x2 span after restoring the scroll.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(),
            originPosition = LogicalPosition(2, 3),
            pointerXPx = 84f + stride,
            pointerYPxViewport = 104f + stride,
            currentScrollXPx = 20f,
            currentScrollYPx = 52f,
        )
        assertEquals(LogicalSpan(2, 2), span)
    }

    @Test
    fun rawSpan_viewportScroll_withSignedNegativeOrigin() {
        // A negative rest offset remains meaningful after scroll conversion:
        // origin top is -20, and viewport y=-8 with scroll 40 is one stride down.
        val span = ResizeDragMath.rawSpan(
            metrics = metrics(restOffsetYPx = -20f),
            originPosition = LogicalPosition(0, 0),
            pointerXPx = 0f,
            pointerYPxViewport = -8f,
            currentScrollYPx = 40f,
        )
        assertEquals(LogicalSpan(1, 2), span)
    }

    @Test
    fun rawSpan_sameStrideOffsetFromDifferentOriginRows_yieldsIdenticalSpans() {
        // A pointer a whole number of strides from the origin top-left reads
        // the same span no matter which absolute row the origin sits in: the
        // rest offset shifts both origin and pointer bands equally.
        val spanFromRow5 = ResizeDragMath.rawSpan(
            metrics = metrics(restOffsetYPx = 26f),
            originPosition = LogicalPosition(2, 5),
            pointerXPx = 2 * stride + stride,
            pointerYPxViewport = 26f + 5 * stride + 2 * stride,
        )
        val spanFromRow50 = ResizeDragMath.rawSpan(
            metrics = metrics(restOffsetYPx = 26f),
            originPosition = LogicalPosition(2, 50),
            pointerXPx = 2 * stride + stride,
            pointerYPxViewport = 26f + 50 * stride + 2 * stride,
        )
        assertEquals(LogicalSpan(2, 3), spanFromRow5)
        assertEquals(spanFromRow5, spanFromRow50)
    }
}
