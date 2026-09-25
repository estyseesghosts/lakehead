package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
 * the pressed container is the base at 70% alpha and the selection border is
 * `scheme.primary`. Only the role is persisted, never these resolved colors.
 */
@Composable
internal fun resolveTileColors(
    role: TileColorRole,
    isPressed: Boolean,
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
        container = if (isPressed) baseContainer.copy(alpha = 0.7f) else baseContainer,
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
 * - ShortcutSlot [Row]: visual-only bottom strip of neutral placeholder dots
 *   (`shortcutPlaceholders` count, default 0 renders none): non-clickable, never
 *   focusable, no [Role.Button]; each dot is a distinct semantics node
 *   ("Shortcut placeholder i+1"). A trailing sibling so order stays Body → Symbol →
 *   slots.
 *
 * A 1x1 tile is full-bleed (no [LauncherDimensions.tilePadding] inset; the tile
 * background fills the whole cell) while the symbol hit region stays icon-sized
 * ([LauncherDimensions.symbolSize] coerced to the 48.dp minimum) centered on the
 * tile, so ring/edge touches reach TileBody; larger spans keep the tile-padding inset with
 * the caller's natural symbol size anchored via [symbolAlignment]: tall 1x2 tiles
 * bottom-center the symbol, every other span bottom-ends it, so icons are never
 * centered.
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
 * is defensively clamped to the bottom-edge capacity for [AppTileState.span]
 * (the icon occupies one slot: single-column tiles allow `rows - 1`, wider
 * tiles allow `columns - 1`, coerced to >= 0), so over-counts render max
 * instead of crashing; negative counts are still rejected.
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
    // Bottom-edge capacity: the icon occupies one slot, so clamp over-counts to
    // max for this span (single-column: rows - 1; wider: columns - 1).
    val maxShortcutSlots = if (state.span.columns == 1) {
        (state.span.rows - 1).coerceAtLeast(0)
    } else {
        (state.span.columns - 1).coerceAtLeast(0)
    }
    val renderedPlaceholders = shortcutPlaceholders.coerceAtMost(maxShortcutSlots)
    val resolvedDescription = contentDescription ?: state.label
    val resolvedSymbolDescription = symbolContentDescription ?: resolvedDescription
    val tileColors = resolveTileColors(role = state.colorRole, isPressed = state.isPressed)
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
                    .align(symbolAlignment(state.span))
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(dimensions.tilePadding)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = resolvedSymbolDescription
                    }
                    .clickable(
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
        // ShortcutSlot row: visual-only bottom strip of neutral placeholder dots.
        // No clickable, never focusable, no Role.Button; each dot keeps its own
        // semantics node ("Shortcut placeholder i+1"), so the strip itself must not
        // merge descendants. Dots stay ~12-16dp visual glyphs and never force a 48dp
        // per-slot target, so 1x1 tiles (default 0, renders none) stay full-bleed.
        val placeholderTint = MaterialTheme.colorScheme.outlineVariant
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(dimensions.tilePadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(renderedPlaceholders) { index ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(placeholderTint, CircleShape)
                        .semantics {
                            this.contentDescription = "Shortcut placeholder ${index + 1}"
                        },
                )
            }
        }
    }
}
