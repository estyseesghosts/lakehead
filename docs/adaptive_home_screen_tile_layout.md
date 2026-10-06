# Adaptive Home Screen Tile Layout — Design Requirements

## 1. Objective

Implement a flexible content layout for home screen tiles of different widths and heights. The layout must adapt to the number of pinned app shortcuts, available notifications, and the tile's shape.

Tiles are not simply scaled versions of one layout. A wide tile, square tile, and tall tile can arrange the same content differently.

The following requirements describe the layout system, including how it should behave for tile sizes not shown in the original sketches.

**All dimensions use width × height**, measured in grid cells. For example, 2×3 is two cells wide and three cells tall.

## 2. Basic layout rules

Every tile can contain three types of content: the main app icon, pinned app shortcuts, and notification previews.

The main app icon is the primary element. Pinned shortcuts are secondary elements. Notification previews occupy the space that remains available.

The layout must follow these rules:

1. Without pinned shortcuts, display a large main app icon, centered and scaled proportionally to the tile's available space.
2. With pinned shortcuts, give the main app icon and shortcuts distinct positions, each occupying approximately one grid cell by default.
3. On horizontal and square tiles, place the main app icon at the right end of the shortcut group. Shortcuts extend left along the bottom. Center the complete group when there is extra horizontal space.
4. On vertical tiles, arrange shortcuts upward from the main app icon. The main icon remains at the bottom.
5. For a sufficiently large or densely populated tile, allow the shortcut arrangement to change from a bottom row to a vertical column.
6. Place notifications in the remaining usable area. Do not allow them to overlap the app icon or shortcuts.
7. Use consistent spacing and padding. Do not force all elements to grow just to fill empty space.

The layout must change when content is added or removed, or when the user resizes the tile.

## 3. Layout by tile shape

### 3.1 Single-cell and single-row tiles

```text
1×1
+-----+
|  X  |
+-----+
```

**1×1:** Display only the main app icon. Do not display pinned shortcuts or notification text. The shortcut menu remains accessible through the existing interaction.

```text
2×1
+-----+-----+
|  O  |  X  |
+-----+-----+
```

**2×1:** With one pinned shortcut, place it to the left of the main icon. Do not reserve room for notification text.

```text
3×1
+-----+-----+-----+
|  O  |  O  |  X  |
+-----+-----+-----+
```

**3×1 and wider:** Continue the same horizontal arrangement. The main icon occupies the rightmost position within the group. Shortcuts extend left until available space is exhausted.

### 3.2 Single-column vertical tiles

For 1×2, 1×3, and taller single-column tiles, use the existing vertical arrangement: **the main app icon is at the bottom, with pinned shortcuts directly above it.**

```text
1×2
+-----+
|  O  |
+-----+
|  X  |
+-----+
```

**1×2:** One shortcut above the main icon. The main icon must not move to the top or be placed alongside the shortcut.

```text
1×3
+-----+
|  O  |
+-----+
|  O  |
+-----+
|  X  |
+-----+
```

**1×3:** Up to two shortcuts above the main icon. The first pinned shortcut occupies the position nearest the main icon. Additional shortcuts extend upward.

For taller single-column tiles, continue this arrangement. Do not use the available height to overload the tile with excessive content.

Single-column tiles should not display full notification messages. They can use a small notification indicator, but their limited width is unsuitable for readable previews.

### 3.3 Small square and rectangular tiles

A 2×2 tile is the smallest tile that should support a main app icon, one pinned shortcut, and a readable notification preview simultaneously.

```text
2×2
+-----------+
| Message   |
+-----+-----+
|  O  |  X  |
+-----+-----+
```

**2×2:** Place the main app icon at bottom-right and the pinned shortcut at bottom-left. The notification preview occupies the area above them. Limit the preview to the available space.

```text
2×3, one shortcut
+-----------+
|           |
+           +
|           |
+-----+-----+
|  O  |  X  |
+-----+-----+
```

**2×3, one shortcut:** Use the same bottom-left and bottom-right arrangement. The upper two rows provide a larger notification area. If there are no notifications, they may remain clear; keep the icon positions stable.

```text
2×3, two shortcuts
+-----------+
|           |
+     X     +
|           |
+-----+-----+
|  O  |  O  |
+-----+-----+
```

**2×3, two shortcuts:** Give the bottom row to both shortcuts. Move the main app icon above them, centered within the larger remaining area. This is an intentional change of layout, rather than shrinking all three icons to fit on one row.

The 2×3 tile is an important example of content-driven layout. One shortcut leaves room for the normal bottom-right icon arrangement. A second shortcut triggers a different arrangement.

For 2×2, do not force this more crowded arrangement merely to display additional shortcuts. Keep excess shortcuts accessible in the pop-up menu.

### 3.4 Wider horizontal tiles

For 3×2, 4×2, 5×2, and larger horizontal tiles, use the bottom row as the default icon and shortcut area.

Place the main app icon at the right end of the group, with shortcuts to its left. When the group does not fill the width, center it along the bottom rather than spreading shortcuts apart.

The remaining upper area is available for notifications.

```text
4×2
+-------+-------+-------+-------+
| Unread message                 |
| Notification preview...        |
+-------+-------+-------+-------+
|   O   |   O   |   O   |   X   |
+-------+-------+-------+-------+
```

*4×2: Notification area above three pinned shortcuts and the main app icon.*

The same rule applies to wider tiles. Do not increase the size of every shortcut simply because a tile has additional width.

If a horizontal tile has more pinned shortcuts than its bottom row can accommodate, use available secondary space where appropriate, or retain excess shortcuts in the menu. Avoid adding extra rows if doing so makes the notification area unusable.

### 3.5 Tall multi-column tiles

Tall tiles need an additional layout option.

For 2×4, 3×4, 3×5, and other sufficiently tall tiles, the layout should be able to switch from a horizontal bottom row to a vertical shortcut column when the pinned shortcut count justifies it.

The vertical column occupies the right edge. Place the main app icon at the bottom of that column, with shortcuts stacked upward.

```text
3×5 with notifications             3×5 without notifications
+-------------+-------+             +-------------+-------+
| Message     |   O   |             |             |   O   |
| Preview...  |       |             |             |       |
+             +-------+             +             +-------+
|             |   O   |             |             |   O   |
+             +-------+             +             +-------+
|             |   O   |             |      X      |   O   |
+             +-------+             +             +-------+
|             |   O   |             |             |   O   |
+             +-------+             +             +-------+
|             |   X   |             |             |   O   |
+-------------+-------+             +-------------+-------+
```

**3×5 with notifications:** Place notifications in the left area. Keep shortcuts and the main app icon in a fixed vertical column on the right.

**3×5 without notifications:** When shortcuts fill the right column and no notifications are present, move and enlarge the main app icon into the larger left area.

The right-hand example is an alternate layout for a shortcut-heavy tile. It must not be used whenever a tall tile has only one or two shortcuts.

A 2×4 tile can follow the same principle, with up to three shortcuts above its bottom-right main icon. A 3×5 tile has room for a longer column and more useful notification content beside it.

The exact point at which a tile switches layouts should depend on usable space, not on one fixed tile dimension.

## 4. Notification layout

Notifications must adapt to the space left after the main icon and pinned shortcuts have been placed.

For a small square tile, show a short message above the icon row. For a taller tile with a bottom icon row, use the upper space for a longer preview. For a wide tile, use the available horizontal space to display more text. For a tall tile with a vertical shortcut column, put notifications beside that column.

When several notifications are available, display them in a vertically scrollable area. The notification area must scroll independently of the tile and must not move the app icon or pinned shortcuts.

Notification previews should have clear separation between messages. Show identifying information and as much message text as the available area permits.

If the available space cannot display a useful preview, show a compact notification indicator instead. Do not shrink the main icon or make shortcuts difficult to tap to accommodate unread messages.

## 5. Shortcut placement and overflow

Shortcut placement must be predictable. The first pinned shortcut is nearest the main app icon. Each subsequent shortcut extends outward: left on horizontal layouts and upward on vertical layouts.

An additional shortcut must not automatically cause the tile to shrink every icon. Instead, first use available positions, then consider a different arrangement if the tile has sufficient space.

Do not overload a tile just because it has enough theoretical grid cells. A 2×2 tile should remain compact, while a 2×3 tile can use its additional height to accommodate a second shortcut by moving the main icon above the shortcut row.

When there is insufficient room for further pinned shortcuts, keep them accessible through the existing shortcut menu. Do not create an independently scrollable shortcut strip inside small tiles.

## 6. Responsive behaviour

The layout must respond to the following changes without requiring the user to configure separate arrangements:

- **Pinning a shortcut:** Add it to the correct position. If the current arrangement is full and an alternate arrangement has enough space, reflow the content.
- **Unpinning a shortcut:** Remove the unused position. When appropriate, return to the simpler arrangement.
- **Receiving a notification:** Use the available notification region. If no suitable region exists, show only an indicator.
- **Clearing notifications:** Remove notification content. In shortcut-heavy tall tiles, the main icon can expand into the newly available space.
- **Resizing a tile:** Recalculate the arrangement for the new width and height. Preserve shortcut order and keep all displayed elements accessible.

Avoid unnecessary movement. For example, a 2×3 tile with one pinned shortcut should keep its main icon at bottom-right whether its notification area is empty or contains a message.

## 7. Interaction requirements

These content layouts must work with the existing tile interaction model.

The main app icon, pinned shortcuts, and visible notifications retain independent tap targets. However, the tile is still one cohesive surface for long-press and edit-mode interaction.

A long press anywhere on the tile must allow the user to select and move the whole tile. Resizing must remain exclusive to the dedicated resize handle.

The shortcut pop-up must remain accessible for shortcuts that are not shown directly on the tile. Do not reintroduce swipe-to-open shortcuts, since that gesture was removed to prevent conflicts with home-screen navigation and edit mode.

## 8. Acceptance requirements

The implementation is complete when the same general layout rules produce coherent results for small, wide, square, and tall tiles, including arbitrary dimensions beyond the examples above.

The essential cases to verify are a 1×2 tile with its main icon below its shortcut; a 1×3 tile with two shortcuts above its main icon; a 2×2 tile with one shortcut and a message; a 2×3 tile that changes arrangement when a second shortcut is pinned; a wide tile with a bottom shortcut group and notifications above it; and a tall tile that can use a right-hand shortcut column with notifications or an enlarged app icon to its left.

The final design must use the available space intelligently, without sacrificing icon readability, shortcut accessibility, or predictable placement.
