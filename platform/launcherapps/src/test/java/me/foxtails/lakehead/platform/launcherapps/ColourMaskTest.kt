package me.foxtails.lakehead.platform.launcherapps

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourMaskTest {
    private val size = 10
    private val transparent = 0x00000000
    private val blue = 0xFF1E50C8.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun raster(background: Int, glyph: Int, glyphRange: IntRange = 3..6): IntArray =
        IntArray(size * size) { i ->
            if (i % size in glyphRange && i / size in glyphRange) glyph else background
        }

    @Test
    fun transparentBackdropKeepsTheIconsOwnAlpha() {
        val pixels = raster(background = transparent, glyph = blue)

        val mask = ColourMask.derive(pixels, size, size)

        assertArrayEquals(IntArray(pixels.size) { pixels[it] ushr 24 }, mask)
    }

    @Test
    fun opaqueBackdropIsRemovedAndTheGlyphKept() {
        val pixels = raster(background = blue, glyph = white)

        val mask = ColourMask.derive(pixels, size, size)

        assertEquals("backdrop corner", 0, mask[0])
        assertEquals("backdrop edge", 0, mask[5])
        assertEquals("glyph centre", 255, mask[4 * size + 4])
    }

    @Test
    fun darkGlyphOnLightBackdropAlsoBecomesTheMask() {
        val mask = ColourMask.derive(raster(background = white, glyph = 0xFF202020.toInt()), size, size)

        assertEquals(0, mask[0])
        assertEquals(255, mask[4 * size + 4])
    }

    @Test
    fun glyphPartiallyTransparentScalesTheMask() {
        val halfWhite = 0x80FFFFFF.toInt()
        val pixels = raster(background = blue, glyph = halfWhite)

        val mask = ColourMask.derive(pixels, size, size)

        assertEquals(0x80, mask[4 * size + 4])
    }

    @Test
    fun nearBackdropColourFallsBelowTheFloor() {
        val nearBlue = 0xFF1F51C9.toInt()

        val mask = ColourMask.derive(raster(background = blue, glyph = nearBlue), size, size)

        // Nothing separates from the backdrop, so the opaque shape is kept rather than blank.
        assertTrue(mask.all { it == 255 })
    }

    @Test
    fun fullyTransparentIconStaysEmpty() {
        val mask = ColourMask.derive(IntArray(size * size), size, size)

        assertTrue(mask.all { it == 0 })
    }

    @Test
    fun sizeMismatchIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ColourMask.derive(IntArray(5), size, size)
        }
    }
}
