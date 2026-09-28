package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class GridRowGapNormalizerTest {
    @Test
    fun gapsOfZeroOneAndTwoRowsAreUnchanged() {
        val placements = listOf(tile("a", 0), tile("b", 1), tile("c", 3), tile("d", 6))

        assertEquals(placements, GridRowGapNormalizer.normalize(placements))
    }

    @Test
    fun threeAndLargeGapsAreReducedToTwoRows() {
        val placements = listOf(tile("a", 0), tile("b", 4), tile("c", 20))

        assertEquals(listOf(0, 3, 6), rows(GridRowGapNormalizer.normalize(placements)))
    }

    @Test
    fun multipleGapsAreReducedCumulatively() {
        val placements = listOf(tile("a", 0), tile("b", 5), tile("c", 12), tile("d", 20))

        assertEquals(listOf(0, 3, 6, 9), rows(GridRowGapNormalizer.normalize(placements)))
    }

    @Test
    fun leadingSpaceIsPreservedAndTrailingSpaceIsIgnored() {
        val placements = listOf(tile("a", 10), tile("b", 14))

        assertEquals(listOf(10, 13), rows(GridRowGapNormalizer.normalize(placements)))
    }

    @Test
    fun spanningTilesOccupyEveryRowInTheirHalfOpenInterval() {
        val placements = listOf(
            tile("span", row = 0, rows = 3),
            tile("next", row = 7),
        )

        assertEquals(listOf(0, 5), rows(GridRowGapNormalizer.normalize(placements)))
    }

    @Test
    fun usesLongArithmeticForRowsNearIntMaximum() {
        val placements = listOf(
            tile("near-end", row = Int.MAX_VALUE - 10, rows = 2),
            tile("last", row = Int.MAX_VALUE),
        )

        assertEquals(
            listOf(Int.MAX_VALUE - 10, Int.MAX_VALUE - 6),
            rows(GridRowGapNormalizer.normalize(placements)),
        )
    }

    @Test
    fun preservesColumnsSpansIdsAndInputOrderWithoutMutatingInput() {
        val placements = listOf(
            tile("second", row = 8, column = 5, columns = 2, rows = 2),
            tile("first", row = 0, column = 0, columns = 4, rows = 1),
        )

        val normalized = GridRowGapNormalizer.normalize(placements)

        assertNotSame(placements, normalized)
        assertEquals(listOf("second", "first"), normalized.map { it.id })
        assertEquals(listOf(5, 0), normalized.map { it.position.column })
        assertEquals(listOf(GridSpan(2, 2), GridSpan(4, 1)), normalized.map { it.span })
        assertEquals(listOf(3, 0), rows(normalized))
        assertEquals(listOf(8, 0), rows(placements))
    }

    @Test
    fun normalizationIsIdempotent() {
        val placements = listOf(tile("a", 2), tile("b", 20), tile("c", 31))
        val once = GridRowGapNormalizer.normalize(placements)

        assertEquals(once, GridRowGapNormalizer.normalize(once))
    }

    @Test
    fun normalizedPlacementsDoNotCollide() {
        val placements = listOf(
            tile("a", 0, column = 0, columns = 2, rows = 2),
            tile("b", 7, column = 2, columns = 4, rows = 2),
            tile("c", 15, column = 0, columns = 6),
        )

        val normalized = GridRowGapNormalizer.normalize(placements)
        val occupiedCells = normalized.flatMap { placement ->
            (placement.position.column until placement.position.column + placement.span.columns).flatMap { column ->
                (placement.position.row until placement.position.row + placement.span.rows).map { row ->
                    column to row
                }
            }
        }

        assertEquals(occupiedCells.size, occupiedCells.toSet().size)
    }

    private fun tile(
        id: String,
        row: Int,
        column: Int = 0,
        columns: Int = 1,
        rows: Int = 1,
    ) = GridPlacement(id, GridPosition(column, row), GridSpan(columns, rows))

    private fun rows(placements: List<GridPlacement<String>>) = placements.map { it.position.row }
}
