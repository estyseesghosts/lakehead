package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * UI span allowlist for drag-resize targets.
 *
 * The grid accepts any positive-integer span, but the UI only offers drag targets at
 * these discrete sizes. [quantize] maps an arbitrary raw span to the nearest renderable
 * entry using Manhattan distance on (columns, rows).
 */
object RenderableSpans {

    /** Renderable span entries in priority order (smallest first). */
    val entries: List<LogicalSpan> = listOf(
        LogicalSpan(1, 1),
        LogicalSpan(2, 1),
        LogicalSpan(1, 2),
        LogicalSpan(2, 2),
        LogicalSpan(4, 2),
    )

    /**
     * Quantize [raw] to the nearest renderable span.
     *
     * Distance is Manhattan: `|raw.columns - e.columns| + |raw.rows - e.rows|`.
     * Ties broken by position in [entries] (earlier entry wins = smaller span preferred).
     * If [raw] is below the smallest entry, returns the smallest. If above the largest,
     * returns the largest.
     */
    fun quantize(raw: LogicalSpan): LogicalSpan {
        require(raw.columns > 0 && raw.rows > 0) { "raw span must be positive, was ${raw.columns}x${raw.rows}" }
        return entries.minBy { entry ->
            val colDist = kotlin.math.abs(raw.columns - entry.columns)
            val rowDist = kotlin.math.abs(raw.rows - entry.rows)
            colDist + rowDist
        }
    }
}