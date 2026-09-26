package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure pixel geometry for a floating circular-button navigation control.
 *
 * The pill is a horizontal stadium whose height derives from one button row:
 * `pillHeightPx = buttonDiameterPx + 2 * outerPaddingPx`, and whose width packs
 * [buttonCount] generic circular slots: `pillWidthPx = buttonCount *
 * buttonDiameterPx + (buttonCount - 1) * buttonGapPx + 2 * outerPaddingPx`.
 * Both radii are concentric derivations of those extents — `outerRadiusPx =
 * pillHeightPx / 2f` and `buttonRadiusPx = buttonDiameterPx / 2f` — never
 * unrelated constants. Integer center math floors halves.
 *
 * Slots are generic positions, not destinations: [buttonCount] is any positive
 * caller-owned slot count. Which screens show how many controls, where the pill
 * sits, and what a press means are screen arrangement decisions owned by
 * `:feature:*`, not this contract. Placement legality and packing remain owned
 * by `core:grid`.
 */
data class NavigationControlGeometry(
    val buttonDiameterPx: Int,
    val buttonGapPx: Int,
    val outerPaddingPx: Int,
    val buttonCount: Int,
) {
    init {
        require(buttonDiameterPx > 0) {
            "buttonDiameterPx must be positive, was $buttonDiameterPx"
        }
        require(buttonGapPx >= 0) { "buttonGapPx must be non-negative, was $buttonGapPx" }
        require(outerPaddingPx >= 0) {
            "outerPaddingPx must be non-negative, was $outerPaddingPx"
        }
        require(buttonCount > 0) { "buttonCount must be positive, was $buttonCount" }
    }

    /** Pill height in pixels: one button diameter plus padding on both edges. */
    val pillHeightPx: Int = checkedSum(
        buttonDiameterPx.toLong(),
        2L * outerPaddingPx,
        "pill height",
    ).toPositivePixel("pill height")

    /** Pill width in pixels: packed buttons, internal gaps, and padding on both edges. */
    val pillWidthPx: Int = checkedSum(
        checkedSum(
            checkedMultiply(buttonCount.toLong(), buttonDiameterPx.toLong(), "button widths"),
            (buttonCount - 1).toLong() * buttonGapPx,
            "button gaps",
        ),
        2L * outerPaddingPx,
        "pill width",
    ).toPositivePixel("pill width")

    /** Outer pill radius: exactly half the pill height (concentric). */
    val outerRadiusPx: Float = pillHeightPx / 2f

    /** Circular button radius: exactly half the button diameter (concentric). */
    val buttonRadiusPx: Float = buttonDiameterPx / 2f

    /**
     * Horizontal center of the [index]-th slot measured from the pill's left edge.
     *
     * @throws IllegalArgumentException when [index] is outside `0 until buttonCount`.
     */
    fun buttonCenterXPx(index: Int): Int {
        require(index in 0 until buttonCount) {
            "index must be in 0 until $buttonCount, was $index"
        }
        val center = outerPaddingPx.toLong() + buttonDiameterPx / 2 +
            index.toLong() * (buttonDiameterPx.toLong() + buttonGapPx)
        return center.toPositivePixel("button center x")
    }

    /** Vertical center of every slot measured from the pill's top edge. */
    fun buttonCenterYPx(): Int =
        (outerPaddingPx.toLong() + buttonDiameterPx / 2).toPositivePixel("button center y")

    private fun checkedMultiply(first: Long, second: Long, description: String): Long =
        try {
            Math.multiplyExact(first, second)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("$description overflows Long", exception)
        }

    private fun checkedSum(first: Long, second: Long, description: String): Long =
        try {
            Math.addExact(first, second)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("$description overflows Long", exception)
        }

    private fun Long.toPositivePixel(description: String): Int {
        require(this in 1..Int.MAX_VALUE.toLong()) {
            "$description overflows Int or is not positive: $this"
        }
        return toInt()
    }
}

/** Minimum touch target applied to every navigation control button, in [Dp]. */
val NavigationControlMinTouchTarget: Dp = 48.dp

/**
 * Resolved fills for one navigation control button.
 *
 * Selected and unselected fills are derived from [MaterialTheme.colorScheme]
 * per docs/grid.md section 12 so the control follows app theming instead of
 * fixed dark fills.
 */
internal data class NavigationControlColors(
    val container: Color,
    val content: Color,
    val outline: Color,
)

/** Resolves [NavigationControlColors] for the given [selected] presentation input. */
@Composable
internal fun resolveNavigationControlColors(selected: Boolean): NavigationControlColors {
    // Theming source per docs/grid.md section 12: selected uses the primary
    // container family, unselected uses the surface-container family.
    val scheme = MaterialTheme.colorScheme
    return if (selected) {
        NavigationControlColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            outline = scheme.primary,
        )
    } else {
        NavigationControlColors(
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant,
            outline = scheme.outline,
        )
    }
}

/**
 * One custom-drawn circular navigation control.
 *
 * The circle, pill-compatible border, and minimum touch target are drawn from
 * Foundation/graphics primitives ([CircleShape], [background], [border]); no
 * Material navigation, chip, or button component is used. [selected] and
 * [enabled] are caller-owned presentation inputs rendered as fill/border
 * emphasis and reduced alpha — they carry no destination or navigation state.
 * [contentDescription] is the required caller-supplied TalkBack label and must
 * be non-blank. [onPress] is a raw press signal only: it navigates nowhere and
 * changes no selection by itself. [onLongPress] is an optional raw long-press
 * signal (same ownership as [onPress]): when non-null the button exposes a
 * long-click action alongside the press; when null the button uses a plain
 * click with no long-click node. Slot [content] must be a label-free glyph;
 * the button merges descendants into a single accessible node.
 *
 * The caller's [modifier] is preserved within an enforced 48dp minimum touch
 * target, so undersized caller sizes cannot shrink the control below the
 * accessible minimum.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavigationControlButton(
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    require(contentDescription.isNotBlank()) { "contentDescription must be non-blank" }

    val colors = resolveNavigationControlColors(selected)
    // Null long-press keeps a plain click (no long-click semantics node);
    // non-null upgrades to a combined click that preserves the press signal.
    val interactionModifier = if (onLongPress != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongPress,
            onClick = onPress,
        )
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onPress)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .sizeIn(
                minWidth = NavigationControlMinTouchTarget,
                minHeight = NavigationControlMinTouchTarget,
            )
            .then(modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape)
            .background(colors.container)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = colors.outline,
                shape = CircleShape,
            )
            .then(interactionModifier)
            .semantics(mergeDescendants = true) {
                // Note: no `disabled()` marker is set here. `clickable(enabled =
                // false)` already exposes the disabled semantics through the
                // Foundation clickable implementation, which carries the
                // required opt-in internally; duplicating it would force this
                // primitive onto an experimental UI API.
                this.contentDescription = contentDescription
                this.selected = selected
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            content()
        }
    }
}

/**
 * Generic floating pill frame that hosts caller-composed circular controls.
 *
 * The stadium clip (`50%` corner radius) keeps the pill concentric with the
 * circular buttons it holds; [outerPadding] and [buttonGap] control the inset
 * and spacing. The frame holds no selection, destination, or navigation state,
 * issues no events, and adds no click action of its own — arrangement, count,
 * order, and press meaning belong to the calling screen.
 */
@Composable
fun NavigationControlPill(
    modifier: Modifier = Modifier,
    outerPadding: Dp = 12.dp,
    buttonGap: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    require(outerPadding.value >= 0f) { "outerPadding must be non-negative, was $outerPadding" }
    require(buttonGap.value >= 0f) { "buttonGap must be non-negative, was $buttonGap" }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(outerPadding),
        horizontalArrangement = Arrangement.spacedBy(buttonGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
