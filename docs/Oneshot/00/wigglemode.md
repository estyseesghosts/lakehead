Below is a planner-facing design report. It assumes the launcher already has tile placement and an **Unpin** control, and that tiles support more flexible dimensions than Windows 10 Mobile.

# Start Screen Tile Edit Mode — Design Report

## 1. Purpose

Implement a persistent home-screen tile editing mode inspired by Windows 10 Mobile.

The interaction should make tile manipulation feel direct:

- long-press a tile to enter edit mode;
- keep one tile selected at a time;
- visually shrink all non-selected tiles;
- allow selection to move between tiles without leaving edit mode;
- drag the selected tile to reposition it;
- drag the selected tile's resize affordances to change its dimensions;
- use the existing Unpin control to remove it;
- remain in edit mode until the user explicitly exits.

This is not an iOS-style "all icons wiggle" model.

The correct conceptual model is:

```text
Normal
    ↓ long press
Edit Mode + Selected Tile
```

The whole home surface enters edit mode, but exactly one tile is the current editing target.

---

# 2. Core interaction model

The home screen should have two primary modes:

```text
NORMAL

EDITING
    selectedTileId
```

Editing may internally contain transient states:

```text
Editing
├─ Selected
├─ Moving
└─ Resizing
```

These are interaction states, not separate launcher screens.

A suitable conceptual state model is:

```kotlin
sealed interface HomeInteractionState {
    data object Normal : HomeInteractionState

    data class Editing(
        val selectedTileId: TileId?
    ) : HomeInteractionState
}
```

Dragging and resizing can either be separate transient state objects or additional fields managed by the interaction controller.

---

# 3. Entering edit mode

A long-press on a tile enters edit mode.

Before:

```text
Tile A
Tile B
Tile C
```

After long-pressing Tile B:

```text
Tile A → recessed
Tile B → selected
Tile C → recessed
```

The long-press must:

1. cancel normal launch behavior;
2. enter global edit mode;
3. assign the pressed tile as `selectedTileId`;
4. expose the existing Unpin control;
5. expose resize affordances;
6. visually recess every other tile.

The selected tile should not substantially grow.

Instead, surrounding tiles should visually shrink.

---

# 4. Primary visual rule

The defining visual effect is:

```text
Normal:
    every tile = 100% scale

Editing:
    selected tile = 100%
    every other tile = approximately 90–94%
```

Initial tuning value:

```text
background tile scale ≈ 0.92
```

The exact value should remain adjustable.

The selected tile should therefore appear to come forward without actually expanding beyond its normal layout dimensions.

This is preferable to enlarging the selected tile because enlargement creates:

- overlap problems;
- apparent grid movement;
- clipping near screen edges;
- layout instability;
- visual jumps when selection changes.

The visual hierarchy should instead be:

```text
selected tile     → foreground
non-selected tile → background
```

---

# 5. Visual scaling must not change layout geometry

This is critical.

When non-selected tiles shrink, their logical grid rectangles must remain unchanged.

Example:

```text
Logical layout:

AAAA BB
AAAA CC
DDDD CC
DDDD EE
```

Edit mode may visually look like:

```text
AAAA bb
AAAA cc
dddd cc
dddd ee
```

but the occupancy grid remains unchanged.

A shrunken tile does not free cells.

Therefore:

```text
visual bounds ≠ layout bounds
```

The edit-mode shrink should be a rendering transform only.

Do not recompute:

- tile spans;
- grid occupancy;
- collision geometry;
- insertion positions;
- available free cells;

because a tile is visually recessed.

---

# 6. Selecting another tile

Edit mode persists when another tile is selected.

Example:

```text
Editing(A)
    ↓ tap B
Editing(B)
```

This must not perform:

```text
Editing(A)
→ Normal
→ Editing(B)
```

Selection should change directly.

The transition should animate simultaneously:

```text
A: 1.00 → 0.92
B: 0.92 → 1.00
```

The existing edit controls move from A to B.

While edit mode is active:

```text
tap tile ≠ launch application
```

Instead:

```text
tap tile = select tile
```

This is a global change in gesture semantics while editing.

---

# 7. Selected-tile presentation

The selected tile should receive a clear foreground treatment.

At minimum:

- full normal scale;
- higher rendering order;
- existing Unpin button;
- resize affordances;
- normal tile content remains visible.

Optional polish can later include:

- subtle elevation/shadow;
- selection outline;
- background contrast adjustment;
- small spring response during selection.

Do not depend on an outline alone.

The main indicator should remain the contrast between:

```text
selected = normal scale
others   = recessed scale
```

---

# 8. Rendering order

Recommended rendering hierarchy:

```text
1. Wallpaper
2. Non-selected tiles
3. Selected tile
4. Resize affordances
5. Unpin control
6. Drag overlays / placement previews
7. Global launcher floating controls
```

The selected tile should be rendered above ordinary tiles.

During movement or resize, the manipulated tile may be placed into an even higher temporary layer.

In Compose this could use `zIndex`, but the architecture should not depend on per-tile composables being responsible for global ordering.

The home container should know:

```text
which tile is selected
which tile is moving
which tile is resizing
```

and draw accordingly.

---

# 9. Moving tiles

The selected tile should support direct dragging.

Interaction:

```text
Editing(A)
    ↓ pointer down on tile body
    ↓ exceeds movement threshold
Moving(A)
    ↓ drag
update proposed placement
    ↓ release
commit placement
    ↓
Editing(A)
```

Dropping the tile must **not** exit edit mode.

The user should be able to perform:

```text
move A
resize A
select B
move B
unpin B
select C
resize C
```

without another long-press.

This persistent workflow is one of the main reasons for implementing edit mode.

---

# 10. Resizing model

Unlike Windows 10 Mobile, this launcher must **not** use a resize button that cycles through preset tile sizes.

Tiles are more freely resizable.

Resizing should therefore be based on drag interaction.

The selected tile exposes one or more resize handles.

Conceptually:

```text
┌───────────────────────┐
│                     × │
│                       │
│       TILE            │
│                       │
│                    ◢  │
└───────────────────────┘
```

`×` represents the existing Unpin control.

`◢` represents a resize affordance.

The exact visual design can be refined later, but the interaction model must support free span changes.

---

# 11. Resize geometry

The resize interaction should operate in grid-space, not raw pixel-space.

During resize:

```text
pointer position
    ↓
convert into grid coordinates
    ↓
derive proposed span
    ↓
validate against layout constraints
    ↓
preview legal dimensions
```

For example:

```kotlin
data class GridSpan(
    val width: Int,
    val height: Int
)
```

The user may drag from:

```text
2×2
```

toward:

```text
3×2
4×2
4×3
```

depending on pointer position and available space.

The displayed tile should snap to valid grid dimensions.

It should not persist arbitrary fractional sizes.

---

# 12. Resize anchors

The planner should explicitly define resize anchoring.

Recommended model:

The tile has a fixed anchor edge or corner while the opposite edge is dragged.

For a bottom-right handle:

```text
top-left remains fixed
bottom-right changes
```

This produces predictable behavior.

Example:

```text
Original:

┌──────┐
│      │
│      │
└──────┘◢
```

Dragging right:

```text
┌────────────┐
│            │
│            │
└────────────┘◢
```

Dragging down:

```text
┌──────┐
│      │
│      │
│      │
│      │
└──────┘◢
```

The layout engine may need to reject or resolve expansion if another tile occupies the target region.

---

# 13. Multiple resize handles

There are two viable approaches.

## Minimum implementation

Use one resize handle:

```text
bottom-right
```

Advantages:

- simple;
- predictable;
- easy gesture arbitration;
- sufficient for initial implementation.

## Expanded implementation

Support several handles:

```text
left
right
top
bottom
corners
```

This allows resizing in any direction.

That is more powerful, but it creates more interaction complexity because resizing can also change the tile origin.

The initial planner should prefer the minimum implementation unless current launcher requirements explicitly need bidirectional resizing.

---

# 14. Resize preview

During resize, show the proposed tile dimensions continuously.

Do not wait until pointer release before giving feedback.

The tile itself can animate toward valid snapped dimensions.

Example:

```text
pointer moves 26 px
→ not enough for next grid unit
→ remain 2×2

pointer crosses threshold
→ preview 3×2
```

The underlying layout engine should determine whether that proposed span is valid.

Possible outcomes:

```text
Valid:
    show new size preview

Blocked:
    remain at last valid size
```

An optional future behavior is to allow neighboring tiles to reflow while resizing. That should be a layout-engine policy, not part of the editor itself.

---

# 15. Resize gesture isolation

The resize handle must consume its own gestures.

Priority should be:

```text
resize handle
    ↓
Unpin button
    ↓
selected tile drag
    ↓
tile selection
    ↓
home scrolling
```

Dragging the resize handle must never accidentally:

- move the tile;
- scroll the home page;
- activate Unpin;
- select another tile.

Likewise, beginning a tile movement from its body must not resize it.

---

# 16. Distinguish moving and resizing

The interaction system should explicitly know which operation is occurring.

Recommended transient state:

```kotlin
sealed interface TileManipulation {
    data object None : TileManipulation

    data class Moving(
        val tileId: TileId,
        val pointerOffset: Offset
    ) : TileManipulation

    data class Resizing(
        val tileId: TileId,
        val initialSpan: GridSpan,
        val proposedSpan: GridSpan
    ) : TileManipulation
}
```

Do not infer the current operation every frame from pointer position.

Once a gesture starts as resize, it remains a resize until release or cancellation.

Once a gesture starts as move, it remains a move.

---

# 17. Existing Unpin behavior

The existing Unpin control should become part of this unified edit-state system.

It should only be visible on the currently selected tile.

Interaction:

```text
Editing(A)
    ↓ Unpin
remove A
```

Unpin should remove the home-screen tile.

It must not imply uninstalling the application.

---

# 18. What happens after Unpin

The editor should ideally remain active.

Recommended transition:

```text
Editing(A)
    ↓ unpin
Editing(null)
```

Then:

- removed tile animates away;
- remaining tiles stay visually recessed;
- no tile is temporarily selected;
- tapping another tile selects it;
- tapping the background exits edit mode.

State should therefore permit:

```kotlin
selectedTileId: TileId?
```

rather than requiring a selected tile at all times.

This produces cleaner removal behavior and avoids arbitrarily selecting another tile.

---

# 19. Exiting edit mode

Edit mode should end only through an explicit global action.

Recommended exits:

- tap empty home-screen space;
- Android Back action;
- navigate away from the tile-grid surface;
- launcher Home action where appropriate.

Do not exit when:

- a tile is moved;
- resize finishes;
- selection changes;
- Unpin completes.

When leaving edit mode:

```text
all recessed tiles:
0.92 → 1.00

selected tile:
1.00 → 1.00

controls:
fade/scale away
```

The effect should feel like the rest of the grid returns to the selected tile.

---

# 20. Scrolling while editing

The home grid remains vertically scrollable.

This creates gesture competition.

Recommended behavior:

### Drag beginning on empty space

```text
vertical movement → scroll
```

### Drag beginning on non-selected tile

A simple tap:

```text
select tile
```

A substantial movement should not automatically move it unless the tile has first become selected.

Recommended conservative rule:

```text
tap non-selected tile
→ select

drag selected tile
→ move
```

This reduces accidental rearrangement.

### Drag beginning on selected tile

```text
tile body → move tile
resize handle → resize
```

Scrolling may still occur from empty areas around the grid.

---

# 21. Horizontal-page interaction

The launcher also has horizontally adjacent surfaces.

Edit mode should prevent ambiguous horizontal navigation gestures when manipulation begins on the selected tile.

Recommended rule:

While:

```text
Moving
or
Resizing
```

the horizontal page pager must not intercept the gesture.

While merely:

```text
Editing
```

horizontal page navigation policy can be decided separately.

A reasonable first implementation is to lock the user to the Start/tile page while edit mode remains active.

This avoids accidentally swiping to the app list during tile manipulation.

If horizontal navigation must remain available later, it should begin only from clearly non-interactive background regions or edge gestures.

---

# 22. Animation requirements

Selection transitions should be coordinated.

When changing selection from A to B:

```text
A:
scale 1.00 → recessed scale

B:
recessed scale → 1.00

A controls:
hide

B controls:
show
```

All should happen in the same animation interval.

Do not:

1. restore all tiles;
2. finish editing;
3. enter editing again;
4. shrink everything again.

There should be no global flash.

---

# 23. Resize animation

Grid-snapped resizing should not look like an immediate harsh geometry jump.

When crossing a valid span threshold:

```text
2×2 → 3×2
```

the tile should animate rapidly toward its new preview bounds.

However, input must remain responsive.

The animation should never lag significantly behind the pointer.

If animation interferes with direct manipulation, prioritize pointer tracking over decorative interpolation.

---

# 24. Tile content during resize

Tile content should adapt to the **previewed dimensions** while resizing.

For example, when transitioning:

```text
1×1
→
2×1
→
2×2
```

the tile's content layout may need to change.

The content system should receive the active preview span rather than only the last persisted span.

Conceptually:

```kotlin
renderedSpan =
    resizePreviewSpan ?: persistedSpan
```

This makes resize feel direct rather than showing a large tile with small-tile content until release.

---

# 25. Persistence

Transient edit state must not be stored in the persistent home-layout model.

Persist:

```text
tile identity
grid position
width
height
tile configuration
```

Do not persist:

```text
selected
editing
moving
resizing
pressed
recessed scale
resize handle state
```

The layout should be persisted when operations successfully commit.

---

# 26. Recommended component ownership

Do not put the complete edit-mode system into each tile composable.

Recommended structure:

```text
HomeScreen
│
├─ HomeInteractionController
│   ├─ edit state
│   ├─ selection
│   ├─ move state
│   └─ resize state
│
├─ TileLayoutEngine
│   ├─ occupancy
│   ├─ positions
│   ├─ resize validation
│   ├─ move validation
│   └─ collision handling
│
├─ TileGrid
│   └─ tile rendering
│
├─ TileEditOverlay
│   ├─ Unpin
│   └─ resize affordances
│
└─ HomePersistence
```

Responsibilities should remain separated.

---

# 27. HomeInteractionController

This system owns interaction state.

It should answer:

```text
Are we editing?

Which tile is selected?

Is a tile moving?

Is a tile resizing?

What manipulation owns the pointer?

Should a tap launch or select?

Should scrolling be allowed?
```

It should not determine whether a `4×3` tile physically fits.

That belongs to the layout engine.

---

# 28. TileLayoutEngine

The layout engine owns geometry.

It should answer:

```text
Where is this tile?

What cells does it occupy?

Can this tile move here?

Can this tile become this size?

What is the nearest valid span?

What happens when a resize collides?

What is the resulting layout?
```

The editor requests operations.

The layout engine validates them.

Example:

```text
Editor:
"Tile 42 wants span 4×2."

Layout engine:
"Allowed."

Editor:
preview/commit 4×2.
```

or:

```text
Layout engine:
"Blocked. Last valid span is 3×2."
```

---

# 29. Tile renderer

The tile renderer should receive simple presentation state.

Example:

```kotlin
data class TilePresentationState(
    val selected: Boolean,
    val recessed: Boolean,
    val moving: Boolean,
    val resizing: Boolean
)
```

It should not contain the business logic for deciding these values.

The home controller supplies them.

---

# 30. Suggested state transitions

```text
NORMAL
│
├─ tap tile
│      └─ launch app
│
└─ long press tile A
       ↓
EDITING(A)
│
├─ tap B
│      ↓
│   EDITING(B)
│
├─ drag B body
│      ↓
│   MOVING(B)
│      ↓ release
│   EDITING(B)
│
├─ drag resize handle
│      ↓
│   RESIZING(B)
│      ↓ release
│   EDITING(B)
│
├─ Unpin B
│      ↓
│   EDITING(null)
│
├─ tap C
│      ↓
│   EDITING(C)
│
└─ tap background / Back
       ↓
NORMAL
```

---

# 31. Gesture precedence

The planner should preserve a strict priority order.

While editing:

```text
1. Unpin hit target
2. Resize-handle hit target
3. Active resize gesture
4. Active move gesture
5. Selected-tile drag
6. Tile selection tap
7. Home scroll
8. Background tap
```

Horizontal pager handling should sit below any active tile-manipulation gesture.

This must be designed centrally.

Do not allow separate composables to independently race for the same pointer stream.

---

# 32. Hit targets

Visual controls may be small, but touch targets should remain comfortably larger.

Resize handles especially need generous invisible hit regions.

Example:

```text
visible resize marker:
16–24 dp

touch target:
approximately 40–48 dp
```

The touch target may extend inward over the tile rather than outward beyond the tile's screen boundary.

This is important for tiles at the right or bottom edge.

---

# 33. Tiles near screen boundaries

Editing controls must remain usable when a tile is:

- at the top edge;
- at the bottom viewport edge;
- against the left edge;
- against the right edge.

Do not place critical controls completely outside the tile.

For example, if using a bottom-right resize handle:

```text
handle center should remain on or inside the tile corner
```

rather than being positioned entirely outside its bounds.

This avoids clipping.

---

# 34. Auto-scroll during tile movement

Because the Start grid can extend vertically, moving a selected tile near the top or bottom viewport edge should eventually support auto-scroll.

Expected behavior:

```text
drag tile toward bottom edge
→ viewport begins scrolling down
→ tile remains under pointer
```

This may be a later implementation stage, but the architecture must not make it impossible.

The same may eventually be useful during vertical resizing.

---

# 35. Edit controls must track animated geometry

The Unpin button and resize affordances should be anchored to the actual rendered bounds of the selected tile.

During:

```text
move
resize
grid animation
scroll
```

they must remain attached.

Do not compute their screen position once when edit mode starts.

They need to follow the selected tile continuously.

---

# 36. Selection after relayout

If moving or resizing causes surrounding tiles to shift, the manipulated tile must remain selected.

Selection is based on stable tile identity:

```text
TileId
```

not:

```text
grid position
list index
composable slot
```

This is mandatory because grid positions can change during editing.

---

# 37. Removal animation

When Unpin is pressed:

1. disable interaction with that tile;
2. animate its visual removal;
3. remove it from occupancy;
4. allow any layout response;
5. clear `selectedTileId`;
6. remain in edit mode.

Exact ordering may depend on the layout system, but the user should not see:

```text
tile instantly disappears
entire editor exits
```

The edit context should persist.

---

# 38. Accessibility

The visual shrink should not be the only indication that a tile is selected.

Expose semantic state:

```text
"Selected"
"Move tile"
"Resize tile"
"Unpin tile"
```

Resize affordances should also have accessible actions where practical.

A user who cannot perform drag resizing should eventually be given an alternate resize path through accessibility actions, even if the ordinary UI remains drag-based.

---

# 39. Reduced-motion behavior

If the user requests reduced motion:

- selection can use shorter or immediate scale transitions;
- moving and resizing should still provide geometry feedback;
- functionality must remain identical.

Do not remove edit-state distinction entirely.

---

# 40. Things the implementation must not do

Do **not**:

- shrink the logical occupancy rectangles;
- reflow tiles merely because edit mode started;
- launch an app from a simple tap while edit mode is active;
- exit edit mode after every move;
- exit edit mode after every resize;
- use a Windows-style resize-size cycling button;
- couple resize implementation to the Unpin button;
- store selection in persistent launcher state;
- identify selected tiles by their grid position;
- allow horizontal paging to steal an active drag;
- implement editing as independent `isEditing` state inside every tile.

---

# 41. Minimum first implementation

The first complete implementation should support:

1. Long-press tile to enter edit mode.
2. Current tile becomes selected.
3. All other tiles shrink visually.
4. Selected tile renders above other tiles.
5. Existing Unpin button appears on the selected tile.
6. One resize affordance appears on the selected tile.
7. Tap another tile to transfer selection.
8. Drag selected tile to move it.
9. Drag resize affordance to snap its width/height to valid grid spans.
10. Moving and resizing retain selection.
11. Unpin keeps the launcher in edit mode.
12. Tap empty space or Back to exit edit mode.
13. All tiles return smoothly to normal scale.

That constitutes the core feature.

---

# 42. Later polish

After the core interaction is stable, optional improvements include:

- multiple resize handles;
- auto-scroll while moving;
- auto-scroll while resizing;
- spring-based scale transitions;
- richer placement previews;
- neighbor reflow previews;
- haptic feedback when entering edit mode;
- haptic feedback when crossing resize grid boundaries;
- stronger selected-tile elevation;
- smooth content-layout changes during resize.

These should not block the initial architecture.

---

# 43. Final design rule

The most important behavior is:

```text
Long-press does not put one tile into edit mode.

Long-press puts the entire Start surface into edit mode,
with one tile selected.
```

Everything else follows from this.

While editing:

```text
selected tile:
    full scale
    elevated
    movable
    resizable by dragging
    existing Unpin control visible

all other tiles:
    visually recessed
    still occupy their normal logical grid bounds
    tap to become selected
```

The user should be able to edit several tiles consecutively without repeatedly entering and leaving the mode.

This preserves the strongest part of the Windows 10 Mobile interaction while adapting it to the launcher's more flexible, drag-resizable tile system.
