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
import me.foxtails.lakehead.core.grid.GridSpan

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
 * Tall 1x2 tiles bottom-center the symbol; every other span bottom-ends it.
 * No placement legality or span-support checks: those stay owned by `core:grid`.
 */
fun symbolAlignment(span: GridSpan): Alignment =
    if (span.columns == 1 && span.rows == 2) Alignment.BottomCenter else Alignment.BottomEnd
