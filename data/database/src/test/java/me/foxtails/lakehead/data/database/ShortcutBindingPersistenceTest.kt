package me.foxtails.lakehead.data.database

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
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
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for shortcut binding persistence: schema, migration, roundtrip,
 * cascade delete, upsert semantics, empty/null handling, and no-op delete.
 */
@RunWith(RobolectricTestRunner::class)
class ShortcutBindingPersistenceTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PlacementDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao get() = database.placementDao()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun schemaVersionIsTwo() {
        assertEquals(2, DatabaseSchema.VERSION)
        assertEquals(DatabaseSchema.VERSION, database.openHelper.readableDatabase.version)
    }

    @Test
    fun migration12CreatesShortcutBindingsTableWithCorrectColumnsAndKeys() {
        val freshDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PlacementDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            assertEquals(
                setOf("tileId", "pinnedShortcutIds"),
                columns(freshDb, "shortcut_bindings"),
            )
            assertEquals(
                setOf("tileId->placements.itemId"),
                foreignKeyEdges(freshDb, "shortcut_bindings"),
            )
            assertEquals(setOf("tileId"), primaryKeyColumns(freshDb, "shortcut_bindings"))
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun roundTripPreservesOrderAndAllFields() {
        seedPlacement("tile-1")
        val ref1 = ShortcutRef("com.a", ProfileId("p1"), "s1")
        val ref2 = ShortcutRef("com.b", ProfileId("p2"), "s2")
        val ref3 = ShortcutRef("com.c", ProfileId("p1"), "s3")
        val binding = TileBinding(ItemId("tile-1"), listOf(ref1, ref2, ref3))

        dao.upsertShortcutBinding(binding.toEntity())

        val loaded = dao.shortcutBinding("tile-1")!!.toDomain()
        assertEquals(binding, loaded)
        assertEquals(listOf(ref1, ref2, ref3), loaded.pinnedShortcuts)
    }

    @Test
    fun cascadeDeletesBindingWhenPlacementIsRemoved() {
        seedPlacement("tile-cascade")
        val binding = TileBinding(
            ItemId("tile-cascade"),
            listOf(ShortcutRef("com.a", ProfileId("p"), "s")),
        )
        dao.upsertShortcutBinding(binding.toEntity())
        assertEquals(1, dao.shortcutBindingCount())

        dao.deletePlacement("tile-cascade")

        assertNull(dao.shortcutBinding("tile-cascade"))
        assertEquals(0, dao.shortcutBindingCount())
    }

    @Test
    fun upsertReplacesExistingBindingOnSameTileId() {
        seedPlacement("tile-upsert")
        val original = TileBinding(
            ItemId("tile-upsert"),
            listOf(ShortcutRef("com.a", ProfileId("p"), "s1")),
        )
        dao.upsertShortcutBinding(original.toEntity())
        assertEquals(1, dao.shortcutBindingCount())

        val updated = TileBinding(
            ItemId("tile-upsert"),
            listOf(
                ShortcutRef("com.b", ProfileId("p"), "s2"),
                ShortcutRef("com.c", ProfileId("p"), "s3"),
            ),
        )
        dao.upsertShortcutBinding(updated.toEntity())
        assertEquals(1, dao.shortcutBindingCount())

        val loaded = dao.shortcutBinding("tile-upsert")!!.toDomain()
        assertEquals(updated, loaded)
    }

    @Test
    fun emptyPinnedShortcutsRoundTripsAsEmptyList() {
        seedPlacement("tile-empty")
        val binding = TileBinding(ItemId("tile-empty"), emptyList())

        dao.upsertShortcutBinding(binding.toEntity())

        val loaded = dao.shortcutBinding("tile-empty")!!.toDomain()
        assertEquals(binding, loaded)
        assertEquals(emptyList<ShortcutRef>(), loaded.pinnedShortcuts)
    }

    @Test
    fun loadReturnsNullForNonexistentTileId() {
        assertNull(dao.shortcutBinding("nonexistent"))
    }

    @Test
    fun deleteReturnsZeroForNonexistentTileId() {
        assertEquals(0, dao.deleteShortcutBinding("nonexistent"))
    }

    @Test
    fun deleteRemovesBindingAndReturnsOne() {
        seedPlacement("tile-del")
        dao.upsertShortcutBinding(
            TileBinding(
                ItemId("tile-del"),
                listOf(ShortcutRef("com.a", ProfileId("p"), "s")),
            ).toEntity(),
        )
        assertEquals(1, dao.shortcutBindingCount())
        assertEquals(1, dao.deleteShortcutBinding("tile-del"))
        assertNull(dao.shortcutBinding("tile-del"))
        assertEquals(0, dao.shortcutBindingCount())
    }

    @Test
    fun multipleBindingsForDifferentTilesAreIndependent() {
        seedPlacement("tile-a")
        seedPlacement("tile-b")
        val bindingA = TileBinding(
            ItemId("tile-a"),
            listOf(ShortcutRef("com.a", ProfileId("p"), "s1")),
        )
        val bindingB = TileBinding(
            ItemId("tile-b"),
            listOf(ShortcutRef("com.b", ProfileId("p"), "s2")),
        )
        dao.upsertShortcutBinding(bindingA.toEntity())
        dao.upsertShortcutBinding(bindingB.toEntity())
        assertEquals(2, dao.shortcutBindingCount())

        dao.deleteShortcutBinding("tile-a")
        assertNull(dao.shortcutBinding("tile-a"))
        assertEquals(bindingB, dao.shortcutBinding("tile-b")!!.toDomain())
    }

    @Test
    fun persistedEntitiesContainNoFrameworkOrPixelFields() {
        val persistedClasses = listOf(
            ShortcutBindingEntity::class.java,
            TileBinding::class.java,
            ShortcutRef::class.java,
        )
        persistedClasses.forEach { type ->
            type.declaredFields.filterNot { it.isSynthetic }.forEach { field ->
                assertEquals(
                    "${type.simpleName}.${field.name} uses ${field.type.name}",
                    false, field.type.name.startsWith("android.")
                )
                assertEquals(
                    "${type.simpleName}.${field.name} uses ${field.type.name}",
                    false, field.type.name.startsWith("androidx.compose.")
                )
                assertEquals(
                    "${type.simpleName}.${field.name} contains 'pixel'",
                    false, field.name.contains("pixel", ignoreCase = true)
                )
            }
        }
    }

    // --- helpers ---

    private fun seedPlacement(itemId: String) {
        val identity = AppIdentity(
            ProfileId("work"),
            ComponentIdentity("org.example", "org.example.Home"),
        )
        if (dao.home("home-main") == null) dao.insertHome(HomeEntity("home-main"))
        if (dao.appReference("work", "org.example", "org.example.Home") == null) {
            dao.insertAppReference(identity.toEntity())
        }
        dao.insertPlacement(
            AppPlacement(
                HomeId("home-main"), ItemId(itemId), identity,
                LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
            ).toEntity(),
        )
    }

    private fun columns(db: PlacementDatabase, table: String): Set<String> =
        db.query(SimpleSQLiteQuery("PRAGMA table_info(`$table`)")).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun primaryKeyColumns(db: PlacementDatabase, table: String): Set<String> =
        db.query(SimpleSQLiteQuery("PRAGMA table_info(`$table`)")).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
            }
        }

    private fun foreignKeyEdges(db: PlacementDatabase, table: String): Set<String> =
        db.query(SimpleSQLiteQuery("PRAGMA foreign_key_list(`$table`)")).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add("${cursor.getString(cursor.getColumnIndexOrThrow("from"))}->" +
                        "${cursor.getString(cursor.getColumnIndexOrThrow("table"))}." +
                        cursor.getString(cursor.getColumnIndexOrThrow("to")))
                }
            }
        }
}