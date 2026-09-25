package me.foxtails.lakehead.core.grid

/** Pure vertical extent measurement, independent of placement legality and packing. */
object GridVerticalLayout {
    /**
     * Measures the exclusive occupied bottom boundary and total scrollable row count.
     *
     * The occupied boundary is `position.row + span.rows` (exclusive). The scrollable row count
     * is the greater of [viewportRows] and that boundary plus [workingMarginRows]. A row count is
     * a quantity rather than an index, so an empty grid with no margin still has at least its
     * requested viewport height.
     *
     * @throws IllegalArgumentException if [viewportRows] is not positive or [workingMarginRows]
     * is negative
     * @throws ArithmeticException if the requested occupied extent plus margin cannot be
     * represented by a `Long`
     */
    fun <T : Any> measure(
        placements: List<GridPlacement<T>>,
        viewportRows: Long,
        workingMarginRows: Long,
    ): GridVerticalExtent {
        require(viewportRows > 0L) { "viewportRows must be positive" }
        require(workingMarginRows >= 0L) { "workingMarginRows must be non-negative" }

        var occupiedBottomRowExclusive = 0L
        for (placement in placements) {
            val placementBottom = placement.position.row.toLong() + placement.span.rows.toLong()
            if (placementBottom > occupiedBottomRowExclusive) {
                occupiedBottomRowExclusive = placementBottom
            }
        }

        val bottomWithMargin = Math.addExact(occupiedBottomRowExclusive, workingMarginRows)
        return GridVerticalExtent(
            occupiedBottomRowExclusive = occupiedBottomRowExclusive,
            totalScrollableRows = maxOf(viewportRows, bottomWithMargin),
        )
    }
}
