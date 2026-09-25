package me.foxtails.lakehead.feature.allapps

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.model.AppIdentity

/**
 * One row of the All Apps list.
 *
 * Presentation only: the caller supplies the already-decoded [icon] snapshot (or null when
 * none was supplied) and the row performs no decode, catalogue/platform lookup, or database
 * access. Foundation primitives only (Row/Box/Image/BasicText): no Material `ListItem`,
 * `Card`, or stock components, and no `AppTile` (that primitive is a centered grid tile;
 * this is a full-width list row with a separate launch area and add control).
 *
 * - The launch area (icon + label) is a single merged button node named by [AllAppsEntry.label];
 *   activating it dispatches the exact [AppIdentity] via [onLaunchApp].
 * - The add control is a separate ≥48dp button node described as `"Add {label} to Home"`;
 *   activating it dispatches the exact [AppIdentity] via [onAddToHome].
 * - When [AllAppsEntry.alreadyOnHome] is true the add control is replaced by an honest,
 *   non-clickable `"Added"` marker so the row never promises an add it will not perform.
 */
@Composable
fun AllAppsRow(
    entry: AllAppsEntry,
    icon: ImageBitmap?,
    onLaunchApp: (AppIdentity) -> Unit,
    onAddToHome: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .testTag(ALL_APPS_ROW_TAG)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = entry.label
                }
                .clickable(
                    role = Role.Button,
                    onClick = { onLaunchApp(entry.identity) },
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .testTag(ALL_APPS_ICON_TAG)
                        .size(40.dp),
                )
            }
            BasicText(
                text = entry.label,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (entry.alreadyOnHome) {
            BasicText(
                text = ADDED_MARKER_TEXT,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .semantics {
                        contentDescription = "${entry.label} already on Home"
                        stateDescription = ADDED_STATE_DESCRIPTION
                    },
            )
        } else {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Add ${entry.label} to Home"
                    }
                    .clickable(
                        role = Role.Button,
                        onClick = { onAddToHome(entry.identity) },
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = ADD_ACTION_TEXT)
            }
        }
    }
}

/** Test tag on every All Apps row (testing hook only; no behavior change). */
const val ALL_APPS_ROW_TAG: String = "allapps-row"

/** Test tag on a directly rendered pre-decoded row icon (testing hook only). */
const val ALL_APPS_ICON_TAG: String = "allapps-row-icon"

/** Visible label of the add control. */
const val ADD_ACTION_TEXT: String = "Add"

/** Visible marker shown instead of the add control when the app is already on Home. */
const val ADDED_MARKER_TEXT: String = "Added"

/** State description exposed on the honest already-on-Home marker. */
const val ADDED_STATE_DESCRIPTION: String = "Added"
