package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry

/**
 * Measured-bounds fit for a tile's pinned shortcut strip.
 *
 * The caller (`:feature:home` arrangement) owns span capacity via `core:grid`
 * and passes the pre-clamped visible list; this fit owns what the measured
 * tile can actually show without overlapping its bounds or the floating edit
 * controls. It never deletes: shortcuts that do not fit spill into
 * [PinnedStripFit.hiddenCount], which the tile renders as a `+N` node when
 * the node itself fits or announces through the tile body otherwise.
 *
 * Pitch is the grid-unit pitch from the measured strip edge plus the shared
 * gap — the `Dp` form of [ShortcutStripGeometry.slotPitchPx] (`cell + gap`):
 * the measured edge holds `cells` cells separated by `cells - 1` gaps, so
 * `pitch = (edge + gap) / cells`. Glyph extent stays
 * [LauncherDimensions.unitIconSize] (never scaled to the tile); only the
 * spacing adapts to width, so a full-capacity group exactly fills the padded
 * edge on a normal-phone grid (for example a `2×2` tile at `120.dp`:
 * `40.dp + 64.dp = 104.dp = 120.dp - 2 * 8.dp`). Renderers keep glyphs at
 * the shared extent with `(pitch - extent)` gaps rather than a hard-coded
 * adjacency pitch, which is what keeps slot positions stable and
 * width-adaptive per `docs/Oneshot/00/shortcuts.md` section 14.
 *
 * Edit reserves mirror the `:feature:home` control geometry without depending
 * on it: the resize handle is a touch square center-pinned on the tile's
 * bottom-right corner and the remove affordance is a touch square center-pinned
 * on the top-right corner for small spans (`1×1`/`1×2`/`2×1`, matching
 * `removeControlPlacement`) or centered for large spans. Each contributes its
 * in-tile half (`editTouch / 2`) as an exclusion: the resize corner shortens
 * the strip's control-side inset, the top-right corner raises the vertical
 * top limit, and the centered box is tested as a rectangle against the group.
 * The fit reduces the visible prefix (far end first, index 0 stays nearest
 * the app) until the group clears every exclusion; when even the app glyph
 * alone collides (for example an edit-selected `2×2`, where a centered
 * `48.dp` control leaves no `40.dp` slot on a `120.dp` tile) the strip is
 * withheld entirely ([PinnedStripFit.showAppGlyph] false) and everything is
 * announced through the tile body. Edit is transient, so yielding is correct;
 * the binding retains every pin and the strip restores on exit.
 *
 * Pure, density-free `Dp` math; framework-free apart from the `Dp` value
 * type, so JVM unit tests cover realistic phone geometry directly.
 */
data class PinnedStripFit(
    /** Prefix length of the caller's visible list to render (far end dropped first). */
    val visibleShortcutCount: Int,
    /** False only when even the app glyph collides in edit: render no strip at all. */
    val showAppGlyph: Boolean,
    /** Whether the `+N` overflow node fits (always false while edit-selected). */
    val showOverflowNode: Boolean,
    /**
     * Retained-but-unrendered count: dropped visibles plus the caller's
     * overflow. Shown as `+N` when [showOverflowNode], otherwise announced
     * through the tile body.
     */
    val hiddenCount: Int,
    /** Grid-unit slot pitch derived from the measured strip edge. */
    val pitch: Dp,
    /**
     * Strip inset on the control side (tile padding plus the resize-corner
     * reserve while edit-selected): row end padding on horizontal strips,
     * bottom padding on vertical strips.
     */
    val endInset: Dp,
)

/**
 * Grid-unit slot pitch for a strip edge of [edgeExtent] holding [edgeCells]
 * cells with shared [gap].
 *
 * Since `edgeExtent = cells * cell + (cells - 1) * gap`, this equals the
 * `core:grid` [ShortcutStripGeometry.slotPitchPx] (`cell + gap`) for the
 * implied cell; a JVM cross-check against that contract lives in
 * `PinnedStripFitTest`.
 */
fun stripPitch(
    edgeExtent: Dp,
    gap: Dp,
    edgeCells: Int,
): Dp {
    require(edgeCells > 0) { "edgeCells must be positive" }
    require(edgeExtent >= 0.dp) { "edgeExtent must be non-negative" }
    require(gap >= 0.dp) { "gap must be non-negative" }
    return (edgeExtent + gap) / edgeCells
}

/**
 * Fits a pinned strip into measured tile bounds.
 *
 * @param tileWidth tile width as measured (the strip container, not the screen).
 * @param tileHeight tile height as measured.
 * @param span tile span; selects the strip orientation
 * ([ShortcutStripGeometry.isNarrow]) and the pitch edge, never capacity.
 * @param pinnedCount size of the caller's pre-clamped visible list.
 * @param overflowCount caller-retained hidden count beyond [pinnedCount].
 * @param showOverflowIndicator caller flag (false in edit / zero capacity).
 * @param isEditSelected whether remove/resize controls float above the tile.
 * @param padding shared tile padding; [gap] shared grid gap; [extent] shared
 * glyph extent; [editTouch] control touch size whose in-tile half is reserved.
 */
fun fitPinnedStrip(
    tileWidth: Dp,
    tileHeight: Dp,
    span: GridSpan,
    pinnedCount: Int,
    overflowCount: Int,
    showOverflowIndicator: Boolean,
    isEditSelected: Boolean,
    padding: Dp = DefaultLauncherDimensions.tilePadding,
    gap: Dp = DefaultLauncherDimensions.gridGap,
    extent: Dp = DefaultLauncherDimensions.unitIconSize,
    editTouch: Dp = CORNER_CONTROL_TOUCH_SIZE.dp,
): PinnedStripFit {
    require(pinnedCount >= 0) { "pinnedCount must be non-negative" }
    require(overflowCount >= 0) { "overflowCount must be non-negative" }
    require(padding >= 0.dp) { "padding must be non-negative" }
    require(gap >= 0.dp) { "gap must be non-negative" }
    require(extent > 0.dp) { "extent must be positive" }
    require(editTouch > 0.dp) { "editTouch must be positive" }
    val narrow = ShortcutStripGeometry.isNarrow(span)
    val large = isLargeTileSpan(span)
    val half = editTouch / 2
    val endInset = padding + if (isEditSelected) half else 0.dp
    val pitch = if (narrow) {
        stripPitch(tileHeight, gap, span.rows)
    } else {
        stripPitch(tileWidth, gap, span.columns)
    }
    // Edit exclusions, tile-local: the resize touch square center-pinned on
    // the bottom-right corner plus the remove touch square (top-right corner
    // on small spans, tile-centered on large spans), each clipped to the tile.
    val controls = if (!isEditSelected) {
        emptyList()
    } else {
        buildList {
            add(DpRect(tileWidth - half, tileHeight - half, tileWidth, tileHeight))
            if (large) {
                add(
                    DpRect(
                        tileWidth / 2 - half,
                        tileHeight / 2 - half,
                        tileWidth / 2 + half,
                        tileHeight / 2 + half,
                    ),
                )
            } else {
                add(DpRect(tileWidth - half, 0.dp, tileWidth, half))
            }
        }
    }
    // Vertical strips additionally clear the top-right remove corner on small
    // spans even when the group does not reach a control rect test below.
    val topLimit = padding + if (isEditSelected && !large) half else 0.dp

    fun groupRect(slots: Int): DpRect {
        // Extent-plus-pitch group, the Dp form of
        // ShortcutStripGeometry.stripGroupExtentPx: the first slot contributes
        // the full extent, each further slot one pitch.
        val groupLength = extent + pitch * (slots - 1)
        return if (narrow) {
            val bottom = tileHeight - endInset
            DpRect(
                tileWidth - padding - extent,
                bottom - groupLength,
                tileWidth - padding,
                bottom,
            )
        } else {
            // Mid-aligned in the control-clear region (padding to endInset),
            // the Dp form of centeredGroupStartPx; without edit reserves this
            // is exactly tile-centered.
            val regionLeft = padding
            val regionWidth = tileWidth - padding - endInset
            val left = regionLeft + (regionWidth - groupLength) / 2
            DpRect(left, tileHeight - padding - extent, left + groupLength, tileHeight - padding)
        }
    }

    fun groupFits(slots: Int): Boolean {
        val rect = groupRect(slots)
        if (rect.left < padding || rect.top < topLimit) return false
        return controls.all { rect.disjointFrom(it) }
    }

    var chosen = -1
    var nodeChosen = false
    for (k in pinnedCount downTo 0) {
        val hidden = (pinnedCount - k) + overflowCount
        val wantNode = showOverflowIndicator && !isEditSelected && hidden > 0
        if (wantNode && groupFits(slots = 1 + k + 1)) {
            chosen = k
            nodeChosen = true
            break
        }
        if (groupFits(slots = 1 + k)) {
            chosen = k
            nodeChosen = false
            break
        }
    }
    if (chosen < 0) {
        // Even the app glyph alone collides: withhold the strip entirely.
        // The caller retains every pin; the tile body announces the total.
        return PinnedStripFit(
            visibleShortcutCount = 0,
            showAppGlyph = false,
            showOverflowNode = false,
            hiddenCount = pinnedCount + overflowCount,
            pitch = pitch,
            endInset = endInset,
        )
    }
    return PinnedStripFit(
        visibleShortcutCount = chosen,
        showAppGlyph = true,
        showOverflowNode = nodeChosen,
        hiddenCount = (pinnedCount - chosen) + overflowCount,
        pitch = pitch,
        endInset = endInset,
    )
}

/** Tile-local rectangle in [Dp]; touching edges count as disjoint. */
private data class DpRect(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
    fun disjointFrom(other: DpRect): Boolean =
        right <= other.left || left >= other.right ||
            bottom <= other.top || top >= other.bottom
}
