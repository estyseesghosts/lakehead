package me.foxtails.lakehead.core.model

/** Zero-based logical cell coordinates. This does not encode grid bounds or placement policy. */
data class LogicalPosition(
    val column: Int,
    val row: Int,
) {
    init {
        require(column >= 0) { "column must be non-negative" }
        require(row >= 0) { "row must be non-negative" }
    }
}

/** Logical cell dimensions; legality and supported spans belong to `:core:grid`. */
data class LogicalSpan(
    val columns: Int,
    val rows: Int,
) {
    init {
        require(columns > 0) { "columns must be positive" }
        require(rows > 0) { "rows must be positive" }
    }
}

enum class HomeItemType {
    APP,
    WIDGET,
}

/** Availability may change as the installed-app catalogue changes; identity does not. */
enum class ItemAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

/** Framework-free logical placement of one item in one Home; the grid sees only id, position and span. */
sealed interface HomePlacement {
    val homeId: HomeId
    val itemId: ItemId
    val position: LogicalPosition
    val span: LogicalSpan
    val availability: ItemAvailability
    val type: HomeItemType

    /** The same item at a new position and span; identity and content are unchanged. */
    fun withGeometry(position: LogicalPosition, span: LogicalSpan): HomePlacement
}

/** Framework-free logical placement of an app in one Home. */
data class AppPlacement(
    override val homeId: HomeId,
    override val itemId: ItemId,
    val appIdentity: AppIdentity,
    override val position: LogicalPosition,
    override val span: LogicalSpan,
    override val availability: ItemAvailability,
) : HomePlacement {
    override val type: HomeItemType = HomeItemType.APP

    override fun withGeometry(position: LogicalPosition, span: LogicalSpan): AppPlacement =
        copy(position = position, span = span)
}

/**
 * A widget placed in one Home. [provider] is the widget provider component under the
 * profile that hosts it; [appWidgetId] is the host-allocated id of the bound instance.
 */
data class WidgetPlacement(
    override val homeId: HomeId,
    override val itemId: ItemId,
    val provider: AppIdentity,
    val appWidgetId: Int,
    override val position: LogicalPosition,
    override val span: LogicalSpan,
    override val availability: ItemAvailability,
) : HomePlacement {
    override val type: HomeItemType = HomeItemType.WIDGET

    override fun withGeometry(position: LogicalPosition, span: LogicalSpan): WidgetPlacement =
        copy(position = position, span = span)
}
