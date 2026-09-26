package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutRefTest {

    @Test
    fun equalRefsAreStableAcrossInstances() {
        val first = ref()
        val equivalent = ref()

        assertEquals(first, equivalent)
        assertEquals(first.hashCode(), equivalent.hashCode())
        assertEquals(1, setOf(first, equivalent).size)
    }

    @Test
    fun differentProfilesProduceDistinctRefs() {
        val personal = ref(profile = "profile-a")
        val work = ref(profile = "profile-b")

        assertNotEquals(personal, work)
        assertEquals(2, setOf(personal, work).size)
    }

    @Test
    fun differentShortcutIdsAreDistinct() {
        val first = ref(shortcutId = "shortcut-1")
        val second = ref(shortcutId = "shortcut-2")

        assertNotEquals(first, second)
        assertEquals(2, setOf(first, second).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankPackageNameIsRejected() {
        ref(packageName = "  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankShortcutIdIsRejected() {
        ref(shortcutId = "  ")
    }

    @Test
    fun fieldsContainNoFrameworkOrPixelTypes() {
        val ref = ref()
        val allowedPrefixes = listOf(
            "java.lang.",
            "me.foxtails.lakehead.core.model.",
        )

        ShortcutRef::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .forEach { field ->
                val name = field.type.name
                assertTrue(
                    "ShortcutRef.${field.name} uses ${field.type.name}",
                    field.type.isPrimitive || allowedPrefixes.any { name.startsWith(it) }
                )
            }
    }

    private fun ref(
        packageName: String = "com.example",
        profile: String = "profile-a",
        shortcutId: String = "shortcut-1",
    ) = ShortcutRef(
        packageName = packageName,
        profileId = ProfileId(profile),
        shortcutId = shortcutId,
    )
}