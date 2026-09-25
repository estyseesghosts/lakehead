package me.foxtails.lakehead.core.grid

/** Pure viewport-width adaptation for logical grid column counts. */
object GridViewportAdaptation {
    /**
     * Chooses columns using the exact formula
     * `max(1, floor((effectiveContentWidth + columnGapPx) / (minimumCellWidthPx + columnGapPx)))`,
     * where `effectiveContentWidth = viewportWidthPx - horizontalInsetPx - 2 * horizontalPaddingPx`.
     * Five columns is a normal-phone reference result, not a minimum; narrower viewports may return
     * fewer columns and wider viewports may return more, without an arbitrary maximum.
     *
     * Pixel values and resources are resolved by callers outside `core:grid`. The returned count
     * does not project, mutate, or otherwise depend on logical placements.
     *
     * @throws IllegalArgumentException if any input is invalid or effective content width is not
      * positive. If the effective width is positive but narrower than one minimum cell, the result
      * is one column.
     */
    fun columnsFor(
        viewportWidthPx: Int,
        horizontalInsetPx: Int = 0,
        horizontalPaddingPx: Int = 0,
        columnGapPx: Int = 0,
        minimumCellWidthPx: Int,
    ): GridColumns {
        require(viewportWidthPx > 0) { "viewportWidthPx must be positive" }
        require(horizontalInsetPx >= 0) { "horizontalInsetPx must be non-negative" }
        require(horizontalPaddingPx >= 0) { "horizontalPaddingPx must be non-negative" }
        require(columnGapPx >= 0) { "columnGapPx must be non-negative" }
        require(minimumCellWidthPx > 0) { "minimumCellWidthPx must be positive" }

        val effectiveContentWidth =
            viewportWidthPx.toLong() - horizontalInsetPx.toLong() -
                2L * horizontalPaddingPx.toLong()
        require(effectiveContentWidth > 0L) { "effective content width must be positive" }

        val rawColumns =
            (effectiveContentWidth + columnGapPx.toLong()) /
                (minimumCellWidthPx.toLong() + columnGapPx.toLong())
        return GridColumns(maxOf(1L, rawColumns).toInt())
    }
}
