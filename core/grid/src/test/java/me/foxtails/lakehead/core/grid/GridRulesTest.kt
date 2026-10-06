package me.foxtails.lakehead.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridRulesTest {
    @Test
    fun signedBoundsRejectNegativeAndPartialSpanButAcceptEdges() {
        assertTrue(GridRules.isWithinBounds(0L, 0L, 1, 1, 4))
        assertTrue(GridRules.isWithinBounds(2L, 2L, 2, 2, 4))
        assertTrue(!GridRules.isWithinBounds(-1L, 0L, 1, 1, 4))
        assertTrue(!GridRules.isWithinBounds(3L, 0L, 2, 1, 4))
        assertTrue(GridRules.isWithinBounds(0L, Long.MAX_VALUE, 1, 2, 4))
    }

    @Test
    fun rawBoundsRejectNegativeRows() {
        assertTrue(!GridRules.isWithinBounds(0L, -1L, 1, 1, 4))
    }

    @Test
    fun rawBoundsRejectInvalidSpans() {
        assertTrue(!GridRules.isWithinBounds(0L, 0L, 0, 1, 4))
        assertTrue(!GridRules.isWithinBounds(0L, 0L, 1, 0, 4))
    }

    @Test
    fun coordinatesAndDimensionsRejectInvalidValues() {
        assertIllegalArgument { GridPosition(-1, 0) }
        assertIllegalArgument { GridPosition(0, -1) }
        assertIllegalArgument { GridSpan(0, 1) }
        assertIllegalArgument { GridSpan(1, 0) }
    }

    @Test
    fun genericSpansCanBePlacedAtEveryCornerAndOnEachEdge() {
        val cornerPositions = listOf(
            GridPosition(0, 0),
            GridPosition(2, 0),
            GridPosition(0, 2),
            GridPosition(2, 2),
        )
        cornerPositions.forEachIndexed { index, position ->
            assertAccepted(
                GridRules.place(emptyList(), placement("corner-$index", position, 2, 2), 4),
            )
        }

        val edgeCandidates = listOf(
            placement("top", GridPosition(1, 0), 2, 1),
            placement("bottom", GridPosition(1, 3), 2, 1),
            placement("left", GridPosition(0, 1), 1, 2),
            placement("right", GridPosition(3, 1), 1, 2),
        )
        edgeCandidates.forEach { assertAccepted(GridRules.place(emptyList(), it, 4)) }
    }

    @Test
    fun genericThreeByOneSpanIsAcceptedWhenInBounds() {
        val wide = GridRules.place(
            emptyList(),
            placement("wide", GridPosition(0, 0), 3, 1),
            6,
        )
        assertAccepted(wide)

        val outsideRight = GridRules.place(
            emptyList(),
            placement("right", GridPosition(3, 0), 2, 1),
            4,
        )
        assertRejected(outsideRight, GridRules.Rejection.OUT_OF_BOUNDS)
        assertAccepted(GridRules.place(emptyList(), placement("bottom", GridPosition(0, Int.MAX_VALUE), 1, 2), 4))
    }

    @Test
    fun fourByTwoSpanFitsSixColumnsAndAcceptsHighRowsButRejectsRightOverflow() {
        assertAccepted(
            GridRules.place(
                emptyList(),
                placement("fits", GridPosition(2, 0), 4, 2),
                columns = 6,
            ),
        )
        assertRejected(
            GridRules.place(
                emptyList(),
                placement("right", GridPosition(3, 0), 4, 2),
                columns = 6,
            ),
            GridRules.Rejection.OUT_OF_BOUNDS,
        )
        assertAccepted(GridRules.place(emptyList(), placement("bottom", GridPosition(0, Int.MAX_VALUE), 4, 2), columns = 6))
    }

    @Test
    fun rectangularOverlapRejectsButEdgeTouchDoesNot() {
        val existing = listOf(placement("wide", GridPosition(1, 1), 2, 2))
        val overlap = GridRules.place(
            existing,
            placement("overlap", GridPosition(2, 2), 2, 1),
            5,
        )
        assertRejected(overlap, GridRules.Rejection.COLLISION)

        val horizontalTouch = GridRules.place(
            existing,
            placement("touch-right", GridPosition(3, 1), 2, 2),
            5,
        )
        assertAccepted(horizontalTouch)
        val verticalTouch = GridRules.place(
            existing,
            placement("touch-below", GridPosition(1, 3), 2, 1),
            5,
        )
        assertAccepted(verticalTouch)
    }

    @Test
    fun placementCanFillAHoleWithoutReorderingExistingEntries() {
        val first = placement("first", GridPosition(0, 0))
        val last = placement("last", GridPosition(2, 0))
        val existing = listOf(first, last)

        val result = accepted(GridRules.place(existing, placement("hole", GridPosition(1, 0)), 4))

        assertEquals(listOf(first, last, placement("hole", GridPosition(1, 0))), result)
    }

    @Test
    fun sameItemMoveIsAcceptedAndKeepsItsListPosition() {
        val unrelated = placement("unrelated", GridPosition(3, 0))
        val moving = placement("moving", GridPosition(0, 0), 2, 1)
        val existing = listOf(unrelated, moving)

        val result = accepted(
            GridRules.move(existing, "moving", GridPosition(1, 1), columns = 5),
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
            GridRules.move(existing, "moving", GridPosition(1, 0), columns = 6),
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
            GridRules.resize(existing, "resizing", GridSpan(2, 1), columns = 5),
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
            GridRules.move(existing, "moving", GridPosition(1, 0), 5),
            GridRules.Rejection.COLLISION,
        )
        assertRejected(
            GridRules.resize(existing, "moving", GridSpan(2, 1), 5),
            GridRules.Rejection.COLLISION,
        )
        assertEquals(listOf(moving, blocker), existing)
    }

    @Test
    fun genericResizeIsAcceptedWhenInBoundsAndMoveRejectsOutOfBounds() {
        val existing = listOf(placement("item", GridPosition(0, 0)))
        val resizeResult = accepted(
            GridRules.resize(existing, "item", GridSpan(3, 1), 5),
        )
        assertEquals(listOf(placement("item", GridPosition(0, 0), 3, 1)), resizeResult)
        assertRejected(
            GridRules.move(existing, "item", GridPosition(5, 0), 5),
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
            GridRules.place(existing, replacement, 6, replacingId = "replace-me"),
        )
        assertEquals(listOf(first, replacement, last), result)

        val otherCollision = GridRules.place(
            existing,
            placement("replacement", GridPosition(0, 0)),
            6,
            replacingId = "replace-me",
        )
        assertRejected(otherCollision, GridRules.Rejection.COLLISION)
        assertEquals(listOf(first, target, last), existing)
    }

    @Test
    fun duplicatePlacementIdWithoutReplacementIsACollision() {
        val existing = listOf(placement("duplicate", GridPosition(0, 0)))

        assertRejected(
            GridRules.place(existing, placement("duplicate", GridPosition(2, 0)), 4),
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
                replacingId = "target",
            ),
            GridRules.Rejection.COLLISION,
        )
        assertEquals(listOf(target, third), existing)
    }

    @Test
    fun replacementWithGenericSpanSucceedsAtOriginalIndex() {
        val target = placement("target", GridPosition(0, 0))
        val existing = listOf(target)
        val replacement = placement("replacement", GridPosition(1, 0), 3, 1)

        val result = accepted(
            GridRules.place(
                existing,
                replacement,
                columns = 6,
                replacingId = "target",
            ),
        )
        assertEquals(listOf(replacement), result)

        assertRejected(
            GridRules.place(
                existing,
                placement("replacement", GridPosition(3, 0), 4, 2),
                columns = 6,
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

        assertIllegalArgument { GridRules.place(existing, candidate, columns = 0) }
        assertIllegalArgument {
            GridRules.move(existing, "item", GridPosition(1, 0), columns = -1)
        }
        assertIllegalArgument {
            GridRules.resize(existing, "item", GridSpan(2, 1), columns = 0)
        }
    }

    @Test
    fun unknownItemsAreReportedForMoveResizeAndReplacement() {
        val existing = listOf(placement("known", GridPosition(0, 0)))
        assertRejected(
            GridRules.move(existing, "missing", GridPosition(1, 0), 4),
            GridRules.Rejection.UNKNOWN_ITEM,
        )
        assertRejected(
            GridRules.resize(existing, "missing", GridSpan(2, 1), 4),
            GridRules.Rejection.UNKNOWN_ITEM,
        )
        assertRejected(
            GridRules.place(existing, placement("new", GridPosition(2, 0)), 4, "missing"),
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
            GridRules.place(mutableInput, placement("blocked", GridPosition(0, 0)), 5),
            GridRules.Rejection.COLLISION,
        )
        assertRejected(
            GridRules.move(mutableInput, "first", GridPosition(1, 0), 5),
            GridRules.Rejection.COLLISION,
        )
        assertRejected(
            GridRules.resize(mutableInput, "first", GridSpan(2, 1), 5),
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

        val result = accepted(GridRules.move(existing, "moved", GridPosition(1, 0), 6))

        assertEquals(listOf(before, moved.copy(position = GridPosition(1, 0)), after), result)
        assertEquals(existing, listOf(before, moved, after))
        assertNotEquals(existing, result)
    }

    @Test
    fun genericSpansPlaceSuccessfullyWhenInBoundsAndFree() {
        assertAccepted(GridRules.place(emptyList(), placement("a", GridPosition(0, 0), 3, 2), 6))
        assertAccepted(GridRules.place(emptyList(), placement("b", GridPosition(0, 0), 3, 3), 6))
        assertAccepted(GridRules.place(emptyList(), placement("c", GridPosition(0, 0), 4, 3), 6))
    }

    @Test
    fun genericSpansMoveAndResizeSuccessfully() {
        val existing = listOf(
            placement("m", GridPosition(0, 0), 3, 2),
            placement("u", GridPosition(4, 4)),
        )
        assertAccepted(GridRules.move(existing, "m", GridPosition(1, 1), 6))
        assertAccepted(GridRules.resize(existing, "m", GridSpan(3, 3), 6))
        assertAccepted(GridRules.resize(existing, "m", GridSpan(4, 3), 6))
    }

    @Test
    fun oversizedGenericSpansRejectHorizontalOverflowAndAcceptHighRows() {
        assertRejected(
            GridRules.place(emptyList(), placement("r", GridPosition(4, 0), 3, 2), 6),
            GridRules.Rejection.OUT_OF_BOUNDS,
        )
        assertAccepted(GridRules.place(emptyList(), placement("b", GridPosition(0, Int.MAX_VALUE), 4, 3), 6))
        assertAccepted(GridRules.place(emptyList(), placement("o", GridPosition(0, Int.MAX_VALUE - 1), 1, 2), 6))
    }

    @Test
    fun genericSpanCollisionVsEdgeTouch() {
        val existing = listOf(placement("e", GridPosition(0, 0), 3, 2))
        assertRejected(
            GridRules.place(existing, placement("overlap", GridPosition(2, 1), 3, 2), 6),
            GridRules.Rejection.COLLISION,
        )
        assertAccepted(GridRules.place(existing, placement("touchR", GridPosition(3, 0), 3, 2), 6))
        assertAccepted(GridRules.place(existing, placement("touchB", GridPosition(0, 2), 3, 2), 6))
    }

    @Test
    fun genericSelfMoveAndResizeIgnoreOwnFootprint() {
        val existing = listOf(
            placement("s", GridPosition(0, 0), 3, 2),
            placement("u", GridPosition(5, 0)),
        )
        assertAccepted(GridRules.move(existing, "s", GridPosition(1, 0), 6))
        assertAccepted(GridRules.resize(existing, "s", GridSpan(4, 3), 6))
    }

    @Test
    fun genericReplacementKeepsIndexAndUnknownStillUnknown() {
        val before = placement("before", GridPosition(0, 0))
        val target = placement("target", GridPosition(1, 0))
        val after = placement("after", GridPosition(4, 0))
        val existing = listOf(before, target, after)
        val replacement = placement("replacement", GridPosition(1, 0), 3, 2)

        val result = accepted(
            GridRules.place(existing, replacement, 6, replacingId = "target"),
        )
        assertEquals(listOf(before, replacement, after), result)

        assertRejected(
            GridRules.place(existing, placement("missing", GridPosition(1, 0)), 6, replacingId = "missing"),
            GridRules.Rejection.UNKNOWN_ITEM,
        )
    }

    @Test
    fun genericRejectionsMutateNothing() {
        val before = listOf(
            placement("a", GridPosition(0, 0), 3, 2),
            placement("b", GridPosition(4, 0)),
        )
        val mutableInput = before.toMutableList()
        assertRejected(
            GridRules.place(mutableInput, placement("blocked", GridPosition(4, 0)), 6),
            GridRules.Rejection.COLLISION,
        )
        assertRejected(
            GridRules.move(mutableInput, "a", GridPosition(3, 0), 6),
            GridRules.Rejection.COLLISION,
        )
        assertEquals(before, mutableInput)
    }

    // ---- move-only invariants ----

    @Test
    fun move_toSamePosition_isAccepted() {
        // Moving to the current position is a valid no-op.
        val existing = listOf(placement("item", GridPosition(2, 3)))
        val result = accepted(GridRules.move(existing, "item", GridPosition(2, 3), 6))
        assertEquals(listOf(placement("item", GridPosition(2, 3))), result)
    }

    @Test
    fun move_preservesSpan() {
        // Move only changes position; span is untouched.
        val existing = listOf(placement("wide", GridPosition(0, 0), 3, 2))
        val result = accepted(GridRules.move(existing, "wide", GridPosition(2, 2), 6))
        assertEquals(GridSpan(3, 2), result[0].span)
        assertEquals(GridPosition(2, 2), result[0].position)
    }

    @Test
    fun move_intoOwnFootprint_isAccepted() {
        // A 2x2 at (0,0) can move to (1,1) which overlaps its old cells — own
        // footprint is ignored.
        val existing = listOf(placement("self", GridPosition(0, 0), 2, 2))
        val result = accepted(GridRules.move(existing, "self", GridPosition(1, 1), 6))
        assertEquals(GridPosition(1, 1), result[0].position)
    }

    @Test
    fun move_blockedByOtherItem_rejected() {
        val existing = listOf(
            placement("mover", GridPosition(0, 0)),
            placement("blocker", GridPosition(2, 0)),
        )
        assertRejected(
            GridRules.move(existing, "mover", GridPosition(2, 0), 6),
            GridRules.Rejection.COLLISION,
        )
    }

    @Test
    fun move_onlyPositionChanges_unrelatedUntouched() {
        val before = placement("before", GridPosition(0, 0))
        val mover = placement("mover", GridPosition(2, 0), 2, 1)
        val after = placement("after", GridPosition(0, 3))
        val existing = listOf(before, mover, after)

        val result = accepted(GridRules.move(existing, "mover", GridPosition(4, 2), 6))

        // Order and unrelated entries preserved.
        assertEquals(before, result[0])
        assertEquals(mover.copy(position = GridPosition(4, 2)), result[1])
        assertEquals(after, result[2])
    }

    @Test
    fun move_toOriginCorner_isAccepted() {
        val existing = listOf(placement("item", GridPosition(3, 3)))
        val result = accepted(GridRules.move(existing, "item", GridPosition(0, 0), 6))
        assertEquals(GridPosition(0, 0), result[0].position)
    }

    @Test
    fun move_rejectedPreservesInputList() {
        val mover = placement("mover", GridPosition(0, 0))
        val blocker = placement("blocker", GridPosition(3, 0))
        val mutableInput = mutableListOf(mover, blocker)
        val before = mutableInput.toList()

        assertRejected(
            GridRules.move(mutableInput, "mover", GridPosition(3, 0), 6),
            GridRules.Rejection.COLLISION,
        )
        assertEquals(before, mutableInput)
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
