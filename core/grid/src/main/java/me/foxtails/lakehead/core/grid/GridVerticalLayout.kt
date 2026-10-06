package me.foxtails.lakehead.core.grid

/** Pure vertical extent measurement, independent of placement legality and packing. */
object GridVerticalLayout {
    /** Maximum transient edit margin; this is not a normal-layout extent limit. */
    const val MAX_WORKING_MARGIN_ROWS: Long = 4_096L

    /** The exclusive end of the temporary edit area. */
    data class EditBoundary(
        val exclusiveRow: Long,
        val marginRows: Long,
    )

    /**
     * Creates an edit-only boundary from the normal intrinsic extent.
     *
     * The margin is capped before it participates in row arithmetic.  In particular, this policy
     * does not put a 4096-row limit on the logical grid; it only bounds the amount of temporary
     * space kept ahead of the current frontier.
     */
    fun createEditBoundary(
        extent: GridVerticalExtent,
        viewportRows: Long,
        workingMarginRows: Long,
    ): EditBoundary {
        require(viewportRows > 0L) { "viewportRows must be positive" }
        val marginRows = cappedWorkingMarginRows(workingMarginRows)
        val contentOrViewportEnd = maxOf(extent.occupiedMaxRowExclusive, viewportRows)
        return EditBoundary(
            exclusiveRow = Math.addExact(contentOrViewportEnd, marginRows),
            marginRows = marginRows,
        )
    }

    /**
     * Extends an edit boundary by one bounded margin when a move reaches its trailing margin.
     * Calling this repeatedly is intentional: each call advances by only one margin, rather than
     * allowing a distant or malformed move to allocate an unbounded edit area in one step.
     */
    fun advanceEditBoundary(
        boundary: EditBoundary,
        movedBottomRowExclusive: Long,
    ): EditBoundary {
        val marginRows = boundary.marginRows.coerceIn(0L, MAX_WORKING_MARGIN_ROWS)
        val safeBoundary = if (marginRows == boundary.marginRows) {
            boundary
        } else {
            boundary.copy(marginRows = marginRows)
        }
        if (marginRows == 0L) return safeBoundary
        val trailingEdge = Math.subtractExact(safeBoundary.exclusiveRow, marginRows)
        if (movedBottomRowExclusive < trailingEdge) return safeBoundary
        return safeBoundary.copy(
            exclusiveRow = Math.addExact(safeBoundary.exclusiveRow, marginRows),
        )
    }

    private fun cappedWorkingMarginRows(workingMarginRows: Long): Long {
        require(workingMarginRows >= 0L) { "workingMarginRows must be non-negative" }
        return minOf(workingMarginRows, MAX_WORKING_MARGIN_ROWS)
    }

    /**
     * Measures intrinsic content bounds and height.
     *
     * Nonnegative [GridPlacement] rows are measured from logical row zero, so their content count
     * is the absolute maximum row-exclusive boundary. [SignedGridPlacement] rows retain their
     * shared signed-coordinate semantics and use the distance between their bounds. The two
     * coordinate families must not be mixed in one call. The viewport minimum and transient edit
     * frontier belong to their respective owners.
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
        var usesAbsoluteRows = false
        var usesSignedRows = false
        for (placement in placements) {
            val row: Int
            val spanRows: Int
            when (placement) {
                is GridPlacement<*> -> {
                    usesAbsoluteRows = true
                    row = placement.position.row
                    spanRows = placement.span.rows
                }
                is SignedGridPlacement<*> -> {
                    usesSignedRows = true
                    row = placement.row
                    spanRows = placement.span.rows
                }
                else -> error("unsupported placement type: ${placement::class}")
            }

            val rowLong = row.toLong()
            val rowExclusive = Math.addExact(rowLong, spanRows.toLong())
            if (rowLong < minRow) minRow = rowLong
            if (rowExclusive > maxRowExclusive) maxRowExclusive = rowExclusive
        }
        require(!usesAbsoluteRows || !usesSignedRows) {
            "GridVerticalLayout.measure cannot mix absolute and signed placements"
        }
        if (placements.isEmpty()) {
            minRow = 0L
            maxRowExclusive = 0L
        }
        // Signed callers measure their signed bounds. Absolute Home content starts at row zero
        // even when its first tile is lower, preserving leading logical rows.
        val occupiedHeightRows = if (usesSignedRows) {
            Math.subtractExact(maxRowExclusive, minRow)
        } else {
            maxRowExclusive
        }
        return GridVerticalExtent(
            occupiedBottomRowExclusive = maxRowExclusive,
            totalScrollableRows = occupiedHeightRows,
            occupiedMinRow = minRow,
            occupiedMaxRowExclusive = maxRowExclusive,
            occupiedHeightRows = occupiedHeightRows,
        )
    }
}
