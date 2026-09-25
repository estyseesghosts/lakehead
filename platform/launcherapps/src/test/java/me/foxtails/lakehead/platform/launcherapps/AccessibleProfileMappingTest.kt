package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccessibleProfileMappingTest {
    @Test
    fun mapsEveryAccessibleProfile() {
        val result = mapAccessibleProfiles(listOf("personal", "work")) { ProfileId(it) }

        assertEquals(listOf(ProfileId("personal"), ProfileId("work")), result)
    }

    @Test
    fun nullMappingFailsInsteadOfReturningPartialProfiles() {
        assertThrows(IllegalArgumentException::class.java) {
            mapAccessibleProfiles(listOf("personal", "work")) {
                if (it == "personal") ProfileId(it) else null
            }
        }
    }

    @Test
    fun emptyAccessibleSetMapsToEmptyList() {
        assertEquals(emptyList<ProfileId>(), mapAccessibleProfiles(emptyList<String>()) { null })
    }
}
