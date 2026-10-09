package me.foxtails.lakehead.core.grid

import me.foxtails.lakehead.core.model.LogicalSpan
import kotlin.math.ceil

/**
 * Computes a default logical span for an AppWidget from its provider metadata.
 *
 * On API 31+ the provider declares exact cell targets ([targetCellWidth], [targetCellHeight]);
 * a value of 0 means the field is unset. When both are positive the function uses them directly.
 * Otherwise it falls back to the standard launcher formula per axis:
 *
 *     cells = ceil((minDp + 30) / 70.0)
 *
 * The result is clamped so columns ∈ [1, [columns]] and rows ≥ 1.
 *
 * @param targetCellWidth  provider-declared cell width  (0 = unset)
 * @param targetCellHeight provider-declared cell height (0 = unset)
 * @param minWidthDp       `minWidth` from widget info, in dp
 * @param minHeightDp      `minHeight` from widget info, in dp
 * @param columns          total grid column count (must be > 0)
 */
fun widgetDefaultSpan(
    targetCellWidth: Int,
    targetCellHeight: Int,
    minWidthDp: Int,
    minHeightDp: Int,
    columns: Int,
): LogicalSpan {
    require(columns > 0) { "columns must be positive" }

    val rawColumns: Int
    val rawRows: Int

    if (targetCellWidth > 0 && targetCellHeight > 0) {
        rawColumns = targetCellWidth
        rawRows = targetCellHeight
    } else {
        // Standard launcher formula: cells = ceil((minDp + 30) / 70.0)
        rawColumns = ceil((minWidthDp + 30) / 70.0).toInt()
        rawRows = ceil((minHeightDp + 30) / 70.0).toInt()
    }

    val clampedColumns = rawColumns.coerceIn(1, columns)
    val clampedRows = rawRows.coerceAtLeast(1)

    return LogicalSpan(columns = clampedColumns, rows = clampedRows)
}