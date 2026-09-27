package me.foxtails.lakehead.feature.allapps

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.AppSymbol
import me.foxtails.lakehead.core.designsystem.LocalLauncherDimensions
import me.foxtails.lakehead.core.model.AppIdentity

/**
 * One row of the All Apps list: a wide 1-tile-high unit.
 *
 * Presentation only: the caller supplies the already-decoded [icon] snapshot (or null when
 * none was supplied) and the row performs no decode, catalogue/platform lookup, or database
 * access. Foundation primitives only (Row/Box/BasicText plus the shared [AppSymbol]):
 * no Material `ListItem`, `Card`, or stock components, and no `AppTile` (that primitive is
 * a centered grid tile; this is a full-width list row with a separate launch area and add
 * control).
 *
 * Geometry comes from [LocalLauncherDimensions] (caller wraps the list in `LauncherTheme`):
 * the outer row pads [horizontal][tilePadding][x2] / [vertical][tilePadding] with a minimum
 * height of `symbolSize + 2 * tilePadding` (the 1x1 tile height, 56.dp by default); the
 * inner launch row pads [vertical][tilePadding]; the label starts after
 * `tilePadding * 1.5f`. Text renders in `onSurface` via a [LocalTextStyle] merge so it
 * stays theme-adaptive (never a hardcoded black/white).
 *
 * - The launch area (icon + label) is a single merged button node named by [AllAppsEntry.displayLabel];
 *   activating it dispatches the exact [AppIdentity] via [onLaunchApp].
 * - The trailing cluster is always two sibling slots directly under the outer [Row]
 *   (never nested): an Add slot followed by a Remove placeholder slot. Each slot is a
 *   square ≥48dp [Box]; the visible glyph is an empty circle ([CircleShape] border in
 *   `onSurface`, never hardcoded black/white) with a centered [BasicText] `"+"`
 *   (Add) or `"−"` (Remove, U+2212).
 * - When [AllAppsEntry.alreadyOnHome] is false the Add slot is an enabled 48dp button
 *   described as `"Add {displayLabel} to Home"`; activating it dispatches the exact [AppIdentity]
 *   via [onAddToHome].
 * - When [AllAppsEntry.alreadyOnHome] is true the Add slot is replaced by an honest,
 *   non-clickable 48dp `"Added"` marker described as `"{displayLabel} already on Home"` with
 *   state `"Added"`, so the row never promises an add it will not perform. The slot
 *   keeps the same 48dp tappable shape (minima) as the Add button.
 * - The Remove slot is visual-only in this slice: a disabled 48dp box described as
 *   `"Remove {displayLabel} from Home"` with no click action, no [Role], and 0.38f alpha.
 *   There is intentionally no `onRemove` callback, no persistence, and no platform call.
 */
@Composable
fun AllAppsRow(
    entry: AllAppsEntry,
    icon: ImageBitmap?,
    onLaunchApp: (AppIdentity) -> Unit,
    onAddToHome: (AppIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = LocalLauncherDimensions.current
    val rowMinHeight = dimensions.symbolSize + dimensions.tilePadding * 2
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceStyle =
        LocalTextStyle.current.merge(color = onSurface)
    Row(
        modifier = modifier
            .testTag(ALL_APPS_ROW_TAG)
            .fillMaxWidth()
            .defaultMinSize(minHeight = rowMinHeight)
            .padding(
                horizontal = dimensions.tilePadding * 2,
                vertical = dimensions.tilePadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = entry.displayLabel
                }
                .clickable(
                    role = Role.Button,
                    onClick = { onLaunchApp(entry.identity) },
                )
                .padding(vertical = dimensions.tilePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                AppSymbol(
                    bitmap = icon,
                    modifier = Modifier.testTag(ALL_APPS_ICON_TAG),
                )
            }
            BasicText(
                text = entry.displayLabel,
                style = onSurfaceStyle,
                modifier = Modifier.padding(start = dimensions.tilePadding * 1.5f),
            )
        }
        if (entry.alreadyOnHome) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "${entry.displayLabel} already on Home"
                        stateDescription = ADDED_STATE_DESCRIPTION
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = ADDED_MARKER_TEXT,
                    style = onSurfaceStyle,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Add ${entry.displayLabel} to Home"
                    }
                    .clickable(
                        role = Role.Button,
                        onClick = { onAddToHome(entry.identity) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .border(1.dp, onSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = ADD_ACTION_TEXT,
                        style = onSurfaceStyle,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .testTag(ALL_APPS_REMOVE_TAG)
                .size(48.dp)
                .alpha(0.38f)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Remove ${entry.displayLabel} from Home"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .border(1.dp, onSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = REMOVE_ACTION_TEXT,
                    style = onSurfaceStyle,
                )
            }
        }
    }
}

/** Test tag on every All Apps row (testing hook only; no behavior change). */
const val ALL_APPS_ROW_TAG: String = "allapps-row"

/** Test tag on a directly rendered pre-decoded row icon (testing hook only). */
const val ALL_APPS_ICON_TAG: String = "allapps-row-icon"

/** Test tag on the visual-only Remove placeholder slot (testing hook only). */
const val ALL_APPS_REMOVE_TAG: String = "allapps-remove"

/** Visible glyph inside the Add circle (tester aid; the slot description carries the meaning). */
const val ADD_ACTION_TEXT: String = "+"

/** Visible glyph inside the visual-only Remove circle (U+2212 minus). */
const val REMOVE_ACTION_TEXT: String = "−"

/** Visible marker shown instead of the add control when the app is already on Home. */
const val ADDED_MARKER_TEXT: String = "Added"

/** State description exposed on the honest already-on-Home marker. */
const val ADDED_STATE_DESCRIPTION: String = "Added"
