package me.foxtails.lakehead.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SixCellGeometryTest {

    @Test
    fun exactGeometryDerivesCellSize() {
        // (1080 - 2 * 48 - 5 * 12) / 6 = 924 / 6 = 154 exactly.
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
        )

        assertEquals(154, geometry.cellSizePx)
        assertEquals(12, geometry.columnGapPx)
        assertEquals(12, geometry.rowGapPx)
    }

    @Test
    fun nonDivisibleContentWidthFloorsCellSize() {
        // (1000 - 2 * 16 - 5 * 11) / 6 = 913 / 6 = 152 remainder 1; floors, never rounds up.
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1000,
            horizontalPaddingPx = 16,
            columnGapPx = 11,
        )

        assertEquals(152, geometry.cellSizePx)
    }

    @Test
    fun spanExtentSingleCellHasNoGaps() {
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
        )

        assertEquals(SixCellGeometry.SpanExtent(widthPx = 154, heightPx = 154), geometry.spanExtentPx(1, 1))
    }

    @Test
    fun spanExtentTwoColumnsIncludesOneInterveningGap() {
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
        )

        // 2 * 154 + 1 * 12 = 320.
        assertEquals(SixCellGeometry.SpanExtent(widthPx = 320, heightPx = 154), geometry.spanExtentPx(2, 1))
    }

    @Test
    fun spanExtentFourByTwoIncludesInterveningGaps() {
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
            rowGapPx = 16,
        )

        // Width: 4 * 154 + 3 * 12 = 652. Height: 2 * 154 + 1 * 16 = 324.
        assertEquals(SixCellGeometry.SpanExtent(widthPx = 652, heightPx = 324), geometry.spanExtentPx(4, 2))
    }

    @Test
    fun rowGapDefaultsToColumnGap() {
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
        )

        assertEquals(12, geometry.rowGapPx)
    }

    @Test
    fun customRowGapIsRespected() {
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
            rowGapPx = 20,
        )

        // 2 * 154 + 1 * 20 = 328.
        assertEquals(328, geometry.spanExtentPx(1, 2).heightPx)
    }

    @Test
    fun columnCountIsFixedAtSixAcrossWidths() {
        assertEquals(6, SixCellGeometry.COLUMN_COUNT)

        // The divisor is always six: for each width the geometry must satisfy
        // available - 2 * padding - 5 * gap == 6 * cell + remainder with 0 <= remainder < 6.
        val cases = listOf(
            Triple(1080, 48, 12),
            Triple(1000, 16, 11),
            Triple(720, 24, 8),
            Triple(1440, 64, 16),
            Triple(1080, 0, 0),
        )
        for ((availableWidthPx, horizontalPaddingPx, columnGapPx) in cases) {
            val geometry = SixCellGeometry.fromViewport(availableWidthPx, horizontalPaddingPx, columnGapPx)
            val remainder = availableWidthPx - 2 * horizontalPaddingPx - 5 * columnGapPx -
                6 * geometry.cellSizePx
            assertTrue(
                "width=$availableWidthPx padding=$horizontalPaddingPx gap=$columnGapPx",
                remainder in 0..5,
            )
        }
    }

    @Test
    fun factoryRejectsNegativeInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.fromViewport(0, 48, 12)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.fromViewport(-1080, 48, 12)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.fromViewport(1080, -4, 12)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.fromViewport(1080, 48, -2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.fromViewport(1080, 48, 12, -1)
        }
    }

    @Test
    fun zeroPaddingAndZeroGapsAreAccepted() {
        // Edge-to-edge geometry: (1080 - 0 - 0) / 6 = 180 with touching cells.
        val edgeToEdge = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 0,
            columnGapPx = 0,
        )

        assertEquals(180, edgeToEdge.cellSizePx)
        assertEquals(0, edgeToEdge.columnGapPx)
        assertEquals(0, edgeToEdge.rowGapPx)
        assertEquals(
            SixCellGeometry.SpanExtent(widthPx = 360, heightPx = 180),
            edgeToEdge.spanExtentPx(2, 1),
        )
    }

    @Test
    fun zeroRowGapIsAccepted() {
        val geometry = SixCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
            rowGapPx = 0,
        )

        assertEquals(0, geometry.rowGapPx)
        // 2 * 154 + 1 * 0 = 308.
        assertEquals(308, geometry.spanExtentPx(1, 2).heightPx)
    }

    @Test
    fun factoryRejectsViewportWithNoPositiveCell() {
        // (100 - 2 * 40 - 5 * 10) / 6 = -30 / 6: padding and gaps consume the width.
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.fromViewport(100, 40, 10)
        }
    }

    @Test
    fun spanExtentRejectsNonPositiveCounts() {
        val geometry = SixCellGeometry.fromViewport(1080, 48, 12)

        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(-2, 1) }
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(1, -1) }
    }

    @Test
    fun constructorAcceptsZeroGapsAndRejectsNegatives() {
        val touching = SixCellGeometry(154, 0, 0)

        assertEquals(0, touching.columnGapPx)
        assertEquals(0, touching.rowGapPx)
        assertThrows(IllegalArgumentException::class.java) { SixCellGeometry(0, 12, 12) }
        assertThrows(IllegalArgumentException::class.java) { SixCellGeometry(154, -1, 12) }
        assertThrows(IllegalArgumentException::class.java) { SixCellGeometry(154, 12, -1) }
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.SpanExtent(0, 154)
        }
    }

    @Test
    fun factoryRejectsInputsWhoseIntMathWouldSilentlyOverflow() {
        // True content is 1_000_000_000 - 2 * 1_500_000_000 - 5 * 200_000_000 = -3_000_000_000,
        // so no positive six-column cell exists. Naive Int arithmetic wraps the doubled
        // padding and reports a bogus positive cell of 1_294_967_296 / 6 = 215_827_882.
        assertThrows(IllegalArgumentException::class.java) {
            SixCellGeometry.fromViewport(1_000_000_000, 1_500_000_000, 200_000_000)
        }
    }

    @Test
    fun largestViewportDerivesExactCell() {
        // (Int.MAX_VALUE - 0 - 0) / 6 = 357_913_941 remainder 1.
        val geometry = SixCellGeometry.fromViewport(Int.MAX_VALUE, 0, 0)

        assertEquals(357_913_941, geometry.cellSizePx)
    }

    @Test
    fun spanExtentRejectsOverflowingWidths() {
        val geometry = SixCellGeometry(cellSizePx = 1_073_741_824, columnGapPx = 0, rowGapPx = 0)

        assertEquals(1_073_741_824, geometry.spanExtentPx(1, 1).widthPx)
        // True width is 5 * 2^30 = 5_368_709_120, which wraps to a positive Int
        // (1_073_741_824) under naive Int arithmetic; it must be rejected instead.
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(5, 1) }
    }

    @Test
    fun spanExtentAcceptsBoundaryWidthAndRejectsBeyondIt() {
        // 6 * 357_913_941 = 2_147_483_646 fits; 7 * 357_913_941 = 2_505_397_587 does not.
        val geometry = SixCellGeometry.fromViewport(Int.MAX_VALUE, 0, 0)

        assertEquals(2_147_483_646, geometry.spanExtentPx(6, 1).widthPx)
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(7, 1) }
    }
}
