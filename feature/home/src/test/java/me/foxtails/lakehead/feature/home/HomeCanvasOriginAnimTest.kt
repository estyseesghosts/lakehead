package me.foxtails.lakehead.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.TileBounds
import me.foxtails.lakehead.core.grid.GridRest
import me.foxtails.lakehead.core.grid.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5 single-object origin animation coverage.
 *
 * The composable owns exactly one origin [Animatable] for the whole field;
 * tiles keep their internal grid positions while the shared origin moves to
 * the signed rest target ([originTargetFor]). These tests prove the target
 * delta for a remove-bottom-half transition, that every kept tile shifts by
 * exactly that delta (one object, placements untouched), that a rapid
 * add+remove retarget converges on the latest target with the same object,
 * and the snap predicate (first frame, null rest, zero height).
 */
class HomeCanvasOriginAnimTest {

    private val geometry = SquareCellGeometry(
        columnCount = 5,
        cellSizePx = 100,
        columnGapPx = 8,
        rowGapPx = 8,
    )
    private val stride = 108L

    @Test
    fun removeBottomHalf_movesFieldDownAsOneObject() {
        // Viewport 2000px, stride 108. Before: 30 rows of content
        // (30 * 100 + 29 * 8 = 3232px) overflows → tall, origin anchors 0.
        // After removing the bottom half: 15 rows
        // (15 * 100 + 14 * 8 = 1612px) fit → short, biased origin
        // round((2000 - 1612) * 0.45) = round(174.6) = 175.
        val viewportHeightPx = 2000
        val beforeRest = signedRestFor(
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = 3232,
            rowStridePx = stride,
        )
        val afterRest = signedRestFor(
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = 1612,
            rowStridePx = stride,
        )
        requireNotNull(beforeRest)
        requireNotNull(afterRest)
        assertEquals(0L, beforeRest.gridOriginY)
        assertEquals(175L, afterRest.gridOriginY)

        // The animation target follows the signed rest origin exactly.
        assertEquals(0f, originTargetFor(beforeRest))
        assertEquals(175f, originTargetFor(afterRest))
        val delta = afterRest.gridOriginY - beforeRest.gridOriginY
        assertEquals(175L, delta)

        // One object: every kept tile shifts down by exactly the target delta,
        // and relative offsets between tiles are unchanged (placements untouched).
        val rowOrigin = 0L
        val beforeTops = mutableListOf<Int>()
        val afterTops = mutableListOf<Int>()
        for (row in 0L..2L) {
            val before = TileBounds.signedBounds(
                column = 0,
                row = row,
                span = GridSpan(1, 1),
                geometry = geometry,
                originRow = rowOrigin,
                originYPx = beforeRest.gridOriginY,
            )
            val after = TileBounds.signedBounds(
                column = 0,
                row = row,
                span = GridSpan(1, 1),
                geometry = geometry,
                originRow = rowOrigin,
                originYPx = afterRest.gridOriginY,
            )
            assertEquals(delta, (after.top - before.top).toLong())
            assertEquals(delta, (after.bottom - before.bottom).toLong())
            beforeTops += before.top
            afterTops += after.top
        }
        assertEquals(beforeTops[1] - beforeTops[0], afterTops[1] - afterTops[0])
        assertEquals(beforeTops[2] - beforeTops[1], afterTops[2] - afterTops[1])
        assertEquals(stride.toInt(), afterTops[1] - afterTops[0])
    }

    @Test
    fun originTargetFor_nullRest_returnsZero() {
        assertEquals(0f, originTargetFor(null))
    }

    @Test
    fun originTargetFor_passesThroughSignedRestOrigin() {
        assertEquals(
            540f,
            originTargetFor(GridRest(gridOriginY = 540L, scrollRangePx = 0L, scrollable = false)),
        )
        assertEquals(
            0f,
            originTargetFor(GridRest(gridOriginY = 0L, scrollRangePx = 1232L, scrollable = true)),
        )
    }

    @Test
    fun shouldSnapOrigin_firstFrame_snaps() {
        val rest = GridRest(gridOriginY = 175L, scrollRangePx = 0L, scrollable = false)
        assertTrue(shouldSnapOrigin(rest, viewportHeightPx = 2000, isFirstFrame = true))
    }

    @Test
    fun shouldSnapOrigin_nullRest_snaps() {
        assertTrue(shouldSnapOrigin(null, viewportHeightPx = 2000, isFirstFrame = false))
    }

    @Test
    fun shouldSnapOrigin_zeroHeight_snaps() {
        val rest = GridRest(gridOriginY = 175L, scrollRangePx = 0L, scrollable = false)
        assertTrue(shouldSnapOrigin(rest, viewportHeightPx = 0, isFirstFrame = false))
    }

    @Test
    fun shouldSnapOrigin_steadyRestChange_animates() {
        val rest = GridRest(gridOriginY = 175L, scrollRangePx = 0L, scrollable = false)
        assertFalse(shouldSnapOrigin(rest, viewportHeightPx = 2000, isFirstFrame = false))
    }

    @Test
    fun retarget_convergesOnLatestTargetWithSameObject(): Unit = runBlocking {
        withContext(ImmediateTestFrameClock()) {
            // One object for the whole field: the retarget reuses it.
            val origin = Animatable(0f)
            val same = origin
            // Start a long flight toward T1, then cancel mid-flight like a
            // LaunchedEffect(target) retarget does when the target changes.
            val flight = launch {
                origin.animateTo(175f, animationSpec = tween(durationMillis = 60_000))
            }
            delay(250)
            flight.cancelAndJoin()
            val midFlight = origin.value
            assertTrue("expected mid-flight progress, got $midFlight", midFlight > 0f && midFlight < 175f)
            // Retarget from the current value converges on the latest target.
            origin.animateTo(0f, animationSpec = tween(durationMillis = 1))
            assertEquals(0f, origin.value, 0.001f)
            assertSame(same, origin)
        }
    }

    /**
     * Test frame clock that fires frames with real timestamps so a single
     * [Animatable] can be driven in a JVM unit test without a Compose runtime.
     * Each frame yields first so the animation cannot starve the test thread:
     * delays and cancellation are still delivered, and long tweens stay
     * mid-flight in real time until cancelled.
     */
    private class ImmediateTestFrameClock : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            kotlinx.coroutines.yield()
            return onFrame(System.nanoTime())
        }
    }
}
