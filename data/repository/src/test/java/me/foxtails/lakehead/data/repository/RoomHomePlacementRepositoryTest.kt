package me.foxtails.lakehead.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.data.database.HomeEntity
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.database.toEntity
import me.foxtails.lakehead.data.database.toDomain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomHomePlacementRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), PlacementDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao get() = database.placementDao()
    private val repository: HomePlacementRepository = RoomHomePlacementRepository(database)

    @After fun closeDatabase() = database.close()

    @Test fun loadIsHomeScopedStableAndRetainsUnavailableAndUnknownDoesNotCreate() {
        seedHome("one")
        seedHome("two")
        val oneFirst = placement("one", "z-first", 0, available = ItemAvailability.UNAVAILABLE)
        val oneSecond = placement("one", "a-second", 1)
        val other = placement("two", "other", 0)
        seed(oneFirst)
        seed(oneSecond)
        seed(other)

        assertEquals(listOf(oneFirst, oneSecond), (repository.load(oneFirst.homeId, 6) as LoadResult.Loaded).placements)
        assertEquals(listOf(other), (repository.load(other.homeId, 6) as LoadResult.Loaded).placements)
        val before = counts()
        assertEquals(LoadResult.NotFound, repository.load(HomeId("missing"), 6))
        assertEquals(before, counts())
    }

    @Test fun placeAcceptedPersistsProfileAwareAvailablePlacementAndRejectedRequestsWriteNothing() {
        val home = HomeId("home")
        seedHome(home.value)
        val existing = placement(home.value, "existing", 0)
        seed(existing)
        val candidateId = ItemId("new")
        val identity = identity("work", "org.new")
        val rejectedSnapshot = snapshot()

        assertRejected(repository.place(home, candidateId, identity, LogicalPosition(0, 0), LogicalSpan(1, 1), 6), GridRules.Rejection.COLLISION)
        assertEquals(rejectedSnapshot, snapshot())
        val genericId = ItemId("generic")
        val genericIdentity = identity("work", "org.generic")
        assertTrue(repository.place(home, genericId, genericIdentity, LogicalPosition(1, 0), LogicalSpan(3, 1), 6) is MutationResult.Accepted)
        assertEquals(RemoveResult.Removed, repository.remove(home, genericId))
        val postGenericSnapshot = snapshot()
        assertRejected(repository.place(home, candidateId, identity, LogicalPosition(6, 0), LogicalSpan(1, 1), 6), GridRules.Rejection.OUT_OF_BOUNDS)
        assertEquals(postGenericSnapshot, snapshot())

        val result = repository.place(home, candidateId, identity, LogicalPosition(2, 0), LogicalSpan(1, 1), 6)
        assertTrue(result is MutationResult.Accepted)
        val persisted = dao.placement(candidateId.value)!!.toDomain()
        assertEquals(identity, persisted.appIdentity)
        assertEquals(LogicalPosition(2, 0), persisted.position)
        assertEquals(ItemAvailability.AVAILABLE, persisted.availability)
    }

    @Test fun moveAndResizeDelegateRejectionAndPersistOnlyTarget() {
        val home = "home"
        seedHome(home)
        val target = placement(home, "target", 0)
        val neighbor = placement(home, "neighbor", 2)
        seed(target)
        seed(neighbor)
        val initial = snapshot()
        assertRejected(repository.move(target.homeId, target.itemId, LogicalPosition(2, 0), 6), GridRules.Rejection.COLLISION)
        assertEquals(initial, snapshot())
        assertRejected(repository.move(target.homeId, target.itemId, LogicalPosition(6, 0), 6), GridRules.Rejection.OUT_OF_BOUNDS)
        assertEquals(initial, snapshot())
        assertTrue(repository.move(target.homeId, target.itemId, LogicalPosition(1, 0), 6) is MutationResult.Accepted)
        assertEquals(LogicalPosition(1, 0), dao.placement(target.itemId.value)!!.toDomain().position)
        assertEquals(neighbor, dao.placement(neighbor.itemId.value)!!.toDomain())

        val beforeResize = snapshot()
        assertRejected(repository.resize(target.homeId, target.itemId, LogicalSpan(3, 1), 6), GridRules.Rejection.COLLISION)
        assertEquals(beforeResize, snapshot())
        assertRejected(repository.resize(target.homeId, target.itemId, LogicalSpan(2, 1), 6), GridRules.Rejection.COLLISION)
        assertEquals(beforeResize, snapshot())
        assertRejected(repository.resize(target.homeId, target.itemId, LogicalSpan(4, 2), 4), GridRules.Rejection.OUT_OF_BOUNDS)
        assertEquals(beforeResize, snapshot())
        assertRejected(repository.resize(target.homeId, target.itemId, LogicalSpan(2, 1), 6), GridRules.Rejection.COLLISION)
        assertTrue(repository.resize(target.homeId, target.itemId, LogicalSpan(1, 2), 6) is MutationResult.Accepted)
        assertEquals(LogicalSpan(1, 2), dao.placement(target.itemId.value)!!.toDomain().span)
    }

    @Test fun acceptedMutationNormalizesOtherRowsAndReturnsFinalPlacements() {
        val home = HomeId("home")
        seedHome(home.value)
        val distant = placement(home.value, "distant", 0).copy(position = LogicalPosition(0, 10))
        seed(distant)

        val result = repository.place(
            home, ItemId("new"), identity(), LogicalPosition(1, 0), LogicalSpan(1, 1), 6,
        ) as MutationResult.Accepted

        assertEquals(LogicalPosition(0, 3), dao.placement(distant.itemId.value)!!.toDomain().position)
        assertEquals(LogicalPosition(0, 3), result.gridResult.placements
            .first { it.id == distant.itemId }.position.let { LogicalPosition(it.column, it.row) })
        val again = repository.load(home, 6) as LoadResult.Loaded
        assertEquals(LogicalPosition(0, 3), again.placements.first { it.itemId == distant.itemId }.position)
    }

    @Test fun moveNormalizesExcessiveGapWithoutChangingShiftedFieldsOrOtherHome() {
        val home = HomeId("home")
        val otherHome = HomeId("other")
        seedHome(home.value)
        seedHome(otherHome.value)
        val target = placement(home.value, "move-target", 0)
        val shifted = placement(home.value, "move-shifted", 2, ItemAvailability.UNAVAILABLE).copy(
            position = LogicalPosition(2, 10),
            span = LogicalSpan(2, 2),
        )
        val other = placement(otherHome.value, "other-item", 1).copy(position = LogicalPosition(1, 10))
        seed(target)
        seed(shifted)
        seed(other)

        assertTrue(repository.move(home, target.itemId, LogicalPosition(1, 1), 6) is MutationResult.Accepted)

        assertEquals(shifted.copy(position = LogicalPosition(2, 4)), dao.placement(shifted.itemId.value)!!.toDomain())
        assertEquals(other, dao.placement(other.itemId.value)!!.toDomain())
    }

    @Test fun resizeNormalizesExcessiveGapWithoutChangingShiftedFieldsOrOtherHome() {
        val home = HomeId("home")
        val otherHome = HomeId("other")
        seedHome(home.value)
        seedHome(otherHome.value)
        val target = placement(home.value, "resize-target", 0)
        val shifted = placement(home.value, "resize-shifted", 3, ItemAvailability.UNAVAILABLE).copy(
            position = LogicalPosition(3, 10),
            span = LogicalSpan(2, 2),
        )
        val other = placement(otherHome.value, "other-item", 1).copy(position = LogicalPosition(1, 10))
        seed(target)
        seed(shifted)
        seed(other)

        assertTrue(repository.resize(home, target.itemId, LogicalSpan(2, 2), 6) is MutationResult.Accepted)

        assertEquals(shifted.copy(position = LogicalPosition(3, 4)), dao.placement(shifted.itemId.value)!!.toDomain())
        assertEquals(other, dao.placement(other.itemId.value)!!.toDomain())
    }

    @Test fun removeNormalizesExcessiveGapWithoutChangingShiftedFieldsOrOtherHome() {
        val home = HomeId("home")
        val otherHome = HomeId("other")
        seedHome(home.value)
        seedHome(otherHome.value)
        val anchor = placement(home.value, "remove-anchor", 0)
        val target = placement(home.value, "remove-target", 1).copy(position = LogicalPosition(1, 4))
        val shifted = placement(home.value, "remove-shifted", 2, ItemAvailability.UNAVAILABLE).copy(
            position = LogicalPosition(2, 10),
            span = LogicalSpan(2, 2),
        )
        val other = placement(otherHome.value, "other-item", 1).copy(position = LogicalPosition(1, 10))
        seed(anchor)
        seed(target)
        seed(shifted)
        seed(other)

        assertEquals(RemoveResult.Removed, repository.remove(home, target.itemId))

        assertEquals(shifted.copy(position = LogicalPosition(2, 3)), dao.placement(shifted.itemId.value)!!.toDomain())
        assertEquals(other, dao.placement(other.itemId.value)!!.toDomain())
    }

    @Test fun unrelatedNeighborOperationsRetainUnavailablePlacementAndRowPosition() {
        val home = "home"
        seedHome(home)
        val unavailable = placement(home, "unavailable", 0, available = ItemAvailability.UNAVAILABLE)
        val neighbor = placement(home, "neighbor", 2)
        seed(unavailable)
        seed(neighbor)
        val originalUnavailableRowPosition = placementRowPosition(unavailable.itemId)

        assertTrue(repository.move(homeId = unavailable.homeId, itemId = neighbor.itemId,
            position = LogicalPosition(3, 0), columns = 6) is MutationResult.Accepted)
        assertTrue(repository.resize(unavailable.homeId, neighbor.itemId, LogicalSpan(2, 1), 6) is MutationResult.Accepted)
        assertEquals(RemoveResult.Removed, repository.remove(unavailable.homeId, neighbor.itemId))

        assertEquals(unavailable, dao.placement(unavailable.itemId.value)!!.toDomain())
        assertEquals(originalUnavailableRowPosition, placementRowPosition(unavailable.itemId))
    }

    @Test fun removeIsScopedAndNeverDeletesHomeOrSharedAppReference() {
        val firstHome = HomeId("first")
        val secondHome = HomeId("second")
        seedHome(firstHome.value)
        seedHome(secondHome.value)
        val first = placement(firstHome.value, "first-item", 0)
        val second = placement(secondHome.value, "second-item", 0)
        seed(first)
        seed(second)
        assertEquals(RemoveResult.WrongHome, repository.remove(secondHome, first.itemId))
        assertEquals(first, dao.placement(first.itemId.value)!!.toDomain())
        assertEquals(RemoveResult.Removed, repository.remove(firstHome, first.itemId))
        assertEquals(null, dao.placement(first.itemId.value))
        assertEquals(second, dao.placement(second.itemId.value)!!.toDomain())
        assertEquals(2, dao.homeCount())
        assertEquals(1, dao.appReferenceCount())
        assertEquals(RemoveResult.NotFound, repository.remove(firstHome, first.itemId))
        assertEquals(RemoveResult.NotFound, repository.remove(HomeId("missing"), second.itemId))
    }

    @Test fun replacementKeepsOrderAndRollbackRestoresAllRows() {
        val home = HomeId("home")
        seedHome(home.value)
        val before = placement(home.value, "before", 0)
        val replaced = placement(home.value, "replaced", 1)
        val after = placement(home.value, "after", 2)
        seed(before)
        seed(replaced)
        seed(after)
        val replacementId = ItemId("replacement")
        val identity = identity("work", "org.replacement")
        assertTrue(repository.place(home, replacementId, identity, LogicalPosition(1, 0), LogicalSpan(1, 1), 6, replaced.itemId) is MutationResult.Accepted)
        val loaded = (repository.load(home, 6) as LoadResult.Loaded).placements
        assertEquals(listOf(before.itemId, replacementId, after.itemId), loaded.map { it.itemId })
        assertEquals(null, dao.placement(replaced.itemId.value))
        assertEquals(identity, loaded[1].appIdentity)

        val fresh = placement(home.value, "rollback-target", 3)
        seed(fresh)
        val rollbackBefore = snapshot()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_placement_update BEFORE UPDATE ON placements " +
                "BEGIN SELECT RAISE(ABORT, 'forced repository write failure'); END",
        )
        try {
            org.junit.Assert.assertThrows(SQLiteConstraintException::class.java) {
                repository.place(home, ItemId("rollback-new"), identity("work", "org.rollback"),
                    LogicalPosition(3, 0), LogicalSpan(1, 1), 6, fresh.itemId)
            }
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_placement_update")
        }
        assertEquals(rollbackBefore, snapshot())
    }

    @Test fun replacementLookupFailuresGridRejectionsAndWrongHomeDoNotWrite() {
        val first = HomeId("first")
        val second = HomeId("second")
        seedHome(first.value)
        seedHome(second.value)
        val target = placement(first.value, "target", 0)
        val blocker = placement(first.value, "blocker", 2)
        val other = placement(second.value, "other", 0)
        seed(target)
        seed(blocker)
        seed(other)
        val before = snapshot()
        val newId = ItemId("new")
        assertEquals(
            MutationResult.NotFound,
            repository.place(HomeId("missing"), newId, identity(), LogicalPosition(1, 0), LogicalSpan(1, 1), 6),
        )
        assertEquals(before, snapshot())
        assertEquals(MutationResult.NotFound, repository.place(first, newId, identity(), LogicalPosition(1, 0), LogicalSpan(1, 1), 6, ItemId("missing")))
        assertEquals(before, snapshot())
        assertEquals(MutationResult.WrongHome, repository.place(first, newId, identity(), LogicalPosition(1, 0), LogicalSpan(1, 1), 6, other.itemId))
        assertEquals(before, snapshot())
        assertRejected(repository.place(first, newId, identity(), LogicalPosition(2, 0), LogicalSpan(1, 1), 6, target.itemId), GridRules.Rejection.COLLISION)
        assertEquals(before, snapshot())
        assertEquals(MutationResult.WrongHome, repository.move(first, other.itemId, LogicalPosition(3, 0), 6))
        assertEquals(MutationResult.WrongHome, repository.resize(first, other.itemId, LogicalSpan(2, 1), 6))
        assertEquals(MutationResult.WrongHome, repository.place(second, target.itemId, identity(), LogicalPosition(3, 0), LogicalSpan(1, 1), 6))
        assertEquals(MutationResult.ItemIdAlreadyExists, repository.place(first, target.itemId, identity(), LogicalPosition(3, 0), LogicalSpan(1, 1), 6))
        assertEquals(before, snapshot())
    }

    @Test fun identityCanAppearInDifferentHomesButItemIdCannotMigrate() {
        val one = HomeId("one")
        val two = HomeId("two")
        seedHome(one.value)
        seedHome(two.value)
        val same = identity("profile", "org.same")
        assertTrue(repository.place(one, ItemId("one-item"), same, LogicalPosition(0, 0), LogicalSpan(1, 1), 6) is MutationResult.Accepted)
        assertTrue(repository.place(two, ItemId("two-item"), same, LogicalPosition(0, 0), LogicalSpan(1, 1), 6) is MutationResult.Accepted)
        assertEquals(same, (repository.load(one, 6) as LoadResult.Loaded).placements.single().appIdentity)
        assertEquals(same, (repository.load(two, 6) as LoadResult.Loaded).placements.single().appIdentity)
        val before = snapshot()
        assertEquals(MutationResult.WrongHome, repository.place(two, ItemId("one-item"), identity(), LogicalPosition(1, 0), LogicalSpan(1, 1), 6))
        assertEquals(before, snapshot())
    }

    @Test fun publicContractAndResultsContainNoFrameworkTypes() {
        val publicTypes = listOf(
            HomePlacementRepository::class.java, LoadResult.Loaded::class.java,
            MutationResult.Accepted::class.java, MutationResult.Rejected::class.java,
            MutationResult::class.java, RemoveResult::class.java,
        )
        publicTypes.forEach { type ->
            (type.declaredFields.map { it.type } + type.methods.flatMap { method ->
                method.parameterTypes.toList() + listOf(method.returnType)
            }).forEach { dependency ->
                assertFalse(dependency.name.startsWith("android."))
                assertFalse(dependency.name.startsWith("androidx.room."))
                assertFalse(dependency.name.startsWith("androidx.compose."))
                assertFalse(dependency.name.contains("pixel", ignoreCase = true))
            }
        }
    }

    @Test fun ensureHomeCreatesRowSoLoadReturnsEmptyNotNotFound() {
        val homeId = HomeId("new-home")
        assertEquals(LoadResult.NotFound, repository.load(homeId, 6))
        assertTrue(repository.ensureHome(homeId))
        val loaded = repository.load(homeId, 6)
        assertTrue(loaded is LoadResult.Loaded)
        assertEquals(emptyList<AppPlacement>(), (loaded as LoadResult.Loaded).placements)
    }

    @Test fun ensureHomeOnExistingHomeIsIdempotent() {
        val homeId = HomeId("existing")
        seedHome(homeId.value)
        val beforeCount = dao.homeCount()
        assertTrue(repository.ensureHome(homeId))
        assertEquals(beforeCount, dao.homeCount())
        assertTrue(repository.ensureHome(homeId))
        assertEquals(beforeCount, dao.homeCount())
    }

    @Test fun ensureHomeThenPlaceSucceedsInsteadOfNotFound() {        val homeId = HomeId("fresh")
        val itemId = ItemId("item")
        val identity = identity()
        assertEquals(
            MutationResult.NotFound,
            repository.place(homeId, itemId, identity, LogicalPosition(0, 0), LogicalSpan(1, 1), 6),
        )
        assertTrue(repository.ensureHome(homeId))
        val result = repository.place(homeId, itemId, identity, LogicalPosition(0, 0), LogicalSpan(1, 1), 6)
        assertTrue(result is MutationResult.Accepted)
    }

    @Test fun loadNormalizesAndPersistsDistantValidPlacement() {
        seedHome("home")
        val near = placement("home", "near", 0)
        seed(near)
        val first = repository.load(HomeId("home"), 6) as LoadResult.Loaded
        assertEquals(listOf(near), first.placements)

        val far = AppPlacement(
            HomeId("home"), ItemId("far"), identity(),
            LogicalPosition(0, 16), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
        )
        seed(far)
        // Reload normalizes the excessive gap and persists the reduced extent.
        val second = repository.load(HomeId("home"), 6) as LoadResult.Loaded
        assertEquals(listOf(near.itemId, far.itemId), second.placements.map { it.itemId })
        assertEquals(LogicalPosition(0, 0), second.placements[0].position)
        assertEquals(LogicalPosition(0, 3), second.placements[1].position)
        assertEquals(LogicalPosition(0, 3), dao.placement(far.itemId.value)!!.toDomain().position)
    }

    @Test
    fun highRowPlacementMoveResizePreserveRowsAndAuthoritativeResults() {
        val home = HomeId("high-row")
        seedHome(home.value)
        val itemId = ItemId("high-row-item")

        val placed = repository.place(
            home,
            itemId,
            identity(),
            LogicalPosition(2, 5_000),
            LogicalSpan(2, 2),
            6,
        ) as MutationResult.Accepted
        assertEquals(LogicalPosition(2, 5_000), placed.gridResult.placements.single { it.id == itemId }.let { LogicalPosition(it.position.column, it.position.row) })
        assertEquals(LogicalPosition(2, 5_000), (repository.load(home, 6) as LoadResult.Loaded).placements.single().position)

        val moved = repository.move(home, itemId, LogicalPosition(2, 6_000), 6) as MutationResult.Accepted
        assertEquals(LogicalPosition(2, 6_000), moved.gridResult.placements.single { it.id == itemId }.let { LogicalPosition(it.position.column, it.position.row) })
        assertEquals(LogicalPosition(2, 6_000), (repository.load(home, 6) as LoadResult.Loaded).placements.single().position)

        val resized = repository.resize(home, itemId, LogicalSpan(2, 3), 6) as MutationResult.Accepted
        assertEquals(LogicalPosition(2, 6_000), resized.gridResult.placements.single { it.id == itemId }.let { LogicalPosition(it.position.column, it.position.row) })
        assertEquals(LogicalSpan(2, 3), resized.gridResult.placements.single { it.id == itemId }.span.let { LogicalSpan(it.columns, it.rows) })
        val reloaded = (repository.load(home, 6) as LoadResult.Loaded).placements.single()
        assertEquals(LogicalPosition(2, 6_000), reloaded.position)
        assertEquals(LogicalSpan(2, 3), reloaded.span)
    }

    private fun seedHome(id: String) = dao.insertHome(HomeEntity(id))

    private fun seed(value: AppPlacement) {
        if (dao.home(value.homeId.value) == null) seedHome(value.homeId.value)
        if (dao.appReference(value.appIdentity.profileId.value, value.appIdentity.component.packageName,
                value.appIdentity.component.className) == null
        ) dao.insertAppReference(value.appIdentity.toEntity())
        dao.insertPlacement(value.toEntity())
    }

    private fun snapshot(): List<String> = buildList {
        addAll(tableRows("homes", "SELECT * FROM homes ORDER BY homeId"))
        addAll(tableRows(
            "app_references",
            "SELECT * FROM app_references ORDER BY profileId, packageName, className",
        ))
        addAll(tableRows("placements", "SELECT * FROM placements ORDER BY rowid"))
    }

    private fun tableRows(table: String, sql: String): List<String> =
        database.query(SimpleSQLiteQuery(sql)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add("$table:${(0 until cursor.columnCount).joinToString("|") { index -> cursor.getString(index) }}")
                }
            }
        }

    private fun placementRowPosition(itemId: ItemId): Long? =
        database.query(SimpleSQLiteQuery(
            "SELECT rowid FROM placements WHERE itemId = ?",
            arrayOf(itemId.value),
        )).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    private fun counts() = listOf(dao.homeCount(), dao.appReferenceCount(), dao.placementCount())

    private fun assertRejected(result: MutationResult, rejection: GridRules.Rejection) {
        assertTrue(result is MutationResult.Rejected)
        assertEquals(rejection, (result as MutationResult.Rejected).gridResult.reason)
    }

    private fun placement(home: String, id: String, column: Int, available: ItemAvailability = ItemAvailability.AVAILABLE) =
        AppPlacement(HomeId(home), ItemId(id), identity(), LogicalPosition(column, 0), LogicalSpan(1, 1), available)

    private fun identity(profile: String = "work", packageName: String = "org.example") =
        AppIdentity(ProfileId(profile), ComponentIdentity(packageName, "$packageName.Home"))
}
