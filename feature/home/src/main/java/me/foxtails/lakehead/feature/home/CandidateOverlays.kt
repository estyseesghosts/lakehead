package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
import me.foxtails.lakehead.core.designsystem.RESIZE_CANDIDATE_OVERLAY_TAG
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan

/** Test tag on the move-candidate validity outline (testing hook only; no behavior change). */
internal const val MOVE_CANDIDATE_OVERLAY_TAG: String = "move-candidate-overlay"

/**
 * Outline at the held last-valid cell: colliding or out-of-bounds drags keep
 * the frame at the last confirmed position. Frame pixels come from [metrics].
 */
@Composable
internal fun MoveCandidateOverlay(
    move: DragState.Move,
    metrics: HomeGridMetrics,
    modifier: Modifier = Modifier,
) {
    CandidateOutline(
        frame = metrics.pixelFrame(move.previewPosition, move.span),
        isValid = move.validity == CandidateValidity.VALID,
        tag = MOVE_CANDIDATE_OVERLAY_TAG,
        modifier = modifier,
    )
}

/** Outline at the held last-valid span; the origin cell never moves during a resize. */
@Composable
internal fun ResizeCandidateOverlay(
    originPosition: LogicalPosition,
    candidateSpan: LogicalSpan,
    metrics: HomeGridMetrics,
    isValid: Boolean,
    modifier: Modifier = Modifier,
) {
    CandidateOutline(
        frame = metrics.pixelFrame(originPosition, candidateSpan),
        isValid = isValid,
        tag = RESIZE_CANDIDATE_OVERLAY_TAG,
        modifier = modifier,
    )
}

@Composable
private fun CandidateOutline(
    frame: SignedPixelFrame,
    isValid: Boolean,
    tag: String,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (isValid) scheme.primary else scheme.error
    Box(
        modifier = modifier
            .offset { IntOffset(frame.left, frame.top) }
            .size(with(density) { frame.widthPx.toDp() }, with(density) { frame.heightPx.toDp() })
            .border(2.dp, borderColor, AppTileDefaults.TileShape)
            .testTag(tag),
    )
}
