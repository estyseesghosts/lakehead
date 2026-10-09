package me.foxtails.lakehead.core.grid

import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Finds the first free position for a rectangle of the given [span] in row-major order,
 * scanning columns `0..(columns - span.columns)` and rows `0..`, where the candidate
 * rectangle does not overlap any placement in [occupied].
 *
 * Rows are unbounded but the search terminates by going one row below the lowest occupied
 * bottom edge, so the function always terminates.
 *
 * @param occupied existing placement rectangles on the grid
 * @param columns total column count (must be positive)
 * @param span dimensions of the rectangle to place (must fit within [columns])
 * @return the first available [LogicalPosition]
 * @throws IllegalArgumentException if columns <= 0 or span.columns > columns
 */
fun firstFreeRect(
    occupied: List<PlacementRect>,
    columns: Int,
    span: LogicalSpan,
): LogicalPosition {
    require(columns > 0) { "columns must be positive" }
    require(span.columns <= columns) { "span columns (${span.columns}) must fit within grid columns ($columns)" }

    val maxOccupiedBottom = occupied.maxOfOrNull { it.position.row + it.span.rows } ?: 0
    // Search up to the row where a candidate could start and still overlap with occupied.
    // A candidate at row r has bottom edge r + span.rows; it overlaps occupied if
    // r + span.rows > occupiedTop, i.e. r < maxOccupiedBottom - span.rows + 1.
    // Below that, the candidate is entirely below all occupied rects and always free.
    val maxCandidateRow = (maxOccupiedBottom - span.rows).coerceAtLeast(0)
    val maxColStart = columns - span.columns

    for (row in 0..maxCandidateRow) {
        for (col in 0..maxColStart) {
            val candidate = PlacementRect(
                position = LogicalPosition(col, row),
                span = span,
            )
            if (occupied.none { it.overlaps(candidate) }) {
                return candidate.position
            }
        }
    }

    // No free position found above all occupied rects; place one row below all content.
    return LogicalPosition(0, maxOccupiedBottom)
}