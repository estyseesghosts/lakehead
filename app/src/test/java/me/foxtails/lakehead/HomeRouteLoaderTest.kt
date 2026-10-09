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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.TileBinding
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
        ),
        columnsBound = 6,
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

        // The shared cache is driven by the whole catalogue, not the placed subset.
        assertEquals(listOf(appA, appB), refreshedIdentities)
        assertEquals(listOf(appA.profileId.value, "work"), decodedPayloads)
        assertEquals(setOf(appA, appB), loader.icons.value.keys)
        assertSame(TestImageBitmap, loader.icons.value[appB])
        assertEquals(
            "Same component in another profile keeps its own icon key",
            2,
            loader.icons.value.keys.map { it.profileId }.toSet().size,
        )
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
    fun commitMoveShowsTheRequestedCellAtOnceThenTheAcceptedGridWithoutReloading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persisted = listOf(
            placementAt("a", appA, 0, 0),
            placementAt("b", appB, 0, 10),
        )
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(persisted) }
        val loader = loader(repository, dispatcher)
        var duringWrite: Map<ItemId, LogicalPosition>? = null
        val moveResult = accepted(cell("a", 1, 1), cell("b", 0, 4))
        repository.moveAction = { _, _, _, _, _ ->
            duringWrite = positionsOf(loader)
            moveResult
        }
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        val result = loader.commitMove(ItemId("a"), LogicalPosition(1, 1))
        advanceUntilIdle()

        assertSame(moveResult, result)
        assertEquals(listOf(Triple(requestedHome, ItemId("a"), LogicalPosition(1, 1))), repository.moveCalls)
        // The first emission already carries the normalised grid the write will accept.
        assertEquals(
            mapOf(ItemId("a") to LogicalPosition(1, 1), ItemId("b") to LogicalPosition(0, 4)),
            duringWrite,
        )
        assertEquals(
            mapOf(ItemId("a") to LogicalPosition(1, 1), ItemId("b") to LogicalPosition(0, 4)),
            positionsOf(loader),
        )
        assertEquals("An accepted move must not need a reload", readsBefore, repository.reads)
        loader.stop()
    }

    @Test
    fun commitMoveAcceptedGridWinsOverTheRequestedCellWhenNormalizationMovesTheDroppedTile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persisted = listOf(placementAt("a", appA, 0, 0), placementAt("b", appB, 3, 0))
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(persisted) }
        val loader = loader(repository, dispatcher)
        var duringWrite: Map<ItemId, LogicalPosition>? = null
        repository.moveAction = { _, _, _, _, _ ->
            duringWrite = positionsOf(loader)
            accepted(cell("a", 0, 3), cell("b", 3, 0))
        }
        loader.start(this)
        advanceUntilIdle()

        loader.commitMove(ItemId("a"), LogicalPosition(0, 16))
        advanceUntilIdle()

        // Optimistic apply is already normalised: row 16 closes up to row 3.
        assertEquals(LogicalPosition(0, 3), duringWrite!!.getValue(ItemId("a")))
        assertEquals(LogicalPosition(0, 3), positionsOf(loader).getValue(ItemId("a")))
        loader.stop()
    }

    @Test
    fun commitMoveRejectedReloadsThePersistedCellOverTheOptimisticOne() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rejected = MutationResult.Rejected(
            me.foxtails.lakehead.core.grid.GridRules.Result.Rejected(
                me.foxtails.lakehead.core.grid.GridRules.Rejection.COLLISION
            )
        )
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placementAt("tile-a", appA, 0, 0)))
        }
        var duringWrite: LogicalPosition? = null
        val loader = loader(repository, dispatcher)
        repository.moveAction = { _, _, _, _, _ ->
            duringWrite = positionsOf(loader).getValue(ItemId("tile-a"))
            rejected
        }
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        val result = loader.commitMove(ItemId("tile-a"), LogicalPosition(2, 3))
        advanceUntilIdle()

        assertSame(rejected, result)
        assertEquals(1, repository.moveCalls.size)
        assertEquals(LogicalPosition(2, 3), duringWrite)
        assertEquals("A rejected move reloads to undo the optimistic cell", readsBefore + 1, repository.reads)
        assertEquals(LogicalPosition(0, 0), positionsOf(loader).getValue(ItemId("tile-a")))
        loader.stop()
    }

    @Test
    fun commitMoveNotFoundReloadsThePersistedState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placementAt("tile-a", appA, 0, 0)))
        }
        repository.moveAction = { _, _, _, _, _ -> MutationResult.NotFound }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        val result = loader.commitMove(ItemId("tile-a"), LogicalPosition(2, 3))
        advanceUntilIdle()

        assertTrue(result is MutationResult.NotFound)
        assertEquals(readsBefore + 1, repository.reads)
        assertEquals(LogicalPosition(0, 0), positionsOf(loader).getValue(ItemId("tile-a")))
        loader.stop()
    }

    @Test
    fun commitMoveFailureReloadsThePersistedStateAndRethrows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placementAt("tile-a", appA, 0, 0)))
        }
        repository.moveAction = { _, _, _, _, _ -> throw IllegalStateException("disk full") }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        var thrown: IllegalStateException? = null
        try {
            loader.commitMove(ItemId("tile-a"), LogicalPosition(2, 3))
        } catch (failure: IllegalStateException) {
            thrown = failure
        }
        advanceUntilIdle()

        assertEquals("disk full", thrown?.message)
        assertEquals(readsBefore + 1, repository.reads)
        assertEquals(LogicalPosition(0, 0), positionsOf(loader).getValue(ItemId("tile-a")))
        loader.stop()
    }

    @Test
    fun acceptedGridNamingAnUnknownTileFallsBackToAReload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placementAt("a", appA, 0, 0)))
        }
        repository.moveAction = { _, _, _, _, _ -> accepted(cell("a", 1, 1), cell("added-elsewhere", 2, 2)) }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        loader.commitMove(ItemId("a"), LogicalPosition(1, 1))
        advanceUntilIdle()

        assertEquals(readsBefore + 1, repository.reads)
        loader.stop()
    }

    @Test
    fun secondMoveWhileTheFirstIsInFlightIsNotOverwrittenByTheFirstResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persisted = listOf(placementAt("a", appA, 0, 0), placementAt("b", appB, 0, 5))
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(persisted) }
        val loader = loader(repository, dispatcher)
        var bWhenSecondWriteStarts: LogicalPosition? = null
        repository.moveAction = { _, itemId, _, _, _ ->
            if (itemId == ItemId("a")) {
                // Database state as of the first write: b is still at its old cell.
                accepted(cell("a", 1, 1), cell("b", 0, 5))
            } else {
                bWhenSecondWriteStarts = positionsOf(loader).getValue(ItemId("b"))
                accepted(cell("a", 1, 1), cell("b", 2, 2))
            }
        }
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        launch { loader.commitMove(ItemId("a"), LogicalPosition(1, 1)) }
        launch { loader.commitMove(ItemId("b"), LogicalPosition(2, 2)) }
        advanceUntilIdle()

        assertEquals(listOf(ItemId("a"), ItemId("b")), repository.moveCalls.map { it.second })
        assertEquals(LogicalPosition(2, 2), bWhenSecondWriteStarts)
        assertEquals(
            mapOf(ItemId("a") to LogicalPosition(1, 1), ItemId("b") to LogicalPosition(2, 2)),
            positionsOf(loader),
        )
        assertEquals(readsBefore, repository.reads)
        loader.stop()
    }

    @Test
    fun firstMoveRejectedThenSecondAccepted_usesTheSecondGridWithoutReloading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persisted = listOf(placementAt("a", appA, 0, 0), placementAt("b", appB, 0, 5))
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(persisted) }
        val loader = loader(repository, dispatcher)
        repository.moveAction = { _, itemId, _, _, _ ->
            if (itemId == ItemId("a")) {
                MutationResult.Rejected(
                    me.foxtails.lakehead.core.grid.GridRules.Result.Rejected(
                        me.foxtails.lakehead.core.grid.GridRules.Rejection.COLLISION,
                    ),
                )
            } else {
                accepted(cell("a", 0, 0), cell("b", 2, 2))
            }
        }
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        launch { loader.commitMove(ItemId("a"), LogicalPosition(1, 1)) }
        launch { loader.commitMove(ItemId("b"), LogicalPosition(2, 2)) }
        advanceUntilIdle()

        assertEquals(
            mapOf(ItemId("a") to LogicalPosition(0, 0), ItemId("b") to LogicalPosition(2, 2)),
            positionsOf(loader),
        )
        assertEquals(readsBefore, repository.reads)
        loader.stop()
    }

    @Test
    fun firstMoveAcceptedThenSecondRejected_reloadsTheFinalPersistedState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var loads = 0
        val repository = FakeRepository { _, _, _ ->
            loads++
            if (loads == 1) {
                LoadResult.Loaded(listOf(placementAt("a", appA, 0, 0), placementAt("b", appB, 0, 5)))
            } else {
                LoadResult.Loaded(listOf(placementAt("a", appA, 1, 1), placementAt("b", appB, 0, 5)))
            }
        }
        val loader = loader(repository, dispatcher)
        repository.moveAction = { _, itemId, _, _, _ ->
            if (itemId == ItemId("a")) {
                accepted(cell("a", 1, 1), cell("b", 0, 5))
            } else {
                MutationResult.NotFound
            }
        }
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        launch { loader.commitMove(ItemId("a"), LogicalPosition(1, 1)) }
        launch { loader.commitMove(ItemId("b"), LogicalPosition(2, 2)) }
        advanceUntilIdle()

        assertEquals(readsBefore + 1, repository.reads)
        assertEquals(
            mapOf(ItemId("a") to LogicalPosition(1, 1), ItemId("b") to LogicalPosition(0, 5)),
            positionsOf(loader),
        )
        loader.stop()
    }

    @Test
    fun commitMovePassesViewportBoundsToRepository() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var capturedColumns = -1
        var capturedRows = -1
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placement("tile-a", appA)))
        }
        repository.moveAction = { _, _, _, columns, rows ->
            capturedColumns = columns
            capturedRows = rows
            MutationResult.NotFound
        }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()

        loader.commitMove(ItemId("tile-a"), LogicalPosition(1, 1))
        advanceUntilIdle()

        assertEquals(6, capturedColumns)
        assertEquals(4096, capturedRows)
        loader.stop()
    }

    // ---- commitResize ----

    @Test
    fun commitResizeAppliesTheAcceptedGridWithoutReloading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resizeResult = accepted(cell("tile-a", 0, 0, 2, 2))
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placement("tile-a", appA)))
        }
        repository.resizeAction = { _, _, _, _, _ -> resizeResult }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        assertTrue(loader.state.value is HomeRenderState.Loaded)
        val readsBefore = repository.reads

        val result = loader.commitResize(ItemId("tile-a"), LogicalSpan(2, 2))
        advanceUntilIdle()

        assertSame(resizeResult, result)
        assertEquals(1, repository.resizeCalls.size)
        assertEquals(requestedHome, repository.resizeCalls[0].homeId)
        assertEquals(ItemId("tile-a"), repository.resizeCalls[0].itemId)
        assertEquals(LogicalSpan(2, 2), repository.resizeCalls[0].span)
        assertEquals("An accepted resize must not need a reload", readsBefore, repository.reads)
        val tile = (loader.state.value as HomeRenderState.Loaded).tiles.single()
        assertEquals(LogicalSpan(2, 2), tile.span)
        loader.stop()
    }

    @Test
    fun commitResizeReloadsOnRejected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rejected = MutationResult.Rejected(
            me.foxtails.lakehead.core.grid.GridRules.Result.Rejected(
                me.foxtails.lakehead.core.grid.GridRules.Rejection.COLLISION
            )
        )
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placement("tile-a", appA)))
        }
        repository.resizeAction = { _, _, _, _, _ -> rejected }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        assertTrue(loader.state.value is HomeRenderState.Loaded)
        val readsBefore = repository.reads

        val result = loader.commitResize(ItemId("tile-a"), LogicalSpan(2, 2))
        advanceUntilIdle()

        assertSame(rejected, result)
        assertEquals(1, repository.resizeCalls.size)
        assertEquals("A rejected resize reloads to undo the optimistic span", readsBefore + 1, repository.reads)
        assertEquals(LogicalSpan(1, 1), (loader.state.value as HomeRenderState.Loaded).tiles.single().span)
        loader.stop()
    }

    @Test
    fun commitResizePassesViewportBoundsToRepository() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var capturedColumns = -1
        var capturedRows = -1
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placement("tile-a", appA)))
        }
        repository.resizeAction = { _, _, _, columns, rows ->
            capturedColumns = columns
            capturedRows = rows
            MutationResult.NotFound
        }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()

        loader.commitResize(ItemId("tile-a"), LogicalSpan(2, 2))
        advanceUntilIdle()

        assertEquals(6, capturedColumns)
        assertEquals(4096, capturedRows)
        loader.stop()
    }

    // ---- commitRemove ----

    @Test
    fun commitRemoveDispatchesToIOAndReloadsOnRemoved() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var loadCount = 0
        val repository = FakeRepository { _, _, _ ->
            loadCount++
            if (loadCount == 1) {
                LoadResult.Loaded(listOf(placement("tile-a", appA)))
            } else {
                LoadResult.Loaded(emptyList())
            }
        }
        repository.removeAction = { _, _ -> RemoveResult.Removed }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        assertTrue(loader.state.value is HomeRenderState.Loaded)
        assertEquals(1, (loader.state.value as HomeRenderState.Loaded).tiles.size)
        val readsBefore = repository.reads

        val result = loader.commitRemove(ItemId("tile-a"))
        advanceUntilIdle()

        assertTrue(result is RemoveResult.Removed)
        assertEquals(1, repository.removeCalls.size)
        assertEquals(requestedHome, repository.removeCalls[0].homeId)
        assertEquals(ItemId("tile-a"), repository.removeCalls[0].itemId)
        assertTrue("Expected reload after removed", repository.reads > readsBefore)
        assertTrue(loader.state.value is HomeRenderState.Loaded)
        assertEquals(0, (loader.state.value as HomeRenderState.Loaded).tiles.size)
        loader.stop()
    }

    @Test
    fun removeWhileAMoveIsInFlightDoesNotReloadAndTheAcceptedGridDropsTheRemovedTile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persisted = listOf(placementAt("a", appA, 0, 0), placementAt("b", appB, 0, 5))
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(persisted) }
        repository.moveAction = { _, _, _, _, _ -> accepted(cell("a", 1, 1)) }
        repository.removeAction = { _, _ -> RemoveResult.Removed }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        // The remove writes first; the move is already in flight when it completes.
        launch { loader.commitRemove(ItemId("b")) }
        launch { loader.commitMove(ItemId("a"), LogicalPosition(1, 1)) }
        advanceUntilIdle()

        assertEquals(mapOf(ItemId("a") to LogicalPosition(1, 1)), positionsOf(loader))
        assertEquals(readsBefore, repository.reads)
        loader.stop()
    }

    @Test
    fun commitRemoveDoesNotReloadOnNotFound() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placement("tile-a", appA)))
        }
        repository.removeAction = { _, _ -> RemoveResult.NotFound }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        assertTrue(loader.state.value is HomeRenderState.Loaded)
        val readsBefore = repository.reads

        val result = loader.commitRemove(ItemId("tile-a"))
        advanceUntilIdle()

        assertTrue(result is RemoveResult.NotFound)
        assertEquals(1, repository.removeCalls.size)
        assertEquals("NotFound remove must not trigger reload", readsBefore, repository.reads)
        loader.stop()
    }

    @Test
    fun commitRemoveDoesNotReloadOnWrongHome() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placement("tile-a", appA)))
        }
        repository.removeAction = { _, _ -> RemoveResult.WrongHome }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        val readsBefore = repository.reads

        val result = loader.commitRemove(ItemId("tile-a"))
        advanceUntilIdle()

        assertTrue(result is RemoveResult.WrongHome)
        assertEquals("WrongHome remove must not trigger reload", readsBefore, repository.reads)
        loader.stop()
    }

    @Test
    fun reloadDistantValidRemainsAndContributesToExtentWithoutPositionChange() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val near = placement("near", appA).copy(position = LogicalPosition(0, 0))
        val far = AppPlacement(
            requestedHome, ItemId("far"), appB, LogicalPosition(0, 16), LogicalSpan(1, 1),
            ItemAvailability.AVAILABLE,
        )
        var second = false
        val repository = FakeRepository { _, _, _ ->
            if (!second) LoadResult.Loaded(listOf(near)) else LoadResult.Loaded(listOf(near, far))
        }
        val loader = loader(repository, dispatcher)
        loader.start(this)
        advanceUntilIdle()
        val first = loader.state.value as HomeRenderState.Loaded
        assertEquals(listOf(ItemId("near")), first.tiles.map { it.itemId })
        assertEquals(1L, first.extent.occupiedMaxRowExclusive)

        second = true
        loader.reload()
        advanceUntilIdle()
        val reloaded = loader.state.value as HomeRenderState.Loaded
        // Distant valid tile remains, positions unchanged, extent contributes.
        assertEquals(listOf(ItemId("near"), ItemId("far")), reloaded.tiles.map { it.itemId })
        assertEquals(LogicalPosition(0, 0), reloaded.tiles[0].position)
        assertEquals(LogicalPosition(0, 16), reloaded.tiles[1].position)
        assertEquals(17L, reloaded.extent.occupiedMaxRowExclusive)
        assertEquals(17L, reloaded.extent.totalScrollableRows)
        loader.stop()
    }

    @Test
    fun decodedIconSnapshotIsImmutableAndUsesInjectedDecoder() {
        val imageA = "decoded-image"
        var decoded = 0
        val cache = DecodedIconCache<String> {
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
        assertEquals(listOf(appA, appB), first.keys.toList())
        assertSame(first, second)
        assertTrue(first[appB] === imageA)
        try {
            (first as MutableMap<*, *>).clear()
            throw AssertionError("snapshot must be immutable")
        } catch (_: UnsupportedOperationException) { }
    }

    @Test
    fun decodedCacheDecodesOnlyNewOrChangedSnapshotsAndPublishesOnlyOnChange() {
        var decodeCount = 0
        val cache = DecodedIconCache<Any> { png ->
            decodeCount++
            Any().also { _ -> png.size }
        }
        val snapshotA = AppIconSnapshot(appA, byteArrayOf(1))
        val snapshotB = AppIconSnapshot(appB, byteArrayOf(2))
        val first = cache.replace(linkedMapOf(appA to snapshotA, appB to snapshotB))
        assertEquals(2, decodeCount)

        val unchanged = cache.replace(linkedMapOf(appA to snapshotA, appB to snapshotB))
        assertEquals("Unchanged snapshots are not decoded again", 2, decodeCount)
        assertSame("Nothing changed, so nothing is republished", first, unchanged)

        val changedB = AppIconSnapshot(appB, byteArrayOf(3))
        val updated = cache.replace(linkedMapOf(appA to snapshotA, appB to changedB))
        assertEquals("Only the changed snapshot is decoded", 3, decodeCount)
        assertSame("Unchanged icon keeps the same image instance", first[appA], updated[appA])
        assertNotSame(first[appB], updated[appB])

        val removed = cache.replace(linkedMapOf(appA to snapshotA))
        assertEquals(3, decodeCount)
        assertEquals(listOf(appA), removed.keys.toList())
    }

    @Test
    fun decodedCacheKeepsEntriesWhenGivenAnEmptyCatalogue() {
        val cache = DecodedIconCache<String> { "decoded" }
        val first = cache.replace(linkedMapOf(appA to AppIconSnapshot(appA, byteArrayOf(1))))

        assertSame(first, cache.replace(emptyMap()))
    }

    @Test
    fun decodedCachePreservesLastGoodWhenAllNewDecodesFail() {
        var decodeCount = 0
        val cache = DecodedIconCache<String> { png ->
            decodeCount++
            if (png[0] == 0.toByte()) null else "decoded-${png[0]}"
        }
        // First replace with valid entries
        val first = cache.replace(linkedMapOf(
            appA to AppIconSnapshot(appA, byteArrayOf(1)),
            appB to AppIconSnapshot(appB, byteArrayOf(2)),
        ))
        assertEquals(2, first.size)
        assertEquals("decoded-1", first[appA])
        assertEquals("decoded-2", first[appB])
        val savedDecodeCount = decodeCount

        // Now replace with entries that all fail to decode (byte 0 → null from decoder)
        val second = cache.replace(linkedMapOf(
            appA to AppIconSnapshot(appA, byteArrayOf(0)),
            appB to AppIconSnapshot(appB, byteArrayOf(0)),
        ))
        // Last-good should be preserved
        assertEquals("Last-good preserved when all new decodes fail", 2, second.size)
        assertEquals("decoded-1", second[appA])
        assertEquals("decoded-2", second[appB])
        assertEquals("Decoder was called for the failing entries", savedDecodeCount + 2, decodeCount)
    }

    @Test
    fun homeAndAllAppsLoadersSharingOneCacheResolveTheSameIcons() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val entries = (0 until 100).map {
            LauncherAppEntry(AppIdentity(profileA, ComponentIdentity("com.example", "App$it")), "App $it")
        }
        val catalogue = FakeCatalogue(entries)
        val repository = FakeRepository { _, _, _ ->
            LoadResult.Loaded(listOf(placement("one", entries[3].identity)))
        }
        val encodedIcons = AppIconSnapshotCache()
        val decodedIcons = DecodedIconCache<ImageBitmap> { TestImageBitmap }
        val refresher = LauncherAppsIconSnapshotRefresher(
            LauncherAppIconLookup { TestLauncherIcon(it) },
            LauncherAppIconPngEncoder { byteArrayOf(1) },
            encodedIcons,
            dispatcher,
        )
        val home = HomeRouteLoader(
            homeId = requestedHome,
            repository = repository,
            catalogue = catalogue,
            iconRefresher = refresher,
            encodedIcons = encodedIcons,
            decodedIcons = decodedIcons,
            viewport = viewport,
            ioDispatcher = dispatcher,
        )
        val allApps = AllAppsRouteLoader(
            homeId = requestedHome,
            repository = repository,
            catalogue = catalogue,
            iconRefresher = refresher,
            encodedIcons = encodedIcons,
            decodedIcons = decodedIcons,
            ioDispatcher = dispatcher,
        )

        home.start(this)
        allApps.start(this)
        advanceUntilIdle()

        val identities = entries.map { it.identity }.toSet()
        assertEquals(identities, home.icons.value.keys)
        assertEquals(home.icons.value, allApps.icons.value)
        assertEquals(identities, encodedIcons.snapshot().keys)
        home.stop()
        allApps.stop()
    }

    @Test
    fun allIconLookupsReturningNullPreservesLastGoodDecodedIcons() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var shouldReturnNull = false
        val testIconLookup = LauncherAppIconLookup { identity ->
            if (shouldReturnNull) null
            else TestLauncherIcon(identity)
        }
        val testEncoder = LauncherAppIconPngEncoder { icon ->
            (icon as TestLauncherIcon).identity.profileId.value.encodeToByteArray()
        }
        val testDecoder: (ByteArray) -> ImageBitmap? = { _ -> TestImageBitmap }
        val repository = FakeRepository { _, _, _ -> LoadResult.Loaded(listOf(placement("work", appB))) }
        val catalogue = FakeCatalogue(listOf(
            LauncherAppEntry(appA, "Personal"),
            LauncherAppEntry(appB, "Work"),
        ))
        val loader = loader(repository, dispatcher, catalogue, testIconLookup, testEncoder, testDecoder)

        loader.start(this)
        advanceUntilIdle()

        // Verify initial icons loaded
        assertEquals(setOf(appA, appB), loader.icons.value.keys)
        assertSame(TestImageBitmap, loader.icons.value[appB])

        // Now make all lookups return null (simulating a transient platform failure)
        shouldReturnNull = true
        catalogue.mutable.value = listOf(
            LauncherAppEntry(appA, "Personal Updated"),
            LauncherAppEntry(appB, "Work Updated"),
        )
        advanceUntilIdle()

        // Last-good icons should be preserved — not wiped to empty
        assertEquals("Icons should be preserved when all lookups return null", setOf(appA, appB), loader.icons.value.keys)
        assertSame(TestImageBitmap, loader.icons.value[appB])
        loader.stop()
    }

    private fun loader(
        repository: HomePlacementRepository,
        dispatcher: CoroutineDispatcher,
        catalogue: FakeCatalogue = FakeCatalogue(emptyList()),
        iconLookup: LauncherAppIconLookup = LauncherAppIconLookup { null },
        pngEncoder: LauncherAppIconPngEncoder = LauncherAppIconPngEncoder { null },
        decoder: (ByteArray) -> ImageBitmap? = { null },
    ): HomeRouteLoader {
        val encodedIcons = AppIconSnapshotCache()
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
            decodedIcons = DecodedIconCache(decoder),
            viewport = viewport,
            ioDispatcher = dispatcher,
        )
    }

    private fun placement(
        item: String,
        identity: AppIdentity,
        availability: ItemAvailability = ItemAvailability.AVAILABLE,
    ) = AppPlacement(requestedHome, ItemId(item), identity, LogicalPosition(0, 0), LogicalSpan(1, 1), availability)

    private fun placementAt(item: String, identity: AppIdentity, column: Int, row: Int) =
        placement(item, identity).copy(position = LogicalPosition(column, row))

    private fun cell(item: String, column: Int, row: Int, columns: Int = 1, rows: Int = 1) =
        me.foxtails.lakehead.core.grid.GridPlacement(
            ItemId(item),
            me.foxtails.lakehead.core.grid.GridPosition(column, row),
            me.foxtails.lakehead.core.grid.GridSpan(columns, rows),
        )

    private fun accepted(vararg cells: me.foxtails.lakehead.core.grid.GridPlacement<ItemId>) =
        MutationResult.Accepted(me.foxtails.lakehead.core.grid.GridRules.Result.Accepted(cells.toList()))

    private fun positionsOf(loader: HomeRouteLoader): Map<ItemId, LogicalPosition> =
        (loader.state.value as HomeRenderState.Loaded).tiles.associate { it.itemId to it.position }

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
        var moveCalls = mutableListOf<Triple<HomeId, ItemId, LogicalPosition>>()
        var moveAction: (HomeId, ItemId, LogicalPosition, Int, Int) -> MutationResult = { _, _, _, _, _ ->
            error("loader must not write")
        }
        data class ResizeCall(val homeId: HomeId, val itemId: ItemId, val span: LogicalSpan, val columns: Int, val rows: Int)
        var resizeCalls = mutableListOf<ResizeCall>()
        var resizeAction: (HomeId, ItemId, LogicalSpan, Int, Int) -> MutationResult = { _, _, _, _, _ ->
            error("loader must not write")
        }
        data class RemoveCall(val homeId: HomeId, val itemId: ItemId)
        var removeCalls = mutableListOf<RemoveCall>()
        var removeAction: (HomeId, ItemId) -> RemoveResult = { _, _ ->
            error("loader must not write")
        }
        override fun load(homeId: HomeId, columns: Int): LoadResult {
            reads++
            return read(homeId, columns, 4096)
        }
        override fun place(homeId: HomeId, itemId: ItemId, appIdentity: AppIdentity, position: LogicalPosition,
            span: LogicalSpan, columns: Int, replacingId: ItemId?): MutationResult {
            mutations++; error("loader must not write")
        }
        override fun placeWidget(
            homeId: me.foxtails.lakehead.core.model.HomeId,
            itemId: me.foxtails.lakehead.core.model.ItemId,
            provider: me.foxtails.lakehead.core.model.AppIdentity,
            appWidgetId: Int,
            position: me.foxtails.lakehead.core.model.LogicalPosition,
            span: me.foxtails.lakehead.core.model.LogicalSpan,
            columns: Int,
        ): MutationResult = error("unused")

        override fun persistedAppWidgetIds(): Set<Int> = emptySet()

        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int): MutationResult {
            mutations++
            moveCalls += Triple(homeId, itemId, position)
            return moveAction(homeId, itemId, position, columns, 4096)
        }
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int): MutationResult {
            mutations++
            resizeCalls += ResizeCall(homeId, itemId, span, columns, 4096)
            return resizeAction(homeId, itemId, span, columns, 4096)
        }
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult {
            mutations++
            removeCalls += RemoveCall(homeId, itemId)
            return removeAction(homeId, itemId)
        }
        override fun ensureHome(homeId: HomeId): Boolean {
            mutations++; error("loader must not write")
        }
        override fun loadBinding(tileId: ItemId): TileBinding = TileBinding(tileId)
        override fun saveBinding(binding: TileBinding) { mutations++; error("loader must not write") }
        override fun deleteBinding(tileId: ItemId) { mutations++; error("loader must not write") }
        override fun loadAlbumBinding(tileId: ItemId): TileAlbumBinding? = null
        override fun saveAlbumBinding(binding: TileAlbumBinding) { mutations++; error("loader must not write") }
        override fun deleteAlbumBinding(tileId: ItemId) { mutations++; error("loader must not write") }
    }
}
