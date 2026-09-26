package me.foxtails.lakehead.core.grid

/**
 * Pure rest-origin and scroll-range calculation for a vertically-scrollable grid.
 *
 * When content fits within the viewport ("short"), the origin is biased so that free space is
 * distributed [bias] fraction below the content and `(1 − bias)` above — defaulting to 0.45 for a
 * slightly-above-centre rest position. When content exceeds the viewport ("tall"), the origin
 * anchors the minimum row at the viewport top and the full surplus becomes the scroll range.
 *
 * All arithmetic uses [Math.addExact], [Math.subtractExact] and [Math.multiplyExact] to surface
 * overflow as [ArithmeticException] rather than silent wrap.
 */
object GridRestOrigin {

    /**
     * Computes the rest origin and scroll range.
     *
     * @param viewportHeightPx visible viewport height in pixels; must be positive
     * @param viewportTopPx    pixel offset of the viewport top in page coordinates
     * @param contentHeightPx  total content height in pixels; must be non-negative
     * @param minRow           first visible row index; origin is relative to this row's top edge
     * @param cellStridePx     pixel height of one grid row; must be positive
     * @param bias             fraction of free space placed below content; must be in [0, 1]
     * @return a [GridRest] whose [GridRest.gridOriginY] is the pixel offset of row [minRow]'s top
     *         edge from the viewport top
     * @throws IllegalArgumentException if any precondition is violated
     * @throws ArithmeticException      if intermediate calculations overflow `Long`
     */
    fun rest(
        viewportHeightPx: Long,
        viewportTopPx: Long,
        contentHeightPx: Long,
        minRow: Long,
        cellStridePx: Long,
        bias: Double = 0.45,
    ): GridRest {
        require(viewportHeightPx > 0L) { "viewportHeightPx must be positive" }
        require(cellStridePx > 0L) { "cellStridePx must be positive" }
        require(contentHeightPx >= 0L) { "contentHeightPx must be non-negative" }
        require(bias in 0.0..1.0) { "bias must be in [0, 1]" }

        return if (contentHeightPx <= viewportHeightPx) {
            // Short branch: content fits; position with bias.
            val freeSpace = Math.subtractExact(viewportHeightPx, contentHeightPx)
            val origin =
                Math.subtractExact(
                    Math.addExact(viewportTopPx, Math.round(freeSpace * bias)),
                    Math.multiplyExact(minRow, cellStridePx),
                )
            GridRest(gridOriginY = origin, scrollRangePx = 0L, scrollable = false)
        } else {
            // Tall branch: content overflows; anchor minRow at viewport top.
            val scrollRange = Math.subtractExact(contentHeightPx, viewportHeightPx)
            val origin = Math.subtractExact(viewportTopPx, Math.multiplyExact(minRow, cellStridePx))
            GridRest(gridOriginY = origin, scrollRangePx = scrollRange, scrollable = true)
        }
    }
}