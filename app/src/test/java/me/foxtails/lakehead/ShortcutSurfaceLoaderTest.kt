package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
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
import me.foxtails.lakehead.platform.shortcuts.ShortcutGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutSurfaceLoaderTest {
    private val profile = ProfileId("personal")
    private val identity = AppIdentity(profile, ComponentIdentity("com.example", "Main"))
    private val tileId = ItemId("tile-a")

    private fun shortcut(id: String, label: String, rank: Int) =
        LauncherShortcut(ShortcutRef("com.example", profile, id), label, rank)

    @Test
    fun rankOrderPreservedAndPinnedFromBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            mapOf(identity to listOf(
                shortcut("a", "A-first", 0),
                shortcut("b", "B-second", 1),
            )),
        )
        val repository = FakeBindingRepository(
            mapOf(tileId to TileBinding(tileId, listOf(ShortcutRef("com.example", profile, "b")))),
        )
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        val entries = loader.entriesForTile(tileId, identity)
        assertEquals(listOf("a", "b"), entries.map { it.id })
        assertEquals(listOf("A-first", "B-second"), entries.map { it.label })
        assertEquals(listOf(false, true), entries.map { it.isPinned })
        assertTrue(entries.all { it.isPinEnabled })
    }

    @Test
    fun zeroShortcutsYieldsEmptyListWithoutReadingBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(emptyMap())
        val repository = FakeBindingRepository(emptyMap())
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        assertTrue(loader.entriesForTile(tileId, identity).isEmpty())
        assertEquals(0, repository.bindingReads)
    }

    @Test
    fun pinnedShortcutsFromOtherTilesDoNotLeak() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            mapOf(identity to listOf(shortcut("a", "A", 0))),
        )
        val repository = FakeBindingRepository(
            mapOf(ItemId("tile-other") to TileBinding(ItemId("tile-other"), listOf(
                ShortcutRef("com.example", profile, "a"),
            ))),
        )
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        val entries = loader.entriesForTile(tileId, identity)
        assertEquals(1, entries.size)
        assertFalse(entries.single().isPinned)
    }

    @Test
    fun executeDelegatesToGatewayRef() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ref = ShortcutRef("com.example", profile, "a")
        val gateway = FakeGateway(emptyMap(), launchResult = false)
        val loader = ShortcutSurfaceLoader(gateway, FakeBindingRepository(emptyMap()), dispatcher)

        assertFalse(loader.execute(ref))
        assertEquals(listOf(ref), gateway.launched)
    }

    private class FakeGateway(
        shortcuts: Map<AppIdentity, List<LauncherShortcut>>,
        private val launchResult: Boolean = true,
    ) : ShortcutGateway {
        private val mutable = MutableStateFlow(shortcuts)
        override val shortcuts: StateFlow<Map<AppIdentity, List<LauncherShortcut>>> = mutable
        val launched = mutableListOf<ShortcutRef>()
        override fun refresh(identity: AppIdentity) = Unit
        override fun launch(ref: ShortcutRef): Boolean {
            launched += ref
            return launchResult
        }
        override fun start(scope: CoroutineScope) = Unit
        override fun stop() = Unit
    }

    private class FakeBindingRepository(
        private val bindings: Map<ItemId, TileBinding>,
    ) : HomePlacementRepository {
        var bindingReads = 0
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
        ): MutationResult = error("surface loader must not write")
        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int, rows: Int): MutationResult =
            error("surface loader must not write")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int, rows: Int): MutationResult =
            error("surface loader must not write")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = error("surface loader must not write")
        override fun ensureHome(homeId: HomeId): Boolean = error("surface loader must not write")
        override fun loadBinding(tileId: ItemId): TileBinding {
            bindingReads++
            return bindings[tileId] ?: TileBinding(tileId)
        }
        override fun saveBinding(binding: TileBinding) = error("surface loader must not write")
        override fun deleteBinding(tileId: ItemId) = error("surface loader must not write")
    }
}
