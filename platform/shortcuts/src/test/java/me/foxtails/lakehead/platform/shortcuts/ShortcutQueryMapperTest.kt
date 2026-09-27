package me.foxtails.lakehead.platform.shortcuts

import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutQueryMapperTest {
    private val profile = ProfileId("10")
    private val packageName = "com.example"

    @Test
    fun rankOrdersLowerFirstWithIdTieBreak() {
        val rows = listOf(
            ShortcutQueryRow("c", "C", null, 5),
            ShortcutQueryRow("a", "A", null, 1),
            ShortcutQueryRow("b", "B", null, 1),
        )
        val mapped = mapShortcutRows(packageName, profile, rows)
        assertEquals(listOf("a", "b", "c"), mapped.map { it.ref.shortcutId })
        assertEquals(listOf(1, 1, 5), mapped.map { it.rank })
        assertEquals(listOf("A", "B", "C"), mapped.map { it.label })
    }

    @Test
    fun labelPrefersShortThenLongThenId() {
        val rows = listOf(
            ShortcutQueryRow("with-short", "Short", "Long", 0),
            ShortcutQueryRow("blank-short", "   ", "Long", 1),
            ShortcutQueryRow("no-labels", null, null, 2),
        )
        val mapped = mapShortcutRows(packageName, profile, rows)
        assertEquals(listOf("Short", "Long", "no-labels"), mapped.map { it.label })
    }

    @Test
    fun blankIdsAreDroppedAndDuplicateIdsKeepLowestRank() {
        val rows = listOf(
            ShortcutQueryRow("", "Blank", null, 0),
            ShortcutQueryRow("dup", "Second", null, 4),
            ShortcutQueryRow("dup", "First", null, 0),
        )
        val mapped = mapShortcutRows(packageName, profile, rows)
        assertEquals(listOf("dup"), mapped.map { it.ref.shortcutId })
        assertEquals("First", mapped.single().label)
        assertEquals(0, mapped.single().rank)
    }

    @Test
    fun refsCarryPackageAndProfile() {
        val mapped = mapShortcutRows(
            packageName,
            profile,
            listOf(ShortcutQueryRow("compose", "Compose", null, 2)),
        )
        val ref = mapped.single().ref
        assertEquals(packageName, ref.packageName)
        assertEquals(profile, ref.profileId)
        assertEquals("compose", ref.shortcutId)
    }

    @Test
    fun negativeRanksClampToZero() {
        val mapped = mapShortcutRows(
            packageName,
            profile,
            listOf(ShortcutQueryRow("odd", "Odd", null, -3)),
        )
        assertEquals(0, mapped.single().rank)
    }

    @Test
    fun emptyRowsMapToEmptyList() {
        assertTrue(mapShortcutRows(packageName, profile, emptyList()).isEmpty())
    }
}
