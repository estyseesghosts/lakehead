package me.foxtails.lakehead.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.model.LogicalSpan

/**
 * Absolute Home tile layout: every tile is composed, measured and placed at the
 * frame [HomeGridMetrics] gives its logical row and column. Nothing is filtered
 * by visibility and no row is rebased, so a tile at row N sits at `top(N)` whichever
 * other rows are occupied.
 *
 * [renderedSpan] is the live placement span of a tile (the held resize preview
 * for the active tile, its committed span otherwise); positions are always the
 * committed ones because a MOVE is shown by an outline, not by moving the tile.
 *
 * The measured height is the lowest tile bottom plus [tailPx], floored at
 * [minHeightPx]. The scroll container wrapping this layout therefore scrolls
 * exactly the content that exists, never a separately computed height. The width
 * is the available width.
 */
@Composable
internal fun HomeGridLayout(
    tiles: List<HomeRenderTile>,
    metrics: HomeGridMetrics,
    renderedSpan: (HomeRenderTile) -> LogicalSpan,
    minHeightPx: Int,
    tailPx: Int,
    modifier: Modifier = Modifier,
    tileContent: @Composable (tile: HomeRenderTile, span: LogicalSpan) -> Unit,
) {
    require(minHeightPx >= 0) { "minHeightPx must be non-negative, was $minHeightPx" }
    require(tailPx >= 0) { "tailPx must be non-negative, was $tailPx" }
    Layout(
        content = {
            tiles.forEach { tile -> key(tile.itemId) { tileContent(tile, renderedSpan(tile)) } }
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            check(measurables.size == tiles.size) {
                "measurables (${measurables.size}) must match tiles (${tiles.size})"
            }
            val frames = tiles.map { tile -> metrics.pixelFrame(tile.position, renderedSpan(tile)) }
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixed(frames[index].widthPx, frames[index].heightPx))
            }
            // Fill the available width: the corner controls, the edit shortcut menu and the
            // background/pointer handlers live in this coordinate space, so a container that
            // wrapped the rightmost tile would clip them and miss touches beside the tiles.
            val width = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                (frames.maxOfOrNull { it.right } ?: 0).coerceAtLeast(constraints.minWidth)
            }
            val height = homeGridContentHeightPx(frames, minHeightPx, tailPx)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.place(frames[index].left, frames[index].top)
                }
            }
        },
    )
}

/**
 * Height of the content region: the lowest tile bottom (rest offset included, it
 * is part of the frame) plus [tailPx], never below [minHeightPx]. Saturates
 * instead of wrapping. Pure.
 */
internal fun homeGridContentHeightPx(
    frames: List<SignedPixelFrame>,
    minHeightPx: Int,
    tailPx: Int,
): Int {
    val lowestBottom = frames.maxOfOrNull { it.bottom.toLong() } ?: 0L
    val withTail = (lowestBottom + tailPx.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong())
    return maxOf(minHeightPx.toLong(), withTail).toInt()
}
