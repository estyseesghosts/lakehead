package me.foxtails.lakehead

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.grid.PlacementRect
import me.foxtails.lakehead.core.grid.firstFreeRect
import me.foxtails.lakehead.core.grid.widgetDefaultSpan
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.WidgetProviderEntry
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import java.util.UUID

/** The host operations the add flow needs; implemented over the widget platform module. */
internal interface WidgetHostPort {
    fun allocateId(): Int
    fun deleteId(appWidgetId: Int)
    fun bindIfAllowed(appWidgetId: Int, provider: WidgetProviderEntry): Boolean
    fun needsConfigure(appWidgetId: Int): Boolean
}

/** Where the add flow stands after one step; the caller launches whatever UI the step names. */
internal sealed interface AddWidgetStep {
    /** The system must ask the user to allow binding this id. */
    data class NeedsBindConsent(val appWidgetId: Int) : AddWidgetStep

    /** The provider's configure activity must run for this id. */
    data class NeedsConfigure(val appWidgetId: Int) : AddWidgetStep

    data object Placed : AddWidgetStep

    /** The flow ended without a placement; the allocated id has been deleted. */
    data class Failed(val reason: String) : AddWidgetStep
}

/**
 * Add flow: allocate an id, bind it, configure if the provider asks, then place the widget
 * at its default size in the first free rectangle. Any cancel or failure deletes the id.
 */
internal class AddWidgetCoordinator(
    private val homeId: HomeId,
    private val repository: HomePlacementRepository,
    private val host: WidgetHostPort,
    private val columns: Int = 5,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun begin(provider: WidgetProviderEntry): AddWidgetStep = guarded(null) {
        val id = host.allocateId()
        guarded(id) {
            if (host.bindIfAllowed(id, provider)) afterBind(id, provider) else AddWidgetStep.NeedsBindConsent(id)
        }
    }

    /** The system consent prompt returned [granted]. */
    suspend fun onBindResult(appWidgetId: Int, provider: WidgetProviderEntry, granted: Boolean): AddWidgetStep =
        if (granted) guarded(appWidgetId) { afterBind(appWidgetId, provider) } else cancel(appWidgetId)

    /** The configure activity returned [ok]. */
    suspend fun onConfigureResult(appWidgetId: Int, provider: WidgetProviderEntry, ok: Boolean): AddWidgetStep =
        if (ok) guarded(appWidgetId) { place(appWidgetId, provider) } else cancel(appWidgetId)

    fun cancel(appWidgetId: Int): AddWidgetStep {
        host.deleteId(appWidgetId)
        return AddWidgetStep.Failed("cancelled")
    }

    private suspend fun afterBind(appWidgetId: Int, provider: WidgetProviderEntry): AddWidgetStep =
        if (host.needsConfigure(appWidgetId)) AddWidgetStep.NeedsConfigure(appWidgetId) else place(appWidgetId, provider)

    private suspend fun place(appWidgetId: Int, provider: WidgetProviderEntry): AddWidgetStep =
        withContext(ioDispatcher) {
            repository.ensureHome(homeId)
            val loaded = repository.load(homeId, columns) as? LoadResult.Loaded
                ?: return@withContext fail(appWidgetId, "home not found")
            val span = widgetDefaultSpan(
                provider.targetCellWidth,
                provider.targetCellHeight,
                provider.minWidthDp,
                provider.minHeightDp,
                columns,
            )
            val position = firstFreeRect(loaded.placements.map { PlacementRect(it.position, it.span) }, columns, span)
            val result = repository.placeWidget(
                homeId, ItemId(UUID.randomUUID().toString()), provider.provider, appWidgetId, position, span, columns,
            )
            if (result is MutationResult.Accepted) AddWidgetStep.Placed else fail(appWidgetId, "placement rejected")
        }

    private fun fail(appWidgetId: Int, reason: String): AddWidgetStep {
        host.deleteId(appWidgetId)
        return AddWidgetStep.Failed(reason)
    }

    /** Runs [block]; an unexpected throw deletes [appWidgetId] (when allocated) and ends the flow. */
    private suspend fun guarded(appWidgetId: Int?, block: suspend () -> AddWidgetStep): AddWidgetStep = try {
        block()
    } catch (cancelled: CancellationException) {
        appWidgetId?.let(host::deleteId)
        throw cancelled
    } catch (throwable: Throwable) {
        appWidgetId?.let(host::deleteId)
        AddWidgetStep.Failed(throwable.message ?: "unexpected error")
    }
}
