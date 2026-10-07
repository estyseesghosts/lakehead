package me.foxtails.lakehead.feature.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Render-only glide of one tile from where it was last seen to where its logical
 * cell now puts it. [retarget] is given how far the tile's old visual spot is
 * from its new layout spot; [offset] then shrinks from that distance to zero as
 * [progress] runs 0 → 1. Retargeting mid-glide continues from the current
 * visual spot, so a second change never snaps.
 *
 * All fields are snapshot state read from a `graphicsLayer` block, so a glide
 * invalidates only the tile's layer. Nothing here is persisted or logical.
 */
@Stable
internal class PlacementGlide {
    private var start by mutableStateOf(Offset.Zero)

    /** Animation progress, 0 at the old spot and 1 at the new one. */
    var progress by mutableFloatStateOf(1f)

    /** Bumped on every [retarget] so the owner restarts its animation. */
    var generation by mutableIntStateOf(0)
        private set

    /** Translation to add to the tile's layout position right now. */
    val offset: Offset get() = start * (1f - progress)

    /** Starts a glide that begins [fromNewSpot] away from the new layout spot. */
    fun retarget(fromNewSpot: Offset) {
        start = offset + fromNewSpot
        progress = 0f
        generation++
    }
}
