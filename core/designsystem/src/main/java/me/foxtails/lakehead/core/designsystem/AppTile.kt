package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.lakehead.core.grid.GridSpan
import me.foxtails.lakehead.core.grid.ShortcutStripGeometry

/**
 * Presentation-only state for a small reusable app tile.
 *
 * [label] is never rendered as visible text; it must stay non-blank because it
 * supplies the tile's accessible name (default `contentDescription`). [span] selects
 * the icon anchor via [symbolAlignment] but performs no placement legality,
 * span-support or column-count checks: whether the span may be placed remains owned
 * by `core:grid`. [isSelected] and [isPressed] are caller-owned transient visual
 * states; this tile never derives them from inventory, persistence or its own
 * interaction source.
 */
data class AppTileState(
    val label: String,
    val span: GridSpan = GridSpan(1, 1),
    val colorRole: TileColorRole = TileColorRole.Normal,
    val isSelected: Boolean = false,
    val isPressed: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "label must be non-blank" }
    }
}

/** Shared visual/semantic constants for [AppTile]. */
object AppTileDefaults {
    /** `stateDescription` announced when [AppTileState.isPressed] is true. */
    const val PressedStateDescription = "Pressed"

    val TileShape = RectangleShape
}

/**
 * Resolved semantic tile colors for one [TileColorRole].
 *
 * @param container tile background (pressed state darkens via alpha).
 * @param content label/icon tint on [container].
 * @param selectionBorder outline drawn when the tile is selected.
 */
internal data class TileColors(
    val container: Color,
    val content: Color,
    val selectionBorder: Color,
)

/**
 * Resolves [role] to Material dynamic colors.
 *
 * See `docs/grid.md` section 12: Normal resolves to
 * `surfaceContainer`/`onSurface`, Accent to `primaryContainer`/`onPrimaryContainer`;
 * the pressed container is the base at 70% alpha, the selected container is the
 * base at 50% alpha (selection border is kept), and the selection border is
 * `scheme.primary`. Only the role is persisted, never these resolved colors.
 */
@Composable
internal fun resolveTileColors(
    role: TileColorRole,
    isPressed: Boolean,
    isSelected: Boolean = false,
): TileColors {
    val scheme = MaterialTheme.colorScheme
    val baseContainer = when (role) {
        TileColorRole.Normal -> scheme.surfaceContainer
        TileColorRole.Accent -> scheme.primaryContainer
    }
    val baseContent = when (role) {
        TileColorRole.Normal -> scheme.onSurface
        TileColorRole.Accent -> scheme.onPrimaryContainer
    }
    return TileColors(
        container = when {
            isSelected -> baseContainer.copy(alpha = 0.5f)
            isPressed -> baseContainer.copy(alpha = 0.7f)
            else -> baseContainer
        },
        content = baseContent,
        selectionBorder = scheme.primary,
    )
}

/**
 * `true` when [span] is a large tile: anything other than `1×1`, `1×2` and
 * `2×1` (including `2×2` and every wider/taller span).
 *
 * Shared rule with the edit-control small set (`1×1`/`1×2`/`2×1` keep the
 * top-right remove affordance; see `removeControlPlacement` in
 * `:feature:home`): small spans keep the compact symbol treatment, large
 * spans with zero pins render the enlarged centered icon and with pins
 * render the shortcut strip.  Pure, framework-free, no legality checks.
 */
fun isLargeTileSpan(span: GridSpan): Boolean =
    !(span.columns == 1 && span.rows == 1) &&
        !((span.columns == 1 && span.rows == 2) || (span.columns == 2 && span.rows == 1))

/**
 * Small reusable icon-only app-tile primitive with sibling hit regions.
 *
 * Custom Compose Foundation/graphics/text only: no Material product components, no app
 * inventory, database, persistence, navigation or business logic. Callers supply the
 * accessibility [AppTileState.label], the caller-composed [icon] slot (which receives
 * the resolved symbol extent: the 1×1 reference size on small/compact paths, the
 * centered large extent on large tiles with zero pins), separate
 * body/symbol click callbacks and an optional Body-only long-click callback, plus
 * optional [contentDescription]/[symbolContentDescription] overrides (each defaults to
 * the label so every region always has an accessible name). No text label is ever
 * rendered on the home-tile path: pinned shortcuts stay icon-only per
 * `docs/Oneshot/00/shortcuts.md` section 24, with their labels exposed as accessible
 * names (see [pinnedShortcutDescription]) rather than visible text.
 *
 * Layout: the outer [Box] is a non-clickable anchor container (background and
 * selection border only; never a gesture detector). Its children are siblings,
 * never nested clickables, in TalkBack order:
 *
 * - TileBody: full-size [Box] (conditional `combinedClickable` only when interaction
 *   is enabled: `onClick = onBodyClick`, `onLongClick` = tile callback, [Role.Button])
 *   with `semantics(mergeDescendants = true)` exposing [Role.Button] plus `disabled()`
 *   while disabled. Owns empty-area taps and long-press; glyph siblings drawn above it cover
 *   their own boxes and forward the same tile long-press.
 * - AppLaunchButton: [Box.combinedClickable] (`onClick = onSymbolClick`,
 *   `onLongClick` = tile callback, [Role.Button])
 *   wrapping the [icon] slot, with `semantics(mergeDescendants = true)`.
 *   One detector per hit region; no root or competing detector.
 * - Album photo layer (only when [albumPhoto] is supplied on a span of at
 *   least 2x2): full-bleed cropped photo with a crossfade on change, drawn
 *   above TileBody (which keeps the accessible name) and below glyphs and
 *   the selection border. No app icon is shown unless pins exist, in which
 *   case the strip draws over a bottom scrim; notification counts and
 *   previews stay hidden while the photo shows.
 * - Pinned shortcut strip (only when [pinnedShortcuts] is non-empty): the app icon
 *   moves to its compact anchor and each visible shortcut becomes its own
 *   [Role.Button] glyph carrying [onShortcutClick] with its opaque id, its own
 *   accessible label, and the same tile `onLongClick` (one `combinedClickable`
 *   per glyph), so TalkBack announces app and shortcuts as discrete actions
 *   while a physical long-press anywhere on the tile enters the one tile
 *   long-press path.
 *   Wide and square spans lay the group out as one bottom-centered [Row] (app
 *   rightmost, first-pinned nearest the app, stable pin order, mid-aligned in
 *   the control-clear region so the group never drifts to an end); narrow
 *   single-column spans lay it out as a right-edge [Column] with the app
 *   bottom-most and shortcuts extending upward. Both recompute from the
 *   measured bounds on every resize. Slot pitch is the grid-unit pitch derived
 *   from the measured strip edge plus the shared gap ([stripPitch], the `Dp`
 *   form of `core:grid` `slotPitchPx`); glyphs stay at
 *   [LauncherDimensions.unitIconSize] (the shared 1×1-equivalent visual metric,
 *   never scaled to the tile) with `(pitch - extent)` gaps, so a
 *   full-capacity group exactly fills the padded edge on a normal-phone grid
 *   and spacing adapts to width instead of a hard-coded adjacency pitch.
 *   Glyph touch regions equal their visuals here (no 48.dp minima) so
 *   neighbouring slots never overlap ambiguously on tight strips. The group
 *   is fitted by [fitPinnedStrip]: shortcuts that do not fit spill into the
 *   overflow count (far end first, index 0 stays nearest the app), the `+N`
 *   node renders only when it too fits, and while [isEditSelected] the strip
 *   additionally clears the floating remove/resize controls (top-right corner
 *   on small spans, tile-centered on large spans, resize corner always),
 *   withholding glyphs — down to the whole strip — rather than overlapping.
 *   Everything withheld stays retained by the caller and announced.
 * - Overflow indicator (only when the fitted [PinnedStripFit.showOverflowNode]
 *   holds): a visible `+N` node with no click action and no [Role.Button]
 *   (non-launchable). Its count covers every hidden shortcut: caller overflow
 *   plus visibles dropped by the fit. When hidden (edit-selected tile,
 *   zero-capacity span, or a node that itself does not fit) the total is
 *   merged into TileBody's `stateDescription` instead, so the overflow stays
 *   announced.
 *
 * Zero pins keeps the unpinned treatment on every span: `1×1` stays full-bleed
 * (no [LauncherDimensions.tilePadding] inset; the tile background fills the
 * whole cell) while the symbol hit region stays icon-sized centered on the
 * tile, small spans (`1×2` bottom-center, `2×1` bottom-right) anchor the
 * reference-size symbol per `docs/grid.md` section 4, and large spans center
 * the icon at `min(availableWidth, availableHeight)` after padding with no
 * strip reserve (the [icon] slot receives that extent). Pinning the first
 * shortcut switches a large tile to the compact strip layout; unpinning the
 * last one restores the unpinned anchor.
 *
 * Accessibility: TileBody merges descendants into a focusable node with the resolved
 * content description, the `selected` flag for [AppTileState.isSelected], and
 * [AppTileDefaults.PressedStateDescription] while [AppTileState.isPressed] is true.
 * Selected/pressed semantics live on TileBody only; the symbol region exposes only
 * its own content description. Every hit region (TileBody, app launch/strip glyph,
 * each shortcut glyph) exposes the same tile `onLongClick` when non-null and no
 * long-click action when null; short taps stay target-specific and a long-press
 * release never fires a short click.
 * Pressed/selected visuals are caller-driven through
 * state; the tile holds no state and issues no events beyond the supplied callbacks.
 *
 * @param onBodyClick invoked from TileBody; callers keep it launch-equivalent to
 * [onSymbolClick] today (Body diverges to a notification surface later per
 * `docs/grid.md` section 10).
 * @param onSymbolClick invoked from AppLaunchButton / app strip glyph.
 * @param onLongClick shared tile long-press callback invoked from TileBody, the app
 * launch/strip glyph and every shortcut glyph (one `combinedClickable` per hit
 * region, no root detector). When null no region exposes a long-click action.
 * @param icon caller-composed symbol slot.  Receives the resolved square symbol
 * extent for this layout (reference size, compact unit size, or the large centered
 * extent) so the caller can scale [AppSymbol] without measuring the tile itself.
 * @param pinnedShortcuts visible pinned shortcuts in pin order (index 0 nearest the
 * app icon), already clamped to [ShortcutStripGeometry.maxSlots] for [AppTileState.span]
 * by the caller (`:feature:home` arrangement owns capacity; this tile never
 * re-derives it).  Empty (default) renders no strip.  Today this is caller-supplied
 * tile state joined from the persisted binding and live shortcut metadata; the tile
 * performs no inventory, platform or database access.
 * @param overflowCount pinned shortcuts hidden beyond strip capacity (default 0).
 * Never deleted: the caller retains the full ordered list and restores visibility
 * on resize.  Must be `>= 0`.
 * @param showOverflowIndicator whether to render the visible `+N` overflow node.
 * Callers pass false while the tile is edit-selected (edit controls float above
 * the strip, so the indicator yields) and on zero-capacity spans; the count is
 * still announced via TileBody semantics in those cases.
 * @param onShortcutClick invoked with the tapped shortcut's opaque id; pin
 * toggling and persistence stay with the caller and never execute here.
 * @param isInteractionEnabled when false every hit region keeps its minima,
 * merged-descendants semantics, [Role.Button], descriptions and border visuals
 * but exposes no click/long-click actions (no gesture detector while disabled),
 * so in-edit drag gestures win
 * the slop race. Defaults to true (fully interactive).  Shortcut glyphs follow
 * the same gate: disabled in edit mode so the tile drags as one object and
 * shortcut invocation never interferes with tile editing.
 * @param isEditSelected whether the tile is the edit-selected tile with
 * floating remove/resize controls above it. The strip reserves their insets
 * (top-right corner on small spans, tile-centered on large spans, resize
 * corner always) and withholds glyphs rather than overlapping; withheld
 * shortcuts join the overflow count announced through the tile body.
 * Defaults to false. `:feature:home` passes `isSelected || isEditing`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    state: AppTileState,
    onBodyClick: () -> Unit,
    onSymbolClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (symbolExtent: Dp) -> Unit)? = null,
    contentDescription: String? = null,
    symbolContentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    pinnedShortcuts: List<PinnedTileShortcut> = emptyList(),
    overflowCount: Int = 0,
    showOverflowIndicator: Boolean = false,
    onShortcutClick: (String) -> Unit = {},
    isInteractionEnabled: Boolean = true,
    isEditSelected: Boolean = false,
    notificationCount: Int = 0,
    notificationPreviews: List<TileNotificationPreview> = emptyList(),
    // Album slideshow photo, already resolved by the caller (see AlbumTileCoordinator
    // in `:app`). Shown full-bleed and cropped only on spans of at least 2x2
    // ([isAlbumPhotoSpan]); smaller spans render the normal icon and keep the
    // binding. While shown, notification counts and previews stay hidden and
    // the pinned strip (when present) draws over a bottom scrim.
    albumPhoto: ImageBitmap? = null,
) {
    require(notificationCount >= 0) { "notificationCount must be >= 0" }
    require(contentDescription == null || contentDescription.isNotBlank()) {
        "contentDescription must be null or non-blank"
    }
    require(symbolContentDescription == null || symbolContentDescription.isNotBlank()) {
        "symbolContentDescription must be null or non-blank"
    }
    require(overflowCount >= 0) {
        "overflowCount must be >= 0"
    }
    // Strip capacity is caller-owned (core:grid via :feature:home arrangement);
    // the visible list arrives pre-clamped and pin-ordered (index 0 nearest app).
    val hasPinned = pinnedShortcuts.isNotEmpty()
    val resolvedDescription = contentDescription ?: state.label
    val resolvedSymbolDescription = symbolContentDescription ?: resolvedDescription
    val tileColors = resolveTileColors(
        role = state.colorRole,
        isPressed = state.isPressed,
        isSelected = state.isSelected,
    )
    val containerColor = tileColors.container
    val dimensions = LocalLauncherDimensions.current

    BoxWithConstraints(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .fillMaxSize()
            .background(containerColor, AppTileDefaults.TileShape)
            .then(
                if (state.isSelected) {
                    Modifier.border(
                        2.dp,
                        tileColors.selectionBorder,
                        AppTileDefaults.TileShape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        // Count-and-icon pair on single-column / single-row tiles: sized from
        // the measured width so the digits match the icon's visible glyph.
        val countText = if (isCountTileSpan(state.span)) {
            notificationCountText(notificationCount, state.span)
        } else {
            ""
        }
        // Large tiles show the newest-first preview instead; the icon drops to
        // its compact anchor while previews exist (stable through edit select).
        // An album photo replaces both the icon and the previews.
        val showAlbumPhoto = albumPhoto != null && isAlbumPhotoSpan(state.span)
        val hasPreview = !showAlbumPhoto && !isCountTileSpan(state.span) && notificationPreviews.isNotEmpty()
        val countLayout = if (countText.isNotEmpty()) {
            countedSymbolLayout(
                availableWidth = maxWidth - dimensions.tilePadding * 2,
                symbolSize = dimensions.symbolSize,
                gap = CountedSymbolGap,
                characterCount = countText.length,
            )
        } else {
            null
        }
        val countedIcon: (@Composable (Dp) -> Unit)? = if (countLayout != null) {
            { _ -> CountedSymbol(countText, countLayout, CountedSymbolGap, icon) }
        } else {
            icon
        }
        val countedPairWidth = if (countLayout != null) {
            countLayout.textWidth + CountedSymbolGap + countLayout.iconExtent
        } else {
            dimensions.unitIconSize
        }
        // A row strip gives the pair extra slots; displaced shortcuts spill into +N.
        val countExtraSlots = if (countLayout != null && !ShortcutStripGeometry.isNarrow(state.span)) {
            val pitch = stripPitch(maxWidth, dimensions.gridGap, state.span.columns)
            kotlin.math.ceil(
                ((countedPairWidth - dimensions.unitIconSize) / pitch).toDouble(),
            ).toInt().coerceAtLeast(1)
        } else {
            0
        }
        // Measured-bounds strip fit: what the tile can show without leaving
        // its bounds or overlapping the floating edit controls. Withheld
        // shortcuts spill into hiddenCount (rendered as +N only when the node
        // itself fits, announced through TileBody otherwise); the caller
        // retains the full ordered list and restores visibility on resize.
        // Null while pin-free (a zero-capacity span may still carry a caller
        // overflow count, announced as before).
        val stripFit = if (hasPinned) {
            fitPinnedStrip(
                tileWidth = maxWidth,
                tileHeight = maxHeight,
                span = state.span,
                pinnedCount = pinnedShortcuts.size,
                overflowCount = overflowCount,
                showOverflowIndicator = showOverflowIndicator,
                isEditSelected = isEditSelected,
                padding = dimensions.tilePadding,
                gap = dimensions.gridGap,
                extent = dimensions.unitIconSize,
                extraSlots = countExtraSlots,
            )
        } else {
            null
        }
        // Hidden overflow stays announced: merged into TileBody's stateDescription
        // alongside the pressed marker (never a visible node in edit, on
        // zero-capacity spans, or when the node itself does not fit).
        val showOverflowNode = stripFit?.showOverflowNode == true
        val effectiveHiddenCount = stripFit?.hiddenCount ?: overflowCount
        val hiddenOverflowDescription =
            if (!showOverflowNode && effectiveHiddenCount > 0) {
                pinnedOverflowDescription(effectiveHiddenCount)
            } else {
                null
            }
        val isFullBleed = state.span == GridSpan(1, 1)
        // TileBody: primary hit region and fallback empty-area long-press
        // target; glyph regions (AppLaunchRegion/AppStripGlyph/ShortcutGlyph)
        // also forward the shared onLongClick via their own combinedClickable.
        // No gesture detector while disabled (conditional combinedClickable
        // only when enabled); disabled semantics stay exposed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = resolvedDescription
                    this.role = Role.Button
                    this.selected = state.isSelected
                    val markers = listOfNotNull(
                        if (state.isPressed) AppTileDefaults.PressedStateDescription else null,
                        if (notificationCount > 0) notificationCountDescription(notificationCount) else null,
                        if (hasPreview) "latest from ${notificationPreviews.first().name}" else null,
                        hiddenOverflowDescription,
                    )
                    if (markers.isNotEmpty()) {
                        this.stateDescription = markers.joinToString(", ")
                    }
                    if (!isInteractionEnabled) {
                        disabled()
                    }
                }
                .then(
                    if (isInteractionEnabled) {
                        Modifier.combinedClickable(
                            onClick = onBodyClick,
                            onLongClick = onLongClick,
                            role = Role.Button,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        // Album photo above the body (below glyphs and the selection border):
        // full-bleed, cropped, crossfading on change. The tile body keeps the
        // accessible name, so this layer exposes none.
        if (showAlbumPhoto && albumPhoto != null) {
            AlbumPhotoLayer(
                photo = albumPhoto,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (hasPreview && !isEditSelected) {
            val region = notificationPreviewRegion(
                tileWidth = maxWidth,
                tileHeight = maxHeight,
                padding = dimensions.tilePadding,
                gap = dimensions.gridGap,
                extent = dimensions.unitIconSize,
            )
            if (region != null) {
                NotificationPreviewList(
                    previews = notificationPreviews,
                    layout = notificationPreviewLayout(region.height, notificationPreviews.size),
                    color = tileColors.content,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(dimensions.tilePadding)
                        .size(region.width, region.height),
                )
            }
        }
        if (!hasPinned && !showAlbumPhoto) {
            // Unpinned presentation: no strip is reserved until the first pin.
            if (isFullBleed) {
                AppLaunchRegion(
                    resolvedSymbolDescription = resolvedSymbolDescription,
                    isInteractionEnabled = isInteractionEnabled,
                    onSymbolClick = onSymbolClick,
                    onLongClick = onLongClick,
                    boxModifier = if (countLayout != null) {
                        Modifier.align(Alignment.Center)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    } else {
                        Modifier.align(Alignment.Center)
                            .size(dimensions.symbolSize.coerceAtLeast(48.dp))
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    },
                ) { countedIcon?.invoke(dimensions.symbolSize) }
            } else if (isLargeTileSpan(state.span) && countLayout == null && !hasPreview) {
                // Large tile, zero pins: icon scales to min(availW, availH)
                // after padding, measured live so every resize recomputes it.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(dimensions.tilePadding),
                    contentAlignment = Alignment.Center,
                ) {
                    val largeExtent = centeredIconExtent(maxWidth, maxHeight)
                    AppLaunchRegion(
                        resolvedSymbolDescription = resolvedSymbolDescription,
                        isInteractionEnabled = isInteractionEnabled,
                        onSymbolClick = onSymbolClick,
                        onLongClick = onLongClick,
                        boxModifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) { icon?.invoke(largeExtent) }
                }
            } else {
                // Small spans keep the unpinned anchor from docs/grid.md
                // section 4: 1x2 bottom-center, 2x1 bottom-right, at the
                // reference symbol size with the shared tile padding.
                val smallAlignment = symbolAlignment(state.span, hasPinned = false)
                Box(
                    modifier = Modifier.fillMaxSize().padding(dimensions.tilePadding),
                    contentAlignment = smallAlignment,
                ) {
                    AppLaunchRegion(
                        resolvedSymbolDescription = resolvedSymbolDescription,
                        isInteractionEnabled = isInteractionEnabled,
                        onSymbolClick = onSymbolClick,
                        onLongClick = onLongClick,
                        boxModifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) { countedIcon?.invoke(dimensions.symbolSize) }
                }
            }
        } else if (hasPinned && ShortcutStripGeometry.isNarrow(state.span)) {
            // Vertical strip: right edge, app bottom-most, shortcuts upward at
            // the grid-unit pitch.  Layout derives from the live bounds (the
            // control-clear bottom inset plus tile padding), so resize
            // recomputes it with no cached offsets.  Farthest-pinned renders
            // first (top), index 0 sits nearest above the app.  The fitted
            // prefix is rendered; withheld shortcuts join the overflow count.
            val fit = stripFit!!
            // Album tiles draw a bottom scrim behind the strip so glyphs stay
            // legible over bright photos; plain tiles keep the bare container.
            if (showAlbumPhoto) {
                AlbumStripScrim(
                    stripHeight = dimensions.tilePadding * 2 + dimensions.unitIconSize,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (fit.showAppGlyph || fit.showOverflowNode || fit.visibleShortcutCount > 0) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            start = dimensions.tilePadding,
                            top = dimensions.tilePadding,
                            end = dimensions.tilePadding,
                            bottom = fit.endInset,
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        (fit.pitch - dimensions.unitIconSize).coerceAtLeast(0.dp),
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (fit.showOverflowNode) {
                        OverflowGlyph(count = fit.hiddenCount)
                    }
                    val visible = pinnedShortcuts.take(fit.visibleShortcutCount)
                    for (index in visible.indices.reversed()) {
                        val pinned = visible[index]
                        ShortcutGlyph(
                            pinned = pinned,
                            isInteractionEnabled = isInteractionEnabled,
                            onShortcutClick = onShortcutClick,
                            onLongClick = onLongClick,
                        )
                    }
                    if (fit.showAppGlyph) {
                        AppStripGlyph(
                            resolvedSymbolDescription = resolvedSymbolDescription,
                            isInteractionEnabled = isInteractionEnabled,
                            onSymbolClick = onSymbolClick,
                            onLongClick = onLongClick,
                            icon = countedIcon,
                            width = countedPairWidth,
                        )
                    }
                }
            }
        } else if (hasPinned) {
            // Horizontal strip: one bottom-centered group (app rightmost, then
            // index 0..N leftward), mid-aligned in the control-clear region
            // (the Dp form of ShortcutStripGeometry.centeredGroupStartPx), so
            // the group stays centered instead of right-aligned; the live
            // anchor recomputes the centering on every resize.  Slots sit at
            // the grid-unit pitch with glyphs at the shared extent.
            // A photo-only album tile (no pins) reaches no branch: the photo
            // layer above already drew it.
            val fit = stripFit!!
            if (showAlbumPhoto) {
                AlbumStripScrim(
                    stripHeight = dimensions.tilePadding * 2 + dimensions.unitIconSize,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (fit.showAppGlyph || fit.showOverflowNode || fit.visibleShortcutCount > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = dimensions.tilePadding,
                            top = 0.dp,
                            end = fit.endInset,
                            bottom = dimensions.tilePadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(
                        (fit.pitch - dimensions.unitIconSize).coerceAtLeast(0.dp),
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fit.showOverflowNode) {
                        OverflowGlyph(count = fit.hiddenCount)
                    }
                    val visible = pinnedShortcuts.take(fit.visibleShortcutCount)
                    for (index in visible.indices.reversed()) {
                        val pinned = visible[index]
                        ShortcutGlyph(
                            pinned = pinned,
                            isInteractionEnabled = isInteractionEnabled,
                            onShortcutClick = onShortcutClick,
                            onLongClick = onLongClick,
                        )
                    }
                    if (fit.showAppGlyph) {
                        AppStripGlyph(
                            resolvedSymbolDescription = resolvedSymbolDescription,
                            isInteractionEnabled = isInteractionEnabled,
                            onSymbolClick = onSymbolClick,
                            onLongClick = onLongClick,
                            icon = countedIcon,
                            width = if (countLayout != null) {
                                dimensions.unitIconSize + fit.pitch * countExtraSlots
                            } else {
                                dimensions.unitIconSize
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Gap between the count text and the app icon. */
private val CountedSymbolGap = 4.dp

/**
 * App symbol hit region: a sibling of TileBody (never nested inside it) with
 * `semantics(mergeDescendants = true)` exposing only its own content
 * description.  Short tap invokes [onSymbolClick]; long-press forwards the
 * shared tile [onLongClick] (null exposes no long-click action).  One
 * `combinedClickable` per region; never a root detector.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppLaunchRegion(
    resolvedSymbolDescription: String,
    isInteractionEnabled: Boolean,
    onSymbolClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    boxModifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = boxModifier
            .semantics(mergeDescendants = true) {
                this.contentDescription = resolvedSymbolDescription
                this.role = Role.Button
            }
            .then(
                if (isInteractionEnabled) {
                    Modifier.combinedClickable(
                        onClick = onSymbolClick,
                        onLongClick = onLongClick,
                        role = Role.Button,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Compact app glyph inside the pinned strip: [LauncherDimensions.unitIconSize]
 * visual (the shared 1×1-equivalent metric per the shortcut spec), own
 * accessible name, own click plus the shared tile long-press.  A sibling of
 * the shortcut glyphs, never merged with them, so TalkBack announces the app
 * launch separately from shortcuts.  One `combinedClickable` when interaction is
 * enabled (no detector while disabled).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppStripGlyph(
    resolvedSymbolDescription: String,
    isInteractionEnabled: Boolean,
    onSymbolClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    icon: (@Composable (symbolExtent: Dp) -> Unit)?,
    width: Dp,
) {
    val dimensions = LocalLauncherDimensions.current
    Box(
        modifier = Modifier
            .size(width = width, height = dimensions.unitIconSize)
            .testTag(APP_STRIP_GLYPH_TAG)
            .semantics(mergeDescendants = true) {
                this.contentDescription = resolvedSymbolDescription
                this.role = Role.Button
            }
            .then(
                if (isInteractionEnabled) {
                    Modifier.combinedClickable(
                        onClick = onSymbolClick,
                        onLongClick = onLongClick,
                        role = Role.Button,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = if (width > dimensions.unitIconSize) Alignment.CenterEnd else Alignment.Center,
    ) {
        icon?.invoke(dimensions.unitIconSize)
    }
}

/**
 * One pinned shortcut glyph: [LauncherDimensions.unitIconSize] neutral marker
 * (outline ring, distinct from the app bitmap) with its own accessible label,
 * its own click reporting the opaque [PinnedTileShortcut.id], and the shared
 * tile [onLongClick] (null exposes no long-click action).  Icon-only
 * visuals (no visible text); the label lives in semantics.  One
 * `combinedClickable` when interaction is enabled (no detector while disabled)
 * so the tile drags as one object in edit mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutGlyph(
    pinned: PinnedTileShortcut,
    isInteractionEnabled: Boolean,
    onShortcutClick: (String) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val dimensions = LocalLauncherDimensions.current
    Box(
        modifier = Modifier
            .size(dimensions.unitIconSize)
            .testTag("$PINNED_SHORTCUT_TAG_PREFIX${pinned.id}")
            .semantics(mergeDescendants = true) {
                this.contentDescription = pinnedShortcutDescription(pinned.label)
                this.role = Role.Button
            }
            .then(
                if (isInteractionEnabled) {
                    Modifier.combinedClickable(
                        onClick = { onShortcutClick(pinned.id) },
                        onLongClick = onLongClick,
                        role = Role.Button,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dimensions.unitIconSize)
                .background(scheme.outlineVariant, CircleShape)
                .border(2.dp, scheme.outline, CircleShape),
        )
    }
}

/**
 * Visible overflow indicator: `+N` with the [pinnedOverflowDescription]
 * accessible name, no click action and no [Role.Button] (non-launchable).
 * Rendered only when the caller shows it (capacity available, tile not
 * edit-selected); otherwise the count merges into TileBody semantics.
 */
@Composable
private fun OverflowGlyph(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(LocalLauncherDimensions.current.unitIconSize)
            .testTag(PINNED_OVERFLOW_TAG)
            .semantics(mergeDescendants = true) {
                this.contentDescription = pinnedOverflowDescription(count)
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "+$count",
            style = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
        )
    }
}

/** Test tag on the compact app glyph inside the pinned strip. */
const val APP_STRIP_GLYPH_TAG: String = "pinned-strip-app"

/** Test-tag prefix for one pinned shortcut glyph; the shortcut id is appended. */
const val PINNED_SHORTCUT_TAG_PREFIX: String = "pinned-shortcut-"

/** Test tag on the visible `+N` overflow indicator. */
const val PINNED_OVERFLOW_TAG: String = "pinned-overflow"
