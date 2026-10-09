package me.foxtails.lakehead.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.TileAlbumBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = AppIdentity(ProfileId("0"), ComponentIdentity("org.example", "org.example.Home"))
    private var database: PlacementDatabase? = null

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migration3To4_keepsV3RowsAndAddsAlbumBindings() {
        createVersion3Database()
        val db = openMigrated()
        val dao = db.placementDao()

        val kept = dao.placement("a1")!!.toDomain()
        assertEquals(
            AppPlacement(
                HomeId("home"), ItemId("a1"), app, LogicalPosition(1, 0), LogicalSpan(1, 1),
                ItemAvailability.AVAILABLE,
            ),
            kept,
        )
        assertEquals(0, dao.albumBindingCount())

        insertAlbum(dao, "a1", AlbumRef("bucket-9", "Camera"))
        assertEquals(
            TileAlbumBinding(ItemId("a1"), AlbumRef("bucket-9", "Camera")),
            dao.albumBinding("a1")!!.toDomain(),
        )
    }

    @Test
    fun albumRoundTrip_restoresBucketIdAndName() {
        val dao = openFresh().placementDao()
        insertAlbum(dao, "t1", AlbumRef("bucket-1", "Screenshots"))

        assertEquals(
            TileAlbumBinding(ItemId("t1"), AlbumRef("bucket-1", "Screenshots")),
            dao.albumBinding("t1")!!.toDomain(),
        )
    }

    @Test
    fun albumUpsert_replacesThePreviousAlbum() {
        val dao = openFresh().placementDao()
        insertAlbum(dao, "t1", AlbumRef("bucket-1", "Camera"))
        insertAlbum(dao, "t1", AlbumRef("bucket-2", "Downloads"))

        assertEquals(
            TileAlbumBinding(ItemId("t1"), AlbumRef("bucket-2", "Downloads")),
            dao.albumBinding("t1")!!.toDomain(),
        )
        assertEquals(1, dao.albumBindingCount())
    }

    @Test
    fun deletingThePlacement_cascadesTheAlbumBinding() {
        val dao = openFresh().placementDao()
        insertAlbum(dao, "t1", AlbumRef("bucket-1", "Camera"))

        dao.deletePlacement("t1")

        assertNull(dao.albumBinding("t1"))
    }

    @Test
    fun deletingTheAlbumBinding_keepsThePlacement() {
        val dao = openFresh().placementDao()
        insertAlbum(dao, "t1", AlbumRef("bucket-1", "Camera"))

        dao.deleteAlbumBinding("t1")

        assertNull(dao.albumBinding("t1"))
        dao.placement("t1")!!.toDomain()
    }

    private fun insertAlbum(dao: PlacementDao, id: String, album: AlbumRef) {
        if (dao.home("home") == null) dao.insertHome(HomeEntity("home"))
        if (dao.appReference("0", app.component.packageName, app.component.className) == null) {
            dao.insertAppReference(app.toEntity())
        }
        if (dao.placement(id) == null) {
            val tile = AppPlacement(
                HomeId("home"), ItemId(id), app, LogicalPosition(0, 0), LogicalSpan(2, 2),
                ItemAvailability.AVAILABLE,
            )
            dao.insertPlacement(tile.toEntity())
        }
        dao.upsertAlbumBinding(TileAlbumBinding(ItemId(id), album).toEntity())
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
            )
            .allowMainThreadQueries().build().also { database = it }

    /** A database shaped exactly as exported schema 3.json, holding one app placement. */
    private fun createVersion3Database() {
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
            "INSERT INTO homes VALUES ('home')",
            "INSERT INTO app_references VALUES ('0', 'org.example', 'org.example.Home')",
            "INSERT INTO placements VALUES ('a1', 'home', 'APP', '0', 'org.example', 'org.example.Home', " +
                "1, 0, 1, 1, 'AVAILABLE')",
            "PRAGMA user_version = 3",
        ).forEach(raw::execSQL)
        raw.close()
    }

    private companion object {
        const val DB_NAME = "album-persistence-test.db"
    }
}
