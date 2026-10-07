package me.foxtails.lakehead.platform.launcherapps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

/**
 * Decides whether an adaptive icon's monochrome layer is worth using as the symbol. Apps
 * sometimes declare a monochrome layer that is blank, a speck, or a solid fill; tinting such a
 * layer yields an invisible or featureless tile, so the resolver falls back to the foreground.
 */
internal object MonochromeUsability {
    const val SAMPLE_SIZE_PX = 48
    private const val MIN_ALPHA = 16
    private const val MIN_COVERAGE = 0.015
    private const val MAX_COVERAGE = 0.95

    /** Rasterizes [layer] at a small fixed size and judges its alpha coverage. */
    fun isUsable(layer: Drawable): Boolean {
        val bitmap = Bitmap.createBitmap(SAMPLE_SIZE_PX, SAMPLE_SIZE_PX, Bitmap.Config.ARGB_8888)
        return try {
            val oldBounds = layer.copyBounds()
            layer.setBounds(0, 0, SAMPLE_SIZE_PX, SAMPLE_SIZE_PX)
            try {
                layer.draw(Canvas(bitmap))
            } finally {
                layer.bounds = oldBounds
            }
            val pixels = IntArray(SAMPLE_SIZE_PX * SAMPLE_SIZE_PX)
            bitmap.getPixels(pixels, 0, SAMPLE_SIZE_PX, 0, 0, SAMPLE_SIZE_PX, SAMPLE_SIZE_PX)
            isUsableCoverage(IntArray(pixels.size) { pixels[it] ushr 24 })
        } catch (_: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    /** True when the share of visible pixels in [alphas] is neither near-empty nor a solid fill. */
    fun isUsableCoverage(alphas: IntArray): Boolean {
        if (alphas.isEmpty()) return false
        val coverage = alphas.count { it >= MIN_ALPHA }.toDouble() / alphas.size
        return coverage in MIN_COVERAGE..MAX_COVERAGE
    }
}
