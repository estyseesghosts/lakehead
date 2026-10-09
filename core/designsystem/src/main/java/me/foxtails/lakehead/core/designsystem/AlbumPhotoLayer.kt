package me.foxtails.lakehead.core.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import me.foxtails.lakehead.core.grid.GridSpan

/** Test tag on the album photo layer. */
const val ALBUM_PHOTO_TAG: String = "album-photo"

/** Test tag on the bottom scrim behind an album tile's shortcut strip. */
const val ALBUM_SCRIM_TAG: String = "album-scrim"

/**
 * `true` when [span] can carry an album photo: at least 2 columns and 2 rows.
 * Below that the tile renders its normal icon and keeps the binding, so
 * growing again restores the slideshow without a prompt.
 */
fun isAlbumPhotoSpan(span: GridSpan): Boolean = span.columns >= 2 && span.rows >= 2

/**
 * Full-bleed album photo with a crossfade on change. The photo is cropped to
 * fill and carries no content description: the tile body already exposes the
 * accessible name, so TalkBack skips this layer.
 */
@Composable
internal fun AlbumPhotoLayer(
    photo: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = photo,
        modifier = modifier
            .fillMaxSize()
            .testTag(ALBUM_PHOTO_TAG),
        label = "albumPhoto",
    ) { current ->
        Image(
            bitmap = current,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Bottom scrim behind an album tile's shortcut strip: a transparent-to-black
 * gradient exactly over the strip band ([stripHeight]) so glyphs stay legible
 * over bright photos. Drawn only when the tile has pins.
 */
@Composable
internal fun AlbumStripScrim(
    stripHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight)
            .testTag(ALBUM_SCRIM_TAG)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                ),
            ),
    )
}
