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
 * Position reuses [TileBounds.bounds] for the absolute candidate frame, rebased via the
 * caller-provided [rowOrigin]. Horizontal placement includes [originX]: the caller-centered
 * remainder split, so column 0 starts at [originX] rather than 0 and left/right slack
 * stays equal within 1px.
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
) {
    val density = LocalDensity.current
    val absolute = TileBounds.bounds(
        position = originPosition,
        span = candidateSpan,
        geometry = geometry,
        originX = originX,
    )
    val rowStridePx = geometry.cellSizePx.toLong() + geometry.rowGapPx.toLong()
    val topPx = rebasedOverlayTopPx(
        absoluteTopPx = absolute.top,
        rowOrigin = rowOrigin,
        rowStridePx = rowStridePx,
    )
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (isValid) {
        scheme.primary
    } else {
        scheme.error
    }
    val widthDp = with(density) { absolute.width.toDp() }
    val heightDp = with(density) { absolute.height.toDp() }
    Box(
        modifier = modifier
            .offset { IntOffset(absolute.left, topPx) }
            .size(widthDp, heightDp)
            .border(2.dp, borderColor, AppTileDefaults.TileShape)
            .testTag(RESIZE_CANDIDATE_OVERLAY_TAG),
    )
}

/** Test tag on the resize candidate overlay. */
const val RESIZE_CANDIDATE_OVERLAY_TAG: String = "resize-candidate-overlay"

/**
 * Rebases an absolute [TileBounds] top edge into windowed-container content coordinates.
 * Same logic as the internal rebasedOverlayTop in HomeCanvasGrid; exposed here for overlay use.
 */
private fun rebasedOverlayTopPx(
    absoluteTopPx: Int,
    rowOrigin: Long,
    rowStridePx: Long,
): Int {
    return try {
        val rebased = Math.subtractExact(
            absoluteTopPx.toLong(),
            Math.multiplyExact(rowOrigin, rowStridePx),
        )
        rebased.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    } catch (_: ArithmeticException) {
        Int.MIN_VALUE
    }
}