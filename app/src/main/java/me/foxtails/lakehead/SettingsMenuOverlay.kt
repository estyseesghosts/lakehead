package me.foxtails.lakehead

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.FloatingControlOverlay
import me.foxtails.lakehead.core.designsystem.FrameAnchor
import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.HorizontalFrameAnchor
import me.foxtails.lakehead.core.designsystem.OverlayLayer
import me.foxtails.lakehead.core.designsystem.VerticalFrameAnchor
import me.foxtails.lakehead.platform.feed.FeedAvailability
import me.foxtails.lakehead.platform.feed.FeedProviderId

/** Test tag on the open settings menu overlay root (testing hook only; no behavior change). */
const val SETTINGS_MENU_TAG: String = "settings-menu"

/** Test tag on the scrim dim layer (testing hook only; no behavior change). */
const val SETTINGS_MENU_SCRIM_TAG: String = "settings-menu-scrim"

/** Accessibility label on the full-viewport scrim; activating it dismisses the menu. */
const val SETTINGS_MENU_SCRIM_DESCRIPTION: String = "Dismiss settings menu"

/** Overlay layer id for the dismiss scrim; retained for compatibility (scrim is now outside
 * [FloatingControlOverlay] so only it carries a click action). */
internal const val SETTINGS_SCRIM_LAYER_ID: String = "settings-menu-scrim"

/** Overlay layer id for the centered menu; it carries no activation (never dismisses). */
internal const val SETTINGS_MENU_LAYER_ID: String = "settings-menu-content"

/** Header label for the feed provider section. */
internal const val SETTINGS_MENU_FEED_HEADER: String = "Feed provider"

/** Suffix appended to the selected provider's row label. */
internal const val SETTINGS_MENU_SELECTED_SUFFIX: String = " (selected)"

/** Header label for the layout debug section. */
internal const val SETTINGS_MENU_LAYOUT_HEADER: String = "Layout (debug)"

/** Test tag for the export layout row. */
const val SETTINGS_MENU_EXPORT_ROW_TAG: String = "home-layout-export-row"

/** Test tag for the import layout row. */
const val SETTINGS_MENU_IMPORT_ROW_TAG: String = "home-layout-import-row"

/**
 * Test tag for one feed provider row.
 *
 * Each [FeedProviderId.PRIORITY] entry gets a stable tag (`feed-provider-row-lawnfeed`,
 * `feed-provider-row-neofeed`) so selection tests address rows without depending on labels.
 */
fun feedProviderRowTag(provider: FeedProviderId): String = when (provider) {
    is FeedProviderId.Lawnfeed -> "feed-provider-row-lawnfeed"
    is FeedProviderId.NeoFeed -> "feed-provider-row-neofeed"
}

/** Display label for one feed provider row, derived from [FeedProviderId.PRIORITY] identity. */
internal fun feedProviderLabel(provider: FeedProviderId): String = when (provider) {
    is FeedProviderId.Lawnfeed -> "Lawnfeed"
    is FeedProviderId.NeoFeed -> "Neo Feed"
}

/**
 * Currently selected feed provider, or null when nothing is hosted.
 *
 * Pure and framework-free: [FeedAvailability.Available] names its provider, every
 * [FeedAvailability.Unavailable] reason maps to no selection. Unit-testable without composition.
 */
internal fun selectedFeedProvider(availability: FeedAvailability): FeedProviderId? =
    (availability as? FeedAvailability.Available)?.provider

/** Honest footer text for an [FeedAvailability.Unavailable] reason. */
internal fun feedUnavailableReasonText(reason: FeedAvailability.Unavailable.Reason): String = when (reason) {
    FeedAvailability.Unavailable.Reason.NO_PROVIDER -> "No feed provider installed"
    FeedAvailability.Unavailable.Reason.PERMISSION_DENIED -> "Feed query was denied"
    FeedAvailability.Unavailable.Reason.QUERY_FAILED -> "Feed query failed"
}

/**
 * Transient settings menu opened by long-holding Home.
 *
 * Presentation only: [isOpen] is caller-hoisted transient gesture state (never persisted),
 * [availability] is the feed host verdict, [onDismiss] closes without navigating, and
 * [onSelectProvider] reports the tapped [FeedProviderId] (the caller binds and dismisses).
 * This overlay owns no catalogue, persistence, binding or grid logic.
 *
 * - Closed (`!isOpen`): nothing is composed.
 * - Open: a full-viewport scrim dim (theme `scrim` at 0.45 alpha,
 *   [SETTINGS_MENU_SCRIM_DESCRIPTION], click dismisses) behind a centered menu hosted
 *   in [FloatingControlOverlay] with no layer activation (priority 10). The menu layer
 *   exposes no click action; only the scrim dismisses and rows are sibling clickables
 *   with a 48dp minimum touch target. The menu lists one row per
 *   [FeedProviderId.PRIORITY] entry with the [selectedFeedProvider] row marked (its
 *   content description carries [SETTINGS_MENU_SELECTED_SUFFIX] and the selected
 *   semantics flag), plus an honest footer when [availability] is
 *   [FeedAvailability.Unavailable]. After the feed section, a "Layout (debug)" header
 *   and two rows ("Export layout…", "Import layout…") invoke [onExportHomeLayout] and
 *   [onImportHomeLayout] respectively without dismissing the menu; they carry no
 *   [selected] semantics. The header and footer expose no click action.
 * - `BackHandler(enabled = isOpen)` dismisses; while open the back press is consumed here
 *   and never propagates to the pager.
 *
 * Built from Foundation/text/graphics primitives only ([BasicText], [Column], [Box]); no
 * Material components are used (colors come from [MaterialTheme.colorScheme] only).
 * Sizes are viewport-derived ([BoxWithConstraints] + density) so both frames always
 * fit: the scrim fills the viewport, the menu is centered at viewport minus rhythm
 * margins clamped to the viewport.
 */
@Composable
internal fun SettingsMenuOverlay(
    isOpen: Boolean,
    availability: FeedAvailability,
    onDismiss: () -> Unit,
    onSelectProvider: (FeedProviderId) -> Unit,
    onExportHomeLayout: () -> Unit = {},
    onImportHomeLayout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return
    BackHandler(enabled = true, onBack = onDismiss)

    val selectedProvider = selectedFeedProvider(availability)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(SETTINGS_MENU_TAG),
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val horizontalMarginPx = with(density) { 32.dp.roundToPx() } * 2
        val verticalMarginPx = with(density) { 64.dp.roundToPx() } * 2
        val menuWidthPx = (viewportWidthPx - horizontalMarginPx)
            .coerceAtLeast(1)
            .coerceAtMost(viewportWidthPx)
        val desiredHeightPx = with(density) { 280.dp.roundToPx() }.coerceAtLeast(1)
        val maxMenuHeightPx = (viewportHeightPx - verticalMarginPx)
            .coerceAtLeast(1)
            .coerceAtMost(viewportHeightPx)
        val menuHeightPx = minOf(desiredHeightPx, maxMenuHeightPx)
        val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
        val menuColor = MaterialTheme.colorScheme.surfaceContainer

        Box(modifier = Modifier.fillMaxSize()) {
            // Dismiss scrim lives outside FloatingControlOverlay so the menu layer
            // carries no click action; only this scrim dismisses.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .semantics {
                        contentDescription = SETTINGS_MENU_SCRIM_DESCRIPTION
                    }
                    .clickable(
                        role = Role.Button,
                        onClick = onDismiss,
                    )
                    .testTag(SETTINGS_MENU_SCRIM_TAG),
            )
            FloatingControlOverlay(
                modifier = Modifier.fillMaxSize(),
                backgroundContent = {},
                overlays = listOf(
                    OverlayLayer(
                        id = SETTINGS_MENU_LAYER_ID,
                        priority = 10,
                        anchor = FrameAnchor(
                            HorizontalFrameAnchor.CENTER,
                            VerticalFrameAnchor.CENTER,
                        ),
                        size = FrameSize(menuWidthPx, menuHeightPx),
                        contentDescription = "Settings menu",
                        content = {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(menuColor)
                                    .padding(16.dp),
                            ) {
                                BasicText(
                                    text = SETTINGS_MENU_FEED_HEADER,
                                    style = LocalTextStyle.current.merge(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                                for (provider in FeedProviderId.PRIORITY) {
                                    val label = feedProviderLabel(provider)
                                    val isSelected = provider == selectedProvider
                                    val rowText = if (isSelected) {
                                        label + SETTINGS_MENU_SELECTED_SUFFIX
                                    } else {
                                        label
                                    }
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .clickable(
                                                role = Role.Button,
                                                onClick = { onSelectProvider(provider) },
                                            )
                                            .semantics(mergeDescendants = true) {
                                                this.contentDescription = rowText
                                                this.selected = isSelected
                                            }
                                            .testTag(feedProviderRowTag(provider)),
                                    ) {
                                        BasicText(
                                            text = rowText,
                                            style = LocalTextStyle.current.merge(
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        )
                                    }
                                }
                                val unavailable = availability as? FeedAvailability.Unavailable
                                if (unavailable != null) {
                                    BasicText(
                                        text = feedUnavailableReasonText(unavailable.reason),
                                        style = LocalTextStyle.current.merge(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                                // Layout debug section: export / import rows.
                                BasicText(
                                    text = SETTINGS_MENU_LAYOUT_HEADER,
                                    style = LocalTextStyle.current.merge(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClick = onExportHomeLayout,
                                        )
                                        .testTag(SETTINGS_MENU_EXPORT_ROW_TAG),
                                ) {
                                    BasicText(
                                        text = "Export layout\u2026",
                                        style = LocalTextStyle.current.merge(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClick = onImportHomeLayout,
                                        )
                                        .testTag(SETTINGS_MENU_IMPORT_ROW_TAG),
                                ) {
                                    BasicText(
                                        text = "Import layout\u2026",
                                        style = LocalTextStyle.current.merge(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                }
                            }
                        },
                    ),
                ),
            )
        }
    }
}
