package me.foxtails.lakehead.feature.home

import androidx.compose.ui.geometry.Offset
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderDropTargetTest {
    private val metrics = homeGridMetricsFor(
        geometry = SquareCellGeometry.fromViewport(
            availableWidthPx = 1000, horizontalPaddingPx = 0, columnGapPx = 0, rowGapPx = 0, columnCount = 5,
        ),
        columns = 5,
        originXPx = 0,
        restOffsetYPx = 0f,
    )

    private fun app(name: String, column: Int) = HomeRenderTile(
        itemId = ItemId(name),
        appIdentity = AppIdentity(ProfileId("0"), ComponentIdentity("org.$name", "org.$name.Main")),
        position = LogicalPosition(column, 0), span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE, label = name, icon = null, isLaunchable = true,
    )

    private fun folder(name: String, column: Int) = HomeRenderTile(
        itemId = ItemId(name), appIdentity = null,
        position = LogicalPosition(column, 0), span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE, label = "Folder", icon = null, isLaunchable = false,
        folderPreview = emptyList(),
    )

    private val tiles = listOf(app("a", 0), app("b", 1), folder("f", 2), folder("g", 3))

    @Test fun pointerOverAFolderTargetsIt() {
        assertEquals(ItemId("f"), folderUnderPointer(tiles, metrics, ItemId("a"), Offset(500f, 100f), 0f))
    }

    @Test fun pointerOverAnAppOrEmptyCellTargetsNothing() {
        assertNull(folderUnderPointer(tiles, metrics, ItemId("a"), Offset(300f, 100f), 0f))
        assertNull(folderUnderPointer(tiles, metrics, ItemId("a"), Offset(900f, 100f), 0f))
    }

    @Test fun aFolderIsNeverDroppedOnAFolder() {
        assertNull(folderUnderPointer(tiles, metrics, ItemId("f"), Offset(700f, 100f), 0f))
    }

    @Test fun scrollShiftsTheContentRowUnderThePointer() {
        // Pointer is viewport-local: scrolled content moves the folder up out from under it.
        assertNull(folderUnderPointer(tiles, metrics, ItemId("a"), Offset(500f, 100f), 400f))
    }

    @Test fun aFolderCanBePickedUpAndMoved() {
        val editing = HomeEditState.Normal.enterOnLongPress(ItemId("f"))
        val moving = editing.beginMove(tiles[2], tiles, metrics, 5f, 5f)
        assertEquals(true, moving.isDragging)
        assertEquals(ItemId("f"), moving.moveDrag?.itemId)
    }

    @Test fun anUnavailableAppStaysPut() {
        val gone = app("gone", 4).copy(isLaunchable = false, label = null, availability = ItemAvailability.UNAVAILABLE)
        val editing = HomeEditState.Normal.enterOnLongPress(ItemId("gone"))
        assertEquals(false, editing.beginMove(gone, tiles + gone, metrics, 5f, 5f).isDragging)
    }
}
