package me.foxtails.lakehead.core.grid

/** A half-open range of logical rows; [firstRow] may be negative. */
data class GridRowWindow(
    val firstRow: Long,
    val lastRowExclusive: Long,
) {
    init {
        require(lastRowExclusive >= firstRow) {
            "lastRowExclusive must be greater than or equal to firstRow"
        }
    }

    /**
     * Returns this range extended by [overscanRows] in both directions.
     *
     * The lower boundary saturates at [Long.MIN_VALUE] and the upper exclusive boundary
     * saturates at [Long.MAX_VALUE]; neither boundary wraps on overflow. An empty range can
     * become non-empty when overscan is positive.
     *
     * @throws IllegalArgumentException if [overscanRows] is negative
     */
    fun withOverscan(overscanRows: Long): GridRowWindow {
        require(overscanRows >= 0L) { "overscanRows must be non-negative" }
        return GridRowWindow(
            firstRow = saturatingSubtract(firstRow, overscanRows),
            lastRowExclusive = saturatingAdd(lastRowExclusive, overscanRows),
        )
    }

    private fun saturatingAdd(value: Long, increment: Long): Long =
        try {
            Math.addExact(value, increment)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }

    private fun saturatingSubtract(value: Long, decrement: Long): Long =
        try {
            Math.subtractExact(value, decrement)
        } catch (_: ArithmeticException) {
            Long.MIN_VALUE
        }
}

/** Pure selection of placements intersecting a logical visible-row window. */
object GridVisiblePlacements {
    /**
     * Selects placements whose row intervals intersect [visibleRows] extended by [overscanRows].
     *
     * The selector preserves input order and does not alter placement IDs or coordinates. A
     * placement crossing either window boundary is included. Placement row ends are computed
     * with checked `Long` addition, so an unrepresentable end is reported rather than wrapping
     * and causing the placement to be silently omitted. The current [GridPlacement] row/span
     * fields are `Int`-bounded, but the range and overscan arithmetic remain safe across the
     * full `Long` domain.
     *
     * @throws IllegalArgumentException if [overscanRows] is negative
     * @throws ArithmeticException if a placement's exclusive row end cannot be represented
     */
    fun <T : Any> select(
        placements: List<GridPlacement<T>>,
        visibleRows: GridRowWindow,
        overscanRows: Long = 0L,
    ): List<GridPlacement<T>> {
        val selectionWindow = visibleRows.withOverscan(overscanRows)
        if (selectionWindow.firstRow == selectionWindow.lastRowExclusive) return emptyList()

        return placements.filter { placement ->
            val firstRow = placement.position.row.toLong()
            val lastRowExclusive = Math.addExact(firstRow, placement.span.rows.toLong())
            firstRow < selectionWindow.lastRowExclusive &&
                lastRowExclusive > selectionWindow.firstRow
        }
    }

    /**
     * Selects [SignedGridPlacement]s whose row intervals intersect [visibleRows] extended
     * by [overscanRows].
     *
     * This mirrors [select] but operates on placements with signed (potentially negative) rows.
     * Preserves input order. Placement row ends are computed with checked `Long` addition.
     *
     * @throws IllegalArgumentException if [overscanRows] is negative
     * @throws ArithmeticException if a placement's exclusive row end cannot be represented
     */
    fun <T : Any> selectSigned(
        placements: List<SignedGridPlacement<T>>,
        visibleRows: GridRowWindow,
        overscanRows: Long = 0L,
    ): List<SignedGridPlacement<T>> {
        val selectionWindow = visibleRows.withOverscan(overscanRows)
        if (selectionWindow.firstRow == selectionWindow.lastRowExclusive) return emptyList()

        return placements.filter { placement ->
            val firstRow = placement.row.toLong()
            val lastRowExclusive = Math.addExact(firstRow, placement.span.rows.toLong())
            firstRow < selectionWindow.lastRowExclusive &&
                lastRowExclusive > selectionWindow.firstRow
        }
    }
}
