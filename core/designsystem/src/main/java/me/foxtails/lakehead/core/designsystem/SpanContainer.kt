package me.foxtails.lakehead.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import me.foxtails.lakehead.core.grid.GridPlacement

/**
 * One caller-supplied tile slot inside a [SpanContainer].
 *
 * [placement] is the tile's logical grid location and span, using the `core:grid`
 * coordinate/span types. [placement.id] is opaque caller identity carried through for
 * correspondence with caller state; it is never interpreted, deduplicated or persisted
 * here. [content] is the tile's caller-composed UI.
 */
class SpanChild<T : Any>(
    val placement: GridPlacement<T>,
    val content: @Composable () -> Unit,
)

/**
 * Custom Compose layout that measures and places caller-supplied tile slots at logical
 * grid positions.
 *
 * Each child is measured at exactly the pixel extent of its span
 * (`geometry.spanExtentPx`) and placed at `TileBounds.bounds(position, span, geometry,
 * originX, originY)`. The container wraps its content: its size is the maximum child
 * right/bottom edge coerced into the incoming constraints, so callers that need to fill
 * or scroll apply their own [modifier] or parent arrangement.
 *
 * This primitive performs no placement legality, collision, packing, span-support or
 * column-count checks: overlapping or out-of-grid placements are rendered overlapped in
 * list order, and whether a placement is legal remains owned by `core:grid`. It holds
 * no state, issues no events, touches no app inventory, persistence or Material
 * components; the [children] list, [geometry] and origin are the whole contract and are
 * owned by the caller.
 *
 * An empty [children] list is valid and occupies the incoming minimum constraints
 * (zero size when unconstrained), so transient empty states do not crash.
 *
 * @throws IllegalArgumentException when [originX] or [originY] is negative, or when any
 * child bound overflows [Int] pixels (surfaced from [TileBounds]).
 */
@Composable
fun <T : Any> SpanContainer(
    modifier: Modifier = Modifier,
    children: List<SpanChild<T>>,
    geometry: SixCellGeometry,
    originX: Int = 0,
    originY: Int = 0,
) {
    require(originX >= 0) { "originX must be non-negative, was $originX" }
    require(originY >= 0) { "originY must be non-negative, was $originY" }

    Layout(
        content = {
            children.forEach { child -> child.content() }
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val bounds = children.map { child ->
                TileBounds.bounds(
                    position = child.placement.position,
                    span = child.placement.span,
                    geometry = geometry,
                    originX = originX,
                    originY = originY,
                )
            }
            val placeables = bounds.mapIndexed { index, rect ->
                measurables[index].measure(Constraints.fixed(rect.width, rect.height))
            }
            val contentWidth = bounds.maxOfOrNull(PixelRect::right) ?: 0
            val contentHeight = bounds.maxOfOrNull(PixelRect::bottom) ?: 0
            val width = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.place(bounds[index].left, bounds[index].top)
                }
            }
        },
    )
}
