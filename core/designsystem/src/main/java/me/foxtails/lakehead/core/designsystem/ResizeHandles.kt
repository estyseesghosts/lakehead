package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom-right corner resize affordance for a selected edit tile.
 *
 * A2-S1 tap-to-arm resize: the arrow tap arms/disarms resize for the selected
 * tile ([armed]), and only an armed arrow-origin drag resizes — an unarmed
 * arrow-drag is inert and a body drag moves with the snapshotted span. The
 * armed state renders distinctly (filled `scheme.primary` circle vs the shared
 * 32dp outline circle) and is announced via state description, with the tap
 * action named `"Arm resize for {label}"` / `"Disarm resize for {label}"`.
 *
 * Touch contract: this composable installs a tap-only `clickable` (when
 * [onToggleArmed] is non-null) and never a drag/slop detector — drag routing
 * stays owned by the parent grid's unified handler, which hit-tests the
 * pointer against the handle's inward 48dp corner rect once at gesture start
 * and routes to RESIZE or MOVE. A second drag detector here would lose the
 * touch-slop race and leave the handle unreachable, so one must never be
 * added. Past slop the tap cancels and the parent owns the gesture. With a
 * null [onToggleArmed] the handle is visual-only (no tap handling).
 * The 48dp touch target satisfies minimum accessibility guidelines; the
 * visual indicator itself is the same 32dp circle as [RemoveButton].
 */
@Composable
fun ResizeHandle(
    label: String,
    armed: Boolean,
    onToggleArmed: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    require(label.isNotBlank()) { "label must be non-blank" }
    val scheme = MaterialTheme.colorScheme
    val action = if (armed) "Disarm resize for $label" else "Arm resize for $label"
    val state = if (armed) "Resize armed" else "Resize not armed"
    var target = modifier
        .size(CORNER_CONTROL_TOUCH_SIZE.dp)
        .testTag(RESIZE_HANDLE_TAG)
        .semantics(mergeDescendants = true) {
            this.contentDescription = action
            this.stateDescription = state
        }
    if (onToggleArmed != null) {
        target = target.clickable(
            onClick = onToggleArmed,
            role = Role.Button,
        )
    }
    Box(
        modifier = target,
        contentAlignment = Alignment.Center,
    ) {
        val visual = Modifier.size(CORNER_CONTROL_VISUAL_SIZE.dp)
        // The glyph is decorative in both branches; the outer node carries the
        // single actionable announcement above.
        if (armed) {
            Box(
                modifier = visual.background(scheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    modifier = Modifier.clearAndSetSemantics {},
                    text = "↘",
                    style = TextStyle(color = scheme.onPrimary, fontSize = 18.sp),
                )
            }
        } else {
            Box(
                modifier = visual.border(2.dp, scheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    modifier = Modifier.clearAndSetSemantics {},
                    text = "↘",
                    style = TextStyle(color = scheme.onSurface, fontSize = 18.sp),
                )
            }
        }
    }
}

/** Test tag on the resize handle affordance. */
const val RESIZE_HANDLE_TAG: String = "resize-handle"
