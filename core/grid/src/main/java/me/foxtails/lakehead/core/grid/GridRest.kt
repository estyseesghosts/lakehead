package me.foxtails.lakehead.core.grid

/**
 * Rest position and scroll range for a grid viewport.
 *
 * [gridOriginY] is the pixel offset of the topmost row's top edge relative to the viewport's top.
 * [scrollRangePx] is the maximum vertical scroll distance in pixels; it is zero when content fits
 * within the viewport. [scrollable] is `true` when the content exceeds the viewport height.
 */
data class GridRest(
    val gridOriginY: Long,
    val scrollRangePx: Long,
    val scrollable: Boolean,
) {
    init {
        require(scrollRangePx >= 0L) { "scrollRangePx must be non-negative" }
        require(scrollable == (scrollRangePx > 0L)) {
            "scrollable must be true exactly when scrollRangePx > 0"
        }
    }
}