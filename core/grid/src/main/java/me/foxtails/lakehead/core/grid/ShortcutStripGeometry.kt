package me.foxtails.lakehead.core.grid

/**
 * Pure geometry for the shortcut strip that appears beside a grid tile.
 *
 * The strip occupies one edge of the tile's bounding rectangle.  Wide tiles
 * (columns ≥ 2, plus the 1×1 tile) anchor the strip at the bottom-right
 * corner and extend it leftward; narrow single-column multi-row tiles
 * (1×2, 1×3 and future 1×N forms) anchor at the bottom-center and extend
 * upward.  All arithmetic is Long-safe and overflow-checked.
 */
object ShortcutStripGeometry {

    /** Corner or edge anchor that the first shortcut slot sits against. */
    enum class ShortcutAnchor { BOTTOM_RIGHT, BOTTOM_CENTER }

    /** Direction in which successive slot indices move away from the anchor. */
    enum class StripDirection { LEFTWARD, UPWARD }

    /**
     * `true` when [span] is a narrow single-column multi-row tile, meaning the
     * strip is narrow.
     *
     * A 1×1 tile is wide for anchor purposes: the app symbol sits bottom-right
     * per `docs/design.md` and `docs/grid.md` §4 (bottom-right except 1×2
     * bottom-center, generic over future spans), and a 1×1 tile has zero
     * in-tile shortcut capacity in any case.
     */
    fun isNarrow(span: GridSpan): Boolean = span.columns == 1 && span.rows >= 2

    /**
     * Returns the anchor for a tile with the given [span].
     *
     * Single-column multi-row tiles use [ShortcutAnchor.BOTTOM_CENTER];
     * every other span (including 1×1) uses [ShortcutAnchor.BOTTOM_RIGHT].
     */
    fun anchorFor(span: GridSpan): ShortcutAnchor =
        if (isNarrow(span)) ShortcutAnchor.BOTTOM_CENTER else ShortcutAnchor.BOTTOM_RIGHT

    /**
     * Returns the strip direction for a tile with the given [span].
     *
     * Single-column multi-row tiles extend [StripDirection.UPWARD];
     * every other span (including 1×1) extends [StripDirection.LEFTWARD].
     */
    fun directionFor(span: GridSpan): StripDirection =
        if (isNarrow(span)) StripDirection.UPWARD else StripDirection.LEFTWARD

    /**
     * Maximum number of shortcut slots that fit along the visible edge of a
     * tile with the given [span].
     *
     * Rules (applied in order):
     * - If either dimension is ≤ 0 the result is 0.
     * - A single-column tile uses `rows - 1` (the strip cannot overlap the
     *   tile's own cell).
     * - Otherwise `columns - 1`.
     * - The result is clamped to ≥ 0.
     */
    fun maxSlots(span: GridSpan): Int = maxSlots(span.columns, span.rows)

    /**
     * Maximum number of shortcut slots for an explicit [columns] × [rows]
     * tile.
     *
     * @see maxSlots(GridSpan)
     */
    fun maxSlots(columns: Int, rows: Int): Int {
        if (columns <= 0 || rows <= 0) return 0
        val raw = if (columns == 1) rows - 1 else columns - 1
        return raw.coerceAtLeast(0)
    }

    /**
     * Centre-to-centre distance between adjacent shortcut slots.
     *
     * @param cellPx  side length of one grid cell in pixels (must be > 0)
     * @param gapPx   inter-cell gap in pixels (must be ≥ 0)
     * @return `cellPx + gapPx`
     * @throws IllegalArgumentException if [cellPx] ≤ 0 or [gapPx] < 0
     */
    fun slotPitchPx(cellPx: Long, gapPx: Long): Long {
        require(cellPx > 0L) { "cellPx must be positive" }
        require(gapPx >= 0L) { "gapPx must be non-negative" }
        return Math.addExact(cellPx, gapPx)
    }

    /**
     * Centre coordinate of the slot at [index], measured from the origin of
     * the anchor axis.
     *
     * Slot 0 is at the anchor; each subsequent index moves one [pitch] away.
     *
     * @param anchorValue  pixel position of the anchor along the relevant axis
     * @param index        zero-based slot index (must be ≥ 0)
     * @param pitch        result of [slotPitchPx] (must be > 0)
     * @return `anchorValue - index * pitch`
     * @throws IllegalArgumentException if [index] < 0 or [pitch] ≤ 0
     * @throws ArithmeticException if the computation overflows a `Long`
     */
    fun slotCenterPx(anchorValue: Long, index: Int, pitch: Long): Long {
        require(index >= 0) { "index must be non-negative" }
        require(pitch > 0L) { "pitch must be positive" }
        val offset = Math.multiplyExact(index.toLong(), pitch)
        return Math.subtractExact(anchorValue, offset)
    }

    /**
     * Number of shortcuts to actually render, clamped to the valid range
     * `[0, maxSlots(span)]`.
     *
     * @param pinned  desired number of pinned shortcuts
     * @param span    tile span
     * @return `pinned` clamped to `[0, maxSlots(span)]`
     */
    fun visibleCount(pinned: Int, span: GridSpan): Int =
        pinned.coerceIn(0, maxSlots(span))
}