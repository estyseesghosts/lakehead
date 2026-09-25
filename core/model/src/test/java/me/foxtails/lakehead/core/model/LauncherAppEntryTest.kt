package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LauncherAppEntryTest {
    @Test
    fun entriesWithSameIdentityAndLabelAreValueEqual() {
        val identity = appIdentity(profile = "profile-a")

        assertEquals(
            LauncherAppEntry(identity, "Example app"),
            LauncherAppEntry(appIdentity(profile = "profile-a"), "Example app"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankLabelIsRejected() {
        LauncherAppEntry(appIdentity(profile = "profile-a"), " \t\n")
    }

    @Test
    fun entryPreservesTheProfileAwareIdentityInstance() {
        val identity = appIdentity(profile = "work-profile")

        val entry = LauncherAppEntry(identity, "Example app")

        assertSame(identity, entry.identity)
        assertEquals(ProfileId("work-profile"), entry.identity.profileId)
        assertEquals(ComponentIdentity("example", "example.MainActivity"), entry.identity.component)
    }

    private fun appIdentity(profile: String) = AppIdentity(
        profileId = ProfileId(profile),
        component = ComponentIdentity("example", "example.MainActivity"),
    )
}
