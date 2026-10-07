package me.foxtails.lakehead

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import me.foxtails.lakehead.data.repository.RoomHomePlacementRepository
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level evidence for the home layout JSON export / import round-trip.
 *
 * Unlike `HomeLayoutExportImportTest` (JVM, hand-rolled fake repo) and the
 * `SettingsMenuExportImportOverlayTest` (Compose row-tap only), this test
 * wires the real [HomeLayoutExportImport] against an in-memory Room
 * [PlacementDatabase], writes the encoded JSON to a real on-device file
 * (the same primitive path the SAF flow uses through `ContentResolver`),
 * wipes the placements, then imports the JSON from disk and verifies the
 * placements are restored to their original positions.
 *
 * This is the slice's live SAF verification step. The actual system file
 * picker UI cannot be driven from an instrumentation test on every ROM;
 * this test confirms the codec + coordinator + repository + Room write/read
 * are all sound end-to-end on the device's filesystem.
 */
@RunWith(AndroidJUnit4::class)
class HomeLayoutExportImportRoundTripDeviceTest {

    private lateinit var database: PlacementDatabase
    private lateinit var repository: HomePlacementRepository
    private lateinit var coordinator: HomeLayoutExportImport

    private val homeId = HomeId("home-roundtrip")
    private val columns = 5

    private val packageA = "me.foxtails.lakehead.a"
    private val packageB = "me.foxtails.lakehead.b"
    private val packageC = "me.foxtails.lakehead.c"
    private val profileMain = ProfileId("0")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PlacementDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomHomePlacementRepository(database)
        coordinator = HomeLayoutExportImport(
            homeId = homeId,
            columns = columns,
            repository = repository,
        )
        repository.ensureHome(homeId)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun identity(packageName: String): AppIdentity = AppIdentity(
        profileId = profileMain,
        component = ComponentIdentity(packageName, "${packageName}.MainActivity"),
    )

    private fun seedPlacements(
        itemIds: List<ItemId>,
        packages: List<String>,
        positions: List<LogicalPosition>,
        spans: List<LogicalSpan>,
    ) {
        itemIds.forEachIndexed { i, id ->
            val result = repository.place(
                homeId = homeId,
                itemId = id,
                appIdentity = identity(packages[i]),
                position = positions[i],
                span = spans[i],
                columns = columns,
            )
            assertTrue("seed place #$i failed: $result", result is MutationResult.Accepted)
        }
    }

    @Test
    fun exportToDeviceFile_thenWipe_thenImportFromDeviceFile_restoresIdenticalPlacements() {
        // Seed: three real placements with distinct app identities.
        val seedItemIds = listOf(
            ItemId("seed-1"),
            ItemId("seed-2"),
            ItemId("seed-3"),
        )
        val seedPackages = listOf(packageA, packageB, packageC)
        val seedPositions = listOf(
            LogicalPosition(0, 0),
            LogicalPosition(2, 0),
            LogicalPosition(0, 3),
        )
        val seedSpans = listOf(
            LogicalSpan(2, 2),
            LogicalSpan(1, 1),
            LogicalSpan(3, 1),
        )
        seedPlacements(seedItemIds, seedPackages, seedPositions, seedSpans)

        // Export and write to a real file on the device.
        val (exportedJson, suggestedFilename) = runBlocking { coordinator.exportLayout() }
        assertTrue("suggested filename ends with .json", suggestedFilename.endsWith(".json"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportFile = context.cacheDir.resolve("home-layout-roundtrip-export.json")
        exportFile.writeText(exportedJson.toString(2))
        assertTrue("export file written", exportFile.exists())
        assertTrue("export file non-empty", exportFile.length() > 0)

        // Wipe placements so the import must rebuild from JSON alone.
        for (id in seedItemIds) {
            repository.remove(homeId, id)
        }
        val wiped = repository.load(homeId, columns)
        assertTrue(
            "wiped home must be Loaded with no placements",
            wiped is LoadResult.Loaded && (wiped as LoadResult.Loaded).placements.isEmpty(),
        )

        // Read the file back (same primitive path the SAF flow uses through
        // ContentResolver — both are URI-to-ByteArray IO at heart) and run
        // the import.
        val readJson = JSONObject(exportFile.readText())
        val result = runBlocking { coordinator.importLayout(readJson) }

        // All three placements must have been accepted with no rejection.
        assertTrue(
            "import must return Applied",
            result is ImportResult.Applied,
        )
        val applied = result as ImportResult.Applied
        assertEquals("3 items placed", 3, applied.itemsPlaced)
        assertEquals("no rejected", 0, applied.rejected.size)

        // The repository must now hold all three original placements at
        // their original (column, row) with the original span and app.
        val loaded = repository.load(homeId, columns) as LoadResult.Loaded
        assertEquals("3 placements loaded", seedItemIds.size, loaded.placements.size)

        val byId = loaded.placements.associateBy { it.itemId }
        for (i in seedItemIds.indices) {
            val id = seedItemIds[i]
            val placement = byId.getValue(id)
            assertEquals("app package for $id", seedPackages[i], placement.appIdentity.component.packageName)
            assertEquals(
                "app class for $id",
                "${seedPackages[i]}.MainActivity",
                placement.appIdentity.component.className,
            )
            assertEquals("column for $id", seedPositions[i].column, placement.position.column)
            assertEquals("row for $id", seedPositions[i].row, placement.position.row)
            assertEquals("span columns for $id", seedSpans[i].columns, placement.span.columns)
            assertEquals("span rows for $id", seedSpans[i].rows, placement.span.rows)
        }

        // Sanity: the encoded document alone also round-trips through the
        // codec without the database.
        val document = HomeLayoutCodec.decodeLayout(readJson)
        assertEquals(homeId, document.homeId)
        assertEquals(columns, document.columns)
        assertEquals(seedItemIds.size, document.items.size)
    }
}