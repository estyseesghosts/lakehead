package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * UI span allowlist for drag-resize targets.
 *
 * The grid accepts any positive-integer span, but the UI only offers drag targets at
 * these discrete sizes. [quantize] maps an arbitrary raw span to the nearest renderable
 * entry using Manhattan distance on (columns, rows).
 *
 * The allowlist covers: width=1 → heights 1/2/3; width=2 → heights 1/2/3;
 * width=3 → rows 2/3; width=4 → rows 2/3/4; width=5 → rows 2/3/4.
 * Entries are ordered smallest-first so that Manhattan ties prefer the smaller span.
 */
object RenderableSpans {

    /** Renderable span entries in priority order (smallest first for Manhattan tie-break). */
    val entries: List<LogicalSpan> = listOf(
        LogicalSpan(1, 1),
        LogicalSpan(2, 1),
        LogicalSpan(1, 2),
        LogicalSpan(2, 2),
        LogicalSpan(1, 3),
        LogicalSpan(2, 3),
        LogicalSpan(3, 2),
        LogicalSpan(4, 2),
        LogicalSpan(5, 2),
        LogicalSpan(3, 3),
        LogicalSpan(4, 3),
        LogicalSpan(5, 3),
        LogicalSpan(4, 4),
        LogicalSpan(5, 4),
    )

    /**
     * Quantize [raw] to the nearest renderable span whose column count fits within
     * the given grid [columns].
     *
     * Distance is Manhattan: `|raw.columns - e.columns| + |raw.rows - e.rows|`.
     * Ties broken by position in [entries] (earlier entry wins = smaller span preferred).
     * If all candidate entries are farther than the smallest, the smallest is returned.
     *
     * @param raw the un-quantized span from a drag gesture.
     * @param columns the visible column count of the grid; entries wider than this are excluded.
     */
    fun quantize(raw: LogicalSpan, columns: Int = Int.MAX_VALUE): LogicalSpan {
        require(raw.columns > 0 && raw.rows > 0) { "raw span must be positive, was ${raw.columns}x${raw.rows}" }
        val candidates = if (columns == Int.MAX_VALUE) entries
            else entries.filter { it.columns <= columns }
        // Should never be empty — entries always contains 1x1 which fits any grid ≥ 1 column.
        return candidates.minBy { entry ->
            val colDist = kotlin.math.abs(raw.columns - entry.columns)
            val rowDist = kotlin.math.abs(raw.rows - entry.rows)
            colDist + rowDist
        }
    }
}