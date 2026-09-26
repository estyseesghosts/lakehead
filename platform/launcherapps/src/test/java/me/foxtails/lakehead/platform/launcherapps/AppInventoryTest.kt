package me.foxtails.lakehead.platform.launcherapps

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInventoryTest {
    private val personal = ProfileId("personal")
    private val work = ProfileId("work")

    @Test
    fun startsEmpty() {
        val inventory = AppInventory()

        assertEquals(emptyList<LauncherAppEntry>(), inventory.apps.value)
        assertEquals(emptyList<LauncherAppEntry>(), inventory.cachedApps())
    }

    @Test
    fun publishedSnapshotIsDefensivelyCopiedAndImmutable() {
        val inventory = AppInventory()
        val callerList = mutableListOf(entry(personal, "z", "Z", "Zed"))
        inventory.replaceAll(callerList)
        val published = inventory.apps.value
        callerList += entry(personal, "a", "A", "Ay")

        assertEquals(listOf(entry(personal, "z", "Z", "Zed")), published)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (published as MutableList<LauncherAppEntry>).add(entry(personal, "b", "B", "Bee"))
        }
        assertEquals(listOf(entry(personal, "z", "Z", "Zed")), inventory.apps.value)
    }

    @Test
    fun exposedFlowCannotBeCastToMutableStateFlowOrPublishExternalValues() {
        val inventory = AppInventory()
        val exposedFlow = inventory.apps

        assertThrows(ClassCastException::class.java) {
            exposedFlow as kotlinx.coroutines.flow.MutableStateFlow<List<LauncherAppEntry>>
        }
        inventory.replaceAll(listOf(entry(personal, "owned.app", "Main", "Owned")))

        assertEquals(listOf(entry(personal, "owned.app", "Main", "Owned")), exposedFlow.value)
    }

    @Test
    fun collectorObservesInitialReplaceAndRemoveSnapshotsWithoutMutatingEarlierValues() = runBlocking {
        val inventory = AppInventory()
        val observed = mutableListOf<List<LauncherAppEntry>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            inventory.apps.take(3).toList(observed)
        }
        val published = entry(personal, "published.app", "Main", "Published")

        yield()
        inventory.replaceAll(listOf(published))
        yield()
        inventory.removeProfile(personal)
        collector.join()

        assertEquals(listOf(emptyList<LauncherAppEntry>(), listOf(published), emptyList()), observed)
        assertEquals(listOf(published), observed[1])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (observed[1] as MutableList<LauncherAppEntry>).clear()
        }
        assertEquals(listOf(published), observed[1])
    }

    @Test
    fun sameComponentInDifferentProfilesRemainsDistinct() {
        val inventory = AppInventory()
        val entryForPersonal = entry(personal, "example.app", "Main", "Example")
        val sameEntryForWork = entry(work, "example.app", "Main", "Example")

        inventory.replaceAll(listOf(entryForPersonal, sameEntryForWork, entryForPersonal))

        assertEquals(listOf(entryForPersonal, sameEntryForWork), inventory.apps.value)
    }

    @Test
    fun duplicateIdentityWithCompetingLabelsCollapsesToOneDeterministicEntry() {
        val inventory = AppInventory()
        val identity = app(personal, "dup.app", "Main")

        inventory.replaceAll(
            listOf(
                LauncherAppEntry(identity, "Zeta"),
                LauncherAppEntry(identity, "Alpha"),
                LauncherAppEntry(identity, "Mid"),
            ),
        )

        assertEquals(listOf(LauncherAppEntry(identity, "Alpha")), inventory.apps.value)
    }

    @Test
    fun deDuplicationDoesNotDependOnInputOrder() {
        val inventory = AppInventory()
        val first = entry(personal, "dup.app", "Main", "Alpha")
        val second = entry(personal, "dup.app", "Main", "Zeta")

        inventory.replaceAll(listOf(first, second))
        val forward = inventory.apps.value
        inventory.replaceAll(listOf(second, first))

        assertEquals(listOf(first), forward)
        assertEquals(forward, inventory.apps.value)
    }

    @Test
    fun publishedEntriesRetainProfileAwareIdentityAndNonBlankLabel() {
        val inventory = AppInventory()
        val identity = app(work, "kept.app", "Main")

        inventory.replaceAll(listOf(LauncherAppEntry(identity, "Kept")))

        val published = inventory.apps.value.single()
        assertEquals(identity, published.identity)
        assertEquals(work, published.identity.profileId)
        assertEquals(ComponentIdentity("kept.app", "Main"), published.identity.component)
        assertTrue("Labels must be non-blank", published.label.isNotBlank())
        assertEquals("Kept", published.label)
    }

    @Test
    fun profileReplacementPreservesOtherProfilesAndReplacesOnlyTarget() {
        val inventory = AppInventory()
        val preserved = entry(work, "kept.app", "Main", "Kept")
        val old = entry(personal, "old.app", "Main", "Old")
        val replacement = entry(personal, "new.app", "Main", "New")
        inventory.replaceAll(listOf(old, preserved))

        inventory.replaceProfile(personal, listOf(replacement))

        // Label-first sort: "Kept" < "New"
        assertEquals(listOf(preserved, replacement), inventory.apps.value)
    }

    @Test
    fun profileReplacementCollapsesCompetingLabelsForTheSameIdentity() {
        val inventory = AppInventory()
        val stale = entry(personal, "same.app", "Main", "Zeta")
        val retained = entry(work, "other.app", "Main", "Other")
        inventory.replaceAll(listOf(stale, retained))

        inventory.replaceProfile(personal, listOf(entry(personal, "same.app", "Main", "Alpha")))

        assertEquals(
            listOf(entry(personal, "same.app", "Main", "Alpha"), retained),
            inventory.apps.value,
        )
    }

    @Test
    fun removalDeletesExactlyTheRequestedProfile() {
        val inventory = AppInventory()
        val retained = entry(work, "same.app", "Main", "Same")
        inventory.replaceAll(listOf(entry(personal, "same.app", "Main", "Same"), retained))

        inventory.removeProfile(personal)

        assertEquals(listOf(retained), inventory.apps.value)
    }

    @Test
    fun snapshotsAreDeterministicallyOrderedByLabelThenPackageClassAndProfile() {
        val inventory = AppInventory()
        val expected = listOf(
            entry(personal, "a.package", "A", "Alpha"),
            entry(work, "z.package", "A", "Alpha"),
            entry(personal, "a.package", "Z", "Beta"),
            entry(personal, "z.package", "A", "Gamma"),
        )

        inventory.replaceAll(expected.reversed())

        assertEquals(expected, inventory.apps.value)
    }

    @Test
    fun entriesAreSortedByLabelCaseInsensitive() {
        val inventory = AppInventory()
        val zebra = entry(personal, "z.app", "Main", "Zebra")
        val apple = entry(personal, "a.app", "Main", "Apple")
        val mango = entry(personal, "m.app", "Main", "Mango")

        inventory.replaceAll(listOf(zebra, apple, mango))

        assertEquals(listOf(apple, mango, zebra), inventory.apps.value)
    }

    @Test
    fun labelSortIsCaseInsensitive() {
        val inventory = AppInventory()
        val upper = entry(personal, "a.app", "Main", "BANANA")
        val lower = entry(personal, "b.app", "Main", "apple")
        val mixed = entry(personal, "c.app", "Main", "Cherry")

        inventory.replaceAll(listOf(upper, lower, mixed))

        assertEquals(listOf(lower, upper, mixed), inventory.apps.value)
    }

    private fun app(profileId: ProfileId, packageName: String, className: String) =
        AppIdentity(profileId, ComponentIdentity(packageName, className))

    private fun entry(profileId: ProfileId, packageName: String, className: String, label: String) =
        LauncherAppEntry(app(profileId, packageName, className), label)
}
