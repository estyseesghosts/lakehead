package me.foxtails.lakehead.feature.allapps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * Composition contract for the P4 shell wiring: header tap opens + pager blocks.
 *
 * [AllAppsScreen] itself stays presentation-only (the caller owns [AlphabetPickerState]
 * transitions); this test pins the exact wiring `LauncherShell` replicates so a
 * regression there is caught here under `:feature:allapps:test`:
 *
 * - header tap builds `sections.toAlphabetPickerState(isOpen = true)` from the current
 *   sections and reports the key,
 * - the opened picker satisfies [blocksPager] (the flag the shell feeds to
 *   `HorizontalPager(userScrollEnabled = ...)`),
 * - letter pick reports the key and dismisses (overlay gone, pager unblocked).
 *
 * Grouping and jump execution are untouched: enablement is asserted as the derived
 * section-key set, and the jump itself remains the caller's (shell's) concern.
 */
@RunWith(RobolectricTestRunner::class)
class AlphabetPickerPagerBlockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(label: String, pkg: String) = AllAppsEntry(
        identity = AppIdentity(ProfileId("0"), ComponentIdentity(pkg, "$pkg.Main")),
        label = label,
        alreadyOnHome = false,
    )

    @Test
    fun headerTap_opensPicker_blocksPager_letterPickReportsAndDismisses() {
        val entries = listOf(
            entry("Apple", "com.example.apple"),
            entry("Banana", "com.example.banana"),
        )
        var picker: AlphabetPickerState? by mutableStateOf(null)
        val reported = mutableListOf<String>()
        composeRule.setContent {
            LauncherTheme {
                // Same read-only derivation the shell uses for enablement.
                val sections = remember(entries) { entries.toAppSections() }
                AllAppsScreen(
                    state = AllAppsViewState.Loaded(entries),
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    onHeaderTap = { key ->
                        picker = sections.toAlphabetPickerState(isOpen = true)
                        reported += key
                    },
                    picker = picker,
                    onPickerDismiss = { picker = picker?.copy(isOpen = false) },
                    onPickerLetterPicked = { letter ->
                        reported += letter
                        picker = picker?.copy(isOpen = false)
                    },
                )
            }
        }

        // Closed: bare list, pager unblocked.
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(picker.blocksPager()) }

        // Header tap opens with current-section enablement and reports the key.
        composeRule.onNodeWithContentDescription("B, application section").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("B"), reported)
            assertTrue(
                "open picker must block the pager (shell: userScrollEnabled = false)",
                picker.blocksPager(),
            )
            assertEquals(setOf("A", "B"), picker?.enabledLetters)
        }

        // Letter pick reports the key and dismisses: overlay gone, pager unblocked.
        composeRule.onNodeWithContentDescription("Jump to A").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf("B", "A"), reported)
            assertFalse(picker.blocksPager())
        }
        composeRule.onNodeWithTag(ALL_APPS_LIST_TAG).assertIsDisplayed()
    }

    /**
     * P5: letter pick dismisses the picker AND computes the correct scroll target
     * index for the matching section header. The test captures the computed
     * [headerItemIndex] result (the value [LauncherShell] passes to
     * [LazyListState.scrollToItem]) and verifies it matches the expected layout:
     * A(2 apps) → B(1 app) → C(1 app), so C's header is at item index 5.
     *
     * Actual scroll position is not verified in Robolectric (layout not computed).
     */
    @Test
    fun pickerLetterPicked_dismissesAndScrollsToSectionHeader() {
        val entries = listOf(
            entry("Apple", "com.example.apple"),
            entry("Avocado", "com.example.avocado"),
            entry("Banana", "com.example.banana"),
            entry("Cherry", "com.example.cherry"),
        )
        var picker: AlphabetPickerState? by mutableStateOf(null)
        var capturedTargetIdx: Int? = null
        composeRule.setContent {
            LauncherTheme {
                val sections = remember(entries) { entries.toAppSections() }
                AllAppsScreen(
                    state = AllAppsViewState.Loaded(entries),
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    onHeaderTap = { picker = sections.toAlphabetPickerState(isOpen = true) },
                    picker = picker,
                    onPickerDismiss = { picker = picker?.copy(isOpen = false) },
                    onPickerLetterPicked = { letter ->
                        picker = picker?.copy(isOpen = false)
                        val sectionIdx = sections.sectionIndexForKey(letter)
                        if (sectionIdx != null) {
                            capturedTargetIdx = sections.headerItemIndex(sectionIdx)
                        }
                    },
                )
            }
        }

        // Open picker via B header.
        composeRule.onNodeWithContentDescription("B, application section").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()

        // Pick C → dismiss + compute C's header index (5: A header=0, A×2=1-2, B header=3, B×1=4, C header=5).
        composeRule.onNodeWithContentDescription("Jump to C").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()
        composeRule.runOnIdle {
            assertFalse(picker.blocksPager())
            assertEquals(5, capturedTargetIdx)
        }
    }

    /**
     * P5: when the first section is "#", picking a letter computes the correct
     * header index accounting for the #" apps that precede it.
     * Entries: 1Password(#), 9GAG(#), Apple(A) → sections [#, A].
     * A's header is at index 3 (header#=0, 1Password=1, 9GAG=2, headerA=3).
     * This verifies [sectionIndexForKey] and [headerItemIndex] integration
     * when # is present in the section list.
     */
    @Test
    fun pickerHashSection_letterAfterHash_computesCorrectHeaderIndex() {
        val entries = listOf(
            entry("1Password", "com.onepassword"),
            entry("9GAG", "com.ninegag"),
            entry("Apple", "com.apple"),
        )
        var picker: AlphabetPickerState? by mutableStateOf(null)
        var capturedTargetIdx: Int? = null
        composeRule.setContent {
            LauncherTheme {
                val sections = remember(entries) { entries.toAppSections() }
                AllAppsScreen(
                    state = AllAppsViewState.Loaded(entries),
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    onHeaderTap = { picker = sections.toAlphabetPickerState(isOpen = true) },
                    picker = picker,
                    onPickerDismiss = { picker = picker?.copy(isOpen = false) },
                    onPickerLetterPicked = { letter ->
                        picker = picker?.copy(isOpen = false)
                        val sectionIdx = sections.sectionIndexForKey(letter)
                        if (sectionIdx != null) {
                            capturedTargetIdx = sections.headerItemIndex(sectionIdx)
                        }
                    },
                )
            }
        }

        // Open picker via A header, pick A → target index 3 (after # section's 2 apps).
        composeRule.onNodeWithContentDescription("A, application section").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Jump to A").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()
        composeRule.runOnIdle {
            assertFalse(picker.blocksPager())
            assertEquals(3, capturedTargetIdx)
        }
    }

    /**
     * P5: picking a letter whose section is at the start computes target index 0.
     * [LauncherShell] uses [LazyListState.scrollToItem] (not animate), so the
     * jump is instant regardless of distance — no traversal animation.
     */
    @Test
    fun pickerShortJump_noTraversal_targetIndexComputedCorrectly() {
        val entries = listOf(
            entry("Apple", "com.example.apple"),
            entry("Banana", "com.example.banana"),
        )
        var picker: AlphabetPickerState? by mutableStateOf(null)
        var capturedTargetIdx: Int? = null
        composeRule.setContent {
            LauncherTheme {
                val sections = remember(entries) { entries.toAppSections() }
                AllAppsScreen(
                    state = AllAppsViewState.Loaded(entries),
                    icons = emptyMap(),
                    onLaunchApp = {},
                    onAddToHome = {},
                    onHeaderTap = { picker = sections.toAlphabetPickerState(isOpen = true) },
                    picker = picker,
                    onPickerDismiss = { picker = picker?.copy(isOpen = false) },
                    onPickerLetterPicked = { letter ->
                        picker = picker?.copy(isOpen = false)
                        val sectionIdx = sections.sectionIndexForKey(letter)
                        if (sectionIdx != null) {
                            capturedTargetIdx = sections.headerItemIndex(sectionIdx)
                        }
                    },
                )
            }
        }

        // Open picker via B header, pick A → target index is 0 (first section).
        composeRule.onNodeWithContentDescription("B, application section").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Jump to A").performClick()
        composeRule.onNodeWithTag(ALPHABET_PICKER_OVERLAY_TAG).assertDoesNotExist()
        composeRule.runOnIdle {
            assertFalse(picker.blocksPager())
            assertEquals(0, capturedTargetIdx)
        }
    }
}
