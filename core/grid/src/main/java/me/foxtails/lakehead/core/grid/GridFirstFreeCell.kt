package me.foxtails.lakehead.core.grid

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * A rectangle on the logical grid, defined by its top-left position and its span.
 */
data class PlacementRect(
    val position: LogicalPosition,
    val span: LogicalSpan,
)

/**
 * Finds the first free 1×1 cell in row-major order that does not collide
 * with any existing placement, within the given column/row bounds.
 *
 * @param occupied set of existing placement rectangles (position + span)
 * @param columns total column count (must be positive)
 * @param maxRow maximum row to search (inclusive, must be >= 0)
 * @return the first available position, or null if no free cell exists
 */
fun firstFreeCell(
    occupied: List<PlacementRect>,
    columns: Int,
    maxRow: Int,
): LogicalPosition? {
    require(columns > 0) { "columns must be positive" }
    require(maxRow >= 0) { "maxRow must be non-negative" }

    for (row in 0..maxRow) {
        for (col in 0 until columns) {
            val candidate = PlacementRect(
                position = LogicalPosition(col, row),
                span = LogicalSpan(1, 1),
            )
            if (occupied.none { it.overlaps(candidate) }) {
                return LogicalPosition(col, row)
            }
        }
    }
    return null
}

/**
 * Two rects overlap if their column ranges intersect AND their row ranges intersect.
 * A rect at (col, row) with span (w, h) occupies columns [col, col+w) and rows [row, row+h).
 */
internal fun PlacementRect.overlaps(other: PlacementRect): Boolean {
    val thisRight = this.position.column.toLong() + this.span.columns
    val otherRight = other.position.column.toLong() + other.span.columns
    val thisBottom = this.position.row.toLong() + this.span.rows
    val otherBottom = other.position.row.toLong() + other.span.rows
    return this.position.column < otherRight &&
        other.position.column < thisRight &&
        this.position.row < otherBottom &&
        other.position.row < thisBottom
}