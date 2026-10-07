package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.grid.GridColumns
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.GridVerticalLayout
import me.foxtails.lakehead.core.grid.GridViewportAdaptation
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HomeRenderProjectionTest {
    private val homeId = HomeId("home-main")
    private val otherHomeId = HomeId("home-other")

    private fun identity(profile: String, pkg: String, cls: String) =
        AppIdentity(ProfileId(profile), ComponentIdentity(pkg, cls))

    private fun placement(
        item: String,
        identity: AppIdentity,
        column: Int = 0,
        row: Int = 0,
        spanColumns: Int = 1,
        spanRows: Int = 1,
        availability: ItemAvailability = ItemAvailability.AVAILABLE,
        homeId: HomeId = this.homeId,
    ) = AppPlacement(
        homeId = homeId,
        itemId = ItemId(item),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(spanColumns, spanRows),
        availability = availability,
    )

    private fun metadata(label: String, byte: Byte = 7) =
        HomeAppMetadata(label, HomeIconBytes(byteArrayOf(byte, 2, 3)))

    private fun inputs(
        viewportWidthPx: Int = 1092 - 12,
        horizontalInsetPx: Int = 0,
        horizontalPaddingPx: Int = 0,
        columnGapPx: Int = 12,
        minimumCellWidthPx: Int = 160,
        viewportRows: Long = 10L,
        workingMarginRows: Long = 2L,
    ) = HomeViewportInputs(
        viewportWidthPx = viewportWidthPx,
        horizontalInsetPx = horizontalInsetPx,
        horizontalPaddingPx = horizontalPaddingPx,
        columnGapPx = columnGapPx,
        minimumCellWidthPx = minimumCellWidthPx,
        viewportRows = viewportRows,
        workingMarginRows = workingMarginRows,
    )

    // ---- HomeIconBytes ----

    @Test
    fun iconBytes_defensiveCopiesAndContentEquality() {
        val source = byteArrayOf(1, 2, 3)
        val icon = HomeIconBytes(source)
        source[0] = 99
        assertEquals(1.toByte(), icon.bytes()[0])

        val exposed = icon.bytes()
        exposed[0] = 99
        assertEquals(1.toByte(), icon.bytes()[0])

        assertEquals(HomeIconBytes(byteArrayOf(1, 2, 3)), icon)
        assertEquals(HomeIconBytes(byteArrayOf(1, 2, 3)).hashCode(), icon.hashCode())
        assertTrue(icon.toString().contains("size=3"))
        assertFalse(icon.toString().contains("99"))
    }

    @Test
    fun iconBytes_rejectsEmptyAndOversized() {
        try {
            HomeIconBytes(byteArrayOf())
            fail("expected empty rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            HomeIconBytes(ByteArray(HomeIconBytes.MAX_BYTES + 1))
            fail("expected oversize rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        // Boundary: exactly 1 MiB is accepted.
        assertEquals(HomeIconBytes.MAX_BYTES, HomeIconBytes(ByteArray(HomeIconBytes.MAX_BYTES)).size)
    }

    @Test
    fun iconBytes_toStringNeverDumpsBytes() {
        val icon = HomeIconBytes(byteArrayOf(10, 20, 30))
        val text = icon.toString()
        assertFalse(text.contains("10"))
        assertFalse(text.contains("20"))
        assertFalse(text.contains("30"))
    }

    // ---- State IDs and empty distinction ----

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun state_carriesRequestedIdsAndLoadedEmptyIsDistinct() {
        val loading: HomeRenderState = HomeRenderProjection.loading(homeId)
        val notFound: HomeRenderState = HomeRenderProjection.notFound(homeId)
        assertEquals(homeId, (loading as HomeRenderState.Loading).requestedHomeId)
        assertEquals(homeId, (notFound as HomeRenderState.NotFound).requestedHomeId)

        val loaded: HomeRenderState =
            HomeRenderProjection.project(homeId, emptyList(), emptyMap(), inputs())
        assertEquals(homeId, (loaded as HomeRenderState.Loaded).requestedHomeId)
        assertTrue((loaded as HomeRenderState.Loaded).tiles.isEmpty())
        // Distinct types: empty Loaded is neither Loading nor NotFound.
        assertTrue(loaded is HomeRenderState.Loaded)
        assertTrue(loaded !is HomeRenderState.Loading)
        assertTrue(loaded !is HomeRenderState.NotFound)
    }

    // ---- resolveTiles: order, unavailable, profile-aware joins ----

    @Test
    fun resolveTiles_preservesOrderAndUnavailablePlacements() {
        val a = identity("p0", "com.a", "A")
        val b = identity("p0", "com.b", "B")
        val c = identity("p0", "com.c", "C")
        val placements = listOf(
            placement("item-3", c, column = 2, row = 0, availability = ItemAvailability.UNAVAILABLE),
            placement("item-1", a, column = 0, row = 0),
            placement("item-2", b, column = 1, row = 0, availability = ItemAvailability.UNAVAILABLE),
        )
        val metadata = mapOf(a to metadata("A"), b to metadata("B"), c to metadata("C"))

        val tiles = HomeRenderProjection.resolveTiles(placements, metadata)

        assertEquals(listOf("item-3", "item-1", "item-2"), tiles.map { it.itemId.value })
        assertEquals(ItemAvailability.UNAVAILABLE, tiles[0].availability)
        assertEquals(ItemAvailability.UNAVAILABLE, tiles[2].availability)
        // Original coordinates preserved.
        assertEquals(LogicalPosition(2, 0), tiles[0].position)
        assertEquals(LogicalSpan(1, 1), tiles[0].span)
        // Unavailable keeps joined label/icon for display but is not launchable.
        assertEquals("C", tiles[0].label)
        assertFalse(tiles[0].isLaunchable)
        assertTrue(tiles[1].isLaunchable)
        assertFalse(tiles[2].isLaunchable)
    }

    @Test
    fun resolveTiles_noCatalogueToTileConversion_tilesFollowPlacementsOnly() {
        val placed = identity("p0", "com.placed", "Placed")
        val unplaced = identity("p0", "com.unplaced", "Unplaced")
        val placements = listOf(placement("only", placed))
        // Catalogue-style metadata contains an extra identity with no placement.
        val metadata = mapOf(placed to metadata("Placed"), unplaced to metadata("Unplaced"))

        val tiles = HomeRenderProjection.resolveTiles(placements, metadata)

        assertEquals(1, tiles.size)
        assertEquals(ItemId("only"), tiles[0].itemId)
        assertEquals(placed, tiles[0].appIdentity)
    }

    @Test
    fun resolveTiles_profileAwareJoin_matchesCompleteIdentity() {
        val personal = identity("personal", "com.app", "Main")
        val work = identity("work", "com.app", "Main")
        val placements = listOf(
            placement("w", work, column = 1),
            placement("p", personal, column = 0),
        )
        val metadata = mapOf(personal to metadata("Personal App", 11))

        val tiles = HomeRenderProjection.resolveTiles(placements, metadata)

        // Order preserved even though only the second has metadata.
        assertEquals(work, tiles[0].appIdentity)
        assertNull(tiles[0].label)
        assertNull(tiles[0].icon)
        assertFalse(tiles[0].isLaunchable)
        assertEquals("Personal App", tiles[1].label)
        assertTrue(tiles[1].isLaunchable)
    }

    @Test
    fun resolveTiles_missingMetadataLeavesNullsWithoutIdentityLoss() {
        val id = identity("p0", "com.missing", "M")
        val placements = listOf(placement("m", id, column = 3, row = 4))

        val tiles = HomeRenderProjection.resolveTiles(placements, emptyMap())

        assertEquals(1, tiles.size)
        assertEquals(ItemId("m"), tiles[0].itemId)
        assertEquals(id, tiles[0].appIdentity)
        assertEquals(LogicalPosition(3, 4), tiles[0].position)
        assertNull(tiles[0].label)
        assertNull(tiles[0].icon)
        assertFalse(tiles[0].isLaunchable)
    }

    @Test
    fun resolveTiles_labelOnlyMetadataKeepsAvailableLaunchableAndUnavailableNotLaunchable() {
        val availableIdentity = identity("p0", "com.available", "A")
        val unavailableIdentity = identity("p0", "com.unavailable", "U")
        val placements = listOf(
            placement("available", availableIdentity),
            placement("unavailable", unavailableIdentity, availability = ItemAvailability.UNAVAILABLE),
        )

        val tiles = HomeRenderProjection.resolveTiles(
            placements,
            mapOf(
                availableIdentity to HomeAppMetadata("Available"),
                unavailableIdentity to HomeAppMetadata("Unavailable"),
            ),
        )

        assertEquals("Available", tiles[0].label)
        assertNull(tiles[0].icon)
        assertTrue(tiles[0].isLaunchable)
        assertEquals("Unavailable", tiles[1].label)
        assertNull(tiles[1].icon)
        assertFalse(tiles[1].isLaunchable)
    }

    @Test
    fun resolveTiles_unavailableWithoutMetadata_notLaunchable() {
        val id = identity("p0", "com.gone", "G")
        val placements = listOf(placement("g", id, availability = ItemAvailability.UNAVAILABLE))

        val tiles = HomeRenderProjection.resolveTiles(placements, emptyMap())

        assertFalse(tiles[0].isLaunchable)
        assertNull(tiles[0].label)
    }

    @Test
    fun resolveTiles_metadataUpdateDoesNotReorderOrMutate() {
        val a = identity("p0", "com.a", "A")
        val b = identity("p0", "com.b", "B")
        val placements = listOf(placement("1", a), placement("2", b))
        val before = HomeRenderProjection.resolveTiles(
            placements,
            mapOf(a to metadata("A1", 1), b to metadata("B1", 2)),
        )
        val after = HomeRenderProjection.resolveTiles(
            placements,
            mapOf(a to metadata("A2", 9), b to metadata("B2", 8)),
        )

        assertEquals(listOf("1", "2"), after.map { it.itemId.value })
        assertEquals("A2", after[0].label)
        assertEquals("B2", after[1].label)
        // Earlier snapshot unchanged.
        assertEquals("A1", before[0].label)
        assertEquals(placements[0].position, after[0].position)
        assertEquals(placements[1].span, after[1].span)
    }

    @Test
    fun renderTile_launchableInvariantEnforced() {
        val id = identity("p0", "com.x", "X")
        val icon = HomeIconBytes(byteArrayOf(1))
        // Valid: AVAILABLE + label-only metadata (an icon is optional).
        HomeRenderTile(ItemId("v"), id, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "X", icon, true)
        HomeRenderTile(ItemId("label-only"), id, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "X", null, true)
        // Valid: unavailable with metadata still not launchable.
        HomeRenderTile(ItemId("u"), id, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.UNAVAILABLE, "X", icon, false)
        // Invalid: claims launchable while unavailable.
        try {
            HomeRenderTile(ItemId("bad"), id, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.UNAVAILABLE, "X", icon, true)
            fail("expected launchable invariant rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        // Invalid: claims launchable without joined metadata/label.
        try {
            HomeRenderTile(ItemId("bad2"), id, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, null, null, true)
            fail("expected launchable invariant rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ---- adaptColumns: oracle + five/narrow/wide ----

    @Test
    fun adaptColumns_matchesGridOracle_normalFive() {
        val five = inputs(
            viewportWidthPx = 1080,
            horizontalInsetPx = 0,
            horizontalPaddingPx = 0,
            columnGapPx = 12,
            minimumCellWidthPx = 200,
        )
        val expected = GridViewportAdaptation.columnsFor(1080, 0, 0, 12, 200)
        assertEquals(GridColumns(5), expected)
        assertEquals(expected, HomeRenderProjection.adaptColumns(five))
        assertEquals(5, HomeRenderProjection.adaptColumns(five).count)
    }

    @Test
    fun adaptColumns_narrowViewportReturnsPositiveCountWithoutSixMinimum() {
        val narrow = inputs(
            viewportWidthPx = 200,
            horizontalInsetPx = 0,
            horizontalPaddingPx = 0,
            columnGapPx = 12,
            minimumCellWidthPx = 160,
        )
        val expected = GridViewportAdaptation.columnsFor(200, 0, 0, 12, 160)
        assertEquals(GridColumns(1), expected)
        val actual = HomeRenderProjection.adaptColumns(narrow)
        assertEquals(expected, actual)
        assertEquals(1, actual.count)
    }

    @Test
    fun adaptColumns_wideViewportReturnsMoreThanSix() {
        val wide = inputs(
            viewportWidthPx = 3000,
            horizontalInsetPx = 0,
            horizontalPaddingPx = 0,
            columnGapPx = 12,
            minimumCellWidthPx = 160,
        )
        val expected = GridViewportAdaptation.columnsFor(3000, 0, 0, 12, 160)
        assertTrue(expected.count > GridColumns.NORMAL_PHONE_REFERENCE_COUNT)
        assertEquals(expected, HomeRenderProjection.adaptColumns(wide))
    }

    @Test
    fun adaptColumns_productionMirrorFiveColumnScaleUp() {
        // Production mirror: W=1080, inset=0, padding=24, gap=24, min=180 → 5.
        // Effective content = 1080 - 0 - 2 * 24 = 1032;
        // floor((1032 + 24) / (180 + 24)) = floor(1056 / 204) = 5.
        val expectedFive = GridViewportAdaptation.columnsFor(1080, 0, 24, 24, 180)
        assertEquals(GridColumns(5), expectedFive)
        val five = inputs(
            viewportWidthPx = 1080,
            horizontalInsetPx = 0,
            horizontalPaddingPx = 24,
            columnGapPx = 24,
            minimumCellWidthPx = 180,
        )
        assertEquals(expectedFive, HomeRenderProjection.adaptColumns(five))
        assertEquals(5, HomeRenderProjection.adaptColumns(five).count)

        // Narrow production mirror: W=500 → 2.
        // Effective content = 500 - 48 = 452; floor((452 + 24) / 204) = 2.
        val expectedNarrow = GridViewportAdaptation.columnsFor(500, 0, 24, 24, 180)
        assertEquals(GridColumns(2), expectedNarrow)
        val narrow = inputs(
            viewportWidthPx = 500,
            horizontalInsetPx = 0,
            horizontalPaddingPx = 24,
            columnGapPx = 24,
            minimumCellWidthPx = 180,
        )
        assertEquals(expectedNarrow, HomeRenderProjection.adaptColumns(narrow))

        // Wide production mirror: W=3000 → 14.
        // Effective content = 3000 - 48 = 2952; floor((2952 + 24) / 204) = 14.
        val expectedWide = GridViewportAdaptation.columnsFor(3000, 0, 24, 24, 180)
        assertEquals(GridColumns(14), expectedWide)
        val wide = inputs(
            viewportWidthPx = 3000,
            horizontalInsetPx = 0,
            horizontalPaddingPx = 24,
            columnGapPx = 24,
            minimumCellWidthPx = 180,
        )
        assertEquals(expectedWide, HomeRenderProjection.adaptColumns(wide))
    }

    // ---- measureExtent: floor, margin, oracle ----

    @Test
    fun measureExtent_emptyGridHasNoRows() {
        // Normal policy: no viewport floor and no working margin in the measured
        // extent — an empty grid has no intrinsic rows. The usable-viewport minimum
        // is imposed by the viewport owner, not the measurement.
        val extent = HomeRenderProjection.measureExtent(emptyList(), 10L, 2L)
        val expected = GridVerticalLayout.measure<GridPlacement<ItemId>>(emptyList(), 10L, 2L)
        assertEquals(expected, extent)
        assertEquals(0L, extent.occupiedBottomRowExclusive)
        assertEquals(0L, extent.totalScrollableRows)
    }

    @Test
    fun measureExtent_ignoresWorkingMarginInNormal() {
        // Normal policy: the working margin is validated but never added. Rows 18..20
        // measure from logical row zero through row 20, identical with a zero margin.
        val id = identity("p0", "com.a", "A")
        val placements = listOf(placement("a", id, row = 18, spanRows = 2))
        val extent = HomeRenderProjection.measureExtent(placements, 10L, 2L)
        val oracle = GridVerticalLayout.measure(
            listOf(GridPlacement(ItemId("a"), GridPosition(0, 18), GridSpan(1, 2))),
            10L,
            2L,
        )
        assertEquals(oracle, extent)
        assertEquals(20L, extent.occupiedBottomRowExclusive)
        assertEquals(18L, extent.occupiedMinRow)
        assertEquals(20L, extent.totalScrollableRows)
        assertEquals(extent, HomeRenderProjection.measureExtent(placements, 10L, 0L))
    }

    @Test
    fun measureExtent_preservesAbsoluteLeadingRowsAcrossViewportSizes() {
        val id = identity("p0", "com.a", "A")
        val oneRow = listOf(placement("one", id, row = 5, spanRows = 1))
        val twoRows = listOf(placement("two", id, row = 5, spanRows = 2))

        assertEquals(6L, HomeRenderProjection.measureExtent(oneRow, 1L, 0L).occupiedHeightRows)
        assertEquals(6L, HomeRenderProjection.measureExtent(oneRow, 20L, 0L).occupiedHeightRows)
        assertEquals(7L, HomeRenderProjection.measureExtent(twoRows, 1L, 0L).occupiedHeightRows)
        assertEquals(7L, HomeRenderProjection.measureExtent(twoRows, 20L, 0L).occupiedHeightRows)
    }

    // ---- invalid inputs ----

    @Test
    fun viewportInputs_rejectsInvalidRanges() {
        val cases = listOf(
            { inputs(viewportWidthPx = 0) },
            { inputs(horizontalInsetPx = -1) },
            { inputs(horizontalPaddingPx = -1) },
            { inputs(columnGapPx = -1) },
            { inputs(minimumCellWidthPx = 0) },
            { inputs(viewportRows = 0L) },
            { inputs(workingMarginRows = -1L) },
        )
        cases.forEach { block ->
            try {
                block()
                fail("expected invalid input rejection")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun adaptColumns_nonPositiveEffectiveWidthFailsFast() {
        // Individually valid ranges, but effective content width (100 - 100) is not positive.
        val bad = inputs(viewportWidthPx = 100, horizontalInsetPx = 100)
        try {
            HomeRenderProjection.adaptColumns(bad)
            fail("expected effective width rejection")
        } catch (e: IllegalArgumentException) {
            // expected from delegated GridViewportAdaptation.columnsFor
        }
    }

    @Test
    fun measureExtent_hugeWorkingMarginReturnsIntrinsicWithoutOverflow() {
        // Normal policy: the working margin is validated but never added, so even
        // Long.MAX_VALUE cannot overflow — the intrinsic single-row extent returns.
        val id = identity("p0", "com.a", "A")
        val placements = listOf(placement("a", id, row = 0))
        val extent = HomeRenderProjection.measureExtent(placements, 10L, Long.MAX_VALUE)
        assertEquals(HomeRenderProjection.measureExtent(placements, 10L, 0L), extent)
        assertEquals(1L, extent.occupiedBottomRowExclusive)
        assertEquals(1L, extent.totalScrollableRows)
    }

    // ---- project: homeId fail-fast and end-to-end ----

    @Test
    fun project_rejectsMismatchedHomeId() {
        val id = identity("p0", "com.a", "A")
        val placements = listOf(
            placement("a", id, homeId = homeId),
            placement("b", id, homeId = otherHomeId),
        )
        try {
            HomeRenderProjection.project(homeId, placements, mapOf(id to metadata("A")), inputs())
            fail("expected mismatched homeId rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun project_computesColumnsTilesExtentWithoutReordering() {
        val a = identity("p0", "com.a", "A")
        val b = identity("p0", "com.b", "B")
        val placements = listOf(
            placement("2", b, column = 1, row = 0),
            placement("1", a, column = 0, row = 2, spanColumns = 2, spanRows = 2),
        )
        val meta = mapOf(a to metadata("A"), b to metadata("B"))
        val viewport = inputs()

        val loaded = HomeRenderProjection.project(homeId, placements, meta, viewport)

        assertEquals(homeId, loaded.requestedHomeId)
        assertEquals(listOf("2", "1"), loaded.tiles.map { it.itemId.value })
        assertEquals(HomeRenderProjection.adaptColumns(viewport), loaded.columns)
        assertEquals(
            HomeRenderProjection.measureExtent(placements, viewport.viewportRows, viewport.workingMarginRows),
            loaded.extent,
        )
        assertEquals(4L, loaded.extent.occupiedBottomRowExclusive)
    }

    // ---- softlock: committed extent parity vs content bounds ----

    @Test
    fun extentParity_sparseMultiSpanNonZeroMin_matchesBoundsHeight() {
        val a = identity("p0", "com.a", "A")
        val b = identity("p0", "com.b", "B")
        val placements = listOf(
            placement("near", a, column = 0, row = 5),
            placement("far", b, column = 1, row = 18, spanColumns = 2, spanRows = 2),
        )
        val viewport = inputs()
        val extent = HomeRenderProjection.measureExtent(
            placements,
            viewport.viewportRows,
            viewport.workingMarginRows,
        )
        // Absolute height runs from logical row zero to the far exclusive boundary at row 20.
        assertEquals(20L, extent.occupiedMaxRowExclusive)
        assertEquals(5L, extent.occupiedMinRow)
        assertEquals(20L, extent.occupiedHeightRows)
        assertEquals(20L, extent.totalScrollableRows)
    }

    @Test
    fun extentParity_empty_isZero() {
        val extent = HomeRenderProjection.measureExtent(emptyList(), 10L, 0L)
        assertEquals(0L, extent.occupiedMaxRowExclusive)
        assertEquals(0L, extent.totalScrollableRows)
        assertEquals(0L, extent.occupiedHeightRows)
    }

    @Test
    fun reload_distantValid_contributesToExtentWithoutMovingTiles() {
        // A reload that adds a distant valid tile keeps every position and
        // grows the committed extent to include it (scroll recovery relies on
        // the committed range, never on moving tiles).
        val a = identity("p0", "com.a", "A")
        val b = identity("p0", "com.b", "B")
        val before = listOf(placement("near", a, column = 0, row = 0))
        val after = before + placement("far", b, column = 0, row = 16)
        val viewport = inputs()
        val first = HomeRenderProjection.project(homeId, before, mapOf(a to metadata("A")), viewport)
        val second = HomeRenderProjection.project(homeId, after, mapOf(a to metadata("A"), b to metadata("B")), viewport)
        assertEquals(LogicalPosition(0, 0), second.tiles[0].position)
        assertEquals(LogicalPosition(0, 16), second.tiles[1].position)
        assertEquals(1L, first.extent.occupiedMaxRowExclusive)
        assertEquals(17L, second.extent.occupiedMaxRowExclusive)
        assertEquals(17L, second.extent.totalScrollableRows)
    }
}
