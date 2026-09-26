package me.foxtails.lakehead.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Launcher spacing/sizing tokens.
 *
 * See `docs/grid.md` section 3: the grid gap is fixed at 8.dp and the screen-edge
 * inset equals the gap (edge == gap B3 contract). These tokens carry the shared
 * gap, inset, tile padding, and symbol size so grid geometry and tile presentation
 * read from one source.
 *
 * @param gridGap spacing between grid cells (8.dp).
 * @param horizontalInset screen-edge inset, equal to [gridGap] per the edge == gap contract.
 * @param tilePadding inner padding applied inside an app tile (8.dp).
 * @param symbolSize preferred icon/symbol size inside a tile (40.dp).
 * @param unitIconSize reference 1x1 icon visual size reused for the compact app icon
 * and each pinned shortcut icon in customized large tiles (40.dp, equal to
 * [symbolSize]). See `docs/Oneshot/00/shortcuts.md` sections 2.1 and 13: pinned
 * shortcut icons and the compact app icon share one visual metric instead of
 * scaling with the large tile.
 */
@Immutable
data class LauncherDimensions(
    val gridGap: Dp,
    val horizontalInset: Dp,
    val tilePadding: Dp,
    val symbolSize: Dp,
    val unitIconSize: Dp,
)

/** CompositionLocal carrying the ambient [LauncherDimensions]; provided by [LauncherTheme]. */
val LocalLauncherDimensions = staticCompositionLocalOf<LauncherDimensions> {
    error("No LauncherDimensions provided; wrap content in LauncherTheme.")
}

/** Default launcher dimensions: 8.dp gap/inset/padding with a 40.dp symbol. */
val DefaultLauncherDimensions = LauncherDimensions(
    gridGap = 8.dp,
    horizontalInset = 8.dp,
    tilePadding = 8.dp,
    symbolSize = 40.dp,
    unitIconSize = 40.dp,
)
