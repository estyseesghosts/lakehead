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

/** D1 currently persists app placements only. */
enum class HomeItemType {
    APP,
}

/** Availability may change as the installed-app catalogue changes; identity does not. */
enum class ItemAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

/** Framework-free logical placement of an app in one Home. */
data class AppPlacement(
    val homeId: HomeId,
    val itemId: ItemId,
    val appIdentity: AppIdentity,
    val position: LogicalPosition,
    val span: LogicalSpan,
    val availability: ItemAvailability,
) {
    val type: HomeItemType = HomeItemType.APP
}
