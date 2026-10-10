package me.foxtails.lakehead.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.WidgetPlacement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = AppIdentity(ProfileId("0"), ComponentIdentity("com.example", "com.example.Clock"))
    private val app = AppIdentity(ProfileId("0"), ComponentIdentity("org.example", "org.example.Home"))
    private var database: PlacementDatabase? = null

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migration2To3_keepsV2RowsAndAddsWidgetBindings() {
        createVersion2Database()
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
        assertEquals(0, dao.allAppWidgetIds().size)

        insertWidget(dao, "w1", appWidgetId = 7)
        assertEquals(listOf(7), dao.allAppWidgetIds())
    }

    @Test
    fun widgetRoundTrip_restoresProviderProfileAndAppWidgetId() {
        val dao = openFresh().placementDao()
        insertWidget(dao, "w1", appWidgetId = 42)

        val loaded = dao.placement("w1")!!.toDomain(dao.widgetBinding("w1"))
        assertEquals(
            WidgetPlacement(
                HomeId("home"), ItemId("w1"), provider, 42, LogicalPosition(0, 1), LogicalSpan(4, 2),
                ItemAvailability.AVAILABLE,
            ),
            loaded,
        )
    }

    @Test
    fun widgetRowWithoutBinding_isRejectedNotReadAsAnApp() {
        val dao = openFresh().placementDao()
        insertWidget(dao, "w1", appWidgetId = 1)
        dao.deleteWidgetBinding("w1")

        assertThrows(IllegalArgumentException::class.java) { dao.placement("w1")!!.toDomain(null) }
    }

    @Test
    fun deletingThePlacement_cascadesTheWidgetBinding() {
        val dao = openFresh().placementDao()
        insertWidget(dao, "w1", appWidgetId = 5)

        dao.deletePlacement("w1")

        assertNull(dao.widgetBinding("w1"))
    }

    private fun insertWidget(dao: PlacementDao, id: String, appWidgetId: Int) {
        if (dao.home("home") == null) dao.insertHome(HomeEntity("home"))
        if (dao.appReference("0", provider.component.packageName, provider.component.className) == null) {
            dao.insertAppReference(provider.toEntity())
        }
        val widget = WidgetPlacement(
            HomeId("home"), ItemId(id), provider, appWidgetId, LogicalPosition(0, 1), LogicalSpan(4, 2),
            ItemAvailability.AVAILABLE,
        )
        dao.insertPlacement(widget.toEntity())
        dao.upsertWidgetBinding(widget.toBindingEntity())
    }

    private fun openFresh(): PlacementDatabase =
        Room.databaseBuilder(context, PlacementDatabase::class.java, DB_NAME)
            .allowMainThreadQueries().build().also { database = it }

    private fun openMigrated(): PlacementDatabase =
        Room.databaseBuilder(context, PlacementDatabase::class.java, DB_NAME)
            .addMigrations(PlacementDatabase.MIGRATION_1_2, PlacementDatabase.MIGRATION_2_3,
                PlacementDatabase.MIGRATION_3_4, PlacementDatabase.MIGRATION_4_5)
            .allowMainThreadQueries().build().also { database = it }

    /** A database shaped exactly as exported schema 2.json, holding one app placement. */
    private fun createVersion2Database() {
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
            "INSERT INTO homes VALUES ('home')",
            "INSERT INTO app_references VALUES ('0', 'org.example', 'org.example.Home')",
            "INSERT INTO placements VALUES ('a1', 'home', 'APP', '0', 'org.example', 'org.example.Home', " +
                "1, 0, 1, 1, 'AVAILABLE')",
            "PRAGMA user_version = 2",
        ).forEach(raw::execSQL)
        raw.close()
    }

    private companion object {
        const val DB_NAME = "widget-persistence-test.db"
    }
}
