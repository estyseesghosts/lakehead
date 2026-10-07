package me.foxtails.lakehead

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import me.foxtails.lakehead.core.grid.GridPlacement
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridRules
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.RemoveResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-rolled fake [HomePlacementRepository] for testing reflow logic
 * without Android or Room dependencies.
 */
class FakeHomePlacementRepository(
    private val columns: Int = 5,
) : HomePlacementRepository {
    private val homes = mutableSetOf<String>()
    private val placements = mutableMapOf<String, AppPlacement>()
    private val bindings = mutableMapOf<String, TileBinding>()

    override fun load(homeId: HomeId, columns: Int): LoadResult {
        if (homeId.value !in homes) return LoadResult.NotFound
        val items = placements.values.filter { it.homeId == homeId }
        return LoadResult.Loaded(items)
    }

    override fun place(
        homeId: HomeId,
        itemId: ItemId,
        appIdentity: AppIdentity,
        position: LogicalPosition,
        span: LogicalSpan,
        columns: Int,
        replacingId: ItemId?,
    ): MutationResult {
        if (homeId.value !in homes) return MutationResult.NotFound
        if (itemId.value in placements && replacingId == null) {
            return MutationResult.ItemIdAlreadyExists
        }
        // Simple bounds + overlap check for the fake.
        if (position.column + span.columns > columns) {
            return MutationResult.Rejected(GridRules.Result.Rejected(GridRules.Rejection.COLLISION))
        }
        val existing = placements.values
            .filter { it.homeId == homeId && it.itemId != replacingId }
        for (p in existing) {
            if (overlaps(
                    position.column, position.row, span.columns, span.rows,
                    p.position.column, p.position.row, p.span.columns, p.span.rows,
                )
            ) {
                return MutationResult.Rejected(GridRules.Result.Rejected(GridRules.Rejection.COLLISION))
            }
        }
        val placement = AppPlacement(
            homeId, itemId, appIdentity, position, span, ItemAvailability.AVAILABLE,
        )
        placements[itemId.value] = placement
        return MutationResult.Accepted(
            GridRules.Result.Accepted(
                placements.values.map {
                    GridPlacement(it.itemId, GridPosition(it.position.column, it.position.row), GridSpan(it.span.columns, it.span.rows))
                }
            )
        )
    }

    override fun move(homeId: HomeId, itemId: ItemId, position: LogicalPosition, columns: Int): MutationResult =
        MutationResult.NotFound

    override fun resize(homeId: HomeId, itemId: ItemId, span: LogicalSpan, columns: Int): MutationResult =
        MutationResult.NotFound

    override fun remove(homeId: HomeId, itemId: ItemId): RemoveResult = RemoveResult.NotFound

    override fun ensureHome(homeId: HomeId): Boolean {
        homes.add(homeId.value)
        return true
    }

    override fun loadBinding(tileId: ItemId): TileBinding =
        bindings[tileId.value] ?: TileBinding(tileId)

    override fun saveBinding(binding: TileBinding) {
        bindings[binding.tileId.value] = binding
    }

    override fun deleteBinding(tileId: ItemId) {
        bindings.remove(tileId.value)
    }

    private fun overlaps(
        c1: Int, r1: Int, cs1: Int, rs1: Int,
        c2: Int, r2: Int, cs2: Int, rs2: Int,
    ): Boolean =
        c1 < c2 + cs2 && c1 + cs1 > c2 && r1 < r2 + rs2 && r1 + rs1 > r2
}

class HomeLayoutExportImportTest {

    private val testDispatcher = StandardTestDispatcher()
    private val homeId = HomeId("home-main")

    private fun makeJsonDoc(items: List<JSONObject>): JSONObject = JSONObject().apply {
        put("version", 1)
        put("kind", "proto-launch.home-layout/v1")
        put("exportedAtMillis", 1730000000000L)
        put("home", JSONObject().apply {
            put("id", "home-main")
            put("columns", 5)
            put("items", JSONArray().apply { items.forEach { put(it) } })
        })
    }

    private fun makeJsonItem(
        id: String,
        column: Int,
        row: Int,
        columns: Int = 1,
        rows: Int = 1,
        profileId: String = "0",
        packageName: String = "com.example",
        className: String = "com.example.Main",
        shortcuts: List<JSONObject> = emptyList(),
    ) = JSONObject().apply {
        put("id", id)
        put("type", "APP")
        put("column", column)
        put("row", row)
        put("columns", columns)
        put("rows", rows)
        put("app", JSONObject().apply {
            put("profileId", profileId)
            put("packageName", packageName)
            put("className", className)
        })
        if (shortcuts.isNotEmpty()) {
            put("pinnedShortcuts", JSONArray().apply { shortcuts.forEach { put(it) } })
        }
    }

    @Test
    fun import_singleItem_placedAtRequestedPosition() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        val doc = makeJsonDoc(listOf(makeJsonItem("tile-1", column = 2, row = 0)))

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Applied)
        val applied = result as ImportResult.Applied
        assertEquals(1, applied.itemsPlaced)
        assertEquals(0, applied.itemsShifted)
        assertTrue(applied.rejected.isEmpty())
    }

    @Test
    fun import_itemExceedsColumns_reflowsLeft() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        // Item at column=4 with columns=2 exceeds 5-column grid (4+2=6 > 5).
        val doc = makeJsonDoc(listOf(makeJsonItem("tile-1", column = 4, row = 0, columns = 2)))

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Applied)
        val applied = result as ImportResult.Applied
        assertEquals(1, applied.itemsPlaced)
        assertEquals(1, applied.itemsShifted)
        // Should have been reflowed left to column=3 (3+2=5 <= 5).
        val loaded = repo.load(homeId, 5) as LoadResult.Loaded
        assertEquals(3, loaded.placements[0].position.column)
    }

    @Test
    fun import_collisionBetweenImportedItems_reflowsLaterItem() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        // Two items both at (0,0), same 1x1 span. Second should be reflowed.
        val doc = makeJsonDoc(listOf(
            makeJsonItem("tile-a", column = 0, row = 0),
            makeJsonItem("tile-b", column = 0, row = 0),
        ))

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Applied)
        val applied = result as ImportResult.Applied
        assertEquals(2, applied.itemsPlaced)
        assertTrue(applied.itemsShifted >= 1)
        assertTrue(applied.rejected.isEmpty())
    }

    @Test
    fun import_itemCannotFitAnywhere_rejected() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        // Fill the entire first row with existing items.
        repo.ensureHome(homeId)
        for (col in 0 until 5) {
            repo.place(
                homeId, ItemId("existing-$col"),
                AppIdentity(ProfileId("0"), ComponentIdentity("com.exist", "com.exist.Main")),
                LogicalPosition(col, 0), LogicalSpan(1, 1), 5,
            )
        }
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        // Try to import a 1x1 item at row=0 — all cells occupied, and only 1 row exists.
        val doc = makeJsonDoc(listOf(makeJsonItem("tile-1", column = 0, row = 0)))

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Applied)
        val applied = result as ImportResult.Applied
        assertEquals(0, applied.itemsPlaced)
        assertEquals(1, applied.rejected.size)
        assertEquals("tile-1", applied.rejected[0].id)
    }

    @Test
    fun import_withPinnedShortcuts_restoresBindings() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        val shortcutJson = JSONObject().apply {
            put("packageName", "com.example")
            put("profileId", "0")
            put("shortcutId", "sc-1")
        }
        val doc = makeJsonDoc(listOf(makeJsonItem("tile-1", column = 0, row = 0, shortcuts = listOf(shortcutJson))))

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Applied)
        val applied = result as ImportResult.Applied
        assertEquals(1, applied.bindingsRestored)
        val binding = repo.loadBinding(ItemId("tile-1"))
        assertEquals(1, binding.pinnedShortcuts.size)
        assertEquals("sc-1", binding.pinnedShortcuts[0].shortcutId)
    }

    @Test
    fun import_multipleShortcuts_orderPreserved() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        val shortcuts = listOf(
            JSONObject().apply { put("packageName", "com.a"); put("profileId", "0"); put("shortcutId", "first") },
            JSONObject().apply { put("packageName", "com.b"); put("profileId", "0"); put("shortcutId", "second") },
            JSONObject().apply { put("packageName", "com.c"); put("profileId", "0"); put("shortcutId", "third") },
        )
        val doc = makeJsonDoc(listOf(makeJsonItem("tile-1", column = 0, row = 0, shortcuts = shortcuts)))

        coordinator.importLayout(doc)

        val binding = repo.loadBinding(ItemId("tile-1"))
        assertEquals(3, binding.pinnedShortcuts.size)
        assertEquals("first", binding.pinnedShortcuts[0].shortcutId)
        assertEquals("second", binding.pinnedShortcuts[1].shortcutId)
        assertEquals("third", binding.pinnedShortcuts[2].shortcutId)
    }

    @Test
    fun import_unknownType_rejected() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        val doc = makeValidDoc().apply {
            getJSONObject("home").getJSONArray("items").put(JSONObject().apply {
                put("id", "tile-1")
                put("type", "WIDGET")
                put("column", 0); put("row", 0)
                put("columns", 1); put("rows", 1)
                put("app", JSONObject().apply {
                    put("profileId", "0")
                    put("packageName", "com.example")
                    put("className", "com.example.Main")
                })
            })
        }

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Applied)
        val applied = result as ImportResult.Applied
        assertEquals(0, applied.itemsPlaced)
        assertEquals(1, applied.rejected.size)
    }

    @Test
    fun import_wrongVersion_entireDocRejected() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        val doc = JSONObject().apply {
            put("version", 2)
            put("kind", "proto-launch.home-layout/v1")
            put("home", JSONObject().apply {
                put("id", "home-main")
                put("columns", 5)
                put("items", JSONArray())
            })
        }

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Rejected)
    }

    @Test
    fun import_reflowUpWhenRowFull() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        repo.ensureHome(homeId)
        // Fill row 0 completely with existing items.
        for (col in 0 until 5) {
            repo.place(
                homeId, ItemId("existing-$col"),
                AppIdentity(ProfileId("0"), ComponentIdentity("com.exist", "com.exist.Main")),
                LogicalPosition(col, 0), LogicalSpan(1, 1), 5,
            )
        }
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)
        // Item wants row 1 but we only have row 0 filled, so it should land at an empty row.
        val doc = makeJsonDoc(listOf(makeJsonItem("tile-1", column = 0, row = 1)))

        val result = coordinator.importLayout(doc)

        assertTrue(result is ImportResult.Applied)
        val applied = result as ImportResult.Applied
        assertEquals(1, applied.itemsPlaced)
        assertTrue(applied.rejected.isEmpty())
    }

    @Test
    fun export_returnsPlacementsAndBindings() = runTest(testDispatcher) {
        val repo = FakeHomePlacementRepository(5)
        repo.ensureHome(homeId)
        repo.place(
            homeId, ItemId("tile-1"),
            AppIdentity(ProfileId("0"), ComponentIdentity("com.example", "com.example.Main")),
            LogicalPosition(2, 0), LogicalSpan(1, 1), 5,
        )
        repo.saveBinding(
            TileBinding(
                ItemId("tile-1"),
                listOf(ShortcutRef("com.example", ProfileId("0"), "sc-1")),
            )
        )
        val coordinator = HomeLayoutExportImport(homeId, 5, repo, testDispatcher)

        val (json, filename) = coordinator.exportLayout()

        assertTrue(filename.startsWith("home-main-"))
        assertTrue(filename.endsWith(".json"))
        val doc = HomeLayoutCodec.decodeLayout(json)
        assertEquals(1, doc.items.size)
        assertEquals("tile-1", doc.items[0].id)
        assertEquals(2, doc.items[0].column)
        assertEquals(1, doc.items[0].pinnedShortcuts.size)
    }

    private fun makeValidDoc() = JSONObject().apply {
        put("version", 1)
        put("kind", "proto-launch.home-layout/v1")
        put("exportedAtMillis", 1730000000000L)
        put("home", JSONObject().apply {
            put("id", "home-main")
            put("columns", 5)
            put("items", JSONArray())
        })
    }
}