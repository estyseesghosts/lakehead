package me.foxtails.lakehead.core.grid

/** Pure generic legality checks for placements on a horizontally bounded grid. */
object GridRules {
    /** A successful operation returns a fresh list, retaining unrelated entries and their order. */
    sealed class Result<out T : Any> {
        data class Accepted<T : Any>(val placements: List<GridPlacement<T>>) : Result<T>()
        data class Rejected(val reason: Rejection) : Result<Nothing>()
    }

    /** Reasons a candidate placement can be rejected. */
    enum class Rejection {
        OUT_OF_BOUNDS,
        COLLISION,
        UNKNOWN_ITEM,
    }

    /** Adds [placement], or explicitly replaces [replacingId] at its existing list position. */
    fun <T : Any> place(
        placements: List<GridPlacement<T>>,
        placement: GridPlacement<T>,
        columns: Int,
        replacingId: T? = null,
    ): Result<T> {
        requireValidColumns(columns)
        val replaceIndex = if (replacingId == null) {
            -1
        } else {
            placements.indexOfFirst { it.id == replacingId }
        }
        if (replacingId != null && replaceIndex == -1) {
            return Result.Rejected(Rejection.UNKNOWN_ITEM)
        }
        if (placements.any { it.id == placement.id } && placement.id != replacingId) {
            return Result.Rejected(Rejection.COLLISION)
        }
        return insertOrReplace(placements, placement, replaceIndex, columns)
    }

    /** Moves an existing item's position, ignoring its own current occupied cells. */
    fun <T : Any> move(
        placements: List<GridPlacement<T>>,
        id: T,
        position: GridPosition,
        columns: Int,
    ): Result<T> {
        requireValidColumns(columns)
        val index = placements.indexOfFirst { it.id == id }
        if (index == -1) return Result.Rejected(Rejection.UNKNOWN_ITEM)
        val current = placements[index]
        val moved = current.copy(position = position)
        return replaceExisting(placements, index, moved, columns)
    }

    /** Resizes an existing item, ignoring its own current occupied cells. */
    fun <T : Any> resize(
        placements: List<GridPlacement<T>>,
        id: T,
        span: GridSpan,
        columns: Int,
    ): Result<T> {
        requireValidColumns(columns)
        val index = placements.indexOfFirst { it.id == id }
        if (index == -1) return Result.Rejected(Rejection.UNKNOWN_ITEM)
        val current = placements[index]
        val resized = current.copy(span = span)
        return replaceExisting(placements, index, resized, columns)
    }

    private fun <T : Any> insertOrReplace(
        placements: List<GridPlacement<T>>,
        candidate: GridPlacement<T>,
        replaceIndex: Int,
        columns: Int,
    ): Result<T> {
        val rejection = legality(candidate, placements, columns, replaceIndex)
        if (rejection != null) return Result.Rejected(rejection)
        val result = placements.toMutableList()
        if (replaceIndex >= 0) {
            result[replaceIndex] = candidate
        } else {
            result.add(candidate)
        }
        return Result.Accepted(result.toList())
    }

    private fun <T : Any> replaceExisting(
        placements: List<GridPlacement<T>>,
        index: Int,
        candidate: GridPlacement<T>,
        columns: Int,
    ): Result<T> {
        val rejection = legality(candidate, placements, columns, index)
        if (rejection != null) return Result.Rejected(rejection)
        val result = placements.toMutableList()
        result[index] = candidate
        return Result.Accepted(result.toList())
    }

    private fun <T : Any> legality(
        candidate: GridPlacement<T>,
        placements: List<GridPlacement<T>>,
        columns: Int,
        ignoredIndex: Int,
    ): Rejection? {
        if (!isWithinBounds(candidate.position, candidate.span, columns)) return Rejection.OUT_OF_BOUNDS
        if (placements.indices.any { index ->
                index != ignoredIndex && overlaps(candidate, placements[index])
            }
        ) {
            return Rejection.COLLISION
        }
        return null
    }

    /** Returns whether a nonnegative position and its complete span fit the grid. */
    fun isWithinBounds(
        position: GridPosition,
        span: GridSpan,
        columns: Int,
    ): Boolean =
        isWithinBounds(
            column = position.column.toLong(),
            row = position.row.toLong(),
            spanColumns = span.columns,
            spanRows = span.rows,
            columns = columns,
        )

    /** Signed, overflow-safe rectangle bounds predicate shared by grid callers. */
    fun isWithinBounds(
        column: Long,
        row: Long,
        spanColumns: Int,
        spanRows: Int,
        columns: Int,
    ): Boolean =
        column >= 0L && row >= 0L &&
            spanColumns > 0 && spanRows > 0 && columns > 0 &&
            column <= columns.toLong() - spanColumns.toLong()

    private fun overlaps(first: GridPlacement<*>, second: GridPlacement<*>): Boolean {
        val firstRight = first.position.column.toLong() + first.span.columns
        val secondRight = second.position.column.toLong() + second.span.columns
        val firstBottom = first.position.row.toLong() + first.span.rows
        val secondBottom = second.position.row.toLong() + second.span.rows
        return first.position.column < secondRight &&
            second.position.column < firstRight &&
            first.position.row < secondBottom &&
            second.position.row < firstBottom
    }

    private fun requireValidColumns(columns: Int) {
        require(columns > 0) { "columns must be positive" }
    }
}
