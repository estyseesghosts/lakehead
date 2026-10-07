package me.foxtails.lakehead.platform.launcherapps

import kotlin.math.abs

/**
 * Derives a tintable alpha mask from a colour icon raster. Icons are drawn as a single tinted
 * mask, so an opaque coloured backdrop would become a solid tile. When the icon has a backdrop
 * (opaque border), the mask is how far each pixel departs from that backdrop colour; otherwise
 * the icon's own transparency already is the shape and is used as is.
 *
 * Pure array logic with no Android types, so it is unit-testable on the JVM.
 */
internal object ColourMask {
    private const val OPAQUE_ALPHA = 200
    private const val BACKDROP_BORDER_SHARE = 0.5
    private const val DIFFERENCE_FLOOR = 0.12f
    private const val DIFFERENCE_FULL = 0.35f
    private const val MIN_COVERAGE = 0.015

    /** Returns one alpha value (0..255) per pixel of [argb], which is [width] x [height]. */
    fun derive(argb: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && argb.size == width * height) { "size mismatch" }
        val ownAlpha = IntArray(argb.size) { argb[it] ushr 24 }
        val backdrop = backdropColour(argb, width, height) ?: return ownAlpha

        val mask = IntArray(argb.size) { index ->
            val pixel = argb[index]
            val difference = maxOf(
                abs(((pixel shr 16) and 0xFF) - ((backdrop shr 16) and 0xFF)),
                abs(((pixel shr 8) and 0xFF) - ((backdrop shr 8) and 0xFF)),
                abs((pixel and 0xFF) - (backdrop and 0xFF)),
            ) / 255f
            val strength = ((difference - DIFFERENCE_FLOOR) / (DIFFERENCE_FULL - DIFFERENCE_FLOOR))
                .coerceIn(0f, 1f)
            (ownAlpha[index] * strength).toInt()
        }
        // A one-colour icon on its backdrop has nothing to separate; keep the opaque shape.
        val visible = mask.count { it > 0 }.toDouble() / mask.size
        return if (visible < MIN_COVERAGE) ownAlpha else mask
    }

    /** The mean RGB of the opaque border pixels, or null when the border is mostly transparent. */
    private fun backdropColour(argb: IntArray, width: Int, height: Int): Int? {
        var count = 0
        var opaque = 0
        var r = 0L
        var g = 0L
        var b = 0L
        fun sample(x: Int, y: Int) {
            val pixel = argb[y * width + x]
            count++
            if ((pixel ushr 24) >= OPAQUE_ALPHA) {
                opaque++
                r += (pixel shr 16) and 0xFF
                g += (pixel shr 8) and 0xFF
                b += pixel and 0xFF
            }
        }
        for (x in 0 until width) {
            sample(x, 0)
            if (height > 1) sample(x, height - 1)
        }
        for (y in 1 until height - 1) {
            sample(0, y)
            if (width > 1) sample(width - 1, y)
        }
        if (opaque < count * BACKDROP_BORDER_SHARE) return null
        return (0xFF shl 24) or ((r / opaque).toInt() shl 16) or ((g / opaque).toInt() shl 8) or
            (b / opaque).toInt()
    }
}
