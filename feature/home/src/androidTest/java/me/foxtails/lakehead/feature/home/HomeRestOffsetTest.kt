package me.foxtails.lakehead.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 5.3: the rest offset glides outside editing and is frozen for an edit session. */
@RunWith(AndroidJUnit4::class)
class HomeRestOffsetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var target by mutableStateOf(100f)
    private var snap by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var seen = Float.NaN

    private fun show() {
        composeRule.setContent { seen = rememberRestOffsetYPx(target, snap, editing) }
        composeRule.waitForIdle()
    }

    @Test
    fun initialValue_isTheTarget() {
        show()
        composeRule.runOnIdle { assertEquals(100f, seen, 0f) }
    }

    @Test
    fun outsideEditing_glidesToANewTarget() {
        show()
        composeRule.runOnIdle { target = 300f }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(300f, seen, 0f) }
    }

    @Test
    fun editing_holdsTheValueAcrossTargetChanges_andResumesAfterwards() {
        show()
        composeRule.runOnIdle { editing = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { target = 300f }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("frozen for the session", 100f, seen, 0f) }

        composeRule.runOnIdle { target = 40f }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnIdle { assertEquals("still frozen", 100f, seen, 0f) }

        composeRule.runOnIdle { editing = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("resumes toward the current target", 40f, seen, 0f) }
    }

    @Test
    fun editingBeganMidGlide_holdsTheMidGlideValue() {
        show()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { target = 500f }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { editing = true }
        composeRule.mainClock.advanceTimeBy(32)
        val frozen = seen
        assertTrue("began mid-glide, saw $frozen", frozen > 100f && frozen < 500f)
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.runOnIdle { assertEquals("never moves under the session", frozen, seen, 0f) }
    }

    @Test
    fun snapToTarget_skipsTheGlide() {
        show()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            snap = true
            target = 700f
        }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.runOnIdle { assertEquals(700f, seen, 0f) }
    }
}
