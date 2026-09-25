package me.foxtails.lakehead.core.grid

/**
 * Vertical measurements of a logical grid.
 *
 * [occupiedBottomRowExclusive] is an exclusive boundary: a placement ending at row `n` has
 * occupied rows only up to, but not including, `n`. [totalScrollableRows] includes viewport and
 * working-margin rows and is measured as a row count, not as a zero-based row index.
 */
data class GridVerticalExtent(
    val occupiedBottomRowExclusive: Long,
    val totalScrollableRows: Long,
) {
    init {
        require(occupiedBottomRowExclusive >= 0L) {
            "occupiedBottomRowExclusive must be non-negative"
        }
        require(totalScrollableRows > 0L) { "totalScrollableRows must be positive" }
    }
}
