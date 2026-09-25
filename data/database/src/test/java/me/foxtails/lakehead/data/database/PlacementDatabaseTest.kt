package me.foxtails.lakehead.data.database

import android.database.sqlite.SQLiteConstraintException
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlacementDatabaseTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PlacementDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao get() = database.placementDao()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun freshDatabaseCreatesVersionOneSchemaAndReferentialKeys() {
        assertEquals(1, DatabaseSchema.VERSION)
        assertEquals(DatabaseSchema.VERSION, database.openHelper.readableDatabase.version)
        assertEquals(
            setOf("homeId"),
            columns("homes"),
        )
        assertEquals(
            setOf("profileId", "packageName", "className"),
            columns("app_references"),
        )
        assertEquals(
            setOf(
                "itemId", "homeId", "itemType", "profileId", "packageName", "className",
                "column", "row", "columnSpan", "rowSpan", "availability",
            ),
            columns("placements"),
        )
        assertEquals(
            setOf("homeId->homes.homeId", "profileId->app_references.profileId",
                "packageName->app_references.packageName", "className->app_references.className"),
            foreignKeyEdges("placements"),
        )
        assertEquals(setOf("profileId", "packageName", "className"), primaryKeyColumns("app_references"))
        assertEquals(setOf("itemId"), primaryKeyColumns("placements"))

        val schema = java.io.File(
            "schemas/me.foxtails.lakehead.data.database.PlacementDatabase/1.json",
        ).readText()
        assertEquals(true, schema.contains("\"version\": 1"))
        listOf("homes", "app_references", "placements", "profileId", "packageName", "className")
            .forEach { assertEquals("missing exported schema token: $it", true, schema.contains(it)) }
    }

    @Test
    fun roundTripPreservesEveryDomainField() {
        val expected = placement()
        insertReferences(expected)
        dao.insertPlacement(expected.toEntity())

        assertEquals(expected, dao.placement(expected.itemId.value)!!.toDomain())
        assertEquals(expected.appIdentity, dao.appReference("work", "org.example", "org.example.Home")!!.toDomain())
    }

    @Test
    fun transactionRollbackLeavesNoPartialHomeAppOrPlacementRows() {
        val candidate = placement()
        assertThrows(IllegalStateException::class.java) {
            database.runInTransaction {
                dao.insertHome(HomeEntity(candidate.homeId.value))
                dao.insertAppReference(candidate.appIdentity.toEntity())
                dao.insertPlacement(candidate.toEntity())
                error("forced failure after all related writes")
            }
        }

        assertEquals(0, dao.homeCount())
        assertEquals(0, dao.appReferenceCount())
        assertEquals(0, dao.placementCount())
    }

    @Test
    fun missingHomeOrAppReferenceFailsWithoutCreatingPlacement() {
        val candidate = placement()
        assertThrows(SQLiteConstraintException::class.java) {
            dao.insertPlacement(candidate.toEntity())
        }
        dao.insertHome(HomeEntity(candidate.homeId.value))
        assertThrows(SQLiteConstraintException::class.java) {
            dao.insertPlacement(candidate.toEntity())
        }
        assertEquals(0, dao.placementCount())

        dao.insertAppReference(candidate.appIdentity.toEntity())
        dao.insertPlacement(candidate.toEntity())
        assertEquals(1, dao.placementCount())
    }

    @Test
    fun duplicateItemIdAndSameProfileAppIdentityAreRejectedButProfileSeparationIsPreserved() {
        val personal = placement(profile = "personal", item = "personal-item")
        val work = placement(profile = "work", item = "work-item")
        insertReferences(personal)
        insertReferences(work)
        dao.insertPlacement(personal.toEntity())
        dao.insertPlacement(work.toEntity())

        assertNotEquals(personal.appIdentity, work.appIdentity)
        assertEquals(personal.appIdentity.component, work.appIdentity.component)
        assertEquals(personal.appIdentity, dao.appReference("personal", "org.example", "org.example.Home")!!.toDomain())
        assertEquals(work.appIdentity, dao.appReference("work", "org.example", "org.example.Home")!!.toDomain())

        assertThrows(SQLiteConstraintException::class.java) {
            dao.insertAppReference(personal.appIdentity.toEntity())
        }
        assertThrows(SQLiteConstraintException::class.java) {
            dao.insertPlacement(personal.toEntity())
        }
    }

    @Test
    fun unavailablePlacementIsRetainedAndCanBeRestoredWithoutIdentityChange() {
        val available = placement()
        insertReferences(available)
        dao.insertPlacement(available.toEntity())

        assertEquals(1, dao.updateAvailability(available.itemId.value, ItemAvailability.UNAVAILABLE.name))
        val unavailable = dao.placement(available.itemId.value)!!.toDomain()
        assertEquals(ItemAvailability.UNAVAILABLE, unavailable.availability)
        assertEquals(available.homeId, unavailable.homeId)
        assertEquals(available.itemId, unavailable.itemId)
        assertEquals(available.appIdentity, unavailable.appIdentity)
        assertEquals(available.position, unavailable.position)
        assertEquals(available.span, unavailable.span)

        assertEquals(1, dao.updateAvailability(available.itemId.value, ItemAvailability.AVAILABLE.name))
        assertEquals(available, dao.placement(available.itemId.value)!!.toDomain())
        assertEquals(1, dao.placementCount())
    }

    @Test
    fun mapperRejectsUnsupportedBoundaryTypesAndAvailability() {
        val supported = placement().toEntity()
        assertEquals("APP", supported.itemType)
        assertEquals("AVAILABLE", supported.availability)
        assertEquals("UNAVAILABLE", supported.with(availability = "UNAVAILABLE").availability)

        assertThrows(IllegalArgumentException::class.java) {
            supported.with(itemType = "FOLDER").toDomain()
        }
        assertThrows(IllegalArgumentException::class.java) {
            supported.with(availability = "UNKNOWN").toDomain()
        }
    }

    @Test
    fun persistedContractContainsNoPlatformObjectsOrPixelFields() {
        val persistedClasses = listOf(
            HomeEntity::class.java,
            AppReferenceEntity::class.java,
            PlacementEntity::class.java,
            AppPlacement::class.java,
            AppIdentity::class.java,
        )
        persistedClasses.forEach { type ->
            assertNotNull(type)
            type.declaredFields.filterNot { it.isSynthetic }.forEach { field ->
                assertEquals(false, field.type.name.startsWith("android."))
                assertEquals(false, field.type.name.startsWith("androidx.compose."))
                assertEquals(false, field.name.contains("pixel", ignoreCase = true))
            }
        }
    }

    private fun insertReferences(placement: AppPlacement) {
        if (dao.home(placement.homeId.value) == null) dao.insertHome(HomeEntity(placement.homeId.value))
        if (dao.appReference(
                placement.appIdentity.profileId.value,
                placement.appIdentity.component.packageName,
                placement.appIdentity.component.className,
            ) == null
        ) {
            dao.insertAppReference(placement.appIdentity.toEntity())
        }
    }

    private fun columns(table: String): Set<String> = database.query(SimpleSQLiteQuery("PRAGMA table_info(`$table`)" )).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
    }

    private fun primaryKeyColumns(table: String): Set<String> = database.query(SimpleSQLiteQuery("PRAGMA table_info(`$table`)" )).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }
    }

    private fun foreignKeyEdges(table: String): Set<String> =
        database.query(SimpleSQLiteQuery("PRAGMA foreign_key_list(`$table`)" )).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add("${cursor.getString(cursor.getColumnIndexOrThrow("from"))}->" +
                        "${cursor.getString(cursor.getColumnIndexOrThrow("table"))}." +
                        cursor.getString(cursor.getColumnIndexOrThrow("to")))
                }
            }
        }

    private fun placement(profile: String = "work", item: String = "item-1") = AppPlacement(
        homeId = HomeId("home-main"),
        itemId = ItemId(item),
        appIdentity = AppIdentity(
            ProfileId(profile),
            ComponentIdentity("org.example", "org.example.Home"),
        ),
        position = LogicalPosition(5, 17),
        span = LogicalSpan(4, 2),
        availability = ItemAvailability.AVAILABLE,
    )

    private fun PlacementEntity.with(
        itemType: String = this.itemType,
        availability: String = this.availability,
    ) = PlacementEntity(
        itemId, homeId, itemType, profileId, packageName, className,
        column, row, columnSpan, rowSpan, availability,
    )
}
