package me.foxtails.lakehead.core.designsystem

/** Physical-edge padding applied to an overlay's selected layout region. */
data class FrameInsets(
    val leftPx: Int = 0,
    val topPx: Int = 0,
    val rightPx: Int = 0,
    val bottomPx: Int = 0,
) {
    init {
        require(leftPx >= 0) { "leftPx must be non-negative, was $leftPx" }
        require(topPx >= 0) { "topPx must be non-negative, was $topPx" }
        require(rightPx >= 0) { "rightPx must be non-negative, was $rightPx" }
        require(bottomPx >= 0) { "bottomPx must be non-negative, was $bottomPx" }
    }
}

/** Positive requested dimensions for an overlay frame, in pixels. */
data class FrameSize(
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx > 0) { "widthPx must be positive, was $widthPx" }
        require(heightPx > 0) { "heightPx must be positive, was $heightPx" }
    }
}

/** Horizontal alignment within the inset-adjusted overlay region. */
enum class HorizontalFrameAnchor {
    LEFT,
    CENTER,
    RIGHT,
}

/** Vertical alignment within the inset-adjusted overlay region. */
enum class VerticalFrameAnchor {
    TOP,
    CENTER,
    BOTTOM,
}

/**
 * Configurable alignment and pixel offsets for an overlay frame.
 *
 * Horizontal and vertical choices combine independently, supporting edges, corners, sides,
 * and floating placements. Offsets are signed and applied after alignment; the resulting
 * frame must still fit within the inset-adjusted region.
 */
data class FrameAnchor(
    val horizontal: HorizontalFrameAnchor,
    val vertical: VerticalFrameAnchor,
    val offsetXPx: Int = 0,
    val offsetYPx: Int = 0,
)

/**
 * Pure frame geometry for a floating overlay.
 *
 * [viewport] and optional [region] use the same absolute pixel coordinate space. When a
 * region is supplied it must be fully contained by the viewport. Insets are applied to the
 * selected region (the supplied region, or the whole viewport); the frame is aligned to the
 * remaining bounds. No screen chrome, navigation placement, clipping, or layout direction is
 * inferred here. Invalid inputs, collapsed inset regions, and frames that do not fit are
 * rejected instead of silently clamped.
 */
object FloatingOverlayFrame {
    /** Measures the exclusive pixel bounds for a requested overlay frame. */
    fun measure(
        viewport: PixelRect,
        size: FrameSize,
        anchor: FrameAnchor,
        insets: FrameInsets = FrameInsets(),
        region: PixelRect? = null,
    ): PixelRect {
        val layoutRegion = region ?: viewport
        require(layoutRegion.left >= viewport.left && layoutRegion.top >= viewport.top &&
            layoutRegion.right <= viewport.right && layoutRegion.bottom <= viewport.bottom
        ) {
            "region $layoutRegion must be contained in viewport $viewport"
        }

        val left = layoutRegion.left.toLong() + insets.leftPx
        val top = layoutRegion.top.toLong() + insets.topPx
        val right = layoutRegion.right.toLong() - insets.rightPx
        val bottom = layoutRegion.bottom.toLong() - insets.bottomPx
        require(right > left && bottom > top) {
            "Insets leave no positive region: left=$left, top=$top, right=$right, bottom=$bottom"
        }

        val frameLeft = when (anchor.horizontal) {
            HorizontalFrameAnchor.LEFT -> left
            HorizontalFrameAnchor.CENTER -> left + (right - left - size.widthPx) / 2L
            HorizontalFrameAnchor.RIGHT -> right - size.widthPx
        } + anchor.offsetXPx
        val frameTop = when (anchor.vertical) {
            VerticalFrameAnchor.TOP -> top
            VerticalFrameAnchor.CENTER -> top + (bottom - top - size.heightPx) / 2L
            VerticalFrameAnchor.BOTTOM -> bottom - size.heightPx
        } + anchor.offsetYPx
        val frameRight = frameLeft + size.widthPx
        val frameBottom = frameTop + size.heightPx

        require(frameLeft >= left && frameTop >= top && frameRight <= right && frameBottom <= bottom) {
            "Frame does not fit inset-adjusted region: frame=[$frameLeft,$frameTop,$frameRight,$frameBottom], " +
                "region=[$left,$top,$right,$bottom]"
        }

        return PixelRect(
            left = frameLeft.toPixelCoordinate("left"),
            top = frameTop.toPixelCoordinate("top"),
            right = frameRight.toPixelCoordinate("right"),
            bottom = frameBottom.toPixelCoordinate("bottom"),
        )
    }

    private fun Long.toPixelCoordinate(name: String): Int {
        require(this in 0..Int.MAX_VALUE.toLong()) { "$name pixel coordinate overflows Int: $this" }
        return toInt()
    }
}
