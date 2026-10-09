package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One notification row on a large tile: [name] (sender or conversation),
 * [body] and an optional circular [photo], already resolved at a platform
 * boundary. Presentation only; never persisted.
 */
@Immutable
data class TileNotificationPreview(
    val name: String,
    val body: String,
    val photo: ImageBitmap? = null,
)

/**
 * Everything a tile shows about its app's active notifications: the unread
 * [count] and the newest-first [previews] (possibly fewer than [count]).
 */
@Immutable
data class TileNotifications(
    val count: Int,
    val previews: List<TileNotificationPreview> = emptyList(),
) {
    init {
        require(count >= 0) { "count must be non-negative" }
    }
}

/** Width and height of the preview region, anchored at the tile padding corner. */
data class PreviewRegion(val width: Dp, val height: Dp)

/** Rows that fit and how many body lines the last row may use. */
data class NotificationPreviewLayout(val rows: Int, val lastBodyLines: Int)

internal val PreviewNameHeight = 16.dp
internal val PreviewLineHeight = 16.dp
internal val PreviewRowGap = 6.dp
internal val PreviewPhotoSize = 20.dp
private val PreviewPhotoGap = 6.dp

/** Height of a row with a one-line body. */
private val PreviewMinRowHeight = PreviewNameHeight + PreviewLineHeight

/**
 * Region above the bottom icon/strip row of a large tile: the tile minus
 * [padding] on every side and minus the bottom row ([extent] plus [gap]).
 * `null` when it cannot hold one row, so a preview never overlaps the strip or
 * the app icon. The region is taller than the strip's own reserve only by the
 * tile itself; it ignores edit controls because the preview is hidden while
 * the tile is edit-selected.
 */
fun notificationPreviewRegion(
    tileWidth: Dp,
    tileHeight: Dp,
    padding: Dp,
    gap: Dp,
    extent: Dp,
): PreviewRegion? {
    val width = tileWidth - padding * 2
    val height = tileHeight - padding * 2 - extent - gap
    if (width <= 0.dp || height < PreviewMinRowHeight) return null
    return PreviewRegion(width, height)
}

/**
 * Newest-first rows that fit [regionHeight] (at most [previewCount]); every
 * row but the last has a one-line body and the last takes the remaining
 * height, its body ellipsised to the lines that fit.
 */
fun notificationPreviewLayout(regionHeight: Dp, previewCount: Int): NotificationPreviewLayout {
    require(previewCount >= 0) { "previewCount must be non-negative" }
    if (previewCount == 0) return NotificationPreviewLayout(0, 0)
    val pitch = PreviewMinRowHeight + PreviewRowGap
    val fitting = ((regionHeight + PreviewRowGap) / pitch).toInt().coerceAtLeast(1)
    val rows = minOf(previewCount, fitting)
    val remaining = regionHeight - pitch * (rows - 1)
    val lines = ((remaining - PreviewNameHeight) / PreviewLineHeight).toInt().coerceAtLeast(1)
    return NotificationPreviewLayout(rows, lines)
}

/**
 * Newest-first preview rows. Carries no semantics: the tile body announces the
 * count and newest sender. Touches fall through to the tile body beneath.
 */
@Composable
internal fun NotificationPreviewList(
    previews: List<TileNotificationPreview>,
    layout: NotificationPreviewLayout,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textStyle = with(density) {
        TextStyle(color = color, fontSize = 12.dp.toSp(), lineHeight = PreviewLineHeight.toSp())
    }
    Column(
        modifier = modifier.clipToBounds().clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(PreviewRowGap),
    ) {
        previews.take(layout.rows).forEachIndexed { index, preview ->
            val lines = if (index == layout.rows - 1) layout.lastBodyLines else 1
            Row {
                preview.photo?.let { photo ->
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(PreviewPhotoSize).clip(CircleShape),
                    )
                    Spacer(Modifier.width(PreviewPhotoGap))
                }
                Column {
                    BasicText(
                        text = preview.name,
                        style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        text = preview.body,
                        style = textStyle,
                        maxLines = lines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
