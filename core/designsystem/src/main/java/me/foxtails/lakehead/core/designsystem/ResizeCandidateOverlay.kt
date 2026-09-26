package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.grid.GridPosition
import me.foxtails.lakehead.core.grid.GridSpan

/**
 * Ghost frame preview during a resize drag.
 *
 * Renders a non-interactive outline at the candidate span position. Valid candidates draw
 * a 2dp outline in `scheme.primary`; invalid candidates draw in `scheme.error`.
 *
 * Position reuses [TileBounds.signedBounds] for the signed candidate frame rebased
 * around the caller-provided [rowOrigin] plus the G2 rest [originYPx] offset, so the
 * outline lands on the same Box content coordinates the windowed tiles are placed
 * at. Horizontal placement includes [originX]: the caller-centered remainder
 * split, so column 0 starts at [originX] rather than 0 and left/right slack
 * stays equal within 1px. [originYPx] carries short-content bias centering
 * (e.g. 540px) or zero for tall content; omitting it ghosts short content
 * off-origin.
 */
@Composable
fun ResizeCandidateOverlay(
    originPosition: GridPosition,
    candidateSpan: GridSpan,
    geometry: SquareCellGeometry,
    rowOrigin: Long,
    isValid: Boolean,
    modifier: Modifier = Modifier,
    originX: Int = 0,
    originYPx: Long = 0L,
) {
    val density = LocalDensity.current
    val frame = resizeCandidateFrame(
        originPosition = originPosition,
        candidateSpan = candidateSpan,
        geometry = geometry,
        originX = originX,
        rowOrigin = rowOrigin,
        originYPx = originYPx,
    )
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (isValid) {
        scheme.primary
    } else {
        scheme.error
    }
    val widthDp = with(density) { frame.widthPx.toDp() }
    val heightDp = with(density) { frame.heightPx.toDp() }
    Box(
        modifier = modifier
            .offset { IntOffset(frame.left, frame.top) }
            .size(widthDp, heightDp)
            .border(2.dp, borderColor, AppTileDefaults.TileShape)
            .testTag(RESIZE_CANDIDATE_OVERLAY_TAG),
    )
}

/** Test tag on the resize candidate overlay. */
const val RESIZE_CANDIDATE_OVERLAY_TAG: String = "resize-candidate-overlay"

/**
 * Signed candidate frame for [ResizeCandidateOverlay] in Box content coordinates.
 *
 * Delegates to [TileBounds.signedBounds] with the candidate origin/span rebased
 * around [rowOrigin] plus the G2 rest [originYPx] offset. Pure math,
 * framework-free, so short-content bias centering stays unit-testable and
 * `core:grid` legality stays untouched.
 */
fun resizeCandidateFrame(
    originPosition: GridPosition,
    candidateSpan: GridSpan,
    geometry: SquareCellGeometry,
    originX: Int = 0,
    rowOrigin: Long = 0L,
    originYPx: Long = 0L,
): SignedPixelFrame = TileBounds.signedBounds(
    column = originPosition.column,
    row = originPosition.row.toLong(),
    span = candidateSpan,
    geometry = geometry,
    originX = originX,
    originRow = rowOrigin,
    originYPx = originYPx,
)