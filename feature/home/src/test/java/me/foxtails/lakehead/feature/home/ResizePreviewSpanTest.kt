package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.resizeCandidateFrame
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W3 reduced-motion parity for the resize preview.
 *
 * The animated path and the reduced-motion snap branch share
 * [resizePreviewSpan], so both render the same validated span and the same
 * signed candidate frame. Functionality (committed span) is identical; only
 * the presentation (rapid animation vs immediate snap) differs.
 */
class ResizePreviewSpanTest {

    private val profile = ProfileId("personal")
    private val identity = AppIdentity(profile, ComponentIdentity("com.app", "Main"))

    private fun tile(
        id: String,
        spanColumns: Int = 2,
        spanRows: Int = 2,
        column: Int = 0,
        row: Int = 0,
    ) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = identity,
        position = LogicalPosition(column, row),
        span = LogicalSpan(spanColumns, spanRows),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
    )

    private val geometry = SquareCellGeometry(
        columnCount = 5,
        cellSizePx = 100,
        columnGapPx = 8,
        rowGapPx = 8,
    )

    @Test
    fun previewSpan_animatedAndReducedMotion_agree() {
        val t = tile("app-a")
        val placements = listOf(t)
        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 5, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(3, 2), placements, columns = 5, rows = 10)
        val active = session as HomeEditSession.Active

        val animated = resizePreviewSpan(active, reducedMotion = false)
        val snapped = resizePreviewSpan(active, reducedMotion = true)

        assertEquals(GridSpan(3, 2), animated)
        assertEquals(animated, snapped)
    }

    @Test
    fun previewFrame_animatedAndReducedMotion_equal() {
        val t = tile("app-a")
        val placements = listOf(t)
        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 5, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(3, 2), placements, columns = 5, rows = 10)
        val active = session as HomeEditSession.Active

        val animatedSpan = resizePreviewSpan(active, reducedMotion = false)
        val snappedSpan = resizePreviewSpan(active, reducedMotion = true)
        val animatedFrame = resizeCandidateFrame(
            originPosition = me.foxtails.lakehead.core.grid.GridPosition(
                active.origin.column,
                active.origin.row,
            ),
            candidateSpan = animatedSpan,
            geometry = geometry,
            rowOrigin = 0L,
            originYPx = 0L,
        )
        val snappedFrame = resizeCandidateFrame(
            originPosition = me.foxtails.lakehead.core.grid.GridPosition(
                active.origin.column,
                active.origin.row,
            ),
            candidateSpan = snappedSpan,
            geometry = geometry,
            rowOrigin = 0L,
            originYPx = 0L,
        )

        assertEquals(animatedFrame, snappedFrame)
        // 3x2 at stride 108: width 3*108-8=316, height 2*108-8=208.
        assertEquals(316, animatedFrame.widthPx)
        assertEquals(208, animatedFrame.heightPx)
    }

    @Test
    fun touchTarget_is48dp() {
        assertEquals(48, RESIZE_TOUCH_DP)
    }

    // ---- B3 hardening: invalid keeps last-valid, commit null on blocked-only ----

    @Test
    fun previewSpan_blockedProposal_keepsLastValid() {
        // B3 last-valid hold at the preview-span level: reach a valid 3x2,
        // then push into the obstacle at column 3. The preview span (both
        // animated and reduced-motion branches) stays at the held 3x2 while
        // validity reports the collision — the error overlay flips color but
        // the frame never follows the pointer into illegal territory.
        val t = tile("app-a")
        val obstacle = tile("app-b", spanColumns = 1, spanRows = 1, column = 3, row = 0)
        val placements = listOf(t, obstacle)
        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 5, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(3, 2), placements, columns = 5, rows = 10)
            .updateResizeCandidate(LogicalSpan(4, 2), placements, columns = 5, rows = 10)
        val active = session as HomeEditSession.Active

        assertEquals(CandidateValidity.COLLIDING, active.candidateValidity)
        assertEquals(GridSpan(3, 2), resizePreviewSpan(active, reducedMotion = false))
        assertEquals(
            resizePreviewSpan(active, reducedMotion = false),
            resizePreviewSpan(active, reducedMotion = true),
        )
    }

    @Test
    fun commitSpan_blockedOnly_isNull() {
        // B3 blocked-only release: the preview never leaves the 1x1 origin
        // (2x1 collides with the neighbour at column 1), so there is nothing
        // to persist — zero writes. The preview span still renders the origin.
        val t = tile("app-a", spanColumns = 1, spanRows = 1)
        val obstacle = tile("app-b", spanColumns = 1, spanRows = 1, column = 1, row = 0)
        val placements = listOf(t, obstacle)
        val session = HomeEditSession.Idle.enterIfLaunchable(t, placements, columns = 5, rows = 10)
            .beginResize()
            .updateResizeCandidate(LogicalSpan(2, 1), placements, columns = 5, rows = 10)
        val active = session as HomeEditSession.Active

        assertEquals(CandidateValidity.COLLIDING, active.candidateValidity)
        assertEquals(null, session.resizeCommitSpan())
        assertEquals(false, session.hasResized())
        assertEquals(GridSpan(1, 1), resizePreviewSpan(active, reducedMotion = false))
        assertEquals(GridSpan(1, 1), resizePreviewSpan(active, reducedMotion = true))
    }
}
