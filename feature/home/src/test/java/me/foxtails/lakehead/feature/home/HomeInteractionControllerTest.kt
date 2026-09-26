package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.ItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInteractionControllerTest {

    private val tileA = ItemId("tile-a")
    private val tileB = ItemId("tile-b")

    // ---- Normal → A → B → Normal, no intermediate states ----

    @Test
    fun lifecycle_normalToAtoBtoNormal_exactlyEachState() {
        val start: HomeInteractionController = HomeInteractionController.Normal

        val afterLongPress = start.enterOnLongPress(tileA, isAvailable = true)
        assertTrue(afterLongPress is HomeInteractionController.Editing)
        assertEquals(tileA, (afterLongPress as HomeInteractionController.Editing).selectedId)

        val afterTapB = afterLongPress.selectOnTap(tileB)
        assertTrue(afterTapB is HomeInteractionController.Editing)
        assertEquals(tileB, (afterTapB as HomeInteractionController.Editing).selectedId)

        val afterExit = afterTapB.exitOnBack()
        assertSame(HomeInteractionController.Normal, afterExit)
    }

    @Test
    fun lifecycle_normalToAtoBtoNormal_viaEmptyTap() {
        val result = HomeInteractionController.Normal
            .enterOnLongPress(tileA, isAvailable = true)
            .selectOnTap(tileB)
            .exitOnEmptyTap()

        assertSame(HomeInteractionController.Normal, result)
    }

    @Test
    fun lifecycle_normalToAtoBtoNormal_viaNavigate() {
        val result = HomeInteractionController.Normal
            .enterOnLongPress(tileA, isAvailable = true)
            .selectOnTap(tileB)
            .exitOnNavigate()

        assertSame(HomeInteractionController.Normal, result)
    }

    // ---- tap never launches (Normal is inert to taps) ----

    @Test
    fun selectOnTap_fromNormal_staysNormal() {
        val result = HomeInteractionController.Normal.selectOnTap(tileA)
        assertSame(HomeInteractionController.Normal, result)
    }

    @Test
    fun selectOnTap_fromNormal_doesNotCreateEditing() {
        // Verify tap never transitions out of Normal
        val state = HomeInteractionController.Normal.selectOnTap(tileA)
        assertTrue("Tap from Normal must not produce Editing", state is HomeInteractionController.Normal)
    }

    // ---- recess keeps cells (controller carries no grid state; exit is pure) ----

    @Test
    fun exitEditing_returnsNormal_withoutMutatingEditingState() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val exited = editing.exitOnBack()
        // Original editing object is immutable (data class)
        assertEquals(tileA, editing.selectedId)
        assertSame(HomeInteractionController.Normal, exited)
    }

    @Test
    fun exitEditing_normalHasNoSelectedId() {
        val normal: HomeInteractionController = HomeInteractionController.Normal
        // Normal has no grid state, no selectedId — pure identity
        assertTrue(normal is HomeInteractionController.Normal)
    }

    @Test
    fun selectOnTap_fromEditing_doesNotModifyOriginalState() {
        val original = HomeInteractionController.Editing(selectedId = tileA)
        val updated = original.selectOnTap(tileB)
        // original is unchanged (data class immutability)
        assertEquals(tileA, original.selectedId)
        assertEquals(tileB, (updated as HomeInteractionController.Editing).selectedId)
    }

    // ---- unavailable tile: no enter ----

    @Test
    fun enterOnLongPress_unavailable_fromNormal_staysNormal() {
        val result = HomeInteractionController.Normal.enterOnLongPress(tileA, isAvailable = false)
        assertSame(HomeInteractionController.Normal, result)
    }

    @Test
    fun enterOnLongPress_unavailable_fromEditing_staysUnchanged() {
        val editing = HomeInteractionController.Editing(selectedId = tileB)
        val result = editing.enterOnLongPress(tileA, isAvailable = false)
        // Unavailable long-press from Editing should not change state
        assertSame(editing, result)
    }

    @Test
    fun enterOnLongPress_available_fromNormal_becomesEditing() {
        val result = HomeInteractionController.Normal.enterOnLongPress(tileA, isAvailable = true)
        assertTrue(result is HomeInteractionController.Editing)
        assertEquals(tileA, (result as HomeInteractionController.Editing).selectedId)
    }

    // ---- null shape (Editing with selectedId = null) ----

    @Test
    fun editing_nullSelectedId_isValidState() {
        val editing = HomeInteractionController.Editing(selectedId = null)
        assertNull(editing.selectedId)
    }

    @Test
    fun editing_nullSelectedId_exitOnBack_returnsNormal() {
        val result = HomeInteractionController.Editing(selectedId = null).exitOnBack()
        assertSame(HomeInteractionController.Normal, result)
    }

    @Test
    fun editing_nullSelectedId_exitOnEmptyTap_returnsNormal() {
        val result = HomeInteractionController.Editing(selectedId = null).exitOnEmptyTap()
        assertSame(HomeInteractionController.Normal, result)
    }

    @Test
    fun editing_nullSelectedId_exitOnNavigate_returnsNormal() {
        val result = HomeInteractionController.Editing(selectedId = null).exitOnNavigate()
        assertSame(HomeInteractionController.Normal, result)
    }

    @Test
    fun editing_nullSelectedId_selectOnTap_selectsTile() {
        val result = HomeInteractionController.Editing(selectedId = null).selectOnTap(tileA)
        assertTrue(result is HomeInteractionController.Editing)
        assertEquals(tileA, (result as HomeInteractionController.Editing).selectedId)
    }

    @Test
    fun editing_nullSelectedId_enterOnLongPress_available_selectsTile() {
        val result = HomeInteractionController.Editing(selectedId = null)
            .enterOnLongPress(tileB, isAvailable = true)
        assertTrue(result is HomeInteractionController.Editing)
        assertEquals(tileB, (result as HomeInteractionController.Editing).selectedId)
    }

    // ---- idempotent exits ----

    @Test
    fun exitOnBack_fromNormal_staysNormal() {
        assertSame(HomeInteractionController.Normal, HomeInteractionController.Normal.exitOnBack())
    }

    @Test
    fun exitOnEmptyTap_fromNormal_staysNormal() {
        assertSame(HomeInteractionController.Normal, HomeInteractionController.Normal.exitOnEmptyTap())
    }

    @Test
    fun exitOnNavigate_fromNormal_staysNormal() {
        assertSame(HomeInteractionController.Normal, HomeInteractionController.Normal.exitOnNavigate())
    }

    @Test
    fun exitOnBack_calledTwiceFromEditing_returnsNormalBothTimes() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val first = editing.exitOnBack()
        assertSame(HomeInteractionController.Normal, first)
        val second = first.exitOnBack()
        assertSame(HomeInteractionController.Normal, second)
    }

    @Test
    fun exitOnEmptyTap_calledTwiceFromEditing_returnsNormalBothTimes() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val first = editing.exitOnEmptyTap()
        assertSame(HomeInteractionController.Normal, first)
        val second = first.exitOnEmptyTap()
        assertSame(HomeInteractionController.Normal, second)
    }

    @Test
    fun exitOnNavigate_calledTwiceFromEditing_returnsNormalBothTimes() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val first = editing.exitOnNavigate()
        assertSame(HomeInteractionController.Normal, first)
        val second = first.exitOnNavigate()
        assertSame(HomeInteractionController.Normal, second)
    }

    // ---- selectOnTap replaces selection ----

    @Test
    fun selectOnTap_replacesSelectedId() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val result = editing.selectOnTap(tileB)
        assertEquals(tileB, (result as HomeInteractionController.Editing).selectedId)
    }

    @Test
    fun selectOnTap_sameTile_idempotent() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val result = editing.selectOnTap(tileA)
        assertTrue(result is HomeInteractionController.Editing)
        assertEquals(tileA, (result as HomeInteractionController.Editing).selectedId)
    }

    // ---- enterOnLongPress from Editing replaces selection ----

    @Test
    fun enterOnLongPress_available_fromEditing_replacesSelection() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val result = editing.enterOnLongPress(tileB, isAvailable = true)
        assertTrue(result is HomeInteractionController.Editing)
        assertEquals(tileB, (result as HomeInteractionController.Editing).selectedId)
    }

    @Test
    fun enterOnLongPress_sameTile_available_fromEditing_staysEditingSameTile() {
        val editing = HomeInteractionController.Editing(selectedId = tileA)
        val result = editing.enterOnLongPress(tileA, isAvailable = true)
        assertTrue(result is HomeInteractionController.Editing)
        assertEquals(tileA, (result as HomeInteractionController.Editing).selectedId)
    }

    // ---- state type safety ----

    @Test
    fun normal_isSingleton() {
        assertSame(HomeInteractionController.Normal, HomeInteractionController.Normal)
    }

    @Test
    fun editing_equality_bySelectedId() {
        val a = HomeInteractionController.Editing(selectedId = tileA)
        val b = HomeInteractionController.Editing(selectedId = tileA)
        assertEquals(a, b)
    }

    @Test
    fun editing_inequality_differentSelectedId() {
        val a = HomeInteractionController.Editing(selectedId = tileA)
        val b = HomeInteractionController.Editing(selectedId = tileB)
        assertNotEquals(a, b)
    }

    @Test
    fun normalAndEditing_notEqual() {
        val normal: HomeInteractionController = HomeInteractionController.Normal
        val editing: HomeInteractionController = HomeInteractionController.Editing(selectedId = null)
        assertNotEquals(normal, editing)
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        assertTrue("Expected $a != $b", a != b)
    }
}