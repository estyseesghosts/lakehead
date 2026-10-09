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
import me.foxtails.lakehead.core.designsystem.CORNER_CONTROL_TOUCH_SIZE
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
 *   beside the tile (try above, flip below, roomier-side fallback) through
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
 *   content minimum. The overlay height is the natural height capped to the
 *   smaller of the visible-row cap (`rowPx * [EDIT_SHORTCUT_MENU_MAX_VISIBLE_ROWS]`
 *   plus padding, so at most five-and-a-half rows show) and the free space on
 *   the tile's roomier side ([editShortcutMenuSideSpacePx], gap-separated from
 *   the tile expanded by the spilling halves of the resize/remove corner
 *   controls), floored at one row ([editShortcutMenuCappedHeightPx]); when the
 *   natural height fits, the box wraps it with no scrolling, and only when it
 *   exceeds the cap does the box pin to the cap with an internal
 *   `verticalScroll` (edge tiles and many-row menus stay reachable, and the
 *   menu never covers the tile or its handles). This
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
    val rowPx = with(density) { SHORTCUT_SURFACE_ROW_HEIGHT.dp.roundToPx() }
    val paddingPx = halfPadPx * 2
    val menuSize = editShortcutMenuSizePx(
        availableWidthPx = availableWidthPx,
        edgeMarginPx = with(density) { 16.dp.roundToPx() },
        maxWidthPx = with(density) { 320.dp.roundToPx() },
        rowPx = rowPx,
        paddingPx = paddingPx,
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
    val gapPx = with(density) { 8.dp.roundToPx() }
    // Corner-control overhangs (pure span policy above): the resize handle spills below
    // the tile on every selected tile, the remove button above it only on small spans.
    // Reserving the spilling halves keeps the menu off the handles as well as the tile.
    val overhangs = editShortcutMenuOverhangs(
        spanColumns = tile.span.columns,
        spanRows = tile.span.rows,
        controlPx = with(density) { CORNER_CONTROL_TOUCH_SIZE.dp.roundToPx() },
    )
    val topOverhangPx = overhangs.topPx
    val bottomOverhangPx = overhangs.bottomPx
    // Capped to the roomier side (and the visible-row cap), never to the whole
    // viewport: a many-shortcut menu pins at the cap and scrolls instead of going
    // viewport-tall and covering the tile and its handles.
    val sideSpacePx = editShortcutMenuSideSpacePx(
        viewport = viewport,
        tileBounds = tileFrame,
        gapPx = gapPx,
        topOverhangPx = topOverhangPx,
        bottomOverhangPx = bottomOverhangPx,
    )
    val cappedHeightPx = editShortcutMenuCappedHeightPx(
        naturalHeightPx = menuSize.heightPx,
        rowCapPx = editShortcutMenuRowCapPx(rowPx, paddingPx),
        sideSpacePx = sideSpacePx,
        oneRowPx = editShortcutMenuOneRowPx(rowPx, paddingPx),
    )
    val frame = editShortcutMenuFrame(
        viewport = viewport,
        tileBounds = tileFrame,
        overlaySize = FrameSize(menuSize.widthPx, cappedHeightPx),
        gapPx = gapPx,
        topOverhangPx = topOverhangPx,
        bottomOverhangPx = bottomOverhangPx,
    )
    // Internal scroll only when the natural height exceeds the roomier-side cap:
    // fitting menus wrap their rows exactly, overflowing menus pin to the
    // frame and scroll inside it. Rows keep their fixed height either way.
    val needsScroll = menuSize.heightPx > cappedHeightPx
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
 * Maximum number of shortcut rows visibly shown before the menu pins and scrolls.
 *
 * The fractional half row is deliberate: it hints that the list scrolls. The pixel cap
 * derived from it ([editShortcutMenuRowCapPx]) bounds the menu even on tall viewports, so
 * the capped menu keeps fitting on the tile's roomier side instead of going viewport-tall
 * and covering the tile and its resize/remove handles.
 */
internal const val EDIT_SHORTCUT_MENU_MAX_VISIBLE_ROWS: Double = 5.5

/**
 * Pixel height of the visible-row cap: `rowPx * [EDIT_SHORTCUT_MENU_MAX_VISIBLE_ROWS]`
 * plus the exact rendered Column padding, coerced into `1..Int.MAX_VALUE`.
 *
 * The `Double` product is exact for every `Int` row height (5.5 halves exactly and the
 * magnitude stays far below 2^53), so no checked arithmetic is needed. Pure math,
 * framework-free.
 */
internal fun editShortcutMenuRowCapPx(rowPx: Int, paddingPx: Int): Int {
    require(rowPx > 0) { "rowPx must be positive, was $rowPx" }
    require(paddingPx >= 0) { "paddingPx must be non-negative, was $paddingPx" }
    return (rowPx * EDIT_SHORTCUT_MENU_MAX_VISIBLE_ROWS + paddingPx)
        .coerceIn(1.0, Int.MAX_VALUE.toDouble())
        .toInt()
}

/**
 * Pixel height of a single menu row plus the exact rendered Column padding: the floor
 * the capped height never drops below, so the menu always renders at least one (possibly
 * scrolled) row instead of collapsing to zero. Pure math, framework-free.
 */
internal fun editShortcutMenuOneRowPx(rowPx: Int, paddingPx: Int): Int {
    require(rowPx > 0) { "rowPx must be positive, was $rowPx" }
    require(paddingPx >= 0) { "paddingPx must be non-negative, was $paddingPx" }
    return try {
        Math.addExact(rowPx.toLong(), paddingPx.toLong())
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    } catch (e: ArithmeticException) {
        Int.MAX_VALUE
    }
}

/**
 * Handle-overhang reserve for the edit-mode shortcut menu, in pixels.
 *
 * The 48dp resize handle stays center-pinned on the selected tile's bottom-right corner
 * while the menu is open, so its outer half always spills below the tile; the 48dp
 * remove button is center-pinned on the top-right corner only for 1×1/1×2/2×1 spans
 * ([removeControlPlacement]) and centered inside larger spans. Reserving the spilling
 * halves ([editShortcutMenuSideSpacePx]/[editShortcutMenuFrame]) keeps the menu off the
 * handles as well as the tile. Pure math, framework-free.
 */
internal data class EditShortcutMenuOverhangs(val topPx: Int, val bottomPx: Int)

/**
 * Resolves the handle-overhang reserve for a tile span and corner-control size.
 *
 * Returns half the control on the bottom always, and half on the top only when
 * [removeControlPlacement] puts the remove button on the top-right corner. Pure math,
 * framework-free.
 */
internal fun editShortcutMenuOverhangs(
    spanColumns: Int,
    spanRows: Int,
    controlPx: Int,
): EditShortcutMenuOverhangs {
    require(spanColumns > 0) { "spanColumns must be positive, was $spanColumns" }
    require(spanRows > 0) { "spanRows must be positive, was $spanRows" }
    require(controlPx > 0) { "controlPx must be positive, was $controlPx" }
    val halfPx = controlPx / 2
    val topPx =
        if (removeControlPlacement(spanColumns, spanRows) == RemoveControlPlacement.TOP_RIGHT) {
            halfPx
        } else {
            0
        }
    return EditShortcutMenuOverhangs(topPx = topPx, bottomPx = halfPx)
}

/**
 * Free vertical space on the tile's roomier side, in pixels.
 *
 * Returns `max(spaceAbove, spaceBelow)` clamped to at least 0, where `spaceAbove` is the
 * gap-separated room between [viewport].top and the tile's avoid-top
 * (`tileBounds.top - topOverhangPx - gapPx`) and `spaceBelow` the mirror between the
 * tile's avoid-bottom and [viewport].bottom. The overhangs reserve the half of the 48dp
 * corner controls that spills outside the tile (resize handle below, remove button above
 * on small spans), so a menu capped to this space never covers the handles either. Pure
 * math, framework-free.
 */
internal fun editShortcutMenuSideSpacePx(
    viewport: PixelRect,
    tileBounds: SignedPixelFrame,
    gapPx: Int,
    topOverhangPx: Int = 0,
    bottomOverhangPx: Int = 0,
): Int {
    require(gapPx >= 0) { "gapPx must be non-negative, was $gapPx" }
    require(topOverhangPx >= 0) { "topOverhangPx must be non-negative, was $topOverhangPx" }
    require(bottomOverhangPx >= 0) { "bottomOverhangPx must be non-negative, was $bottomOverhangPx" }
    val avoidTop = tileBounds.top.toLong() - topOverhangPx
    val avoidBottom = tileBounds.bottom.toLong() + bottomOverhangPx
    val spaceAbove = avoidTop - gapPx - viewport.top
    val spaceBelow = viewport.bottom.toLong() - (avoidBottom + gapPx)
    return maxOf(spaceAbove, spaceBelow)
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()
}

/**
 * [PixelRect] tile overload of [editShortcutMenuSideSpacePx] for callers without signed frames.
 */
internal fun editShortcutMenuSideSpacePx(
    viewport: PixelRect,
    tileBounds: PixelRect,
    gapPx: Int,
    topOverhangPx: Int = 0,
    bottomOverhangPx: Int = 0,
): Int = editShortcutMenuSideSpacePx(
    viewport = viewport,
    tileBounds = SignedPixelFrame(
        left = tileBounds.left,
        top = tileBounds.top,
        widthPx = tileBounds.width,
        heightPx = tileBounds.height,
    ),
    gapPx = gapPx,
    topOverhangPx = topOverhangPx,
    bottomOverhangPx = bottomOverhangPx,
)

/**
 * Clamps the single measured menu height to the tile's roomier side and the row cap.
 *
 * Returns `min(naturalHeightPx, rowCapPx, sideSpacePx)` floored at [oneRowPx] so the menu
 * always renders at least one (possibly scrolled) row instead of collapsing to zero when
 * a tall tile leaves almost no free space. Short menus pass through unchanged; long menus
 * stop at the row cap (or earlier when the roomier side is smaller), and the caller
 * scrolls the remainder inside the frame. Pure math, framework-free; no overflow at the
 * extremes (no arithmetic, only `min`/`max`).
 */
internal fun editShortcutMenuCappedHeightPx(
    naturalHeightPx: Int,
    rowCapPx: Int,
    sideSpacePx: Int,
    oneRowPx: Int,
): Int {
    require(naturalHeightPx > 0) { "naturalHeightPx must be positive, was $naturalHeightPx" }
    require(rowCapPx > 0) { "rowCapPx must be positive, was $rowCapPx" }
    require(sideSpacePx >= 0) { "sideSpacePx must be non-negative, was $sideSpacePx" }
    require(oneRowPx > 0) { "oneRowPx must be positive, was $oneRowPx" }
    return minOf(naturalHeightPx, rowCapPx, sideSpacePx).coerceAtLeast(oneRowPx)
}

/**
 * Measures the edit-mode shortcut menu frame beside a tile: try above, flip, roomier-side fallback.
 *
 * Pure geometry; no composition, no reflow, no tile mutation. The caller renders
 * the returned frame as a sibling of the tiles so neighbouring tiles are
 * overlaid, never moved. The caller caps the requested height to the roomier side
 * ([editShortcutMenuSideSpacePx] + [editShortcutMenuCappedHeightPx]), so one side
 * always fits; every attempt is validated through [FloatingOverlayFrame.measure]
 * against [viewport]:
 *
 * 1. Above the avoid-rect (left-aligned with the tile, `avoidTop - gapPx -
 *    height`), where the avoid-rect is the tile expanded by the handle overhangs.
 * 2. Flipped below the avoid-rect (left-aligned, `avoidBottom + gapPx`) when
 *    above would leave the viewport (first-row tiles).
 * 3. Roomier-side fallback with horizontal clamping when neither side fits
 *    unclamped (corners, tiles wider than the remaining space): the menu stays on
 *    the side with more room, so the frame never lands on the tile. When the
 *    request itself exceeds the roomier side (only possible when the caller bypasses
 *    the cap), the height shrinks to the roomier side's space — the menu scrolls
 *    the remainder instead of overlapping the tile or its handles.
 *
 * [tileBounds] is the tile's content-local frame (may start above the content
 * top for rows above the signed origin; such candidates fail validation and
 * fall through to flipping/fallback). [viewport] is the visible safe window
 * in the same content-local coordinates (usually starting at the grid scroll
 * offset, not at 0,0 — see [editShortcutMenuVisibleViewport]). [gapPx]
 * separates tile and menu; [topOverhangPx]/[bottomOverhangPx] reserve the half
 * of the 48dp corner controls that spills outside the tile (resize handle below
 * on every selected tile, remove button above on 1×1/1×2/2×1 spans), so the
 * frame clears the handles as well as the tile. The overlay must fit the viewport
 * on its own; oversized requests throw instead of silently overflowing.
 */
internal fun editShortcutMenuFrame(
    viewport: PixelRect,
    tileBounds: SignedPixelFrame,
    overlaySize: FrameSize,
    gapPx: Int = 0,
    topOverhangPx: Int = 0,
    bottomOverhangPx: Int = 0,
): PixelRect {
    require(gapPx >= 0) { "gapPx must be non-negative, was $gapPx" }
    require(topOverhangPx >= 0) { "topOverhangPx must be non-negative, was $topOverhangPx" }
    require(bottomOverhangPx >= 0) { "bottomOverhangPx must be non-negative, was $bottomOverhangPx" }
    require(overlaySize.widthPx <= viewport.width && overlaySize.heightPx <= viewport.height) {
        "overlay $overlaySize does not fit viewport $viewport"
    }
    val avoidTop = tileBounds.top.toLong() - topOverhangPx
    val avoidBottom = tileBounds.bottom.toLong() + bottomOverhangPx
    val left = tileBounds.left.toLong()
    val aboveTop = avoidTop - gapPx - overlaySize.heightPx
    val belowTop = avoidBottom + gapPx
    tryMeasureMenuAt(viewport, overlaySize, left, aboveTop)?.let { return it }
    tryMeasureMenuAt(viewport, overlaySize, left, belowTop)?.let { return it }
    // Neither side fits unclamped: stay on the roomier side with the left edge
    // clamped into the viewport. A capped caller always fits the roomier side, so
    // this keeps the valid side's top; an uncapped request shrinks to the roomier
    // side's space rather than landing on the tile. Either way the frame clears
    // the avoid-rect whenever the roomier side holds at least one pixel.
    val spaceAbove = avoidTop - gapPx - viewport.top
    val spaceBelow = viewport.bottom.toLong() - (avoidBottom + gapPx)
    val aboveRoomier = spaceAbove >= spaceBelow
    val roomierSpace = (if (aboveRoomier) spaceAbove else spaceBelow).coerceAtLeast(0L)
    val heightPx = minOf(overlaySize.heightPx.toLong(), roomierSpace)
        .coerceIn(1L, Int.MAX_VALUE.toLong())
        .toInt()
    val size = if (heightPx == overlaySize.heightPx) {
        overlaySize
    } else {
        FrameSize(overlaySize.widthPx, heightPx)
    }
    val top = if (aboveRoomier) avoidTop - gapPx - heightPx else avoidBottom + gapPx
    val clampedLeft = left.coerceIn(
        viewport.left.toLong(),
        viewport.right.toLong() - size.widthPx,
    )
    // No-op for every capped caller (the roomier-side top already fits); only the
    // impossible tile-covers-viewport case coerces, preferring inside-viewport over
    // tile avoidance exactly as the old clamp did.
    val clampedTop = top.coerceIn(
        viewport.top.toLong(),
        viewport.bottom.toLong() - heightPx,
    )
    return FloatingOverlayFrame.measure(
        viewport = viewport,
        size = size,
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
    topOverhangPx: Int = 0,
    bottomOverhangPx: Int = 0,
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
    topOverhangPx = topOverhangPx,
    bottomOverhangPx = bottomOverhangPx,
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
