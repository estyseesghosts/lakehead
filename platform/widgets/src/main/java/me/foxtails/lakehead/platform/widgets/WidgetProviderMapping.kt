package me.foxtails.lakehead.platform.widgets

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.WidgetProviderEntry

/** The fields read from an `AppWidgetProviderInfo`, copied at the Android boundary. */
internal data class RawWidgetProvider(
    val packageName: String,
    val className: String,
    val label: String?,
    val appLabel: String?,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val minResizeWidthDp: Int,
    val minResizeHeightDp: Int,
    val targetCellWidth: Int,
    val targetCellHeight: Int,
    val hasConfigureActivity: Boolean,
)

internal fun RawWidgetProvider.toEntry(profileId: ProfileId): WidgetProviderEntry =
    WidgetProviderEntry(
        provider = AppIdentity(profileId, ComponentIdentity(packageName, className)),
        label = label?.takeIf { it.isNotBlank() } ?: className.substringAfterLast('.'),
        appLabel = appLabel?.takeIf { it.isNotBlank() } ?: packageName,
        minWidthDp = minWidthDp.coerceAtLeast(0),
        minHeightDp = minHeightDp.coerceAtLeast(0),
        minResizeWidthDp = minResizeWidthDp.coerceAtLeast(0),
        minResizeHeightDp = minResizeHeightDp.coerceAtLeast(0),
        targetCellWidth = targetCellWidth.coerceAtLeast(0),
        targetCellHeight = targetCellHeight.coerceAtLeast(0),
        hasConfigureActivity = hasConfigureActivity,
    )

/** Picker order: owning app, then widget label, case-insensitive, then component for stability. */
internal val widgetProviderOrder: Comparator<WidgetProviderEntry> =
    compareBy<WidgetProviderEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        .thenBy { it.provider.profileId.value }
        .thenBy { it.provider.component.packageName }
        .thenBy { it.provider.component.className }
