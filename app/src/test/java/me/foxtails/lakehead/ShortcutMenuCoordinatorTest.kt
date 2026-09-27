package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
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
    fun rapidSwitchDropsStaleReload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // Manual loader queue: entries computation only runs when the test
        // pumps it. This lets stale A's load finish after the selection has
        // already moved to B, while B's reload (and its cancel of A's job)
        // has not run yet — so A truly reaches the publication
        // generation/selection guard with a stale selection instead of dying
        // in a cancelled withContext before the guard.
        val loaderQueue = ArrayDeque<() -> Unit>()
        val loaderDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                loaderQueue.addLast { block.run() }
            }
        }
        val gateway = FakeGateway(
            mapOf(
                identityA to listOf(shortcut(identityA, "a1", 0)),
                identityB to listOf(shortcut(identityB, "b1", 0)),
            ),
        )
        val coordinator = ShortcutMenuCoordinator(
            gateway,
            ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), loaderDispatcher),
            dispatcher,
        )
        coordinator.start(this)
        try {
            coordinator.onHomeTilesChanged(listOf(tile(tileA, identityA), tile(tileB, identityB)))
            // Start A's load and let it park inside the loader (no rows yet).
            coordinator.onSelectionChanged(tileA)
            advanceUntilIdle()
            assertNull(coordinator.menu.value)
            assertEquals(1, loaderQueue.size)

            // Finish A's load while the coordinator is idle: A's publication
            // resume queues on the test scheduler but does not run yet.
            loaderQueue.removeFirst().invoke()

            // Switch to B before A's stale resume runs. B's reload has not
            // run, so A's job is still alive when it reaches the guard.
            coordinator.onSelectionChanged(tileB)
            // Honest empty synchronously: A's rows never render on B.
            assertNull(coordinator.menu.value)

            // Run A's stale publication attempt: the guard must drop it (B's
            // reload launches here and parks behind the manual queue, so B's
            // rows cannot overwrite the evidence yet). Removing the guard
            // publishes the stale A menu here and fails this assertion.
            testScheduler.runCurrent()
            assertNull(coordinator.menu.value)
            assertEquals(1, loaderQueue.size)

            // Let B load and publish; B wins.
            loaderQueue.removeFirst().invoke()
            advanceUntilIdle()

            val menu = coordinator.menu.value
            assertEquals(tileB, menu?.tileId)
            assertEquals(listOf("b1"), menu?.entries?.map { it.id })
        } finally {
            // Drain any load still parked in the manual queue before stopping:
            // otherwise a job suspended there (e.g. B's load when an
            // assertion above fails) would never observe teardown
            // cancellation, which parks on an un-pumped custom dispatcher and
            // hangs runTest instead of reporting the failure.
            while (loaderQueue.isNotEmpty()) loaderQueue.removeFirst().invoke()
            coordinator.stop()
        }
    }

    private class FakeGateway(
        shortcuts: Map<AppIdentity, List<LauncherShortcut>>,
    ) : ShortcutGateway {
        private val mutable = MutableStateFlow(shortcuts)
        override val shortcuts: StateFlow<Map<AppIdentity, List<LauncherShortcut>>> = mutable
        val refreshed = mutableListOf<AppIdentity>()
        val launched = mutableListOf<ShortcutRef>()

        fun publish(next: Map<AppIdentity, List<LauncherShortcut>>) {
            mutable.value = next
        }

        override fun shortcutsFor(identity: AppIdentity): List<LauncherShortcut> =
            mutable.value[identity].orEmpty()

        override fun refresh(identity: AppIdentity) {
            refreshed += identity
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
    ) : HomePlacementRepository {
        private val mutableBindings = bindings.toMutableMap()
        override fun load(homeId: HomeId, columns: Int, rows: Int): LoadResult = LoadResult.NotFound
        override fun place(
            homeId: HomeId,
            itemId: ItemId,
            appIdentity: AppIdentity,
            position: LogicalPosition,
            span: LogicalSpan,
            columns: Int,
            rows: Int,
            replacingId: ItemId?,
        ): MutationResult = error("menu coordinator must not write")
        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int, rows: Int): MutationResult =
            error("menu coordinator must not write")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int, rows: Int): MutationResult =
            error("menu coordinator must not write")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = error("menu coordinator must not write")
        override fun ensureHome(homeId: HomeId): Boolean = error("menu coordinator must not write")
        override fun loadBinding(tileId: ItemId): TileBinding =
            mutableBindings[tileId] ?: TileBinding(tileId)
        fun binding(tileId: ItemId): TileBinding = loadBinding(tileId)
        override fun saveBinding(binding: TileBinding) {
            mutableBindings[binding.tileId] = binding
        }
        override fun deleteBinding(tileId: ItemId) = error("menu coordinator must not write")
    }
}
