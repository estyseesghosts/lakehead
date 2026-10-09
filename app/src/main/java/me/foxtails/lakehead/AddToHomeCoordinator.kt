package me.foxtails.lakehead

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.grid.PlacementRect
import me.foxtails.lakehead.core.grid.firstFreeCell
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import java.util.UUID

/** Result of an attempt to add an app to the home screen. */
sealed interface AddToHomeResult {
    /** The app was placed at the given position. */
    data class Added(val position: LogicalPosition) : AddToHomeResult
    /** The app is already on the home screen. */
    data object AlreadyOnHome : AddToHomeResult
    /** No representable free 1×1 cell is available. */
    data object HomeFull : AddToHomeResult
    /** An unexpected error occurred during the add operation. */
    data class Error(val message: String, val cause: Throwable? = null) : AddToHomeResult
}

/**
 * Places an app on the home grid.
 *
 * Ensures the home exists, loads the current placements, checks for duplicates, finds the
 * first free 1×1 cell, and persists the new placement. All I/O runs on [ioDispatcher].
 */
internal class AddToHomeCoordinator(
    private val homeId: HomeId,
    private val repository: HomePlacementRepository,
    private val columns: Int = 5,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun add(appIdentity: AppIdentity): AddToHomeResult = withContext(ioDispatcher) {
        try {
            repository.ensureHome(homeId)

            val loadResult = repository.load(homeId, columns)
            if (loadResult !is LoadResult.Loaded) {
                return@withContext AddToHomeResult.Error("Home not found after ensureHome")
            }

            val placements = loadResult.placements
            if (placements.filterIsInstance<AppPlacement>().any { it.appIdentity == appIdentity }) {
                return@withContext AddToHomeResult.AlreadyOnHome
            }

            val occupied = placements.map { PlacementRect(it.position, it.span) }
            val maxRow = occupied.maxOfOrNull { rect ->
                (rect.position.row.toLong() + rect.span.rows.toLong())
                    .coerceAtLeast(0L)
            }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
            val position = firstFreeCell(occupied, columns, maxRow)
                ?: return@withContext AddToHomeResult.HomeFull

            val itemId = ItemId(UUID.randomUUID().toString())
            val span = LogicalSpan(1, 1)
            when (val result = repository.place(homeId, itemId, appIdentity, position, span, columns)) {
                is MutationResult.Accepted -> AddToHomeResult.Added(position)
                is MutationResult.Rejected -> AddToHomeResult.Error("Grid rejected placement")
                is MutationResult.NotFound -> AddToHomeResult.Error("Home disappeared during place")
                is MutationResult.WrongHome -> AddToHomeResult.Error("Home mismatch during place")
                is MutationResult.ItemIdAlreadyExists -> AddToHomeResult.Error("ItemId collision")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            AddToHomeResult.Error("Unexpected error", throwable)
        }
    }
}
