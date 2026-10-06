package me.foxtails.lakehead.feature.allapps

import me.foxtails.lakehead.core.designsystem.alphabetPickerCells
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the P4 [AlphabetPickerState] slice.
 *
 * Covers the fixed 28-slot cell model (via the shared designsystem cells) and the
 * pure String-key enablement derivation from P3 [AppSection]s. No Compose, Android,
 * persistence, or platform access.
 */
class AlphabetPickerStateTest {

    private fun entry(label: String, pkg: String) = AllAppsEntry(
        identity = AppIdentity(ProfileId("0"), ComponentIdentity(pkg, "$pkg.Main")),
        label = label,
        alreadyOnHome = false,
    )

    @Test
    fun cells_are28Slots_AtoZPlusTwoSpares() {
        val cells = alphabetPickerCells()
        assertEquals(28, cells.size)
        assertEquals(('A'..'Z').toList(), cells.take(26))
        assertEquals(listOf(null, null), cells.takeLast(2))
    }

    @Test
    fun toAlphabetPickerState_derivesStringKeys_fromSections() {
        val entries = listOf(
            entry("Apple", "com.example.apple"),
            entry("Avocado", "com.example.avocado"),
            entry("Banana", "com.example.banana"),
            entry("1Password", "com.example.onepassword"),
        )
        val sections = entries.toAppSections(sectionCollator())
        val keys = sections.map { it.key }.toSet()

        val open = sections.toAlphabetPickerState(isOpen = true)
        assertTrue(open.isOpen)
        assertEquals(keys, open.enabledLetters)
        assertTrue(open.enabledLetters.contains("A"))
        assertTrue(open.enabledLetters.contains("B"))
        assertTrue(open.enabledLetters.contains("#"))

        val closed = sections.toAlphabetPickerState(isOpen = false)
        assertFalse(closed.isOpen)
        assertEquals(keys, closed.enabledLetters)
    }

    @Test
    fun toAlphabetPickerState_emptySections_yieldsEmptyEnablement() {
        val state = emptyList<AppSection>().toAlphabetPickerState(isOpen = true)
        assertTrue(state.isOpen)
        assertTrue(state.enabledLetters.isEmpty())
    }

    @Test
    fun enabledChars_adaptsStringKeys_toGridChars() {
        val state = AlphabetPickerState(isOpen = true, enabledLetters = setOf("A", "Q", "#"))
        assertEquals(setOf('A', 'Q', '#'), state.enabledChars())
    }

    @Test
    fun blocksPager_reflectsIsOpen_only() {
        assertTrue(AlphabetPickerState(isOpen = true).blocksPager())
        assertFalse(AlphabetPickerState(isOpen = false).blocksPager())
        assertFalse((null as AlphabetPickerState?).blocksPager())
    }
}
