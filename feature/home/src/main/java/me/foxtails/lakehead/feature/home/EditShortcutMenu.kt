package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * `[HomeInteractionController.Editing.selectedId][HomeInteractionController.Editing.selectedId]`
 * and never owns selection, inventory, platform access, persistence or grid
 * legality. Swipe-to-open stays removed (B1): no flick ever shows this menu.
 *
 * - Follow: long-press enters `Editing(selected)` and shows the menu; tap on
 *   another tile transfers selection directly (no `Normal` round-trip) and the
 *   menu follows; the caller renders [entries] in its own rank order and this
 *   menu performs no ordering (`ShortcutOrdering` visible/overflow stays a
 *   tile-strip concern and is never applied here).
 * - Dismiss with zero activation: empty-tap/`Back`/navigate exits to `Normal`,
 *   move/resize start enters [HomeEditSession.Active], and removal/uninstall
 *   clears the selection or the tile lookup — every path uncomposes the menu,
 *   and only a row tap ever reports [onSurfaceExecute]/[onSurfaceTogglePin].
 *   The menu itself never moves, resizes or pages tiles.
 * - None means no menu: a null/missing selection or an empty [entries] list
 *   renders nothing (no empty box, no honest-empty row).
 * - Geometry is overlay-only: [editShortcutMenuFrame] measures a sibling frame
 *   above the tile (try above, flip below, clamp) through
 *   [FloatingOverlayFrame.measure], so neighbours are overlaid and the grid
 *   never reflows, repacks or re-checks legality. The frame is the capped
 *   maximum (320dp / available-width-minus-margins, rows plus padding); the
 *   rendered box hugs content up to that maximum ([IntrinsicSize.Max] column
 *   with wrap + min/max width) so one short shortcut is a very small box and
 *   long labels cap and ellipsize in the row.
 */
@Composable
internal fun EditShortcutMenu(
    tile: HomeRenderTile,
    entries: List<ShortcutSurfaceEntry>,
    geometry: SquareCellGeometry,
    originX: Int,
    rowOrigin: Long,
    originYPx: Long,
    availableWidthPx: Int,
    minHeightPx: Int,
    onSurfaceExecute: (ItemId, String) -> Unit,
    onSurfaceTogglePin: (ItemId, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(entries.isNotEmpty()) {
        "entries must not be empty: callers show no menu when there are no shortcuts"
    }
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val tileFrame = TileBounds.signedBounds(
        column = tile.position.column,
        row = tile.position.row.toLong(),
        span = GridSpan(tile.span.columns, tile.span.rows),
        geometry = geometry,
        originX = originX,
        originRow = rowOrigin,
        originYPx = originYPx,
    )
    val menuSize = editShortcutMenuSizePx(
        availableWidthPx = availableWidthPx,
        edgeMarginPx = with(density) { 16.dp.roundToPx() },
        maxWidthPx = with(density) { 320.dp.roundToPx() },
        rowPx = with(density) { 56.dp.roundToPx() },
        paddingPx = with(density) { 8.dp.roundToPx() },
        entryCount = entries.size,
    )
    val viewport = PixelRect(
        left = 0,
        top = 0,
        right = availableWidthPx,
        bottom = maxOf(minHeightPx, menuSize.heightPx),
    )
    val frame = editShortcutMenuFrame(
        viewport = viewport,
        tileBounds = tileFrame,
        overlaySize = FrameSize(
            menuSize.widthPx,
            menuSize.heightPx.coerceAtMost(viewport.height),
        ),
        gapPx = with(density) { 8.dp.roundToPx() },
    )
    val maxWidthDp = with(density) { frame.width.toDp() }
    val maxHeightDp = with(density) { frame.height.toDp() }
    Box(
        modifier = modifier
            .offset { IntOffset(frame.left, frame.top) }
            .wrapContentSize(align = Alignment.TopStart, unbounded = false)
            .widthIn(min = EDIT_SHORTCUT_MENU_MIN_WIDTH, max = maxWidthDp)
            .heightIn(max = maxHeightDp)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outline)
            .testTag(EDIT_SHORTCUT_MENU_TAG),
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
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
    active: HomeEditSession.Active?,
    selectedId: ItemId?,
    tiles: List<HomeRenderTile>,
    entries: List<ShortcutSurfaceEntry>,
): Boolean = active == null &&
    editShortcutMenuEntries(selectedId, tiles, entries).isNotEmpty()

/**
 * Size policy for the edit-mode shortcut menu, in pixels.
 *
 * Width is `min(availableWidthPx - 2 * edgeMarginPx, maxWidthPx)` coerced into
 * `1..availableWidthPx`; height is `rowPx * max(entryCount, 1) + paddingPx`
 * coerced to at least 1px. Callers pass the B4 constants (16dp edge margin,
 * 320dp max width, 56dp rows, 8dp padding); the `max(entryCount, 1)` floor
 * keeps the formula total even though empty lists never reach composition
 * (they render no menu). Pure math, framework-free.
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
 * fall through to flipping/clamping). [viewport] is the content-local overlay
 * region (origin at 0,0). [gapPx] separates tile and menu. The overlay must
 * fit the viewport on its own; oversized requests throw instead of silently
 * overflowing.
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
