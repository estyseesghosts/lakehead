package me.foxtails.lakehead.core.grid

/** Pure vertical extent measurement, independent of placement legality and packing. */
object GridVerticalLayout {
    /**
     * Measures intrinsic content bounds and height.
     *
     * Nonnegative [GridPlacement] rows are measured from logical row zero, so their content count
     * is the absolute maximum row-exclusive boundary. The viewport minimum and the transient
     * edit workspace belong to their respective owners.
     *
     * @throws IllegalArgumentException if [viewportRows] is not positive or [workingMarginRows]
     * is negative
     * The viewport and working-margin arguments remain for the edit-policy call boundary; they
     * are validated here but intentionally do not affect normal intrinsic measurement.
     */
    fun <T : Any> measure(
        placements: List<T>,
        viewportRows: Long,
        workingMarginRows: Long,
    ): GridVerticalExtent {
        require(viewportRows > 0L) { "viewportRows must be positive" }
        require(workingMarginRows >= 0L) { "workingMarginRows must be non-negative" }

        var minRow = Long.MAX_VALUE
        var maxRowExclusive = Long.MIN_VALUE
        for (placement in placements) {
            val row: Int
            val spanRows: Int
            when (placement) {
                is GridPlacement<*> -> {
                    row = placement.position.row
                    spanRows = placement.span.rows
                }
                else -> error("unsupported placement type: ${placement::class}")
            }

            val rowLong = row.toLong()
            val rowExclusive = Math.addExact(rowLong, spanRows.toLong())
            if (rowLong < minRow) minRow = rowLong
            if (rowExclusive > maxRowExclusive) maxRowExclusive = rowExclusive
        }
        if (placements.isEmpty()) {
            minRow = 0L
            maxRowExclusive = 0L
        }
        // Absolute Home content starts at row zero even when its first tile is lower,
        // preserving leading logical rows.
        val occupiedHeightRows = maxRowExclusive
        return GridVerticalExtent(
            occupiedBottomRowExclusive = maxRowExclusive,
            totalScrollableRows = occupiedHeightRows,
            occupiedMinRow = minRow,
            occupiedMaxRowExclusive = maxRowExclusive,
            occupiedHeightRows = occupiedHeightRows,
        )
    }
}
