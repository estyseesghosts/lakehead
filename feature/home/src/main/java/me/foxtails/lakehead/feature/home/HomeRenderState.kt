package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVerticalExtent
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Immutable decoded icon bytes for one home tile.
 *
 * Defensive copies are taken on construction and on access. Instances are non-empty and bounded
 * to [MAX_BYTES] so callers cannot retain or mutate the backing array. Equality and hash code
 * use array content; [toString] reports only the size and never dumps bytes.
 */
class HomeIconBytes(bytes: ByteArray) {
    private val backing: ByteArray = bytes.copyOf()

    init {
        require(backing.isNotEmpty()) { "icon bytes must not be empty" }
        require(backing.size <= MAX_BYTES) { "icon bytes must not exceed $MAX_BYTES bytes" }
    }

    /** Number of icon bytes. */
    val size: Int get() = backing.size

    /** Returns a defensive copy of the icon bytes. */
    fun bytes(): ByteArray = backing.copyOf()

    override fun equals(other: Any?): Boolean =
        other is HomeIconBytes && backing.contentEquals(other.backing)

    override fun hashCode(): Int = backing.contentHashCode()

    override fun toString(): String = "HomeIconBytes(size=$size)"

    companion object {
        /** Maximum accepted icon payload: 1 MiB. */
        const val MAX_BYTES: Int = 1024 * 1024
    }
}

/**
 * Caller-resolved display metadata for one [AppIdentity].
 *
 * Resolution (catalogue lookups, icon decoding) stays with the caller; this type only carries
 * the resolved values into the pure projection. An icon is optional, so label-only metadata can
 * still describe an available app when no icon snapshot is available.
 */
data class HomeAppMetadata(
    val label: String,
    val icon: HomeIconBytes? = null,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

/**
 * Pure viewport inputs for the home render projection.
 *
 * Pixel values are resolved by callers outside `core:grid`. Column-count policy belongs to
 * `GridViewportAdaptation`; extent policy belongs to `GridVerticalLayout`; window policy belongs
 * to `GridRowWindow`. This type only validates ranges.
 */
data class HomeViewportInputs(
    val viewportWidthPx: Int,
    val horizontalInsetPx: Int = 0,
    val horizontalPaddingPx: Int = 0,
    val columnGapPx: Int = 0,
    val minimumCellWidthPx: Int,
    val viewportRows: Long,
    val workingMarginRows: Long = 0L,
    val overscanRows: Long = 0L,
    val visibleRows: GridRowWindow,
) {
    init {
        require(viewportWidthPx > 0) { "viewportWidthPx must be positive" }
        require(horizontalInsetPx >= 0) { "horizontalInsetPx must be non-negative" }
        require(horizontalPaddingPx >= 0) { "horizontalPaddingPx must be non-negative" }
        require(columnGapPx >= 0) { "columnGapPx must be non-negative" }
        require(minimumCellWidthPx > 0) { "minimumCellWidthPx must be positive" }
        require(viewportRows > 0L) { "viewportRows must be positive" }
        require(workingMarginRows >= 0L) { "workingMarginRows must be non-negative" }
        require(overscanRows >= 0L) { "overscanRows must be non-negative" }
    }
}

/**
 * One projected render tile.
 *
 * The tile preserves the persisted identity ([itemId], [appIdentity]), the original logical
 * [position]/[span], and the persisted [availability]. [label] and [icon] are the joined
 * metadata for the complete [appIdentity], or null when no metadata was supplied. The icon is
 * optional. [isLaunchable] is true only when the placement is [ItemAvailability.AVAILABLE] and
 * metadata (including its label) is present; an icon is not required to launch.
 */
data class HomeRenderTile(
    val itemId: ItemId,
    val appIdentity: AppIdentity,
    val position: LogicalPosition,
    val span: LogicalSpan,
    val availability: ItemAvailability,
    val label: String?,
    val icon: HomeIconBytes?,
    val isLaunchable: Boolean,
) {
    init {
        require(isLaunchable == (availability == ItemAvailability.AVAILABLE && label != null)) {
            "isLaunchable must be true only when AVAILABLE with metadata label present"
        }
    }
}

/**
 * Pure render states for one requested home.
 *
 * [Loaded] with an empty [HomeRenderState.Loaded.tiles] list is a distinct successful projection;
 * it is not [Loading] and it is not [NotFound]. [Loaded.tiles] preserves the input placement
 * order, including [ItemAvailability.UNAVAILABLE] placements; the projection never sorts,
 * deduplicates, or omits placements.
 */
sealed interface HomeRenderState {
    /** No persisted result has arrived yet for [requestedHomeId]. */
    data class Loading(val requestedHomeId: HomeId) : HomeRenderState

    /**
     * Pure projection of the persisted placements for [requestedHomeId].
     *
     * @param tiles projected tiles in persisted placement order.
     * @param columns adapted column count; five is only the normal-phone reference result.
     * @param extent vertical extent measured from the persisted placements.
     * @param window visible rows extended by the requested overscan.
     */
    data class Loaded(
        val requestedHomeId: HomeId,
        val tiles: List<HomeRenderTile>,
        val columns: GridColumns,
        val extent: GridVerticalExtent,
        val window: GridRowWindow,
    ) : HomeRenderState

    /** No persisted home exists for [requestedHomeId]. */
    data class NotFound(val requestedHomeId: HomeId) : HomeRenderState
}
