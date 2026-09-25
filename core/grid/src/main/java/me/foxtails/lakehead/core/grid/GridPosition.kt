package me.foxtails.lakehead.core.grid

/** Zero-based cell coordinate within a grid. */
data class GridPosition(
    val column: Int,
    val row: Int,
) {
    init {
        require(column >= 0) { "column must be non-negative" }
        require(row >= 0) { "row must be non-negative" }
    }
}
