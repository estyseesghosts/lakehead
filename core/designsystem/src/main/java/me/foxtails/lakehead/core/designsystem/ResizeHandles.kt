package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom-right corner resize affordance for an active edit tile.
 *
 * Visual-only: a small circle rendered at the tile's bottom-right
 * corner. The caller positions this absolutely within the tile's bounds.
 * This composable installs no pointer-input detector of its own — the parent
 * grid's unified drag handler hit-tests the pointer against the handle's
 * inward 48dp corner rect (see `isInHandle` in `feature:home`) once at gesture
 * start and routes to RESIZE or MOVE. A second detector here would lose the
 * touch-slop race and leave the handle unreachable, so it must never be added.
 * The 48dp touch target satisfies minimum accessibility guidelines; the
 * visual indicator itself is the same 32dp circle as [RemoveButton].
 */
@Composable
fun ResizeHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(CORNER_CONTROL_TOUCH_SIZE.dp)
            .testTag(RESIZE_HANDLE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        val scheme = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .size(CORNER_CONTROL_VISUAL_SIZE.dp)
                .border(2.dp, scheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                // The glyph is decorative; the parent routes resize and the sibling
                // remove control supplies the actionable corner-control announcement.
                modifier = Modifier.clearAndSetSemantics {},
                text = "↘",
                style = TextStyle(color = scheme.onSurface, fontSize = 18.sp),
            )
        }
    }
}

/** Test tag on the resize handle affordance. */
const val RESIZE_HANDLE_TAG: String = "resize-handle"
