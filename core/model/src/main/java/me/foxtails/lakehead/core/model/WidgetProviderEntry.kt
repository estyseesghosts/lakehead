package me.foxtails.lakehead.core.model

/**
 * An installed widget provider, as offered by the add picker. Framework-free: the
 * provider is a component scoped to the profile that hosts it, sizes are the
 * provider's declared hints (dp for minimums, cells for the API 31 targets, 0 =
 * unset) and nothing here is persisted.
 */
data class WidgetProviderEntry(
    val provider: AppIdentity,
    val label: String,
    val appLabel: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val minResizeWidthDp: Int,
    val minResizeHeightDp: Int,
    val targetCellWidth: Int,
    val targetCellHeight: Int,
    val hasConfigureActivity: Boolean,
)
