package me.foxtails.lakehead

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.FolderPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.repository.CreateFolderResult
import me.foxtails.lakehead.data.repository.Dissolve
import me.foxtails.lakehead.data.repository.FolderRepository
import me.foxtails.lakehead.data.repository.LoadFolderResult
import me.foxtails.lakehead.data.repository.RemoveFolderResult
import me.foxtails.lakehead.data.repository.RemoveMemberResult
import me.foxtails.lakehead.data.repository.TransferResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutFolderExportImportTest {
    private val home = HomeId("home")
    private val folderId = ItemId("f")

    private fun app(name: String, parent: ItemId? = null, column: Int = 0, row: Int = 0) = AppPlacement(
        home, ItemId(name),
        AppIdentity(ProfileId("0"), ComponentIdentity("org.$name", "org.$name.Main")),
        LogicalPosition(column, row), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, parentFolderId = parent,
    )

    private class RecordingFolders : FolderRepository {
        val calls = mutableListOf<String>()
        override fun createFolder(homeId: HomeId, fromItemId: ItemId, folderId: ItemId, columns: Int): CreateFolderResult {
            calls += "create ${fromItemId.value}->${folderId.value}"
            return CreateFolderResult.Created(folderId)
        }
        override fun loadFolder(homeId: HomeId, folderId: ItemId, columns: Int): LoadFolderResult =
            LoadFolderResult.Loaded(emptyList())
        override fun loadAllMembers(homeId: HomeId): Map<ItemId, List<HomePlacement>> = emptyMap()
        override fun moveIntoFolder(itemId: ItemId, folderId: ItemId, columns: Int): TransferResult {
            calls += "into ${itemId.value}->${folderId.value}"
            return TransferResult.Done(Dissolve.Deleted(folderId))
        }
        override fun moveToHome(itemId: ItemId, columns: Int): TransferResult = TransferResult.NotFound
        override fun removeMember(homeId: HomeId, itemId: ItemId, columns: Int): RemoveMemberResult =
            RemoveMemberResult.NotFound
        override fun removeFolder(homeId: HomeId, folderId: ItemId): RemoveFolderResult = RemoveFolderResult.NotFound
    }

    private fun folderDocument(memberNames: List<String>): JSONObject {
        val folder = FolderPlacement(
            home, folderId, LogicalPosition(1, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
        )
        val members = memberNames.map { app(it, parent = folderId) }
        return HomeLayoutCodec.encodeLayout(
            home, 5, listOf(folder), emptyMap<ItemId, TileBinding>(), folderMembers = mapOf(folderId to members),
        )
    }

    @Test
    fun codecRoundTripsFolderMembersAndSkipsWidgets() {
        val json = folderDocument(listOf("a", "b"))
        val doc = HomeLayoutCodec.decodeLayout(json)

        val item = doc.items.single()
        assertEquals("FOLDER", item.type)
        assertNull(item.app)
        assertEquals(listOf("a", "b"), item.members.map { it.id })
        assertEquals("org.a", item.members[0].app.packageName)
        assertEquals(1, HomeLayoutCodec.HOME_LAYOUT_VERSION)
    }

    @Test
    fun appOnlyDocumentsStillDecodeWithNoMembers() {
        val json = JSONObject().apply {
            put("version", 1)
            put("kind", HomeLayoutCodec.HOME_LAYOUT_KIND)
            put("home", JSONObject().apply {
                put("id", "home")
                put("columns", 5)
                put("items", JSONArray())
            })
        }
        assertTrue(HomeLayoutCodec.decodeLayout(json).items.isEmpty())
    }

    @Test
    fun importRebuildsFolderThroughRepository() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val folders = RecordingFolders()
        val coordinator = HomeLayoutExportImport(home, 5, FakeHomePlacementRepository(5), dispatcher, folders = folders)

        val result = coordinator.importLayout(folderDocument(listOf("a", "b", "c")))

        assertTrue(result is ImportResult.Applied)
        assertEquals(3, (result as ImportResult.Applied).itemsPlaced)
        assertTrue(result.rejected.isEmpty())
        assertEquals(listOf("create a->f", "into b->f", "into c->f"), folders.calls)
    }

    @Test
    fun importRejectsFoldersWhenUnavailable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = HomeLayoutExportImport(home, 5, FakeHomePlacementRepository(5), dispatcher)

        val result = coordinator.importLayout(folderDocument(listOf("a", "b")))

        val applied = result as ImportResult.Applied
        assertEquals(0, applied.itemsPlaced)
        assertEquals(listOf("f"), applied.rejected.map { it.id })
    }
}
