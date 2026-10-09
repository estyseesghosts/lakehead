package me.foxtails.lakehead.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.lakehead.core.grid.widgetDefaultSpan
import me.foxtails.lakehead.core.model.WidgetProviderEntry

/**
 * Floating picker over Home listing installed widget providers grouped by owning app.
 *
 * Takes a plain list and reports the chosen provider; it owns no discovery, binding or
 * placement. Each row shows the widget label and the cell size it will land at on a
 * [columns]-wide grid. Back and the scrim dismiss. No preview images yet.
 */
@Composable
fun WidgetPickerOverlay(
    isOpen: Boolean,
    providers: List<WidgetProviderEntry>,
    columns: Int,
    onPick: (WidgetProviderEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return
    BackHandler(enabled = true, onBack = onDismiss)
    val scheme = MaterialTheme.colorScheme
    val rows = remember(providers) { providers.toPickerRows() }
    Box(modifier = modifier.fillMaxSize().testTag(WIDGET_PICKER_TAG)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 64.dp)
                .heightIn(max = 520.dp)
                .background(scheme.surfaceContainer)
                .padding(16.dp),
        ) {
            BasicText(
                text = "Add widget",
                style = TextStyle(color = scheme.onSurface, fontSize = 18.sp),
            )
            if (rows.isEmpty()) {
                BasicText(
                    text = "No widgets available",
                    style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 14.sp),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            LazyColumn {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is PickerRow.Header -> BasicText(
                            text = row.appLabel,
                            style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 13.sp),
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        is PickerRow.Widget -> {
                            val span = widgetDefaultSpan(
                                row.entry.targetCellWidth,
                                row.entry.targetCellHeight,
                                row.entry.minWidthDp,
                                row.entry.minHeightDp,
                                columns,
                            )
                            BasicText(
                                text = "${row.entry.label}  ${span.columns}×${span.rows}",
                                style = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button) { onPick(row.entry) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal sealed interface PickerRow {
    val key: String

    data class Header(val appLabel: String, override val key: String) : PickerRow

    data class Widget(val entry: WidgetProviderEntry) : PickerRow {
        override val key: String get() = with(entry.provider) {
            "w:${profileId.value}:${component.packageName}/${component.className}"
        }
    }
}

/** Flattens an ordered provider list into one header row per consecutive owning app. */
internal fun List<WidgetProviderEntry>.toPickerRows(): List<PickerRow> {
    val rows = ArrayList<PickerRow>()
    var group: Pair<String, String>? = null
    for (entry in this) {
        val id = entry.provider.profileId.value to entry.provider.component.packageName
        if (id != group) {
            group = id
            rows += PickerRow.Header(entry.appLabel, "h:${id.first}:${id.second}")
        }
        rows += PickerRow.Widget(entry)
    }
    return rows
}

/** Test tag on the widget picker. */
const val WIDGET_PICKER_TAG: String = "widget-picker"
