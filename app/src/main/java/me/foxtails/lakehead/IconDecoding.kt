package me.foxtails.lakehead

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Decodes an encoded icon into an alpha-only bitmap (one byte per pixel). Icons are drawn as
 * tinted monochrome masks, so colour channels would only cost memory. Returns null when the PNG
 * cannot be decoded or converted to [Bitmap.Config.ALPHA_8].
 */
internal fun decodeAlpha8Icon(png: ByteArray): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ALPHA_8 }
    val decoded = BitmapFactory.decodeByteArray(png, 0, png.size, options) ?: return null
    if (decoded.config == Bitmap.Config.ALPHA_8) return decoded.asImageBitmap()

    // The codec may ignore the preferred config (e.g. for colour PNGs); extract the alpha.
    val alphaOnly = decoded.copy(Bitmap.Config.ALPHA_8, false)
    decoded.recycle()
    return alphaOnly?.asImageBitmap()
}
