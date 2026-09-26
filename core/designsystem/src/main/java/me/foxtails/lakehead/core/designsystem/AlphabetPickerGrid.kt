package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Columns in [AlphabetPickerGrid]: 4 across, so 26 letters fill 6½ rows. */
const val COLUMNS: Int = 4

/** Rows in [AlphabetPickerGrid]: 7 rows of 4 yield 28 slots for 26 letters + 2 spares. */
const val ROWS: Int = 7

/** Test tag on the [AlphabetPickerGrid] root column. */
const val GRID_TAG: String = "alphabet-picker-grid"

/** Test-tag prefix for picker cells; the cell index (0 until 28) is appended. */
const val CELL_PREFIX: String = "alphabet-picker-cell-"

/**
 * Fixed 28-slot picker model: `'A'`–`'Z'` in order followed by two `null` spares.
 *
 * Pure and stable: the same list on every call, so composition order never shifts.
 * Spares pad the final row ([COLUMNS] × [ROWS] = 28) and render as empty boxes.
 */
fun alphabetPickerCells(): List<Char?> =
    ('A'..'Z').toList() + listOf(null, null)

/**
 * Accessible name for a picker cell: `"Jump to X"`.
 *
 * Shared by the implementation and tests so the announcement contract has one owner.
 */
fun jumpContentDescription(letter: Char): String = "Jump to $letter"

/**
 * Static A–Z jump picker: 7 rows × 4 columns, all 28 slots composed eagerly, no scroll.
 *
 * Custom Compose Foundation/layout/text only: no Material product components, no lazy
 * containers, no app inventory, persistence, grouping or navigation. Callers supply the
 * set of jumpable letters ([enabledLetters]) and the tap callback ([onLetterTap]); this
 * grid never derives enablement and performs no scrolling or section jumps itself.
 *
 * Layout: a [Column] of [ROWS] [Row]s with [COLUMNS] cells each. Rows share the column
 * weight (`1f`) and cells share the row weight (`1f`), so every defined-constraint grid
 * fills its bounds evenly. Every cell keeps a 48.dp minimum touch target.
 *
 * Accessibility per letter cell (merged descendants, [Role.Button]):
 *
 * - Enabled (in [enabledLetters]): [contentDescription][jumpContentDescription] plus an
 *   `OnClick` action invoking `onLetterTap(letter)`.
 * - Disabled: same description and role but `clickable(enabled = false)`, which strips
 *   the `OnClick` action and marks the node `disabled()`, so assistive tech announces
 *   the target without offering a dead action.
 *
 * `null` spares are empty, non-clickable boxes with no description, role or actions.
 *
 * @param enabledLetters letters that expose an `OnClick` jump action; others render disabled.
 * @param onLetterTap invoked with the tapped letter; never invoked for disabled or spare cells.
 */
@Composable
fun AlphabetPickerGrid(
    enabledLetters: Set<Char>,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = alphabetPickerCells()
    Column(modifier = modifier.testTag(GRID_TAG)) {
        for (row in 0 until ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                for (column in 0 until COLUMNS) {
                    val index = row * COLUMNS + column
                    val letter = cells[index]
                    AlphabetPickerCell(
                        letter = letter,
                        index = index,
                        isEnabled = letter != null && enabledLetters.contains(letter),
                        onLetterTap = onLetterTap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * One picker slot: a letter cell or an empty spare.
 *
 * Letter cells center a [BasicText] glyph in a >= 48.dp box whose click handling follows
 * [isEnabled] (see [AlphabetPickerGrid]); spares are inert boxes with only a test tag.
 */
@Composable
private fun AlphabetPickerCell(
    letter: Char?,
    index: Int,
    isEnabled: Boolean,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letter == null) {
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag(CELL_PREFIX + index),
        )
        return
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(CELL_PREFIX + index)
            .semantics(mergeDescendants = true) {
                this.contentDescription = jumpContentDescription(letter)
            }
            .clickable(
                enabled = isEnabled,
                onClick = { onLetterTap(letter) },
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = letter.toString(),
            style = TextStyle(
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
            ),
        )
    }
}
