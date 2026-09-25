package me.foxtails.lakehead.core.designsystem

/**
 * Hit-test target regions inside a home tile.
 *
 * Used by gesture handlers to distinguish taps on the tile body,
 * the app symbol, or a specific shortcut slot (by index).
 */
sealed interface TileHitTarget {
    /** The tile's general background area. */
    data object Body : TileHitTarget

    /** The primary app-symbol icon region. */
    data object AppSymbol : TileHitTarget

    /**
     * A shortcut slot at the given [index].
     *
     * Index is zero-based and stable within a tile's shortcut row.
     */
    data class Shortcut(val index: Int) : TileHitTarget
}