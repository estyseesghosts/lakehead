package me.foxtails.lakehead.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FloatingOverlayFrameTest {
    private val viewport = PixelRect(left = 10, top = 20, right = 410, bottom = 820)

    @Test
    fun positionsTopBottomAndSideAnchorsInViewport() {
        assertEquals(
            PixelRect(10, 20, 110, 80),
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(100, 60),
                FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
            ),
        )
        assertEquals(
            PixelRect(310, 780, 410, 820),
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(100, 40),
                FrameAnchor(HorizontalFrameAnchor.RIGHT, VerticalFrameAnchor.BOTTOM),
            ),
        )
        assertEquals(
            PixelRect(10, 370, 70, 470),
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(60, 100),
                FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.CENTER),
            ),
        )
    }

    @Test
    fun centersFloatingFrameWithDeterministicFlooring() {
        assertEquals(
            PixelRect(164, 384, 255, 455),
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(91, 71),
                FrameAnchor(HorizontalFrameAnchor.CENTER, VerticalFrameAnchor.CENTER),
            ),
        )
    }

    @Test
    fun appliesInsetsToExplicitRegionBeforeAnchoring() {
        val region = PixelRect(left = 40, top = 60, right = 360, bottom = 700)

        assertEquals(
            PixelRect(50, 75, 350, 675),
            FloatingOverlayFrame.measure(
                viewport = viewport,
                size = FrameSize(300, 600),
                anchor = FrameAnchor(HorizontalFrameAnchor.CENTER, VerticalFrameAnchor.CENTER),
                insets = FrameInsets(leftPx = 10, topPx = 15, rightPx = 10, bottomPx = 25),
                region = region,
            ),
        )
    }

    @Test
    fun signedOffsetsAreAppliedAfterAlignment() {
        assertEquals(
            PixelRect(35, 35, 85, 65),
            FloatingOverlayFrame.measure(
                viewport = PixelRect(10, 20, 110, 120),
                size = FrameSize(50, 30),
                anchor = FrameAnchor(
                    horizontal = HorizontalFrameAnchor.LEFT,
                    vertical = VerticalFrameAnchor.TOP,
                    offsetXPx = 25,
                    offsetYPx = 15,
                ),
            ),
        )
    }

    @Test
    fun repeatedMeasurementsAreDeterministic() {
        val arguments = Triple(
            FrameSize(137, 211),
            FrameAnchor(HorizontalFrameAnchor.CENTER, VerticalFrameAnchor.BOTTOM, offsetYPx = -7),
            FrameInsets(leftPx = 3, topPx = 9, rightPx = 11, bottomPx = 13),
        )

        val first = FloatingOverlayFrame.measure(viewport, arguments.first, arguments.second, arguments.third)
        val second = FloatingOverlayFrame.measure(viewport, arguments.first, arguments.second, arguments.third)

        assertEquals(first, second)
    }

    @Test
    fun rejectsRegionOutsideViewport() {
        assertThrows(IllegalArgumentException::class.java) {
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(10, 10),
                FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
                region = PixelRect(0, 20, 20, 40),
            )
        }
    }

    @Test
    fun rejectsCollapsedInsetsAndFrameThatDoesNotFit() {
        assertThrows(IllegalArgumentException::class.java) {
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(1, 1),
                FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
                insets = FrameInsets(leftPx = 200, rightPx = 200),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(401, 10),
                FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FloatingOverlayFrame.measure(
                viewport,
                FrameSize(10, 10),
                FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP, offsetXPx = -1),
            )
        }
    }

    @Test
    fun rejectsInvalidDimensionsInsetsAndOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { FrameSize(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { FrameSize(1, -1) }
        assertThrows(IllegalArgumentException::class.java) { FrameInsets(bottomPx = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            FloatingOverlayFrame.measure(
                PixelRect(0, 0, Int.MAX_VALUE, 10),
                FrameSize(10, 5),
                FrameAnchor(
                    HorizontalFrameAnchor.LEFT,
                    VerticalFrameAnchor.TOP,
                    offsetXPx = Int.MAX_VALUE,
                ),
            )
        }
    }
}
