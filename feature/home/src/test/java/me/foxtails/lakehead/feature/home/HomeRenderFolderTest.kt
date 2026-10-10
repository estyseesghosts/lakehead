package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.FolderPlacement
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.WidgetPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRenderFolderTest {
    private val home = HomeId("home")
    private val folderId = ItemId("f")

    private fun id(name: String) = AppIdentity(ProfileId("0"), ComponentIdentity("org.$name", "org.$name.Main"))

    private fun member(
        name: String,
        column: Int,
        row: Int,
        columns: Int = 1,
        rows: Int = 1,
    ): HomePlacement = AppPlacement(
        home, ItemId(name), id(name), LogicalPosition(column, row), LogicalSpan(columns, rows),
        ItemAvailability.AVAILABLE, parentFolderId = folderId,
    )

    private val folder = FolderPlacement(
        home, folderId, LogicalPosition(2, 1), LogicalSpan(1, 1), ItemAvailability.AVAILABLE,
    )

    @Test fun previewFollowsRowMajorOrderOfTheInteriorGridNotPersistedOrder() {
        val members = listOf(member("d", 1, 1), member("b", 1, 0), member("c", 0, 1), member("a", 0, 0))
        assertEquals(listOf(id("a"), id("b"), id("c"), id("d")), HomeRenderProjection.folderPreview(members))
    }

    @Test fun previewIsBoundedToFourEntries() {
        val members = (0 until 7).map { member("m$it", it % 5, it / 5) }
        val preview = HomeRenderProjection.folderPreview(members)
        assertEquals(FOLDER_PREVIEW_MAX, preview.size)
        assertEquals(listOf(id("m0"), id("m1"), id("m2"), id("m3")), preview)
    }

    @Test fun aWideMemberTakesOnePreviewCellAndSortsByItsOrigin() {
        val members = listOf(member("wide", 0, 0, 4, 2), member("next", 0, 2), member("side", 4, 0))
        assertEquals(listOf(id("wide"), id("side"), id("next")), HomeRenderProjection.folderPreview(members))
    }

    @Test fun aWidgetMemberPreviewsItsProvidersSymbol() {
        val widget = WidgetPlacement(
            home, ItemId("w"), id("clock"), 5, LogicalPosition(0, 0), LogicalSpan(4, 2),
            ItemAvailability.AVAILABLE, parentFolderId = folderId,
        )
        assertEquals(listOf(id("clock"), id("a")), HomeRenderProjection.folderPreview(listOf(widget, member("a", 0, 2))))
    }

    @Test fun anEmptyFolderPreviewsNothing() {
        assertEquals(emptyList<AppIdentity>(), HomeRenderProjection.folderPreview(emptyList()))
    }

    @Test fun aFolderPlacementProjectsToAFolderTile() {
        val tiles = HomeRenderProjection.resolveTiles(
            listOf(folder),
            emptyMap(),
            mapOf(folderId to listOf(member("a", 0, 0), member("b", 1, 0))),
        )
        val tile = tiles.single()
        assertTrue(tile.isFolder)
        assertNull(tile.appIdentity)
        assertEquals(FOLDER_LABEL, tile.label)
        assertEquals(listOf(id("a"), id("b")), tile.folderPreview)
        assertEquals(LogicalPosition(2, 1), tile.position)
        assertEquals(LogicalSpan(1, 1), tile.span)
    }

    @Test fun aFolderTileIsNotLaunchableAndNotResizable() {
        val tile = HomeRenderProjection.resolveTiles(listOf(folder), emptyMap()).single()
        assertFalse(tile.isLaunchable)
        assertFalse(tile.isResizable)
        assertFalse(tile.isWidget)
    }

    @Test fun appTilesStayLaunchableAndResizable() {
        val app = AppPlacement(home, ItemId("a"), id("a"), LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE)
        val tile = HomeRenderProjection.resolveTiles(
            listOf(app), mapOf(id("a") to HomeAppMetadata("A")),
        ).single()
        assertTrue(tile.isLaunchable)
        assertTrue(tile.isResizable)
        assertFalse(tile.isFolder)
    }

    @Test fun aFolderWithNoMembersKnownStillProjectsWithAnEmptyPreview() {
        val tile = HomeRenderProjection.resolveTiles(listOf(folder), emptyMap()).single()
        assertEquals(emptyList<AppIdentity>(), tile.folderPreview)
    }

    @Test fun renderTileInvariantsRejectInconsistentFolders() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeRenderTile(folderId, id("a"), LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "Folder", null, false, folderPreview = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            HomeRenderTile(folderId, null, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "Folder", null, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HomeRenderTile(folderId, null, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "Folder", null, false, appWidgetId = 3, folderPreview = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            HomeRenderTile(folderId, null, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "Folder", null, false, folderPreview = List(5) { id("x$it") })
        }
    }

    @Test fun projectKeepsFolderTilesInPlacementOrderAndMeasuresThemInExtent() {
        val app = AppPlacement(home, ItemId("a"), id("a"), LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE)
        val state = HomeRenderProjection.project(
            home, listOf(app, folder), mapOf(id("a") to HomeAppMetadata("A")),
            HomeViewportInputs(viewportWidthPx = 1080, columnGapPx = 12, minimumCellWidthPx = 160, viewportRows = 10L),
            folderMembers = mapOf(folderId to listOf(member("a", 0, 0))),
        )
        assertEquals(listOf(ItemId("a"), folderId), state.tiles.map { it.itemId })
        assertEquals(listOf(id("a")), state.tiles[1].folderPreview)
    }
}
