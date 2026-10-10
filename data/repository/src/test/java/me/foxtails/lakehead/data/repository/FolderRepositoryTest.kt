package me.foxtails.lakehead.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.model.AlbumRef
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
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.WidgetPlacement
import me.foxtails.lakehead.data.database.PlacementDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest {
    private val columns = 5
    private val home = HomeId("home")

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), PlacementDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val homeRepo: HomePlacementRepository = RoomHomePlacementRepository(database)
    private val folders: FolderRepository = RoomFolderRepository(database)

    @After fun closeDatabase() = database.close()

    // ---------- helpers ----------

    private fun app(name: String) = AppIdentity(ProfileId("0"), ComponentIdentity("org.$name", "org.$name.Main"))

    private fun placeApp(id: String, col: Int, row: Int, cols: Int = 1, rows: Int = 1) {
        homeRepo.ensureHome(home)
        val result = homeRepo.place(home, ItemId(id), app(id), LogicalPosition(col, row), LogicalSpan(cols, rows), columns)
        assertTrue("place $id: $result", result is MutationResult.Accepted)
    }

    private fun placeWidget(id: String, col: Int, row: Int, appWidgetId: Int, cols: Int = 4, rows: Int = 2) {
        homeRepo.ensureHome(home)
        val result = homeRepo.placeWidget(
            home, ItemId(id), app(id), appWidgetId, LogicalPosition(col, row), LogicalSpan(cols, rows), columns,
        )
        assertTrue("placeWidget $id: $result", result is MutationResult.Accepted)
    }

    private fun homeRows(): List<HomePlacement> =
        (homeRepo.load(home, columns) as LoadResult.Loaded).placements

    private fun members(folder: String): List<HomePlacement> =
        (folders.loadFolder(home, ItemId(folder), columns) as LoadFolderResult.Loaded).members

    private fun create(from: String, folder: String) {
        val result = folders.createFolder(home, ItemId(from), ItemId(folder), columns)
        assertEquals(CreateFolderResult.Created(ItemId(folder)), result)
    }

    private fun into(item: String, folder: String) {
        assertEquals(TransferResult.Done(Dissolve.None), folders.moveIntoFolder(ItemId(item), ItemId(folder), columns))
    }

    private fun snapshot() = homeRows() to database.placementDao().placements().map { it.itemId to it.parentFolderId }

    // ---------- slice 3: parent-scoped occupancy ----------

    @Test fun homeLoadReturnsFolderTileButNeverItsMembers() {
        placeApp("a", 0, 0)
        placeApp("b", 1, 0)
        create("a", "f")
        into("b", "f")

        assertEquals(listOf(ItemId("f")), homeRows().map { it.itemId })
        assertEquals(setOf(ItemId("a"), ItemId("b")), members("f").map { it.itemId }.toSet())
    }

    @Test fun memberCellsAreFreeOnHomeAndHomeCellsAreFreeInTheFolder() {
        placeApp("a", 0, 0)
        create("a", "f")
        // The member sits at (0,0) inside the folder; Home cell (0,0) holds the folder, but (1,0) etc are free.
        val onMemberCell = homeRepo.place(home, ItemId("x"), app("x"), LogicalPosition(0, 1), LogicalSpan(2, 2), columns)
        assertTrue(onMemberCell is MutationResult.Accepted)
        val inside = members("f").single()
        assertEquals(LogicalPosition(0, 0), inside.position)
        assertEquals(LogicalPosition(0, 1), homeRows().first { it.itemId == ItemId("x") }.position)
    }

    @Test fun memberMoveAndResizeAreJudgedAgainstTheFoldersGrid() {
        placeApp("a", 0, 0)
        placeApp("b", 1, 0)
        placeApp("h", 3, 0, 2, 2)
        create("a", "f")
        into("b", "f")
        val b = ItemId("b")

        // (3,0) is occupied by a Home tile but free inside the folder.
        assertTrue(homeRepo.move(home, b, LogicalPosition(3, 0), columns) is MutationResult.Accepted)
        // (0,0) is held by sibling member a.
        val clash = homeRepo.move(home, b, LogicalPosition(0, 0), columns)
        assertEquals(GridRules.Rejection.COLLISION, (clash as MutationResult.Rejected).gridResult.reason)
        assertTrue(homeRepo.resize(home, b, LogicalSpan(2, 2), columns) is MutationResult.Accepted)
        assertEquals(LogicalSpan(2, 2), members("f").first { it.itemId == b }.span)
        assertEquals(LogicalSpan(2, 2), homeRows().first { it.itemId == ItemId("h") }.span)
    }

    @Test fun memberRowsNeverShiftHomeTiles() {
        placeApp("a", 0, 0)
        placeApp("t", 2, 3)
        create("a", "f")
        val before = homeRows().map { it.itemId to it.position }
        homeRepo.move(home, ItemId("a"), LogicalPosition(4, 9), columns)
        assertEquals(before, homeRows().map { it.itemId to it.position })
    }

    @Test fun homeRemoveRefusesMembersAndNonEmptyFolders() {
        placeApp("a", 0, 0)
        create("a", "f")
        assertEquals(RemoveResult.FolderMember, homeRepo.remove(home, ItemId("a")))
        assertEquals(RemoveResult.NotEmptyFolder, homeRepo.remove(home, ItemId("f")))
        assertEquals(1, members("f").size)
    }

    // ---------- slice 4: create, load, transfer ----------

    @Test fun createFolderPutsA1x1FolderAtTheTilesOriginAndMovesTheTileInWithItsSpanAndBindings() {
        placeApp("big", 1, 2, 2, 2)
        placeApp("other", 4, 0)
        homeRepo.saveAlbumBinding(TileAlbumBinding(ItemId("big"), AlbumRef("b1", "Camera")))
        val otherBefore = homeRows().first { it.itemId == ItemId("other") }

        create("big", "f")

        val folder = homeRows().first { it.itemId == ItemId("f") } as FolderPlacement
        assertEquals(LogicalPosition(1, 2), folder.position)
        assertEquals(LogicalSpan(1, 1), folder.span)
        val member = members("f").single() as AppPlacement
        assertEquals(ItemId("big"), member.itemId)
        assertEquals(LogicalPosition(0, 0), member.position)
        assertEquals(LogicalSpan(2, 2), member.span)
        assertEquals(ItemId("f"), member.parentFolderId)
        assertEquals(AlbumRef("b1", "Camera"), homeRepo.loadAlbumBinding(ItemId("big"))!!.album)
        assertEquals(otherBefore, homeRows().first { it.itemId == ItemId("other") })
    }

    @Test fun createFolderRejectsFoldersMembersMissingAndForeignInputs() {
        placeApp("a", 0, 0)
        create("a", "f")
        assertEquals(CreateFolderResult.WouldNest, folders.createFolder(home, ItemId("f"), ItemId("g"), columns))
        assertEquals(CreateFolderResult.WouldNest, folders.createFolder(home, ItemId("a"), ItemId("g"), columns))
        assertEquals(CreateFolderResult.NotFound, folders.createFolder(home, ItemId("nope"), ItemId("g"), columns))
        assertEquals(CreateFolderResult.WrongHome, folders.createFolder(HomeId("other"), ItemId("f"), ItemId("g"), columns))
        placeApp("b", 1, 0)
        assertEquals(CreateFolderResult.ItemIdAlreadyExists, folders.createFolder(home, ItemId("b"), ItemId("a"), columns))
    }

    @Test fun widgetsAndWideTilesTransferBothWaysWithBindingsIntact() {
        placeApp("a", 0, 0)
        placeWidget("w", 0, 2, appWidgetId = 77)
        create("a", "f")
        into("w", "f")

        val inside = members("f").first { it.itemId == ItemId("w") } as WidgetPlacement
        assertEquals(77, inside.appWidgetId)
        assertEquals(LogicalSpan(4, 2), inside.span)
        assertTrue(77 in homeRepoPersistedIds())

        // a is 1x1 at (0,0); the widget takes the next free 4x2 spot.
        assertEquals(LogicalPosition(1, 0), inside.position)

        // Add a third member so leaving does not dissolve.
        placeApp("c", 3, 5)
        into("c", "f")
        assertEquals(TransferResult.Done(Dissolve.None), folders.moveToHome(ItemId("w"), columns))
        val back = homeRows().first { it.itemId == ItemId("w") } as WidgetPlacement
        assertNull(back.parentFolderId)
        assertEquals(77, back.appWidgetId)
        assertEquals(LogicalSpan(4, 2), back.span)
    }

    private fun homeRepoPersistedIds() = homeRepo.persistedAppWidgetIds()

    @Test fun transferResultsAreTyped() {
        placeApp("a", 0, 0)
        placeApp("b", 1, 0)
        create("a", "f")
        assertEquals(TransferResult.NotFound, folders.moveIntoFolder(ItemId("nope"), ItemId("f"), columns))
        assertEquals(TransferResult.NotAFolder, folders.moveIntoFolder(ItemId("b"), ItemId("b"), columns))
        assertEquals(TransferResult.WouldNest, folders.moveIntoFolder(ItemId("f"), ItemId("f"), columns))
        assertEquals(TransferResult.WouldNest, folders.moveIntoFolder(ItemId("a"), ItemId("f"), columns))
        assertEquals(TransferResult.NotAMember, folders.moveToHome(ItemId("b"), columns))
        assertEquals(LoadFolderResult.NotAFolder, folders.loadFolder(home, ItemId("b"), columns))
        assertEquals(LoadFolderResult.NotFound, folders.loadFolder(home, ItemId("zz"), columns))
        assertEquals(LoadFolderResult.WrongHome, folders.loadFolder(HomeId("x"), ItemId("f"), columns))
    }

    @Test fun aTileWiderThanTheGridIsRejectedWithoutChangingAnything() {
        placeApp("a", 0, 0)
        placeWidget("w", 0, 2, appWidgetId = 5)
        create("a", "f")
        val before = snapshot()
        val result = folders.moveIntoFolder(ItemId("w"), ItemId("f"), 3)
        assertTrue(result is TransferResult.Rejected)
        assertEquals(before, snapshot())
    }

    @Test fun equalColumnCountsAlwaysFitTheWidestSpan() {
        placeWidget("w", 0, 0, appWidgetId = 5)
        placeApp("a", 0, 3)
        create("a", "f")
        into("w", "f")
        assertEquals(LogicalSpan(4, 2), members("f").first { it.itemId == ItemId("w") }.span)
    }

    // ---------- slice 5: dissolve and remove ----------

    @Test fun twoToOneReplacesTheFolderAtItsPositionWithTheSurvivorsSpan() {
        placeApp("a", 2, 1, 2, 2)
        placeApp("b", 0, 0)
        create("a", "f")
        into("b", "f")
        homeRepo.saveAlbumBinding(TileAlbumBinding(ItemId("a"), AlbumRef("b1", "Camera")))

        val result = folders.moveToHome(ItemId("b"), columns)

        assertEquals(TransferResult.Done(Dissolve.Replaced(ItemId("f"), ItemId("a"))), result)
        val rows = homeRows()
        assertNull(rows.firstOrNull { it.itemId == ItemId("f") })
        val survivor = rows.first { it.itemId == ItemId("a") }
        assertEquals(LogicalPosition(2, 1), survivor.position)
        assertEquals(LogicalSpan(2, 2), survivor.span)
        assertNull(survivor.parentFolderId)
        assertNotNull(homeRepo.loadAlbumBinding(ItemId("a")))
        assertTrue(rows.any { it.itemId == ItemId("b") })
    }

    @Test fun aSurvivorTooLargeForTheFolderCellLandsAtTheFirstFreeRectangle() {
        placeWidget("w", 1, 0, appWidgetId = 9)
        placeApp("b", 0, 5)
        create("w", "f")           // folder at (1,0), 1x1
        placeApp("x", 3, 1)        // blocks a 4x2 at the folder's cell
        into("b", "f")
        folders.moveToHome(ItemId("b"), columns).let {
            assertEquals(TransferResult.Done(Dissolve.Replaced(ItemId("f"), ItemId("w"))), it)
        }
        val survivor = homeRows().first { it.itemId == ItemId("w") }
        assertNull(survivor.parentFolderId)
        assertEquals(LogicalSpan(4, 2), survivor.span)
        assertEquals(LogicalPosition(0, 2), survivor.position)
        val others = homeRows().filter { it.itemId != ItemId("w") }
        assertTrue("survivor must not overlap", others.none { overlaps(it, survivor) })
    }

    @Test fun aFailingDissolveRollsBackTheRemovalThatCausedIt() {
        placeApp("a", 0, 0)
        placeWidget("w", 0, 2, appWidgetId = 4)
        create("a", "f")
        into("w", "f")
        val before = snapshot()

        // At 3 columns the 4-wide survivor cannot be placed, so the dissolve throws.
        val failure = runCatching { folders.moveToHome(ItemId("a"), 3) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, snapshot())
    }

    @Test fun oneToZeroDeletesTheFolder() {
        placeApp("a", 0, 0)
        create("a", "f")
        val result = folders.removeMember(home, ItemId("a"), columns)
        assertEquals(RemoveMemberResult.Removed(Dissolve.Deleted(ItemId("f"))), result)
        assertTrue(homeRows().isEmpty())
        assertNull(homeRepo.loadAlbumBinding(ItemId("a")))
    }

    @Test fun movingTheOnlyMemberOutDeletesTheFolderAndPlacesItOnHome() {
        placeApp("a", 0, 0)
        create("a", "f")
        val result = folders.moveToHome(ItemId("a"), columns)
        assertEquals(TransferResult.Done(Dissolve.Deleted(ItemId("f"))), result)
        assertEquals(listOf(ItemId("a")), homeRows().map { it.itemId })
    }

    @Test fun creatingAOneMemberFolderDoesNotDissolveIt() {
        placeApp("a", 0, 0)
        create("a", "f")
        assertEquals(listOf(ItemId("f")), homeRows().map { it.itemId })
        assertEquals(1, members("f").size)
    }

    @Test fun anUnavailableMemberStillCountsAsAMember() {
        placeApp("a", 0, 0)
        placeApp("b", 1, 0)
        create("a", "f")
        into("b", "f")
        database.placementDao().updateAvailability("b", ItemAvailability.UNAVAILABLE.name)
        placeApp("c", 2, 0)
        into("c", "f")
        assertEquals(TransferResult.Done(Dissolve.None), folders.moveToHome(ItemId("c"), columns))
        assertEquals(2, members("f").size)
    }

    @Test fun removingAMemberFromAThreeMemberFolderKeepsTheFolder() {
        placeApp("a", 0, 0)
        placeApp("b", 1, 0)
        placeApp("c", 2, 0)
        create("a", "f")
        into("b", "f")
        into("c", "f")
        assertEquals(
            RemoveMemberResult.Removed(Dissolve.None),
            folders.removeMember(home, ItemId("b"), columns),
        )
        assertEquals(2, members("f").size)
    }

    @Test fun removeFolderDeletesMembersAndBindingsAndReportsWidgetIds() {
        placeApp("a", 0, 0)
        placeWidget("w", 0, 2, appWidgetId = 31)
        placeApp("keep", 4, 0)
        create("a", "f")
        into("w", "f")
        homeRepo.saveAlbumBinding(TileAlbumBinding(ItemId("a"), AlbumRef("b1", "Camera")))

        val result = folders.removeFolder(home, ItemId("f"))

        assertEquals(RemoveFolderResult.Removed(listOf(31)), result)
        assertEquals(listOf(ItemId("keep")), homeRows().map { it.itemId })
        assertNull(homeRepo.loadAlbumBinding(ItemId("a")))
        assertTrue(31 !in homeRepo.persistedAppWidgetIds())
        assertEquals(0, database.placementDao().placementCount() - 1)
    }

    @Test fun removeFolderResultsAreTyped() {
        placeApp("a", 0, 0)
        assertEquals(RemoveFolderResult.NotFound, folders.removeFolder(home, ItemId("zz")))
        assertEquals(RemoveFolderResult.NotAFolder, folders.removeFolder(home, ItemId("a")))
        create("a", "f")
        assertEquals(RemoveFolderResult.WrongHome, folders.removeFolder(HomeId("x"), ItemId("f")))
        assertEquals(RemoveMemberResult.NotAMember, folders.removeMember(home, ItemId("f"), columns))
    }

    private fun overlaps(a: HomePlacement, b: HomePlacement): Boolean =
        a.position.column < b.position.column + b.span.columns &&
            b.position.column < a.position.column + a.span.columns &&
            a.position.row < b.position.row + b.span.rows &&
            b.position.row < a.position.row + a.span.rows
}
