package me.foxtails.lakehead.core.grid

/**
 * Removes excess completely empty row gaps between occupied sections.
 *
 * The input order is not used to determine sections, and the returned list is a new list. Only
 * rows are changed; columns, spans, ids, and the order of placements are retained.
 */
object GridRowGapNormalizer {
    private data class Section(
        val start: Long,
        val endExclusive: Long,
        val removedBefore: Long,
    )

    fun <T : Any> normalize(placements: List<GridPlacement<T>>): List<GridPlacement<T>> {
        if (placements.isEmpty()) return emptyList()

        val sections = occupiedSections(placements)
        var removedBefore = 0L
        val shiftedSections = sections.mapIndexed { index, section ->
            val shifted = Section(section.start, section.endExclusive, removedBefore)
            val next = sections.getOrNull(index + 1)
            if (next != null) {
                val emptyRows = next.start - section.endExclusive
                if (emptyRows > MAX_EMPTY_ROWS) {
                    removedBefore += emptyRows - MAX_EMPTY_ROWS
                }
            }
            shifted
        }

        return placements.map { placement ->
            val row = placement.position.row.toLong()
            val section = shiftedSections.first { row >= it.start && row < it.endExclusive }
            val normalizedRow = row - section.removedBefore
            placement.copy(position = placement.position.copy(row = normalizedRow.toInt()))
        }
    }

    private fun <T : Any> occupiedSections(placements: List<GridPlacement<T>>): List<Section> {
        val intervals = placements
            .map {
                val start = it.position.row.toLong()
                start..(start + it.span.rows.toLong() - 1L)
            }
            .sortedBy { it.first }

        val sections = mutableListOf<Section>()
        for (interval in intervals) {
            val start = interval.first
            val endExclusive = interval.last + 1L
            val previous = sections.lastOrNull()
            if (previous == null || start > previous.endExclusive) {
                sections += Section(start, endExclusive, removedBefore = 0L)
            } else if (endExclusive > previous.endExclusive) {
                sections[sections.lastIndex] = previous.copy(endExclusive = endExclusive)
            }
        }
        return sections
    }

    private const val MAX_EMPTY_ROWS = 2L
}
