package me.foxtails.lakehead.feature.allapps

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.LauncherTheme
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Focused composition tests for the S3 [AllAppsScreen] slice.
 *
 * Covers loading/loaded-empty honesty, label visibility, exact launch/add identity dispatch
 * (including profile scoping), input-order preservation, the honest already-on-Home marker,
 * and pre-decoded icon rendering via [Map.get] with label-only fallback. Runs as a local
 * unit test on Robolectric; the screen is presentation-only so nothing here exercises
 * inventory, persistence, placement, or platform launches.
 */
@RunWith(RobolectricTestRunner::class)
class AllAppsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun identity(profile: String, pkg: String, cls: String) =
        AppIdentity(ProfileId(profile), ComponentIdentity(pkg, cls))

    private val personal = identity("0", "com.example.app", "com.example.app.Main")
    private val work = identity("10", "com.example.app", "com.example.app.Main")

    private fun entry(
        appIdentity: AppIdentity,
        label: String,
        alreadyOnHome: Boolean = false,
    ) = AllAppsEntry(identity = appIdentity, label = label, alreadyOnHome = alreadyOnHome)

    private fun setScreen(
        state: AllAppsViewState,
        icons: Map<AppIdentity, ImageBitmap> = emptyMap(),
        onLaunchApp: (AppIdentity) -> Unit = {},
        onAddToHome: (AppIdentity) -> Unit = {},
    ) {
        composeRule.setContent {
            LauncherTheme {
                AllAppsScreen(
                    state = state,
                    icons = icons,
                    onLaunchApp = onLaunchApp,
                    onAddToHome = onAddToHome,
                )
            }
        }
    }

    @Test
    fun loading_showsLoadingText_andNoListOrRows() {
        setScreen(AllAppsViewState.Loading)

        composeRule.onNodeWithText(LOADING_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText(EMPTY_TEXT).assertDoesNotExist()
        composeRule.onNodeWithTag(ALL_APPS_LIST_TAG).assertDoesNotExist()
        composeRule.onAllNodesWithTag(ALL_APPS_ROW_TAG).assertCountEquals(0)
    }

    @Test
    fun loaded_showsAllLabels() {
        setScreen(
            AllAppsViewState.Loaded(
                listOf(
                    entry(personal, "Personal App"),
                    entry(work, "Work App"),
                ),
            ),
        )

        composeRule.onNodeWithText("Personal App").assertIsDisplayed()
        composeRule.onNodeWithText("Work App").assertIsDisplayed()
        composeRule.onNodeWithText(LOADING_TEXT).assertDoesNotExist()
        composeRule.onNodeWithText(EMPTY_TEXT).assertDoesNotExist()
    }

    @Test
    fun empty_showsHonestEmptyState_andNoRows() {
        setScreen(AllAppsViewState.Loaded(emptyList()))

        composeRule.onNodeWithText(EMPTY_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText(LOADING_TEXT).assertDoesNotExist()
        composeRule.onNodeWithTag(ALL_APPS_LIST_TAG).assertDoesNotExist()
        composeRule.onAllNodesWithTag(ALL_APPS_ROW_TAG).assertCountEquals(0)
    }

    @Test
    fun rowTap_dispatchesExactFullIdentity_includingProfile() {
        var launched: AppIdentity? = null
        var added: AppIdentity? = null
        setScreen(
            AllAppsViewState.Loaded(
                listOf(
                    entry(personal, "Personal App"),
                    entry(work, "Work App"),
                ),
            ),
            onLaunchApp = { launched = it },
            onAddToHome = { added = it },
        )

        composeRule.onNodeWithText("Work App").assertIsDisplayed()
        val workNode = composeRule.onNodeWithText("Work App").fetchSemanticsNode()
        assertTrue(
            "launch row must expose a click action",
            workNode.config.contains(SemanticsActions.OnClick),
        )
        composeRule.onNodeWithText("Work App").performClick()

        composeRule.runOnIdle {
            assertEquals(work, launched)
            assertEquals(null, added)
        }
    }

    @Test
    fun addTap_dispatchesExactFullIdentity_withoutLaunching() {
        var launched: AppIdentity? = null
        var added: AppIdentity? = null
        setScreen(
            AllAppsViewState.Loaded(
                listOf(
                    entry(personal, "Personal App"),
                    entry(work, "Work App"),
                ),
            ),
            onLaunchApp = { launched = it },
            onAddToHome = { added = it },
        )

        composeRule.onNodeWithContentDescription("Add Personal App to Home").assertIsDisplayed()
        val addNode = composeRule.onNodeWithContentDescription("Add Personal App to Home")
            .fetchSemanticsNode()
        assertTrue(
            "add control must expose a click action",
            addNode.config.contains(SemanticsActions.OnClick),
        )
        composeRule.onNodeWithContentDescription("Add Personal App to Home").performClick()

        composeRule.runOnIdle {
            assertEquals(personal, added)
            assertEquals(null, launched)
        }
    }

    @Test
    fun order_matchesInputOrder() {
        val entries = listOf(
            entry(identity("0", "com.example.zebra", "com.example.zebra.Main"), "Zebra"),
            entry(identity("0", "com.example.apple", "com.example.apple.Main"), "Apple"),
            entry(identity("0", "com.example.mango", "com.example.mango.Main"), "Mango"),
        )
        setScreen(AllAppsViewState.Loaded(entries))

        val tops = entries.map { row ->
            composeRule.onNodeWithContentDescription(row.label)
                .assertIsDisplayed()
                .fetchSemanticsNode().positionInRoot.y
        }
        assertTrue(
            "rows must render top-to-bottom in catalogue order: $tops",
            tops.zipWithNext().all { (first, second) -> first < second },
        )
    }

    @Test
    fun alreadyOnHome_showsHonestAddedMarker_withNoAddControlOrDispatch() {
        var launched: AppIdentity? = null
        var added: AppIdentity? = null
        setScreen(
            AllAppsViewState.Loaded(
                listOf(
                    entry(personal, "Personal App", alreadyOnHome = true),
                    entry(work, "Work App", alreadyOnHome = false),
                ),
            ),
            onLaunchApp = { launched = it },
            onAddToHome = { added = it },
        )

        // Honest marker for the on-Home app; no Add control promising a duplicate add.
        composeRule.onNodeWithText(ADDED_MARKER_TEXT).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add Personal App to Home").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Add Work App to Home").assertIsDisplayed()

        // The marker is not clickable: tapping it dispatches nothing.
        composeRule.onNodeWithContentDescription("Personal App already on Home").performClick()
        composeRule.runOnIdle {
            assertEquals(null, launched)
            assertEquals(null, added)
        }

        // The on-Home row still launches.
        composeRule.onNodeWithText("Personal App").performClick()
        composeRule.runOnIdle { assertEquals(personal, launched) }
    }

    @Test
    fun predecodedIcon_rendersDirectly_andMissingRendersLabelOnly() {
        // Built from android.graphics.Bitmap (shadow-backed under Robolectric) instead of
        // ImageBitmap(width, height), whose DisplayMetrics createBitmap path returns null off-device.
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).asImageBitmap()
        setScreen(
            AllAppsViewState.Loaded(
                listOf(
                    entry(personal, "Icon App"),
                    entry(work, "Label Only App"),
                ),
            ),
            icons = mapOf(personal to bitmap),
        )

        composeRule.onNodeWithText("Icon App").assertIsDisplayed()
        composeRule.onNodeWithText("Label Only App").assertIsDisplayed()
        // The icon slot is decorative (contentDescription=null); presence is verified by tag.
        composeRule.onAllNodesWithTag(ALL_APPS_ICON_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun row_meetsOneByOneTileHeight_withAccessibleLaunchArea() {
        // Default tokens: symbolSize 40.dp + 2 * tilePadding 8.dp = 56.dp (the 1x1 tile height).
        setScreen(AllAppsViewState.Loaded(listOf(entry(personal, "Personal App"))))

        composeRule.onNodeWithTag(ALL_APPS_ROW_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp)
        composeRule.onNodeWithContentDescription("Personal App")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Add Personal App to Home")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun icon_usesSharedSymbolSize() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).asImageBitmap()
        setScreen(
            AllAppsViewState.Loaded(listOf(entry(personal, "Icon App"))),
            icons = mapOf(personal to bitmap),
        )

        // Shared AppSymbol renders at the token symbolSize (40.dp by default).
        composeRule.onNodeWithTag(ALL_APPS_ICON_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(40.dp)
            .assertHeightIsEqualTo(40.dp)
    }

    @Test
    fun launchAndAdd_keepButtonRoles_withUnchangedDescriptions() {
        setScreen(AllAppsViewState.Loaded(listOf(entry(personal, "Personal App"))))

        val launchRole = composeRule.onNodeWithContentDescription("Personal App")
            .assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.Role]
        assertEquals(Role.Button, launchRole)
        val addRole = composeRule.onNodeWithContentDescription("Add Personal App to Home")
            .assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.Role]
        assertEquals(Role.Button, addRole)
    }

    @Test
    fun publicSignatures_referenceOnlyPredecodedSnapshots_noPlatformOrDataTypes() {
        // Behavior evidence: icons arrive as already-decoded ImageBitmap snapshots keyed by
        // full identity; the feature never calls LauncherApps/PackageManager/decode/database.
        // Signature evidence: inspect the feature AllAppsScreen entry point for forbidden types.
        val methods = Class.forName("me.foxtails.lakehead.feature.allapps.AllAppsScreenKt").declaredMethods
            .filter { it.name == "AllAppsScreen" }
        assertTrue("AllAppsScreen entry point must exist", methods.isNotEmpty())
        val signature = methods.joinToString("\n") { method ->
            method.genericParameterTypes.joinToString(",") { it.typeName }
        }
        val forbidden = listOf(
            "LauncherApps",
            "PackageManager",
            "Drawable",
            "BitmapFactory",
            "Room",
            "Flow",
            "Cursor",
            "SQLite",
            "Grid",
        )
        forbidden.forEach { token ->
            assertFalse("AllAppsScreen signature must not reference $token: $signature", signature.contains(token))
        }
        assertTrue(
            "AllAppsScreen must accept pre-decoded ImageBitmap snapshots: $signature",
            signature.contains("ImageBitmap"),
        )
    }
}
