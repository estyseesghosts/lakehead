package me.foxtails.lakehead.core.grid

/**
 * Vertical measurements of a logical grid.
 *
 * [occupiedBottomRowExclusive] is retained as the legacy name for the signed maximum boundary.
 * [occupiedMinRow] and [occupiedMaxRowExclusive] are the complete signed bounds; neither is a
 * row count. [occupiedHeightRows] and [totalScrollableRows] are counts. Normal measurement makes
 * the latter an intrinsic content height; viewport and edit margins are imposed by their owners.
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
