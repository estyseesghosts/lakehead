package me.foxtails.lakehead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.data.repository.CreateFolderResult
import me.foxtails.lakehead.data.repository.Dissolve
import me.foxtails.lakehead.data.repository.FolderRepository
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.data.repository.LoadFolderResult
import me.foxtails.lakehead.data.repository.RemoveFolderResult
import me.foxtails.lakehead.data.repository.RemoveMemberResult
import me.foxtails.lakehead.data.repository.TransferResult
import me.foxtails.lakehead.feature.home.HomeAppMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderCoordinatorTest {
    private val home = HomeId("home")
    private val folder = ItemId("f")

    private fun id(name: String) = AppIdentity(ProfileId("0"), ComponentIdentity("org.$name", "org.$name.Main"))

    private fun member(name: String, column: Int = 0, row: Int = 0): HomePlacement = AppPlacement(
        home, ItemId(name), id(name), LogicalPosition(column, row), LogicalSpan(1, 1),
        ItemAvailability.AVAILABLE, parentFolderId = folder,
    )

    private class FakeFolders : FolderRepository {
        var members: List<HomePlacement> = emptyList()
        var loadResult: LoadFolderResult? = null
        var create: CreateFolderResult = CreateFolderResult.NotFound
        var transfer: TransferResult = TransferResult.NotFound
        var removeMember: RemoveMemberResult = RemoveMemberResult.NotFound
        var removeFolder: RemoveFolderResult = RemoveFolderResult.NotFound
        val calls = mutableListOf<String>()

        override fun createFolder(homeId: HomeId, fromItemId: ItemId, folderId: ItemId, columns: Int): CreateFolderResult {
            calls += "create ${fromItemId.value}->${folderId.value}"
            return create
        }

        override fun loadFolder(homeId: HomeId, folderId: ItemId, columns: Int): LoadFolderResult =
            loadResult ?: LoadFolderResult.Loaded(members)

        override fun loadAllMembers(homeId: HomeId): Map<ItemId, List<HomePlacement>> = emptyMap()

        override fun moveIntoFolder(itemId: ItemId, folderId: ItemId, columns: Int): TransferResult {
            calls += "into ${itemId.value}->${folderId.value}"
            return transfer
        }

        override fun moveToHome(itemId: ItemId, columns: Int): TransferResult {
            calls += "home ${itemId.value}"
            return transfer
        }

        override fun removeMember(homeId: HomeId, itemId: ItemId, columns: Int): RemoveMemberResult {
            calls += "removeMember ${itemId.value}"
            return removeMember
        }

        override fun removeFolder(homeId: HomeId, folderId: ItemId): RemoveFolderResult {
            calls += "removeFolder ${folderId.value}"
            return removeFolder
        }
    }

    private class RecordingPlacements(
        var result: MutationResult = MutationResult.NotFound,
    ) : HomePlacementRepository by FakeHomePlacementRepository() {
        val calls = mutableListOf<String>()
        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int): MutationResult {
            calls += "move ${itemId.value}->${position.column},${position.row}"
            return result
        }
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int): MutationResult {
            calls += "resize ${itemId.value}->${span.columns}x${span.rows}"
            return result
        }
    }

    private class Harness(val repo: FakeFolders, val placements: RecordingPlacements = RecordingPlacements()) {
        var homeChanges = 0
        val released = mutableListOf<Int>()
        val coordinator = FolderCoordinator(
            homeId = HomeId("home"),
            folders = repo,
            placements = placements,
            columns = 5,
            metadata = { mapOf(AppIdentity(ProfileId("0"), ComponentIdentity("org.a", "org.a.Main")) to HomeAppMetadata("A")) },
            onHomeChanged = { homeChanges++ },
            releaseWidget = { released += it },
            ioDispatcher = Dispatchers.Unconfined,
            newFolderId = { ItemId("new") },
        )
    }

    private fun harness(configure: FakeFolders.() -> Unit = {}) = Harness(FakeFolders().apply(configure))

    @Test fun openLoadsMembersIntoTiles() = runTest {
        val h = harness { members = listOf(member("a"), member("b", 1)) }
        h.coordinator.open(folder)
        assertEquals(folder, h.coordinator.openFolder.value)
        assertEquals(listOf(ItemId("a"), ItemId("b")), h.coordinator.members.value.map { it.itemId })
        assertEquals("A", h.coordinator.members.value.first().label)
    }

    @Test fun openingAMissingFolderLeavesTheOverlayClosed() = runTest {
        val h = harness { loadResult = LoadFolderResult.NotFound }
        h.coordinator.open(folder)
        assertNull(h.coordinator.openFolder.value)
    }

    @Test fun closeClearsState() = runTest {
        val h = harness { members = listOf(member("a")) }
        h.coordinator.open(folder)
        h.coordinator.close()
        assertNull(h.coordinator.openFolder.value)
        assertTrue(h.coordinator.members.value.isEmpty())
    }

    @Test fun createFolderReloadsHomeOnlyOnSuccess() = runTest {
        val h = harness { create = CreateFolderResult.Created(ItemId("new")) }
        assertTrue(h.coordinator.createFolder(ItemId("a")))
        assertEquals(listOf("create a->new"), h.repo.calls)
        assertEquals(1, h.homeChanges)
        h.repo.create = CreateFolderResult.WouldNest
        assertFalse(h.coordinator.createFolder(ItemId("a")))
        assertEquals(1, h.homeChanges)
    }

    @Test fun addToFolderGoesThroughTheChooser() = runTest {
        val h = harness { transfer = TransferResult.Done(Dissolve.None) }
        assertFalse("nothing pending", h.coordinator.pickFolder(folder))
        h.coordinator.requestAddToFolder(ItemId("t"))
        assertEquals(ItemId("t"), h.coordinator.addSource.value)
        assertTrue(h.coordinator.pickFolder(folder))
        assertEquals(listOf("into t->f"), h.repo.calls)
        assertNull(h.coordinator.addSource.value)
        assertEquals(1, h.homeChanges)
    }

    @Test fun dismissingTheChooserWritesNothing() = runTest {
        val h = harness()
        h.coordinator.requestAddToFolder(ItemId("t"))
        h.coordinator.dismissChooser()
        assertNull(h.coordinator.addSource.value)
        assertTrue(h.repo.calls.isEmpty())
    }

    @Test fun movingToHomeKeepsTheOverlayOpenWhileTheFolderSurvives() = runTest {
        val h = harness {
            members = listOf(member("a"), member("b", 1))
            transfer = TransferResult.Done(Dissolve.None)
        }
        h.coordinator.open(folder)
        h.repo.members = listOf(member("a"))
        h.coordinator.moveToHome(ItemId("b"))
        assertEquals(folder, h.coordinator.openFolder.value)
        assertEquals(listOf(ItemId("a")), h.coordinator.members.value.map { it.itemId })
        assertEquals(1, h.homeChanges)
    }

    @Test fun dissolveToOneClosesTheOpenOverlay() = runTest {
        val h = harness {
            members = listOf(member("a"), member("b", 1))
            transfer = TransferResult.Done(Dissolve.Replaced(folder, ItemId("a")))
        }
        h.coordinator.open(folder)
        assertTrue(h.coordinator.moveToHome(ItemId("b")))
        assertNull(h.coordinator.openFolder.value)
        assertEquals(1, h.homeChanges)
    }

    @Test fun removingTheLastMemberClosesTheOverlay() = runTest {
        val h = harness {
            members = listOf(member("a"))
            removeMember = RemoveMemberResult.Removed(Dissolve.Deleted(folder))
        }
        h.coordinator.open(folder)
        assertTrue(h.coordinator.removeMember(ItemId("a")))
        assertNull(h.coordinator.openFolder.value)
    }

    @Test fun failedMemberActionsLeaveEverythingAlone() = runTest {
        val h = harness { members = listOf(member("a")) }
        h.coordinator.open(folder)
        assertFalse(h.coordinator.moveToHome(ItemId("a")))
        assertFalse(h.coordinator.removeMember(ItemId("a")))
        assertEquals(folder, h.coordinator.openFolder.value)
        assertEquals(0, h.homeChanges)
    }

    @Test fun removingANonEmptyFolderAsksFirstThenReleasesWidgetsAfterTheDelete() = runTest {
        val h = harness {
            members = listOf(member("a"))
            removeFolder = RemoveFolderResult.Removed(listOf(31, 32))
        }
        h.coordinator.requestRemoveFolder(folder)
        assertEquals(folder, h.coordinator.removePrompt.value)
        assertTrue("no delete before confirmation", h.repo.calls.isEmpty())
        assertTrue(h.coordinator.confirmRemoveFolder())
        assertEquals(listOf(31, 32), h.released)
        assertNull(h.coordinator.removePrompt.value)
        assertEquals(1, h.homeChanges)
    }

    @Test fun cancellingTheRemovePromptDeletesNothing() = runTest {
        val h = harness { members = listOf(member("a")) }
        h.coordinator.requestRemoveFolder(folder)
        h.coordinator.dismissRemovePrompt()
        assertFalse(h.coordinator.confirmRemoveFolder())
        assertTrue(h.repo.calls.isEmpty())
        assertTrue(h.released.isEmpty())
    }

    @Test fun aFailedRemoveReleasesNoWidgets() = runTest {
        val h = harness {
            members = listOf(member("a"))
            removeFolder = RemoveFolderResult.NotFound
        }
        h.coordinator.requestRemoveFolder(folder)
        assertFalse(h.coordinator.confirmRemoveFolder())
        assertTrue(h.released.isEmpty())
        assertEquals(0, h.homeChanges)
    }

    @Test fun removingTheOpenFolderClosesTheOverlay() = runTest {
        val h = harness {
            members = listOf(member("a"))
            removeFolder = RemoveFolderResult.Removed(emptyList())
        }
        h.coordinator.open(folder)
        h.coordinator.requestRemoveFolder(folder)
        h.coordinator.confirmRemoveFolder()
        assertNull(h.coordinator.openFolder.value)
    }

    @Test fun refreshClosesAFolderThatNoLongerExists() = runTest {
        val h = harness { members = listOf(member("a")) }
        h.coordinator.open(folder)
        h.repo.loadResult = LoadFolderResult.NotFound
        h.coordinator.refreshOpen()
        assertNull(h.coordinator.openFolder.value)
    }

    @Test fun moveMemberWritesThenRereadsTheOpenFolder() = runTest {
        val h = harness { members = listOf(member("a"), member("b", 1)) }
        h.coordinator.open(folder)
        h.repo.members = listOf(member("a", 2), member("b", 1))
        h.placements.result = MutationResult.Accepted(
            GridRules.Result.Accepted(emptyList()),
        )

        val moved = h.coordinator.moveMember(ItemId("a"), LogicalPosition(2, 0))

        assertTrue(moved)
        assertEquals(listOf("move a->2,0"), h.placements.calls)
        assertEquals(2, h.coordinator.members.value.first { it.itemId == ItemId("a") }.position.column)
    }

    @Test fun rejectedMoveStillRereadsSoTheTileSnapsBack() = runTest {
        val h = harness { members = listOf(member("a")) }
        h.coordinator.open(folder)
        h.placements.result = MutationResult.Rejected(GridRules.Result.Rejected(GridRules.Rejection.COLLISION))

        assertFalse(h.coordinator.moveMember(ItemId("a"), LogicalPosition(1, 0)))
        assertEquals(0, h.coordinator.members.value.single().position.column)
    }

    @Test fun resizeMemberWritesThroughTheRepository() = runTest {
        val h = harness { members = listOf(member("a")) }
        h.coordinator.open(folder)

        h.coordinator.resizeMember(ItemId("a"), LogicalSpan(2, 1))

        assertEquals(listOf("resize a->2x1"), h.placements.calls)
    }
}
