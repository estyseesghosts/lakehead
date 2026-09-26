package me.foxtails.lakehead.core.grid

/**
 * A grid placement whose [row] may be negative (unbounded) while [column] stays non-negative.
 *
 * This pairs with [GridSpan] for the rectangular extent and is intentionally distinct from
 * [GridPlacement], whose `GridPosition` requires a non-negative row. Signed-row placements
 * are used by content-bounds calculations that must represent items above the origin row.
 */
data class SignedGridPlacement<T : Any>(
    val id: T,
    val column: Int,
    val row: Int,
    val span: GridSpan,
) {
    init {
        require(column >= 0) { "column must be non-negative" }
    }
}