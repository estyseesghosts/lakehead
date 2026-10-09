package me.foxtails.lakehead.platform.widgets

import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProviderMappingTest {
    private val personal = ProfileId("0")
    private val work = ProfileId("10")

    private fun raw(
        pkg: String = "com.example",
        cls: String = "com.example.ClockWidget",
        label: String? = "Clock",
        appLabel: String? = "Example",
        configure: Boolean = false,
    ) = RawWidgetProvider(pkg, cls, label, appLabel, 250, 110, 40, 40, 4, 2, configure)

    @Test
    fun toEntry_carriesProviderProfileSizesAndConfigureFlag() {
        val entry = raw(configure = true).toEntry(work)
        assertEquals(work, entry.provider.profileId)
        assertEquals("com.example", entry.provider.component.packageName)
        assertEquals("com.example.ClockWidget", entry.provider.component.className)
        assertEquals(250, entry.minWidthDp)
        assertEquals(4, entry.targetCellWidth)
        assertEquals(2, entry.targetCellHeight)
        assertTrue(entry.hasConfigureActivity)
    }

    @Test
    fun toEntry_blankLabelsFallBackToClassAndPackage() {
        val entry = raw(label = " ", appLabel = null).toEntry(personal)
        assertEquals("ClockWidget", entry.label)
        assertEquals("com.example", entry.appLabel)
    }

    @Test
    fun toEntry_negativeSizesFloorAtZero() {
        val entry = raw().copy(minWidthDp = -1, targetCellWidth = -3).toEntry(personal)
        assertEquals(0, entry.minWidthDp)
        assertEquals(0, entry.targetCellWidth)
    }

    @Test
    fun order_groupsByAppThenLabelIgnoringCase_andKeepsProfilesDistinct() {
        val entries = listOf(
            raw(pkg = "b", cls = "b.W", label = "Zeta", appLabel = "banana").toEntry(personal),
            raw(pkg = "a", cls = "a.W2", label = "beta", appLabel = "Apple").toEntry(personal),
            raw(pkg = "a", cls = "a.W1", label = "Alpha", appLabel = "Apple").toEntry(work),
            raw(pkg = "a", cls = "a.W1", label = "Alpha", appLabel = "Apple").toEntry(personal),
        )
        val sorted = entries.sortedWith(widgetProviderOrder)
        assertEquals(
            listOf("Alpha" to personal, "Alpha" to work, "beta" to personal, "Zeta" to personal),
            sorted.map { it.label to it.provider.profileId },
        )
    }
}
