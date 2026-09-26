package me.foxtails.lakehead.core.grid

/**
 * Axis-aligned bounding box of placements on a grid with signed (unbounded) rows and
 * non-negative columns. All boundaries are exclusive on the max side.
 *
 * Column boundaries are clamped to the grid's valid range `[0, columns)` so that the
 * bounding box never extends outside the grid. Row boundaries preserve their original
 * signed values.
 */
data class ContentBounds(
    val minRow: Long,
    val maxRowExclusive: Long,
    val minCol: Long,
    val maxColExclusive: Long,
)

/**
 * Signed-row content-bounds utilities.
 *
 * Placements use [SignedGridPlacement], which allows negative row values. Columns are
 * bounded by the grid's column count; rows are unbounded. All arithmetic is overflow-checked.
 */
object GridContentBounds {

    /**
     * Returns the bounding box of [placements], or `null` when the list is empty.
     *
     * The [columns] parameter sets the valid column range `0 ..< columns`; any placement that
     * extends outside that range triggers an [IllegalArgumentException]. Column boundaries of
     * the result are clamped to `[0, columns]` when the grid is empty-of-items-but-not-null
     * (currently unused — this is kept explicit for future vertical-only scroll scenarios).
     *
     * @param columns total column count (must be positive)
     * @throws IllegalArgumentException if any placement extends outside the column range, or
     * if [columns] is not positive
     * @throws ArithmeticException if row/column extent arithmetic overflows a `Long`
     */
    fun <T : Any> contentBoundsOf(
        placements: List<SignedGridPlacement<T>>,
        columns: Int = 5,
    ): ContentBounds? {
        require(columns > 0) { "columns must be positive" }
        if (placements.isEmpty()) return null

        var minCol = Long.MAX_VALUE
        var maxColExclusive = Long.MIN_VALUE
        var minRow = Long.MAX_VALUE
        var maxRowExclusive = Long.MIN_VALUE

        for (placement in placements) {
            require(placement.column >= 0) { "column must be non-negative" }
            val colExclusive = Math.addExact(placement.column.toLong(), placement.span.columns.toLong())
            require(colExclusive <= columns.toLong()) {
                "placement '${placement.id}' extends to column $colExclusive, exceeding columns=$columns"
            }

            if (placement.column.toLong() < minCol) minCol = placement.column.toLong()
            if (colExclusive > maxColExclusive) maxColExclusive = colExclusive

            val row = placement.row.toLong()
            val rowExclusive = Math.addExact(row, placement.span.rows.toLong())
            if (row < minRow) minRow = row
            if (rowExclusive > maxRowExclusive) maxRowExclusive = rowExclusive
        }

        return ContentBounds(
            minRow = minRow,
            maxRowExclusive = maxRowExclusive,
            minCol = minCol,
            maxColExclusive = maxColExclusive,
        )
    }

    /**
     * Returns the total occupied row height as a row count (not an index).
     *
     * This is `maxRowExclusive - minRow` and may span negative rows. Returns `0` for
     * an empty bounds map.
     *
     * @throws ArithmeticException if the difference overflows (theoretically unreachable
     * since both values share the same `Long` domain)
     */
    fun contentHeightRows(bounds: ContentBounds): Long =
        Math.subtractExact(bounds.maxRowExclusive, bounds.minRow)

    /**
     * Returns the occupied row height for the given placements, or `0` when the list is empty.
     *
     * @see contentBoundsOf
     */
    fun <T : Any> contentHeightRows(
        placements: List<SignedGridPlacement<T>>,
        columns: Int = 5,
    ): Long {
        val bounds = contentBoundsOf(placements, columns) ?: return 0L
        return contentHeightRows(bounds)
    }

    /**
     * Returns `true` if two rectangles strictly overlap (share interior area).
     *
     * Rectangles that touch only at an edge or corner do **not** overlap. Both rectangles
     * use exclusive max boundaries.
     */
    fun hasOverlap(
        aMinRow: Long, aMaxRowExclusive: Long, aMinCol: Long, aMaxColExclusive: Long,
        bMinRow: Long, bMaxRowExclusive: Long, bMinCol: Long, bMaxColExclusive: Long,
    ): Boolean =
        aMinRow < bMaxRowExclusive &&
            bMinRow < aMaxRowExclusive &&
            aMinCol < bMaxColExclusive &&
            bMinCol < aMaxColExclusive
}