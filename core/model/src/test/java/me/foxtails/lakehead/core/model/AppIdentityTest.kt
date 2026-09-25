package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentityTest {
    @Test
    fun sameComponentAndProfileAreEqualAndSetStable() {
        val first = identity(profile = "profile-a")
        val equivalent = identity(profile = "profile-a")

        assertEquals(first, equivalent)
        assertEquals(1, setOf(first, equivalent).size)
        assertTrue(setOf(first).contains(equivalent))
    }

    @Test
    fun sameComponentInDifferentProfilesRemainsDistinct() {
        val personal = identity(profile = "profile-a")
        val work = identity(profile = "profile-b")

        assertNotEquals(personal, work)
        assertEquals(2, setOf(personal, work).size)
    }

    @Test
    fun differentComponentsInOneProfileAreDistinct() {
        val first = identity(profile = "profile-a", className = "example.MainActivity")
        val second = identity(profile = "profile-a", className = "example.OtherActivity")

        assertNotEquals(first, second)
        assertEquals(2, setOf(first, second).size)
    }

    private fun identity(
        profile: String,
        className: String = "example.MainActivity",
    ) = AppIdentity(
        profileId = ProfileId(profile),
        component = ComponentIdentity("example", className),
    )
}
