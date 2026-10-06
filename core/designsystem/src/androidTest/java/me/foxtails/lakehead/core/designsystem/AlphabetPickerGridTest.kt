package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Composition tests for the P4 [AlphabetPickerGrid] primitive: fixed 28-slot model,
 * 4 × 7 structure, jump descriptions, enabled/disabled click contracts, spare
 * inertness and 48.dp touch minima.
 *
 * The grid is presentation-only: tests supply every enabled set and callback, so
 * nothing here exercises app inventory, grouping, scrolling or navigation.
 *
 * Note: this module pins ui-test 1.7.0, which exposes `assertExists` /
 * `assertDoesNotExist` as members rather than top-level imports. Role and action
 * behavior is verified through `fetchSemanticsNode` configuration, matching
 * [AppTileTest].
 */
@RunWith(AndroidJUnit4::class)
class AlphabetPickerGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setGrid(
        enabledLetters: Set<Char> = ('A'..'Z').toSet(),
        onLetterTap: (Char) -> Unit = {},
    ) {
        composeRule.setContent {
            Box(Modifier.size(400.dp)) {
                AlphabetPickerGrid(
                    enabledLetters = enabledLetters,
                    onLetterTap = onLetterTap,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    // ── pure model ─────────────────────────────────────────────────────────

    @Test
    fun gridConstants_defineFourBySeven() {
        assertEquals(4, COLUMNS)
        assertEquals(7, ROWS)
        assertEquals("grid must hold exactly 28 slots", 28, COLUMNS * ROWS)
    }

    @Test
    fun alphabetPickerCells_holdsAThroughZThenTwoSpares() {
        val cells = alphabetPickerCells()

        assertEquals(28, cells.size)
        assertEquals(('A'..'Z').toList(), cells.take(26))
        assertNull(cells[26])
        assertNull(cells[27])
    }

    @Test
    fun alphabetPickerCells_isStableAcrossCalls() {
        assertEquals(alphabetPickerCells(), alphabetPickerCells())
    }

    @Test
    fun jumpContentDescription_formatsJumpTo() {
        assertEquals("Jump to A", jumpContentDescription('A'))
        assertEquals("Jump to M", jumpContentDescription('M'))
        assertEquals("Jump to Z", jumpContentDescription('Z'))
    }

    // ── structure: 28 composed, 4 × 7 ──────────────────────────────────────

    @Test
    fun allTwentyEightCells_composedWithoutScrolling() {
        setGrid()

        composeRule.onNodeWithTag(GRID_TAG).assertExists()
        for (index in 0 until 28) {
            composeRule.onNodeWithTag(CELL_PREFIX + index).assertExists()
        }
    }

    @Test
    fun lettersAndSpares_aThroughZDisplayed_lastTwoEmpty() {
        setGrid()

        for (letter in 'A'..'Z') {
            composeRule.onNodeWithText(letter.toString()).assertExists()
        }
        // Spares carry no letter text.
        composeRule.onNodeWithText("null").assertDoesNotExist()
    }

    @Test
    fun cells_arrangedFourAcrossSevenDown() {
        setGrid()

        val left0 = cellLeft(CELL_PREFIX + 0)
        val left1 = cellLeft(CELL_PREFIX + 1)
        val left3 = cellLeft(CELL_PREFIX + 3)
        val top0 = cellTop(CELL_PREFIX + 0)
        val top4 = cellTop(CELL_PREFIX + 4)
        val top24 = cellTop(CELL_PREFIX + 24)
        assertTrue("columns must advance right (cell0=$left0 cell1=$left1)", left1 > left0)
        assertTrue("fourth cell must be further right (cell3=$left3)", left3 > left1)
        assertTrue("next row must start below (row1=$top4 row0=$top0)", top4 > top0)
        assertTrue("last row must be below the first (row6=$top24)", top24 > top4)

        // Each row of four shares one horizontal band: same top within 1px.
        for (row in 0 until ROWS) {
            val tops = (0 until COLUMNS).map { column -> cellTop(CELL_PREFIX + (row * COLUMNS + column)) }
            assertTrue(
                "row $row cells must share a band (tops=$tops)",
                tops.max() - tops.min() <= 1f,
            )
        }
    }

    // ── semantics: jump descriptions, roles, enabled/disabled actions ──────

    @Test
    fun letterCells_exposeJumpDescriptionsAndButtonRole() {
        setGrid()

        for (letter in 'A'..'Z') {
            val node = composeRule
                .onNodeWithTag(CELL_PREFIX + alphabetPickerCells().indexOf(letter))
                .fetchSemanticsNode()
            composeRule.runOnIdle {
                val descriptions = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                assertTrue(
                    "'$letter' must announce '${jumpContentDescription(letter)}' (was $descriptions)",
                    descriptions.contains(jumpContentDescription(letter)),
                )
                assertEquals(Role.Button, node.config[SemanticsProperties.Role])
                assertTrue(
                    "enabled '$letter' must expose OnClick",
                    node.config.contains(SemanticsActions.OnClick),
                )
            }
        }
    }

    @Test
    fun disabledLetters_keepDescriptionAndRole_butExposeNoOnClick() {
        setGrid(enabledLetters = setOf('A', 'B'))

        val disabled = composeRule
            .onNodeWithTag(CELL_PREFIX + alphabetPickerCells().indexOf('C'))
            .fetchSemanticsNode()
        composeRule.runOnIdle {
            val descriptions = disabled.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            assertTrue(
                "disabled 'C' must keep its jump description (was $descriptions)",
                descriptions.contains("Jump to C"),
            )
            assertEquals(Role.Button, disabled.config[SemanticsProperties.Role])
            assertFalse(
                "disabled 'C' must expose no OnClick action",
                disabled.config.contains(SemanticsActions.OnClick),
            )
            assertTrue(
                "disabled 'C' must be marked disabled()",
                disabled.config.contains(SemanticsProperties.Disabled),
            )
        }

        val enabled = composeRule
            .onNodeWithTag(CELL_PREFIX + alphabetPickerCells().indexOf('A'))
            .fetchSemanticsNode()
        composeRule.runOnIdle {
            assertTrue(
                "enabled 'A' must expose OnClick",
                enabled.config.contains(SemanticsActions.OnClick),
            )
            assertFalse(
                "enabled 'A' must not be marked disabled()",
                enabled.config.contains(SemanticsProperties.Disabled),
            )
        }
    }

    @Test
    fun disabledTap_neverInvokesCallback_enabledTapRoutesLetter() {
        val tapped = mutableListOf<Char>()
        setGrid(enabledLetters = setOf('A'), onLetterTap = { tapped.add(it) })

        composeRule
            .onNodeWithTag(CELL_PREFIX + alphabetPickerCells().indexOf('A'))
            .assertExists()
        // Disabled cells expose no click action, so there is nothing to invoke.
        composeRule
            .onNodeWithTag(CELL_PREFIX + alphabetPickerCells().indexOf('B'))
            .assertExists()
            .assertHasNoClickAction()
        composeRule.runOnIdle { assertTrue("disabled tap must never invoke", tapped.isEmpty()) }

        composeRule
            .onNodeWithTag(CELL_PREFIX + alphabetPickerCells().indexOf('A'))
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf('A'), tapped) }
    }

    // ── spares ─────────────────────────────────────────────────────────────

    @Test
    fun spares_areEmptyNonClickableWithoutSemantics() {
        setGrid()

        listOf(26, 27).forEach { index ->
            val node = composeRule.onNodeWithTag(CELL_PREFIX + index).fetchSemanticsNode()
            composeRule.runOnIdle {
                assertFalse(
                    "spare $index must expose no click action",
                    node.config.contains(SemanticsActions.OnClick),
                )
                assertFalse(
                    "spare $index must expose no long-click action",
                    node.config.contains(SemanticsActions.OnLongClick),
                )
                assertFalse(
                    "spare $index must not be a button",
                    node.config.contains(SemanticsProperties.Role),
                )
                assertTrue(
                    "spare $index must carry no content description",
                    node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.isEmpty(),
                )
            }
        }
    }

    // ── touch targets ──────────────────────────────────────────────────────

    @Test
    fun allCells_meet48dpMinima() {
        setGrid()

        val minTouchPx = with(composeRule.density) { 48.dp.roundToPx() }
        for (index in 0 until 28) {
            val node = composeRule.onNodeWithTag(CELL_PREFIX + index).fetchSemanticsNode()
            composeRule.runOnIdle {
                assertTrue(
                    "cell $index must meet 48dp minima (size=${node.size})",
                    node.size.width >= minTouchPx && node.size.height >= minTouchPx,
                )
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun cellLeft(tag: String): Float =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.x

    private fun cellTop(tag: String): Float =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y
}
