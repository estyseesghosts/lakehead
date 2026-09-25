package me.foxtails.lakehead.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TileHitTargetTest {

    // -- Singleton identity ---------------------------------------------------

    @Test
    fun bodyIsSingleton() {
        assertSame(TileHitTarget.Body, TileHitTarget.Body)
    }

    @Test
    fun appSymbolIsSingleton() {
        assertSame(TileHitTarget.AppSymbol, TileHitTarget.AppSymbol)
    }

    // -- Equality -------------------------------------------------------------

    @Test
    fun shortcutWithSameIndexAreEqual() {
        assertEquals(TileHitTarget.Shortcut(0), TileHitTarget.Shortcut(0))
        assertEquals(TileHitTarget.Shortcut(3), TileHitTarget.Shortcut(3))
    }

    @Test
    fun shortcutWithDifferentIndexAreNotEqual() {
        assertNotEquals(TileHitTarget.Shortcut(0), TileHitTarget.Shortcut(1))
        assertNotEquals(TileHitTarget.Shortcut(2), TileHitTarget.Shortcut(9))
    }

    @Test
    fun differentTypesAreNotEqual() {
        assertNotEquals(TileHitTarget.Body as Any, TileHitTarget.AppSymbol as Any)
        assertNotEquals(TileHitTarget.Body as Any, TileHitTarget.Shortcut(0) as Any)
        assertNotEquals(TileHitTarget.AppSymbol as Any, TileHitTarget.Shortcut(0) as Any)
    }

    // -- Exhaustive when (compiler verifies all branches) ---------------------

    @Test
    fun whenExhaustiveOnAllVariants() {
        val targets: List<TileHitTarget> = listOf(
            TileHitTarget.Body,
            TileHitTarget.AppSymbol,
            TileHitTarget.Shortcut(0),
            TileHitTarget.Shortcut(42),
        )

        val labels = targets.map { target ->
            when (target) {
                TileHitTarget.Body -> "body"
                TileHitTarget.AppSymbol -> "appSymbol"
                is TileHitTarget.Shortcut -> "shortcut(${target.index})"
            }
        }

        assertEquals(
            listOf("body", "appSymbol", "shortcut(0)", "shortcut(42)"),
            labels,
        )
    }
}