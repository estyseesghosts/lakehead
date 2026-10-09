package me.foxtails.lakehead

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import me.foxtails.lakehead.core.model.AlbumPhoto
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.MediaAlbum
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.RoomHomePlacementRepository
import me.foxtails.lakehead.feature.home.HomeRenderTile
import me.foxtails.lakehead.platform.media.MediaAccess
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Slice 7 device proof: the edit-menu album rows end to end against a real
 * Room database on the device.
 *
 * A gallery tile with zero platform shortcuts and a persisted album binding
 * shows exactly the Change/Remove action rows through the real
 * [ShortcutSurfaceLoader] + [ShortcutMenuCoordinator]; Change reopens the
 * picker through the real [AlbumTileCoordinator] pending-tile flow and a
 * pick rebinds the real row; Remove deletes the real row, clears the
 * projected photo, and the refreshed menu drops its rows. The media library
 * and shortcut gateway are in-memory fakes (no photo content on device is
 * read); everything persisted goes through Room.
 */
@RunWith(AndroidJUnit4::class)
class AlbumMenuDeviceTest {

    private lateinit var database: PlacementDatabase
    private lateinit var repository: HomePlacementRepository
    private lateinit var scope: CoroutineScope

    private val homeId = HomeId("home-album-menu")
    private val columns = 5
    private val tileId = ItemId("gallery-tile")
    private val galleryIdentity = AppIdentity(
        ProfileId("0"),
        ComponentIdentity("com.sec.android.gallery3d", "com.sec.android.gallery3d.Main"),
    )
    private val cameraAlbum = AlbumRef("b1", "Camera")
    private val otherAlbum = AlbumRef("b2", "Screenshots")

    private val library = FakeLibrary()
    private var access = MediaAccess.FULL
    private var permissionRequests = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PlacementDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomHomePlacementRepository(database)
        repository.ensureHome(homeId)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        library.albums = listOf(MediaAlbum(cameraAlbum, 1, "content://media/cover"))
        library.photosByBucket[cameraAlbum.bucketId] =
            mutableListOf(AlbumPhoto("content://media/p1", 1L))
        library.photosByBucket[otherAlbum.bucketId] =
            mutableListOf(AlbumPhoto("content://media/p2", 2L))
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun renderTile() = HomeRenderTile(
        itemId = tileId,
        appIdentity = galleryIdentity,
        position = LogicalPosition(0, 0),
        span = LogicalSpan(2, 2),
        availability = ItemAvailability.AVAILABLE,
        label = "Gallery",
        icon = null,
        isLaunchable = true,
    )

    private fun placeTile() {
        repository.place(homeId, tileId, galleryIdentity, LogicalPosition(0, 0), LogicalSpan(2, 2), columns)
    }

    private fun albumCoordinator(): AlbumTileCoordinator {
        val coordinator = AlbumTileCoordinator(
            repository = repository,
            library = library,
            accessNow = { access },
            onRequestPermission = { permissionRequests++ },
            ioDispatcher = Dispatchers.IO,
        )
        coordinator.start(scope)
        return coordinator
    }

    private fun menuCoordinator(): ShortcutMenuCoordinator {
        val gateway = FakeGateway()
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, Dispatchers.IO),
            Dispatchers.IO,
        )
        coordinator.start(scope)
        return coordinator
    }

    private fun awaitEntries(
        coordinator: ShortcutMenuCoordinator,
        expected: List<String>,
        timeoutMs: Long = 5_000L,
    ): List<String> {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var last = coordinator.menu.value?.entries?.map { it.id }.orEmpty()
        while (SystemClock.uptimeMillis() < end && last != expected) {
            SystemClock.sleep(50)
            last = coordinator.menu.value?.entries?.map { it.id }.orEmpty()
        }
        return last
    }

    private fun awaitBinding(expected: TileAlbumBinding?, timeoutMs: Long = 5_000L): TileAlbumBinding? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var last = repository.loadAlbumBinding(tileId)
        while (SystemClock.uptimeMillis() < end && last != expected) {
            SystemClock.sleep(50)
            last = repository.loadAlbumBinding(tileId)
        }
        return last
    }

    @Test
    fun boundTileWithZeroShortcuts_menuShowsChangeAndRemove() {
        placeTile()
        runBlocking { repository.saveAlbumBinding(TileAlbumBinding(tileId, cameraAlbum)) }
        val shortcutMenu = menuCoordinator()
        val albums = albumCoordinator()
        try {
            albums.onHomeTilesChanged(listOf(renderTile()))
            shortcutMenu.onHomeTilesChanged(listOf(renderTile()))
            shortcutMenu.onSelectionChanged(tileId)

            val entries = awaitEntries(shortcutMenu, listOf(ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID))
            assertEquals(listOf(ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID), entries)
        } finally {
            albums.stop()
            shortcutMenu.stop()
        }
    }

    @Test
    fun changeAlbum_picksNewBindingOnDevice() {
        placeTile()
        runBlocking { repository.saveAlbumBinding(TileAlbumBinding(tileId, cameraAlbum)) }
        val albums = albumCoordinator()
        try {
            albums.onHomeTilesChanged(listOf(renderTile()))
            albums.openAlbumPicker(tileId)

            val end = SystemClock.uptimeMillis() + 5_000L
            while (SystemClock.uptimeMillis() < end && albums.pickerAlbums.value == null) {
                SystemClock.sleep(50)
            }
            assertEquals(1, albums.pickerAlbums.value?.size)
            assertEquals(0, permissionRequests)

            albums.onAlbumPick(otherAlbum)
            assertEquals(TileAlbumBinding(tileId, otherAlbum), awaitBinding(TileAlbumBinding(tileId, otherAlbum)))
        } finally {
            albums.stop()
        }
    }

    @Test
    fun changeAlbumDenied_requestsPermissionAndOpensNothing() {
        access = MediaAccess.DENIED
        placeTile()
        runBlocking { repository.saveAlbumBinding(TileAlbumBinding(tileId, cameraAlbum)) }
        val albums = albumCoordinator()
        try {
            albums.onHomeTilesChanged(listOf(renderTile()))
            albums.openAlbumPicker(tileId)

            assertEquals(1, permissionRequests)
            assertNull(albums.pickerAlbums.value)
            assertEquals(cameraAlbum, repository.loadAlbumBinding(tileId)?.album)
        } finally {
            albums.stop()
        }
    }

    @Test
    fun removeAlbum_clearsBindingPhotoAndMenuRows() {
        placeTile()
        runBlocking { repository.saveAlbumBinding(TileAlbumBinding(tileId, cameraAlbum)) }
        val albums = albumCoordinator()
        val shortcutMenu = menuCoordinator()
        try {
            albums.onHomeTilesChanged(listOf(renderTile()))
            shortcutMenu.onHomeTilesChanged(listOf(renderTile()))
            shortcutMenu.onSelectionChanged(tileId)
            assertEquals(
                listOf(ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID),
                awaitEntries(shortcutMenu, listOf(ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID)),
            )
            // Serialize with the coordinator's initial load: only remove once
            // its refresh has projected, so no in-flight reload can resurrect
            // the binding afterwards.
            val settled = SystemClock.uptimeMillis() + 5_000L
            while (SystemClock.uptimeMillis() < settled && albums.selectedPhotoByTile.value[tileId] == null) {
                SystemClock.sleep(50)
            }
            assertEquals("content://media/p1", albums.selectedPhotoByTile.value[tileId])

            runBlocking { albums.removeAlbumBinding(tileId) }
            shortcutMenu.refreshSelection()

            assertTrue(awaitEntries(shortcutMenu, emptyList()).isEmpty())
            assertNull(shortcutMenu.menu.value)
            assertNull(repository.loadAlbumBinding(tileId))
            assertTrue(albums.selectedPhotoByTile.value.isEmpty())
            assertTrue(albums.albumPhotoByTile.value.isEmpty())
        } finally {
            albums.stop()
            shortcutMenu.stop()
        }
    }

    private class FakeGateway : ShortcutGateway {
        private val mutable = MutableStateFlow(emptyMap<AppIdentity, List<me.foxtails.lakehead.core.model.LauncherShortcut>>())
        override val shortcuts: StateFlow<Map<AppIdentity, List<me.foxtails.lakehead.core.model.LauncherShortcut>>> = mutable
        override fun refresh(identity: AppIdentity) = Unit
        override fun launch(ref: me.foxtails.lakehead.core.model.ShortcutRef): Boolean = false
        override fun start(scope: CoroutineScope) = Unit
        override fun stop() = Unit
    }

    private class FakeLibrary : AlbumMediaLibrary {
        var albums: List<MediaAlbum> = emptyList()
        val photosByBucket = mutableMapOf<String, MutableList<AlbumPhoto>>()
        private val changesFlow = MutableStateFlow(0L)
        override val changes: StateFlow<Long> = changesFlow.asStateFlow()
        override suspend fun albums(): List<MediaAlbum> = albums
        override suspend fun photos(bucketId: String): List<AlbumPhoto> =
            photosByBucket[bucketId].orEmpty().toList()
        override suspend fun thumbnail(contentUri: String, sizePx: Int): ImageBitmap? = null
    }
}
