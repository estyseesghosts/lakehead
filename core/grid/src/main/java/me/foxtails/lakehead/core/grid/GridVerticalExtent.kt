package me.foxtails.lakehead.core.grid

/**
 * Vertical measurements of a logical grid.
 *
 * [occupiedBottomRowExclusive] is the absolute row-zero content boundary for nonnegative
 * [GridPlacement] measurement. [occupiedMinRow] remains informational for that mode, while
 * [occupiedMaxRowExclusive] is the same exclusive boundary. For shared signed placement callers,
 * the min/max fields remain signed bounds and the count fields retain signed-bound distance
 * semantics. [occupiedHeightRows] and [totalScrollableRows] are counts; viewport and edit margins
 * are imposed by their owners.
 */
data class GridVerticalExtent(
    val occupiedBottomRowExclusive: Long,
    val totalScrollableRows: Long,
    val occupiedMinRow: Long = 0L,
    val occupiedMaxRowExclusive: Long = occupiedBottomRowExclusive,
    val occupiedHeightRows: Long = totalScrollableRows,
) {
    init {
        require(occupiedMaxRowExclusive >= occupiedMinRow) {
            "occupiedMaxRowExclusive must not be less than occupiedMinRow"
        }
        require(occupiedHeightRows >= 0L) { "occupiedHeightRows must be non-negative" }
        require(totalScrollableRows >= 0L) { "totalScrollableRows must be non-negative" }
    }
}
