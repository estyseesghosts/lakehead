package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Bottom-right corner resize affordance for an active edit tile.
 *
 * Visual-only: a small rounded rectangle rendered at the tile's bottom-right
 * corner. The caller positions this absolutely within the tile's bounds.
 * This composable installs no pointer-input detector of its own — the parent
 * grid's unified drag handler hit-tests the pointer against the handle's
 * inward 48dp corner rect (see `isInHandle` in `feature:home`) once at gesture
 * start and routes to RESIZE or MOVE. A second detector here would lose the
 * touch-slop race and leave the handle unreachable, so it must never be added.
 * The 48dp touch target satisfies minimum accessibility guidelines; the
 * visual indicator itself is 16dp ([RESIZE_HANDLE_VISUAL_SIZE]).
 */
@Composable
fun ResizeHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(RESIZE_HANDLE_VISUAL_SIZE.dp)
            .testTag(RESIZE_HANDLE_TAG),
    )
}

/** Test tag on the resize handle affordance. */
const val RESIZE_HANDLE_TAG: String = "resize-handle"

/** Visual size of the resize handle indicator in dp. */
private const val RESIZE_HANDLE_VISUAL_SIZE = 16