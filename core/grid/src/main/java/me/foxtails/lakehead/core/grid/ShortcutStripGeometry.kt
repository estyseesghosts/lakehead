package me.foxtails.lakehead.core.grid

/**
 * Pure geometry for the shortcut strip that appears beside a grid tile.
 *
 * The strip occupies one edge of the tile's bounding rectangle.  Wide and
 * square tiles (columns ≥ 2, plus the 1×1 tile) lay the strip out
 * horizontally along the bottom edge as one mid-aligned group (app icon plus
 * its pinned shortcuts, centered — never right-aligned); narrow
 * single-column multi-row tiles (1×2, 1×3 and future 1×N forms) lay it out
 * vertically on the right edge with the app icon bottom-right and shortcuts
 * extending upward.  All arithmetic is Long-safe and overflow-checked.
 *
 * Spans are always read columns × rows ([GridSpan.columns] first): a 2-wide
 * × 4-tall tile is `GridSpan(2, 4)` with capacity 1, while a 4-wide × 2-tall
 * tile is `GridSpan(4, 2)` with capacity 3.  Never transpose the axes.
 */
object ShortcutStripGeometry {

    /** Corner or edge anchor that the first shortcut slot sits against. */
    enum class ShortcutAnchor { BOTTOM_RIGHT, BOTTOM_CENTER }

    /** Direction in which successive slot indices move away from the anchor. */
    enum class StripDirection { LEFTWARD, UPWARD }

    /**
     * Strip orientation for a tile with the given [span].
     *
     * Single-column multi-row tiles use [PinnedStripOrientation.VERTICAL]
     * (right-edge column, app bottom-right, shortcuts upward); every other
     * span (including 1×1) uses [PinnedStripOrientation.HORIZONTAL]
     * (bottom-edge row, app plus shortcuts mid-aligned as one group).
     */
    enum class PinnedStripOrientation { HORIZONTAL, VERTICAL }

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
     * Returns the strip orientation for a tile with the given [span].
     *
     * Single-column multi-row tiles use
     * [PinnedStripOrientation.VERTICAL]; every other span (including 1×1)
     * uses [PinnedStripOrientation.HORIZONTAL].  This is the layout-contract
     * form of [directionFor] for renderers that branch on orientation rather
     * than growth direction.
     */
    fun orientationFor(span: GridSpan): PinnedStripOrientation =
        if (isNarrow(span)) PinnedStripOrientation.VERTICAL else PinnedStripOrientation.HORIZONTAL

    /**
     * Total edge extent of one centered strip group in pixels.
     *
     * The group holds [slotCount] equal slots (the app icon plus its visible
     * pinned shortcuts): the first slot contributes the full [extentPx] and
     * each further slot contributes one [pitchPx] (slot extent plus the fixed
     * inter-slot gap, see [slotPitchPx]).  Renderers mid-align this width
     * inside the tile's measured bottom edge (horizontal) or right edge
     * (vertical) and recompute it from the measured bounds on every resize,
     * so the group stays centered instead of drifting to an end anchor and
     * slot positions stay stable as pins are added or removed.
     *
     * @param extentPx side length of one square slot in pixels (must be > 0)
     * @param pitchPx centre-to-centre slot pitch in pixels (must be > 0)
     * @param slotCount number of slots in the group: 1 + visible shortcuts
     * (must be ≥ 1)
     * @return `extentPx + (slotCount - 1) * pitchPx`
     * @throws IllegalArgumentException if any argument is out of range
     * @throws ArithmeticException if the computation overflows a `Long`
     */
    fun stripGroupExtentPx(extentPx: Long, pitchPx: Long, slotCount: Int): Long {
        require(extentPx > 0L) { "extentPx must be positive" }
        require(pitchPx > 0L) { "pitchPx must be positive" }
        require(slotCount >= 1) { "slotCount must be at least 1" }
        val gaps = Math.multiplyExact((slotCount - 1).toLong(), pitchPx)
        return Math.addExact(extentPx, gaps)
    }

    /**
     * Start offset that mid-aligns a strip group inside its container edge.
     *
     * `start = (containerPx - groupPx) / 2` with truncating division, so the
     * leftover pixel (if any) sits on the far side.  Callers pass the tile's
     * measured edge ([containerPx]) and [stripGroupExtentPx] ([groupPx]) as
     * captured from the same measurement pass, which is what makes the group
     * recompute its centering on every resize instead of caching an offset.
     *
     * @throws IllegalArgumentException if [containerPx] or [groupPx] is negative
     */
    fun centeredGroupStartPx(containerPx: Long, groupPx: Long): Long {
        require(containerPx >= 0L) { "containerPx must be non-negative" }
        require(groupPx >= 0L) { "groupPx must be non-negative" }
        return (containerPx - groupPx) / 2L
    }

    /**
     * Maximum number of shortcut slots that fit along the visible edge of a
     * tile with the given [span].
     *
     * Capacity is `usableEdgeCells - 1`: the app icon occupies exactly one
     * slot on the tile's strip edge, so the remaining edge cells bound the
     * visible pinned shortcuts (`2×2` → app + 1, `4`-wide × `2`-high → app
     * + 3).  Axes are never transposed: a 2-wide × 4-tall tile
     * (`GridSpan(2, 4)`) has capacity 1, not 3.
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