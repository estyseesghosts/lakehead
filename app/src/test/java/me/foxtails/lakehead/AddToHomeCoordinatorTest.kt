package me.foxtails.lakehead

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LauncherAppEntry
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.RemoveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddToHomeCoordinatorTest {
    private val homeId = HomeId("home-main")
    private val profileA = ProfileId("personal")
    private val appA = AppIdentity(profileA, ComponentIdentity("com.example", "Main"))
    private val appB = AppIdentity(profileA, ComponentIdentity("com.example", "Other"))

    @Test
    fun addsNewAppToFirstFreeCell() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakePlacingRepository(
            initialPlacements = emptyList(),
            columns = 6,
            rows = 4096,
        )
        val coordinator = AddToHomeCoordinator(homeId, repository, 6, 4096, dispatcher)
        val result = coordinator.add(appA)
        assertTrue(result is AddToHomeResult.Added)
        assertEquals(LogicalPosition(0, 0), (result as AddToHomeResult.Added).position)
        assertEquals(1, repository.placedCount)
    }

    @Test
    fun returnsAlreadyOnHomeWhenIdentityIsPlaced() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val existing = AppPlacement(
            homeId, ItemId("existing"), appA,
            LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
        )
        val repository = FakePlacingRepository(
            initialPlacements = listOf(existing),
            columns = 6,
            rows = 4096,
        )
        val coordinator = AddToHomeCoordinator(homeId, repository, 6, 4096, dispatcher)
        val result = coordinator.add(appA)
        assertTrue(result is AddToHomeResult.AlreadyOnHome)
        assertEquals(0, repository.placedCount)
    }

    @Test
    fun returnsHomeFullWhenGridIsExhausted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // Fill a tiny 1x1 grid completely
        val existing = AppPlacement(
            homeId, ItemId("existing"), appB,
            LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
        )
        val repository = FakePlacingRepository(
            initialPlacements = listOf(existing),
            columns = 1,
            rows = 1,
        )
        val coordinator = AddToHomeCoordinator(homeId, repository, 1, 1, dispatcher)
        val result = coordinator.add(appA)
        assertTrue(result is AddToHomeResult.HomeFull)
    }

    @Test
    fun returnsErrorWhenHomeNotFound() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakePlacingRepository(
            initialPlacements = null, // NotFound
            columns = 6,
            rows = 4096,
        )
        val coordinator = AddToHomeCoordinator(homeId, repository, 6, 4096, dispatcher)
        val result = coordinator.add(appA)
        assertTrue(result is AddToHomeResult.Error)
    }

    @Test
    fun returnsErrorOnUnexpectedException() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ExplodingRepository()
        val coordinator = AddToHomeCoordinator(homeId, repository, 6, 4096, dispatcher)
        val result = coordinator.add(appA)
        assertTrue(result is AddToHomeResult.Error)
    }

    /**
     * Fake repository that supports place operations for testing the coordinator.
     *
     * [initialPlacements] of null simulates LoadResult.NotFound.
     */
    private class FakePlacingRepository(
        private val initialPlacements: List<AppPlacement>?,
        private val columns: Int,
        private val rows: Int,
    ) : HomePlacementRepository {
        private val placements = initialPlacements?.toMutableList() ?: mutableListOf()
        var placedCount = 0
            private set

        override fun load(homeId: HomeId, columns: Int, rows: Int): LoadResult =
            if (initialPlacements == null) LoadResult.NotFound
            else LoadResult.Loaded(placements.toList())

        override fun place(
            homeId: HomeId,
            itemId: ItemId,
            appIdentity: AppIdentity,
            position: LogicalPosition,
            span: LogicalSpan,
            columns: Int,
            rows: Int,
            replacingId: ItemId?,
        ): MutationResult {
            placedCount++
            val candidate = GridPlacement(itemId, GridPosition(position.column, position.row), GridSpan(span.columns, span.rows))
            val current = placements.map {
                GridPlacement(it.itemId, GridPosition(it.position.column, it.position.row), GridSpan(it.span.columns, it.span.rows))
            }
            val result = GridRules.place(current, candidate, columns, rows, replacingId)
            return when (result) {
                is GridRules.Result.Accepted -> {
                    val accepted = result.placements.first { it.id == itemId }
                    placements.add(
                        AppPlacement(
                            homeId, itemId, appIdentity,
                            LogicalPosition(accepted.position.column, accepted.position.row),
                            LogicalSpan(accepted.span.columns, accepted.span.rows),
                            ItemAvailability.AVAILABLE,
                        ),
                    )
                    MutationResult.Accepted(result)
                }
                is GridRules.Result.Rejected -> MutationResult.Rejected(result)
            }
        }

        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int, rows: Int): MutationResult =
            error("not implemented")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int, rows: Int): MutationResult =
            error("not implemented")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult =
            error("not implemented")
        override fun ensureHome(homeId: HomeId): Boolean = true
    }

    /** Repository that throws on load to test error wrapping. */
    private class ExplodingRepository : HomePlacementRepository {
        override fun load(homeId: HomeId, columns: Int, rows: Int): LoadResult =
            throw RuntimeException("boom")
        override fun place(
            homeId: HomeId, itemId: ItemId, appIdentity: AppIdentity,
            position: LogicalPosition, span: LogicalSpan, columns: Int, rows: Int, replacingId: ItemId?,
        ): MutationResult = error("not implemented")
        override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int, rows: Int): MutationResult =
            error("not implemented")
        override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int, rows: Int): MutationResult =
            error("not implemented")
        override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = error("not implemented")
        override fun ensureHome(homeId: HomeId): Boolean = true
    }
}