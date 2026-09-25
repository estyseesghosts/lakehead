package me.foxtails.lakehead.core.grid

/** Positive rectangular cell dimensions. Any positive dimensions are representable; legality is bounds + collision in [GridRules]. */
data class GridSpan(
    val columns: Int,
    val rows: Int,
) {
    init {
        require(columns > 0) { "columns must be positive" }
        require(rows > 0) { "rows must be positive" }
    }
}
