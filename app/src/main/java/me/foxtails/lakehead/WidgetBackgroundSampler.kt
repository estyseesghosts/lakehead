package me.foxtails.lakehead

/**
 * Picks the colour a widget tile should fill its own background with, from a downscaled render of
 * the hosted widget view.
 *
 * A widget often draws a solid surface smaller than the tile it sits in, leaving a transparent
 * margin. When the rendered content is such a surface, the tile takes the surface's edge colour
 * so the widget looks like it fills the tile. Pure so it is unit-testable; no Android types.
 *
 * Returns an opaque ARGB colour, or null when filling would be wrong: nothing opaque is drawn,
 * the opaque area is small or sparse (text or icons on a transparent widget), or the surface edge
 * has no clearly dominant colour (a photo or gradient).
 */
internal object WidgetBackgroundSampler {
    private const val OPAQUE_ALPHA = 200
    private const val RING_PX = 2
    private const val MIN_BOUNDS_FRACTION = 0.15f
    private const val MIN_FILL_FRACTION = 0.6f
    private const val MIN_DOMINANT_SHARE = 0.6f

    /** [pixels] is row-major non-premultiplied ARGB, [width] x [height]. */
    fun sample(pixels: IntArray, width: Int, height: Int): Int? {
        require(width > 0 && height > 0 && pixels.size >= width * height) { "bad pixel buffer" }
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isOpaque(pixels[y * width + x])) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null
        val boundsW = right - left + 1
        val boundsH = bottom - top + 1
        if (boundsW * boundsH < MIN_BOUNDS_FRACTION * width * height) return null

        var opaqueInBounds = 0
        val votes = HashMap<Int, Bucket>()
        var ringCount = 0
        for (y in top..bottom) {
            for (x in left..right) {
                val argb = pixels[y * width + x]
                if (!isOpaque(argb)) continue
                opaqueInBounds++
                val inRing = x < left + RING_PX || x > right - RING_PX || y < top + RING_PX || y > bottom - RING_PX
                if (!inRing) continue
                ringCount++
                votes.getOrPut(quantize(argb)) { Bucket() }.add(argb)
            }
        }
        if (opaqueInBounds < MIN_FILL_FRACTION * boundsW * boundsH) return null
        if (ringCount == 0) return null
        val dominant = votes.values.maxByOrNull { it.count } ?: return null
        if (dominant.count < MIN_DOMINANT_SHARE * ringCount) return null
        return dominant.average()
    }

    private fun isOpaque(argb: Int): Boolean = (argb ushr 24) >= OPAQUE_ALPHA

    /** 4 bits per channel: close shades vote together. */
    private fun quantize(argb: Int): Int =
        (((argb shr 16) and 0xFF) shr 4 shl 8) or (((argb shr 8) and 0xFF) shr 4 shl 4) or ((argb and 0xFF) shr 4)

    private class Bucket {
        var count = 0
        private var r = 0L
        private var g = 0L
        private var b = 0L

        fun add(argb: Int) {
            count++
            r += (argb shr 16) and 0xFF
            g += (argb shr 8) and 0xFF
            b += argb and 0xFF
        }

        fun average(): Int =
            (0xFF shl 24) or ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
    }
}
