package me.foxtails.lakehead.core.grid

/** Pure vertical extent measurement, independent of placement legality and packing. */
object GridVerticalLayout {
    /**
     * Measures intrinsic content bounds and height from [placements] alone.
     *
     * Nonnegative [GridPlacement] rows are measured from logical row zero, so their content count
     * is the absolute maximum row-exclusive boundary. The viewport minimum and the transient
     * edit workspace belong to their respective owners.
     *
     * **Edit-policy call boundary:** [viewportRows] and [workingMarginRows] are accepted
     * and validated here so that a future edit workspace can pass its viewport and margin
     * through the same entry point without a second overload. Today neither argument affects
     * the intrinsic measurement — the math reads only [placements] — and callers pass
     * conventionally valid literals (positive viewport, non-negative margin). The validation
     * guards the contract for that future boundary; it does not alter the result.
     *
     * @throws IllegalArgumentException if [viewportRows] is not positive or [workingMarginRows]
     * is negative
     */
    fun measure(
        placements: List<GridPlacement<*>>,
        viewportRows: Long,
        workingMarginRows: Long,
    ): GridVerticalExtent {
        require(viewportRows > 0L) { "viewportRows must be positive" }
        require(workingMarginRows >= 0L) { "workingMarginRows must be non-negative" }

        var minRow = Long.MAX_VALUE
        var maxRowExclusive = Long.MIN_VALUE
        for (placement in placements) {
            val row = placement.position.row
            val spanRows = placement.span.rows

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
