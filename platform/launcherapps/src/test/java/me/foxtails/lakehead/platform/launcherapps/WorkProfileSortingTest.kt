package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileDisplay
import me.foxtails.lakehead.core.model.ProfileDisplayKind
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorkProfileSortingTest {

    @Test
    fun workPrefixIsDisplayOnly_andPersonalAndWorkFirefoxStayUnderF() {
        val personalFirefox = display(
            profile = "personal",
            packageName = "com.example.firefox",
            className = "Firefox",
            sortKey = "Firefox",
            displayLabel = "Firefox",
            kind = ProfileDisplayKind.PERSONAL,
        )
        val workFirefox = display(
            profile = "work",
            packageName = "com.example.firefox",
            className = "Firefox",
            sortKey = "Firefox",
            displayLabel = "Work Firefox",
            kind = ProfileDisplayKind.WORK,
        )

        val inventory = AppInventory()
        inventory.replaceAll(listOf(toEntry(workFirefox), toEntry(personalFirefox)))

        assertEquals(listOf("Firefox", "Firefox"), inventory.apps.value.map { it.sortKey })
        assertEquals(listOf("Firefox", "Work Firefox"), inventory.apps.value.map { it.displayLabel })
        assertEquals("F", inventory.apps.value.first().sortKey.first().uppercase())
        assertNotEquals(personalFirefox.identity, workFirefox.identity)
        assertEquals(
            listOf(personalFirefox.identity, workFirefox.identity),
            inventory.apps.value.map { it.identity },
        )
    }

    @Test
    fun equalSortKeysUsePackageClassThenProfileAsStableTieBreakers() {
        val entries = listOf(
            toEntry(display("work", "com.zed", "Z", "Firefox", "Work Firefox", ProfileDisplayKind.WORK)),
            toEntry(display("personal", "com.alpha", "Z", "Firefox", "Firefox", ProfileDisplayKind.PERSONAL)),
            toEntry(display("personal", "com.alpha", "A", "Firefox", "Firefox", ProfileDisplayKind.PERSONAL)),
            toEntry(display("work", "com.alpha", "A", "Firefox", "Work Firefox", ProfileDisplayKind.WORK)),
        )

        val inventory = AppInventory()
        inventory.replaceAll(entries.reversed())

        assertEquals(
            listOf(
                "personal:com.alpha/A",
                "work:com.alpha/A",
                "personal:com.alpha/Z",
                "work:com.zed/Z",
            ),
            inventory.apps.value.map {
                "${it.identity.profileId.value}:${it.identity.component.packageName}/${it.identity.component.className}"
            },
        )
    }

    private fun display(
        profile: String,
        packageName: String,
        className: String,
        sortKey: String,
        displayLabel: String,
        kind: ProfileDisplayKind,
    ) = ProfileDisplay(
        identity = AppIdentity(ProfileId(profile), ComponentIdentity(packageName, className)),
        sortKey = sortKey,
        displayLabel = displayLabel,
        profileDisplay = kind,
    )

    private fun toEntry(display: ProfileDisplay) = LauncherAppEntry(
        identity = display.identity,
        label = display.displayLabel,
        sortKey = display.sortKey,
        displayLabel = display.displayLabel,
        profileDisplay = display.profileDisplay,
    )
}
