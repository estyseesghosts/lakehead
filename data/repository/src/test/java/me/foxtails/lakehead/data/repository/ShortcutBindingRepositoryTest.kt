package me.foxtails.lakehead.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.database.HomeEntity
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.database.toEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository-level tests for shortcut binding operations: load, save, delete,
 * default empty binding, upsert, and persistence through the repo boundary.
 */
@RunWith(RobolectricTestRunner::class)
class ShortcutBindingRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), PlacementDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao get() = database.placementDao()
    private val repository: HomePlacementRepository = RoomHomePlacementRepository(database)

    @After fun closeDatabase() = database.close()

    @Test
    fun loadBindingReturnsEmptyBindingWhenNoneExists() {
        seedPlacement("tile-none")
        val loaded = repository.loadBinding(ItemId("tile-none"))
        assertEquals(ItemId("tile-none"), loaded.tileId)
        assertTrue(loaded.pinnedShortcuts.isEmpty())
    }

    @Test
    fun saveThenLoadRoundTripsThroughRepository() {
        seedPlacement("tile-rt")
        val ref1 = ShortcutRef("com.a", ProfileId("p1"), "s1")
        val ref2 = ShortcutRef("com.b", ProfileId("p2"), "s2")
        val binding = TileBinding(ItemId("tile-rt"), listOf(ref1, ref2))

        repository.saveBinding(binding)

        val loaded = repository.loadBinding(ItemId("tile-rt"))
        assertEquals(binding, loaded)
    }

    @Test
    fun saveUpsertsReplacingPreviousBinding() {
        seedPlacement("tile-upsert")
        val original = TileBinding(
            ItemId("tile-upsert"),
            listOf(ShortcutRef("com.a", ProfileId("p"), "s1")),
        )
        repository.saveBinding(original)

        val updated = TileBinding(
            ItemId("tile-upsert"),
            listOf(
                ShortcutRef("com.b", ProfileId("p"), "s2"),
                ShortcutRef("com.c", ProfileId("p"), "s3"),
            ),
        )
        repository.saveBinding(updated)

        assertEquals(updated, repository.loadBinding(ItemId("tile-upsert")))
        assertEquals(1, dao.shortcutBindingCount())
    }

    @Test
    fun deleteBindingRemovesExistingBinding() {
        seedPlacement("tile-del")
        repository.saveBinding(
            TileBinding(
                ItemId("tile-del"),
                listOf(ShortcutRef("com.a", ProfileId("p"), "s")),
            ),
        )
        assertEquals(1, dao.shortcutBindingCount())

        repository.deleteBinding(ItemId("tile-del"))

        assertEquals(0, dao.shortcutBindingCount())
        assertTrue(repository.loadBinding(ItemId("tile-del")).pinnedShortcuts.isEmpty())
    }

    @Test
    fun deleteBindingIsNoOpWhenNoneExists() {
        // Should not throw
        repository.deleteBinding(ItemId("nonexistent"))
    }

    @Test
    fun saveEmptyBindingAndLoadReturnsEmptyList() {
        seedPlacement("tile-empty")
        val binding = TileBinding(ItemId("tile-empty"), emptyList())
        repository.saveBinding(binding)

        val loaded = repository.loadBinding(ItemId("tile-empty"))
        assertTrue(loaded.pinnedShortcuts.isEmpty())
    }

    @Test
    fun cascadeDeletesBindingWhenPlacementIsRemoved() {
        seedPlacement("tile-cascade")
        repository.saveBinding(
            TileBinding(
                ItemId("tile-cascade"),
                listOf(ShortcutRef("com.a", ProfileId("p"), "s")),
            ),
        )
        assertEquals(1, dao.shortcutBindingCount())

        repository.remove(HomeId("home-main"), ItemId("tile-cascade"))

        assertEquals(0, dao.shortcutBindingCount())
    }

    @Test
    fun multipleBindingsForDifferentTilesAreIndependent() {
        seedPlacement("tile-a")
        seedPlacement("tile-b")
        val bindingA = TileBinding(ItemId("tile-a"), listOf(ShortcutRef("com.a", ProfileId("p"), "s1")))
        val bindingB = TileBinding(ItemId("tile-b"), listOf(ShortcutRef("com.b", ProfileId("p"), "s2")))
        repository.saveBinding(bindingA)
        repository.saveBinding(bindingB)

        repository.deleteBinding(ItemId("tile-a"))

        assertTrue(repository.loadBinding(ItemId("tile-a")).pinnedShortcuts.isEmpty())
        assertEquals(bindingB, repository.loadBinding(ItemId("tile-b")))
    }

    // --- helpers ---

    private fun seedPlacement(itemId: String) {
        val identity = AppIdentity(
            ProfileId("work"),
            ComponentIdentity("org.example", "org.example.Home"),
        )
        if (dao.home("home-main") == null) dao.insertHome(HomeEntity("home-main"))
        if (dao.appReference("work", "org.example", "org.example.Home") == null) {
            dao.insertAppReference(identity.toEntity())
        }
        dao.insertPlacement(
            AppPlacement(
                HomeId("home-main"), ItemId(itemId), identity,
                LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
            ).toEntity(),
        )
    }
}