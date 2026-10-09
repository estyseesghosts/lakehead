package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import me.foxtails.lakehead.core.grid.GridSpan

/**
 * Pre-resolved app symbol.
 *
 * Renders a caller-supplied [ImageBitmap] snapshot at [extent] (default
 * [LauncherDimensions.symbolSize]) with [ContentScale.Fit]. Callers resolve
 * the bitmap outside composition (cached metadata with callback
 * invalidation); this composable never touches LauncherApps,
 * PackageManager, or icon decoding.
 *
 * Large tiles with zero pinned shortcuts pass
 * [centeredIconExtent] (the smaller of the tile's available width/height
 * after padding, with no strip reserve) so the icon scales proportionally
 * to the tile instead of staying at the 1×1 reference size; every other
 * path keeps the default extent.  Aspect preservation stays with
 * [ContentScale.Fit] in both cases.
 *
 * Draw-time monochrome tint: applies [ColorFilter.tint] with the theme `onSurface`
 * colour so every cached normalised PNG (white-on-transparent alpha mask from the
 * platform encoder) renders in the current theme's foreground colour. The tint
 * follows light/dark automatically because it reads `MaterialTheme.colorScheme.onSurface`
 * at composition time; features receive theme-adaptive monochrome icons with no edits.
 *
 * @param extent square visual extent of the rendered symbol.  The trailing
 * `Modifier.size(extent)` applies after the caller's [modifier], so a
 * caller-supplied size is refined to this square rather than stacked with
 * the default: pass an explicit value only to scale the symbol (the large
 * centered-tile path), never to restate the default.
 */
@Composable
fun AppSymbol(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    extent: Dp = LocalLauncherDimensions.current.symbolSize,
) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(symbolTint()),
        modifier = modifier.then(Modifier.size(extent)),
    )
}

/** Single source of the colour shared by the app symbol and the tile's notification count. */
@Composable
internal fun symbolTint(): Color = MaterialTheme.colorScheme.onSurface

/**
 * Pure alignment rule for an app symbol inside its tile.
 *
 * Single-span form delegates to [symbolAlignment] with `hasPinned = true`: it
 * preserves the compact shortcut-layout anchor (reuse the existing anchor, no new
 * offset) for the current strip caller. No placement legality or span-support
 * checks: those stay owned by `core:grid`.
 */
fun symbolAlignment(span: GridSpan): Alignment = symbolAlignment(span, hasPinned = true)

/**
 * Pure alignment rule for an app symbol inside its tile, given pin state.
 *
 * Zero pinned shortcuts keeps the unpinned anchor from `docs/grid.md`
 * section 4: bottom-right on every span except `1×2`, which is
 * bottom-center. The rule is generic over future spans (only the `1×2`
 * exception is special-cased). One or more pinned shortcuts uses the
 * compact strip anchor: wide and square tiles anchor the app icon
 * bottom-end (rightmost slot of the bottom-centered group), and narrow
 * single-column multi-row tiles anchor it bottom-end on the right edge
 * with shortcuts extending upward (C1 layout contract).
 * A 1×1 tile anchors bottom-end (capacity 0 keeps it pin-free in practice).
 *
 * Alignment mapping only, no legality checks: placement legality stays owned
 * by `core:grid`.
 *
 * @param hasPinned whether the tile has at least one launcher-pinned shortcut
 * (launcher-tile pinning, not Android-platform pinning).
 */
fun symbolAlignment(span: GridSpan, hasPinned: Boolean): Alignment {
    if (!hasPinned) {
        return if (span.columns == 1 && span.rows == 2) Alignment.BottomCenter else Alignment.BottomEnd
    }
    return Alignment.BottomEnd
}

/**
 * Centered icon extent for a large tile with zero pinned shortcuts.
 *
 * `docs/Oneshot/00/shortcuts.md` section 2.2: the icon scales proportionally to
 * the tile's content area without reserving shortcut space, so the binding
 * dimension is the smaller of the available width/height (callers subtract
 * insets before calling; aspect preservation stays with `ContentScale.Fit`).
 */
fun centeredIconExtent(availableWidth: Dp, availableHeight: Dp): Dp =
    minOf(availableWidth, availableHeight)
