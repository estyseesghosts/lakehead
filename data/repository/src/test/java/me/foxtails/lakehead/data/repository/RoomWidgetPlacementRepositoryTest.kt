package me.foxtails.lakehead.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomeItemType
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.WidgetPlacement
import me.foxtails.lakehead.data.database.HomeEntity
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.database.toEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [RoomHomePlacementRepository] widget-specific operations:
 * [HomePlacementRepository.placeWidget], [HomePlacementRepository.persistedAppWidgetIds],
 * and widget move/resize/remove integrated with the 5-column grid.
 */
@RunWith(RobolectricTestRunner::class)
class RoomWidgetPlacementRepositoryTest {
    private val columns = 5

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), PlacementDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao get() = database.placementDao()
    private val repository: HomePlacementRepository = RoomHomePlacementRepository(database)

    @After fun closeDatabase() = database.close()

    // ---------- helpers ----------

    private fun seedHome(id: String) = dao.insertHome(HomeEntity(id))

    private fun ensureHome(homeId: HomeId) {
        seedHome(homeId.value)
    }

    private fun identity(profile: String = "work", packageName: String = "org.example") =
        AppIdentity(ProfileId(profile), ComponentIdentity(packageName, "$packageName.Home"))

    private fun loadPlacements(homeId: HomeId): List<HomePlacement> {
        val result = repository.load(homeId, columns)
        assertTrue("expected Loaded but got $result", result is LoadResult.Loaded)
        return (result as LoadResult.Loaded).placements
    }

    private fun assertWidget(
        placement: HomePlacement,
        expectedItemId: ItemId,
        expectedAppWidgetId: Int,
        expectedPosition: LogicalPosition,
        expectedSpan: LogicalSpan,
    ) {
        assertTrue("expected WidgetPlacement but got ${placement::class.simpleName}", placement is WidgetPlacement)
        val widget = placement as WidgetPlacement
        assertEquals(expectedItemId, widget.itemId)
        assertEquals(expectedAppWidgetId, widget.appWidgetId)
        assertEquals(expectedPosition, widget.position)
        assertEquals(expectedSpan, widget.span)
        assertEquals(HomeItemType.WIDGET, widget.type)
    }

    private fun assertApp(
        placement: HomePlacement,
        expectedItemId: ItemId,
        expectedPosition: LogicalPosition,
        expectedSpan: LogicalSpan,
    ) {
        assertTrue("expected AppPlacement but got ${placement::class.simpleName}", placement is AppPlacement)
        val app = placement as AppPlacement
        assertEquals(expectedItemId, app.itemId)
        assertEquals(expectedPosition, app.position)
        assertEquals(expectedSpan, app.span)
        assertEquals(HomeItemType.APP, app.type)
    }

    // ---------- (1) placeWidget 4x2 at (0,0), load returns WidgetPlacement with appWidgetId ----------

    @Test fun placeWidget4x2AtOriginAndLoadReturnsWidgetPlacement() {
        val home = HomeId("home")
        ensureHome(home)
        val itemId = ItemId("widget-1")
        val provider = identity("work", "org.widget.provider")
        val appWidgetId = 42

        val result = repository.placeWidget(
            home, itemId, provider, appWidgetId,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        )
        assertTrue(result is MutationResult.Accepted)

        val placements = loadPlacements(home)
        assertEquals(1, placements.size)
        assertWidget(placements[0], itemId, appWidgetId, LogicalPosition(0, 0), LogicalSpan(4, 2))
    }

    // ---------- (2) mixed home: app at (4,0) + 4x2 widget at (0,0) ----------

    @Test fun mixedHomeLoadReturnsBothInOrderWithCorrectTypes() {
        val home = HomeId("home")
        ensureHome(home)
        val widgetId = ItemId("widget-1")
        val widgetProvider = identity("work", "org.widget.provider")
        val widgetAppWidgetId = 99
        val appId = ItemId("app-1")
        val appIdentity = identity("work", "org.app")

        assertTrue(repository.placeWidget(
            home, widgetId, widgetProvider, widgetAppWidgetId,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        ) is MutationResult.Accepted)

        assertTrue(repository.place(
            home, appId, appIdentity, LogicalPosition(4, 0), LogicalSpan(1, 1), columns,
        ) is MutationResult.Accepted)

        val placements = loadPlacements(home)
        assertEquals(2, placements.size)
        // Widget first (rowid order — widget inserted first)
        assertWidget(placements[0], widgetId, widgetAppWidgetId, LogicalPosition(0, 0), LogicalSpan(4, 2))
        assertApp(placements[1], appId, LogicalPosition(4, 0), LogicalSpan(1, 1))
    }

    // ---------- (3) move widget to another free spot, keeps appWidgetId ----------

    @Test fun moveWidgetPersistsAndKeepsAppWidgetId() {
        val home = HomeId("home")
        ensureHome(home)
        val itemId = ItemId("widget-move")
        val provider = identity("work", "org.widget.provider")
        val appWidgetId = 7

        assertTrue(repository.placeWidget(
            home, itemId, provider, appWidgetId,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        ) is MutationResult.Accepted)

        val moveResult = repository.move(home, itemId, LogicalPosition(0, 2), columns)
        assertTrue(moveResult is MutationResult.Accepted)

        val placements = loadPlacements(home)
        assertEquals(1, placements.size)
        assertWidget(placements[0], itemId, appWidgetId, LogicalPosition(0, 2), LogicalSpan(4, 2))

        // Also verify the binding entity survived the move
        assertNotNull(dao.widgetBinding(itemId.value))
        assertEquals(appWidgetId, dao.widgetBinding(itemId.value)!!.appWidgetId)
    }

    // ---------- (4a) resize widget 4x2 -> 5x3 persists span ----------

    @Test fun resizeWidgetToLargerSpanPersists() {
        val home = HomeId("home")
        ensureHome(home)
        val itemId = ItemId("widget-resize")
        val provider = identity("work", "org.widget.provider")
        val appWidgetId = 21

        assertTrue(repository.placeWidget(
            home, itemId, provider, appWidgetId,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        ) is MutationResult.Accepted)

        val resizeResult = repository.resize(home, itemId, LogicalSpan(5, 3), columns)
        assertTrue(resizeResult is MutationResult.Accepted)

        val placements = loadPlacements(home)
        assertEquals(1, placements.size)
        assertWidget(placements[0], itemId, appWidgetId, LogicalPosition(0, 0), LogicalSpan(5, 3))
    }

    // ---------- (4b) resize that collides with an app is Rejected and nothing changes ----------

    @Test fun resizeWidgetCollisionWithAppIsRejectedAndUnchanged() {
        val home = HomeId("home")
        ensureHome(home)
        val widgetId = ItemId("widget-4x2")
        val provider = identity("work", "org.widget.provider")
        val appWidgetId = 55

        assertTrue(repository.placeWidget(
            home, widgetId, provider, appWidgetId,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        ) is MutationResult.Accepted)

        val appId = ItemId("app-blocker")
        assertTrue(repository.place(
            home, appId, identity("work", "org.app"),
            LogicalPosition(0, 2), LogicalSpan(1, 1), columns,
        ) is MutationResult.Accepted)

        // Trying to resize widget from 4x2 to 4x3 should collide with the app at (0,2)
        val resizeResult = repository.resize(home, widgetId, LogicalSpan(4, 3), columns)
        assertTrue("expected Rejected but got $resizeResult", resizeResult is MutationResult.Rejected)

        // Verify nothing changed
        val placements = loadPlacements(home)
        assertEquals(2, placements.size)
        assertWidget(placements[0], widgetId, appWidgetId, LogicalPosition(0, 0), LogicalSpan(4, 2))
        assertApp(placements[1], appId, LogicalPosition(0, 2), LogicalSpan(1, 1))
    }

    // ---------- (5) remove widget deletes the binding and persistedAppWidgetIds no longer contains it ----------

    @Test fun removeWidgetDeletesBindingAndClearsFromPersistedIds() {
        val home = HomeId("home")
        ensureHome(home)
        val itemId = ItemId("widget-remove")
        val provider = identity("work", "org.widget.provider")
        val appWidgetId = 101

        assertTrue(repository.placeWidget(
            home, itemId, provider, appWidgetId,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        ) is MutationResult.Accepted)

        assertEquals(setOf(appWidgetId), repository.persistedAppWidgetIds())
        assertNotNull(dao.widgetBinding(itemId.value))

        assertEquals(RemoveResult.Removed, repository.remove(home, itemId))

        assertNull(dao.widgetBinding(itemId.value))
        assertTrue(repository.persistedAppWidgetIds().isEmpty())
    }

    // ---------- (6) placeWidget overlapping existing app is Rejected, persistedAppWidgetIds stays empty ----------

    @Test fun placeWidgetOverlappingAppIsRejectedAndNoBindingPersisted() {
        val home = HomeId("home")
        ensureHome(home)

        assertTrue(repository.place(
            home, ItemId("existing-app"), identity("work", "org.app"),
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        ) is MutationResult.Accepted)

        val widgetId = ItemId("widget-overlap")
        val result = repository.placeWidget(
            home, widgetId, identity("work", "org.widget.provider"), 88,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        )
        assertTrue("expected Rejected but got $result", result is MutationResult.Rejected)

        assertTrue(repository.persistedAppWidgetIds().isEmpty())
        assertNull(dao.widgetBinding(widgetId.value))
    }

    // ---------- (7) gap normalization: widget at row 3 with empty rows above shifts up, keeps type and appWidgetId ----------

    @Test fun loadNormalizesWidgetGapAndPreservesTypeAndAppWidgetId() {
        val home = HomeId("home")
        ensureHome(home)

        // Place a widget at (0, 3) via the repository so it's valid, then manually
        // move it to a high row to simulate a persisted excess gap.
        val itemId = ItemId("widget-gap")
        val provider = identity("work", "org.widget.provider")
        val appWidgetId = 77

        assertTrue(repository.placeWidget(
            home, itemId, provider, appWidgetId,
            LogicalPosition(0, 0), LogicalSpan(4, 2), columns,
        ) is MutationResult.Accepted)

        // Move the widget far below to create a gap, then place another item above
        // so the gap normalization path is triggered on load.
        assertTrue(repository.move(home, itemId, LogicalPosition(0, 10), columns) is MutationResult.Accepted)

        // Place an app at (0, 0) so the widget won't be the only/first item
        assertTrue(repository.place(
            home, ItemId("anchor-app"), identity("work", "org.app"),
            LogicalPosition(0, 0), LogicalSpan(1, 1), columns,
        ) is MutationResult.Accepted)

        // Load triggers normalization: excessive gap between anchor (row 0) and widget (row 10)
        // should be reduced so widget shifts up. The load order is by rowid (insertion order),
        // not by normalized position; widget was inserted first so it comes first.
        val placements = loadPlacements(home)
        assertEquals(2, placements.size)
        val byItemId = placements.associateBy { it.itemId }

        val anchorPlacement = byItemId.getValue(ItemId("anchor-app"))
        assertApp(anchorPlacement, ItemId("anchor-app"), LogicalPosition(0, 0), LogicalSpan(1, 1))

        val widgetPlacement = byItemId.getValue(itemId)
        assertTrue(widgetPlacement is WidgetPlacement)
        val widget = widgetPlacement as WidgetPlacement
        assertEquals(itemId, widget.itemId)
        assertEquals(appWidgetId, widget.appWidgetId)
        assertEquals(HomeItemType.WIDGET, widget.type)
        // The widget should have shifted closer to the anchor (gap of 10 reduced)
        assertTrue(
            "widget row should be less than 10 after normalization but was ${widget.position.row}",
            widget.position.row < 10,
        )
        assertEquals(LogicalSpan(4, 2), widget.span)
    }
}