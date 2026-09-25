package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridColumns

/**
 * Pixel geometry for a square-cell launcher grid with any positive visual column count.
 *
 * This type is pure launcher geometry over [Int] pixels: it has no Android or Compose
 * dependency, performs no grid legality checks, no column-count adaptation and no
 * packing. The visual column count is supplied by the caller, typically the count
 * returned by `core:grid` `GridViewportAdaptation.columnsFor`; this type never
 * re-derives, clamps, or floors that count. Placement legality, packing and width
 * adaptation belong to `core:grid`; this type only converts a viewport width and a
 * supplied column count into square cell and gap pixels and measures span extents.
 *
 * Five is only the normal-phone reference arrangement (see
 * [NORMAL_PHONE_REFERENCE_COLUMN_COUNT], which matches
 * [GridColumns.NORMAL_PHONE_REFERENCE_COUNT]); it is never a lower bound. Narrower
 * viewports may use fewer positive columns and wider viewports may use more.
 *
 * @property columnCount number of visual columns. Always positive; supplied by the
 * caller (for example `GridColumns.count`), never clamped here.
 * @property cellSizePx side length of one square 1x1 cell, in pixels. Always positive.
 * @property columnGapPx horizontal gap between adjacent columns, in pixels. Non-negative;
 * zero means adjacent columns touch.
 * @property rowGapPx vertical gap between adjacent rows, in pixels. Non-negative;
 * zero means adjacent rows touch.
 */
data class SquareCellGeometry(
    val columnCount: Int,
    val cellSizePx: Int,
    val columnGapPx: Int,
    val rowGapPx: Int,
) {
    init {
        require(columnCount > 0) { "columnCount must be positive, was $columnCount" }
        require(cellSizePx > 0) { "cellSizePx must be positive, was $cellSizePx" }
        require(columnGapPx >= 0) { "columnGapPx must be non-negative, was $columnGapPx" }
        require(rowGapPx >= 0) { "rowGapPx must be non-negative, was $rowGapPx" }
    }

    /**
     * Pixel extent of a tile spanning [columns] by [rows] cells, including the gaps
     * between the spanned cells.
     *
     * Width is `columns * cellSizePx + (columns - 1) * columnGapPx`; height is
     * `rows * cellSizePx + (rows - 1) * rowGapPx`. Both cell counts must be positive.
     * Both extents are computed with [Long] intermediates and must fit a positive [Int];
     * out-of-range results throw [IllegalArgumentException] instead of silently
     * overflowing. This function measures pixels only: whether the span is a supported
     * or legal grid span is decided by `core:grid`.
     */
    fun spanExtentPx(columns: Int, rows: Int): SpanExtent {
        require(columns > 0) { "columns must be positive, was $columns" }
        require(rows > 0) { "rows must be positive, was $rows" }
        val widthLong = columns.toLong() * cellSizePx + (columns - 1).toLong() * columnGapPx
        require(widthLong in 1..Int.MAX_VALUE.toLong()) {
            "span width for columns=$columns overflows Int: $widthLong"
        }
        val heightLong = rows.toLong() * cellSizePx + (rows - 1).toLong() * rowGapPx
        require(heightLong in 1..Int.MAX_VALUE.toLong()) {
            "span height for rows=$rows overflows Int: $heightLong"
        }
        return SpanExtent(
            widthPx = widthLong.toInt(),
            heightPx = heightLong.toInt(),
        )
    }

    /** Pixel width/height of a spanned tile. Both values are always positive. */
    data class SpanExtent(
        val widthPx: Int,
        val heightPx: Int,
    ) {
        init {
            require(widthPx > 0) { "widthPx must be positive, was $widthPx" }
            require(heightPx > 0) { "heightPx must be positive, was $heightPx" }
        }
    }

    companion object {
        /**
         * Normal-phone reference column count. This is a reference arrangement only,
         * never a lower bound: narrower viewports may use fewer columns.
         */
        const val NORMAL_PHONE_REFERENCE_COLUMN_COUNT: Int =
            GridColumns.NORMAL_PHONE_REFERENCE_COUNT

        /**
         * Derives square-cell geometry from an available viewport width and the
         * caller-supplied visual column count.
         *
         * Rule: `cellSizePx = (availableWidthPx - 2 * horizontalPaddingPx -
         * (columns.count - 1) * columnGapPx) / columns.count`, using flooring integer
         * division. The count is used exactly as supplied by `core:grid`
         * `GridViewportAdaptation.columnsFor`; it is never clamped to the reference
         * count.
         *
         * Padding convention: [horizontalPaddingPx] is the per-side inset applied to
         * both the left and the right edge, so the total horizontal inset subtracted
         * from [availableWidthPx] is `2 * horizontalPaddingPx`. The content width between
         * the insets holds exactly `columns.count` cells separated by
         * `columns.count - 1` column gaps.
         *
         * Floor remainder: when the gap-adjusted content width is not divisible by
         * `columns.count`, integer division floors and the leftover
         * `0..columns.count - 1` pixels are left undistributed as trailing slack. Cells
         * are never stretched or rounded up to absorb the remainder, so they stay
         * square and equal. The remainder split itself is caller-centered: callers
         * derive a horizontal origin (column 0 left edge) that splits the leftover
         * evenly so left/right slack stays equal within 1px; this type keeps the
         * geometry formula unchanged and performs no centering.
         *
         * [availableWidthPx] must be positive while [horizontalPaddingPx], [columnGapPx]
         * and [rowGapPx] must be non-negative; the derived cell must be positive.
         * Otherwise [IllegalArgumentException] is thrown. [rowGapPx] defaults to
         * [columnGapPx], keeping cells and gaps uniform unless a distinct row gap
         * is given.
         *
         * Zero padding and zero gaps are valid: they describe edge-to-edge geometry with
         * touching cells. [availableWidthPx] must stay positive, and the derived cell
         * must fit a positive [Int]; the derivation uses [Long] intermediates so large
         * inputs are rejected instead of silently overflowing.
         */
        fun fromViewport(
            availableWidthPx: Int,
            horizontalPaddingPx: Int,
            columnGapPx: Int,
            rowGapPx: Int = columnGapPx,
            columns: GridColumns,
        ): SquareCellGeometry = fromViewport(
            availableWidthPx = availableWidthPx,
            horizontalPaddingPx = horizontalPaddingPx,
            columnGapPx = columnGapPx,
            rowGapPx = rowGapPx,
            columnCount = columns.count,
        )

        /**
         * Derives square-cell geometry from an available viewport width and an explicit
         * positive [columnCount].
         *
         * This overload exists for callers that already hold a validated positive count;
         * it applies the same rule as [fromViewport] with a [GridColumns] input and
         * likewise never clamps the count to the reference arrangement.
         */
        fun fromViewport(
            availableWidthPx: Int,
            horizontalPaddingPx: Int,
            columnGapPx: Int,
            rowGapPx: Int = columnGapPx,
            columnCount: Int,
        ): SquareCellGeometry {
            require(availableWidthPx > 0) {
                "availableWidthPx must be positive, was $availableWidthPx"
            }
            require(horizontalPaddingPx >= 0) {
                "horizontalPaddingPx must be non-negative, was $horizontalPaddingPx"
            }
            require(columnGapPx >= 0) { "columnGapPx must be non-negative, was $columnGapPx" }
            require(rowGapPx >= 0) { "rowGapPx must be non-negative, was $rowGapPx" }
            require(columnCount > 0) { "columnCount must be positive, was $columnCount" }
            val contentWidthPx = availableWidthPx.toLong() - 2L * horizontalPaddingPx -
                (columnCount - 1).toLong() * columnGapPx
            val cellSizeLong = contentWidthPx / columnCount
            require(cellSizeLong in 1..Int.MAX_VALUE.toLong()) {
                "availableWidthPx=$availableWidthPx with horizontalPaddingPx=$horizontalPaddingPx " +
                    "and columnGapPx=$columnGapPx leaves no positive $columnCount-column cell " +
                    "fitting Int (content=$contentWidthPx)"
            }
            return SquareCellGeometry(
                columnCount = columnCount,
                cellSizePx = cellSizeLong.toInt(),
                columnGapPx = columnGapPx,
                rowGapPx = rowGapPx,
            )
        }
    }
}
