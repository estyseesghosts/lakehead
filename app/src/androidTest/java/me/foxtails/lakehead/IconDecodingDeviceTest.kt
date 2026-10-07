package me.foxtails.lakehead

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Device evidence that decoded icons are alpha-only masks that keep their shape. */
@RunWith(AndroidJUnit4::class)
class IconDecodingDeviceTest {
    @Test
    fun colourPngIsDecodedAsAlpha8WithItsAlphaPreserved() {
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(1, 1, Color.argb(255, 200, 30, 30))
        }
        val png = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val decoded = decodeAlpha8Icon(png.toByteArray())

        assertNotNull(decoded)
        val bitmap = decoded!!.asAndroidBitmap()
        assertEquals(Bitmap.Config.ALPHA_8, bitmap.config)
        assertEquals(4, bitmap.width)
        val alphaAt = { x: Int, y: Int -> Color.alpha(bitmap.copy(Bitmap.Config.ARGB_8888, false).getPixel(x, y)) }
        assertEquals(255, alphaAt(1, 1))
        assertEquals(0, alphaAt(0, 0))
    }

    @Test
    fun undecodableBytesGiveNull() {
        assertNull(decodeAlpha8Icon(byteArrayOf(1, 2, 3)))
    }
}
