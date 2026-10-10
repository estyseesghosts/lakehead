package me.foxtails.lakehead

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** Renders a real view hierarchy through the same path the hosted widget uses. */
class WidgetBackgroundSampleDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun host(child: View?, w: Int = 400, h: Int = 200): FrameLayout =
        FrameLayout(context).also { root ->
            if (child != null) root.addView(child, FrameLayout.LayoutParams(240, 120, Gravity.CENTER))
            root.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, w, h)
        }

    @Test fun aSmallerSolidSurfaceFillsWithItsColour() {
        val grey = Color.rgb(58, 58, 60)
        val surface = View(context).apply { setBackgroundColor(grey) }
        val sampled = runBlocking { sampleBackground(host(surface)) }
        assertNotNull("a solid surface smaller than the view must give a fill", sampled)
        // The downscaled edge blends a little; the fill only has to read as the same grey.
        for (shift in intArrayOf(16, 8, 0)) {
            val expected = (grey shr shift) and 0xFF
            val actual = (sampled!! shr shift) and 0xFF
            assertTrue("channel $shift: $actual vs $expected", kotlin.math.abs(actual - expected) <= 6)
        }
    }

    @Test fun anEmptyTransparentWidgetGetsNoFill() {
        assertNull(runBlocking { sampleBackground(host(null)) })
    }
}
