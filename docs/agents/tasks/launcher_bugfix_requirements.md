# Launcher Bug Fix and Layout Requirements

## Objective

Fix inconsistent shortcut popup sizing and unsafe tile dragging. Enforce a maximum of two completely empty rows between occupied tile sections. Make the navigation pill a true floating overlay, with continuous edge-to-edge backgrounds.

Investigate the existing implementation before changing it. The suspected causes below are not confirmed.

## 1. App Shortcut Popup: Inconsistent Sizing

### Observed problem

The compact shortcut popup is the intended design. Another popup expands to nearly the full screen despite containing only one shortcut. The difference appears related to static versus dynamic shortcuts and requires investigation.

### Requirements

#### 1.1 Unified sizing

- Use one sizing and layout policy for static, dynamic, and pinned shortcuts.
- Calculate popup dimensions from content, not shortcut type.
- A one-shortcut popup must use only the space needed for that shortcut and its controls.
- Increase height according to visible content when there are multiple shortcuts.
- Never stretch shortcut rows to fill available screen height.
- Use the existing compact popup as the visual reference.

#### 1.2 Size constraints

- Apply consistent padding, row heights, icon sizes, and spacing.
- Measure content with a maximum height based on the available viewport.
- Enable internal scrolling only if content exceeds that maximum.
- Account for system insets and the floating navigation pill.
- Keep all popup controls accessible near screen edges.

#### 1.3 Investigation

Trace the complete popup layout and measurement path. Check whether:

- Static and dynamic shortcuts use different composables or containers.
- Any container uses `fillMaxSize`, `fillMaxHeight`, excessive layout weights, or incorrect propagated constraints.
- Measurement occurs before asynchronous shortcut loading completes.
- Shortcut types produce different item counts, placeholder heights, or measurement results.
- Screen-edge placement expands the popup container.

Fix the underlying measurement problem. Do not add shortcut-type-specific size overrides.

### Acceptance tests

| Test | Expected result |
|---|---|
| One static shortcut | Compact popup |
| One dynamic shortcut | Same dimensions as an equivalent static shortcut |
| Multiple static shortcuts | Height grows with content |
| Multiple dynamic shortcuts | Same layout rules as static shortcuts |
| Mixed shortcut types | Consistent row heights and spacing |
| Shortcut data updates | Popup remeasures correctly |
| Many shortcuts | Popup respects viewport limits and scrolls internally |
| Popup near screen edge | All controls remain accessible |

Preserve shortcut execution and pinning controls. Do not redesign the menu.

## 2. Tile Dragging: Off-Screen Placement and Inaccessible Tiles

### Observed problem

Dragging can move a tile unexpectedly far outside its original position. The grid can then become unscrollable even though the displaced tile is inaccessible. Investigate drag-coordinate conversion, occupied bounds, and scroll-range calculation.

### Requirements

#### 2.1 Correct drag coordinates

- Convert pointer movement into grid coordinates using the current scroll offset, grid origin, and tile dimensions.
- Do not accumulate pointer displacement incorrectly between drag events.
- Prevent ordinary drags from causing unexpectedly large position changes.
- Preserve free horizontal and vertical movement.
- Keep dragging separate from resizing. Dragging must not resize tiles.

#### 2.2 Placement boundaries

- Restrict horizontal placement to the six-column grid.
- Do not allow partially off-grid horizontal placement.
- Permit vertical placement beyond the viewport through controlled scrolling.
- Reject invalid negative row positions.
- Never leave a tile permanently inaccessible after placement.

#### 2.3 Scrollability invariant

**In normal mode, disable vertical scrolling only if every occupied tile fits within the available vertical safe area above the navigation bar. If any occupied tile extends beyond that area, the grid must be scrollable so every tile is accessible. In edit mode, always permit scrolling and allow downward grid extension by dragging tiles into additional rows.**

- Derive scrollability from actual occupied bounds, including tile heights.
- Recalculate after completed moves, resizes, removals, insertions, and layout changes.
- Recalculate when viewport dimensions or safe-area insets change.
- Provide enough scroll range to bring the lowest tile fully into the accessible area.
- Do not determine scrollability using only currently visible tiles.
- Do not let temporary drag overlays corrupt committed occupied bounds.

#### 2.4 Dragging beyond the viewport

- Auto-scroll when a dragged tile approaches the top or bottom of the accessible viewport.
- Make auto-scroll speed proportional to pointer proximity to the edge.
- Stop auto-scrolling when the pointer leaves the edge region or dragging ends.
- Keep the dragged tile attached to the pointer during auto-scrolling.
- Recalculate proposed grid position using the updated scroll offset.

#### 2.5 Recovery

- Detect invalid horizontal and negative vertical positions in existing layouts.
- Correct invalid positions without overlapping other tiles.
- Recalculate scroll bounds for valid tiles that are already off-screen.
- Do not delete or reset the user's layout to recover an inaccessible tile.

### Acceptance tests

| Test | Expected result |
|---|---|
| Move tile horizontally | Follows pointer without jumps |
| Move tile vertically | Follows pointer and snaps to intended grid position |
| Drag toward bottom edge | Grid auto-scrolls in edit mode |
| Drag toward top edge | Grid auto-scrolls upward |
| Move tile below safe area | Grid remains scrollable |
| All tiles fit above navigation bar | Normal-mode scrolling is disabled |
| Lowest tile extends below safe area | Normal-mode scrolling is enabled |
| Resize tile beyond safe area | Scroll range updates |
| Remove lowest tile | Scroll range contracts as necessary |
| Reopen launcher | All saved tiles remain accessible |

## 3. Maximum Empty-Row Spacing

### Required behavior

Allow no more than **two consecutive, completely empty rows** between occupied sections of the grid. A row is occupied if any of its six columns contains any part of a tile, including a tile spanning multiple rows.

Example:

```text
Before:                   After:
Row 1: [tile]             Row 1: [tile]
Row 2: [empty]            Row 2: [empty]
Row 3: [empty]            Row 3: [empty]
Row 4: [empty]            Row 4: [tile]
Row 5: [empty]
Row 6: [tile]
```

Remove only completely empty rows. Preserve the arrangement of tiles within occupied sections.

### Requirements

#### 3.1 Identify empty rows

- A row is completely empty only when all six columns are unoccupied.
- Never treat a partially occupied row as empty.
- Account for tiles that span multiple rows.

#### 3.2 Truncate excessive gaps

- Find consecutive empty rows between occupied sections.
- Preserve gaps of zero, one, or two empty rows.
- Reduce larger gaps to exactly two empty rows.
- Shift the lower section and everything below it upward by the number of removed rows.
- Preserve relative tile positions within each occupied section.
- Never compact individual columns or partially occupied rows.

#### 3.3 Leading and trailing space

- Do not apply the two-row limit above the first occupied row; preserve intentional vertical positioning.
- Do not treat space below the last tile as an internal gap.
- Continue to allow downward grid extension in edit mode.

#### 3.4 When to normalize

- Normalize after completed tile moves, resizes, removals, and insertions.
- Do not continuously truncate rows during dragging.
- Persist updated tile positions after normalization.
- Recalculate occupied bounds and scrollability after normalization.
- Apply the same rules when restoring saved layouts.

### Acceptance tests

| Test | Expected result |
|---|---|
| One empty row between groups | Preserved |
| Two empty rows | Preserved |
| Three empty rows | Reduced to two |
| Ten empty rows | Reduced to two |
| Row contains one tile | Remains occupied |
| Row contains part of a larger tile | Remains occupied |
| Multiple excessive gaps | Each reduced independently |
| Drag through empty space | No premature normalization |
| Finish dragging | Excess gaps are truncated |
| Reopen launcher | Normalized positions persist |

## 4. Implementation Constraints and Validation

Investigate the current architecture and identify responsible components before editing. Keep these responsibilities separate:

- Shortcut measurement and presentation.
- Pointer-to-grid coordinate conversion.
- Drag handling and auto-scroll.
- Occupied-bound calculation.
- Scrollability.
- Empty-row normalization.

Do not introduce a second independent source of truth for tile positions or occupied bounds. Add automated tests where practical, then validate gestures on a device or emulator. Verify that normalization cannot create tile collisions and that every valid tile remains accessible after dragging.

**Non-goals:** Do not redesign shortcut popups, change shortcut execution or pinning, replace free placement with automatic packing, change the six-column layout, or change the existing resize gesture.

**Completion criteria:** All acceptance tests pass. Report the root cause of each bug, changed components, validation performed, and remaining limitations.

## 5. Navigation Bar: True Floating Overlay and Edge-to-Edge Rendering

### Required behavior

The navigation pill must be a **genuine floating overlay**, not a simulated floating bar inside an opaque bottom container. The grid must remain visible behind and around the pill while scrolling, including along the bottom edge of the screen.

### Requirements

#### 5.1 Floating navigation bar

- The navigation bar MUST NOT have a solid, full-width background.
- Only the pill and its buttons may have their intended backgrounds.
- Render the tile grid continuously behind the bar to the bottom of the screen.
- Keep scrolling tiles visible behind the pill without an opaque container obscuring them.
- Keep the pill fixed while the grid scrolls underneath.
- Preserve existing pill dimensions, alignment, corner radii, and button arrangement.

#### 5.2 Edge-to-edge rendering

- Use genuine edge-to-edge rendering, not opaque system-bar backgrounds that simulate it.
- Remove full-width bottom scrims, spacers, or container backgrounds that hide the grid.
- Render navigation controls above the grid.
- Preserve the navigation safe area when calculating grid scrollability.
- Allow tiles to render behind the pill without making them permanently inaccessible.

#### 5.3 Status bar

- Continue hiding the system status bar.
- Do not replace the hidden status bar with a black strip.
- Extend the existing background colour or wallpaper into the status-bar region.
- Keep the background continuous across the display, including the top system-inset region.

### Acceptance tests

| Test | Expected result |
|---|---|
| Grid does not require scrolling | Pill floats over existing background |
| Grid extends below navigation bar | Tiles remain visible behind pill |
| Scroll grid | Tiles pass beneath fixed pill |
| Inspect bottom screen edge | No full-width opaque background |
| Reach lowest tile | Enough scrolling to access it |
| Hide status bar | Existing background extends to top edge |
| Change background colour | Status-bar region matches new background |
| Resize or rotate display | Edge-to-edge rendering and overlay position remain correct |

**Non-goals:** Do not redesign the navigation pill, change its buttons, or alter navigation gestures. This task concerns rendering, layering, system insets, and background continuity.
