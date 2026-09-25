package me.foxtails.lakehead

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.RemoveResult
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.feature.home.HomeViewportInputs
import me.foxtails.lakehead.platform.launcherapps.AppCatalogue
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshotCache
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshot
import me.foxtails.lakehead.platform.launcherapps.LauncherAppIconPngEncoder
import me.foxtails.lakehead.platform.launcherapps.LauncherAppIcon
import me.foxtails.lakehead.platform.launcherapps.LauncherAppIconLookup
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconSnapshotRefresher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRouteLoaderTest {
    private val requestedHome = HomeId("home-main")
    private val viewport = HomeRouteViewport(
        inputs = HomeViewportInputs(
            viewportWidthPx = 1080,
            horizontalPaddingPx = 32,
            columnGapPx = 16,
            minimumCellWidthPx = 160,
            viewportRows = 10,
            visibleRows = GridRowWindow(0, 10),
        ),
        columnsBound = 6,
        rowsBound = 4096,
    )
    private val profileA = ProfileId("personal")
    private val profileB = ProfileId("work")
    private val appA = AppIdentity(profileA, ComponentIdentity("com.example", "Main"))
    private val appB = AppIdentity(profileB, ComponentIdentity("com.example", "Main"))

    @Test
    fun explicitHomeIdLoadingToLoadedEmptyAndNotFoundAreDistinctWithoutWrites() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeRepository { id, columns, rows ->
            assertEquals(requestedHome, id)
            assertEquals(6, columns)
            assertEquals(4096, rows)
            LoadResult.Loaded(emptyList())
        }
        val loader = loader(repository, dispatcher)
        assertEquals(HomeRenderState.Loading(requestedHome), loader.state.value)
        loader.start(this)
        advanceUntilIdle()
        assertTrue(loader.state.value is HomeRenderState.Loaded)
        assertTrue((loader.state.value as HomeRenderState.Loaded).tiles.isEmpty())
        assertEquals(0, repository.mutations)
        loader.stop()

        val missingRepository = FakeRepository { _, _, _ -> LoadResult.NotFound }
        val missing = loader(missingRepository, dispatcher)
        missing.start(this)
        advanceUntilIdle()
        assertEquals(HomeRenderState.NotFound(requestedHome), missing.state.value)
        assertEquals(0, missingRepository.mutations)
        missing.stop()
    }

    @Test
    fun placementsJoinByFullIdentityRetainOrderAvailabilityAndExcludeCatalogueOnlyApps() = runTest {
        val placements = listOf(
            placement("work", appB, ItemAvailability.UNAVAILABLE),
            placement("personal", appA),
        )
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(placements) }
        val catalogue = FakeCatalogue(listOf(
            LauncherAppEntry(appA, "Personal"),
            LauncherAppEntry(appB, "Work"),
            LauncherAppEntry(identity("guest"), "Catalogue-only"),
        ))
        val loader = loader(repository, StandardTestDispatcher(testScheduler), catalogue)
        loader.start(this)
        advanceUntilIdle()
        val loaded = loader.state.value as HomeRenderState.Loaded
        assertEquals(listOf(ItemId("work"), ItemId("personal")), loaded.tiles.map { it.itemId })
        assertEquals(appB, loaded.tiles[0].appIdentity)
        assertEquals("Work", loaded.tiles[0].label)
        assertEquals(ItemAvailability.UNAVAILABLE, loaded.tiles[0].availability)
        assertFalse(loaded.tiles[0].isLaunchable)
        assertEquals("Personal", loaded.tiles[1].label)
        assertTrue(loaded.tiles[1].isLaunchable)
        assertFalse(loaded.tiles.any { it.label == "Catalogue-only" })

        catalogue.mutable.value = listOf(LauncherAppEntry(appA, "Updated Personal"))
        advanceUntilIdle()
        val updated = loader.state.value as HomeRenderState.Loaded
        assertEquals(listOf(ItemId("work"), ItemId("personal")), updated.tiles.map { it.itemId })
        assertNull(updated.tiles[0].label)
        assertEquals("Updated Personal", updated.tiles[1].label)
        assertEquals("Catalogue emissions must not reload persisted placements", 1, repository.reads)
        loader.stop()
    }

    @Test
    fun missingMetadataKeepsIdentityLabelOnlyAndUnavailablePlacement() = runTest {
        val persisted = listOf(placement("gone", appB, ItemAvailability.UNAVAILABLE))
        val loader = loader(FakeRepository { _, _, _ -> LoadResult.Loaded(persisted) },
            StandardTestDispatcher(testScheduler))
        loader.start(this)
        advanceUntilIdle()
        val tile = (loader.state.value as HomeRenderState.Loaded).tiles.single()
        assertEquals(appB, tile.appIdentity)
        assertNull(tile.label)
        assertFalse(tile.isLaunchable)
        loader.stop()
    }

    @Test
    fun placedFullIdentityFlowsThroughSharedEncodedCacheToDecodedIconSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val refreshedIdentities = mutableListOf<AppIdentity>()
        val decodedPayloads = mutableListOf<String>()
        val testIconLookup = LauncherAppIconLookup { identity ->
            refreshedIdentities += identity
            TestLauncherIcon(identity)
        }
        val testEncoder = LauncherAppIconPngEncoder { icon ->
            (icon as TestLauncherIcon).identity.profileId.value.encodeToByteArray()
        }
        val testDecoder: (ByteArray) -> ImageBitmap? = { png ->
            decodedPayloads += png.decodeToString()
            TestImageBitmap
        }
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(listOf(placement("work", appB))) }
        val catalogue = FakeCatalogue(listOf(
            LauncherAppEntry(appA, "Personal"),
            LauncherAppEntry(appB, "Work"),
        ))
        val loader = loader(repository, dispatcher, catalogue, testIconLookup, testEncoder, testDecoder)

        loader.start(this)
        advanceUntilIdle()

        assertEquals(listOf(appB), refreshedIdentities)
        assertEquals(listOf("work"), decodedPayloads)
        assertEquals(setOf(appB), loader.icons.value.keys)
        assertSame(TestImageBitmap, loader.icons.value[appB])
        assertFalse("Same component in another profile must not share the icon key", appA in loader.icons.value)
        loader.stop()
    }

    @Test
    fun cancelledOlderLoadCannotOverwriteNewerReload() = runTest {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        var count = 0
        val repository = FakeRepository { _, _, _ ->
            val request = synchronized(this) { ++count }
            if (request == 1) {
                firstStarted.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
                LoadResult.NotFound
            } else {
                secondStarted.countDown()
                LoadResult.Loaded(listOf(placement("new", appA)))
            }
        }
        val loader = loader(repository, Dispatchers.IO)
        val routeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            loader.start(routeScope)
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            loader.reload()
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
            val loaded = loader.state.first { it is HomeRenderState.Loaded } as HomeRenderState.Loaded
            assertEquals(listOf(ItemId("new")), loaded.tiles.map { it.itemId })
            releaseFirst.countDown()
            delay(100)
            assertTrue(loader.state.value is HomeRenderState.Loaded)
        } finally {
            releaseFirst.countDown()
            loader.stop()
            routeScope.cancel()
        }
    }

    @Test
    fun decodedIconSnapshotIsImmutableBoundedAndUsesInjectedDecoder() {
        val imageA = "decoded-image"
        var decoded = 0
        val cache = DecodedHomeIconCache<String>(maxEntries = 1) {
            decoded++
            imageA
        }
        val snapshots = linkedMapOf(
            appA to AppIconSnapshot(appA, byteArrayOf(1)),
            appB to AppIconSnapshot(appB, byteArrayOf(2)),
        )
        val first = cache.replace(snapshots)
        val second = cache.snapshot()
        assertEquals(2, decoded)
        assertEquals(1, first.size)
        assertEquals(listOf(appB), first.keys.toList())
        assertNotSame(first, second)
        assertTrue(first[appB] === imageA)
        try {
            (first as MutableMap<*, *>).clear()
            throw AssertionError("snapshot must be immutable")
        } catch (_: UnsupportedOperationException) { }
    }

    private fun loader(
        repository: HomePlacementRepository,
        dispatcher: CoroutineDispatcher,
        catalogue: FakeCatalogue = FakeCatalogue(emptyList()),
        iconLookup: LauncherAppIconLookup = LauncherAppIconLookup { null },
        pngEncoder: LauncherAppIconPngEncoder = LauncherAppIconPngEncoder { null },
        decoder: (ByteArray) -> ImageBitmap? = { null },
    ): HomeRouteLoader {
        val encodedIcons = AppIconSnapshotCache(8)
        return HomeRouteLoader(
            homeId = requestedHome,
            repository = repository,
            catalogue = catalogue,
            iconRefresher = LauncherAppsIconSnapshotRefresher(
                iconLookup,
                pngEncoder,
                encodedIcons,
                dispatcher,
            ),
            encodedIcons = encodedIcons,
            decodedIcons = DecodedHomeIconCache(8, decoder),
            viewport = viewport,
            ioDispatcher = dispatcher,
        )
    }

    private fun placement(
        item: String,
        identity: AppIdentity,
        availability: ItemAvailability = ItemAvailability.AVAILABLE,
    ) = AppPlacement(requestedHome, ItemId(item), identity, LogicalPosition(0, 0), LogicalSpan(1, 1), availability)

    private fun identity(profile: String) = AppIdentity(ProfileId(profile), ComponentIdentity("com.other", "Other"))

    private data class TestLauncherIcon(val identity: AppIdentity) : LauncherAppIcon

    private object TestImageBitmap : ImageBitmap {
        override val width: Int = 1
        override val height: Int = 1
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val hasAlpha: Boolean = true
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) = Unit
        override fun prepareToDraw() = Unit
    }

    private class FakeCatalogue(entries: List<LauncherAppEntry>) : AppCatalogue {
        val mutable = MutableStateFlow(entries)
        override val apps = mutable
    }

    private class FakeRepository(
        private val read: (HomeId, Int, Int) -> LoadResult,
    ) : HomePlacementRepository {
        var mutations = 0
        var reads = 0
        override fun load(homeId: HomeId, columns: Int, rows: Int): LoadResult {
            reads++
            return read(homeId, columns, rows)
        }
        override fun place(homeId: HomeId, itemId: ItemId, appIdentity: AppIdentity, position: LogicalPosition,
            span: LogicalSpan, columns: Int, rows: Int, replacingId: ItemId?): MutationResult {
            mutations++; error("loader must not write")
        }
        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int, rows: Int): MutationResult {
            mutations++; error("loader must not write")
        }
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int, rows: Int): MutationResult {
            mutations++; error("loader must not write")
        }
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult {
            mutations++; error("loader must not write")
        }
    }
}
