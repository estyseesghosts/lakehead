package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.lakehead.core.grid.GridSpan

/**
 * Presentation-only state for a small reusable app tile.
 *
 * [label] is the visible, non-blank app name. [span] selects the tile's internal
 * arrangement (wide spans place the icon beside the label; square and tall spans stack
 * the icon above the label) but performs no placement legality, span-support or
 * column-count checks: whether the span may be placed remains owned by `core:grid`.
 * [isSelected] and [isPressed] are caller-owned transient visual states; this tile never
 * derives them from inventory, persistence or its own interaction source.
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

    val TileShape = RoundedCornerShape(12.dp)
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
 * Small reusable app-tile primitive.
 *
 * Custom Compose Foundation/graphics/text only: no Material product components, no app
 * inventory, database, persistence, navigation or business logic. Callers supply the
 * visible [AppTileState.label], the optional caller-composed [icon] slot, click and
 * optional long-click callbacks, and an optional [contentDescription] override (which
 * defaults to the label so the tile always has an accessible name).
 *
 * Accessibility: the tile merges descendants into a single focusable node with
 * [Role.Button], the resolved content description, the `selected` flag for
 * [AppTileState.isSelected], and [AppTileDefaults.PressedStateDescription] while
 * [AppTileState.isPressed] is true. Pressed/selected visuals are caller-driven through
 * state; the tile holds no state and issues no events beyond the supplied callbacks.
 *
 * @param onLongClick when null the tile exposes no long-click action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    state: AppTileState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    require(contentDescription == null || contentDescription.isNotBlank()) {
        "contentDescription must be null or non-blank"
    }
    val resolvedDescription = contentDescription ?: state.label
    val tileColors = resolveTileColors(role = state.colorRole, isPressed = state.isPressed)
    val containerColor = tileColors.container

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = resolvedDescription
                this.selected = state.isSelected
                if (state.isPressed) {
                    this.stateDescription = AppTileDefaults.PressedStateDescription
                }
            }
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick,
                role = Role.Button,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.span.columns > state.span.rows) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (icon != null) {
                    icon()
                }
                AppTileLabel(state.label, tileColors.content)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                if (icon != null) {
                    icon()
                }
                AppTileLabel(state.label, tileColors.content)
            }
        }
    }
}

@Composable
private fun AppTileLabel(label: String, contentColor: Color) {
    BasicText(
        text = label,
        style = TextStyle(
            color = contentColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
