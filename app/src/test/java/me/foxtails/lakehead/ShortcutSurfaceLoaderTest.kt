package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.awaitAll
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
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.TileAlbumBinding
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
    fun zeroShortcutsWithoutAlbumBindingYieldsEmptyWithoutReadingShortcutBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(emptyMap())
        val repository = FakeBindingRepository(emptyMap())
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        assertTrue(loader.entriesForTile(tileId, identity).isEmpty())
        assertEquals(0, repository.bindingReads)
        assertEquals(1, repository.albumReads)
    }

    @Test
    fun boundTileWithZeroShortcutsYieldsOnlyAlbumRows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(emptyMap())
        val repository = FakeBindingRepository(
            emptyMap(),
            albumBindings = mapOf(tileId to TileAlbumBinding(tileId, AlbumRef("b1", "Camera"))),
        )
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        val entries = loader.entriesForTile(tileId, identity)
        assertEquals(listOf(ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID), entries.map { it.id })
        assertEquals(listOf("Change album", "Remove album"), entries.map { it.label })
        assertTrue(entries.all { it.isAction })
        // The shortcut binding is still skipped when there are no shortcuts.
        assertEquals(0, repository.bindingReads)
    }

    @Test
    fun boundTileWithShortcutsAppendsAlbumRowsAfter() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            mapOf(identity to listOf(shortcut("a", "A", 0), shortcut("b", "B", 1))),
        )
        val repository = FakeBindingRepository(
            emptyMap(),
            albumBindings = mapOf(tileId to TileAlbumBinding(tileId, AlbumRef("b1", "Camera"))),
        )
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        val entries = loader.entriesForTile(tileId, identity, LogicalSpan(2, 1))
        assertEquals(listOf("a", "b", ALBUM_MENU_CHANGE_ID, ALBUM_MENU_REMOVE_ID), entries.map { it.id })
        assertEquals(listOf(false, false, true, true), entries.map { it.isAction })
    }

    @Test
    fun togglePinForAlbumRowIdReturnsFalseWithoutWrite() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identity to listOf(shortcut("a", "A", 0))))
        val repository = FakeBindingRepository(emptyMap())
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        assertFalse(loader.togglePin(tileId, identity, LogicalSpan(2, 1), ALBUM_MENU_REMOVE_ID))
        assertEquals(0, repository.saveCount)
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

    @Test
    fun concurrentTogglesForOneTileRetainBothPins() = runTest {
        val gateway = FakeGateway(
            mapOf(identity to listOf(shortcut("a", "A", 0), shortcut("b", "B", 1))),
        )
        val repository = FakeBindingRepository(emptyMap(), delayFirstLoad = true)
        val loader = ShortcutSurfaceLoader(gateway, repository, Dispatchers.Default)

        val results = listOf("a", "b").map { id ->
            async { loader.togglePin(tileId, identity, LogicalSpan(3, 1), id) }
        }.awaitAll()

        assertEquals(listOf(true, true), results)
        assertEquals(
            setOf("a", "b"),
            repository.binding(tileId).pinnedShortcuts.map { it.shortcutId }.toSet(),
        )
    }

    @Test
    fun pinAndUnpinPreserveOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(
            mapOf(identity to listOf(
                shortcut("a", "A", 0), shortcut("b", "B", 1), shortcut("c", "C", 2),
            )),
        )
        val repository = FakeBindingRepository(emptyMap())
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        assertTrue(loader.togglePin(tileId, identity, LogicalSpan(4, 1), "b"))
        assertTrue(loader.togglePin(tileId, identity, LogicalSpan(4, 1), "a"))
        assertEquals(
            listOf("b", "a"),
            repository.binding(tileId).pinnedShortcuts.map { it.shortcutId },
        )
        assertTrue(loader.togglePin(tileId, identity, LogicalSpan(4, 1), "b"))
        assertEquals(listOf("a"), repository.binding(tileId).pinnedShortcuts.map { it.shortcutId })
    }

    @Test
    fun atCapacityDoesNotWriteBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val a = shortcut("a", "A", 0)
        val gateway = FakeGateway(mapOf(identity to listOf(a, shortcut("b", "B", 1))))
        val repository = FakeBindingRepository(mapOf(tileId to TileBinding(tileId, listOf(a.ref))))
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        assertFalse(loader.togglePin(tileId, identity, LogicalSpan(2, 1), "b"))
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun spanChangeReprojectsCapacityWithoutPersistingOverflow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = FakeGateway(mapOf(identity to listOf(
            shortcut("a", "A", 0), shortcut("b", "B", 1), shortcut("c", "C", 2),
        )))
        val binding = TileBinding(tileId, listOf(gateway.shortcutsFor(identity).first().ref))
        val repository = FakeBindingRepository(mapOf(tileId to binding))
        val loader = ShortcutSurfaceLoader(gateway, repository, dispatcher)

        val narrow = loader.entriesForTile(tileId, identity, LogicalSpan(2, 1))
        val wide = loader.entriesForTile(tileId, identity, LogicalSpan(4, 1))
        assertEquals(listOf("a", "b", "c"), narrow.map { it.id })
        assertEquals(listOf(true, false, false), narrow.map { it.isPinEnabled })
        assertEquals(listOf(true, true, true), wide.map { it.isPinEnabled })
        assertEquals(binding, repository.binding(tileId))
        assertEquals(0, repository.saveCount)
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
        bindings: Map<ItemId, TileBinding>,
        albumBindings: Map<ItemId, TileAlbumBinding> = emptyMap(),
        private val delayFirstLoad: Boolean = false,
    ) : HomePlacementRepository {
        private val mutableBindings = bindings.toMutableMap()
        private val mutableAlbumBindings = albumBindings.toMutableMap()
        private var loadCount = 0
        var bindingReads = 0
        var albumReads = 0
        var saveCount = 0
        fun binding(tileId: ItemId): TileBinding = mutableBindings[tileId] ?: TileBinding(tileId)
        override fun load(homeId: HomeId, columns: Int): LoadResult = LoadResult.NotFound
        override fun place(
            homeId: HomeId,
            itemId: ItemId,
            appIdentity: AppIdentity,
            position: LogicalPosition,
            span: LogicalSpan,
            columns: Int,
            replacingId: ItemId?,
        ): MutationResult = error("surface loader must not write")
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
            error("surface loader must not write")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int): MutationResult =
            error("surface loader must not write")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = error("surface loader must not write")
        override fun ensureHome(homeId: HomeId): Boolean = error("surface loader must not write")
        override fun loadBinding(tileId: ItemId): TileBinding {
            bindingReads++
            if (delayFirstLoad && ++loadCount == 1) Thread.sleep(100)
            return synchronized(mutableBindings) {
                mutableBindings[tileId] ?: TileBinding(tileId)
            }
        }
        override fun saveBinding(binding: TileBinding) {
            saveCount++
            synchronized(mutableBindings) { mutableBindings[binding.tileId] = binding }
        }
        override fun deleteBinding(tileId: ItemId) = error("surface loader must not write")
        override fun loadAlbumBinding(tileId: ItemId): TileAlbumBinding? {
            albumReads++
            return synchronized(mutableBindings) { mutableAlbumBindings[tileId] }
        }
        override fun saveAlbumBinding(binding: TileAlbumBinding) = error("surface loader must not write")
        override fun deleteAlbumBinding(tileId: ItemId) = error("surface loader must not write")
    }
}
