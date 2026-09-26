# App-Tile Shortcut System — Design Specification

## 1. Design goal

App shortcuts should become part of the tile itself rather than a separate launcher feature.

An app tile has two distinct visual states:

| State | Appearance |
|---|---|
| **No pinned shortcuts** | App icon is centered and scaled proportionally to fit the tile. |
| **One or more pinned shortcuts** | App icon moves to the existing large-tile anchor position. Shortcut icons occupy adjacent positions extending away from it. |

The important rule is:

> **Do not reserve shortcut space until the user actually pins a shortcut.**

A newly placed 2×2, 3×2, 2×3, etc. tile should therefore look like a large icon tile, not like an empty shortcut container.

---

# 2. Default tile appearance

## 2.1 1×1 tiles

Keep the existing 1×1 presentation.

The application icon should use the normal 1×1 icon visual size and alignment.

This becomes the **reference icon size** for the shortcut layout.

Call this conceptually:

```text
unitIconSize
```

This does not necessarily mean the full dimensions of a grid cell. It means the actual visual size currently used for the icon inside a 1×1 tile.

Pinned shortcut icons and the compact app icon in customized large tiles should be approximately this same visual size.

---

## 2.2 Large tiles with no pinned shortcuts

Change the current behavior.

Currently, large tiles immediately use the compact app-icon position intended for shortcut-enabled tiles.

That should stop.

A large tile with zero pinned shortcuts should instead behave as a simple enlarged icon container:

```text
┌─────────────────────┐
│                     │
│                     │
│          ◉          │
│                     │
│                     │
└─────────────────────┘
```

The icon must:

- remain centered horizontally and vertically;
- preserve its aspect ratio;
- scale proportionally;
- become as large as the tile's content area reasonably allows;
- use the same icon normalization rules as the existing 1×1 implementation;
- never stretch independently on X/Y;
- never reserve empty space for shortcuts.

Conceptually:

```kotlin
availableWidth = tileWidth - horizontalInsets
availableHeight = tileHeight - verticalInsets

iconExtent = min(availableWidth, availableHeight)
```

The actual visual-size multiplier should reuse the launcher's current 1×1 icon treatment rather than introducing a second arbitrary scale system.

For example, a 1×3 tile will still be limited primarily by its width:

```text
┌───────┐
│       │
│       │
│   ◉   │
│       │
│       │
└───────┘
```

A 3×3 tile can produce a much larger centered icon:

```text
┌───────────────────┐
│                   │
│                   │
│        ◉◉◉        │
│        ◉◉◉        │
│        ◉◉◉        │
│                   │
└───────────────────┘
```

This centered presentation is the default state of every large tile.

---

# 3. Shortcut discovery

Every application tile should expose the Android app shortcuts available for that application.

This includes the launcher-visible shortcuts that the installed app publishes through Android's shortcut system.

The feature should conceptually operate on:

```text
Application
    └── Shortcut A
    └── Shortcut B
    └── Shortcut C
```

The launcher should retain enough identity to distinguish shortcuts reliably. At minimum the logical identity is approximately:

```kotlin
ShortcutRef(
    packageName,
    user/profile,
    shortcutId
)
```

Do not identify shortcuts only by label, icon, or intent.

Shortcut IDs are the important stable application-provided identity.

Work-profile and secondary-profile applications must also remain distinct. Two instances of the same package belonging to different Android users must not accidentally share shortcut state.

---

# 4. Launcher pinning is not Android shortcut pinning

The word **pinned** in this design means:

> "This launcher displays this app shortcut permanently inside this particular home-screen tile."

It does **not** necessarily mean Android's `requestPinShortcut()` mechanism.

These are separate concepts.

The launcher needs its own persistent relationship:

```text
Tile
  ├── application
  └── locallyPinnedShortcuts[]
```

For example:

```kotlin
TileShortcutState(
    tileId = ...,
    pinnedShortcutIds = [
        "compose",
        "camera",
        "new_message"
    ]
)
```

The launcher can still use Android's launcher shortcut APIs to discover and invoke those shortcuts, but the layout configuration belongs to the launcher.

This distinction should be preserved in naming inside the implementation. Avoid ambiguous fields such as:

```kotlin
isPinned
```

when it is unclear whether that means Android-platform pinning or launcher-tile pinning.

Prefer concepts such as:

```kotlin
isPinnedToTile
tileShortcutIds
TileShortcutBinding
```

---

# 5. Opening the shortcut menu

A user should be able to swipe across an app tile in **any direction** to expose its shortcuts.

All four general directions are equivalent:

```text
←  →  ↑  ↓
```

Do not assign different meanings to different directions.

For example:

```text
swipe left   → shortcut menu
swipe right  → shortcut menu
swipe up     → shortcut menu
swipe down   → shortcut menu
```

The gesture means:

> "Show actions belonging to this tile."

The exact menu visual design is deliberately temporary.

Its architecture therefore should not become coupled to the temporary menu appearance.

Think of the gesture as invoking:

```kotlin
showShortcutSurface(tile)
```

rather than:

```kotlin
showShortcutPopupAtRightEdge(...)
```

The eventual UI can then be replaced without changing tile behavior or shortcut state.

---

# 6. Gesture arbitration

This part needs special care because the launcher itself uses a vertically scrolling home grid.

A normal vertical drag must not accidentally open shortcut menus everywhere.

The gesture recognizer needs to distinguish at least:

```text
tap
long press
shortcut swipe
home-screen scroll
tile drag/edit interaction
```

The shortcut gesture should behave more like a deliberate **flick** than ownership of all drag input beginning on a tile.

Recommended behavioral model:

```text
Pointer down on tile
        │
        ├── negligible movement → normal tap
        │
        ├── long hold → existing edit/drag behavior
        │
        ├── normal sustained vertical drag → scroll home surface
        │
        └── deliberate short swipe/flick → shortcut surface
```

The exact velocity and distance thresholds belong in the later implementation plan and should use Android input density rather than hard-coded pixels.

Important requirement:

> Adding shortcut gestures must not make normal launcher scrolling unreliable.

Do not simply consume every `drag` event originating inside an application tile.

---

# 7. Shortcut pop-out

The first implementation can intentionally be visually simple.

Its responsibilities are more important than its appearance.

A menu entry should conceptually contain:

```text
[shortcut icon]   Shortcut label          [📌]
```

The thumbtack is a separate control.

The row itself and the pin control therefore have different actions:

```text
tap shortcut row/icon
    → execute shortcut

tap thumbtack
    → pin/unpin shortcut on this tile
```

For an already pinned shortcut, the pin control should show an active state.

For example:

```text
○ pin / outline thumbtack = not pinned
● pin / filled thumbtack  = pinned
```

The final icons can be changed later.

The menu should update immediately after pinning or unpinning.

---

# 8. Menu placement

Swipe direction should **not** define shortcut ordering or behavior.

The pop-out should position itself wherever it best fits within the visible launcher area.

For example, a tile at the right edge should not force its menu outside the screen just because the user swiped right.

A simple first implementation can:

```text
1. Try beside the tile.
2. Flip to the opposite side when necessary.
3. Clamp to the viewport.
```

The menu may overlay neighbouring tiles.

It does not need to reflow the home grid.

---

# 9. Pinning the first shortcut

Pinning the **first** shortcut changes the tile into its compact shortcut layout.

This is the important state transition:

```text
CENTERED_APP
        │
        │ pin first shortcut
        ▼
APP_WITH_PINNED_SHORTCUTS
```

Before:

```text
┌──────────────────────┐
│                      │
│          ◉           │
│                      │
└──────────────────────┘
```

After:

```text
┌──────────────────────┐
│                      │
│                      │
│             ◆     ◉  │
└──────────────────────┘
              shortcut app
```

The enlarged centered icon disappears.

The application icon moves to the existing compact large-tile position.

This existing layout should be reused rather than replaced with another slightly different geometry.

---

# 10. Application icon anchor

There are two layout families.

## Wide tiles

For all large tiles other than the narrow 1×2 and 1×3 forms:

> The application icon is anchored at the **bottom-right**.

Examples include:

```text
2×1
3×1
2×2
3×2
2×3
3×3
...
```

Conceptually:

```text
┌────────────────────────┐
│                        │
│                        │
│                    ◉   │
└────────────────────────┘
```

This should use the **existing bottom-right large-tile position** already implemented in the launcher.

Do not create a new approximately-similar offset.

---

## 1×2 and 1×3 tiles

These use the existing **bottom-centre** arrangement.

```text
1×2:

┌───────┐
│       │
│       │
│   ◉   │
└───────┘
```

```text
1×3:

┌───────┐
│       │
│       │
│       │
│   ◉   │
└───────┘
```

Again, reuse the existing compact app-icon geometry.

---

# 11. Shortcut layout on wide tiles

Pinned shortcuts radiate **leftward** from the application icon along the tile's bottom edge.

The app icon is effectively slot zero.

```text
direction of growth
← ← ← ←

┌───────────────────────────────┐
│                               │
│                               │
│   ◆       ◆       ◆       ◉   │
└───────────────────────────────┘
                            app
```

A useful conceptual slot model is:

```text
[ shortcut 3 ][ shortcut 2 ][ shortcut 1 ][ APP ]
```

The first shortcut pinned should occupy the position immediately left of the app icon.

The second shortcut occupies the next position left.

The third continues outward.

Thus pin order is spatially stable:

```text
first pinned  → closest to app
second pinned → one slot farther
third pinned  → one slot farther
```

Do not reverse all existing shortcut positions whenever another shortcut is pinned.

---

# 12. Shortcut layout on 1×2 and 1×3 tiles

Narrow vertical tiles use exactly the same model rotated vertically.

The app remains at the bottom.

Shortcuts radiate **upward**.

### 1×2

```text
┌───────┐
│   ◆   │
│       │
│   ◉   │
└───────┘
```

### 1×3

```text
┌───────┐
│   ◆   │
│       │
│   ◆   │
│       │
│   ◉   │
└───────┘
```

Conceptually:

```text
shortcut 2
    ↑
shortcut 1
    ↑
application
```

The first pinned shortcut is therefore always closest to the application icon.

---

# 13. Size of pinned icons

The shortcut layout should visually decompose the bottom edge of the large tile into something resembling several normal launcher icon positions.

The target is:

> **The application icon and each pinned shortcut should appear approximately the same size as an app icon inside a 1×1 tile.**

Do not shrink shortcuts progressively as more are added.

Do not scale them relative to the dimensions of the large tile.

Instead use one shared visual metric:

```text
unitIconSize
```

Then:

```text
compact app icon ≈ unitIconSize
shortcut icon    ≈ unitIconSize
```

Example:

```text
1×1:

┌────────┐
│   ◉    │
└────────┘


3×2 customized:

┌────────────────────────┐
│                        │
│   ◆       ◆       ◉    │
└────────────────────────┘
```

Those three symbols should have approximately equal visual weight.

---

# 14. Slot spacing

Shortcut positions should not be calculated from the number of shortcuts currently visible.

Bad:

```text
two shortcuts → evenly distribute over entire tile width
three shortcuts → recompute everything
```

That would make icons jump whenever pinning occurs.

Instead, use fixed slot spacing based on the launcher's normal grid geometry.

Conceptually:

```text
slotPitch ≈ one launcher grid unit
```

For horizontal layouts:

```text
shortcutPosition(n) =
    appAnchor - n * horizontalSlotPitch
```

For vertical layouts:

```text
shortcutPosition(n) =
    appAnchor - n * verticalSlotPitch
```

The implementation does not need to literally create invisible 1×1 child tiles. It only needs to produce equivalent geometry.

This makes the shortcut strip feel related to the launcher grid without making the large tile itself into a nested grid.

---

# 15. Pinned shortcut ordering

Pinned shortcuts need a stable explicit order.

Use pin order initially.

For example:

```text
User pins:
1. New post
2. Camera
3. Search
```

Result:

```text
[ Search ][ Camera ][ New post ][ App ]
```

Spatially, the earliest item stays closest to the app.

Internally:

```kotlin
pinnedShortcutIds = [
    "new_post",
    "camera",
    "search"
]
```

Rendering then maps index `0` to the nearest slot.

If the user unpins Camera:

```text
Before:

[ Search ][ Camera ][ New post ][ App ]

After:

[ Search ][ New post ][ App ]
```

Remaining items collapse toward the application icon while preserving their relative order.

Do not leave holes.

---

# 16. Capacity

A tile may only visibly contain as many shortcuts as its geometry supports.

Conceptually:

### Horizontal tiles

```text
visible shortcut capacity ≈ number of available unit slots left of app
```

### Vertical 1×N tiles

```text
visible shortcut capacity ≈ number of available unit slots above app
```

The implementation agent should derive the exact capacity from the launcher's real grid dimensions and padding rather than from arbitrary constants.

An intuitive model is approximately:

```text
2×N wide tile → app + 1 shortcut
3×N wide tile → app + 2 shortcuts
4×N wide tile → app + 3 shortcuts
```

and:

```text
1×2 → app + 1 shortcut
1×3 → app + 2 shortcuts
```

This matches the requirement that every shortcut retains approximately 1×1 icon scale.

---

# 17. At-capacity behavior

Do not compress icons to make more shortcuts fit.

Do not overlap icons.

Do not create a second row automatically.

The shortcut layout is deliberately a **single edge strip**.

When the tile reaches capacity, additional unpinned shortcuts should remain accessible from the swipe menu.

Their pin controls should become unavailable while that tile has no space.

The temporary menu can represent this very simply:

```text
Camera        📌
New message   📌
Search        📌 disabled
Compose       📌 disabled
```

The user can unpin something first and then choose another shortcut.

---

# 18. Resizing a customized tile

Tile resize must not silently destroy user configuration.

Suppose a 3×2 tile contains:

```text
[ C ][ B ][ A ][ App ]
```

and the user resizes it to 2×2.

Only one shortcut may now fit.

The launcher should retain the ordered shortcut configuration internally rather than permanently deleting B and C.

Conceptually:

```kotlin
pinned = [A, B, C]
visibleCapacity = 1

visible = [A]
overflow = [B, C]
```

If the tile becomes large again:

```text
[ C ][ B ][ A ][ App ]
```

returns automatically.

The menu can still show B and C as pinned, even though the current tile dimensions prevent them from being displayed.

This makes resizing reversible.

---

# 19. Removing the final shortcut

When the user unpins the final pinned shortcut, the tile immediately returns to the normal large-tile presentation.

```text
APP_WITH_PINNED_SHORTCUTS
        │
        │ remove final shortcut
        ▼
CENTERED_APP
```

Before:

```text
┌──────────────────────┐
│                      │
│             ◆     ◉  │
└──────────────────────┘
```

After:

```text
┌──────────────────────┐
│                      │
│          ◉           │
│                      │
└──────────────────────┘
```

No empty compact layout should remain.

Zero pinned shortcuts means **centered mode**, always.

---

# 20. 1×1 tiles and pinning

All app tiles, including 1×1 tiles, can still expose the shortcut pop-out.

For example:

```text
1×1 app tile
   ↓ swipe

shortcut popup:
New message
Camera
Compose
```

However, the design described here provides no physical in-tile shortcut positions for a 1×1 tile.

Therefore:

> A 1×1 tile should expose and execute shortcuts, but should not allow in-tile pinning unless another explicit behavior is designed later.

The first implementation should not invent automatic resizing.

For a 1×1 tile, the thumbtack can therefore be hidden or disabled.

That keeps these two features separate:

```text
shortcut availability ≠ shortcut pin capacity
```

---

# 21. Shortcut interaction inside the tile

Once shortcuts are pinned, the tile contains several independent interactive regions.

Example:

```text
┌──────────────────────────────┐
│                              │
│                              │
│    ◆       ◆       ◆      ◉  │
└──────────────────────────────┘
     │       │       │      │
     │       │       │      └─ launch application
     │       │       └──────── execute shortcut 1
     │       └──────────────── execute shortcut 2
     └──────────────────────── execute shortcut 3
```

A tap directly on a shortcut icon should launch that shortcut.

A tap directly on the app icon should launch the normal application.

Existing tile-background tap behavior can remain as the normal application launch behavior, unless the launcher already defines another action.

The shortcut icons must therefore be actual hit targets, not decorative children drawn over a tile whose parent consumes every tap.

---

# 22. Touch targets versus visual size

Visual icon size and touch target size should be separate concepts.

The icons should visually remain approximately:

```text
1×1 app icon size
```

But their interactive regions may extend around them to provide comfortable touch targets.

Conceptually:

```text
┌──────── touch region ────────┐
│                              │
│             ◆                │
│                              │
└──────────────────────────────┘
              ↑
       visual shortcut icon
```

Neighbouring touch regions must not overlap ambiguously.

This is another reason to derive shortcut positions from stable grid-sized slots.

---

# 23. Shortcut icon treatment

A shortcut may provide an icon independent from the parent application's main icon.

Use the shortcut-specific icon where available.

However, it should be normalized into the same visual container system as other launcher icons:

```text
same visual extent
same slot dimensions
same edge padding policy
same clipping policy
```

A shortcut icon must not become visually twice as large simply because its source drawable has tighter intrinsic bounds.

Likewise, do not force every shortcut into its own coloured tile or chip.

Pinned shortcuts are intended to appear as icon elements **inside the existing app tile**.

---

# 24. Labels

Pinned shortcuts inside tiles should be icon-only.

Do not place labels under or beside each pinned shortcut.

For example:

```text
GOOD

[ ◆ ][ ◆ ][ ◆ ][ ◉ ]


NOT

[ ◆ Camera ][ ◆ Search ][ ◆ Compose ][ ◉ App ]
```

Labels belong in the pop-out menu, where there is enough space to communicate the shortcut's actual meaning.

This also keeps the shortcut strip compact enough to preserve the 1×1-equivalent icon scale.

---

# 25. Runtime shortcut changes

Android applications can change their shortcut sets.

The launcher must assume that an available shortcut can:

```text
appear
disappear
be disabled
change label
change icon
change rank
```

The launcher should persist the shortcut's identity, not a frozen copy of all its display information.

When rendering the tile, resolve the current shortcut metadata where possible.

If an application permanently removes a shortcut that the user had pinned, the launcher must not keep launching stale intents indefinitely.

The detailed recovery implementation can be decided later, but the design rule should be:

> Stale shortcuts must fail safely and must not destabilize the tile.

The simplest first behavior is to omit a no-longer-valid shortcut from rendering while retaining enough state to recover if it becomes available again.

---

# 26. Application removal

Shortcut state belongs to the corresponding app tile.

If the application itself is removed, its shortcut bindings should follow whatever lifecycle already applies to that app tile.

They must not become global shortcut objects detached from the application.

Conceptually:

```text
App tile removed
    ↓
its tile shortcut configuration is no longer independently visible
```

If the launcher's current model retains temporarily unavailable applications, shortcut configuration may be retained alongside the tile using the same persistence rules.

---

# 27. Multiple tiles for the same application

Shortcut configuration should belong to a **tile instance**, not globally to the package.

For example, if the user has two Discord tiles:

```text
Tile A:
[ New message ][ App ]

Tile B:
[ Search ][ App ]
```

these should be valid simultaneously.

Do not implement:

```kotlin
packageName -> pinnedShortcutIds
```

Implement the relationship closer to:

```kotlin
tileId -> pinnedShortcutIds
```

with the package and profile being attributes of the tile.

This leaves the launcher free to support different shortcut arrangements for different home layouts.

---

# 28. Two independent launcher layouts

Because the launcher can maintain separate home layouts, the shortcut configuration should follow the same ownership boundary.

If tile layouts are independent, shortcut pinning must also be independent.

Do not accidentally make shortcut selections global simply because both tiles ultimately refer to the same Android application.

Conceptually:

```text
Layout A
└── Tile X
    └── Shortcut configuration X

Layout B
└── Tile Y
    └── Shortcut configuration Y
```

---

# 29. Menu ordering versus pinned ordering

There are two different ordering concerns.

The **shortcut menu** should normally follow the application's/system's shortcut ordering or ranking.

The **tile strip** should follow user pin order.

These should not be coupled.

For example, an application may later change:

```text
Search rank 3 → rank 1
```

That may move Search in the pop-out menu.

It should **not** unexpectedly rearrange a customized home tile.

Once pinned, tile ordering belongs to the user's launcher configuration.

---

# 30. Animation requirements

The shortcut menu itself can remain quick and dirty.

The tile state change should nevertheless preserve spatial continuity.

### First pin

The centered app icon should conceptually transition toward its compact anchor while the shortcut appears beside it:

```text
          ◉

      ↓

           ◆       ◉
```

### Additional pin

Existing icons remain fixed while the new icon appears in the next available outward slot.

### Unpin

Remaining shortcut icons collapse toward the app anchor.

### Last unpin

The compact app icon returns toward the center and scales back to the large centered presentation.

These can initially be basic translation/scale/fade animations.

Do not make the persistence or layout model dependent on animation state.

---

# 31. Interaction with tile edit mode

Shortcut invocation should not interfere with tile editing.

When the launcher is in its explicit tile editing/rearrangement mode:

```text
shortcut swipe → disabled
shortcut launch → preferably disabled
tile manipulation → owns interaction
```

The shortcut strip remains visually present, but the tile should behave as one rearrangeable/resizable object.

Pinned shortcuts are part of the tile, not independent home-grid objects.

Dragging a shortcut icon must therefore never detach that shortcut and start moving it around the main grid.

---

# 32. Accessibility semantics

The launcher should expose pinned shortcuts as discrete accessible actions.

For example:

```text
Discord
New message
Search
Open Discord
```

rather than exposing the entire tile as one unlabeled canvas.

The thumbtack control in the menu also needs distinct semantics such as:

```text
Pin "New message" to tile
```

and:

```text
Unpin "New message" from tile
```

Visual icon-only presentation must not result in unlabeled accessibility nodes.

---

# 33. Recommended conceptual tile state

The implementation does not need to use these exact classes, but the architecture should represent approximately this information:

```kotlin
AppTile {
    tileId
    packageName
    userHandle

    widthUnits
    heightUnits

    pinnedShortcutIds: List<String>
}
```

Presentation derives from state:

```kotlin
if (visiblePinnedShortcuts.isEmpty()) {
    renderCenteredAppIcon()
} else {
    renderCompactAppIcon()
    renderPinnedShortcutStrip()
}
```

Do **not** persist presentation details such as:

```text
appIconX
appIconY
shortcut1X
shortcut1Y
```

Those are consequences of tile dimensions and layout rules.

Persist user intent; derive geometry.

---

# 34. State model

The tile can be understood as:

```text
                     pin first shortcut
CENTERED_APP ─────────────────────────────► SHORTCUT_LAYOUT
      ▲                                          │
      │                                          │
      └──────────────────────────────────────────┘
                  unpin final shortcut
```

Within `SHORTCUT_LAYOUT`, pinning and unpinning only changes the ordered shortcut collection.

Resizing changes the number that can currently be rendered but does not fundamentally create another tile type.

This avoids turning each tile dimension into a separate component.

---

# 35. Visual invariants

The final implementation should preserve these invariants:

1. **Zero pinned shortcuts means centered icon.**
2. **The first pin activates the compact large-tile layout.**
3. **The final unpin restores the centered layout.**
4. **Wide tiles anchor the app icon bottom-right.**
5. **1×2 and 1×3 tiles anchor it bottom-centre.**
6. **Wide-tile shortcuts extend leftward.**
7. **Narrow-tile shortcuts extend upward.**
8. **The first pinned shortcut is nearest to the app icon.**
9. **All pinned shortcut icons and the compact app icon are approximately the normal 1×1 icon size.**
10. **Pinned shortcuts do not create coloured mini-tiles or labels inside the parent tile.**
11. **Shortcut spacing is stable and does not redistribute whenever the count changes.**
12. **No shortcut strip is visible until at least one shortcut is pinned.**
13. **A swipe in any direction opens the same shortcut surface.**
14. **Normal vertical launcher scrolling must remain usable.**
15. **Shortcut configuration belongs to the individual tile.**
16. **Resizing must not silently destroy saved shortcut configuration.**
17. **Pinned shortcuts are children/actions of the tile, not independent grid objects.**
18. **Android-platform shortcut pinning and launcher tile pinning must remain distinct concepts.**

The key visual transformation is therefore simply:

```text
DEFAULT LARGE TILE

┌──────────────────────────┐
│                          │
│            ◉             │
│                          │
└──────────────────────────┘


AFTER CUSTOMIZATION

┌──────────────────────────┐
│                          │
│                          │
│   ◆       ◆       ◆    ◉ │
└──────────────────────────┘
```

For a narrow tile:

```text
DEFAULT              CUSTOMIZED

┌────────┐           ┌────────┐
│        │           │   ◆    │
│   ◉    │           │   ◆    │
│        │           │   ◉    │
└────────┘           └────────┘
```

That distinction—**large visual app tile until the user explicitly turns it into an app-plus-actions tile**—should be the central design rule the implementation plan preserves.
