package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.RemoveResult
import me.foxtails.lakehead.feature.home.HomeRenderTile
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1 unit tests for the selected-tile menu coordinator: scoped projection,
 * query-on-select, honest empty, live invalidation without leaving edit, and
 * profile-exact execution and B2 pin mutation routing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutMenuCoordinatorTest {
    private val personal = ProfileId("personal")
    private val work = ProfileId("work")
    private val component = ComponentIdentity("com.example", "Main")
    private val identityA = AppIdentity(personal, component)
    private val identityB = AppIdentity(work, component)
    private val tileA = ItemId("tile-a")
    private val tileB = ItemId("tile-b")

    private fun tile(id: ItemId, identity: AppIdentity) = HomeRenderTile(
        itemId = id,
        appIdentity = identity,
        position = LogicalPosition(0, 0),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
        label = id.value,
        icon = null,
        isLaunchable = true,
    )

    private fun shortcut(identity: AppIdentity, id: String, rank: Int) =
        LauncherShortcut(
            ShortcutRef(identity.component.packageName, identity.profileId, id),
            "Label $id",
            rank,
        )

    @Test
    fun selectionLoadsScopedEntriesAndRefreshesOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            // Rank-sorted input: the gateway contract guarantees rank order and
            // the loader/coordinator preserve it (ordering itself is covered by
            // ShortcutSurfaceLoaderTest/ShortcutQueryMapperTest).
            mapOf(identityA to listOf(shortcut(identityA, "s1", 0), shortcut(identityA, "s2", 1))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()

        val menu = coordinator.menu.value
        assertEquals(tileA, menu?.tileId)
        // Gateway rank order preserved (rank 0 first despite map order).
        assertEquals(listOf("s1", "s2"), menu?.entries?.map { it.id })
        assertEquals(listOf(identityA), gateway.refreshed)
        coordinator.stop()
    }

    @Test
    fun folderTilesAreIgnoredAndDoNotBreakAppTiles() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "s1", 0))))
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        val folder = HomeRenderTile(
            itemId = ItemId("folder"),
            appIdentity = null,
            position = LogicalPosition(1, 0),
            span = LogicalSpan(1, 1),
            availability = ItemAvailability.AVAILABLE,
            label = "Folder",
            icon = null,
            isLaunchable = false,
            folderPreview = emptyList(),
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), folder))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()

        assertEquals(tileA, coordinator.menu.value?.tileId)
        coordinator.stop()
    }

    @Test
    fun switchClearsStaleImmediatelyThenLoadsNewOwner() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            mapOf(
                identityA to listOf(shortcut(identityA, "a1", 0)),
                identityB to listOf(shortcut(identityB, "b1", 0)),
            ),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), tile(tileB, identityB)))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()
        assertEquals(tileA, coordinator.menu.value?.tileId)

        coordinator.onSelectionChanged(tileB)
        // Honest empty synchronously: A's rows never render on B.
        assertNull(coordinator.menu.value)
        advanceUntilIdle()

        val menu = coordinator.menu.value
        assertEquals(tileB, menu?.tileId)
        assertEquals(listOf("b1"), menu?.entries?.map { it.id })
        assertEquals(listOf(identityA, identityB), gateway.refreshed)
        coordinator.stop()
    }

    @Test
    fun zeroShortcutsYieldsNoMenuButStillRefreshes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(emptyMap())
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()

        assertNull(coordinator.menu.value)
        assertEquals(listOf(identityA), gateway.refreshed)
        coordinator.stop()
    }

    @Test
    fun unknownSelectionYieldsNoMenuAndNoRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(emptyMap())
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        coordinator.onSelectionChanged(ItemId("gone"))
        advanceUntilIdle()

        assertNull(coordinator.menu.value)
        assertTrue(gateway.refreshed.isEmpty())
        coordinator.stop()
    }

    @Test
    fun missingTileClearsMenuWithoutLeavingEdit() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "a1", 0))))
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), tile(tileB, identityB)))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()
        assertEquals(tileA, coordinator.menu.value?.tileId)

        // Removal/uninstall: the selected tile leaves the loaded tiles while
        // the selection itself is untouched (edit not exited by this layer).
        coordinator.onHomeTilesChanged(listOf(tile(tileB, identityB)))
        advanceUntilIdle()
        assertNull(coordinator.menu.value)
        coordinator.stop()
    }

    @Test
    fun livePublishRefreshesRowsWithoutReselectionOrRequery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "a1", 0))))
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()
        assertEquals(listOf("a1"), coordinator.menu.value?.entries?.map { it.id })

        gateway.publish(mapOf(identityA to listOf(shortcut(identityA, "a1", 0), shortcut(identityA, "a2", 1))))
        advanceUntilIdle()

        val menu = coordinator.menu.value
        assertEquals(tileA, menu?.tileId)
        assertEquals(listOf("a1", "a2"), menu?.entries?.map { it.id })
        // The reload never re-fires refresh: exactly one query-on-select.
        assertEquals(listOf(identityA), gateway.refreshed)
        coordinator.stop()
    }

    @Test
    fun executeResolvesExactProfileRef() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            mapOf(
                identityA to listOf(shortcut(identityA, "s", 0)),
                identityB to listOf(shortcut(identityB, "s", 0)),
            ),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), tile(tileB, identityB)))

        assertTrue(coordinator.execute(tileB, "s"))
        assertEquals(
            listOf(ShortcutRef(component.packageName, work, "s")),
            gateway.launched,
        )
        coordinator.stop()
    }

    @Test
    fun executeUnknownTileOrEntryReturnsFalseWithoutLaunch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "s", 0))))
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))

        assertFalse(coordinator.execute(ItemId("gone"), "s"))
        assertFalse(coordinator.execute(tileA, "missing"))
        assertTrue(gateway.launched.isEmpty())
        coordinator.stop()
    }

    @Test
    fun togglePinPersistsThroughCoordinatorAndDoesNotLaunch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "s", 0))))
        val repository = FakeBindingRepository(emptyMap())
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA).copy(span = LogicalSpan(2, 1))))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()

        coordinator.togglePin(tileA, "s")
        advanceUntilIdle()

        assertEquals(listOf("s"), repository.binding(tileA).pinnedShortcuts.map { it.shortcutId })
        assertTrue(gateway.launched.isEmpty())
        coordinator.stop()
    }

    @Test
    fun pinnedProjectionUsesBindingOrderAndIsEmptyWithoutPins() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = shortcut(identityA, "first", 0)
        val second = shortcut(identityA, "second", 1)
        val repository = FakeBindingRepository(
            mapOf(tileA to TileBinding(tileA, listOf(second.ref, first.ref))),
        )
        val gateway = FakeGateway(mapOf(identityA to listOf(first, second)))
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), tile(tileB, identityB)))
        advanceUntilIdle()

        assertEquals(listOf("second", "first"), coordinator.pinnedByTile.value[tileA]?.map { it.id })
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileB))
        coordinator.stop()
    }

    @Test
    fun persistedPinsRefreshMetadataOnColdStart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pinned = shortcut(identityA, "pinned", 0)
        val gateway = FakeGateway(
            emptyMap(),
            refreshResults = mapOf(identityA to listOf(pinned)),
        )
        val repository = FakeBindingRepository(
            mapOf(tileA to TileBinding(tileA, listOf(pinned.ref))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        advanceUntilIdle()

        assertEquals(listOf(identityA), gateway.refreshed)
        assertEquals(listOf("pinned"), coordinator.pinnedByTile.value[tileA]?.map { it.id })
        coordinator.stop()
    }

    @Test
    fun restartRefreshesPersistedPinsBeforeReprojectingRetainedTiles() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stale = shortcut(identityA, "same", 0).copy(label = "Old label")
        val fresh = shortcut(identityA, "same", 0).copy(label = "Fresh label")
        val gateway = FakeGateway(
            mapOf(identityA to listOf(stale)),
            refreshResults = mapOf(identityA to listOf(fresh)),
        )
        val repository = FakeBindingRepository(
            // The stale cached row is deliberately still bound. If the
            // coordinator projects independently of the refresh, this would
            // publish the old label before A's refresh completes.
            mapOf(tileA to TileBinding(tileA, listOf(stale.ref))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        advanceUntilIdle()

        coordinator.stop()
        gateway.publish(mapOf(identityA to listOf(stale)))
        coordinator.start(this)
        advanceUntilIdle()

        assertEquals(listOf("Fresh label"), coordinator.pinnedByTile.value[tileA]?.map { it.label })
        assertEquals(listOf(identityA, identityA), gateway.refreshed)
        coordinator.stop()
    }

    @Test
    fun restartDoesNotPublishRetainedPinsFromStaleSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stale = shortcut(identityA, "same", 0).copy(label = "Old label")
        val fresh = shortcut(identityA, "same", 0).copy(label = "Fresh label")
        val gateway = FakeGateway(
            mapOf(identityA to listOf(stale)),
            refreshResults = mapOf(identityA to listOf(fresh)),
            autoPublishRefreshResults = false,
        )
        val repository = FakeBindingRepository(
            mapOf(tileA to TileBinding(tileA, listOf(stale.ref))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )

        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        runCurrent()

        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))

        // Publishing another identity is not evidence that A's refresh ran.
        gateway.publish(mapOf(identityA to listOf(stale), identityB to listOf(shortcut(identityB, "b", 0))))
        runCurrent()
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))

        gateway.completeRefresh(identityA)
        runCurrent()

        assertEquals(listOf("Fresh label"), coordinator.pinnedByTile.value[tileA]?.map { it.label })
        coordinator.stop()
    }

    @Test
    fun pinToggleDoesNotCancelRestartHoldAndFreshProjectionRecovers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stale = shortcut(identityA, "same", 0).copy(label = "Old label")
        val newlyPinned = shortcut(identityA, "new", 1)
        val fresh = stale.copy(label = "Fresh label")
        val gateway = FakeGateway(
            mapOf(identityA to listOf(stale, newlyPinned)),
            refreshResults = mapOf(identityA to listOf(fresh, newlyPinned)),
            autoPublishRefreshResults = false,
        )
        val repository = FakeBindingRepository(
            mapOf(tileA to TileBinding(tileA, listOf(stale.ref))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )

        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA).copy(span = LogicalSpan(4, 2))))
        runCurrent()
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))

        // The ordinary metadata refresh caused by this successful toggle must
        // not cancel the unresolved restart refresh/fallback that owns the hold.
        coordinator.togglePin(tileA, "new")
        runCurrent()
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))

        gateway.completeRefresh(identityA)
        runCurrent()

        assertEquals(listOf("Fresh label", "Label new"), coordinator.pinnedByTile.value[tileA]?.map { it.label })
        coordinator.stop()
    }

    @Test
    fun supersededRefreshResultAfterNewBaselineStaysBlockedUntilCurrentOrFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stale = shortcut(identityA, "same", 0).copy(label = "Old label")
        val gateway = FakeGateway(
            mapOf(identityA to listOf(stale)),
            refreshResults = mapOf(identityA to listOf(stale)),
            autoPublishRefreshResults = false,
        )
        val repository = FakeBindingRepository(
            mapOf(tileA to TileBinding(tileA, listOf(stale.ref))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        runCurrent()

        // A new restart request establishes a newer baseline. A publication
        // from the superseded A request must not release that request's hold.
        // The snapshot must actually change (B gains rows while A stays
        // stale) so StateFlow emits and the coordinator has a publication
        // it is forced to ignore; re-publishing the identical map would be
        // deduped and prove nothing.
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        runCurrent()
        gateway.publish(mapOf(identityA to listOf(stale), identityB to listOf(shortcut(identityB, "b", 0))))
        runCurrent()
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("Old label"), coordinator.pinnedByTile.value[tileA]?.map { it.label })
        coordinator.stop()
    }

    @Test
    fun restartFallbackReleasesStaleProjectionAndLateResultDoesNotReblock() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stale = shortcut(identityA, "same", 0).copy(label = "Old label")
        val fresh = shortcut(identityA, "same", 0).copy(label = "Fresh label")
        val gateway = FakeGateway(
            mapOf(identityA to listOf(stale)),
            refreshResults = mapOf(identityA to listOf(fresh)),
            autoPublishRefreshResults = false,
        )
        val repository = FakeBindingRepository(
            mapOf(tileA to TileBinding(tileA, listOf(stale.ref))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        runCurrent()
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("Old label"), coordinator.pinnedByTile.value[tileA]?.map { it.label })

        // The timed-out refresh cannot re-enter the initial hold when its
        // result arrives late; it is an ordinary live update now.
        gateway.completeRefresh(identityA)
        runCurrent()
        assertEquals(listOf("Fresh label"), coordinator.pinnedByTile.value[tileA]?.map { it.label })
        coordinator.stop()
    }

    @Test
    fun unrelatedShortcutUpdateDoesNotReReadEveryBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pinned = shortcut(identityA, "pinned", 0)
        val gateway = FakeGateway(
            mapOf(identityA to listOf(pinned), identityB to emptyList()),
        )
        val repository = FakeBindingRepository(
            mapOf(tileA to TileBinding(tileA, listOf(pinned.ref))),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), tile(tileB, identityB)))
        advanceUntilIdle()
        val loadsBeforeUnrelatedUpdate = repository.bindingLoads

        gateway.publish(mapOf(identityA to listOf(pinned), identityB to listOf(shortcut(identityB, "new", 0))))
        advanceUntilIdle()

        assertEquals(loadsBeforeUnrelatedUpdate, repository.bindingLoads)
        coordinator.stop()
    }

    @Test
    fun pinAndUnpinUpdatesTileProjection() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "s", 0))))
        val repository = FakeBindingRepository(emptyMap())
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA).copy(span = LogicalSpan(2, 1))))
        advanceUntilIdle()
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))

        coordinator.togglePin(tileA, "s")
        advanceUntilIdle()
        assertEquals(listOf("s"), coordinator.pinnedByTile.value[tileA]?.map { it.id })

        coordinator.togglePin(tileA, "s")
        advanceUntilIdle()
        assertFalse(coordinator.pinnedByTile.value.containsKey(tileA))
        coordinator.stop()
    }

    @Test
    fun rapidSwitchDropsStaleReload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            mapOf(
                identityA to listOf(shortcut(identityA, "a1", 0)),
                identityB to listOf(shortcut(identityB, "b1", 0)),
            ),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), tile(tileB, identityB)))
        coordinator.onSelectionChanged(tileA)
        coordinator.onSelectionChanged(tileB)
        advanceUntilIdle()

        assertEquals(tileB, coordinator.menu.value?.tileId)
        assertEquals(listOf("b1"), coordinator.menu.value?.entries?.map { it.id })
        coordinator.stop()
    }

    @Test
    fun boundTileWithoutShortcutsShowsAlbumActionRows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(emptyMap())
        val repository = FakeBindingRepository(
            emptyMap(),
            albumBindings = mapOf(
                tileA to TileAlbumBinding(
                    tileA,
                    AlbumRef("b1", "Camera"),
                ),
            ),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()

        val menu = coordinator.menu.value
        assertEquals(tileA, menu?.tileId)
        assertEquals(listOf(ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID), menu?.entries?.map { it.id })
        assertTrue(menu?.entries?.all { it.isAction } == true)
        coordinator.stop()
    }

    @Test
    fun refreshSelectionDropsAlbumRowsAfterBindingRemoved() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "s1", 0))))
        val repository = FakeBindingRepository(
            emptyMap(),
            albumBindings = mapOf(
                tileA to TileAlbumBinding(
                    tileA,
                    AlbumRef("b1", "Camera"),
                ),
            ),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, repository, dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))
        coordinator.onSelectionChanged(tileA)
        advanceUntilIdle()
        assertEquals(
            listOf("s1", ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID),
            coordinator.menu.value?.entries?.map { it.id },
        )

        // The edit-menu Remove path clears the binding with no tile or
        // selection transition; the refresh drops the action rows in place.
        repository.setAlbumBinding(tileA, null)
        coordinator.refreshSelection()
        advanceUntilIdle()

        assertEquals(tileA, coordinator.menu.value?.tileId)
        assertEquals(listOf("s1"), coordinator.menu.value?.entries?.map { it.id })
        coordinator.stop()
    }

    @Test
    fun executeAlbumRowIdReturnsFalseWithoutLaunch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identityA to listOf(shortcut(identityA, "s", 0))))
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher),
            dispatcher,
        )
        coordinator.start(this)
        coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA)))

        assertFalse(coordinator.execute(tileA, ALBUM_MENU_CHANGE_ID))
        assertFalse(coordinator.execute(tileA, ALBUM_MENU_REMOVE_ID))
        assertTrue(gateway.launched.isEmpty())
        coordinator.stop()
    }

    private class FakeGateway(
        shortcuts: Map<AppIdentity, List<LauncherShortcut>>,
        private val refreshResults: Map<AppIdentity, List<LauncherShortcut>> = emptyMap(),
        private val autoPublishRefreshResults: Boolean = true,
    ) : ShortcutGateway {
        private val mutable = MutableStateFlow(shortcuts)
        override val shortcuts: StateFlow<Map<AppIdentity, List<LauncherShortcut>>> = mutable
        val refreshed = mutableListOf<AppIdentity>()
        val launched = mutableListOf<ShortcutRef>()
        private val refreshCompletions = mutableMapOf<AppIdentity, CompletableDeferred<Unit>>()

        fun publish(next: Map<AppIdentity, List<LauncherShortcut>>) {
            mutable.value = next
        }

        fun completeRefresh(identity: AppIdentity) {
            refreshResults[identity]?.let { mutable.value = mutable.value + (identity to it) }
            refreshCompletions.remove(identity)?.complete(Unit)
        }

        override fun shortcutsFor(identity: AppIdentity): List<LauncherShortcut> =
            mutable.value[identity].orEmpty()

        override fun refresh(identity: AppIdentity) {
            refreshed += identity
            if (autoPublishRefreshResults) completeRefresh(identity)
        }

        override suspend fun refreshAndAwait(identity: AppIdentity): Boolean {
            refreshed += identity
            if (autoPublishRefreshResults) {
                completeRefresh(identity)
                return true
            }
            val completion = CompletableDeferred<Unit>()
            refreshCompletions[identity] = completion
            completion.await()
            return true
        }

        override fun launch(ref: ShortcutRef): Boolean {
            launched += ref
            return true
        }

        override fun start(scope: CoroutineScope) = Unit
        override fun stop() = Unit
    }

    private class FakeBindingRepository(
        bindings: Map<ItemId, TileBinding>,
        albumBindings: Map<ItemId, TileAlbumBinding> = emptyMap(),
    ) : HomePlacementRepository {
        private val mutableBindings = bindings.toMutableMap()
        private val mutableAlbumBindings = albumBindings.toMutableMap()
        var bindingLoads = 0
            private set
        override fun load(homeId: HomeId, columns: Int): LoadResult = LoadResult.NotFound
        override fun place(
            homeId: HomeId,
            itemId: ItemId,
            appIdentity: AppIdentity,
            position: LogicalPosition,
            span: LogicalSpan,
            columns: Int,
            replacingId: ItemId?,
        ): MutationResult = error("menu coordinator must not write")
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

        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int): MutationResult =
            error("menu coordinator must not write")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int): MutationResult =
            error("menu coordinator must not write")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = error("menu coordinator must not write")
        override fun ensureHome(homeId: HomeId): Boolean = error("menu coordinator must not write")
        override fun loadBinding(tileId: ItemId): TileBinding =
            mutableBindings[tileId].also { bindingLoads++ } ?: TileBinding(tileId)
        fun binding(tileId: ItemId): TileBinding = loadBinding(tileId)
        override fun saveBinding(binding: TileBinding) {
            mutableBindings[binding.tileId] = binding
        }
        override fun deleteBinding(tileId: ItemId) = error("menu coordinator must not write")
        override fun loadAlbumBinding(tileId: ItemId): TileAlbumBinding? = mutableAlbumBindings[tileId]
        fun setAlbumBinding(tileId: ItemId, binding: TileAlbumBinding?) {
            if (binding == null) mutableAlbumBindings.remove(tileId) else mutableAlbumBindings[tileId] = binding
        }
        override fun saveAlbumBinding(binding: TileAlbumBinding) = error("menu coordinator must not write")
        override fun deleteAlbumBinding(tileId: ItemId) = error("menu coordinator must not write")
    }
}
