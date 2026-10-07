package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.FloatingOverlayFrame
import me.foxtails.lakehead.core.designsystem.FrameAnchor
import me.foxtails.lakehead.core.designsystem.FrameSize
import me.foxtails.lakehead.core.designsystem.HorizontalFrameAnchor
import me.foxtails.lakehead.core.designsystem.PixelRect
import me.foxtails.lakehead.core.designsystem.SHORTCUT_SURFACE_ROW_HEIGHT
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceEntry
import me.foxtails.lakehead.core.designsystem.ShortcutSurfaceRow
import me.foxtails.lakehead.core.designsystem.SignedPixelFrame
import me.foxtails.lakehead.core.designsystem.SquareCellGeometry
import me.foxtails.lakehead.core.designsystem.TileBounds
import me.foxtails.lakehead.core.designsystem.VerticalFrameAnchor
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.model.ItemId

/**
 * Edit-mode shortcut menu above the selected tile (B4).
 *
 * Transient presentation only: the menu follows
 * [HomeEditState.selectedTileId]
 * and never owns selection, inventory, platform access, persistence or grid
 * legality. Swipe-to-open stays removed (B1): no flick ever shows this menu.
 *
 * - Follow: long-press enters `Editing(selected)` and shows the menu; tap on
 *   another tile transfers selection directly (no `Normal` round-trip) and the
 *   menu follows; the caller renders [entries] in its own rank order and this
 *   menu performs no ordering (`ShortcutOrdering` visible/overflow stays a
 *   tile-strip concern and is never applied here).
 * - Dismiss with zero activation: empty-tap/`Back`/navigate exits to `Normal`,
 *   move/resize start begins a [DragState], and removal/uninstall
 *   clears the selection or the tile lookup — every path uncomposes the menu,
 *   and only a row tap ever reports [onSurfaceExecute]/[onSurfaceTogglePin].
 *   The menu itself never moves, resizes or pages tiles.
 * - None means no menu: a null/missing selection or an empty [entries] list
 *   renders nothing (no empty box, no honest-empty row).
 * - Geometry is overlay-only: [editShortcutMenuFrame] measures a sibling frame
 *   above the tile (try above, flip below, clamp) through
 *   [FloatingOverlayFrame.measure], so neighbours are overlaid and the grid
 *   never reflows, repacks or re-checks legality.
 * - One measured content size plus a safe viewport: the natural height is
 *   `entries.size * [SHORTCUT_SURFACE_ROW_HEIGHT]` plus the explicit Column
 *   vertical padding below ([EDIT_SHORTCUT_MENU_HALF_PADDING] top + bottom,
 *   zero inter-row spacing), so a one-shortcut menu hugs exactly one fixed
 *   row and taller menus grow by whole rows — rows never stretch to fill.
 *   The width cap (`min(availableWidth - 2 * edge, 320dp)`) and the 96dp
 *   minimum are constraints, not measured dimensions: the rendered box hugs
 *   the widest row ([IntrinsicSize.Max]) up to the capped frame width, and
 *   the frame is placed with the capped width so a narrower render still
 *   fits. On narrow viewports where the capped frame itself is under 96dp
 *   (avail < 128dp after the edge reserve), the effective minimum follows the
 *   frame (`minOf(96dp, frameWidth)`) so the box never measures wider than
 *   its frame. Long labels cap and ellipsize in the row.
 * - Visible safe viewport: the frame is measured against
 *   [editShortcutMenuVisibleViewport] — the caller-visible window in
 *   grid-content coordinates (`visibleTopPx..visibleBottomPx`, the grid's
 *   scroll offset plus its app-safe usable height, which already excludes
 *   the floating-pill/system-gesture reserve) — never against the whole
 *   content minimum. The overlay height is the natural height clamped to
 *   that viewport ([editShortcutMenuCappedHeightPx]); when the natural
 *   height fits, the box wraps it with no scrolling, and only when it
 *   exceeds the cap does the box pin to the cap with an internal
 *   `verticalScroll` (edge tiles and many-row menus stay reachable). This
 *   menu reads no `WindowInsets` and re-subtracts no pill reserve: the
 *   usable height it receives is already safe.
 */
@Composable
internal fun EditShortcutMenu(
    tile: HomeRenderTile,
    entries: List<ShortcutSurfaceEntry>,
    metrics: HomeGridMetrics,
    availableWidthPx: Int,
    visibleTopPx: Int,
    visibleBottomPx: Int,
    onSurfaceExecute: (ItemId, String) -> Unit,
    onSurfaceTogglePin: (ItemId, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(entries.isNotEmpty()) {
        "entries must not be empty: callers show no menu when there are no shortcuts"
    }
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val tileFrame = metrics.pixelFrame(tile.position, tile.span)
    // Single measured content size: the width cap and the natural height are
    // derived once from the shared row height plus the exact vertical padding
    // rendered below (two half-pads, one computation, so math and render
    // agree to the pixel). Both stay constraints for placement; the render
    // hugs content up to them.
    val halfPadPx = with(density) { EDIT_SHORTCUT_MENU_HALF_PADDING.roundToPx() }
    val menuSize = editShortcutMenuSizePx(
        availableWidthPx = availableWidthPx,
        edgeMarginPx = with(density) { 16.dp.roundToPx() },
        maxWidthPx = with(density) { 320.dp.roundToPx() },
        rowPx = with(density) { SHORTCUT_SURFACE_ROW_HEIGHT.dp.roundToPx() },
        paddingPx = halfPadPx * 2,
        entryCount = entries.size,
    )
    // Safe viewport in grid-content coordinates: the caller passes the
    // visible window (grid scroll offset .. offset + app-safe usable height).
    // The overlay height clamps to it; the menu never sizes against the whole
    // content minimum.
    val viewport = editShortcutMenuVisibleViewport(
        availableWidthPx = availableWidthPx,
        visibleTopPx = visibleTopPx,
        visibleBottomPx = visibleBottomPx,
    )
    val cappedHeightPx = editShortcutMenuCappedHeightPx(
        naturalHeightPx = menuSize.heightPx,
        viewportHeightPx = viewport.height,
    )
    val frame = editShortcutMenuFrame(
        viewport = viewport,
        tileBounds = tileFrame,
        overlaySize = FrameSize(menuSize.widthPx, cappedHeightPx),
        gapPx = with(density) { 8.dp.roundToPx() },
    )
    // Internal scroll only when the natural height exceeds the safe cap:
    // fitting menus wrap their rows exactly, overflowing menus pin to the
    // frame and scroll inside it. Rows keep their fixed height either way.
    val needsScroll = menuSize.heightPx > viewport.height
    val menuScroll = remember(tile.itemId) { ScrollState(0) }
    val maxWidthDp = with(density) { frame.width.toDp() }
    // P2 narrow-viewport guard: the 96dp floor is a content-hug minimum, never a
    // widening constraint. When the capped frame itself is narrower (avail < 128dp
    // after the 2x16dp edge reserve), the effective minimum follows the frame so
    // widthIn(min, max) never demands a box wider than its frame.
    val effectiveMinWidthDp = minOf(EDIT_SHORTCUT_MENU_MIN_WIDTH, maxWidthDp)
    val maxHeightDp = with(density) { frame.height.toDp() }
    Box(
        modifier = modifier
            .offset { IntOffset(frame.left, frame.top) }
            .wrapContentSize(align = Alignment.TopStart, unbounded = false)
            .widthIn(min = effectiveMinWidthDp, max = maxWidthDp)
            .heightIn(max = maxHeightDp)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outline)
            .testTag(EDIT_SHORTCUT_MENU_TAG),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(vertical = EDIT_SHORTCUT_MENU_HALF_PADDING)
                .verticalScroll(menuScroll, enabled = needsScroll),
        ) {
            entries.forEach { entry ->
                ShortcutSurfaceRow(
                    entry = entry,
                    onExecute = { onSurfaceExecute(tile.itemId, entry.id) },
                    onTogglePin = { onSurfaceTogglePin(tile.itemId, entry.id) },
                )
            }
        }
    }
}

/** Test tag on the edit-mode shortcut menu overlay (testing hook only; no behavior change). */
internal const val EDIT_SHORTCUT_MENU_TAG: String = "edit-shortcut-menu"

/**
 * Minimum menu width so a single short label never collapses toward zero.
 * The box otherwise hugs the widest row ([IntrinsicSize.Max]) up to the
 * capped frame width; long labels cap at the frame and ellipsize in the row.
 */
private val EDIT_SHORTCUT_MENU_MIN_WIDTH = 96.dp

/**
 * Half of the menu's explicit vertical padding, applied top and bottom.
 *
 * The Column renders `padding(vertical = this)` with zero inter-row spacing,
 * so the rendered padding totals `2 * round(4dp)` — exactly the `paddingPx`
 * the caller passes to [editShortcutMenuSizePx] (`halfPadPx * 2`, one
 * rounding shared by math and render). Rows keep the shared fixed
 * [SHORTCUT_SURFACE_ROW_HEIGHT]; nothing stretches.
 */
private val EDIT_SHORTCUT_MENU_HALF_PADDING = 4.dp

/**
 * Resolves the shortcut rows to show for the current selection.
 *
 * Returns [entries] unchanged (caller rank order preserved) only when
 * [selectedId] is non-null, names a tile still present in [tiles], and
 * [entries] is non-empty; every other combination returns an empty list so
 * the caller renders no menu. Pure, framework-free and unit-testable:
 * long-press shows, tap-other transfers, null selection (empty-tap/`Back`/
 * navigate/unpin) hides, a removed/uninstalled tile hides via the lookup miss,
 * and zero shortcuts hides with no empty box. Move/resize hiding is owned by
 * [isEditShortcutMenuVisible], which additionally gates on the edit session.
 */
internal fun editShortcutMenuEntries(
    selectedId: ItemId?,
    tiles: List<HomeRenderTile>,
    entries: List<ShortcutSurfaceEntry>,
): List<ShortcutSurfaceEntry> {
    if (selectedId == null) return emptyList()
    if (entries.isEmpty()) return emptyList()
    if (tiles.none { it.itemId == selectedId }) return emptyList()
    return entries
}

/**
 * Owner predicate for selection-scoped menu rows (B1).
 *
 * Production scopes [entries] to the tile they were loaded for: the menu
 * shows rows only when [ownerId] names the [selectedId] tile, so an A→B
 * tap-switch never flashes A's rows on B while B's query is in flight (the
 * caller clears to an honest empty until B's rows arrive). A null [ownerId]
 * is the unscoped legacy path (previews/tests that pass global rows); it
 * preserves the [editShortcutMenuEntries] behavior unchanged. Pure,
 * framework-free and unit-testable.
 */
internal fun isSurfaceEntriesForSelection(
    ownerId: ItemId?,
    selectedId: ItemId?,
): Boolean = ownerId == null || ownerId == selectedId

/**
 * Visibility predicate for the edit-mode shortcut menu.
 *
 * The menu shows only while no move/resize session is active ([active] == null)
 * and [editShortcutMenuEntries] resolves a non-empty row list for [selectedId].
 * Move/resize start therefore dismisses the menu with zero activation; release
 * re-follows the (possibly transferred or unpinned) selection honestly. Pure,
 * framework-free and unit-testable.
 */
internal fun isEditShortcutMenuVisible(
    isDragging: Boolean,
    selectedId: ItemId?,
    tiles: List<HomeRenderTile>,
    entries: List<ShortcutSurfaceEntry>,
): Boolean = !isDragging &&
    editShortcutMenuEntries(selectedId, tiles, entries).isNotEmpty()

/**
 * Size policy for the edit-mode shortcut menu, in pixels.
 *
 * Width is `min(availableWidthPx - 2 * edgeMarginPx, maxWidthPx)` coerced into
 * `1..availableWidthPx`: a placement cap and render `max` constraint, never a
 * measured width (the box hugs the widest row up to it). Height is the single
 * measured natural content size, `rowPx * max(entryCount, 1) + paddingPx`
 * coerced to at least 1px, where `rowPx` is the shared
 * [SHORTCUT_SURFACE_ROW_HEIGHT] and `paddingPx` is the exact rendered Column
 * padding (`2 * [EDIT_SHORTCUT_MENU_HALF_PADDING]`, zero spacing). Callers
 * pass the B4 constants (16dp edge margin, 320dp max width); the
 * `max(entryCount, 1)` floor keeps the formula total even though empty lists
 * never reach composition (they render no menu). The natural height is then
 * clamped to the visible safe viewport via
 * [editShortcutMenuCappedHeightPx]; this function itself never clamps to a
 * viewport. Pure math, framework-free.
 */
internal fun editShortcutMenuSizePx(
    availableWidthPx: Int,
    edgeMarginPx: Int,
    maxWidthPx: Int,
    rowPx: Int,
    paddingPx: Int,
    entryCount: Int,
): FrameSize {
    require(availableWidthPx > 0) { "availableWidthPx must be positive, was $availableWidthPx" }
    require(edgeMarginPx >= 0) { "edgeMarginPx must be non-negative, was $edgeMarginPx" }
    require(maxWidthPx > 0) { "maxWidthPx must be positive, was $maxWidthPx" }
    require(rowPx > 0) { "rowPx must be positive, was $rowPx" }
    require(paddingPx >= 0) { "paddingPx must be non-negative, was $paddingPx" }
    require(entryCount >= 0) { "entryCount must be non-negative, was $entryCount" }
    val widthPx = minOf(
        availableWidthPx.toLong() - 2L * edgeMarginPx,
        maxWidthPx.toLong(),
    ).coerceIn(1L, availableWidthPx.toLong()).toInt()
    val heightPx = try {
        Math.addExact(
            Math.multiplyExact(rowPx.toLong(), maxOf(entryCount, 1).toLong()),
            paddingPx.toLong(),
        ).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
    return FrameSize(widthPx, heightPx)
}

/**
 * Visible safe viewport for the edit-mode shortcut menu, in grid-content pixels.
 *
 * `[visibleTopPx, visibleBottomPx]` is the caller-visible window the menu must
 * stay inside: the grid passes its scroll offset as [visibleTopPx] and
 * `offset + app-safe usable height` as [visibleBottomPx]. That usable height
 * already excludes the floating-pill/system-gesture reserve (resolved once in
 * `:app` and carried as a plain pixel count through `HomeCanvas`), so this
 * helper subtracts nothing further and reads no insets. The top is usually
 * nonzero once the grid has scrolled. Pure math, framework-free.
 */
internal fun editShortcutMenuVisibleViewport(
    availableWidthPx: Int,
    visibleTopPx: Int,
    visibleBottomPx: Int,
): PixelRect {
    require(availableWidthPx > 0) { "availableWidthPx must be positive, was $availableWidthPx" }
    require(visibleTopPx >= 0) { "visibleTopPx must be non-negative, was $visibleTopPx" }
    require(visibleBottomPx > visibleTopPx) {
        "visibleBottomPx ($visibleBottomPx) must exceed visibleTopPx ($visibleTopPx)"
    }
    return PixelRect(
        left = 0,
        top = visibleTopPx,
        right = availableWidthPx,
        bottom = visibleBottomPx,
    )
}

/**
 * Clamps the single measured menu height to the visible safe viewport.
 *
 * Returns `min(naturalHeightPx, viewportHeightPx)` coerced to at least 1px:
 * fitting menus wrap their natural height with no internal scroll, while
 * taller menus pin to the viewport height and scroll inside the frame. Pure
 * math, framework-free.
 */
internal fun editShortcutMenuCappedHeightPx(
    naturalHeightPx: Int,
    viewportHeightPx: Int,
): Int {
    require(naturalHeightPx > 0) { "naturalHeightPx must be positive, was $naturalHeightPx" }
    require(viewportHeightPx > 0) { "viewportHeightPx must be positive, was $viewportHeightPx" }
    return minOf(naturalHeightPx, viewportHeightPx).coerceAtLeast(1)
}

/**
 * Measures the edit-mode shortcut menu frame above a tile: try above, flip, clamp.
 *
 * Pure geometry; no composition, no reflow, no tile mutation. The caller renders
 * the returned frame as a sibling above the tiles so neighbouring tiles are
 * overlaid, never moved. This mirrors the retained [swipeSurfaceFrame]
 * try-beside/flip/clamp pattern transposed to the vertical axis, with every
 * attempt validated through [FloatingOverlayFrame.measure] against [viewport]:
 *
 * 1. Above the tile (left-aligned with the tile, `tileBounds.top - gapPx -
 *    height`).
 * 2. Flipped below the tile (left-aligned, `tileBounds.bottom + gapPx`) when
 *    above would leave the viewport (first-row tiles).
 * 3. Clamped into the viewport (left/top edges coerced so the frame fits) when
 *    neither side fits unclamped (corners, tiles wider than the remaining space).
 *
 * [tileBounds] is the tile's content-local frame (may start above the content
 * top for rows above the signed origin; such candidates fail validation and
 * fall through to flipping/clamping). [viewport] is the visible safe window
 * in the same content-local coordinates (usually starting at the grid scroll
 * offset, not at 0,0 — see [editShortcutMenuVisibleViewport]). [gapPx]
 * separates tile and menu. The overlay must fit the viewport on its own;
 * oversized requests throw instead of silently overflowing.
 */
internal fun editShortcutMenuFrame(
    viewport: PixelRect,
    tileBounds: SignedPixelFrame,
    overlaySize: FrameSize,
    gapPx: Int = 0,
): PixelRect {
    require(gapPx >= 0) { "gapPx must be non-negative, was $gapPx" }
    require(overlaySize.widthPx <= viewport.width && overlaySize.heightPx <= viewport.height) {
        "overlay $overlaySize does not fit viewport $viewport"
    }
    val left = tileBounds.left.toLong()
    val aboveTop = tileBounds.top.toLong() - gapPx - overlaySize.heightPx
    val belowTop = tileBounds.bottom.toLong() + gapPx
    tryMeasureMenuAt(viewport, overlaySize, left, aboveTop)?.let { return it }
    tryMeasureMenuAt(viewport, overlaySize, left, belowTop)?.let { return it }
    val clampedLeft = left.coerceIn(
        viewport.left.toLong(),
        viewport.right.toLong() - overlaySize.widthPx,
    )
    val clampedTop = aboveTop.coerceIn(
        viewport.top.toLong(),
        viewport.bottom.toLong() - overlaySize.heightPx,
    )
    return FloatingOverlayFrame.measure(
        viewport = viewport,
        size = overlaySize,
        anchor = FrameAnchor(
            horizontal = HorizontalFrameAnchor.LEFT,
            vertical = VerticalFrameAnchor.TOP,
            offsetXPx = (clampedLeft - viewport.left).toInt(),
            offsetYPx = (clampedTop - viewport.top).toInt(),
        ),
    )
}

/**
 * [PixelRect] tile overload of [editShortcutMenuFrame] for callers without signed frames.
 */
internal fun editShortcutMenuFrame(
    viewport: PixelRect,
    tileBounds: PixelRect,
    overlaySize: FrameSize,
    gapPx: Int = 0,
): PixelRect = editShortcutMenuFrame(
    viewport = viewport,
    tileBounds = SignedPixelFrame(
        left = tileBounds.left,
        top = tileBounds.top,
        widthPx = tileBounds.width,
        heightPx = tileBounds.height,
    ),
    overlaySize = overlaySize,
    gapPx = gapPx,
)

private fun tryMeasureMenuAt(
    viewport: PixelRect,
    size: FrameSize,
    left: Long,
    top: Long,
): PixelRect? {
    if (left < Int.MIN_VALUE.toLong() || left > Int.MAX_VALUE.toLong()) return null
    if (top < Int.MIN_VALUE.toLong() || top > Int.MAX_VALUE.toLong()) return null
    return try {
        FloatingOverlayFrame.measure(
            viewport = viewport,
            size = size,
            anchor = FrameAnchor(
                horizontal = HorizontalFrameAnchor.LEFT,
                vertical = VerticalFrameAnchor.TOP,
                offsetXPx = (left - viewport.left).toInt(),
                offsetYPx = (top - viewport.top).toInt(),
            ),
        )
    } catch (e: IllegalArgumentException) {
        null
    }
}
