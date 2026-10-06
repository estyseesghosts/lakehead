package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [ShortcutOrdering]: ordering, unpin-collapse,
 * resize-retain, restore, centered vs app-with-pinned, at-capacity, and purity.
 */
class ShortcutOrderingTest {

    private val s1 = ref("s1")
    private val s2 = ref("s2")
    private val s3 = ref("s3")
    private val s4 = ref("s4")
    private val s5 = ref("s5")

    // --- visibleShortcuts ---

    @Test
    fun visibleTakesFirstNCapacityItems() {
        val full = listOf(s1, s2, s3, s4, s5)
        assertEquals(listOf(s1, s2, s3), ShortcutOrdering.visibleShortcuts(full, 3))
    }

    @Test
    fun visibleReturnsAllWhenCapacityExceedsListSize() {
        val full = listOf(s1, s2)
        assertEquals(full, ShortcutOrdering.visibleShortcuts(full, 5))
    }

    @Test
    fun visibleReturnsEmptyForZeroCapacity() {
        assertEquals(emptyList<ShortcutRef>(), ShortcutOrdering.visibleShortcuts(listOf(s1, s2), 0))
    }

    @Test
    fun visibleReturnsEmptyForEmptyList() {
        assertEquals(emptyList<ShortcutRef>(), ShortcutOrdering.visibleShortcuts<ShortcutRef>(emptyList(), 3))
    }

    @Test
    fun visiblePreservesOrder() {
        val full = listOf(s3, s1, s2)
        assertEquals(listOf(s3, s1), ShortcutOrdering.visibleShortcuts(full, 2))
    }

    // --- overflowShortcuts ---

    @Test
    fun overflowReturnsItemsBeyondCapacity() {
        val full = listOf(s1, s2, s3, s4, s5)
        assertEquals(listOf(s4, s5), ShortcutOrdering.overflowShortcuts(full, 3))
    }

    @Test
    fun overflowIsEmptyWhenAllFit() {
        val full = listOf(s1, s2)
        assertEquals(emptyList<ShortcutRef>(), ShortcutOrdering.overflowShortcuts(full, 5))
    }

    @Test
    fun overflowIsEntireListForZeroCapacity() {
        val full = listOf(s1, s2)
        assertEquals(full, ShortcutOrdering.overflowShortcuts(full, 0))
    }

    // --- pin ---

    @Test
    fun pinAppendsToListWhenBelowCapacity() {
        val full = listOf(s1, s2)
        val result = ShortcutOrdering.pin(full, s3, 5)
        assertEquals(listOf(s1, s2, s3), result)
    }

    @Test
    fun pinReturnsNullWhenAtCapacity() {
        val full = listOf(s1, s2, s3)
        assertNull(ShortcutOrdering.pin(full, s4, 3))
    }

    @Test
    fun pinReturnsNullWhenOverCapacity() {
        val full = listOf(s1, s2, s3, s4, s5)
        assertNull(ShortcutOrdering.pin(full, ref("s6"), 3))
    }

    @Test
    fun pinToEmptyListWithCapacityOne() {
        val result = ShortcutOrdering.pin(emptyList(), s1, 1)
        assertEquals(listOf(s1), result)
    }

    @Test
    fun pinToEmptyListWithZeroCapacityReturnsNull() {
        assertNull(ShortcutOrdering.pin(emptyList(), s1, 0))
    }

    // --- unpin ---

    @Test
    fun unpinRemovesSpecifiedShortcut() {
        val full = listOf(s1, s2, s3)
        assertEquals(listOf(s1, s3), ShortcutOrdering.unpin(full, s2))
    }

    @Test
    fun unpinCollapseFillsVisibleGapFromOverflow() {
        // Full list: [s1, s2, s3, s4, s5], capacity 3 → visible [s1, s2, s3]
        // Unpin s2 → [s1, s3, s4, s5] → visible [s1, s3, s4] — s4 collapses in
        val full = listOf(s1, s2, s3, s4, s5)
        val after = ShortcutOrdering.unpin(full, s2)
        assertEquals(listOf(s1, s3, s4, s5), after)
        assertEquals(listOf(s1, s3, s4), ShortcutOrdering.visibleShortcuts(after, 3))
    }

    @Test
    fun unpinReturnsSameListWhenShortcutNotFound() {
        val full = listOf(s1, s2, s3)
        assertEquals(full, ShortcutOrdering.unpin(full, ref("nonexistent")))
    }

    @Test
    fun unpinFromEmptyListReturnsEmpty() {
        assertEquals(emptyList<ShortcutRef>(), ShortcutOrdering.unpin(emptyList(), s1))
    }

    @Test
    fun unpinLastItemReturnsEmpty() {
        assertEquals(emptyList<ShortcutRef>(), ShortcutOrdering.unpin(listOf(s1), s1))
    }

    // --- isLastUnpin ---

    @Test
    fun isLastUnpinTrueWhenSingleItem() {
        assertTrue(ShortcutOrdering.isLastUnpin(listOf(s1), s1, 3))
    }

    @Test
    fun isLastUnpinTrueForZeroCapacity() {
        assertTrue(ShortcutOrdering.isLastUnpin(listOf(s1, s2), s1, 0))
    }

    @Test
    fun isLastUnpinTrueWhenRemovingLastItem() {
        assertTrue(ShortcutOrdering.isLastUnpin(listOf(s1), s1, 5))
    }

    @Test
    fun isLastUnpinFalseWhenMultipleRemain() {
        assertEquals(false, ShortcutOrdering.isLastUnpin(listOf(s1, s2, s3), s1, 3))
    }

    @Test
    fun isLastUnpinTrueWhenRemovingFromEmptyList() {
        assertTrue(ShortcutOrdering.isLastUnpin(emptyList(), s1, 3))
    }

    // --- resize ---

    @Test
    fun resizeRetainsFullListOnlyChangesVisible() {
        val full = listOf(s1, s2, s3, s4, s5)
        val visible = ShortcutOrdering.resize(full, oldCapacity = 3, newCapacity = 2)
        assertEquals(listOf(s1, s2), visible)
    }

    @Test
    fun resizeLargerShowsMoreItems() {
        val full = listOf(s1, s2, s3, s4, s5)
        val visible = ShortcutOrdering.resize(full, oldCapacity = 2, newCapacity = 4)
        assertEquals(listOf(s1, s2, s3, s4), visible)
    }

    @Test
    fun resizeToZeroHidesAll() {
        val full = listOf(s1, s2, s3)
        assertEquals(emptyList<ShortcutRef>(), ShortcutOrdering.resize(full, 3, 0))
    }

    @Test
    fun resizePreservesOrder() {
        val full = listOf(s3, s1, s2)
        assertEquals(listOf(s3, s1), ShortcutOrdering.resize(full, 1, 2))
    }

    // --- restore ---

    @Test
    fun restoreReturnsVisibleSubsetForCapacity() {
        val full = listOf(s1, s2, s3, s4, s5)
        assertEquals(listOf(s1, s2, s3), ShortcutOrdering.restore(full, 3))
    }

    @Test
    fun restoreReturnsAllWhenCapacityExceedsList() {
        val full = listOf(s1, s2)
        assertEquals(full, ShortcutOrdering.restore(full, 10))
    }

    @Test
    fun restoreFromEmptyReturnsEmpty() {
        assertEquals(emptyList<ShortcutRef>(), ShortcutOrdering.restore<ShortcutRef>(emptyList(), 5))
    }

    // --- layout ---

    @Test
    fun centeredLayoutReturnsVisibleListUnchanged() {
        val visible = listOf(s1, s2, s3)
        assertEquals(visible, ShortcutOrdering.layoutShortcuts(visible, ShortcutOrdering.Layout.CENTERED))
    }

    @Test
    fun appWithPinnedLayoutReturnsVisibleListUnchanged() {
        val visible = listOf(s1, s2, s3)
        assertEquals(visible, ShortcutOrdering.layoutShortcuts(visible, ShortcutOrdering.Layout.APP_WITH_PINNED))
    }

    @Test
    fun layoutOfEmptyListReturnsEmpty() {
        assertEquals(
            emptyList<ShortcutRef>(),
            ShortcutOrdering.layoutShortcuts(emptyList(), ShortcutOrdering.Layout.CENTERED),
        )
    }

    // --- purity ---

    @Test
    fun allOperationsArePureSameInputProducesSameOutput() {
        val full = listOf(s1, s2, s3, s4, s5)

        // visibleShortcuts
        val v1 = ShortcutOrdering.visibleShortcuts(full, 3)
        val v2 = ShortcutOrdering.visibleShortcuts(full, 3)
        assertEquals(v1, v2)

        // overflowShortcuts
        val o1 = ShortcutOrdering.overflowShortcuts(full, 3)
        val o2 = ShortcutOrdering.overflowShortcuts(full, 3)
        assertEquals(o1, o2)

        // pin
        val p1 = ShortcutOrdering.pin(listOf(s1, s2), s3, 5)
        val p2 = ShortcutOrdering.pin(listOf(s1, s2), s3, 5)
        assertEquals(p1, p2)

        // unpin
        val u1 = ShortcutOrdering.unpin(full, s2)
        val u2 = ShortcutOrdering.unpin(full, s2)
        assertEquals(u1, u2)
        assertNotSame(u1, u2) // different list instances

        // resize
        val r1 = ShortcutOrdering.resize(full, 3, 2)
        val r2 = ShortcutOrdering.resize(full, 3, 2)
        assertEquals(r1, r2)

        // restore
        val re1 = ShortcutOrdering.restore(full, 3)
        val re2 = ShortcutOrdering.restore(full, 3)
        assertEquals(re1, re2)
    }

    @Test
    fun pinDoesNotMutateInputList() {
        val original = listOf(s1, s2)
        val result = ShortcutOrdering.pin(original, s3, 5)
        assertEquals(listOf(s1, s2), original) // unchanged
        assertEquals(listOf(s1, s2, s3), result)
    }

    @Test
    fun unpinDoesNotMutateInputList() {
        val original = listOf(s1, s2, s3)
        val result = ShortcutOrdering.unpin(original, s2)
        assertEquals(listOf(s1, s2, s3), original) // unchanged
        assertEquals(listOf(s1, s3), result)
    }

    // --- combined workflow ---

    @Test
    fun pinUnpinResizeWorkflowPreservesCorrectState() {
        var full = listOf<ShortcutRef>()

        // Pin 3 shortcuts with capacity 3
        full = ShortcutOrdering.pin(full, s1, 3)!!
        full = ShortcutOrdering.pin(full, s2, 3)!!
        full = ShortcutOrdering.pin(full, s3, 3)!!
        assertEquals(listOf(s1, s2, s3), full)

        // At capacity — pin returns null
        assertNull(ShortcutOrdering.pin(full, s4, 3))

        // Visible equals full since all fit
        assertEquals(full, ShortcutOrdering.visibleShortcuts(full, 3))

        // Unpin middle — s3 collapses into visible
        full = ShortcutOrdering.unpin(full, s2)
        assertEquals(listOf(s1, s3), full)

        // Resize to 1 — only s1 visible, full list preserved
        assertEquals(listOf(s1), ShortcutOrdering.resize(full, 2, 1))

        // Restore at capacity 2 — both visible again
        assertEquals(listOf(s1, s3), ShortcutOrdering.restore(full, 2))
    }

    // --- helper ---

    private fun ref(id: String) = ShortcutRef("com.example", ProfileId("profile"), id)
}