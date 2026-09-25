package me.foxtails.lakehead.core.designsystem

/**
 * Measured vertical boundary for interactive controls and the content area above them.
 *
 * [interactiveBottomBoundaryPx] is an exclusive vertical floor: content must end no lower
 * than this pixel coordinate. [availableContentHeightPx] is the positive distance from
 * [contentTopPx] to that floor.
 */
data class ControlFloor(
    val interactiveBottomBoundaryPx: Int,
    val contentTopPx: Int,
    val availableContentHeightPx: Int,
) {
    init {
        require(contentTopPx >= 0) { "contentTopPx must be non-negative, was $contentTopPx" }
        require(interactiveBottomBoundaryPx > contentTopPx) {
            "interactiveBottomBoundaryPx must be below contentTopPx: " +
                "$interactiveBottomBoundaryPx <= $contentTopPx"
        }
        require(availableContentHeightPx > 0) {
            "availableContentHeightPx must be positive, was $availableContentHeightPx"
        }
        require(availableContentHeightPx == interactiveBottomBoundaryPx - contentTopPx) {
            "availableContentHeightPx must equal the distance from contentTopPx to boundary"
        }
    }
}

/** Measures a safe content floor from the current viewport, inset, and visible controls. */
object SafeControlFloor {
    /**
     * Measures the earliest visible interactive control top, falling back to the top of
     * the bottom system inset when no measured control starts above that fallback.
     *
     * A control is considered visible for this calculation when its [PixelRect.top] is
     * strictly above the inset-adjusted fallback. A control beginning at the fallback or
     * below it does not further reduce the available content area. No fixed control padding
     * or placement policy is applied.
     *
     * @throws IllegalArgumentException when dimensions are invalid or the resulting area
     * from [contentTopPx] to the selected floor is not positive.
     */
    fun measure(
        viewportHeightPx: Int,
        systemBottomInsetPx: Int,
        controls: List<PixelRect>,
        contentTopPx: Int = 0,
    ): ControlFloor {
        require(viewportHeightPx > 0) {
            "viewportHeightPx must be positive, was $viewportHeightPx"
        }
        require(systemBottomInsetPx >= 0 && systemBottomInsetPx < viewportHeightPx) {
            "systemBottomInsetPx must be in 0 until viewportHeightPx, was " +
                "$systemBottomInsetPx for viewportHeightPx=$viewportHeightPx"
        }
        require(contentTopPx >= 0) { "contentTopPx must be non-negative, was $contentTopPx" }

        val insetFallbackPx = viewportHeightPx - systemBottomInsetPx
        val measuredBoundaryPx = controls.asSequence()
            .map(PixelRect::top)
            .filter { controlTopPx -> controlTopPx < insetFallbackPx }
            .minOrNull()
            ?: insetFallbackPx

        require(measuredBoundaryPx > contentTopPx) {
            "Controls and system inset leave no content area: contentTopPx=$contentTopPx, " +
                "boundary=$measuredBoundaryPx"
        }
        val availableHeightPx = measuredBoundaryPx - contentTopPx

        return ControlFloor(
            interactiveBottomBoundaryPx = measuredBoundaryPx,
            contentTopPx = contentTopPx,
            availableContentHeightPx = availableHeightPx,
        )
    }
}
