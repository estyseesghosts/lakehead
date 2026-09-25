package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Focus behavior applied to background content while at least one overlay is present.
 *
 * This policy only controls whether background content stays exposed to focus and
 * accessibility services. It never dismisses an overlay: what an overlay activation
 * means (dismiss, keep, navigate) is decided by the caller through [onOverlayActivated].
 */
enum class OverlayFocusPolicy {
    /**
     * Background content cannot take focus and is removed from the merged semantics
     * tree while overlays are present, so focus and screen readers stay within the
     * overlay layers. Background content remains composed underneath.
     */
    TRAP_FOCUS_IN_OVERLAYS,

    /**
     * Background content keeps its own focus and semantics behavior. The caller owns
     * focus order between background and overlay content.
     */
    LEAVE_FOCUS_UNCHANGED,
}

/**
 * One caller-supplied floating layer inside a [FloatingControlOverlay].
 *
 * [id] is opaque caller identity carried through to [onOverlayBoundaryMeasured] and
 * [onOverlayActivated]; it is never interpreted or persisted here and must be unique
 * within one overlay. [priority] orders hit testing: higher values paint nearer the
 * viewer and win overlapping input (see [OverlayHitTestOrder]). [anchor], [size] and
 * [insets] locate the layer with [FloatingOverlayFrame] against the measured viewport.
 * [contentDescription] labels the layer's interactive boundary for accessibility;
 * [content] is the layer's caller-composed UI, rendered at exactly the measured frame
 * size and top-start aligned inside the interactive boundary.
 */
class OverlayLayer(
    val id: String,
    val priority: Int,
    val anchor: FrameAnchor,
    val size: FrameSize,
    val insets: FrameInsets = FrameInsets(),
    val contentDescription: String,
    val content: @Composable () -> Unit,
) {
    init {
        require(id.isNotBlank()) { "OverlayLayer id must be non-blank" }
        require(contentDescription.isNotBlank()) {
            "OverlayLayer contentDescription must be non-blank for accessibility"
        }
    }
}

/**
 * Pure hit-test ordering for overlay layers.
 *
 * Layers sort ascending by [OverlayLayer.priority] so higher-priority layers compose
 * later and nearer the viewer. Ties keep caller list order (stable sort): the later
 * list entry wins overlapping input. This function decides order only; it performs no
 * placement legality, packing or dismissal, which remain owned by `core:grid` and the
 * caller respectively.
 */
object OverlayHitTestOrder {
    /** Returns [layers] in paint order: lowest priority first, highest last. */
    fun sort(layers: List<OverlayLayer>): List<OverlayLayer> = layers.sortedBy(OverlayLayer::priority)
}

/**
 * Custom Compose primitive that floats caller-supplied control layers above background
 * content.
 *
 * Each layer's frame is measured with [FloatingOverlayFrame] against the measured
 * viewport bounds, then coerced to at least [minTouchTargetDp] per axis to form the
 * interactive boundary. The coerced boundary is reported per layer through
 * [onOverlayBoundaryMeasured]; visual content keeps the uncoerced frame size aligned
 * to the boundary origin. Layers compose in [OverlayHitTestOrder] with matching
 * z-order, so [OverlayLayer.priority] configures which layer wins overlapping input.
 * Each layer keeps independent semantics: the container merges nothing and invents no
 * labels, and only layers supply their own [OverlayLayer.contentDescription].
 *
 * The container holds no state, touches no app inventory, database, persistence,
 * navigation destinations or Material components. [onOverlayActivated] reports taps on
 * a layer's boundary; whether activation dismisses anything is the caller's policy and
 * is intentionally absent here. Grid placement legality remains owned by `core:grid`.
 *
 * @throws IllegalArgumentException when layer ids are duplicated, [minTouchTargetDp]
 * is not positive, or the incoming constraints are unbounded.
 */
@Composable
fun FloatingControlOverlay(
    modifier: Modifier = Modifier,
    backgroundContent: @Composable () -> Unit,
    overlays: List<OverlayLayer>,
    focusPolicy: OverlayFocusPolicy = OverlayFocusPolicy.TRAP_FOCUS_IN_OVERLAYS,
    minTouchTargetDp: Dp = 48.dp,
    onOverlayBoundaryMeasured: (id: String, bounds: PixelRect) -> Unit = { _, _ -> },
    onOverlayActivated: ((id: String) -> Unit)? = null,
) {
    require(overlays.map(OverlayLayer::id).toSet().size == overlays.size) {
        "OverlayLayer ids must be unique within one FloatingControlOverlay"
    }
    require(minTouchTargetDp > 0.dp) {
        "minTouchTargetDp must be positive, was $minTouchTargetDp"
    }

    BoxWithConstraints(modifier = modifier) {
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "FloatingControlOverlay requires bounded viewport constraints"
        }
        val viewport = PixelRect(
            left = 0,
            top = 0,
            right = constraints.maxWidth,
            bottom = constraints.maxHeight,
        )
        val minTouchTargetPx = with(LocalDensity.current) { minTouchTargetDp.roundToPx() }
        require(minTouchTargetPx > 0) {
            "minTouchTargetDp=$minTouchTargetDp rounds to no pixels at this density"
        }

        val ordered = remember(overlays) { OverlayHitTestOrder.sort(overlays) }
        val frames = ordered.map { layer ->
            FloatingOverlayFrame.measure(
                viewport = viewport,
                size = layer.size,
                anchor = layer.anchor,
                insets = layer.insets,
            )
        }
        val interactiveBounds = frames.map { frame -> frame.coerceMinTouchTarget(minTouchTargetPx) }

        ordered.forEachIndexed { index, layer ->
            val bounds = interactiveBounds[index]
            LaunchedEffect(layer.id, bounds) {
                onOverlayBoundaryMeasured(layer.id, bounds)
            }
        }

        val trapBackground = focusPolicy == OverlayFocusPolicy.TRAP_FOCUS_IN_OVERLAYS &&
            ordered.isNotEmpty()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (trapBackground) {
                        Modifier
                            .focusProperties { canFocus = false }
                            .clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                ),
        ) {
            backgroundContent()
        }

        ordered.forEachIndexed { index, layer ->
            val frame = frames[index]
            val bounds = interactiveBounds[index]
            key(layer.id) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(bounds.left, bounds.top) }
                        .size(
                            width = with(LocalDensity.current) { bounds.width.toDp() },
                            height = with(LocalDensity.current) { bounds.height.toDp() },
                        )
                        .zIndex(layer.priority.toFloat())
                        .semantics(mergeDescendants = false) {
                            contentDescription = layer.contentDescription
                        }
                        .then(
                            if (onOverlayActivated != null) {
                                Modifier.clickable(
                                    role = Role.Button,
                                    onClick = { onOverlayActivated(layer.id) },
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Box(
                        modifier = Modifier.size(
                            width = with(LocalDensity.current) { frame.width.toDp() },
                            height = with(LocalDensity.current) { frame.height.toDp() },
                        ),
                    ) {
                        layer.content()
                    }
                }
            }
        }
    }
}

/**
 * Expands this frame's right/bottom edges so the result spans at least [minTouchTargetPx]
 * on each axis, keeping the measured origin. The expanded edges must fit [Int].
 */
private fun PixelRect.coerceMinTouchTarget(minTouchTargetPx: Int): PixelRect {
    val right = maxOf(right.toLong(), left.toLong() + minTouchTargetPx)
    val bottom = maxOf(bottom.toLong(), top.toLong() + minTouchTargetPx)
    require(right in 1..Int.MAX_VALUE.toLong()) {
        "interactive right edge overflows Int: $right"
    }
    require(bottom in 1..Int.MAX_VALUE.toLong()) {
        "interactive bottom edge overflows Int: $bottom"
    }
    return PixelRect(left = left, top = top, right = right.toInt(), bottom = bottom.toInt())
}
