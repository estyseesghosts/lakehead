package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Floating add affordance shown while editing Home.
 *
 * Custom Foundation/graphics/text only, alongside [RemoveButton]: a filled circle in
 * the scheme's primary colour with a centred "+". The callers position it; this
 * composable performs no placement math and owns no state.
 */
@Composable
fun AddButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(description.isNotBlank()) { "description must be non-blank" }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(ADD_BUTTON_SIZE.dp)
            .clip(CircleShape)
            .background(scheme.primary)
            .testTag(ADD_BUTTON_TAG)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "+",
            style = TextStyle(color = scheme.onPrimary, fontSize = 28.sp, textAlign = TextAlign.Center),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Test tag on the add button. */
const val ADD_BUTTON_TAG: String = "add-button"

/** Edge of the add button, in dp. */
const val ADD_BUTTON_SIZE: Int = 56
