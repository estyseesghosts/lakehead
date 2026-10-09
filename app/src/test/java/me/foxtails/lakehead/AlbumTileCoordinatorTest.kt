package me.foxtails.lakehead

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.RemoveResult
import me.foxtails.lakehead.feature.home.HomeRenderTile
import me.foxtails.lakehead.platform.media.MediaAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 6 unit tests for the album tile coordinator: the resize offer rules,
 * the prompt → permission → list → save flow, the 15-minute rotation clock
 * with a fake clock, tap advance with wrap-around, and the visibility-gated
 * ticker (no timer while Home is hidden).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumTileCoordinatorTest {
    private companion object {
        const val MINUTE_MS: Long = 60_000L
        const val SLOT_MS: Long = 15 * MINUTE_MS
    }

    private val personal = ProfileId("personal")
    private val work = ProfileId("work")
    private val galleryComponent = ComponentIdentity("com.sec.android.gallery3d", "com.sec.android.gallery3d.Main")
    private val photosComponent = ComponentIdentity("com.google.android.apps.photos", "com.google.android.apps.photos.Main")

    private val newest = AlbumPhoto("content://media/newest", 300L)
    private val mid = AlbumPhoto("content://media/mid", 200L)
    private val oldest = AlbumPhoto("content://media/oldest", 100L)
    private val cameraAlbum = AlbumRef("b1", "Camera")

    private fun galleryTile(
        id: String,
        span: LogicalSpan,
        label: String? = "Gallery",
        widgetId: Int? = null,
        profile: ProfileId = personal,
    ) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = AppIdentity(profile, galleryComponent),
        position = LogicalPosition(0, 0),
        span = span,
        availability = ItemAvailability.AVAILABLE,
        label = label,
        icon = null,
        isLaunchable = label != null,
        appWidgetId = widgetId,
    )

    private fun photosTile(id: String, span: LogicalSpan) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = AppIdentity(personal, photosComponent),
        position = LogicalPosition(0, 0),
        span = span,
        availability = ItemAvailability.AVAILABLE,
        label = "Photos",
        icon = null,
        isLaunchable = true,
    )

    private inner class Fixture {
        val repo = FakeAlbumRepository()
        val library = FakeAlbumLibrary()
        var now = 0L
        var access = MediaAccess.DENIED
        var permissionRequests = 0

        init {
            library.albums = listOf(MediaAlbum(cameraAlbum, 3, newest.contentUri))
            library.photosByBucket[cameraAlbum.bucketId] = mutableListOf(newest, mid, oldest)
        }

        fun coordinator(dispatcher: CoroutineDispatcher, scope: CoroutineScope) = AlbumTileCoordinator(
            repository = repo,
            library = library,
            accessNow = { access },
            onRequestPermission = { permissionRequests++ },
            clock = { now },
            ioDispatcher = dispatcher,
        ).also { it.start(scope) }
    }

    private fun offer(
        coordinator: AlbumTileCoordinator,
        tile: HomeRenderTile,
        oldSpan: LogicalSpan,
        newSpan: LogicalSpan,
        isPersonalProfile: Boolean = true,
    ) {
        coordinator.onHomeTilesChanged(listOf(tile))
        coordinator.onResized(tile, oldSpan, newSpan, isPersonalProfile)
    }

    @Test
    fun resizeCrossingOffersPromptForGallery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()

        assertEquals(AlbumOfferPrompt(ItemId("t"), "Gallery"), coordinator.prompt.value)
        coordinator.stop()
    }

    @Test
    fun alreadyBoundTileNeverOffers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.repo.saveAlbumBinding(TileAlbumBinding(ItemId("t"), cameraAlbum))
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()

        assertNull(coordinator.prompt.value)
        coordinator.stop()
    }

    @Test
    fun largeToLargeResizeNeverReoffers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        offer(coordinator, tile, LogicalSpan(2, 2), LogicalSpan(4, 2))
        advanceUntilIdle()

        assertNull(coordinator.prompt.value)
        coordinator.stop()
    }

    @Test
    fun nonGalleryPackageNeverOffers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = photosTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()

        assertNull(coordinator.prompt.value)
        coordinator.stop()
    }

    @Test
    fun widgetTileNeverOffers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1), widgetId = 7)
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()

        assertNull(coordinator.prompt.value)
        coordinator.stop()
    }

    @Test
    fun workProfileTileNeverOffers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1), profile = work)
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2), isPersonalProfile = false)
        advanceUntilIdle()

        assertNull(coordinator.prompt.value)
        coordinator.stop()
    }

    @Test
    fun declineClearsAndNextCrossingOffersAgain() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()
        assertEquals(ItemId("t"), coordinator.prompt.value?.tileId)

        coordinator.onPromptDecline()
        assertNull(coordinator.prompt.value)

        // "No" is not remembered: the next crossing asks again.
        coordinator.onResized(tile, LogicalSpan(1, 1), LogicalSpan(2, 2), true)
        advanceUntilIdle()
        assertEquals(ItemId("t"), coordinator.prompt.value?.tileId)
        coordinator.stop()
    }

    @Test
    fun acceptWithAccessOpensPickerWithoutPermissionRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()

        coordinator.onPromptAccept()
        advanceUntilIdle()

        assertNull(coordinator.prompt.value)
        assertEquals(0, fixture.permissionRequests)
        assertEquals(
            listOf(MediaAlbum(cameraAlbum, 3, newest.contentUri)),
            coordinator.pickerAlbums.value,
        )
        coordinator.stop()
    }

    @Test
    fun acceptWithoutAccessRequestsPermissionAndOpensNoPicker() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()

        coordinator.onPromptAccept()
        advanceUntilIdle()

        assertEquals(1, fixture.permissionRequests)
        assertNull(coordinator.pickerAlbums.value)
        coordinator.stop()
    }

    @Test
    fun grantAfterRequestOpensPicker() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()
        coordinator.onPromptAccept()
        advanceUntilIdle()

        coordinator.onPermissionResult(MediaAccess.PARTIAL)
        advanceUntilIdle()

        assertEquals(1, fixture.library.albums.size)
        assertEquals(
            listOf(MediaAlbum(cameraAlbum, 3, newest.contentUri)),
            coordinator.pickerAlbums.value,
        )
        coordinator.stop()
    }

    @Test
    fun denyAfterRequestKeepsPickerClosed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()
        coordinator.onPromptAccept()
        advanceUntilIdle()

        coordinator.onPermissionResult(MediaAccess.DENIED)
        advanceUntilIdle()

        assertNull(coordinator.pickerAlbums.value)
        assertTrue(fixture.repo.savedBindings.isEmpty())
        coordinator.stop()
    }

    @Test
    fun pickSavesBindingAndSelectsNewest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()
        coordinator.onPromptAccept()
        advanceUntilIdle()

        coordinator.onAlbumPick(cameraAlbum)
        advanceUntilIdle()

        assertEquals(
            listOf(TileAlbumBinding(ItemId("t"), cameraAlbum)),
            fixture.repo.savedBindings,
        )
        assertNull(coordinator.pickerAlbums.value)
        // Slot 0, no taps: the newest photo.
        assertEquals(mapOf(ItemId("t") to newest.contentUri), coordinator.selectedPhotoByTile.value)
        coordinator.stop()
    }

    @Test
    fun fifteenMinuteSlotAdvancesPhoto() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        val coordinator = fixture.coordinator(dispatcher, this)
        coordinator.setHomeVisible(true)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        // While the ticker runs, settle with runCurrent: advanceUntilIdle
        // would chase the next tick forever and never return.
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        runCurrent()
        coordinator.onPromptAccept()
        runCurrent()
        coordinator.onAlbumPick(cameraAlbum)
        runCurrent()
        assertEquals(newest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        fixture.now = SLOT_MS
        advanceTimeBy(SLOT_MS)
        runCurrent()
        assertEquals(mid.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        fixture.now = 2 * SLOT_MS
        advanceTimeBy(SLOT_MS)
        runCurrent()
        assertEquals(oldest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        // Sequential order wraps around the album.
        fixture.now = 3 * SLOT_MS
        advanceTimeBy(SLOT_MS)
        runCurrent()
        assertEquals(newest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])
        coordinator.stop()
        // The ticker is cancelled: this settles and proves no live coroutines.
        advanceUntilIdle()
    }

    @Test
    fun tapAdvancesPhotoWithWrapAndIgnoresUnboundTiles() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        val plain = galleryTile("plain", LogicalSpan(2, 2))
        coordinator.onHomeTilesChanged(listOf(tile, plain))
        advanceUntilIdle()
        coordinator.onResized(tile, LogicalSpan(1, 1), LogicalSpan(2, 2), true)
        advanceUntilIdle()
        coordinator.onPromptAccept()
        advanceUntilIdle()
        coordinator.onAlbumPick(cameraAlbum)
        advanceUntilIdle()

        coordinator.onHomeTileTapped(ItemId("plain"))
        advanceUntilIdle()
        assertEquals(newest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        coordinator.onHomeTileTapped(ItemId("t"))
        advanceUntilIdle()
        assertEquals(mid.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        coordinator.onHomeTileTapped(ItemId("t"))
        advanceUntilIdle()
        assertEquals(oldest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        coordinator.onHomeTileTapped(ItemId("t"))
        advanceUntilIdle()
        assertEquals(newest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])
        coordinator.stop()
    }

    @Test
    fun noTimerWhileHomeIsHidden() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        val coordinator = fixture.coordinator(dispatcher, this)
        coordinator.setHomeVisible(true)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        // While the ticker runs, settle with runCurrent: advanceUntilIdle
        // would chase the next tick forever and never return.
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        runCurrent()
        coordinator.onPromptAccept()
        runCurrent()
        coordinator.onAlbumPick(cameraAlbum)
        runCurrent()

        fixture.now = SLOT_MS
        advanceTimeBy(SLOT_MS)
        runCurrent()
        assertEquals(mid.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        // Hidden: four slots pass with no projection.
        coordinator.setHomeVisible(false)
        runCurrent()
        fixture.now = 5 * SLOT_MS
        advanceTimeBy(4 * SLOT_MS)
        runCurrent()
        assertEquals(mid.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        // Returning recomputes from the clock: slot 5 on 3 photos.
        coordinator.setHomeVisible(true)
        runCurrent()
        assertEquals(oldest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])
        coordinator.stop()
        // The ticker is cancelled: this settles and proves no live coroutines.
        advanceUntilIdle()
    }

    @Test
    fun libraryChangeRefreshesToNewestPhoto() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()
        coordinator.onPromptAccept()
        advanceUntilIdle()
        coordinator.onAlbumPick(cameraAlbum)
        advanceUntilIdle()
        assertEquals(newest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        val brandNew = AlbumPhoto("content://media/brand-new", 400L)
        fixture.library.photosByBucket[cameraAlbum.bucketId] =
            mutableListOf(brandNew, newest, mid, oldest)
        fixture.library.changesFlow.value++
        advanceUntilIdle()

        assertEquals(brandNew.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])
        coordinator.stop()
    }

    @Test
    fun emptyAlbumSelectsNothingButKeepsBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        fixture.library.photosByBucket[cameraAlbum.bucketId] = mutableListOf()
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()
        coordinator.onPromptAccept()
        advanceUntilIdle()
        coordinator.onAlbumPick(cameraAlbum)
        advanceUntilIdle()

        assertTrue(coordinator.selectedPhotoByTile.value.isEmpty())
        assertEquals(
            TileAlbumBinding(ItemId("t"), cameraAlbum),
            fixture.repo.loadAlbumBinding(ItemId("t")),
        )
        coordinator.stop()
    }

    @Test
    fun removedTileDropsPhotoPromptAndPendingPick() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(1, 1))
        offer(coordinator, tile, LogicalSpan(1, 1), LogicalSpan(2, 2))
        advanceUntilIdle()
        assertEquals(ItemId("t"), coordinator.prompt.value?.tileId)

        coordinator.onHomeTilesChanged(emptyList())
        advanceUntilIdle()

        assertNull(coordinator.prompt.value)
        assertNull(coordinator.pickerAlbums.value)
        coordinator.onAlbumPick(cameraAlbum)
        advanceUntilIdle()
        assertTrue(fixture.repo.savedBindings.isEmpty())
        coordinator.stop()
    }

    @Test
    fun changeAlbumWithAccessOpensPickerAndPickRebinds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        fixture.repo.saveAlbumBinding(TileAlbumBinding(ItemId("t"), cameraAlbum))
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        coordinator.onHomeTilesChanged(listOf(tile))
        advanceUntilIdle()

        coordinator.openAlbumPicker(ItemId("t"))
        advanceUntilIdle()

        assertEquals(0, fixture.permissionRequests)
        assertEquals(
            listOf(MediaAlbum(cameraAlbum, 3, newest.contentUri)),
            coordinator.pickerAlbums.value,
        )

        val other = AlbumRef("b2", "Screenshots")
        fixture.library.photosByBucket["b2"] = mutableListOf(mid)
        coordinator.onAlbumPick(other)
        advanceUntilIdle()

        assertEquals(TileAlbumBinding(ItemId("t"), other), fixture.repo.loadAlbumBinding(ItemId("t")))
        assertNull(coordinator.pickerAlbums.value)
        assertEquals(mapOf(ItemId("t") to mid.contentUri), coordinator.selectedPhotoByTile.value)
        coordinator.stop()
    }

    @Test
    fun changeAlbumWithoutAccessRequestsPermissionAndGrantOpensPicker() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.repo.saveAlbumBinding(TileAlbumBinding(ItemId("t"), cameraAlbum))
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        coordinator.onHomeTilesChanged(listOf(tile))
        advanceUntilIdle()

        coordinator.openAlbumPicker(ItemId("t"))
        advanceUntilIdle()

        assertEquals(1, fixture.permissionRequests)
        assertNull(coordinator.pickerAlbums.value)

        coordinator.onPermissionResult(MediaAccess.FULL)
        advanceUntilIdle()

        assertEquals(
            listOf(MediaAlbum(cameraAlbum, 3, newest.contentUri)),
            coordinator.pickerAlbums.value,
        )
        coordinator.stop()
    }

    @Test
    fun changeAlbumDenyKeepsPickerClosedAndBindingKept() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.repo.saveAlbumBinding(TileAlbumBinding(ItemId("t"), cameraAlbum))
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        coordinator.onHomeTilesChanged(listOf(tile))
        advanceUntilIdle()

        coordinator.openAlbumPicker(ItemId("t"))
        advanceUntilIdle()
        coordinator.onPermissionResult(MediaAccess.DENIED)
        advanceUntilIdle()

        assertNull(coordinator.pickerAlbums.value)
        assertEquals(cameraAlbum, fixture.repo.loadAlbumBinding(ItemId("t"))?.album)
        coordinator.stop()
    }

    @Test
    fun changeAlbumForUnknownTileIsIgnored() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        val coordinator = fixture.coordinator(dispatcher, this)
        coordinator.onHomeTilesChanged(listOf(galleryTile("t", LogicalSpan(2, 2))))
        advanceUntilIdle()

        coordinator.openAlbumPicker(ItemId("gone"))
        advanceUntilIdle()

        assertEquals(0, fixture.permissionRequests)
        assertNull(coordinator.pickerAlbums.value)
        coordinator.stop()
    }

    @Test
    fun removeAlbumBindingClearsBindingPhotoAndTapState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        fixture.repo.saveAlbumBinding(TileAlbumBinding(ItemId("t"), cameraAlbum))
        val coordinator = fixture.coordinator(dispatcher, this)
        val tile = galleryTile("t", LogicalSpan(2, 2))
        coordinator.onHomeTilesChanged(listOf(tile))
        advanceUntilIdle()
        assertEquals(newest.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        coordinator.onHomeTileTapped(ItemId("t"))
        advanceUntilIdle()
        assertEquals(mid.contentUri, coordinator.selectedPhotoByTile.value[ItemId("t")])

        coordinator.removeAlbumBinding(ItemId("t"))
        advanceUntilIdle()

        assertNull(fixture.repo.loadAlbumBinding(ItemId("t")))
        assertTrue(coordinator.selectedPhotoByTile.value.isEmpty())
        assertTrue(coordinator.albumPhotoByTile.value.isEmpty())

        // Taps after removal stay a no-op for the slideshow.
        coordinator.onHomeTileTapped(ItemId("t"))
        advanceUntilIdle()
        assertTrue(coordinator.selectedPhotoByTile.value.isEmpty())
        coordinator.stop()
    }

    @Test
    fun removeAlbumBindingClosesPendingPickerForSameTile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = Fixture()
        fixture.access = MediaAccess.FULL
        fixture.repo.saveAlbumBinding(TileAlbumBinding(ItemId("t"), cameraAlbum))
        val coordinator = fixture.coordinator(dispatcher, this)
        coordinator.onHomeTilesChanged(listOf(galleryTile("t", LogicalSpan(2, 2))))
        advanceUntilIdle()
        coordinator.openAlbumPicker(ItemId("t"))
        advanceUntilIdle()
        assertEquals(1, coordinator.pickerAlbums.value?.size)

        coordinator.removeAlbumBinding(ItemId("t"))
        advanceUntilIdle()

        assertNull(coordinator.pickerAlbums.value)
        assertNull(fixture.repo.loadAlbumBinding(ItemId("t")))
        coordinator.stop()
    }

    private class FakeAlbumRepository : HomePlacementRepository {
        private val albumBindings = mutableMapOf<ItemId, TileAlbumBinding>()
        val savedBindings = mutableListOf<TileAlbumBinding>()

        override fun load(homeId: HomeId, columns: Int): LoadResult = LoadResult.NotFound
        override fun place(
            homeId: HomeId,
            itemId: ItemId,
            appIdentity: AppIdentity,
            position: LogicalPosition,
            span: LogicalSpan,
            columns: Int,
            replacingId: ItemId?,
        ): MutationResult = error("album coordinator must not place")
        override fun placeWidget(
            homeId: HomeId,
            itemId: ItemId,
            provider: AppIdentity,
            appWidgetId: Int,
            position: LogicalPosition,
            span: LogicalSpan,
            columns: Int,
        ): MutationResult = error("album coordinator must not place")
        override fun persistedAppWidgetIds(): Set<Int> = emptySet()
        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int): MutationResult =
            error("album coordinator must not move")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int): MutationResult =
            error("album coordinator must not resize")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = error("album coordinator must not remove")
        override fun ensureHome(homeId: HomeId): Boolean = error("album coordinator must not write")
        override fun loadBinding(tileId: ItemId): TileBinding = TileBinding(tileId)
        override fun saveBinding(binding: TileBinding) = error("album coordinator must not write")
        override fun deleteBinding(tileId: ItemId) = error("album coordinator must not write")
        override fun loadAlbumBinding(tileId: ItemId): TileAlbumBinding? = albumBindings[tileId]
        override fun saveAlbumBinding(binding: TileAlbumBinding) {
            albumBindings[binding.tileId] = binding
            savedBindings += binding
        }
        override fun deleteAlbumBinding(tileId: ItemId) {
            albumBindings -= tileId
        }
    }

    private class FakeAlbumLibrary : AlbumMediaLibrary {
        var albums: List<MediaAlbum> = emptyList()
        val photosByBucket = mutableMapOf<String, MutableList<AlbumPhoto>>()
        val changesFlow = MutableStateFlow(0L)
        override val changes: StateFlow<Long> = changesFlow.asStateFlow()
        override suspend fun albums(): List<MediaAlbum> = albums
        override suspend fun photos(bucketId: String): List<AlbumPhoto> =
            photosByBucket[bucketId].orEmpty().toList()
        override suspend fun thumbnail(contentUri: String, sizePx: Int): ImageBitmap? = null
    }
}
