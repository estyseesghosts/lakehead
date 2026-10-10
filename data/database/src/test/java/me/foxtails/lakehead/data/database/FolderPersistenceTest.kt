package me.foxtails.lakehead.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.FolderPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.WidgetPlacement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = AppIdentity(ProfileId("0"), ComponentIdentity("org.example", "org.example.Home"))
    private var database: PlacementDatabase? = null

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migration4To5_preservesEveryRowAndBindingAsTopLevel() {
        createVersion4Database()
        val dao = openMigrated().placementDao()

        val rows = dao.topLevelPlacements("home")
        assertEquals(listOf("a1", "w1"), rows.map { it.itemId })
        assertEquals(listOf<String?>(null, null), rows.map { it.parentFolderId })
        assertEquals(
            AppPlacement(
                HomeId("home"), ItemId("a1"), app, LogicalPosition(1, 0), LogicalSpan(2, 2),
                ItemAvailability.AVAILABLE,
            ),
            rows[0].toDomain(),
        )
        assertEquals(
            WidgetPlacement(
                HomeId("home"), ItemId("w1"), app, 42, LogicalPosition(0, 2), LogicalSpan(4, 2),
                ItemAvailability.UNAVAILABLE,
            ),
            rows[1].toDomain(dao.widgetBinding("w1")),
        )
        assertEquals("[]", dao.shortcutBinding("a1")!!.pinnedShortcutIds)
        assertEquals(
            TileAlbumBinding(ItemId("a1"), AlbumRef("bucket-9", "Camera")),
            dao.albumBinding("a1")!!.toDomain(),
        )
        assertEquals(1, dao.widgetBindingsForHome("home").size)
    }

    @Test
    fun folderRowRoundTripsWithoutAppReference() {
        val dao = openFresh().placementDao()
        dao.insertHome(HomeEntity("home"))
        val folder = FolderPlacement(
            HomeId("home"), ItemId("f1"), LogicalPosition(3, 1), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
        )
        dao.insertPlacement(folder.toEntity())

        val entity = dao.placement("f1")!!
        assertNull(entity.packageName)
        assertEquals(folder, entity.toDomain())
    }

    @Test
    fun queriesSeparateTopLevelFromMembers() {
        val dao = openFresh().placementDao()
        seedFolderWithMember(dao)

        assertEquals(listOf("f1"), dao.topLevelPlacements("home").map { it.itemId })
        assertEquals(listOf("m1"), dao.folderMembers("home", "f1").map { it.itemId })
        assertEquals(1, dao.folderMemberCount("f1"))
        assertEquals(listOf("f1"), dao.folders("home").map { it.itemId })
    }

    @Test
    fun deletingTheFolderCascadesToMembersAndTheirBindings() {
        val dao = openFresh().placementDao()
        seedFolderWithMember(dao)
        dao.upsertAlbumBinding(TileAlbumBinding(ItemId("m1"), AlbumRef("b", "Camera")).toEntity())
        dao.upsertWidgetBinding(WidgetBindingEntity("m1", 9))

        dao.deletePlacement("f1")

        assertNull(dao.placement("m1"))
        assertNull(dao.albumBinding("m1"))
        assertNull(dao.widgetBinding("m1"))
    }

    @Test
    fun reparentKeepsIdentityAndBindings() {
        val dao = openFresh().placementDao()
        seedFolderWithMember(dao)
        dao.upsertAlbumBinding(TileAlbumBinding(ItemId("m1"), AlbumRef("b", "Camera")).toEntity())

        dao.reparentPlacement("m1", null, 4, 7)

        val moved = dao.placement("m1")!!
        assertNull(moved.parentFolderId)
        assertEquals(4, moved.column)
        assertEquals(7, moved.row)
        assertEquals(TileAlbumBinding(ItemId("m1"), AlbumRef("b", "Camera")), dao.albumBinding("m1")!!.toDomain())
    }

    @Test
    fun mappingRejectsCorruptCombinations() {
        val withApp = PlacementEntity("f", "home", "FOLDER", "0", "p", "c", 0, 0, 1, 1, "AVAILABLE", null)
        val appWithout = PlacementEntity("a", "home", "APP", null, null, null, 0, 0, 1, 1, "AVAILABLE", null)
        val partial = PlacementEntity("a", "home", "APP", "0", null, "c", 0, 0, 1, 1, "AVAILABLE", null)
        val nested = PlacementEntity("f", "home", "FOLDER", null, null, null, 0, 0, 1, 1, "AVAILABLE", "g")
        listOf(withApp, appWithout, partial, nested).forEach { corrupt ->
            assertThrows(IllegalArgumentException::class.java) { corrupt.toDomain() }
        }
    }

    private fun seedFolderWithMember(dao: PlacementDao) {
        dao.insertHome(HomeEntity("home"))
        dao.insertAppReference(app.toEntity())
        dao.insertPlacement(
            FolderPlacement(
                HomeId("home"), ItemId("f1"), LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
            ).toEntity(),
        )
        dao.insertPlacement(
            AppPlacement(
                HomeId("home"), ItemId("m1"), app, LogicalPosition(0, 0), LogicalSpan(2, 2),
                ItemAvailability.AVAILABLE, parentFolderId = ItemId("f1"),
            ).toEntity(),
        )
    }

    private fun openFresh(): PlacementDatabase =
        Room.databaseBuilder(context, PlacementDatabase::class.java, DB_NAME)
            .allowMainThreadQueries().build().also { database = it }

    private fun openMigrated(): PlacementDatabase =
        Room.databaseBuilder(context, PlacementDatabase::class.java, DB_NAME)
            .addMigrations(
                PlacementDatabase.MIGRATION_1_2,
                PlacementDatabase.MIGRATION_2_3,
                PlacementDatabase.MIGRATION_3_4,
                PlacementDatabase.MIGRATION_4_5,
            )
            .allowMainThreadQueries().build().also { database = it }

    /** A database shaped as exported schema 4.json: an app, a widget and all three binding kinds. */
    private fun createVersion4Database() {
        val file = context.getDatabasePath(DB_NAME).also { it.parentFile?.mkdirs() }
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        listOf(
            "CREATE TABLE `homes` (`homeId` TEXT NOT NULL, PRIMARY KEY(`homeId`))",
            "CREATE TABLE `app_references` (`profileId` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                "`className` TEXT NOT NULL, PRIMARY KEY(`profileId`, `packageName`, `className`))",
            "CREATE TABLE `placements` (`itemId` TEXT NOT NULL, `homeId` TEXT NOT NULL, " +
                "`itemType` TEXT NOT NULL, `profileId` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                "`className` TEXT NOT NULL, `column` INTEGER NOT NULL, `row` INTEGER NOT NULL, " +
                "`columnSpan` INTEGER NOT NULL, `rowSpan` INTEGER NOT NULL, `availability` TEXT NOT NULL, " +
                "PRIMARY KEY(`itemId`), " +
                "FOREIGN KEY(`homeId`) REFERENCES `homes`(`homeId`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`profileId`, `packageName`, `className`) " +
                "REFERENCES `app_references`(`profileId`, `packageName`, `className`) " +
                "ON UPDATE NO ACTION ON DELETE NO ACTION)",
            "CREATE INDEX `index_placements_homeId` ON `placements` (`homeId`)",
            "CREATE INDEX `index_placements_profileId_packageName_className` " +
                "ON `placements` (`profileId`, `packageName`, `className`)",
            "CREATE TABLE `shortcut_bindings` (`tileId` TEXT NOT NULL, `pinnedShortcutIds` TEXT NOT NULL, " +
                "PRIMARY KEY(`tileId`), FOREIGN KEY(`tileId`) REFERENCES `placements`(`itemId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX `index_shortcut_bindings_tileId` ON `shortcut_bindings` (`tileId`)",
            "CREATE TABLE `widget_bindings` (`itemId` TEXT NOT NULL, `appWidgetId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`itemId`), FOREIGN KEY(`itemId`) REFERENCES `placements`(`itemId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX `index_widget_bindings_itemId` ON `widget_bindings` (`itemId`)",
            "CREATE TABLE `tile_album_bindings` (`tileId` TEXT NOT NULL, `bucketId` TEXT NOT NULL, " +
                "`albumName` TEXT NOT NULL, PRIMARY KEY(`tileId`), " +
                "FOREIGN KEY(`tileId`) REFERENCES `placements`(`itemId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX `index_tile_album_bindings_tileId` ON `tile_album_bindings` (`tileId`)",
            "INSERT INTO homes VALUES ('home')",
            "INSERT INTO app_references VALUES ('0', 'org.example', 'org.example.Home')",
            "INSERT INTO placements VALUES ('a1', 'home', 'APP', '0', 'org.example', 'org.example.Home', " +
                "1, 0, 2, 2, 'AVAILABLE')",
            "INSERT INTO placements VALUES ('w1', 'home', 'WIDGET', '0', 'org.example', 'org.example.Home', " +
                "0, 2, 4, 2, 'UNAVAILABLE')",
            "INSERT INTO shortcut_bindings VALUES ('a1', '[]')",
            "INSERT INTO widget_bindings VALUES ('w1', 42)",
            "INSERT INTO tile_album_bindings VALUES ('a1', 'bucket-9', 'Camera')",
            "PRAGMA user_version = 4",
        ).forEach(raw::execSQL)
        raw.close()
    }

    private companion object {
        const val DB_NAME = "folder-persistence-test.db"
    }
}
