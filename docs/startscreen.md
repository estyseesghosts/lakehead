## How the Windows 10 Mobile Start screen works

Windows 10 Mobile treats Start as **one continuous vertical tile surface**, not as Android-style pages.



Its important properties are:

- Tiles occupy discrete grid units.
- Tiles can have several footprints: effectively small, medium, wide, and large variants. Microsoft's Windows tile model used sizes equivalent to `1×1`, `2×2`, `4×2`, and `4×4` grid units. :chatgpt-content-reference{index="1"}
- Tiles can be moved and resized by the user. :chatgpt-content-reference{index="2"}
- There is **one vertically scrolling Start surface**. It can extend for many screens rather than adding horizontal pages. :chatgpt-content-reference{index="3"}
- Windows 10 Mobile could increase the **horizontal grid density** using **Show more tiles**. For example, some devices moved from three medium-tile columns to four. :chatgpt-content-reference{index="4"}

### One correction about the "contracting" grid

Windows 10 Mobile generally did **not continuously resize tile height to make the current set fit the screen**.

What changes is mainly the **extent of the tile surface**:

```text
few tiles:

┌──────────────────┐
│ ███ ███ ████████ │
│ ███ ███ ████████ │
│ ████████ ███ ███ │
│ ████████ ███ ███ │
└──────────────────┘
        ↑
  content ends here
```

Add more:

```text
┌──────────────────┐
│ ███ ███ ████████ │
│ ███ ███ ████████ │
│ ████████ ███ ███ │
│ ████████ ███ ███ │
│ ███ ███ ████████ │
│ ███ ███ ████████ │
│ ████████████████ │
│ ...              │
└──────────────────┘
```

The logical grid becomes taller, so the **scroll range expands**. Remove tiles and the scrollable extent contracts. The tile unit itself remains substantially constant.

There was also an elastic end-of-scroll effect in the Windows Phone family: at the limits, content could visually compress and rebound instead of stopping rigidly. Contemporary documentation describes the icons as compressing slightly at the top/bottom limit. :chatgpt-content-reference{index="5"}

That effect is separate from the actual grid layout.

---

# What I would copy for your launcher

For your launcher, I would separate these concepts:

```text
VIEWPORT
    ↓
┌──────────────────────────────┐
│                              │
│       GRID TRANSFORM         │
│            ↓                 │
│       ┌────────────┐         │
│       │ occupied   │         │
│       │ tile area  │         │
│       │            │         │
│       └────────────┘         │
│                              │
└──────────────────────────────┘

Grid coordinates ≠ screen coordinates
```

Do **not** make row 0 mean:

> "the first row must touch the top of the screen."

Instead, make row 0 a **logical coordinate**.

Then have a separate:

```kotlin
gridOriginY
```

which determines where that logical grid appears inside the viewport.

That is the important architectural decision.

---

# A centered, expanding grid

Suppose your phone has this much usable vertical area:

```text
┌─────────────────────┐
│                     │
│                     │
│                     │
│                     │
│                     │
│                     │
└─────────────────────┘
```

With only a few tiles, you can place their occupied bounds near the middle:

```text
┌─────────────────────┐
│                     │
│    ■ ■■■            │
│    ■■■ ■            │
│    ■■■■■            │
│                     │
│                     │
└─────────────────────┘
```

Add tiles:

```text
┌─────────────────────┐
│                     │
│    ■ ■■■            │
│    ■■■ ■            │
│    ■■■■■            │
│    ■ ■■■            │
│    ■■■■■            │
│                     │
└─────────────────────┘
```

And eventually:

```text
         ↑ scroll

┌─────────────────────┐
│    ■■■ ■            │
│    ■■■■■            │
│    ■ ■■■            │
│    ■■■■■            │
│    ■■■ ■            │
│    ■ ■■■            │
└─────────────────────┘

         ↓ more content
```

The transition from **floating content** to **scrolling content** can therefore happen automatically.

---

# Do not resize the tiles

I would specifically avoid doing this:

```kotlin
tileHeight = availableHeight / occupiedRows
```

That creates unstable geometry:

```text
4 rows -> large tiles
5 rows -> smaller tiles
6 rows -> smaller again
7 rows -> smaller again
```

Every added item would change the size and position of everything.

Instead:

```kotlin
cellSize = fixedCellSize
gap = fixedGap
```

and alter only:

```kotlin
gridOriginY
scrollRange
```

This keeps spatial memory intact.

---

# Better model: grow around an anchor

For your launcher, I would go a little further than Windows Phone.

Give the home layout an **anchor row**:

```text
logical row -4
logical row -3
logical row -2
logical row -1
logical row  0  ← home anchor
logical row +1
logical row +2
logical row +3
logical row +4
```

The initial viewport could place row `0` somewhere around the vertical center:

```text
screen
┌─────────────────────┐
│ row -2              │
│ row -1              │
│─────────────────────│ ← anchor
│ row  0              │
│ row +1              │
│ row +2              │
└─────────────────────┘
```

Now your grid is conceptually **unbounded in both vertical directions**.

A tile could be represented as:

```kotlin
data class TilePlacement(
    val id: TileId,
    val column: Int,
    val row: Int,       // may be negative
    val widthCells: Int,
    val heightCells: Int
)
```

For example:

```kotlin
TilePlacement(
    id = phone,
    column = 0,
    row = -1,
    widthCells = 2,
    heightCells = 2
)

TilePlacement(
    id = weather,
    column = 2,
    row = -1,
    widthCells = 4,
    heightCells = 2
)
```

This is much cleaner than defining the first visible row as `0`.

---

## Determine the occupied area

From your placements:

```kotlin
minRow = tiles.minOf { it.row }
maxRow = tiles.maxOf { it.row + it.heightCells }
```

Therefore:

```kotlin
contentHeight =
    (maxRow - minRow) * cellStride
```

where:

```kotlin
cellStride = cellSize + gap
```

Now you have the actual bounding box of the user's layout.

---

# Position the bounding box independently

If the content is shorter than the viewport:

```kotlin
val freeSpace = viewportHeight - contentHeight

val contentTop =
    viewportTop + freeSpace * verticalBias
```

`verticalBias` controls where it sits.

For example:

```text
0.0 = top
0.5 = center
1.0 = bottom
```

I would probably use approximately:

```kotlin
verticalBias = 0.45f
```

rather than exact `0.5`.

That puts the visual mass slightly above center, which also leaves more useful room near the bottom for your floating controls.

So:

```text
not this:

┌─────────────────────┐
│ ■ ■■■               │
│ ■■■ ■               │
│                     │
│                     │
│                     │
└─────────────────────┘


but this:

┌─────────────────────┐
│                     │
│ ■ ■■■               │
│ ■■■ ■               │
│                     │
│                     │
└─────────────────────┘
```

---

# Once the grid becomes taller than the viewport

Change only the scrolling constraint.

For example:

```kotlin
if (contentHeight <= viewportHeight) {
    scrollRange = 0f

    gridOriginY =
        viewportTop +
        (viewportHeight - contentHeight) * 0.45f -
        minRow * cellStride
} else {
    enableScrolling()
}
```

But do **not** suddenly snap the grid to `y = 0`.

Instead, retain the current anchor position.

Conceptually:

```text
short:

       viewport
┌─────────────────────┐
│                     │
│      CONTENT        │
│      CONTENT        │
│                     │
└─────────────────────┘


medium:

┌─────────────────────┐
│    CONTENT          │
│    CONTENT          │
│    CONTENT          │
│    CONTENT          │
│                     │
└─────────────────────┘


long:

       ↑
│    CONTENT          │
┌─────────────────────┐
│    CONTENT          │
│    CONTENT          │
│    CONTENT          │
│    CONTENT          │
└─────────────────────┘
│    CONTENT          │
       ↓
```

The coordinate system never changes. Only its allowed translation changes.

---

# This works especially well with your launcher design

You already have a **six-unit-wide**, freely placeable tile layout.

I would define it approximately as:

```kotlin
class HomeGridState {
    val columns: Int = 6

    var placements: List<TilePlacement>

    // Translation from logical grid space to screen space.
    var viewportOffsetY: Float

    // Where the home position should visually sit.
    var anchorBiasY: Float = 0.45f
}
```

Then your custom renderer effectively does:

```kotlin
screenX =
    horizontalInset +
    column * cellStride

screenY =
    gridOriginY +
    row * cellStride +
    viewportOffsetY
```

That gives you complete freedom.

The home screen no longer means:

```text
row 0 == screen top
```

It means:

```text
logical tile space
        +
viewport transform
        =
what the user sees
```

That distinction is what you want.

---

## It also solves awkward sparse layouts

Suppose the user deliberately makes this:

```text
■ ■
■■■


       ■■
       ■■
```

Do **not** compute height from the number of tiles.

Compute it from their **occupied coordinates**:

```text
min occupied row
        ↓
■ ■
■■■
.
.
.
       ■■
       ■■
        ↑
max occupied row
```

The empty area is intentional layout space.

Your bounding box therefore includes it.

This matters because your launcher allows genuinely free placement instead of automatically packing everything like a normal Android launcher.

---

# When tiles are added or removed

Animate the **grid origin**, not every tile separately.

For example, removing the bottom half:

```text
BEFORE

┌─────────────────────┐
│ ■■■                 │
│ ■■■                 │
│ ■■■                 │
│ ■■■                 │
│ ■■■                 │
└─────────────────────┘


AFTER

┌─────────────────────┐
│                     │
│ ■■■                 │
│ ■■■                 │
│ ■■■                 │
│                     │
└─────────────────────┘
```

The calculation changes:

```kotlin
targetOriginY = calculateGridOrigin(...)
```

Then:

```kotlin
Animatable(currentOriginY)
    .animateTo(targetOriginY)
```

The tiles maintain their internal positions while the **entire field moves as one object**.

That would capture a lot of the fluid feeling of Windows Phone without copying its layout exactly.

---

## Recommended architecture

I would model the launcher as four layers:

```text
┌────────────────────────────────────┐
│ FLOATING CONTROLS                  │
│ nav / context controls             │
├────────────────────────────────────┤
│                                    │
│ VIEWPORT                           │
│   ┌────────────────────────────┐   │
│   │ GRID TRANSFORM             │   │
│   │                            │   │
│   │ LOGICAL 6-COLUMN GRID      │   │
│   │                            │   │
│   └────────────────────────────┘   │
│                                    │
├────────────────────────────────────┤
│ WALLPAPER                          │
└────────────────────────────────────┘
```

The **logical grid should know nothing about screen-top alignment**.

The viewport decides:

- current scroll position;
- default resting position;
- vertical bias;
- system-bar insets;
- space needed around your floating controls;
- overscroll;
- animations.

The grid only decides:

- tile coordinates;
- tile dimensions;
- collisions;
- occupancy;
- content bounds.

### The central rule

```text
Tile positions are stable in grid space.

Grid space moves relative to the phone.
```

That gives you the Windows Phone-style **vertically extensible Start surface**, but avoids inheriting its assumption that Start should originate at the top edge. It also fits your launcher better because a sparse, deliberately composed home layout can remain visually centered, while a large layout naturally becomes an effectively infinite vertical scrolling surface.
