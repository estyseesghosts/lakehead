package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridRulesTest {
    @Test
    fun supportedSpansAreExactlyTheB1Set() {
        assertEquals(
            setOf(
                GridSpan(1, 1),
                GridSpan(2, 1),
                GridSpan(1, 2),
                GridSpan(2, 2),
                GridSpan(4, 2),
            ),
            GridSpan.SUPPORTED,
        )
        GridSpan.SUPPORTED.forEach { assertTrue(it.isSupported) }
        assertFalse(GridSpan(3, 1).isSupported)
        assertFalse(GridSpan(1, 3).isSupported)
    }

    @Test
    fun coordinatesAndDimensionsRejectInvalidValues() {
        assertIllegalArgument { GridPosition(-1, 0) }
        assertIllegalArgument { GridPosition(0, -1) }
        assertIllegalArgument { GridSpan(0, 1) }
        assertIllegalArgument { GridSpan(1, 0) }
    }

    @Test
    fun supportedSpansCanBePlacedAtEveryCornerAndOnEachEdge() {
        val cornerPositions = listOf(
            GridPosition(0, 0),
            GridPosition(2, 0),
            GridPosition(0, 2),
            GridPosition(2, 2),
        )
        cornerPositions.forEachIndexed { index, position ->
            assertAccepted(
                GridRules.place(emptyList(), placement("corner-$index", position, 2, 2), 4, 4),
            )
        }

        val edgeCandidates = listOf(
            placement("top", GridPosition(1, 0), 2, 1),
            placement("bottom", GridPosition(1, 3), 2, 1),
            placement("left", GridPosition(0, 1), 1, 2),
            placement("right", GridPosition(3, 1), 1, 2),
        )
        edgeCandidates.forEach { assertAccepted(GridRules.place(emptyList(), it, 4, 4)) }
    }

    @Test
    fun outOfBoundsIsDistinctFromUnsupportedSpan() {
        val unsupported = GridRules.place(
            emptyList(),
            placement("wide", GridPosition(0, 0), 3, 1),
            6,
            4,
        )
        assertRejected(unsupported, GridRules.Rejection.UNSUPPORTED_SPAN)

        val outsideRight = GridRules.place(
            emptyList(),
            placement("right", GridPosition(3, 0), 2, 1),
            4,
            4,
        )
        assertRejected(outsideRight, GridRules.Rejection.OUT_OF_BOUNDS)
        val outsideBottom = GridRules.place(
            emptyList(),
            placement("bottom", GridPosition(0, 3), 1, 2),
            4,
            4,
        )
        assertRejected(outsideBottom, GridRules.Rejection.OUT_OF_BOUNDS)
    }

    @Test
    fun fourByTwoSpanFitsSixColumnsButRejectsRightAndBottomOverflow() {
        assertAccepted(
            GridRules.place(
                emptyList(),
                placement("fits", GridPosition(2, 0), 4, 2),
                columns = 6,
                rows = 4,
            ),
        )
        assertRejected(
            GridRules.place(
                emptyList(),
                placement("right", GridPosition(3, 0), 4, 2),
                columns = 6,
                rows = 4,
            ),
            GridRules.Rejection.OUT_OF_BOUNDS,
        )
        assertRejected(
            GridRules.place(
                emptyList(),
                placement("bottom", GridPosition(0, 3), 4, 2),
                columns = 6,
                rows = 4,
            ),
            GridRules.Rejection.OUT_OF_BOUNDS,
        )
    }

    @Test
    fun rectangularOverlapRejectsButEdgeTouchDoesNot() {
        val existing = listOf(placement("wide", GridPosition(1, 1), 2, 2))
        val overlap = GridRules.place(
            existing,
            placement("overlap", GridPosition(2, 2), 2, 1),
            5,
            5,
        )
        assertRejected(overlap, GridRules.Rejection.COLLISION)

        val horizontalTouch = GridRules.place(
            existing,
            placement("touch-right", GridPosition(3, 1), 2, 2),
            5,
            5,
        )
        assertAccepted(horizontalTouch)
        val verticalTouch = GridRules.place(
            existing,
            placement("touch-below", GridPosition(1, 3), 2, 1),
            5,
            5,
        )
        assertAccepted(verticalTouch)
    }

    @Test
    fun placementCanFillAHoleWithoutReorderingExistingEntries() {
        val first = placement("first", GridPosition(0, 0))
        val last = placement("last", GridPosition(2, 0))
        val existing = listOf(first, last)

        val result = accepted(GridRules.place(existing, placement("hole", GridPosition(1, 0)), 4, 3))

        assertEquals(listOf(first, last, placement("hole", GridPosition(1, 0))), result)
    }

    @Test
    fun sameItemMoveIsAcceptedAndKeepsItsListPosition() {
        val unrelated = placement("unrelated", GridPosition(3, 0))
        val moving = placement("moving", GridPosition(0, 0), 2, 1)
        val existing = listOf(unrelated, moving)

        val result = accepted(
            GridRules.move(existing, "moving", GridPosition(1, 1), columns = 5, rows = 4),
        )

        assertEquals(listOf(unrelated, moving.copy(position = GridPosition(1, 1))), result)
        assertEquals(listOf(unrelated, moving), existing)
    }

    @Test
    fun overlappingMoveIgnoresItsOldFootprintAndPreservesUnrelatedPlacement() {
        val moving = placement("moving", GridPosition(0, 0), 2, 2)
        val unrelated = placement("unrelated", GridPosition(4, 0))
        val existing = listOf(moving, unrelated)

        val result = accepted(
            GridRules.move(existing, "moving", GridPosition(1, 0), columns = 6, rows = 4),
        )

        assertEquals(
            listOf(moving.copy(position = GridPosition(1, 0)), unrelated),
            result,
        )
        assertEquals(listOf(moving, unrelated), existing)
    }

    @Test
    fun sameOriginResizeIgnoresItsOldFootprintAndPreservesUnrelatedPlacement() {
        val resizing = placement("resizing", GridPosition(0, 0))
        val unrelated = placement("unrelated", GridPosition(3, 0))
        val existing = listOf(resizing, unrelated)

        val result = accepted(
            GridRules.resize(existing, "resizing", GridSpan(2, 1), columns = 5, rows = 4),
        )

        assertEquals(listOf(resizing.copy(span = GridSpan(2, 1)), unrelated), result)
        assertEquals(listOf(resizing, unrelated), existing)
    }

    @Test
    fun moveAndResizeRejectOtherItemCollisions() {
        val moving = placement("moving", GridPosition(0, 0))
        val blocker = placement("blocker", GridPosition(1, 0))
        val existing = listOf(moving, blocker)

        assertRejected(
            GridRules.move(existing, "moving", GridPosition(1, 0), 5, 4),
            GridRules.Rejection.COLLISION,
        )
        assertRejected(
            GridRules.resize(existing, "moving", GridSpan(2, 1), 5, 4),
            GridRules.Rejection.COLLISION,
        )
        assertEquals(listOf(moving, blocker), existing)
    }

    @Test
    fun moveAndResizeDistinguishUnsupportedAndOutOfBounds() {
        val existing = listOf(placement("item", GridPosition(0, 0)))
        assertRejected(
            GridRules.resize(existing, "item", GridSpan(3, 1), 5, 4),
            GridRules.Rejection.UNSUPPORTED_SPAN,
        )
        assertRejected(
            GridRules.move(existing, "item", GridPosition(5, 0), 5, 4),
            GridRules.Rejection.OUT_OF_BOUNDS,
        )
    }

    @Test
    fun explicitReplacementSucceedsOnlyWhenThereAreNoOtherCollisions() {
        val first = placement("first", GridPosition(0, 0))
        val target = placement("replace-me", GridPosition(1, 0), 2, 1)
        val last = placement("last", GridPosition(4, 0))
        val existing = listOf(first, target, last)
        val replacement = placement("replacement", GridPosition(1, 0), 2, 1)

        val result = accepted(
            GridRules.place(existing, replacement, 6, 4, replacingId = "replace-me"),
        )
        assertEquals(listOf(first, replacement, last), result)

        val otherCollision = GridRules.place(
            existing,
            placement("replacement", GridPosition(0, 0)),
            6,
            4,
            replacingId = "replace-me",
        )
        assertRejected(otherCollision, GridRules.Rejection.COLLISION)
        assertEquals(listOf(first, target, last), existing)
    }

    @Test
    fun duplicatePlacementIdWithoutReplacementIsACollision() {
        val existing = listOf(placement("duplicate", GridPosition(0, 0)))

        assertRejected(
            GridRules.place(existing, placement("duplicate", GridPosition(2, 0)), 4, 4),
            GridRules.Rejection.COLLISION,
        )
    }

    @Test
    fun replacementCandidateCollidingWithThirdPlacementIsRejected() {
        val target = placement("target", GridPosition(0, 0))
        val third = placement("third", GridPosition(2, 0))
        val existing = listOf(target, third)

        assertRejected(
            GridRules.place(
                existing,
                placement("replacement", GridPosition(2, 0)),
                columns = 5,
                rows = 4,
                replacingId = "target",
            ),
            GridRules.Rejection.COLLISION,
        )
        assertEquals(listOf(target, third), existing)
    }

    @Test
    fun replacementReportsUnsupportedSpanAndOutOfBoundsSeparately() {
        val target = placement("target", GridPosition(0, 0))
        val existing = listOf(target)

        assertRejected(
            GridRules.place(
                existing,
                placement("replacement", GridPosition(1, 0), 3, 1),
                columns = 6,
                rows = 4,
                replacingId = "target",
            ),
            GridRules.Rejection.UNSUPPORTED_SPAN,
        )
        assertRejected(
            GridRules.place(
                existing,
                placement("replacement", GridPosition(3, 0), 4, 2),
                columns = 6,
                rows = 4,
                replacingId = "target",
            ),
            GridRules.Rejection.OUT_OF_BOUNDS,
        )
        assertEquals(listOf(target), existing)
    }

    @Test
    fun placeMoveAndResizeRejectInvalidGridSizes() {
        val existing = listOf(placement("item", GridPosition(0, 0)))
        val candidate = placement("new", GridPosition(1, 0))

        assertIllegalArgument { GridRules.place(existing, candidate, columns = 0, rows = 4) }
        assertIllegalArgument { GridRules.place(existing, candidate, columns = 4, rows = 0) }
        assertIllegalArgument {
            GridRules.move(existing, "item", GridPosition(1, 0), columns = -1, rows = 4)
        }
        assertIllegalArgument {
            GridRules.move(existing, "item", GridPosition(1, 0), columns = 4, rows = -1)
        }
        assertIllegalArgument {
            GridRules.resize(existing, "item", GridSpan(2, 1), columns = 0, rows = 4)
        }
        assertIllegalArgument {
            GridRules.resize(existing, "item", GridSpan(2, 1), columns = 4, rows = 0)
        }
    }

    @Test
    fun unknownItemsAreReportedForMoveResizeAndReplacement() {
        val existing = listOf(placement("known", GridPosition(0, 0)))
        assertRejected(
            GridRules.move(existing, "missing", GridPosition(1, 0), 4, 4),
            GridRules.Rejection.UNKNOWN_ITEM,
        )
        assertRejected(
            GridRules.resize(existing, "missing", GridSpan(2, 1), 4, 4),
            GridRules.Rejection.UNKNOWN_ITEM,
        )
        assertRejected(
            GridRules.place(existing, placement("new", GridPosition(2, 0)), 4, 4, "missing"),
            GridRules.Rejection.UNKNOWN_ITEM,
        )
    }

    @Test
    fun rejectedOperationsPreserveTheExactInputListAndEntries() {
        val first = placement("first", GridPosition(0, 0))
        val second = placement("second", GridPosition(1, 0))
        val mutableInput = mutableListOf(first, second)
        val before = mutableInput.toList()

        assertRejected(
            GridRules.place(mutableInput, placement("blocked", GridPosition(0, 0)), 5, 4),
            GridRules.Rejection.COLLISION,
        )
        assertRejected(
            GridRules.move(mutableInput, "first", GridPosition(1, 0), 5, 4),
            GridRules.Rejection.COLLISION,
        )
        assertRejected(
            GridRules.resize(mutableInput, "first", GridSpan(2, 1), 5, 4),
            GridRules.Rejection.COLLISION,
        )
        assertEquals(before, mutableInput)
    }

    @Test
    fun acceptedOperationsPreserveUnrelatedEntriesAndTheirOrder() {
        val before = placement("before", GridPosition(4, 0))
        val moved = placement("moved", GridPosition(0, 0))
        val after = placement("after", GridPosition(4, 2))
        val existing = listOf(before, moved, after)

        val result = accepted(GridRules.move(existing, "moved", GridPosition(1, 0), 6, 4))

        assertEquals(listOf(before, moved.copy(position = GridPosition(1, 0)), after), result)
        assertEquals(existing, listOf(before, moved, after))
        assertNotEquals(existing, result)
    }

    private fun placement(
        id: String,
        position: GridPosition,
        columns: Int = 1,
        rows: Int = 1,
    ) = GridPlacement(id, position, GridSpan(columns, rows))

    private fun accepted(result: GridRules.Result<String>): List<GridPlacement<String>> {
        assertTrue("expected accepted result, got $result", result is GridRules.Result.Accepted)
        return (result as GridRules.Result.Accepted).placements
    }

    private fun assertAccepted(result: GridRules.Result<String>) {
        assertTrue("expected accepted result, got $result", result is GridRules.Result.Accepted)
    }

    private fun assertRejected(result: GridRules.Result<String>, reason: GridRules.Rejection) {
        assertEquals(GridRules.Result.Rejected(reason), result)
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}
