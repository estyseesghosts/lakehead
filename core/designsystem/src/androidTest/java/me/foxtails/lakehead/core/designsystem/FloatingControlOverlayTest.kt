package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused composition tests for the C3 [FloatingControlOverlay] primitive: measured
 * interactive boundary, hit-test priority ordering, independent overlay semantics,
 * focus-trapping policy, and minimum touch-target/accessibility behavior.
 *
 * The host container is fixed at 400x800 dp so expected pixel geometry is derived at
 * runtime from [createComposeRule] density; no hard-coded pixel expectations appear
 * below.
 */
@RunWith(AndroidJUnit4::class)
class FloatingControlOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val centerAnchor = FrameAnchor(HorizontalFrameAnchor.CENTER, VerticalFrameAnchor.CENTER)

    @Test
    fun measuredBoundaryMatchesFrameGeometry() {
        val measured = mutableMapOf<String, PixelRect>()
        val contentFrames = mutableMapOf<String, RecordedFrame>()
        val containerSizes = mutableMapOf<String, IntSize>()

        composeRule.setContent {
            FloatingControlOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                backgroundContent = { backgroundBox(containerSizes) },
                overlays = listOf(
                    OverlayLayer(
                        id = "panel",
                        priority = 1,
                        anchor = centerAnchor,
                        size = FrameSize(120, 90),
                        contentDescription = "Control panel",
                        content = { recordingBox("panel", contentFrames) },
                    ),
                ),
                focusPolicy = OverlayFocusPolicy.LEAVE_FOCUS_UNCHANGED,
                // Small enough that the 120x90 px frame is never touch-expanded.
                minTouchTargetDp = 1.dp,
                onOverlayBoundaryMeasured = { id, bounds -> measured[id] = bounds },
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            // The host container is requested at 400x800 dp but the device screen
            // may constrain it, so the expected frame is derived from the measured
            // container size rather than a hard-coded viewport.
            val container = containerSizes.getValue("background")
            val expected = FloatingOverlayFrame.measure(
                viewport = PixelRect(0, 0, container.width, container.height),
                size = FrameSize(120, 90),
                anchor = centerAnchor,
            )
            assertEquals(expected, measured.getValue("panel"))
            // Visual content keeps the uncoerced frame size at the measured origin.
            assertEquals(Offset(expected.left.toFloat(), expected.top.toFloat()), contentFrames.getValue("panel").position)
            assertEquals(IntSize(expected.width, expected.height), contentFrames.getValue("panel").size)
        }
    }

    @Test
    fun interactiveBoundaryHonorsMinimumTouchTarget() {
        val measured = mutableMapOf<String, PixelRect>()
        val minTouchPx = with(composeRule.density) { 48.dp.roundToPx() }

        composeRule.setContent {
            FloatingControlOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                backgroundContent = { backgroundBox() },
                overlays = listOf(
                    OverlayLayer(
                        id = "tiny",
                        priority = 1,
                        anchor = FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
                        size = FrameSize(2, 2),
                        contentDescription = "Tiny control",
                        content = { Box(Modifier) },
                    ),
                ),
                focusPolicy = OverlayFocusPolicy.LEAVE_FOCUS_UNCHANGED,
                onOverlayBoundaryMeasured = { id, bounds -> measured[id] = bounds },
            )
        }
        composeRule.waitForIdle()

        // fetchSemanticsNode synchronizes internally and must run on the test
        // thread, outside runOnIdle.
        val nodeSize = composeRule
            .onNodeWithContentDescription("Tiny control")
            .fetchSemanticsNode().size
        composeRule.runOnIdle {
            assertEquals(PixelRect(0, 0, minTouchPx, minTouchPx), measured.getValue("tiny"))
            assertTrue("interactive size $nodeSize must meet 48dp ($minTouchPx px)", nodeSize.width >= minTouchPx)
            assertTrue("interactive size $nodeSize must meet 48dp ($minTouchPx px)", nodeSize.height >= minTouchPx)
        }
    }

    @Test
    fun higherPriorityWinsOverlappingHitTest() {
        val activated = mutableListOf<String>()

        composeRule.setContent {
            FloatingControlOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                backgroundContent = { backgroundBox() },
                overlays = listOf(
                    OverlayLayer(
                        id = "bottom",
                        priority = 1,
                        anchor = centerAnchor,
                        size = FrameSize(200, 200),
                        contentDescription = "Bottom control",
                        content = { Box(Modifier) },
                    ),
                    OverlayLayer(
                        id = "top",
                        priority = 10,
                        anchor = centerAnchor,
                        size = FrameSize(200, 200),
                        contentDescription = "Top control",
                        content = { Box(Modifier) },
                    ),
                ),
                focusPolicy = OverlayFocusPolicy.LEAVE_FOCUS_UNCHANGED,
                minTouchTargetDp = 1.dp,
                onOverlayActivated = { activated.add(it) },
            )
        }
        composeRule.waitForIdle()
        // Inject at the center of the actual overlay node. This keeps the touch in
        // that node's coordinate space even when the Compose root is inset/offset.
        composeRule.onNodeWithContentDescription("Top control").performTouchInput { click() }
        composeRule.waitForIdle()

        // Diagnostic geometry is fetched on the test thread (fetch synchronizes
        // internally) so a miss reports where every layer actually landed.
        val topBounds = composeRule
            .onNodeWithContentDescription("Top control")
            .fetchSemanticsNode().boundsInRoot
        val bottomBounds = composeRule
            .onNodeWithContentDescription("Bottom control")
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertEquals(
                "top=$topBounds bottom=$bottomBounds",
                listOf("top"),
                activated.toList(),
            )
        }
    }

    @Test
    fun equalPriorityKeepsCallerListOrder() {
        val activated = mutableListOf<String>()

        composeRule.setContent {
            FloatingControlOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                backgroundContent = { backgroundBox() },
                overlays = listOf(
                    OverlayLayer(
                        id = "first",
                        priority = 5,
                        anchor = centerAnchor,
                        size = FrameSize(200, 200),
                        contentDescription = "First control",
                        content = { Box(Modifier) },
                    ),
                    OverlayLayer(
                        id = "second",
                        priority = 5,
                        anchor = centerAnchor,
                        size = FrameSize(200, 200),
                        contentDescription = "Second control",
                        content = { Box(Modifier) },
                    ),
                ),
                focusPolicy = OverlayFocusPolicy.LEAVE_FOCUS_UNCHANGED,
                minTouchTargetDp = 1.dp,
                onOverlayActivated = { activated.add(it) },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Second control").performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf("second"), activated.toList()) }
    }

    @Test
    fun hitTestOrderingIsStableAndAscending() {
        val layers = listOf(
            testLayer("b", priority = 5),
            testLayer("a", priority = 1),
            testLayer("c", priority = 5),
        )

        assertEquals(listOf("a", "b", "c"), OverlayHitTestOrder.sort(layers).map(OverlayLayer::id))
    }

    @Test
    fun overlaySemanticsStayIndependentWithoutMerging() {
        val activated = mutableListOf<String>()

        composeRule.setContent {
            FloatingControlOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                backgroundContent = { backgroundBox() },
                overlays = listOf(
                    OverlayLayer(
                        id = "one",
                        priority = 1,
                        anchor = FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
                        size = FrameSize(120, 90),
                        contentDescription = "Overlay one",
                        content = { Box(Modifier) },
                    ),
                    OverlayLayer(
                        id = "two",
                        priority = 2,
                        anchor = FrameAnchor(HorizontalFrameAnchor.RIGHT, VerticalFrameAnchor.BOTTOM),
                        size = FrameSize(120, 90),
                        contentDescription = "Overlay two",
                        content = { Box(Modifier) },
                    ),
                ),
                focusPolicy = OverlayFocusPolicy.LEAVE_FOCUS_UNCHANGED,
                minTouchTargetDp = 1.dp,
                onOverlayActivated = { activated.add(it) },
            )
        }

        // Background and both overlays are independently addressable; the container
        // invents no labels of its own.
        composeRule.onNodeWithContentDescription("Background content").assertExists()
        composeRule.onNodeWithContentDescription("Overlay one").assertExists()
        composeRule.onNodeWithContentDescription("Overlay two").assertExists()
        // Click actions come only from overlay boundaries, never the background.
        composeRule.onNodeWithContentDescription("Background content").assertHasNoClickAction()
        composeRule.onNodeWithContentDescription("Overlay one").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Overlay two").assertHasClickAction()
        composeRule.onNode(hasClickAction() and hasContentDescription("Background content"))
            .assertDoesNotExist()
    }

    @Test
    fun trapFocusHidesBackgroundFromAccessibilityTreeButKeepsItComposed() {
        composeRule.setContent {
            FloatingControlOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                backgroundContent = { backgroundBox() },
                overlays = listOf(
                    testLayer(
                        id = "panel",
                        description = "Control panel",
                        anchor = centerAnchor,
                    ),
                ),
                focusPolicy = OverlayFocusPolicy.TRAP_FOCUS_IN_OVERLAYS,
                minTouchTargetDp = 1.dp,
            )
        }

        // Hidden from the merged (accessibility-facing) tree, but still composed.
        composeRule.onNodeWithContentDescription("Background content").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Control panel").assertExists()
        composeRule.onNodeWithContentDescription(
            "Background content",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun trapFocusWithNoOverlaysLeavesBackgroundExposed() {
        composeRule.setContent {
            FloatingControlOverlay(
                modifier = Modifier.size(400.dp, 800.dp),
                backgroundContent = { backgroundBox() },
                overlays = emptyList(),
                focusPolicy = OverlayFocusPolicy.TRAP_FOCUS_IN_OVERLAYS,
            )
        }

        composeRule.onNodeWithContentDescription("Background content").assertExists()
    }

    @Test
    fun duplicateLayerIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                FloatingControlOverlay(
                    modifier = Modifier.size(400.dp, 800.dp),
                    backgroundContent = { backgroundBox() },
                    overlays = listOf(
                        testLayer(id = "same"),
                        testLayer(id = "same"),
                    ),
                )
            }
        }
    }

    @Composable
    private fun backgroundBox(containerSizes: MutableMap<String, IntSize>? = null) {
        // The content fills the primitive's background wrapper so the captured
        // size is the measured container size, not a zero-size empty box.
        var modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Background content" }
        if (containerSizes != null) {
            modifier = modifier.onGloballyPositioned { coordinates ->
                containerSizes["background"] = coordinates.size
            }
        }
        Box(modifier)
    }

    @Composable
    private fun recordingBox(tag: String, frames: MutableMap<String, RecordedFrame>) {
        // fillMaxSize so the capture observes the frame-sized parent the
        // primitive provides, rather than a zero-size wrapped box.
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    frames[tag] = RecordedFrame(coordinates)
                },
        )
    }

    private fun testLayer(
        id: String,
        priority: Int = 1,
        description: String = "Layer $id",
        anchor: FrameAnchor = FrameAnchor(HorizontalFrameAnchor.LEFT, VerticalFrameAnchor.TOP),
    ): OverlayLayer = OverlayLayer(
        id = id,
        priority = priority,
        anchor = anchor,
        size = FrameSize(120, 90),
        contentDescription = description,
        content = { Box(Modifier) },
    )

    private data class RecordedFrame(
        val position: Offset,
        val size: IntSize,
    ) {
        constructor(coordinates: LayoutCoordinates) : this(
            position = coordinates.positionInRoot(),
            size = coordinates.size,
        )
    }
}
