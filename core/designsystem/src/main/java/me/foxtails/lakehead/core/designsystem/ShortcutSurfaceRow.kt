package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One rank-ordered shortcut entry for a tile's swipe surface.
 *
 * Presentation only: callers pass entries in the application's/system's shortcut rank
 * order (menu order), which is decoupled from the tile's user pin order. [id] is opaque
 * caller identity carried back through execute/pin callbacks; it is never interpreted
 * here. [isPinned] selects the pin toggle's filled (pinned) vs outline (unpinned)
 * visual. [isPinEnabled] is false while the tile has no visible-strip capacity left
 * (at-capacity disabled pin, §17); the toggle keeps its label but exposes no action.
 *
 * Plain action rows (album "Change album" / "Remove album") set [isAction]: they
 * render label-only with no pin toggle and report taps through the same execute
 * callback, routed by [id]. Shortcut rows keep [isAction] false.
 */
data class ShortcutSurfaceEntry(
    val id: String,
    val label: String,
    val isPinned: Boolean,
    val isPinEnabled: Boolean = true,
    val isAction: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

/**
 * Accessible name for the pin toggle of [label]: `Pin "X" to tile` when unpinned,
 * `Unpin "X" from tile` when pinned. Pure, framework-free.
 */
fun pinToggleDescription(label: String, isPinned: Boolean): String {
    require(label.isNotBlank()) { "label must not be blank" }
    return if (isPinned) {
        "Unpin \"$label\" from tile"
    } else {
        "Pin \"$label\" to tile"
    }
}

/**
 * One temporary shortcut-surface row: launch zone plus pin toggle as siblings.
 *
 * Custom Foundation/graphics/text only: no Material product components, no inventory,
 * persistence or navigation. The outer [Row] is a non-clickable container; its two
 * children are siblings, never nested clickables (extends the tile Body/AppSymbol
 * sibling rule):
 *
 * - RowLaunchZone: horizontally weight-filling [Row.clickable]
 *   (`onClick = onExecute`, [Role.Button]) with a fixed
 *   [SHORTCUT_SURFACE_ROW_HEIGHT] height (no `fillMaxHeight` stretch) and
 *   `semantics(mergeDescendants = true)` exposing [entry]'s label.
 *   Tapping executes the shortcut.
 * - PinToggle: 48dp [Box.clickable] (`onClick = onTogglePin`, [Role.Button]) with
 *   `semantics(mergeDescendants = true)` exposing [pinToggleDescription]. Tapping
 *   pins/unpins the shortcut on the tile and never executes it. The visual is a 32dp
 *   circle: filled `scheme.primary` while [ShortcutSurfaceEntry.isPinned], 2dp
 *   `scheme.outline` border otherwise (temporary menu visual). While
 *   [ShortcutSurfaceEntry.isPinEnabled] is false the toggle keeps its label but
 *   exposes no click action.
 *
 * Theme-adaptive via the scheme, never hardcoded Black/White. Callers render rows in
 * rank order; this row performs no ordering.
 */
@Composable
fun ShortcutSurfaceRow(
    entry: ShortcutSurfaceEntry,
    onExecute: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(SHORTCUT_SURFACE_ROW_HEIGHT.dp)
            .testTag(SHORTCUT_SURFACE_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(SHORTCUT_SURFACE_ROW_HEIGHT.dp)
                .testTag(ROW_LAUNCH_ZONE_TAG)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = entry.label
                }
                .clickable(
                    onClick = onExecute,
                    role = Role.Button,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = entry.label,
                style = TextStyle(
                    color = scheme.onSurface,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .testTag(PIN_TOGGLE_TAG)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = pinToggleDescription(entry.label, entry.isPinned)
                    this.role = Role.Button
                    if (!entry.isPinEnabled) {
                        disabled()
                    }
                }
                .then(
                    if (entry.isPinEnabled) {
                        Modifier
                    } else {
                        Modifier.alpha(PIN_DISABLED_ALPHA)
                    },
                )
                .then(
                    if (entry.isPinEnabled) {
                        Modifier.clickable(
                            onClick = onTogglePin,
                            role = Role.Button,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.isPinned) {
                Box(
                    modifier = Modifier
                        .size(PIN_VISUAL_SIZE.dp)
                        .background(
                            color = scheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "●",
                        style = TextStyle(
                            color = scheme.onPrimary,
                            fontSize = 16.sp,
                        ),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(PIN_VISUAL_SIZE.dp)
                        .border(
                            width = 2.dp,
                            color = scheme.outline,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "●",
                        style = TextStyle(
                            color = scheme.outline,
                            fontSize = 16.sp,
                        ),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/** Test tag on a shortcut-surface row container. */
const val SHORTCUT_SURFACE_ROW_TAG: String = "shortcut-surface-row"

/** Test tag on a row's launch zone (executes the shortcut). */
const val ROW_LAUNCH_ZONE_TAG: String = "shortcut-row-launch-zone"

/** Test tag on a row's pin toggle (pins/unpins without executing). */
const val PIN_TOGGLE_TAG: String = "shortcut-pin-toggle"

/**
 * Shared fixed row height in dp.
 *
 * One constant sizes the outer row and the launch zone together, so rows never
 * stretch to fill the popup: a one-shortcut popup hugs exactly one 56dp row
 * and multi-row popups grow by whole rows. 56dp accommodates the 48dp pin
 * target alongside the launch-zone label.
 */
const val SHORTCUT_SURFACE_ROW_HEIGHT: Int = 56

/** Visual circle diameter of the pin toggle in dp. */
private const val PIN_VISUAL_SIZE: Int = 32

/** Alpha applied to the pin toggle while pinning is disabled (at capacity). */
private const val PIN_DISABLED_ALPHA: Float = 0.38f
