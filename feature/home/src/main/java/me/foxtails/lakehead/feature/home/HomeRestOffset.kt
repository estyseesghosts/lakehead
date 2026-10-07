package me.foxtails.lakehead.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * The vertical rest offset (centring of short content) that [HomeGridMetrics]
 * renders and inverse-maps with. Presentation only: row 0 stays the logical top.
 *
 * Outside editing the offset glides to [targetPx]. The moment editing begins it is
 * frozen at the value in effect, and that same value is returned for the whole edit
 * session, so rendering and pointer mapping share one coordinate frame no matter
 * how the target moves meanwhile (a commit can change the content height). When
 * editing ends the offset resumes from the frozen value toward the current target.
 *
 * [snapToTarget] (invalid viewport, zero height) skips the glide.
 *
 * The result is a whole pixel count so rect and inverse math agree exactly.
 */
@Composable
internal fun rememberRestOffsetYPx(
    targetPx: Float,
    snapToTarget: Boolean,
    isEditing: Boolean,
): Float {
    val glide = remember { Animatable(targetPx) }
    var isFirstFrame by remember { mutableStateOf(true) }
    LaunchedEffect(targetPx, isEditing) {
        if (isEditing) return@LaunchedEffect
        if (isFirstFrame || snapToTarget) {
            glide.snapTo(targetPx)
            isFirstFrame = false
        } else {
            glide.animateTo(targetPx)
        }
    }
    val live = glide.value
    // Captured once per edit session: remember is keyed on the session boundary.
    val frozen = remember(isEditing) { if (isEditing) live else null }
    return (frozen ?: live).roundToInt().toFloat()
}
