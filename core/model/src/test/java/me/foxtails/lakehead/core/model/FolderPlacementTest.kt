package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FolderPlacementTest {
    private val app = AppIdentity(ProfileId("0"), ComponentIdentity("com.example", "com.example.Main"))

    private val folder = FolderPlacement(
        homeId = HomeId("home"),
        itemId = ItemId("f1"),
        position = LogicalPosition(2, 1),
        span = LogicalSpan(1, 1),
        availability = ItemAvailability.AVAILABLE,
    )

    @Test
    fun folderIsTopLevelAndTypedFolder() {
        assertEquals(HomeItemType.FOLDER, folder.type)
        assertNull(folder.parentFolderId)
    }

    @Test
    fun withGeometryKeepsFolderIdentityAndStaysTopLevel() {
        val moved = folder.withGeometry(LogicalPosition(0, 4), LogicalSpan(1, 1))
        assertEquals(ItemId("f1"), moved.itemId)
        assertEquals(LogicalPosition(0, 4), moved.position)
        assertNull(moved.parentFolderId)
    }

    @Test
    fun memberKeepsParentThroughWithGeometry() {
        val member = AppPlacement(
            HomeId("home"), ItemId("a"), app, LogicalPosition(0, 0), LogicalSpan(2, 2),
            ItemAvailability.AVAILABLE, parentFolderId = ItemId("f1"),
        )
        val moved = member.withGeometry(LogicalPosition(1, 1), LogicalSpan(1, 1))
        assertEquals(ItemId("f1"), moved.parentFolderId)
        assertEquals(app, moved.appIdentity)
    }

    @Test
    fun widgetMemberKeepsParentAndBinding() {
        val member = WidgetPlacement(
            HomeId("home"), ItemId("w"), app, 7, LogicalPosition(0, 0), LogicalSpan(4, 2),
            ItemAvailability.AVAILABLE, parentFolderId = ItemId("f1"),
        )
        val moved = member.withGeometry(LogicalPosition(0, 2), LogicalSpan(4, 2))
        assertEquals(ItemId("f1"), moved.parentFolderId)
        assertEquals(7, moved.appWidgetId)
    }

    @Test
    fun anItemCannotBeItsOwnParent() {
        assertThrows(IllegalArgumentException::class.java) {
            AppPlacement(
                HomeId("home"), ItemId("a"), app, LogicalPosition(0, 0), LogicalSpan(1, 1),
                ItemAvailability.AVAILABLE, parentFolderId = ItemId("a"),
            )
        }
    }
}
