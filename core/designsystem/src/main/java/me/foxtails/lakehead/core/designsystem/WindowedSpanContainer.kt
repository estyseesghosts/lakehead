package me.foxtails.lakehead.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import me.foxtails.lakehead.core.grid.GridRowWindow
import me.foxtails.lakehead.core.grid.GridVisiblePlacements
import java.util.IdentityHashMap

/**
 * Rebasing math for [WindowedSpanContainer], kept in a pure object so overflow and
 * selection behavior can be tested without composition.
 *
 * Selection itself is delegated to grid-owned [GridVisiblePlacements.select]: this object
 * never re-derives visibility, legality, packing, or extent policy. It only joins the
 * grid-selected placements back to caller [SpanChild] entries (preserving caller order)
 * and rebases logical rows around a nearby origin so distant [Int]-bounded rows do not
 * overflow Compose's [Int] pixel representation.
 */
object WindowedSpanLayout {
    /**
     * One selected child with its rebased pixel frame.
     *
     * [left] is always non-negative; [top] may be negative when the child's span starts
     * above the row origin (a span crossing the lower window boundary). [widthPx] and
     * [heightPx] are always positive. [right] and [bottom] always fit [Int].
     */
    data class PlacedChild<T : Any>(
        val child: SpanChild<T>,
        val left: Int,
        val top: Int,
        val widthPx: Int,
        val heightPx: Int,
    ) {
        val right: Int
            get() = left + widthPx

        val bottom: Int
            get() = top + heightPx
    }

    /**
     * Resolves the effective logical row origin: the caller override when supplied,
     * otherwise the overscanned window's first row.
     *
     * The origin is signed and may be negative (e.g. a signed content `minRow`
     * above row zero). Only [overscanRows] must be non-negative.
     *
     * @throws IllegalArgumentException when [overscanRows] is negative.
     */
    fun resolveRowOrigin(
        window: GridRowWindow,
        overscanRows: Long,
        rowOrigin: Long? = null,
    ): Long {
        require(overscanRows >= 0L) { "overscanRows must be non-negative, was $overscanRows" }
        if (rowOrigin != null) {
            // Touch the window's overscan validation so an invalid overscan is still
            // reported explicitly even when the caller overrides the origin.
            window.withOverscan(overscanRows)
            return rowOrigin
        }
        return window.withOverscan(overscanRows).firstRow
    }

    /**
     * Selects the caller children intersecting [window] expanded by [overscanRows] and
     * rebases their rows around the effective origin for safe local pixel coordinates.
     *
     * Order follows the caller's [children] list; nothing is sorted, deduplicated, or
     * legality-checked. Children outside the grid-selected window are omitted from the
     * result (they remain available to the caller and render when scrolled into range).
     * No empty-row spacers are produced: the result contains exactly the selected
     * children.
     *
     * The [rowOrigin] is signed and may be negative (a signed content `minRow`);
     * [checkedTop] stays signed-tolerant (negative tops for spans starting above the
     * origin) while [checkedLeft] remains non-negative.
     *
     * @throws IllegalArgumentException for negative [overscanRows]/[originX],
     * or when a selected child's rebased frame cannot be represented in [Int] pixels
     * (reported explicitly instead of silently dropping the child).
     */
    fun <T : Any> boundsFor(
        children: List<SpanChild<T>>,
        window: GridRowWindow,
        overscanRows: Long,
        geometry: SquareCellGeometry,
        originX: Int = 0,
        rowOrigin: Long? = null,
    ): PlacedBounds<T> {
        require(originX >= 0) { "originX must be non-negative, was $originX" }
        val origin = resolveRowOrigin(window, overscanRows, rowOrigin)
        if (children.isEmpty()) return PlacedBounds(origin = origin, placed = emptyList())

        val placements = children.map { it.placement }
        val selected = GridVisiblePlacements.select(
            placements = placements,
            visibleRows = window,
            overscanRows = overscanRows,
        )
        // Join by reference identity so duplicate/equal placements keep per-entry content
        // and input order without a second row-interval policy here. `select` filters the
        // same instances, so membership is by reference, not equality.
        val selectedIdentities: MutableSet<me.foxtails.lakehead.core.grid.GridPlacement<T>> =
            java.util.Collections.newSetFromMap(
                IdentityHashMap<me.foxtails.lakehead.core.grid.GridPlacement<T>, Boolean>(),
            )
        selectedIdentities.addAll(selected)
        val visible = children.filter { child -> selectedIdentities.contains(child.placement) }

        val placed = visible.map { child ->
            val extent = geometry.spanExtentPx(
                child.placement.span.columns,
                child.placement.span.rows,
            )
            val left = checkedLeft(originX, child.placement.position.column, geometry)
            val top = checkedTop(child.placement.position.row.toLong(), origin, geometry)
            val right = checkedAddExact(left.toLong(), extent.widthPx.toLong(), "right")
                .toPixelCoordinate("right", allowNegative = false)
            val bottom = checkedAddExact(top.toLong(), extent.heightPx.toLong(), "bottom")
                .toPixelCoordinate("bottom", allowNegative = true)
            PlacedChild(
                child = child,
                left = left,
                top = top,
                widthPx = extent.widthPx,
                heightPx = extent.heightPx,
            ).also {
                // Eagerly validate the derived edges so failures name the coordinate.
                check(right == it.right) { "right pixel coordinate mismatch" }
                check(bottom == it.bottom) { "bottom pixel coordinate mismatch" }
            }
        }
        return PlacedBounds(origin = origin, placed = placed)
    }

    /** Selected children in caller order plus the logical origin their frames are relative to. */
    data class PlacedBounds<T : Any>(
        val origin: Long,
        val placed: List<PlacedChild<T>>,
    )

    private fun checkedLeft(originX: Int, column: Int, geometry: SquareCellGeometry): Int {
        val stride = geometry.cellSizePx.toLong() + geometry.columnGapPx.toLong()
        val offset = checkedMultiplyExact(column.toLong(), stride, "column offset")
        return checkedAddExact(originX.toLong(), offset, "left")
            .toPixelCoordinate("left", allowNegative = false)
    }

    private fun checkedTop(row: Long, origin: Long, geometry: SquareCellGeometry): Int {
        val stride = geometry.cellSizePx.toLong() + geometry.rowGapPx.toLong()
        val localRow = checkedAddExact(row, -origin, "local row")
        val top = checkedMultiplyExact(localRow, stride, "rebased top")
        return top.toPixelCoordinate("top", allowNegative = true)
    }

    private fun checkedMultiplyExact(first: Long, second: Long, description: String): Long =
        try {
            Math.multiplyExact(first, second)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("$description overflows Long", exception)
        }

    private fun checkedAddExact(first: Long, second: Long, description: String): Long =
        try {
            Math.addExact(first, second)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("$description overflows Long", exception)
        }

    private fun Long.toPixelCoordinate(name: String, allowNegative: Boolean): Int {
        val inRange = if (allowNegative) {
            this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        } else {
            this in 0..Int.MAX_VALUE.toLong()
        }
        require(inRange) { "$name pixel coordinate is unrepresentable in Int: $this" }
        return toInt()
    }
}

/**
 * Sparse/windowed Compose span container that composes only caller children intersecting
 * a grid-owned [GridRowWindow] plus overscan.
 *
 * The visible set is exactly [GridVisiblePlacements.select] joined back to [children] in
 * stable caller order: off-window content lambdas are never invoked, so offscreen tiles
 * are not composed. Spans crossing either window boundary are included deterministically.
 * Empty logical gaps between distant placements produce no row/spacer children: only
 * selected children are emitted and measured.
 *
 * Rows are rebased around [rowOrigin] (defaulting to the overscanned window's first row)
 * using checked arithmetic, so a single distant [Int]-bounded logical row renders at a
 * small safe local offset instead of overflowing pixel [Int] arithmetic. [rowOrigin] is
 * signed and may be negative (the signed content `minRow`, e.g. rows above zero). The container's
 * extent wraps the selected children ([minHeightPx] floors the height, e.g. a viewport
 * minimum); callers that need scrolling supply their own [modifier] arrangement.
 *
 * This primitive performs no scrolling policy, feature arrangement, inventory,
 * persistence, gesture, legality, collision, packing, or column-count policy: the
 * [window]/[overscanRows] selection is grid-owned, [geometry] converts bounded measured
 * geometry to pixels, and overlapping placements render overlapped in list order.
 *
 * @throws IllegalArgumentException for negative [overscanRows], [originX], [minHeightPx]
 * or when a selected child's rebased frame (or the resulting container
 * extent over sparse selected children) cannot be represented in [Int] pixels. An
 * unrepresentably wide/tall selected extent fails explicitly instead of silently
 * dropping children.
 */
@Composable
fun <T : Any> WindowedSpanContainer(
    modifier: Modifier = Modifier,
    children: List<SpanChild<T>>,
    window: GridRowWindow,
    overscanRows: Long = 0L,
    geometry: SquareCellGeometry,
    originX: Int = 0,
    rowOrigin: Long? = null,
    minHeightPx: Int = 0,
) {
    require(overscanRows >= 0L) { "overscanRows must be non-negative, was $overscanRows" }
    require(originX >= 0) { "originX must be non-negative, was $originX" }
    require(minHeightPx >= 0) { "minHeightPx must be non-negative, was $minHeightPx" }

    val layout = WindowedSpanLayout.boundsFor(
        children = children,
        window = window,
        overscanRows = overscanRows,
        geometry = geometry,
        originX = originX,
        rowOrigin = rowOrigin,
    )
    val visible = layout.placed

    Layout(
        content = {
            visible.forEach { placed -> placed.child.content() }
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            check(measurables.size == visible.size) {
                "measurables (${measurables.size}) must match selected children (${visible.size})"
            }
            val placeables = visible.mapIndexed { index, placed ->
                measurables[index].measure(
                    Constraints.fixed(placed.widthPx, placed.heightPx),
                )
            }
            val contentWidth = visible.maxOfOrNull { it.right } ?: 0
            val selectedBottom = visible.maxOfOrNull { it.bottom } ?: 0
            val contentHeight = maxOf(selectedBottom.coerceAtLeast(0), minHeightPx)
            val width = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.place(visible[index].left, visible[index].top)
                }
            }
        },
    )
}
