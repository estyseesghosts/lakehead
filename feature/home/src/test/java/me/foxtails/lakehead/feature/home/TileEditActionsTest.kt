package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Test

class TileEditActionsTest {
    private val identity = AppIdentity(ProfileId("0"), ComponentIdentity("org.a", "org.a.Main"))

    private fun app() = HomeRenderTile(
        ItemId("a"), identity, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "A", null, true,
    )

    private fun widget() = HomeRenderTile(
        ItemId("w"), identity, LogicalPosition(0, 0), LogicalSpan(4, 2), ItemAvailability.AVAILABLE, "Widget", null, true,
        appWidgetId = 3,
    )

    private fun folder() = HomeRenderTile(
        ItemId("f"), null, LogicalPosition(0, 0), LogicalSpan(1, 1), ItemAvailability.AVAILABLE, "Folder", null, false,
        folderPreview = emptyList(),
    )

    @Test fun anAppOffersOnlyNewFolderWhenNoFolderExists() {
        assertEquals(listOf(TileEditAction.NEW_FOLDER), tileEditActionsFor(app(), hasFolders = false))
    }

    @Test fun anAppOffersAddToFolderOnceAFolderExists() {
        assertEquals(
            listOf(TileEditAction.NEW_FOLDER, TileEditAction.ADD_TO_FOLDER),
            tileEditActionsFor(app(), hasFolders = true),
        )
    }

    @Test fun aWidgetOffersTheSameFolderActionsAsAnApp() {
        assertEquals(tileEditActionsFor(app(), true), tileEditActionsFor(widget(), true))
        assertEquals(tileEditActionsFor(app(), false), tileEditActionsFor(widget(), false))
    }

    @Test fun aFolderTileOffersNoFolderActions() {
        assertEquals(emptyList<TileEditAction>(), tileEditActionsFor(folder(), hasFolders = true))
    }
}
