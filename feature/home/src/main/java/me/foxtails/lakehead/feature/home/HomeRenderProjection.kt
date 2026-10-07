package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.grid.GridVerticalLayout
import me.foxtails.lakehead.core.grid.GridViewportAdaptation
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability

/**
 * Pure projection from persisted placements plus caller-resolved metadata to render state.
 *
 * Every helper delegates column, extent, and window policy to `core:grid` and performs no
 * platform, data, Compose, or Android access. Column adaptation returns the
 * [GridViewportAdaptation.columnsFor] count unchanged; five is only the normal-phone reference
 * result, never a minimum or clamp.
 */
object HomeRenderProjection {
    /**
     * Delegates directly to [GridViewportAdaptation.columnsFor] and returns its positive count
     * unchanged.
     */
    fun adaptColumns(inputs: HomeViewportInputs): GridColumns =
        GridViewportAdaptation.columnsFor(
            viewportWidthPx = inputs.viewportWidthPx,
            horizontalInsetPx = inputs.horizontalInsetPx,
            horizontalPaddingPx = inputs.horizontalPaddingPx,
            columnGapPx = inputs.columnGapPx,
            minimumCellWidthPx = inputs.minimumCellWidthPx,
        )

    /**
     * Maps [AppPlacement] entries to [GridPlacement] and delegates only to
     * [GridVerticalLayout.measure]. No second extent policy is computed here.
     */
    fun measureExtent(
        placements: List<AppPlacement>,
        viewportRows: Long,
        workingMarginRows: Long,
    ): GridVerticalExtent {
        val gridPlacements = placements.map { placement ->
            GridPlacement(
                id = placement.itemId,
                position = GridPosition(
                    column = placement.position.column,
                    row = placement.position.row,
                ),
                span = GridSpan(
                    columns = placement.span.columns,
                    rows = placement.span.rows,
                ),
            )
        }
        return GridVerticalLayout.measure(
            placements = gridPlacements,
            viewportRows = viewportRows,
            workingMarginRows = workingMarginRows,
        )
    }

    /**
     * Joins caller-resolved [metadataByIdentity] onto [placements] by complete [AppIdentity].
     *
     * Order, identity, position, span, and availability are preserved exactly; nothing is sorted,
     * deduplicated, or omitted. A placement without metadata keeps its identity with null
     * label/icon and `isLaunchable = false`. An [ItemAvailability.UNAVAILABLE] placement keeps
     * any joined label/icon for display but is never launchable. Available placements with
     * joined label-only metadata remain launchable; an icon is optional.
     */
    fun resolveTiles(
        placements: List<AppPlacement>,
        metadataByIdentity: Map<AppIdentity, HomeAppMetadata>,
    ): List<HomeRenderTile> =
        placements.map { placement ->
            val metadata = metadataByIdentity[placement.appIdentity]
            val launchable =
                placement.availability == ItemAvailability.AVAILABLE && metadata != null
            HomeRenderTile(
                itemId = placement.itemId,
                appIdentity = placement.appIdentity,
                position = placement.position,
                span = placement.span,
                availability = placement.availability,
                label = metadata?.label,
                icon = metadata?.icon,
                isLaunchable = launchable,
            )
        }

    /**
     * Projects persisted [placements] for [requestedHomeId] into [HomeRenderState.Loaded].
     *
     * @throws IllegalArgumentException if any placement's `homeId` differs from [requestedHomeId].
     */
    fun project(
        requestedHomeId: HomeId,
        placements: List<AppPlacement>,
        metadataByIdentity: Map<AppIdentity, HomeAppMetadata>,
        inputs: HomeViewportInputs,
    ): HomeRenderState.Loaded {
        placements.forEach { placement ->
            require(placement.homeId == requestedHomeId) {
                "placement homeId ${placement.homeId} does not match requested $requestedHomeId"
            }
        }
        val columns = adaptColumns(inputs)
        val tiles = resolveTiles(placements, metadataByIdentity)
        val extent = measureExtent(
            placements = placements,
            viewportRows = inputs.viewportRows,
            workingMarginRows = inputs.workingMarginRows,
        )
        return HomeRenderState.Loaded(
            requestedHomeId = requestedHomeId,
            tiles = tiles,
            columns = columns,
            extent = extent,
        )
    }

    /** Constructor for the pre-emission state. */
    fun loading(requestedHomeId: HomeId): HomeRenderState.Loading =
        HomeRenderState.Loading(requestedHomeId)

    /** Constructor for the missing-home state. */
    fun notFound(requestedHomeId: HomeId): HomeRenderState.NotFound =
        HomeRenderState.NotFound(requestedHomeId)
}
