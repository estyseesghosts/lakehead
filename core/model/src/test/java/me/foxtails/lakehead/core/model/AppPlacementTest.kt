package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPlacementTest {
    @Test
    fun identifiersRequireNonblankValuesAndKeepTheirOwnEqualityDomains() {
        assertInvalid { HomeId("") }
        assertInvalid { HomeId("  \t") }
        assertInvalid { ItemId("") }
        assertInvalid { ItemId(" \n") }

        assertEquals(HomeId("home-a"), HomeId("home-a"))
        assertNotEquals(HomeId("home-a"), HomeId("home-b"))
        assertEquals(ItemId("item-a"), ItemId("item-a"))
        assertNotEquals(ItemId("item-a"), ItemId("item-b"))
        assertNotEquals(HomeId("same"), ItemId("same"))
    }

    @Test
    fun placementIdentityIncludesTheFullProfileAwareAppIdentity() {
        val personal = placement(identity(profile = "personal"))
        val work = placement(identity(profile = "work"))

        assertNotEquals(personal.appIdentity, work.appIdentity)
        assertNotEquals(personal, work)
        assertEquals("example", work.appIdentity.component.packageName)
        assertEquals("example.MainActivity", work.appIdentity.component.className)
        assertEquals(ProfileId("work"), work.appIdentity.profileId)
    }

    @Test
    fun unavailablePlacementRetainsItemAndAppIdentity() {
        val available = placement(identity(profile = "work"), ItemAvailability.AVAILABLE)
        val unavailable = available.copy(availability = ItemAvailability.UNAVAILABLE)
        val restored = unavailable.copy(availability = ItemAvailability.AVAILABLE)

        assertEquals(HomeItemType.APP, unavailable.type)
        assertEquals(available.itemId, unavailable.itemId)
        assertEquals(available.appIdentity, unavailable.appIdentity)
        assertEquals(available.position, unavailable.position)
        assertEquals(available.span, unavailable.span)
        assertEquals(available, restored)
        assertEquals(setOf(ItemAvailability.AVAILABLE, ItemAvailability.UNAVAILABLE),
            ItemAvailability.entries.toSet())
    }

    @Test
    fun logicalPositionAndSpanValidatePrimitiveCoordinatesAndDimensions() {
        assertInvalid { LogicalPosition(-1, 0) }
        assertInvalid { LogicalPosition(0, -1) }
        assertInvalid { LogicalSpan(0, 1) }
        assertInvalid { LogicalSpan(1, 0) }

        assertEquals(LogicalPosition(2, 7), LogicalPosition(2, 7))
        assertEquals(LogicalSpan(4, 2), LogicalSpan(4, 2))
    }

    @Test
    fun placementContractHasNoPlatformOrPresentationTypes() {
        val domainTypes = listOf(
            AppPlacement::class.java,
            HomeId::class.java,
            ItemId::class.java,
            LogicalPosition::class.java,
            LogicalSpan::class.java,
            AppIdentity::class.java,
            ProfileId::class.java,
            ComponentIdentity::class.java,
        )

        domainTypes.forEach { type ->
            assertTrue("Unexpected framework type ${type.name}",
                !type.name.startsWith("android.") && !type.name.startsWith("androidx.compose."))
            type.declaredFields
                .filterNot { it.isSynthetic }
                .forEach { field ->
                    val fieldType = field.type
                    assertTrue("${type.name}.${field.name} uses ${fieldType.name}",
                        fieldType.isPrimitive || fieldType.isEnum ||
                            fieldType.name.startsWith("java.lang.") ||
                            fieldType.name.startsWith("me.foxtails.lakehead.core.model."))
                }
        }
    }

    private fun placement(
        appIdentity: AppIdentity,
        availability: ItemAvailability = ItemAvailability.AVAILABLE,
    ) = AppPlacement(
        homeId = HomeId("home-main"),
        itemId = ItemId("item-one"),
        appIdentity = appIdentity,
        position = LogicalPosition(column = 2, row = 3),
        span = LogicalSpan(columns = 2, rows = 1),
        availability = availability,
    )

    private fun identity(profile: String) = AppIdentity(
        profileId = ProfileId(profile),
        component = ComponentIdentity("example", "example.MainActivity"),
    )

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
