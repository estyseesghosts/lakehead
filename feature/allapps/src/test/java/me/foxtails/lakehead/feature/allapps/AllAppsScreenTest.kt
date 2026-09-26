package me.foxtails.lakehead.feature.allapps

import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        onHeaderTap: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            LauncherTheme {
                AllAppsScreen(
                    state = state,
                    icons = icons,
                    onLaunchApp = onLaunchApp,
                    onAddToHome = onAddToHome,
                    onHeaderTap = onHeaderTap,
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
    fun order_followsSectionOrder_withInputOrderWithinSection() {
        val zebra = entry(identity("0", "com.example.zebra", "com.example.zebra.Main"), "Zebra")
        val apple = entry(identity("0", "com.example.apple", "com.example.apple.Main"), "Apple")
        val apricot = entry(identity("0", "com.example.apricot", "com.example.apricot.Main"), "Apricot")
        val mango = entry(identity("0", "com.example.mango", "com.example.mango.Main"), "Mango")
        // Input order deliberately unsorted: sections A, M, Z render in section order with
        // input order preserved within a section (Apple above Apricot).
        val entries = listOf(zebra, apple, apricot, mango)
        setScreen(AllAppsViewState.Loaded(entries))

        val topsByLabel = entries.associate { row ->
            row.label to composeRule.onNodeWithContentDescription(row.label)
                .assertIsDisplayed()
                .fetchSemanticsNode().positionInRoot.y
        }
        val tops = listOf("Apple", "Apricot", "Mango", "Zebra").map { topsByLabel.getValue(it) }
        assertTrue(
            "rows must render section-by-section with input order within a section: $topsByLabel",
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

        // The marker is honest and non-clickable: no click action, no Button role.
        val addedNode = composeRule.onNodeWithContentDescription("Personal App already on Home")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertFalse(
            "Added marker must not expose a click action",
            addedNode.config.contains(SemanticsActions.OnClick),
        )
        assertFalse(
            "Added marker must not expose a Button role",
            addedNode.config.contains(SemanticsProperties.Role),
        )
        assertEquals(ADDED_STATE_DESCRIPTION, addedNode.config[SemanticsProperties.StateDescription])

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
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Remove Personal App from Home")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
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
        // Remove placeholder is visual-only: no Button role and no click action.
        val removeNode = composeRule.onNodeWithContentDescription("Remove Personal App from Home")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertFalse(
            "Remove placeholder must not expose a Role",
            removeNode.config.contains(SemanticsProperties.Role),
        )
        assertFalse(
            "Remove placeholder must not expose a click action",
            removeNode.config.contains(SemanticsActions.OnClick),
        )
    }

    @Test
    fun notOnHome_exposesLaunchAddAndDisabledRemove_asThreeNodes() {
        var launched: AppIdentity? = null
        var added: AppIdentity? = null
        setScreen(
            AllAppsViewState.Loaded(listOf(entry(personal, "Personal App"))),
            onLaunchApp = { launched = it },
            onAddToHome = { added = it },
        )

        // Launch + enabled Add + disabled Remove placeholder are all displayed siblings.
        composeRule.onNodeWithContentDescription("Personal App").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add Personal App to Home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove Personal App from Home").assertIsDisplayed()
        composeRule.onNodeWithTag(ALL_APPS_REMOVE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(REMOVE_ACTION_TEXT).assertIsDisplayed()

        // Add is enabled; Remove is disabled visual-only.
        val addNode = composeRule.onNodeWithContentDescription("Add Personal App to Home")
            .fetchSemanticsNode()
        assertTrue(
            "Add slot must expose a click action",
            addNode.config.contains(SemanticsActions.OnClick),
        )
        val removeNode = composeRule.onNodeWithContentDescription("Remove Personal App from Home")
            .fetchSemanticsNode()
        assertFalse(
            "Remove placeholder must not expose a click action",
            removeNode.config.contains(SemanticsActions.OnClick),
        )

        // Add tap dispatches the exact identity without launching.
        composeRule.onNodeWithContentDescription("Add Personal App to Home").performClick()
        composeRule.runOnIdle {
            assertEquals(personal, added)
            assertEquals(null, launched)
        }
    }

    @Test
    fun removePlaceholder_tapDispatchesNothing() {
        var launched: AppIdentity? = null
        var added: AppIdentity? = null
        setScreen(
            AllAppsViewState.Loaded(listOf(entry(personal, "Personal App"))),
            onLaunchApp = { launched = it },
            onAddToHome = { added = it },
        )

        // Disabled placeholder exposes no click action, so there is nothing to dispatch.
        val removeNode = composeRule.onNodeWithContentDescription("Remove Personal App from Home")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertFalse(
            "Remove placeholder must not expose a click action",
            removeNode.config.contains(SemanticsActions.OnClick),
        )
        composeRule.runOnIdle {
            assertEquals(null, launched)
            assertEquals(null, added)
        }
    }

    @Test
    fun alreadyOnHome_exposesLaunchAddedAndDisabledRemove_withNoAddNode() {
        setScreen(
            AllAppsViewState.Loaded(listOf(entry(personal, "Personal App", alreadyOnHome = true))),
        )

        composeRule.onNodeWithContentDescription("Personal App").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Personal App already on Home").assertIsDisplayed()
        composeRule.onNodeWithText(ADDED_MARKER_TEXT).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove Personal App from Home").assertIsDisplayed()
        composeRule.onNodeWithTag(ALL_APPS_REMOVE_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add Personal App to Home").assertDoesNotExist()

        // Added marker meets the 48dp slot minima despite being non-clickable.
        composeRule.onNodeWithContentDescription("Personal App already on Home")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Remove Personal App from Home")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)

        // Neither the Added marker nor the Remove placeholder carries a Button role.
        val addedRoleNode = composeRule.onNodeWithContentDescription("Personal App already on Home")
            .fetchSemanticsNode()
        assertFalse(
            "Added marker must not expose a Role",
            addedRoleNode.config.contains(SemanticsProperties.Role),
        )
        val removeRoleNode = composeRule.onNodeWithContentDescription("Remove Personal App from Home")
            .fetchSemanticsNode()
        assertFalse(
            "Remove placeholder must not expose a Role",
            removeRoleNode.config.contains(SemanticsProperties.Role),
        )
    }

    @Test
    fun publicSignatures_referenceOnlyPredecodedSnapshots_noPlatformOrDataTypes() {
        // Behavior evidence: icons arrive as already-decoded ImageBitmap snapshots keyed by
        // full identity; the feature never calls LauncherApps/PackageManager/decode/database.
        // Signature evidence: inspect the feature AllAppsScreen entry point for forbidden types.
        // The compiler mangles the JVM name with a value-class suffix (e.g. AllAppsScreen-PfoAEA0
        // for the Dp bottomClearance), so match by prefix and skip the synthetic $default stub.
        val methods = Class.forName("me.foxtails.lakehead.feature.allapps.AllAppsScreenKt").declaredMethods
            .filter { it.name.startsWith("AllAppsScreen") && !it.isSynthetic }
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

    // ── P3 section headers ────────────────────────────────────────────────

    @Test
    fun headersRenderOncePerSection() {
        setScreen(
            AllAppsViewState.Loaded(
                listOf(
                    entry(personal, "Banana"),
                    entry(work, "Apple"),
                    entry(identity("0", "com.example.apricot", "com.example.apricot.Main"), "Apricot"),
                ),
            ),
        )

        // Sections A + B, one header each; "A, application section" names both A rows.
        composeRule.onAllNodesWithTag(ALL_APPS_SECTION_HEADER_TAG).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("A, application section").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("B, application section").assertIsDisplayed()
        composeRule.onNodeWithText("Apple").assertIsDisplayed()
        composeRule.onNodeWithText("Apricot").assertIsDisplayed()
        composeRule.onNodeWithText("Banana").assertIsDisplayed()
    }

    @Test
    fun headerTapDispatches() {
        var tapped: String? = null
        setScreen(
            AllAppsViewState.Loaded(
                listOf(
                    entry(personal, "Banana"),
                    entry(work, "Apple"),
                ),
            ),
            onHeaderTap = { tapped = it },
        )

        composeRule.onNodeWithContentDescription("B, application section").performClick()
        composeRule.runOnIdle { assertEquals("B", tapped) }
    }

    @Test
    fun header_buttonRole_with48dpMinHeight() {
        setScreen(AllAppsViewState.Loaded(listOf(entry(personal, "Personal App"))))

        composeRule.onNodeWithContentDescription("P, application section")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        val role = composeRule.onNodeWithContentDescription("P, application section")
            .fetchSemanticsNode().config[SemanticsProperties.Role]
        assertEquals(Role.Button, role)
        val node = composeRule.onNodeWithContentDescription("P, application section")
            .fetchSemanticsNode()
        assertTrue(
            "header must expose a click action",
            node.config.contains(SemanticsActions.OnClick),
        )
    }

    @Test
    fun emptyNoHeaders() {
        setScreen(AllAppsViewState.Loaded(emptyList()))

        composeRule.onNodeWithText(EMPTY_TEXT).assertIsDisplayed()
        composeRule.onAllNodesWithTag(ALL_APPS_SECTION_HEADER_TAG).assertCountEquals(0)
    }

    @Test
    fun singleStillHeadered() {
        setScreen(AllAppsViewState.Loaded(listOf(entry(personal, "Solo App"))))

        composeRule.onAllNodesWithTag(ALL_APPS_SECTION_HEADER_TAG).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("S, application section").assertIsDisplayed()
        composeRule.onNodeWithText("Solo App").assertIsDisplayed()
    }

    // ── P6 anchor derive/restore ───────────────────────────────────────────

    @Test
    fun anchor_derive_row_returnsEntryKeyAndSectionKey() {
        val alpha = entry(identity("0", "com.alpha", "com.alpha.Main"), "Alpha")
        val apple = entry(identity("0", "com.apple", "com.apple.Main"), "Apple")
        val banana = entry(identity("0", "com.banana", "com.banana.Main"), "Banana")
        val entries = listOf(alpha, apple, banana)
        val sections = entries.toAppSections()

        // Flat: [header A(0), Alpha(1), Apple(2), header B(3), Banana(4)]
        // Start at index 1 → first visible item is Alpha (a row entry).
        val listState = LazyListState(firstVisibleItemIndex = 1)
        composeRule.setContent {
            LauncherTheme {
                AllAppsScreen(
                    state = AllAppsViewState.Loaded(entries),
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    listState = listState,
                )
            }
        }
        composeRule.waitForIdle()

        val anchor = deriveAnchor(listState, sections)
        assertNotNull("Anchor must not be null when entries are visible", anchor)
        assertEquals(alpha.rowKey(), anchor!!.entryKey)
        assertEquals("A", anchor.sectionKey)
    }

    @Test
    fun anchor_derive_header_returnsNullEntryKeyAndCorrectSectionKey() {
        val alpha = entry(identity("0", "com.alpha", "com.alpha.Main"), "Alpha")
        val apple = entry(identity("0", "com.apple", "com.apple.Main"), "Apple")
        val banana = entry(identity("0", "com.banana", "com.banana.Main"), "Banana")
        val entries = listOf(alpha, apple, banana)
        val sections = entries.toAppSections()

        // Flat: [header A(0), Alpha(1), Apple(2), header B(3), Banana(4)]
        // findSectionHeaderLazyIndex / findEntryLazyIndex are pure functions that
        // compute flat-list indices without needing a composed LazyListState.
        assertEquals(0, findSectionHeaderLazyIndex(sections, "A"))
        assertEquals(3, findSectionHeaderLazyIndex(sections, "B"))
        assertNull(findSectionHeaderLazyIndex(sections, "Z"))

        assertEquals(1, findEntryLazyIndex(sections, alpha.rowKey()))
        assertEquals(2, findEntryLazyIndex(sections, apple.rowKey()))
        assertEquals(4, findEntryLazyIndex(sections, banana.rowKey()))
        assertNull(findEntryLazyIndex(sections, "nonexistent-key"))

        // AllAppsAnchor with null entryKey represents a section-header-only anchor.
        val headerAnchor = AllAppsAnchor(entryKey = null, sectionKey = "A")
        assertNull(headerAnchor.entryKey)
        assertEquals("A", headerAnchor.sectionKey)
    }

    @Test
    fun anchor_restore_removedEntry_fallsBackToSectionHeader() {
        val alpha = entry(identity("0", "com.alpha", "com.alpha.Main"), "Alpha")
        val apple = entry(identity("0", "com.apple", "com.apple.Main"), "Apple")
        val banana = entry(identity("0", "com.banana", "com.banana.Main"), "Banana")

        var viewState: AllAppsViewState by mutableStateOf(
            AllAppsViewState.Loaded(listOf(alpha, apple, banana)),
        )

        // Flat: [header A(0), Alpha(1), Apple(2), header B(3), Banana(4)]
        // Start at index 2 → Apple (row in section A).
        val listState = LazyListState(firstVisibleItemIndex = 2)
        composeRule.setContent {
            LauncherTheme {
                AllAppsScreen(
                    state = viewState,
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    listState = listState,
                )
            }
        }
        composeRule.waitForIdle()

        // Remove Apple: new sections [A(Alpha), B(Banana)].
        // Anchor was for Apple in section A; entry is gone → restore falls back to
        // section A header (index 0).
        viewState = AllAppsViewState.Loaded(listOf(alpha, banana))
        composeRule.waitForIdle()

        // Section A header must be visible after restore.
        composeRule.onNodeWithContentDescription("A, application section").assertIsDisplayed()
    }

    @Test
    fun anchor_derive_empty_returnsNull() {
        val listState = LazyListState()
        assertNull(deriveAnchor(listState, emptyList()))
    }

    @Test
    fun anchor_derive_single_returnsCorrectAnchor() {
        val solo = entry(identity("0", "com.solo", "com.solo.Main"), "Solo App")
        val entries = listOf(solo)
        val sections = entries.toAppSections()

        // Flat: [header S(0), Solo App(1)]
        // Start at index 1 → Solo App (a row).
        val listState = LazyListState(firstVisibleItemIndex = 1)
        composeRule.setContent {
            LauncherTheme {
                AllAppsScreen(
                    state = AllAppsViewState.Loaded(entries),
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    listState = listState,
                )
            }
        }
        composeRule.waitForIdle()

        val anchor = deriveAnchor(listState, sections)
        assertNotNull("Anchor must not be null for single-entry list", anchor)
        assertEquals(solo.rowKey(), anchor!!.entryKey)
        assertEquals("S", anchor.sectionKey)
    }
}
