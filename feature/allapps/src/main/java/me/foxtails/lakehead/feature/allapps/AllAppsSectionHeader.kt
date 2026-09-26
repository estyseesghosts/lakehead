package me.foxtails.lakehead.feature.allapps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Full-row section header for the grouped All Apps list.
 *
 * Presentation only: the caller supplies the already-computed section [key] and the header
 * performs no grouping, catalogue/platform lookup, or database access. Foundation
 * primitives only (Row/BasicText): no Material `Card`, `ListItem`, or stock components.
 *
 * - Full-row [Row] with [Modifier.fillMaxWidth] and minimum height 48.dp (accessible tap
 *   target); inner padding follows the shared row rhythm (16.dp horizontal, 12.dp vertical).
 * - A single merged button node described as `"$key, application section"`; activating it
 *   dispatches [key] via [onHeaderTap]. Headers scroll with content (plain `item`, never
 *   sticky).
 * - Text renders in `onSurface` via a [LocalTextStyle] merge so it stays theme-adaptive
 *   (never a hardcoded black/white).
 */
@Composable
fun AllAppsSectionHeader(
    key: String,
    onHeaderTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurfaceStyle = LocalTextStyle.current.merge(color = MaterialTheme.colorScheme.onSurface)
    Row(
        modifier = modifier
            .testTag(ALL_APPS_SECTION_HEADER_TAG)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$key, application section"
            }
            .clickable(
                role = Role.Button,
                onClick = { onHeaderTap(key) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = key,
            style = onSurfaceStyle,
        )
    }
}

/** Test tag on every All Apps section header (testing hook only; no behavior change). */
const val ALL_APPS_SECTION_HEADER_TAG: String = "allapps-section-header"
