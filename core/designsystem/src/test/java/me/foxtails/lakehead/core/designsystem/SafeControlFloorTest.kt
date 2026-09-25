package me.foxtails.lakehead.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeControlFloorTest {

    @Test
    fun earliestVisibleControlTopWinsOverInsetFallback() {
        val floor = SafeControlFloor.measure(
            viewportHeightPx = 1_000,
            systemBottomInsetPx = 40,
            controls = listOf(rect(top = 700, bottom = 760), rect(top = 620, bottom = 680)),
        )

        assertEquals(620, floor.interactiveBottomBoundaryPx)
        assertEquals(0, floor.contentTopPx)
        assertEquals(620, floor.availableContentHeightPx)
    }

    @Test
    fun controlHeightChangesBoundaryOneToOne() {
        val shortControlFloor = SafeControlFloor.measure(
            viewportHeightPx = 1_000,
            systemBottomInsetPx = 40,
            controls = listOf(rect(top = 700, bottom = 740)),
        )
        val tallerControlFloor = SafeControlFloor.measure(
            viewportHeightPx = 1_000,
            systemBottomInsetPx = 40,
            controls = listOf(rect(top = 660, bottom = 740)),
        )

        assertEquals(40, shortControlFloor.interactiveBottomBoundaryPx -
            tallerControlFloor.interactiveBottomBoundaryPx)
        assertEquals(40, shortControlFloor.availableContentHeightPx -
            tallerControlFloor.availableContentHeightPx)
    }

    @Test
    fun noControlsUsesInsetAdjustedFallback() {
        val floor = SafeControlFloor.measure(
            viewportHeightPx = 1_000,
            systemBottomInsetPx = 40,
            controls = emptyList(),
        )

        assertEquals(960, floor.interactiveBottomBoundaryPx)
        assertEquals(960, floor.availableContentHeightPx)
    }

    @Test
    fun zeroInsetUsesViewportAsFallback() {
        val floor = SafeControlFloor.measure(
            viewportHeightPx = 1_000,
            systemBottomInsetPx = 0,
            controls = emptyList(),
        )

        assertEquals(1_000, floor.interactiveBottomBoundaryPx)
        assertEquals(1_000, floor.availableContentHeightPx)
    }

    @Test
    fun controlAtOrBelowInsetFallbackIsIgnored() {
        val floor = SafeControlFloor.measure(
            viewportHeightPx = 1_000,
            systemBottomInsetPx = 100,
            controls = listOf(rect(top = 900, bottom = 940), rect(top = 950, bottom = 980)),
        )

        assertEquals(900, floor.interactiveBottomBoundaryPx)
        assertEquals(900, floor.availableContentHeightPx)
    }

    @Test
    fun contentTopReducesAvailableHeightWithoutChangingFloor() {
        val floor = SafeControlFloor.measure(
            viewportHeightPx = 1_000,
            systemBottomInsetPx = 100,
            controls = emptyList(),
            contentTopPx = 120,
        )

        assertEquals(900, floor.interactiveBottomBoundaryPx)
        assertEquals(120, floor.contentTopPx)
        assertEquals(780, floor.availableContentHeightPx)
    }

    @Test
    fun invalidDimensionsAndEmptyContentAreaAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SafeControlFloor.measure(0, 0, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeControlFloor.measure(100, -1, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeControlFloor.measure(100, 100, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeControlFloor.measure(100, 0, emptyList(), contentTopPx = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeControlFloor.measure(
                viewportHeightPx = 100,
                systemBottomInsetPx = 0,
                controls = listOf(rect(top = 0, bottom = 10)),
            )
        }
    }

    @Test
    fun repeatedMeasurementsAreDeterministic() {
        val controls = listOf(rect(top = 700, bottom = 750), rect(top = 650, bottom = 690))

        val first = SafeControlFloor.measure(1_000, 40, controls, contentTopPx = 50)
        val second = SafeControlFloor.measure(1_000, 40, controls, contentTopPx = 50)

        assertEquals(first, second)
    }

    private fun rect(top: Int, bottom: Int) = PixelRect(left = 0, top = top, right = 10, bottom = bottom)
}
