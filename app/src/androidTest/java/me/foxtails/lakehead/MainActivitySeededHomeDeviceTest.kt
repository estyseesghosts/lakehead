package me.foxtails.lakehead

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.data.database.AppReferenceEntity
import me.foxtails.lakehead.data.database.HomeEntity
import me.foxtails.lakehead.data.database.PlacementDatabase
import me.foxtails.lakehead.data.database.PlacementEntity
import me.foxtails.lakehead.platform.launcherapps.LauncherAppsAppCatalogue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device evidence for the production route reading only an explicitly seeded D1 Home. */
@RunWith(AndroidJUnit4::class)
class MainActivitySeededHomeDeviceTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun removeInstrumentationFixture() {
        cleanDatabase()
    }

    @Test
    fun missingHomeStaysNotFoundAndDoesNotProvisionAnyRows() {
        cleanDatabase()
        val scenario = launchMainActivity()
        try {
            waitForText("Home not found")
        } finally {
            scenario.close()
        }
        try {
            withDatabase { database ->
                assertEquals(0, database.placementDao().homeCount())
                assertEquals(0, database.placementDao().placementCount())
                assertEquals(0, database.placementDao().appReferenceCount())
            }
        } finally {
            cleanDatabase()
        }
    }

    @Test
    fun explicitlySeededEmptyHomeShowsEmptyStateAndOnlyTruthfulHomeRoleControl() {
        cleanDatabase()
        insertHomeOnly()
        val catalogue = discoverCatalogue()
        val scenario = launchMainActivity()
        try {
            awaitActivityCatalogue(scenario, setOf(catalogue.first().identity))
            waitForText("Home is empty")
            waitForText("Set ProtoLaunch as default home")
            assertEquals(
                1,
                composeRule.onAllNodesWithText("Set ProtoLaunch as default home").fetchSemanticsNodes().size,
            )
            catalogue.firstOrNull()?.label?.let { label ->
                waitForTextAbsent(label)
            }
        } finally {
            scenario.close()
            cleanDatabase()
        }
    }

    @Test
    fun seededPlacementsKeepCatalogueLabelsAndOrderAndOnlyLaunchExactAvailableIdentity() {
        cleanDatabase()
        val catalogueEntries = discoverCatalogue()
        val entries = catalogueEntries
            .filter { candidate -> catalogueEntries.count { it.label == candidate.label } == 1 }
            .distinctBy { it.identity }
            .take(2)
        assertTrue("Device catalogue needs two real launchable identities", entries.size == 2)
        val unseededEntry = catalogueEntries.firstOrNull { candidate ->
            catalogueEntries.count { it.label == candidate.label } == 1 &&
                entries.none { it.identity == candidate.identity }
        }
        assertTrue("Device catalogue needs one unseeded entry for the no-conversion assertion", unseededEntry != null)

        val unavailableIdentity = AppIdentity(
            profileId = ProfileId("missing-profile"),
            component = ComponentIdentity("example.missing", "example.missing.NotInstalled"),
        )
        seedHome(
            entries.mapIndexed { index, entry ->
                placement("real-$index", entry.identity, index, ItemAvailability.AVAILABLE)
            } + placement("missing", unavailableIdentity, 2, ItemAvailability.UNAVAILABLE),
        )

        val scenario = launchMainActivity()
        val dispatched = CopyOnWriteArrayList<AppIdentity>()
        try {
            awaitActivityCatalogue(scenario, (entries + unseededEntry!!).mapTo(mutableSetOf()) { it.identity })
            scenario.onActivity { activity ->
                activity.appLauncherForTest = me.foxtails.lakehead.platform.launcherapps.AppLauncher { identity ->
                    dispatched += identity
                    true
                }
            }
            entries.forEach { entry -> waitForText(entry.label) }
            waitForText(unavailableLabel())
            waitForTextAbsent(unseededEntry.label)

            val firstBounds = composeRule.onNodeWithText(entries[0].label).fetchSemanticsNode().boundsInRoot
            val secondBounds = composeRule.onNodeWithText(entries[1].label).fetchSemanticsNode().boundsInRoot
            val firstPrecedesSecond = firstBounds.top < secondBounds.top ||
                (firstBounds.top == secondBounds.top && firstBounds.left < secondBounds.left)
            assertTrue("Persisted placement order should remain row-major", firstPrecedesSecond)

            composeRule.onNodeWithText(unavailableLabel()).performClick()
            assertTrue("Unavailable placement must not dispatch a launch", dispatched.toList().isEmpty())
            composeRule.onNodeWithText(entries[1].label).performClick()
            assertEquals(listOf(entries[1].identity), dispatched.toList())
        } finally {
            scenario.close()
            cleanDatabase()
        }
    }

    private fun discoverCatalogue(): List<me.foxtails.lakehead.core.model.LauncherAppEntry> = runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val catalogue = LauncherAppsAppCatalogue(context, scope)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { catalogue.start() }
            withTimeout(30_000) { catalogue.apps.first { it.isNotEmpty() } }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { catalogue.stop() }
            scope.cancel()
        }
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java)

    private fun awaitActivityCatalogue(
        scenario: ActivityScenario<MainActivity>,
        expectedIdentities: Set<AppIdentity>,
    ) {
        lateinit var catalogue: LauncherAppsAppCatalogue
        scenario.onActivity { activity -> catalogue = activity.appCatalogue }
        runBlocking {
            withTimeout(30_000) {
                catalogue.apps.first { entries -> expectedIdentities.all { identity -> entries.any { it.identity == identity } } }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextAbsent(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    /** The feature-owned fallback is internal to its module; read its actual constant, not a copy. */
    private fun unavailableLabel(): String =
        Class.forName("me.foxtails.lakehead.feature.home.HomeCanvasTileKt")
            .getField("UNAVAILABLE_LABEL")
            .get(null) as String

    private fun insertHomeOnly() = withDatabase { database ->
        database.placementDao().insertHome(HomeEntity(PRODUCTION_HOME_ID.value))
    }

    private fun seedHome(placements: List<PlacementEntity>) = withDatabase { database ->
        val dao = database.placementDao()
        database.runInTransaction {
            dao.insertHome(HomeEntity(PRODUCTION_HOME_ID.value))
            placements.forEach { placement ->
                dao.insertAppReference(
                    AppReferenceEntity(placement.profileId, placement.packageName, placement.className),
                )
                dao.insertPlacement(placement)
            }
        }
    }

    private fun placement(
        itemId: String,
        identity: AppIdentity,
        column: Int,
        availability: ItemAvailability,
    ) = PlacementEntity(
        itemId,
        PRODUCTION_HOME_ID.value,
        "APP",
        identity.profileId.value,
        identity.component.packageName,
        identity.component.className,
        column,
        0,
        1,
        1,
        availability.name,
    )

    private fun cleanDatabase() = withDatabase { database ->
        database.runInTransaction {
            database.openHelper.writableDatabase.execSQL("DELETE FROM placements")
            database.openHelper.writableDatabase.execSQL("DELETE FROM app_references")
            database.openHelper.writableDatabase.execSQL("DELETE FROM homes")
        }
    }

    private inline fun <T> withDatabase(block: (PlacementDatabase) -> T): T {
        val database = Room.databaseBuilder(context, PlacementDatabase::class.java, "placements.db")
            .allowMainThreadQueries()
            .build()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }
}
