package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Delete affordance for the active edit tile.
 *
 * Custom Foundation/graphics/text only: no Material product components, no inventory,
 * persistence or navigation. The outer 48dp [Box] is the touch target
 * (`clickable` with [Role.Button], accessible name `"Remove {label}"`); the inner
 * 32dp circle is visual-only (2dp `scheme.outline` border, centered `"×"` in
 * `scheme.onSurface`, hidden from accessibility so the merged node keeps exactly
 * the outer description). Theme-adaptive via the scheme: never hardcoded
 * Black/White. Callers position this absolutely; this composable performs no
 * placement math.
 */
@Composable
fun RemoveButton(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(label.isNotBlank()) { "label must be non-blank" }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(REMOVE_BUTTON_TOUCH_SIZE.dp)
            .testTag(REMOVE_BUTTON_TAG)
            .semantics(mergeDescendants = true) {
                this.contentDescription = "Remove $label"
            }
            .clickable(
                onClick = onRemove,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(REMOVE_BUTTON_VISUAL_SIZE.dp)
                .border(
                    width = 2.dp,
                    color = scheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "×",
                style = TextStyle(
                    color = scheme.onSurface,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** Test tag on the remove-button touch target. */
const val REMOVE_BUTTON_TAG: String = "remove-button"

/** Touch-target edge of [RemoveButton] in dp. */
private const val REMOVE_BUTTON_TOUCH_SIZE = 48

/** Visual circle diameter of [RemoveButton] in dp (within the 32–40dp range). */
private const val REMOVE_BUTTON_VISUAL_SIZE = 32
