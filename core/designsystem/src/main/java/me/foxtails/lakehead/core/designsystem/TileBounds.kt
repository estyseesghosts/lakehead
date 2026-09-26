package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan

/** Pixel frame with a non-negative left edge and a signed top edge (may be negative above the row origin). */
data class SignedPixelFrame(
    val left: Int,
    val top: Int,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(left >= 0) { "left must be non-negative, was $left" }
        require(widthPx > 0) { "widthPx must be positive, was $widthPx" }
        require(heightPx > 0) { "heightPx must be positive, was $heightPx" }
    }

    val right: Int
        get() = left + widthPx

    val bottom: Int
        get() = top + heightPx
}

/** Pixel rectangle with a non-negative inclusive top-left and exclusive bottom-right. */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "left must be non-negative, was $left" }
        require(top >= 0) { "top must be non-negative, was $top" }
        require(right > left) { "right must be greater than left, was $right <= $left" }
        require(bottom > top) { "bottom must be greater than top, was $bottom <= $top" }
    }

    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

/** Measures a grid tile's exclusive pixel bounds without checking placement legality. */
object TileBounds {
    /**
     * Converts [position] and [span] to pixel bounds using [geometry], offset by the
     * non-negative pixel origin. Any positive span dimensions can be measured; grid
     * legality and supported-span policy remain owned by `core:grid`.
     *
     * Coordinates use `position * (cell size + gap) + origin`; the measured extent
     * includes gaps inside the span but not a gap after its final cell. Arithmetic is
     * checked and resulting coordinates must fit [Int].
     */
    fun bounds(
        position: GridPosition,
        span: GridSpan,
        geometry: SixCellGeometry,
        originX: Int = 0,
        originY: Int = 0,
    ): PixelRect {
        require(originX >= 0) { "originX must be non-negative, was $originX" }
        require(originY >= 0) { "originY must be non-negative, was $originY" }

        val extent = geometry.spanExtentPx(span.columns, span.rows)
        return boundsFromExtent(
            position = position,
            extentWidthPx = extent.widthPx,
            extentHeightPx = extent.heightPx,
            cellSizePx = geometry.cellSizePx,
            columnGapPx = geometry.columnGapPx,
            rowGapPx = geometry.rowGapPx,
            originX = originX,
            originY = originY,
        )
    }

    /**
     * Converts [position] and [span] to pixel bounds using a generalized
     * [SquareCellGeometry] for any positive visual column count, offset by the
     * non-negative pixel origin. Any positive span dimensions can be measured; grid
     * legality and supported-span policy remain owned by `core:grid`.
     *
     * Coordinates use `position * (cell size + gap) + origin`; the measured extent
     * includes gaps inside the span but not a gap after its final cell. Arithmetic is
     * checked and resulting coordinates must fit [Int]. The visual column count only
     * determines how the geometry was derived and is not re-checked here.
     */
    fun bounds(
        position: GridPosition,
        span: GridSpan,
        geometry: SquareCellGeometry,
        originX: Int = 0,
        originY: Int = 0,
    ): PixelRect {
        require(originX >= 0) { "originX must be non-negative, was $originX" }
        require(originY >= 0) { "originY must be non-negative, was $originY" }

        val extent = geometry.spanExtentPx(span.columns, span.rows)
        return boundsFromExtent(
            position = position,
            extentWidthPx = extent.widthPx,
            extentHeightPx = extent.heightPx,
            cellSizePx = geometry.cellSizePx,
            columnGapPx = geometry.columnGapPx,
            rowGapPx = geometry.rowGapPx,
            originX = originX,
            originY = originY,
        )
    }

    /**
     * Signed-row variant of [bounds] for a generalized [SquareCellGeometry].
     *
     * Measures the tile at non-negative [column] and signed [row] (may be negative,
     * e.g. above the content origin) rebased around [originRow] (typically the signed
     * content `minRow`) plus a pixel [originYPx] offset (typically the G2 rest
     * `gridOriginY` computed in rebased coordinates).
     *
     * `left = originX + column * (cell + columnGap)` (always non-negative);
     * `top = originYPx + (row - originRow) * (cell + rowGap)` (may be negative when
     * the tile starts above the origin). The measured extent includes gaps inside the
     * span but not a gap after its final cell. All arithmetic is checked `Long`
     * arithmetic; `left`/`top`/`right`/`bottom` must fit [Int] (`top`/`bottom` may be
     * negative). Grid legality and supported-span policy remain owned by `core:grid`.
     */
    fun signedBounds(
        column: Int,
        row: Long,
        span: GridSpan,
        geometry: SquareCellGeometry,
        originX: Int = 0,
        originRow: Long = 0L,
        originYPx: Long = 0L,
    ): SignedPixelFrame {
        require(column >= 0) { "column must be non-negative, was $column" }
        require(originX >= 0) { "originX must be non-negative, was $originX" }

        val extent = geometry.spanExtentPx(span.columns, span.rows)
        val columnStride = geometry.cellSizePx.toLong() + geometry.columnGapPx.toLong()
        val rowStride = geometry.cellSizePx.toLong() + geometry.rowGapPx.toLong()
        val leftLong = checkedAdd(
            originX.toLong(),
            checkedMultiply(column.toLong(), columnStride, "column offset"),
            "left",
        )
        val localRow = checkedAdd(row, -originRow, "local row")
        val topLong = checkedAdd(
            originYPx,
            checkedMultiply(localRow, rowStride, "rebased top"),
            "top",
        )
        val left = leftLong.toNonNegativePixelCoordinate("left")
        val top = topLong.toSignedPixelCoordinate("top")
        val right = checkedAdd(leftLong, extent.widthPx.toLong(), "right")
            .toNonNegativePixelCoordinate("right")
        val bottom = checkedAdd(topLong, extent.heightPx.toLong(), "bottom")
            .toSignedPixelCoordinate("bottom")
        check(right == left + extent.widthPx) { "right pixel coordinate mismatch" }
        check(bottom == top + extent.heightPx) { "bottom pixel coordinate mismatch" }
        return SignedPixelFrame(
            left = left,
            top = top,
            widthPx = extent.widthPx,
            heightPx = extent.heightPx,
        )
    }

    private fun boundsFromExtent(
        position: GridPosition,
        extentWidthPx: Int,
        extentHeightPx: Int,
        cellSizePx: Int,
        columnGapPx: Int,
        rowGapPx: Int,
        originX: Int,
        originY: Int,
    ): PixelRect {
        val left = coordinate(originX, position.column, cellSizePx, columnGapPx)
        val top = coordinate(originY, position.row, cellSizePx, rowGapPx)
        val right = checkedAdd(left, extentWidthPx.toLong(), "right")
        val bottom = checkedAdd(top, extentHeightPx.toLong(), "bottom")

        return PixelRect(
            left = left.toPixelCoordinate("left"),
            top = top.toPixelCoordinate("top"),
            right = right.toPixelCoordinate("right"),
            bottom = bottom.toPixelCoordinate("bottom"),
        )
    }

    private fun coordinate(origin: Int, cellIndex: Int, cellSize: Int, gap: Int): Long {
        val stride = cellSize.toLong() + gap.toLong()
        val offset = checkedMultiply(cellIndex.toLong(), stride, "position offset")
        return checkedAdd(origin.toLong(), offset, "position coordinate")
    }

    private fun checkedMultiply(first: Long, second: Long, description: String): Long =
        try {
            Math.multiplyExact(first, second)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("$description overflows Long", exception)
        }

    private fun checkedAdd(first: Long, second: Long, description: String): Long =
        try {
            Math.addExact(first, second)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("$description overflows Long", exception)
        }

    private fun Long.toPixelCoordinate(name: String): Int {
        require(this in 0..Int.MAX_VALUE.toLong()) {
            "$name pixel coordinate overflows Int: $this"
        }
        return toInt()
    }

    private fun Long.toNonNegativePixelCoordinate(name: String): Int {
        require(this in 0..Int.MAX_VALUE.toLong()) {
            "$name pixel coordinate is unrepresentable in Int: $this"
        }
        return toInt()
    }

    private fun Long.toSignedPixelCoordinate(name: String): Int {
        require(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "$name pixel coordinate is unrepresentable in Int: $this"
        }
        return toInt()
    }
}
