package me.foxtails.lakehead.core.designsystem

import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridViewportAdaptation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SquareCellGeometryTest {

    @Test
    fun referenceCountIsFiveButNeverAMinimum() {
        assertEquals(5, SquareCellGeometry.NORMAL_PHONE_REFERENCE_COLUMN_COUNT)
        assertEquals(
            GridColumns.NORMAL_PHONE_REFERENCE_COUNT,
            SquareCellGeometry.NORMAL_PHONE_REFERENCE_COLUMN_COUNT,
        )
        // Narrower viewports truthfully use fewer columns: the reference is not a floor.
        val narrow = SquareCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
            columnCount = 2,
        )
        assertEquals(2, narrow.columnCount)
    }

    @Test
    fun countsBelowFiveDeriveCells() {
        // Two columns: (1080 - 2 * 48 - 1 * 12) / 2 = 972 / 2 = 486.
        val two = SquareCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
            columns = GridColumns(2),
        )
        assertEquals(2, two.columnCount)
        assertEquals(486, two.cellSizePx)

        // Four columns: (1080 - 2 * 48 - 3 * 12) / 4 = 948 / 4 = 237.
        val four = SquareCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
            columns = GridColumns(4),
        )
        assertEquals(4, four.columnCount)
        assertEquals(237, four.cellSizePx)
    }

    @Test
    fun fiveColumnPhoneReferenceDerivesExpectedCell() {
        // Five columns: (1080 - 2 * 48 - 4 * 12) / 5 = 936 / 5 = 187 remainder 1.
        val five = SquareCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 48,
            columnGapPx = 12,
            columns = GridColumns(5),
        )

        assertEquals(5, five.columnCount)
        assertEquals(187, five.cellSizePx)
        val remainder = 1080 - 2 * 48 - 4 * 12 - 5 * five.cellSizePx
        assertEquals(1, remainder)
    }

    @Test
    fun countsAboveFiveDeriveCells() {
        // Eight columns: (1080 - 2 * 24 - 7 * 8) / 8 = 976 / 8 = 122.
        val eight = SquareCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 24,
            columnGapPx = 8,
            columns = GridColumns(8),
        )
        assertEquals(8, eight.columnCount)
        assertEquals(122, eight.cellSizePx)

        // Ten columns: (1440 - 2 * 64 - 9 * 16) / 10 = 1168 / 10 = 116 remainder 8.
        val ten = SquareCellGeometry.fromViewport(
            availableWidthPx = 1440,
            horizontalPaddingPx = 64,
            columnGapPx = 16,
            columnCount = 10,
        )
        assertEquals(10, ten.columnCount)
        assertEquals(116, ten.cellSizePx)
    }

    @Test
    fun nonDivisibleContentWidthFloorsLeavingTrailingSlack() {
        // (1000 - 2 * 16 - 3 * 11) / 4 = 935 / 4 = 233 remainder 3; floors, never rounds up.
        val geometry = SquareCellGeometry.fromViewport(
            availableWidthPx = 1000,
            horizontalPaddingPx = 16,
            columnGapPx = 11,
            columnCount = 4,
        )

        assertEquals(233, geometry.cellSizePx)
        val remainder = 1000 - 2 * 16 - 3 * 11 - 4 * geometry.cellSizePx
        assertTrue("remainder=$remainder", remainder in 0..3)
    }

    @Test
    fun derivationHoldsAcrossCountsWidthsPaddingAndGaps() {
        data class Case(val width: Int, val padding: Int, val gap: Int, val count: Int)
        val cases = listOf(
            Case(1080, 48, 12, 2),
            Case(1000, 16, 11, 4),
            Case(1080, 48, 12, 5),
            Case(720, 24, 8, 3),
            Case(1440, 64, 16, 10),
            Case(1080, 0, 0, 8),
        )
        for ((width, padding, gap, count) in cases) {
            val geometry = SquareCellGeometry.fromViewport(width, padding, gap, columnCount = count)
            val remainder = width - 2 * padding - (count - 1) * gap - count * geometry.cellSizePx
            assertTrue(
                "width=$width padding=$padding gap=$gap count=$count remainder=$remainder",
                remainder in 0 until count,
            )
        }
    }

    @Test
    fun spanExtentSingleCellHasNoGaps() {
        val geometry = SquareCellGeometry.fromViewport(1080, 48, 12, columnCount = 4)

        assertEquals(
            SquareCellGeometry.SpanExtent(widthPx = 237, heightPx = 237),
            geometry.spanExtentPx(1, 1),
        )
    }

    @Test
    fun spanExtentIncludesOnlyInternalGaps() {
        val geometry = SquareCellGeometry(columnCount = 4, cellSizePx = 50, columnGapPx = 8, rowGapPx = 10)

        // 2 * 50 + 1 * 8 = 108.
        assertEquals(
            SquareCellGeometry.SpanExtent(widthPx = 108, heightPx = 50),
            geometry.spanExtentPx(2, 1),
        )
        // Width: 4 * 50 + 3 * 8 = 224. Height: 2 * 50 + 1 * 10 = 110.
        assertEquals(
            SquareCellGeometry.SpanExtent(widthPx = 224, heightPx = 110),
            geometry.spanExtentPx(4, 2),
        )
    }

    @Test
    fun rowGapDefaultsToColumnGapAndCustomRowGapIsRespected() {
        val defaulted = SquareCellGeometry.fromViewport(1080, 48, 12, columnCount = 4)
        assertEquals(12, defaulted.rowGapPx)

        val custom = SquareCellGeometry.fromViewport(1080, 48, 12, rowGapPx = 20, columnCount = 4)
        assertEquals(20, custom.rowGapPx)
        // 2 * 237 + 1 * 20 = 494.
        assertEquals(494, custom.spanExtentPx(1, 2).heightPx)
    }

    @Test
    fun zeroPaddingAndZeroGapsAreAccepted() {
        // Edge-to-edge: (1080 - 0 - 0) / 4 = 270 with touching cells.
        val edgeToEdge = SquareCellGeometry.fromViewport(
            availableWidthPx = 1080,
            horizontalPaddingPx = 0,
            columnGapPx = 0,
            columnCount = 4,
        )

        assertEquals(270, edgeToEdge.cellSizePx)
        assertEquals(
            SquareCellGeometry.SpanExtent(widthPx = 540, heightPx = 270),
            edgeToEdge.spanExtentPx(2, 1),
        )
    }

    @Test
    fun adaptedGridCountIsUsedAsIsWithoutSixColumnClamp() {
        // Narrow viewport adapts to three columns via the owned grid contract.
        val adapted = GridViewportAdaptation.columnsFor(
            viewportWidthPx = 600,
            horizontalPaddingPx = 50,
            columnGapPx = 12,
            minimumCellWidthPx = 150,
        )
        assertEquals(3, adapted.count)
        assertTrue(adapted.count < SquareCellGeometry.NORMAL_PHONE_REFERENCE_COLUMN_COUNT)

        val geometry = SquareCellGeometry.fromViewport(
            availableWidthPx = 600,
            horizontalPaddingPx = 50,
            columnGapPx = 12,
            columns = adapted,
        )
        assertEquals(3, geometry.columnCount)
        // (600 - 2 * 50 - 2 * 12) / 3 = 476 / 3 = 158 remainder 2.
        assertEquals(158, geometry.cellSizePx)
    }

    @Test
    fun tileBoundsConsumesGeneralizedGeometry() {
        val geometry = SquareCellGeometry(columnCount = 4, cellSizePx = 10, columnGapPx = 2, rowGapPx = 3)

        assertEquals(
            PixelRect(left = 0, top = 0, right = 10, bottom = 10),
            TileBounds.bounds(GridPosition(0, 0), GridSpan(1, 1), geometry),
        )
        // 2x2 extent includes only internal gaps: 22 x 23.
        assertEquals(
            PixelRect(left = 0, top = 0, right = 22, bottom = 23),
            TileBounds.bounds(GridPosition(0, 0), GridSpan(2, 2), geometry),
        )
        // Position and origin both offset: left = 3 * 12 + 5 = 41, top = 2 * 13 + 4 = 30.
        assertEquals(
            PixelRect(left = 41, top = 30, right = 51, bottom = 40),
            TileBounds.bounds(
                position = GridPosition(3, 2),
                span = GridSpan(1, 1),
                geometry = geometry,
                originX = 5,
                originY = 4,
            ),
        )
    }

    @Test
    fun tileBoundsParityAtFiveColumnReference() {
        // Five-column reference: derived geometry matches an explicitly constructed
        // equivalent (SixCellGeometry stays a legacy fixed-six baseline and is no
        // longer the reference, so parity against it is obsolete here).
        val derived = SquareCellGeometry.fromViewport(1080, 48, 12, columnCount = 5)
        assertEquals(187, derived.cellSizePx)
        val manual = SquareCellGeometry(
            columnCount = 5,
            cellSizePx = 187,
            columnGapPx = 12,
            rowGapPx = 12,
        )

        assertEquals(
            TileBounds.bounds(GridPosition(2, 1), GridSpan(2, 1), manual),
            TileBounds.bounds(GridPosition(2, 1), GridSpan(2, 1), derived),
        )
    }

    @Test
    fun factoryRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(0, 48, 12, columnCount = 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(-1080, 48, 12, columnCount = 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(1080, -4, 12, columnCount = 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(1080, 48, -2, columnCount = 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(1080, 48, 12, rowGapPx = -1, columnCount = 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(1080, 48, 12, columnCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(1080, 48, 12, columnCount = -3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GridColumns(0)
        }
    }

    @Test
    fun factoryRejectsViewportWithNoPositiveCell() {
        // (100 - 2 * 40 - 3 * 10) / 4 = -10 / 4: padding and gaps consume the width.
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(100, 40, 10, columnCount = 4)
        }
    }

    @Test
    fun constructorAndSpanExtentRejectNonPositiveInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry(columnCount = 0, cellSizePx = 10, columnGapPx = 2, rowGapPx = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry(columnCount = 4, cellSizePx = 0, columnGapPx = 2, rowGapPx = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry(columnCount = 4, cellSizePx = 10, columnGapPx = -1, rowGapPx = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry(columnCount = 4, cellSizePx = 10, columnGapPx = 2, rowGapPx = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.SpanExtent(0, 10)
        }

        val geometry = SquareCellGeometry.fromViewport(1080, 48, 12, columnCount = 4)
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(-2, 1) }
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(1, -1) }
    }

    @Test
    fun factoryRejectsInputsWhoseIntMathWouldSilentlyOverflow() {
        // True content is 1_000_000_000 - 2 * 1_500_000_000 - 5 * 200_000_000 = -3_000_000_000,
        // so no positive six-column cell exists. Naive Int arithmetic wraps the doubled
        // padding and reports a bogus positive cell.
        assertThrows(IllegalArgumentException::class.java) {
            SquareCellGeometry.fromViewport(1_000_000_000, 1_500_000_000, 200_000_000, columnCount = 6)
        }
    }

    @Test
    fun largestViewportDerivesExactCellForGeneralizedCount() {
        // (Int.MAX_VALUE - 0 - 0) / 4 = 536_870_911 remainder 3.
        val geometry = SquareCellGeometry.fromViewport(Int.MAX_VALUE, 0, 0, columnCount = 4)

        assertEquals(536_870_911, geometry.cellSizePx)
    }

    @Test
    fun spanExtentRejectsOverflowingWidths() {
        val geometry = SquareCellGeometry(columnCount = 4, cellSizePx = 1_073_741_824, columnGapPx = 0, rowGapPx = 0)

        assertEquals(1_073_741_824, geometry.spanExtentPx(1, 1).widthPx)
        // True width is 3 * 2^30 = 3_221_225_472, which wraps under naive Int arithmetic;
        // it must be rejected instead.
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(3, 1) }
    }

    @Test
    fun spanExtentAcceptsBoundaryWidthAndRejectsBeyondIt() {
        // 4 * 536_870_911 = 2_147_483_644 fits; 5 * 536_870_911 = 2_684_354_555 does not.
        val geometry = SquareCellGeometry.fromViewport(Int.MAX_VALUE, 0, 0, columnCount = 4)

        assertEquals(2_147_483_644, geometry.spanExtentPx(4, 1).widthPx)
        assertThrows(IllegalArgumentException::class.java) { geometry.spanExtentPx(5, 1) }
    }

    @Test
    fun tileBoundsRejectsGeneralizedCoordinateOverflow() {
        val largeCell = SquareCellGeometry(
            columnCount = 2,
            cellSizePx = Int.MAX_VALUE,
            columnGapPx = 0,
            rowGapPx = 0,
        )

        assertThrows(IllegalArgumentException::class.java) {
            TileBounds.bounds(GridPosition(1, 0), GridSpan(1, 1), largeCell)
        }
    }
}
