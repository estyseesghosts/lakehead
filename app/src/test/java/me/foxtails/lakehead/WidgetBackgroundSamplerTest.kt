package me.foxtails.lakehead

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetBackgroundSamplerTest {
    private val clear = 0x00000000
    private val grey = 0xFF3A3A3C.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun canvas(w: Int, h: Int, fill: Int = clear) = IntArray(w * h) { fill }

    private fun IntArray.rect(w: Int, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        for (y in top..bottom) for (x in left..right) this[y * w + x] = color
    }

    @Test fun aSolidSurfaceSmallerThanTheViewYieldsItsColour() {
        val px = canvas(100, 100).also { it.rect(100, 20, 30, 79, 69, grey) }
        assertEquals(grey, WidgetBackgroundSampler.sample(px, 100, 100))
    }

    @Test fun textContentOnAnOtherwiseTransparentWidgetYieldsNothing() {
        val px = canvas(100, 100)
        // A few sparse white strokes.
        for (x in 10..90 step 6) px.rect(100, x, 40, x + 1, 60, white)
        assertNull(WidgetBackgroundSampler.sample(px, 100, 100))
    }

    @Test fun anEmptyWidgetYieldsNothing() {
        assertNull(WidgetBackgroundSampler.sample(canvas(50, 50), 50, 50))
    }

    @Test fun aTinyOpaqueSpotYieldsNothing() {
        val px = canvas(100, 100).also { it.rect(100, 45, 45, 50, 50, grey) }
        assertNull(WidgetBackgroundSampler.sample(px, 100, 100))
    }

    @Test fun aBusyEdgeLikeAPhotoYieldsNothing() {
        val px = canvas(100, 100)
        for (y in 0 until 100) for (x in 0 until 100) {
            px[y * 100 + x] = 0xFF000000.toInt() or ((x * 2) shl 16) or ((y * 2) shl 8) or ((x + y) and 0xFF)
        }
        assertNull(WidgetBackgroundSampler.sample(px, 100, 100))
    }

    @Test fun contentInsideTheSurfaceDoesNotChangeTheEdgeColour() {
        val px = canvas(100, 100).also {
            it.rect(100, 10, 10, 89, 89, grey)
            it.rect(100, 40, 40, 60, 60, white)
        }
        assertEquals(grey, WidgetBackgroundSampler.sample(px, 100, 100))
    }

    @Test fun roundedCornersDoNotBreakTheSample() {
        val px = canvas(100, 100).also { it.rect(100, 10, 10, 89, 89, grey) }
        for (c in listOf(10 to 10, 89 to 10, 10 to 89, 89 to 89)) px[c.second * 100 + c.first] = clear
        assertNotNull(WidgetBackgroundSampler.sample(px, 100, 100))
    }

    @Test fun nearbyShadesVoteTogether() {
        val px = canvas(100, 100).also { it.rect(100, 10, 10, 89, 89, 0xFF3A3A3C.toInt()) }
        for (x in 10..89) px[10 * 100 + x] = 0xFF3B3A3C.toInt()
        assertNotNull(WidgetBackgroundSampler.sample(px, 100, 100))
    }
}
