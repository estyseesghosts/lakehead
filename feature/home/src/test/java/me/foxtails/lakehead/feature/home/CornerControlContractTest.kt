package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_VISUAL_SIZE
import org.junit.Assert.assertEquals
import org.junit.Test

class CornerControlContractTest {
    @Test
    fun controls_shareVisualAndTouchContract() {
        assertEquals(32, CORNER_CONTROL_VISUAL_SIZE)
        assertEquals(48, CORNER_CONTROL_TOUCH_SIZE)
        assertEquals(CORNER_CONTROL_TOUCH_SIZE, RESIZE_TOUCH_DP)
    }
}
