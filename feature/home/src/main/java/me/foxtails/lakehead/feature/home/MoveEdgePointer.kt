package me.foxtails.lakehead.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Single owner of the held viewport-local pointer during a MOVE drag.
 *
 * Written by the drag host ([set]), read by the ticker ([position]),
 * cleared on every exit path ([clear]).
 */
internal class MoveEdgePointer {
    private var x: Float? by mutableStateOf(null)
    private var y: Float? by mutableStateOf(null)

    /** Viewport-local pointer position, or null when no MOVE drag is active. */
    val position: Offset?
        get() {
            val cx = x ?: return null
            val cy = y ?: return null
            return Offset(cx, cy)
        }

    /** Updates the held pointer position from the drag host. */
    fun set(xPx: Float, yPx: Float) {
        x = xPx
        y = yPx
    }

    /** Clears the held pointer position. Idempotent. */
    fun clear() {
        x = null
        y = null
    }
}