package me.foxtails.lakehead.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.data.database.HomeEntity
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.database.toEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository-level tests for album binding operations: load, save, delete,
 * absent binding, upsert, and removal with the tile.
 */
@RunWith(RobolectricTestRunner::class)
class AlbumBindingRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), PlacementDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao get() = database.placementDao()
    private val repository: HomePlacementRepository = RoomHomePlacementRepository(database)

    @After fun closeDatabase() = database.close()

    @Test
    fun loadAlbumBindingReturnsNullWhenNoneExists() {
        seedPlacement("tile-none")

        assertNull(repository.loadAlbumBinding(ItemId("tile-none")))
    }

    @Test
    fun saveThenLoadRoundTripsThroughRepository() {
        seedPlacement("tile-rt")
        val binding = TileAlbumBinding(ItemId("tile-rt"), AlbumRef("bucket-9", "Camera"))

        repository.saveAlbumBinding(binding)

        assertEquals(binding, repository.loadAlbumBinding(ItemId("tile-rt")))
    }

    @Test
    fun saveUpsertsReplacingPreviousAlbum() {
        seedPlacement("tile-upsert")
        repository.saveAlbumBinding(TileAlbumBinding(ItemId("tile-upsert"), AlbumRef("b1", "Camera")))

        val updated = TileAlbumBinding(ItemId("tile-upsert"), AlbumRef("b2", "Downloads"))
        repository.saveAlbumBinding(updated)

        assertEquals(updated, repository.loadAlbumBinding(ItemId("tile-upsert")))
        assertEquals(1, dao.albumBindingCount())
    }

    @Test
    fun deleteAlbumBindingRemovesExistingBinding() {
        seedPlacement("tile-del")
        repository.saveAlbumBinding(TileAlbumBinding(ItemId("tile-del"), AlbumRef("b1", "Camera")))
        assertEquals(1, dao.albumBindingCount())

        repository.deleteAlbumBinding(ItemId("tile-del"))

        assertEquals(0, dao.albumBindingCount())
        assertNull(repository.loadAlbumBinding(ItemId("tile-del")))
    }

    @Test
    fun deleteAlbumBindingIsNoOpWhenNoneExists() {
        // Should not throw
        repository.deleteAlbumBinding(ItemId("nonexistent"))
    }

    @Test
    fun removeDeletesAlbumBindingWithItsTile() {
        seedPlacement("tile-cascade")
        repository.saveAlbumBinding(TileAlbumBinding(ItemId("tile-cascade"), AlbumRef("b1", "Camera")))
        assertEquals(1, dao.albumBindingCount())

        repository.remove(HomeId("home-main"), ItemId("tile-cascade"))

        assertEquals(0, dao.albumBindingCount())
    }

    @Test
    fun albumBindingsForDifferentTilesAreIndependent() {
        seedPlacement("tile-a")
        seedPlacement("tile-b")
        val bindingA = TileAlbumBinding(ItemId("tile-a"), AlbumRef("b1", "Camera"))
        val bindingB = TileAlbumBinding(ItemId("tile-b"), AlbumRef("b2", "Downloads"))
        repository.saveAlbumBinding(bindingA)
        repository.saveAlbumBinding(bindingB)

        repository.deleteAlbumBinding(ItemId("tile-a"))

        assertNull(repository.loadAlbumBinding(ItemId("tile-a")))
        assertEquals(bindingB, repository.loadAlbumBinding(ItemId("tile-b")))
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
                LogicalPosition(0, 0), LogicalSpan(2, 2), ItemAvailability.AVAILABLE,
            ).toEntity(),
        )
    }
}
