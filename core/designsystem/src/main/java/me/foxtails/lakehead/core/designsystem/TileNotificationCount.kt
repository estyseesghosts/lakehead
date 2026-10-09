package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.grid.GridSpan

/** Test tag on the visible notification count text. */
const val NOTIFICATION_COUNT_TAG: String = "tile-notification-count"

/** Digit height as a fraction of the font size (cap height of the sans-serif figures). */
private const val DIGIT_HEIGHT_PER_FONT_SIZE = 0.714f

/** Tabular-figure advance width relative to the digit height. */
private const val GLYPH_ADVANCE_PER_DIGIT_HEIGHT = 0.8f

/**
 * Share of the icon box covered by the visible glyph. Icon masks carry internal
 * padding, so the digit is matched to the visible glyph rather than the box.
 */
private const val ICON_VISIBLE_FRACTION = 0.75f

/** Smallest icon box a count-bearing tile shrinks to. */
private val MinCountedIconExtent = 24.dp

/**
 * `true` when [span] shows an unread count beside the app icon: a single
 * column or a single row (`1×1`, `1×N`, `N×1`). Every other span is a large
 * tile that shows the notification preview instead. Deliberately not
 * [isLargeTileSpan], which treats `1×3` and `4×1` as large.
 */
fun isCountTileSpan(span: GridSpan): Boolean = span.columns == 1 || span.rows == 1

/**
 * Visible count text: empty for a non-positive [count], otherwise the number
 * capped at `9+` on a one-column tile (so a `1×1` never needs three glyphs) and
 * `99+` on a row.
 */
fun notificationCountText(count: Int, span: GridSpan): String {
    if (count <= 0) return ""
    val cap = if (span.columns == 1) 9 else 99
    return if (count > cap) "$cap+" else count.toString()
}

/** Accessible text for an unread count: `"1 notification"` / `"N notifications"`. */
fun notificationCountDescription(count: Int): String {
    require(count > 0) { "count must be positive" }
    return if (count == 1) "1 notification" else "$count notifications"
}

/**
 * Shared sizes for the count-and-icon pair. [iconExtent] is the icon box;
 * [digitHeight] equals the visible glyph height inside it; [textWidth] is the
 * tabular-figure run width.
 */
data class CountedSymbolLayout(
    val iconExtent: Dp,
    val digitHeight: Dp,
    val textWidth: Dp,
)

/**
 * Fits the count text and icon into [availableWidth]: the digit height equals
 * the visible icon glyph (`iconExtent * 0.75`), both capped at [symbolSize].
 * The pair shrinks as `min(symbolSize, (available - gap) / (1 + textWidthRatio))`
 * in glyph terms, with the icon box floored at 24dp (or [symbolSize] if smaller);
 * past the floor only the digits shrink. Pure `Dp` maths.
 */
fun countedSymbolLayout(
    availableWidth: Dp,
    symbolSize: Dp,
    gap: Dp,
    characterCount: Int,
): CountedSymbolLayout {
    require(characterCount > 0) { "characterCount must be positive" }
    require(symbolSize > 0.dp) { "symbolSize must be positive" }
    val ratio = characterCount * GLYPH_ADVANCE_PER_DIGIT_HEIGHT
    val fitted = (availableWidth - gap).coerceAtLeast(0.dp) /
        (1f / ICON_VISIBLE_FRACTION + ratio)
    val iconExtent = maxOf(fitted / ICON_VISIBLE_FRACTION, minOf(MinCountedIconExtent, symbolSize))
        .coerceAtMost(symbolSize)
    val widthForDigits = (availableWidth - gap - iconExtent).coerceAtLeast(0.dp)
    val digitHeight = minOf(iconExtent * ICON_VISIBLE_FRACTION, widthForDigits / ratio)
    return CountedSymbolLayout(
        iconExtent = iconExtent,
        digitHeight = digitHeight,
        textWidth = digitHeight * ratio,
    )
}

/**
 * Count text immediately left of the icon, sharing the icon's colour
 * ([symbolTint]) and visible height. The text carries no semantics of its own;
 * the tile body announces the count through [notificationCountDescription].
 */
@Composable
internal fun CountedSymbol(
    text: String,
    layout: CountedSymbolLayout,
    gap: Dp,
    icon: (@Composable (symbolExtent: Dp) -> Unit)?,
) {
    val fontSize = with(LocalDensity.current) { (layout.digitHeight / DIGIT_HEIGHT_PER_FONT_SIZE).toSp() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = text,
            modifier = Modifier.clearAndSetSemantics { testTag = NOTIFICATION_COUNT_TAG },
            style = TextStyle(
                color = symbolTint(),
                fontSize = fontSize,
                lineHeight = fontSize,
                fontFamily = FontFamily.SansSerif,
                fontFeatureSettings = "tnum",
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(gap))
        icon?.invoke(layout.iconExtent)
    }
}
