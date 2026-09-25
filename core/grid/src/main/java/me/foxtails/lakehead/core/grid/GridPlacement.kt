package me.foxtails.lakehead.core.grid

/** An item's logical location and rectangular occupied cell span. */
data class GridPlacement<T : Any>(
    val id: T,
    val position: GridPosition,
    val span: GridSpan,
)
