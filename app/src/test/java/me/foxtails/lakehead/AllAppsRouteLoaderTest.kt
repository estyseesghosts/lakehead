package me.foxtails.lakehead

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.platform.launcherapps.AppCatalogue
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshotCache
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshot
import me.foxtails.lakehead.platform.launcherapps.LauncherAppIconPngEncoder
import me.foxtails.lakehead.platform.launcherapps.LauncherAppIcon
import me.foxtails.lakehead.platform.launcherapps.LauncherAppIconLookup
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsIconSnapshotRefresher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AllAppsRouteLoaderTest {
    private val homeId = HomeId("home-main")
    private val profileA = ProfileId("personal")
    private val profileB = ProfileId("work")
    private val appA = AppIdentity(profileA, ComponentIdentity("com.example", "Main"))
    private val appB = AppIdentity(profileB, ComponentIdentity("com.example", "Main"))
    private val appC = AppIdentity(profileA, ComponentIdentity("com.other", "Other"))

    @Test
    fun preservesCatalogueOrderAndMarksAlreadyOnHome() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val placements = listOf(
            AppPlacement(homeId, ItemId("placed"), appA, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE),
        )
        val repository = FakeReadRepository(LoadResult.Loaded(placements))
        val catalogue = FakeCatalogue(listOf(
            LauncherAppEntry(appA, "App A"),
            LauncherAppEntry(appB, "App B"),
            LauncherAppEntry(appC, "App C"),
        ))
        val loader = allAppsLoader(repository, dispatcher, catalogue)
        loader.start(this)
        advanceUntilIdle()

        val loaded = loader.state.value as AllAppsViewState.Loaded
        assertEquals(3, loaded.entries.size)
        // Catalogue order preserved
        assertEquals(appA, loaded.entries[0].identity)
        assertEquals("App A", loaded.entries[0].label)
        assertTrue(loaded.entries[0].alreadyOnHome)
        assertEquals(appB, loaded.entries[1].identity)
        assertFalse(loaded.entries[1].alreadyOnHome)
        assertEquals(appC, loaded.entries[2].identity)
        assertFalse(loaded.entries[2].alreadyOnHome)
        loader.stop()
    }

    @Test
    fun notFoundHomeYieldsAllFalseMembership() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeReadRepository(LoadResult.NotFound)
        val catalogue = FakeCatalogue(listOf(
            LauncherAppEntry(appA, "App A"),
            LauncherAppEntry(appB, "App B"),
        ))
        val loader = allAppsLoader(repository, dispatcher, catalogue)
        loader.start(this)
        advanceUntilIdle()

        val loaded = loader.state.value as AllAppsViewState.Loaded
        assertEquals(2, loaded.entries.size)
        assertFalse(loaded.entries[0].alreadyOnHome)
        assertFalse(loaded.entries[1].alreadyOnHome)
        loader.stop()
    }

    @Test
    fun catalogueUpdatesPreserveOrderAndRefreshMembership() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val placements = listOf(
            AppPlacement(homeId, ItemId("placed"), appA, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE),
        )
        val repository = FakeReadRepository(LoadResult.Loaded(placements))
        val catalogue = FakeCatalogue(listOf(
            LauncherAppEntry(appA, "App A"),
            LauncherAppEntry(appB, "App B"),
        ))
        val loader = allAppsLoader(repository, dispatcher, catalogue)
        loader.start(this)
        advanceUntilIdle()

        val first = loader.state.value as AllAppsViewState.Loaded
        assertEquals(2, first.entries.size)
        assertTrue(first.entries[0].alreadyOnHome)
        assertFalse(first.entries[1].alreadyOnHome)

        // Update catalogue: reorder and add new app
        catalogue.mutable.value = listOf(
            LauncherAppEntry(appB, "App B Updated"),
            LauncherAppEntry(appC, "App C"),
            LauncherAppEntry(appA, "App A"),
        )
        advanceUntilIdle()

        val updated = loader.state.value as AllAppsViewState.Loaded
        assertEquals(3, updated.entries.size)
        assertEquals(appB, updated.entries[0].identity)
        assertEquals("App B Updated", updated.entries[0].label)
        assertFalse(updated.entries[0].alreadyOnHome)
        assertEquals(appC, updated.entries[1].identity)
        assertFalse(updated.entries[1].alreadyOnHome)
        assertEquals(appA, updated.entries[2].identity)
        assertTrue(updated.entries[2].alreadyOnHome)
        // Catalogue update must not trigger a repository re-read
        assertEquals(1, repository.reads)
        loader.stop()
    }

    @Test
    fun iconsRefreshedForAllCatalogueEntriesNotJustPlaced() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val refreshedIdentities = mutableListOf<AppIdentity>()
        val testIconLookup = LauncherAppIconLookup { identity ->
            refreshedIdentities += identity
            TestLauncherIcon(identity)
        }
        val testEncoder = LauncherAppIconPngEncoder { icon ->
            (icon as TestLauncherIcon).identity.profileId.value.encodeToByteArray()
        }
        val testDecoder: (ByteArray) -> ImageBitmap? = { _ -> TestImageBitmap }
        val placements = listOf(
            AppPlacement(homeId, ItemId("placed"), appA, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE),
        )
        val repository = FakeReadRepository(LoadResult.Loaded(placements))
        val catalogue = FakeCatalogue(listOf(
            LauncherAppEntry(appA, "App A"),
            LauncherAppEntry(appB, "App B"),
        ))
        val encodedIcons = AppIconSnapshotCache(8)
        val loader = AllAppsRouteLoader(
            homeId = homeId,
            repository = repository,
            catalogue = catalogue,
            iconRefresher = LauncherAppsIconSnapshotRefresher(testIconLookup, testEncoder, encodedIcons, dispatcher),
            encodedIcons = encodedIcons,
            decodedIcons = DecodedHomeIconCache(8, testDecoder),
            ioDispatcher = dispatcher,
        )
        loader.start(this)
        advanceUntilIdle()

        // Both apps should be refreshed, not just the placed one
        assertEquals(setOf(appA, appB), refreshedIdentities.toSet())
        assertEquals(2, loader.icons.value.size)
        assertTrue(appA in loader.icons.value)
        assertTrue(appB in loader.icons.value)
        loader.stop()
    }

    private fun allAppsLoader(
        repository: HomePlacementRepository,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        catalogue: FakeCatalogue = FakeCatalogue(emptyList()),
    ): AllAppsRouteLoader {
        val encodedIcons = AppIconSnapshotCache(8)
        return AllAppsRouteLoader(
            homeId = homeId,
            repository = repository,
            catalogue = catalogue,
            iconRefresher = LauncherAppsIconSnapshotRefresher(
                LauncherAppIconLookup { null },
                LauncherAppIconPngEncoder { null },
                encodedIcons,
                dispatcher,
            ),
            encodedIcons = encodedIcons,
            decodedIcons = DecodedHomeIconCache(8) { null },
            ioDispatcher = dispatcher,
        )
    }

    private data class TestLauncherIcon(val identity: AppIdentity) : LauncherAppIcon

    private object TestImageBitmap : ImageBitmap {
        override val width: Int = 1
        override val height: Int = 1
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val hasAlpha: Boolean = true
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override fun readPixels(
            buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int,
        ) = Unit
        override fun prepareToDraw() = Unit
    }

    private class FakeCatalogue(entries: List<LauncherAppEntry>) : AppCatalogue {
        val mutable = MutableStateFlow(entries)
        override val apps = mutable
    }

    private class FakeReadRepository(
        private val result: LoadResult,
    ) : HomePlacementRepository {
        var reads = 0
            private set
        override fun load(homeId: HomeId, columns: Int, rows: Int): LoadResult {
            reads++
            return result
        }
        override fun place(
            homeId: HomeId, itemId: ItemId, appIdentity: AppIdentity,
            position: LogicalPosition, span: LogicalSpan, columns: Int, rows: Int, replacingId: ItemId?,
        ): MutationResult = error("loader must not write")
        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int, rows: Int): MutationResult =
            error("loader must not write")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int, rows: Int): MutationResult =
            error("loader must not write")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = error("loader must not write")
        override fun ensureHome(homeId: HomeId): Boolean = error("loader must not write")
    }
}