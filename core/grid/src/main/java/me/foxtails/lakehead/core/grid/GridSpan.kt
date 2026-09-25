package me.foxtails.lakehead.core.grid

/** Positive rectangular cell dimensions. Legality for this phase is limited to [SUPPORTED]. */
data class GridSpan(
    val columns: Int,
    val rows: Int,
) {
    init {
        require(columns > 0) { "columns must be positive" }
        require(rows > 0) { "rows must be positive" }
    }

    /** Whether this span is one of the B1-supported dimensions. */
    val isSupported: Boolean
        get() = this in SUPPORTED

    companion object {
        /** The exact span set supported by B1; no dimension adaptation is implied. */
        val SUPPORTED: Set<GridSpan> = setOf(
            GridSpan(1, 1),
            GridSpan(2, 1),
            GridSpan(1, 2),
            GridSpan(2, 2),
            GridSpan(4, 2),
        )
    }
}
