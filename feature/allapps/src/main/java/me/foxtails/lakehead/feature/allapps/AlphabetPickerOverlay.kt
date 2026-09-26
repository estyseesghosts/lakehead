package me.foxtails.lakehead.feature.allapps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.AlphabetPickerGrid
import me.foxtails.lakehead.core.designsystem.FloatingControlOverlay
import me.foxtails.lakehead.core.designsystem.FrameAnchor
import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.HorizontalFrameAnchor
import me.foxtails.lakehead.core.designsystem.OverlayLayer
import me.foxtails.lakehead.core.designsystem.VerticalFrameAnchor

/** Accessibility label on the full-viewport scrim; activating it dismisses the picker. */
const val ALPHABET_PICKER_SCRIM_DESCRIPTION: String = "Dismiss alphabet picker"

/** Test tag on the open picker overlay root (testing hook only; no behavior change). */
const val ALPHABET_PICKER_OVERLAY_TAG: String = "alphabet-picker-overlay"

/** Test tag on the scrim dim layer (testing hook only; no behavior change). */
const val ALPHABET_PICKER_SCRIM_TAG: String = "alphabet-picker-scrim"

/** Overlay layer id for the dismiss scrim; routes [FloatingControlOverlay] activation to dismiss. */
internal const val SCRIM_LAYER_ID: String = "alphabet-picker-scrim"

/** Overlay layer id for the centered picker grid; activation is a no-op (never dismisses). */
internal const val PICKER_LAYER_ID: String = "alphabet-picker"

/** Appear/dismiss animation duration in ms (spec range 120–150ms). */
internal const val PICKER_ANIMATION_MILLIS: Int = 130

/**
 * Floating P4 alphabet picker overlay.
 *
 * Presentation only: [picker] carries transient open/enablement, [onDismiss] closes
 * without navigating, [onLetterPicked] reports the tapped [String] key (caller jumps
 * and closes), and [backgroundContent] is the caller-composed All Apps list. No
 * catalogue, persistence, grouping, search, or jump execution lives here.
 *
 * - Closed (`!picker.isOpen`): [backgroundContent] alone, no scrim/grid/animation.
 * - Open: [FloatingControlOverlay] over [backgroundContent] with two layers —
 *   a full-viewport scrim dim (`Black` alpha 0.45, [ALPHABET_PICKER_SCRIM_DESCRIPTION],
 *   activation dismisses) below a centered [AlphabetPickerGrid]. The grid adapts the
 *   [String] enablement to [Char] via [enabledChars]; taps report
 *   `onLetterPicked(letter.toString())`. Disabled letters expose no click action, so
 *   tapping them is a no-op that stays open; spares are inert.
 * - `BackHandler(enabled = isOpen)` dismisses; while open the back press is consumed
 *   here and never propagates to the caller pager/list.
 * - Appear animates scale 0.92→1 plus alpha via [animateFloatAsState] at
 *   [PICKER_ANIMATION_MILLIS]ms. Dismiss is state-driven (parent sets `isOpen=false`)
 *   so there is no exit animation window; the same spec values govern appear.
 * - `isOpen` is the pager-block flag: callers disable horizontal paging while
 *   `picker.isOpen` (see [blocksPager]).
 *
 * Sizes are viewport-derived ([BoxWithConstraints] + density) so both frames always
 * fit: scrim fills the viewport, picker is centered at viewport minus rhythm margins
 * clamped to the viewport.
 */
@Composable
fun AlphabetPickerOverlay(
    picker: AlphabetPickerState,
    onDismiss: () -> Unit,
    onLetterPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    backgroundContent: @Composable () -> Unit,
) {
    if (!picker.isOpen) {
        Box(modifier = modifier) {
            backgroundContent()
        }
        return
    }
    BackHandler(enabled = true, onBack = onDismiss)

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.92f,
        animationSpec = tween(durationMillis = PICKER_ANIMATION_MILLIS),
        label = "alphabet-picker-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = PICKER_ANIMATION_MILLIS),
        label = "alphabet-picker-alpha",
    )
    val enabled = remember(picker.enabledLetters) { picker.enabledChars() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val horizontalMarginPx = with(density) { 32.dp.roundToPx() } * 2
        val verticalMarginPx = with(density) { 64.dp.roundToPx() } * 2
        val pickerWidthPx = (viewportWidthPx - horizontalMarginPx)
            .coerceAtLeast(1)
            .coerceAtMost(viewportWidthPx)
        val desiredHeightPx = (pickerWidthPx.toLong() * 7L / 4L)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        val maxPickerHeightPx = (viewportHeightPx - verticalMarginPx)
            .coerceAtLeast(1)
            .coerceAtMost(viewportHeightPx)
        val pickerHeightPx = minOf(desiredHeightPx, maxPickerHeightPx)
        val surface = MaterialTheme.colorScheme.surface

        FloatingControlOverlay(
            modifier = Modifier
                .fillMaxSize()
                .testTag(ALPHABET_PICKER_OVERLAY_TAG),
            backgroundContent = backgroundContent,
            overlays = listOf(
                OverlayLayer(
                    id = SCRIM_LAYER_ID,
                    priority = 1,
                    anchor = FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
                    size = FrameSize(viewportWidthPx, viewportHeightPx),
                    contentDescription = ALPHABET_PICKER_SCRIM_DESCRIPTION,
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                                .testTag(ALPHABET_PICKER_SCRIM_TAG),
                        )
                    },
                ),
                OverlayLayer(
                    id = PICKER_LAYER_ID,
                    priority = 10,
                    anchor = FrameAnchor(
                        HorizontalFrameAnchor.CENTER,
                        VerticalFrameAnchor.CENTER,
                    ),
                    size = FrameSize(pickerWidthPx, pickerHeightPx),
                    contentDescription = "Alphabet picker",
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .background(surface),
                        ) {
                            AlphabetPickerGrid(
                                enabledLetters = enabled,
                                onLetterTap = { letter -> onLetterPicked(letter.toString()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                ),
            ),
            onOverlayActivated = { id ->
                if (id == SCRIM_LAYER_ID) onDismiss()
            },
        )
    }
}
