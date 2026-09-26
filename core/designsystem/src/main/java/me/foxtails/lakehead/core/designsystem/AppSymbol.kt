package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry

/**
 * Pre-resolved app symbol.
 *
 * Renders a caller-supplied [ImageBitmap] snapshot at [LauncherDimensions.symbolSize]
 * with [ContentScale.Fit]. Callers resolve the bitmap outside composition (cached
 * metadata with callback invalidation); this composable never touches LauncherApps,
 * PackageManager, or icon decoding.
 *
 * Draw-time monochrome tint: applies [ColorFilter.tint] with the theme `onSurface`
 * colour so every cached normalised PNG (white-on-transparent alpha mask from the
 * platform encoder) renders in the current theme's foreground colour. The tint
 * follows light/dark automatically because it reads `MaterialTheme.colorScheme.onSurface`
 * at composition time; features receive theme-adaptive monochrome icons with no edits.
 */
@Composable
fun AppSymbol(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        modifier = modifier.then(Modifier.size(LocalLauncherDimensions.current.symbolSize)),
    )
}

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
 * Zero pinned shortcuts means centered icon (`docs/Oneshot/00/shortcuts.md`
 * section 2.2: enlarged, aspect-preserved, no reserved shortcut space). One or
 * more pinned shortcuts reuses the compact large-tile anchor by delegating to
 * the `core:grid` strip-geometry anchor contract
 * ([ShortcutStripGeometry.anchorFor]): `BOTTOM_CENTER` maps to bottom-center,
 * `BOTTOM_RIGHT` maps to bottom-end. A 1×1 tile anchors bottom-end (wide) per
 * `docs/design.md` and `docs/grid.md` §4; narrow single-column multi-row tiles
 * (1×2, 1×3 and future 1×N forms) anchor bottom-center.
 *
 * Alignment mapping only, no legality checks: placement legality stays owned
 * by `core:grid`.
 *
 * @param hasPinned whether the tile has at least one launcher-pinned shortcut
 * (launcher-tile pinning, not Android-platform pinning).
 */
fun symbolAlignment(span: GridSpan, hasPinned: Boolean): Alignment {
    if (!hasPinned) return Alignment.Center
    return when (ShortcutStripGeometry.anchorFor(span)) {
        ShortcutStripGeometry.ShortcutAnchor.BOTTOM_CENTER -> Alignment.BottomCenter
        ShortcutStripGeometry.ShortcutAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
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
