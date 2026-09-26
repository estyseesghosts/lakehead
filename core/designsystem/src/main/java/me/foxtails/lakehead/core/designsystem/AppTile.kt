package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry

/**
 * Presentation-only state for a small reusable app tile.
 *
 * [label] is never rendered as visible text; it must stay non-blank because it
 * supplies the tile's accessible name (default `contentDescription`). [span] selects
 * the icon anchor via [symbolAlignment] but performs no placement legality,
 * span-support or column-count checks: whether the span may be placed remains owned
 * by `core:grid`. [isSelected] and [isPressed] are caller-owned transient visual
 * states; this tile never derives them from inventory, persistence or its own
 * interaction source.
 */
data class AppTileState(
    val label: String,
    val span: GridSpan = GridSpan(1, 1),
    val colorRole: TileColorRole = TileColorRole.Normal,
    val isSelected: Boolean = false,
    val isPressed: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "label must be non-blank" }
    }
}

/** Shared visual/semantic constants for [AppTile]. */
object AppTileDefaults {
    /** `stateDescription` announced when [AppTileState.isPressed] is true. */
    const val PressedStateDescription = "Pressed"

    val TileShape = RectangleShape
}

/**
 * Resolved semantic tile colors for one [TileColorRole].
 *
 * @param container tile background (pressed state darkens via alpha).
 * @param content label/icon tint on [container].
 * @param selectionBorder outline drawn when the tile is selected.
 */
internal data class TileColors(
    val container: Color,
    val content: Color,
    val selectionBorder: Color,
)

/**
 * Resolves [role] to Material dynamic colors.
 *
 * See `docs/grid.md` section 12: Normal resolves to
 * `surfaceContainer`/`onSurface`, Accent to `primaryContainer`/`onPrimaryContainer`;
 * the pressed container is the base at 70% alpha, the selected container is the
 * base at 50% alpha (selection border is kept), and the selection border is
 * `scheme.primary`. Only the role is persisted, never these resolved colors.
 */
@Composable
internal fun resolveTileColors(
    role: TileColorRole,
    isPressed: Boolean,
    isSelected: Boolean = false,
): TileColors {
    val scheme = MaterialTheme.colorScheme
    val baseContainer = when (role) {
        TileColorRole.Normal -> scheme.surfaceContainer
        TileColorRole.Accent -> scheme.primaryContainer
    }
    val baseContent = when (role) {
        TileColorRole.Normal -> scheme.onSurface
        TileColorRole.Accent -> scheme.onPrimaryContainer
    }
    return TileColors(
        container = when {
            isSelected -> baseContainer.copy(alpha = 0.5f)
            isPressed -> baseContainer.copy(alpha = 0.7f)
            else -> baseContainer
        },
        content = baseContent,
        selectionBorder = scheme.primary,
    )
}

/**
 * Small reusable icon-only app-tile primitive with sibling hit regions.
 *
 * Custom Compose Foundation/graphics/text only: no Material product components, no app
 * inventory, database, persistence, navigation or business logic. Callers supply the
 * accessibility [AppTileState.label], the optional caller-composed [icon] slot, separate
 * body/symbol click callbacks and an optional Body-only long-click callback, plus
 * optional [contentDescription]/[symbolContentDescription] overrides (each defaults to
 * the label so every region always has an accessible name). No text label is ever
 * rendered on the home-tile path.
 *
 * Layout: the outer [Box] is a non-clickable anchor container (background and
 * selection border only). Its children are siblings, never nested clickables, in
 * TalkBack order:
 *
 * - TileBody: full-size [Box.combinedClickable] (`onClick = onBodyClick`,
 *   Body-only `onLongClick`, [Role.Button]) with `semantics(mergeDescendants = true)`.
 * - AppLaunchButton: [Box.clickable] (`onClick = onSymbolClick`, [Role.Button])
 *   wrapping the [icon] slot, with `semantics(mergeDescendants = true)`.
 * - ShortcutSlot strip: visual-only bottom strip of neutral placeholder dots
 *   (`shortcutPlaceholders` count, default 0 renders none): non-clickable, never
 *   focusable, no [Role.Button]; each dot is a distinct semantics node
 *   ("Shortcut placeholder i+1"). A trailing sibling so order stays Body → Symbol →
 *   slots. Wide spans anchor the strip [Alignment.BottomEnd] as a [Row];
 *   narrow single-column spans anchor [Alignment.BottomCenter] as a [Column].
 *   Index 0 sits nearest the anchor (rightmost in a row, bottommost in a column)
 *   with a fixed pitch, so pin order stays spatially stable.
 *
 * A 1x1 tile is full-bleed (no [LauncherDimensions.tilePadding] inset; the tile
 * background fills the whole cell) while the symbol hit region stays icon-sized
 * ([LauncherDimensions.symbolSize] coerced to the 48.dp minimum) centered on the
 * tile, so ring/edge touches reach TileBody; larger spans keep the tile-padding inset with
 * the caller's natural symbol size anchored via [symbolAlignment] with
 * `hasPinned = pinnedCount > 0`: zero pinned shortcuts centers the symbol,
 * while pinned shortcuts reuse the compact large-tile anchor (tall 1x2 tiles
 * bottom-center, every other span bottom-ends).
 *
 * Accessibility: TileBody merges descendants into a focusable node with the resolved
 * content description, the `selected` flag for [AppTileState.isSelected], and
 * [AppTileDefaults.PressedStateDescription] while [AppTileState.isPressed] is true.
 * Selected/pressed semantics live on TileBody only; the symbol region exposes only
 * its own content description. Pressed/selected visuals are caller-driven through
 * state; the tile holds no state and issues no events beyond the supplied callbacks.
 *
 * @param onBodyClick invoked from TileBody; callers keep it launch-equivalent to
 * [onSymbolClick] today (Body diverges to a notification surface later per
 * `docs/grid.md` section 10).
 * @param onSymbolClick invoked from AppLaunchButton.
 * @param onLongClick when null TileBody exposes no long-click action; AppLaunchButton
 * never exposes one.
 * @param shortcutPlaceholders test/mock-only count of visual-only neutral-dot
 * shortcut glyphs in the ShortcutSlot strip (default 0, so 1x1 tiles show none).
 * Glyphs are inert: no click/long-click handling and no text labels. The count
 * is clamped via [ShortcutStripGeometry.visibleCount] for [AppTileState.span],
 * so over-counts render max instead of crashing; negative counts are still
 * rejected.
 * @param pinnedCount launcher-tile pinned shortcut count driving the symbol anchor
 * (default 0 → centered via `symbolAlignment(span, false)`). One or more reuses
 * the compact large-tile anchor. Negative counts are rejected. Today this is
 * caller-supplied test state; swipe/menu/persistence wiring lands separately.
 * @param isInteractionEnabled when false both hit regions keep their minima,
 * merged-descendants semantics, [Role.Button], descriptions and border visuals
 * but expose no click/long-click actions (`enabled = false` on TileBody's
 * `combinedClickable` and the symbol `clickable`), so in-edit drag gestures win
 * the slop race. Defaults to true (fully interactive).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    state: AppTileState,
    onBodyClick: () -> Unit,
    onSymbolClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    symbolContentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    shortcutPlaceholders: Int = 0,
    pinnedCount: Int = 0,
    isInteractionEnabled: Boolean = true,
) {
    require(contentDescription == null || contentDescription.isNotBlank()) {
        "contentDescription must be null or non-blank"
    }
    require(symbolContentDescription == null || symbolContentDescription.isNotBlank()) {
        "symbolContentDescription must be null or non-blank"
    }
    require(shortcutPlaceholders >= 0) {
        "shortcutPlaceholders must be >= 0"
    }
    require(pinnedCount >= 0) {
        "pinnedCount must be >= 0"
    }
    // Bottom-edge capacity owned by core:grid: the icon occupies one slot.
    val renderedPlaceholders = ShortcutStripGeometry.visibleCount(shortcutPlaceholders, state.span)
    val hasPinned = pinnedCount > 0
    val resolvedDescription = contentDescription ?: state.label
    val resolvedSymbolDescription = symbolContentDescription ?: resolvedDescription
    val tileColors = resolveTileColors(
        role = state.colorRole,
        isPressed = state.isPressed,
        isSelected = state.isSelected,
    )
    val containerColor = tileColors.container
    val dimensions = LocalLauncherDimensions.current

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .fillMaxSize()
            .background(containerColor, AppTileDefaults.TileShape)
            .then(
                if (state.isSelected) {
                    Modifier.border(
                        2.dp,
                        tileColors.selectionBorder,
                        AppTileDefaults.TileShape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        val isFullBleed = state.span == GridSpan(1, 1)
        // TileBody: primary hit region; the only region with a long-click action.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = resolvedDescription
                    this.selected = state.isSelected
                    if (state.isPressed) {
                        this.stateDescription = AppTileDefaults.PressedStateDescription
                    }
                }
                .combinedClickable(
                    enabled = isInteractionEnabled,
                    onLongClick = onLongClick,
                    onClick = onBodyClick,
                    role = Role.Button,
                ),
        )
        // AppLaunchButton: symbol hit region wrapping the icon slot; a sibling of
        // TileBody, never nested inside it.
        if (isFullBleed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(dimensions.symbolSize.coerceAtLeast(48.dp))
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = resolvedSymbolDescription
                    }
                    .clickable(
                        enabled = isInteractionEnabled,
                        onClick = onSymbolClick,
                        role = Role.Button,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    icon()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(symbolAlignment(state.span, hasPinned))
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(dimensions.tilePadding)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = resolvedSymbolDescription
                    }
                    .clickable(
                        enabled = isInteractionEnabled,
                        onClick = onSymbolClick,
                        role = Role.Button,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    icon()
                }
            }
        }
        // ShortcutSlot strip: visual-only bottom strip of neutral placeholder dots.
        // No clickable, never focusable, no Role.Button; each dot keeps its own
        // semantics node ("Shortcut placeholder i+1"), so the strip itself must not
        // merge descendants. Wide spans anchor BottomEnd as a Row extending leftward;
        // narrow single-column spans anchor BottomCenter as a Column extending upward
        // (core:grid strip-geometry contract). Index 0 sits nearest the anchor with
        // fixed pitch, so pin order stays spatially stable. Dots render at
        // unitIconSize visual weight and never force a 48dp per-slot target, so
        // 1x1 tiles (default 0, renders none) stay full-bleed.
        val placeholderTint = MaterialTheme.colorScheme.outlineVariant
        if (renderedPlaceholders > 0) {
            if (ShortcutStripGeometry.isNarrow(state.span)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(dimensions.tilePadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (index in renderedPlaceholders - 1 downTo 0) {
                        Box(
                            modifier = Modifier
                                .size(dimensions.unitIconSize)
                                .background(placeholderTint, CircleShape)
                                .semantics {
                                    this.contentDescription = "Shortcut placeholder ${index + 1}"
                                },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensions.tilePadding),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (index in renderedPlaceholders - 1 downTo 0) {
                        Box(
                            modifier = Modifier
                                .size(dimensions.unitIconSize)
                                .background(placeholderTint, CircleShape)
                                .semantics {
                                    this.contentDescription = "Shortcut placeholder ${index + 1}"
                                },
                        )
                    }
                }
            }
        }
    }
}
