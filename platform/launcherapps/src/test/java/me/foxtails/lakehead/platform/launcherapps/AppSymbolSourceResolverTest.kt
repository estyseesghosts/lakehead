package me.foxtails.lakehead.platform.launcherapps

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSymbolSourceResolverTest {

    private val resolver = AppSymbolSourceResolver()

    // --- SDK 33+ with monochrome → Monochrome ---

    @Test
    fun sdk33_adaptiveWithMonochrome_returnsMonochrome() {
        val mono = FakeDrawable("mono")
        val fg = FakeDrawable("fg")
        val input = FakeAdaptiveDrawable(mono, fg)

        val result = resolver.resolve(input, sdkInt = 33, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.Monochrome)
        assertSame(mono, result.drawable)
    }

    @Test
    fun sdk36_adaptiveWithMonochrome_returnsMonochrome() {
        val mono = FakeDrawable("mono")
        val fg = FakeDrawable("fg")
        val input = FakeAdaptiveDrawable(mono, fg)

        val result = resolver.resolve(input, sdkInt = 36, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.Monochrome)
        assertSame(mono, result.drawable)
    }

    // --- SDK 33+ without monochrome → AdaptiveForeground ---

    @Test
    fun sdk33_adaptiveWithoutMonochrome_returnsAdaptiveForeground() {
        val fg = FakeDrawable("fg")
        val input = FakeAdaptiveDrawable(monochrome = null, foreground = fg)

        val result = resolver.resolve(input, sdkInt = 33, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.AdaptiveForeground)
        assertSame(fg, result.drawable)
    }

    // --- SDK 26–32 adaptive → AdaptiveForeground ---

    @Test
    fun sdk26_adaptive_returnsAdaptiveForeground() {
        val fg = FakeDrawable("fg")
        val input = FakeAdaptiveDrawable(monochrome = null, foreground = fg)

        val result = resolver.resolve(input, sdkInt = 26, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.AdaptiveForeground)
        assertSame(fg, result.drawable)
    }

    @Test
    fun sdk29_adaptive_returnsAdaptiveForeground() {
        val fg = FakeDrawable("fg")
        val input = FakeAdaptiveDrawable(monochrome = FakeDrawable("mono"), foreground = fg)

        val result = resolver.resolve(input, sdkInt = 29, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.AdaptiveForeground)
        assertSame(fg, result.drawable)
    }

    @Test
    fun sdk32_adaptive_returnsAdaptiveForeground() {
        val fg = FakeDrawable("fg")
        val input = FakeAdaptiveDrawable(monochrome = FakeDrawable("mono"), foreground = fg)

        val result = resolver.resolve(input, sdkInt = 32, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.AdaptiveForeground)
        assertSame(fg, result.drawable)
    }

    // --- Non-adaptive → Legacy ---

    @Test
    fun sdk29_nonAdaptive_returnsLegacy() {
        val input = FakeDrawable("icon")

        val result = resolver.resolve(input, sdkInt = 29, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.Legacy)
        assertSame(input, result.drawable)
    }

    @Test
    fun sdk33_nonAdaptive_returnsLegacy() {
        val input = FakeDrawable("icon")

        val result = resolver.resolve(input, sdkInt = 33, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { true })

        assertTrue(result is AppSymbolSource.Legacy)
        assertSame(input, result.drawable)
    }

    // --- Unusable monochrome falls back to the foreground ---

    @Test
    fun sdk33_unusableMonochrome_fallsBackToAdaptiveForeground() {
        val fg = FakeDrawable("fg")
        val input = FakeAdaptiveDrawable(FakeDrawable("blank-mono"), fg)

        val result = resolver.resolve(
            input, sdkInt = 33, readAdaptive = fakeReadAdaptive, isUsableMonochrome = { false },
        )

        assertTrue(result is AppSymbolSource.AdaptiveForeground)
        assertSame(fg, result.drawable)
    }

    @Test
    fun usabilityIsJudgedOnTheMonochromeLayerItself() {
        val mono = FakeDrawable("mono")
        val input = FakeAdaptiveDrawable(mono, FakeDrawable("fg"))
        var judged: Drawable? = null

        resolver.resolve(
            input, sdkInt = 34, readAdaptive = fakeReadAdaptive,
            isUsableMonochrome = { judged = it; true },
        )

        assertSame(mono, judged)
    }

    @Test
    fun coverageRejectsEmptySpeckAndSolidFillButAcceptsAShape() {
        fun alphas(visible: Int, total: Int = 1000) =
            IntArray(total) { if (it < visible) 255 else 0 }

        assertFalse(MonochromeUsability.isUsableCoverage(IntArray(0)))
        assertFalse("blank", MonochromeUsability.isUsableCoverage(alphas(0)))
        assertFalse("speck", MonochromeUsability.isUsableCoverage(alphas(5)))
        assertFalse("solid fill", MonochromeUsability.isUsableCoverage(alphas(990)))
        assertTrue("glyph", MonochromeUsability.isUsableCoverage(alphas(300)))
        assertTrue("thin glyph at the lower bound", MonochromeUsability.isUsableCoverage(alphas(15)))
    }

    @Test
    fun faintPixelsDoNotCountAsCoverage() {
        assertFalse(MonochromeUsability.isUsableCoverage(IntArray(1000) { 8 }))
    }

    // --- Fakes ---

    /** Minimal Drawable stand-in for JVM tests (no Robolectric). */
    private open class FakeDrawable(val tag: String = "") : Drawable() {
        override fun draw(canvas: Canvas) {}
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = 0
        override fun getIntrinsicWidth(): Int = 0
        override fun getIntrinsicHeight(): Int = 0
    }

    /** Simulates an AdaptiveIconDrawable — identifiable by containing a foreground. */
    private class FakeAdaptiveDrawable(
        val monochrome: Drawable?,
        val foreground: Drawable,
    ) : FakeDrawable("adaptive")

    /**
     * Fake readAdaptive that mimics the production behaviour:
     * - Below SDK 26: no adaptive layers
     * - SDK 26+: if [drawable] looks adaptive, return foreground (and monochrome only when sdkInt ≥ 33)
     * - Otherwise: null (non-adaptive)
     */
    private val fakeReadAdaptive: (Drawable, Int) -> AdaptiveLayers? = { drawable, sdkInt ->
        if (sdkInt >= 26 && drawable is FakeAdaptiveDrawable) {
            AdaptiveLayers(
                foreground = drawable.foreground,
                monochrome = if (sdkInt >= 33) drawable.monochrome else null,
            )
        } else {
            null
        }
    }
}